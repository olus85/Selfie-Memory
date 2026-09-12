package com.example.selfiememory.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SelfieDao {
    @Query("SELECT * FROM selfies WHERE trashedAt IS NULL ORDER BY timestamp DESC")
    fun getAllSelfies(): Flow<List<SelfieEntity>>

    @Query("SELECT * FROM selfies WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun getTrashed(): Flow<List<SelfieEntity>>

    @Query("SELECT * FROM selfies ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestSelfies(limit: Int): List<SelfieEntity>

    @Query("SELECT COUNT(*) FROM selfies WHERE timestamp >= :dayStart")
    suspend fun getCountSince(dayStart: Long): Int

    @Query("DELETE FROM selfies WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selfie: SelfieEntity): Long

    @Delete
    suspend fun delete(selfie: SelfieEntity)

    @Query("SELECT * FROM selfies WHERE id = :id")
    suspend fun getById(id: Int): SelfieEntity?

    @Query("SELECT COUNT(*) FROM selfies WHERE timestamp = :timestamp")
    suspend fun countAt(timestamp: Long): Int

    @Query("UPDATE selfies SET mediaUri = :mediaUri WHERE id = :id")
    suspend fun updateMediaUri(id: Int, mediaUri: String)

    @Query("UPDATE selfies SET filePath = '' WHERE id = :id")
    suspend fun clearFilePath(id: Int)

    @Query("UPDATE selfies SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Int, favorite: Boolean)

    @Query("UPDATE selfies SET note = :note, tags = :tags WHERE id = :id")
    suspend fun updateJournal(id: Int, note: String, tags: String)

    @Query("UPDATE selfies SET trashedAt = :time WHERE id = :id")
    suspend fun moveToTrash(id: Int, time: Long)

    @Query("UPDATE selfies SET trashedAt = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("SELECT * FROM selfies WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun expiredTrash(before: Long): List<SelfieEntity>
}
