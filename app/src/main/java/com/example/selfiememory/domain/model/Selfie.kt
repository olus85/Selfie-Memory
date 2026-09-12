package com.example.selfiememory.domain.model

data class Selfie(
    val id: Int,
    val timestamp: Long,
    val filePath: String,
    val mediaUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val favorite: Boolean = false,
    val note: String = "",
    val tags: String = "",
    val trashedAt: Long? = null
)
