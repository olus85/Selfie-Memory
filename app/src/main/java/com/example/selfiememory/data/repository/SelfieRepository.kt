package com.example.selfiememory.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.selfiememory.data.local.SelfieDao
import com.example.selfiememory.data.local.SelfieEntity
import com.example.selfiememory.domain.model.Selfie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SelfieRepository(
    private val context: Context,
    private val selfieDao: SelfieDao
) {
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
        }
    }

    suspend fun deleteSelfie(selfie: Selfie) {
        withContext(Dispatchers.IO) {
            // Delete file
            val file = File(selfie.filePath)
            if (file.exists()) file.delete()
            // Delete DB entry
            selfieDao.deleteById(selfie.id)
        }
    }

    suspend fun getCountSince(dayStart: Long): Int = selfieDao.getCountSince(dayStart)

    suspend fun getOldestSelfies(limit: Int): List<Selfie> = selfieDao.getOldestSelfies(limit).map { it.toDomain() }

    suspend fun enforceDailyLimit(limit: Int, dayStart: Long) {
        val count = getCountSince(dayStart)
        if (count > limit) {
            val toDelete = count - limit
            val oldest = getOldestSelfies(toDelete)
            oldest.forEach { deleteSelfie(it) }
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