package com.example.selfiememory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "selfies")
data class SelfieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val filePath: String,
    val mediaUri: String? = null,
    val latitude: Double?,
    val longitude: Double?
)
