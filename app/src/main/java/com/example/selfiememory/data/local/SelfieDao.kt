package com.example.selfiememory.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SelfieDao {
    @Query("SELECT * FROM selfies ORDER BY timestamp DESC")
    fun getAllSelfies(): Flow<List<SelfieEntity>>

    @Query("SELECT * FROM selfies ORDER BY timestamp DESC LIMIT :limit")
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
}