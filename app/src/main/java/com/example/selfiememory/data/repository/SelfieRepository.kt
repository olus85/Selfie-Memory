package com.example.selfiememory.data.repository

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.selfiememory.data.local.SelfieDao
import com.example.selfiememory.data.local.SelfieEntity
import com.example.selfiememory.domain.model.Selfie
import com.example.selfiememory.domain.model.StorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import android.util.Base64

class SelfieRepository(
    private val context: Context,
    private val selfieDao: SelfieDao
) {
    private val mutex = Mutex()
    private val storageMutex = Mutex()

    companion object {
        private const val TAG = "SelfieRepository"
        private const val SELFIE_PREFIX = "selfie_"
        private const val SELFIE_EXTENSION = ".jpg"
        private const val MEDIA_FOLDER = "Pictures/Selfie Memory"
        private const val COPY_BUFFER_SIZE = 128 * 1024
    }

    fun getAllSelfies(): Flow<List<Selfie>> {
        return selfieDao.getAllSelfies().map { entities ->
            entities
                .filter {
                    !it.mediaUri.isNullOrBlank() ||
                        (it.filePath.isNotBlank() && File(it.filePath).isFile)
                }
                .map { it.toDomain() }
        }
    }

    fun getTrashed(): Flow<List<Selfie>> = selfieDao.getTrashed().map { list -> list.map { it.toDomain() } }

    suspend fun saveSelfie(imageBytes: ByteArray, latitude: Double?, longitude: Double?, storageMode: StorageMode = StorageMode.GALLERY): Selfie {
        return storageMutex.withLock {
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val fileName = buildFileName(timestamp)

                // MediaStore is the canonical store. A private file is only a safety
                // fallback for old devices where public storage cannot be written.
                if (storageMode == StorageMode.GALLERY) saveDirectlyToMediaStore(imageBytes, fileName, timestamp)?.let { mediaUri ->
                    val entity = SelfieEntity(
                        timestamp = timestamp,
                        filePath = "",
                        mediaUri = mediaUri,
                        latitude = latitude,
                        longitude = longitude
                    )
                    try {
                        val id = selfieDao.insert(entity).toInt()
                        return@withContext entity.copy(id = id).toDomain()
                    } catch (error: Exception) {
                        runCatching { context.contentResolver.delete(Uri.parse(mediaUri), null, null) }
                        throw error
                    }
                }

                val file = File(context.filesDir, fileName)
                try {
                    FileOutputStream(file).use { it.write(imageBytes) }
                    val entity = SelfieEntity(
                        timestamp = timestamp,
                        filePath = file.absolutePath,
                        latitude = latitude,
                        longitude = longitude
                    )
                    val id = selfieDao.insert(entity).toInt()
                    Log.w(TAG, "MediaStore unavailable; kept private safety copy ${file.name}")
                    entity.copy(id = id).toDomain()
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
            }
        }
    }

    suspend fun deleteSelfie(selfie: Selfie) {
        selfieDao.moveToTrash(selfie.id, System.currentTimeMillis())
    }

    suspend fun restoreSelfie(id: Int) = selfieDao.restore(id)
    suspend fun setFavorite(id: Int, favorite: Boolean) = selfieDao.setFavorite(id, favorite)
    suspend fun updateJournal(id: Int, note: String, tags: String) = selfieDao.updateJournal(id, note.trim(), tags.trim())

    suspend fun emptyExpiredTrash(days: Int = 30) {
        val before = System.currentTimeMillis() - days * 86_400_000L
        selfieDao.expiredTrash(before).forEach { permanentlyDelete(it.toDomain()) }
    }

    private suspend fun permanentlyDelete(selfie: Selfie) {
        withContext(Dispatchers.IO) {
            val file = selfie.filePath.takeIf { it.isNotBlank() }?.let(::File)
            if (file?.exists() == true && !file.delete()) {
                throw IllegalStateException("Failed to delete file: ${selfie.filePath}")
            }
            selfie.mediaUri?.let { uri ->
                val deleted = context.contentResolver.delete(Uri.parse(uri), null, null)
                if (deleted < 1) throw IllegalStateException("Galeriefoto konnte nicht gelöscht werden")
            }
            selfieDao.deleteById(selfie.id)
        }
    }

    suspend fun getCountSince(dayStart: Long): Int = selfieDao.getCountSince(dayStart)

    suspend fun getOldestSelfies(limit: Int): List<Selfie> = selfieDao.getOldestSelfies(limit).map { it.toDomain() }

    suspend fun enforceDailyLimit(limit: Int, dayStart: Long) {
        // A daily capture limit is a gate, never a retention policy.
        if (getCountSince(dayStart) > limit) Log.w(TAG, "Daily limit exceeded without deleting archive")
    }

    suspend fun exportBackup(target: Uri) = withContext(Dispatchers.IO) {
        val entries = selfieDao.getAllSelfies().first()
        context.contentResolver.openOutputStream(target, "w")!!.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("selfies.csv"))
                zip.write("id,timestamp,latitude,longitude,favorite,note64,tags64,file,rotationDegrees\n".toByteArray())
                entries.forEach { e ->
                    val name = "photos/${e.id}.jpg"
                    val line = listOf(e.id,e.timestamp,e.latitude?:"",e.longitude?:"",e.favorite,b64(e.note),b64(e.tags),name,e.rotationDegrees).joinToString(",")+"\n"
                    zip.write(line.toByteArray())
                }
                zip.closeEntry()
                entries.forEach { e ->
                    val name = "photos/${e.id}.jpg"
                    val input = e.mediaUri?.let { context.contentResolver.openInputStream(Uri.parse(it)) }
                        ?: e.filePath.takeIf(String::isNotBlank)?.let { File(it).takeIf(File::isFile)?.inputStream() }
                    input?.use {
                        zip.putNextEntry(ZipEntry(name)); it.copyTo(zip, COPY_BUFFER_SIZE); zip.closeEntry()
                    }
                }
            }
        }
    }

    suspend fun importBackup(source: Uri): Int = withContext(Dispatchers.IO) {
        data class Meta(val timestamp:Long,val lat:Double?,val lon:Double?,val favorite:Boolean,val note:String,val tags:String,val rotationDegrees:Int)
        val meta=mutableMapOf<String,Meta>();var imported=0
        context.contentResolver.openInputStream(source)!!.use { raw -> ZipInputStream(raw).use { zip ->
            while(true){val entry=zip.nextEntry?:break
                if(entry.name=="selfies.csv") zip.bufferedReader().readLines().drop(1).forEach{line->val p=line.split(',');if(p.size>=8)meta[p[7]]=Meta(p[1].toLong(),p[2].toDoubleOrNull(),p[3].toDoubleOrNull(),p[4].toBoolean(),unb64(p[5]),unb64(p[6]),p.getOrNull(8)?.toIntOrNull()?:0)}
                else meta[entry.name]?.let { m -> if(selfieDao.countAt(m.timestamp)==0){val file=File(context.filesDir,"restore_${m.timestamp}.jpg");file.outputStream().use{zip.copyTo(it,COPY_BUFFER_SIZE)};selfieDao.insert(SelfieEntity(timestamp=m.timestamp,filePath=file.absolutePath,latitude=m.lat,longitude=m.lon,favorite=m.favorite,note=m.note,tags=m.tags,rotationDegrees=m.rotationDegrees));imported++} }
                zip.closeEntry()
            }
        }}; imported
    }
    private fun b64(value:String)=Base64.encodeToString(value.toByteArray(),Base64.NO_WRAP)
    private fun unb64(value:String)=String(Base64.decode(value,Base64.NO_WRAP))

    /**
     * Recovers unindexed private files, publishes them to MediaStore, verifies
     * their bytes, and only then removes redundant app-private originals.
     * The operation is idempotent and safe to resume after process death.
     */
    suspend fun reconcileAndPublishPhotos() {
        storageMutex.withLock {
            withContext(Dispatchers.IO) {
                var entities = selfieDao.getAllSelfies().first()
                val dbPaths = entities.map { it.filePath }.filter { it.isNotBlank() }.toSet()
                val storageDir = context.filesDir
                var recovered = 0
                var migrated = 0
                var retained = 0

                storageDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(SELFIE_PREFIX) && file.name.endsWith(SELFIE_EXTENSION)) {
                        if (file.absolutePath !in dbPaths) {
                            Log.w(TAG, "Recovering unindexed photo: ${file.absolutePath}")
                            selfieDao.insert(
                                SelfieEntity(
                                    timestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                                    filePath = file.absolutePath,
                                    latitude = null,
                                    longitude = null
                                )
                            )
                            recovered++
                        }
                    }
                }

                entities = selfieDao.getAllSelfies().first()
                entities.forEach { entity ->
                    val source = entity.filePath.takeIf { it.isNotBlank() }?.let(::File)
                    if (source?.isFile != true) return@forEach

                    val verifiedExistingUri = entity.mediaUri
                        ?.takeIf { mediaCopyMatches(source, it) }
                    // publishToGallery verifies the completed copy before returning.
                    val mediaUri = verifiedExistingUri ?: publishToGallery(entity)
                    if (mediaUri == null) {
                        retained++
                        Log.e(TAG, "Keeping private original because MediaStore verification failed: ${source.name}")
                        return@forEach
                    }

                    if (entity.mediaUri != mediaUri) {
                        selfieDao.updateMediaUri(entity.id, mediaUri)
                        if (selfieDao.getById(entity.id)?.mediaUri != mediaUri) {
                            retained++
                            Log.e(TAG, "Keeping private original because database update failed: ${source.name}")
                            return@forEach
                        }
                    }

                    // Only app-private duplicates are automatically reclaimed.
                    if (isInsideAppFiles(source)) {
                        if (source.delete()) {
                            selfieDao.clearFilePath(entity.id)
                            migrated++
                        } else {
                            retained++
                            Log.e(TAG, "Verified gallery copy, but could not remove private duplicate: ${source.name}")
                        }
                    }
                }
                Log.i(TAG, "Storage reconciliation complete: recovered=$recovered, migrated=$migrated, retained=$retained")
            }
        }
    }

    private fun publishToGallery(entity: SelfieEntity): String? {
        val source = File(entity.filePath)
        if (!source.isFile) return null
        val resolver = context.contentResolver
        val displayName = source.name

        val existingItem = findMediaItem(displayName)
        existingItem?.let { existing ->
            if (mediaCopyMatches(source, existing)) return existing
        }

        val safeName = if (existingItem == null) {
            displayName
        } else {
            "${displayName.removeSuffix(SELFIE_EXTENSION)}_${entity.id}$SELFIE_EXTENSION"
        }
        val uri = createMediaItem(safeName, entity.timestamp) ?: return null
        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
            } ?: throw IllegalStateException("No MediaStore output stream")
            finishMediaItem(uri)
            if (!mediaCopyMatches(source, uri.toString())) {
                throw IllegalStateException("MediaStore byte verification failed")
            }
            uri.toString()
        } catch (error: Exception) {
            Log.e(TAG, "Publishing failed for $safeName", error)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun saveDirectlyToMediaStore(imageBytes: ByteArray, displayName: String, timestamp: Long): String? {
        val resolver = context.contentResolver
        val uri = createMediaItem(displayName, timestamp) ?: return null
        return try {
            resolver.openOutputStream(uri, "w")?.use { it.write(imageBytes) }
                ?: throw IllegalStateException("No MediaStore output stream")
            finishMediaItem(uri)
            val expected = MessageDigest.getInstance("SHA-256").digest(imageBytes)
            val actual = resolver.openInputStream(uri)?.use(::sha256)
                ?: throw IllegalStateException("Cannot verify MediaStore image")
            if (!expected.contentEquals(actual)) throw IllegalStateException("MediaStore byte verification failed")
            uri.toString()
        } catch (error: Exception) {
            Log.e(TAG, "Direct MediaStore save failed for $displayName", error)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun findMediaItem(displayName: String): String? {
        val resolver = context.contentResolver
        val collection = mediaCollection()
        return runCatching {
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?"
            } else {
                "${MediaStore.Images.Media.DISPLAY_NAME}=?"
            }
            val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(displayName, "$MEDIA_FOLDER/")
            } else arrayOf(displayName)
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Uri.withAppendedPath(collection, cursor.getLong(0).toString()).toString()
                } else null
            }
        }.getOrNull()
    }

    private fun createMediaItem(displayName: String, timestamp: Long): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, MEDIA_FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return runCatching { context.contentResolver.insert(mediaCollection(), values) }
            .onFailure { Log.e(TAG, "Could not create gallery item for $displayName", it) }
            .getOrNull()
    }

    private fun finishMediaItem(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        }
    }

    private fun mediaCopyMatches(source: File, uriString: String): Boolean = runCatching {
        if (!source.isFile) return@runCatching false
        val mediaHash = context.contentResolver.openInputStream(Uri.parse(uriString))?.use(::sha256)
            ?: return@runCatching false
        source.inputStream().use(::sha256).contentEquals(mediaHash)
    }.getOrElse {
        Log.w(TAG, "Cannot verify gallery item $uriString", it)
        false
    }

    private fun sha256(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun mediaCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun isInsideAppFiles(file: File): Boolean = runCatching {
        val root = context.filesDir.canonicalFile
        val candidate = file.canonicalFile
        candidate.parentFile == root
    }.getOrDefault(false)

    private fun buildFileName(timestamp: Long): String =
        "selfie_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))}$SELFIE_EXTENSION"

    private fun SelfieEntity.toDomain() = Selfie(
        id = id,
        timestamp = timestamp,
        filePath = filePath,
        mediaUri = mediaUri,
        latitude = latitude,
        longitude = longitude
        ,favorite = favorite,
        note = note,
        tags = tags,
        trashedAt = trashedAt,
        rotationDegrees = rotationDegrees
    )
}
