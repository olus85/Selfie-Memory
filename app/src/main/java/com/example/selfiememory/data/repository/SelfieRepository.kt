package com.example.selfiememory.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.selfiememory.data.local.SelfieDao
import com.example.selfiememory.data.local.SelfieEntity
import com.example.selfiememory.domain.model.Selfie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SelfieRepository(
    private val context: Context,
    private val selfieDao: SelfieDao
) {
    private val mutex = Mutex()

    companion object {
        private const val TAG = "SelfieRepository"
        private const val SELFIE_PREFIX = "selfie_"
        private const val SELFIE_EXTENSION = ".jpg"
    }

    fun getAllSelfies(): Flow<List<Selfie>> {
        return selfieDao.getAllSelfies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveSelfie(imageBytes: ByteArray, latitude: Double?, longitude: Double?): Selfie {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val fileName = "selfie_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))}.jpg"
            val file = File(context.filesDir, fileName)

            try {
                FileOutputStream(file).use { fos ->
                    fos.write(imageBytes)
                }

                val entity = SelfieEntity(
                    timestamp = timestamp,
                    filePath = file.absolutePath,
                    latitude = latitude,
                    longitude = longitude
                )

                val id = selfieDao.insert(entity).toInt()
                entity.copy(id = id).toDomain()
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    }

    suspend fun deleteSelfie(selfie: Selfie) {
        withContext(Dispatchers.IO) {
            val file = File(selfie.filePath)
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("Failed to delete file: ${selfie.filePath}")
            }
            selfieDao.deleteById(selfie.id)
        }
    }

    suspend fun getCountSince(dayStart: Long): Int = selfieDao.getCountSince(dayStart)

    suspend fun getOldestSelfies(limit: Int): List<Selfie> = selfieDao.getOldestSelfies(limit).map { it.toDomain() }

    suspend fun enforceDailyLimit(limit: Int, dayStart: Long) {
        mutex.withLock {
            val count = getCountSince(dayStart)
            if (count > limit) {
                val toDelete = count - limit
                val oldest = getOldestSelfies(toDelete)
                withContext(Dispatchers.IO) {
                    oldest.forEach { selfie ->
                        try {
                            val file = File(selfie.filePath)
                            if (file.exists() && !file.delete()) {
                                throw IllegalStateException("Failed to delete file: ${selfie.filePath}")
                            }
                            selfieDao.deleteById(selfie.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete selfie during enforceDailyLimit: ${selfie.id}", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Cleans up orphaned files that exist in the storage directory but are not
     * referenced in the database. Call this at app startup.
     */
    suspend fun cleanupOrphanedFiles() {
        withContext(Dispatchers.IO) {
            val dbPaths = selfieDao.getAllSelfies().first().map { it.filePath }.toSet()
            val storageDir = context.filesDir

            storageDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(SELFIE_PREFIX) && file.name.endsWith(SELFIE_EXTENSION)) {
                    if (file.absolutePath !in dbPaths) {
                        Log.w(TAG, "Found orphaned file, deleting: ${file.absolutePath}")
                        if (!file.delete()) {
                            Log.e(TAG, "Failed to delete orphaned file: ${file.absolutePath}")
                        }
                    }
                }
            }
        }
    }

    private fun SelfieEntity.toDomain() = Selfie(
        id = id,
        timestamp = timestamp,
        filePath = filePath,
        latitude = latitude,
        longitude = longitude
    )
}