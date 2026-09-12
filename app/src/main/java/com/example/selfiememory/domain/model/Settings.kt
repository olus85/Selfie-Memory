package com.example.selfiememory.domain.model

data class Settings(
    val enabled: Boolean = true,
    val pausedUntil: Long = 0L,
    val networkMode: NetworkMode = NetworkMode.ANY,
    val specificSsid: String = "",
    val allowedSsids: Set<String> = emptySet(),
    val cameraType: CameraType = CameraType.FRONT_ULTRA_WIDE,
    val captureDelaySeconds: Int = 2,
    val cooldownMinutes: Int = 10,
    val dailyLimit: Int = 15,
    val startHour: Int = 0,
    val endHour: Int = 24,
    val weekdaysMask: Int = 0x7f,
    val minBatteryPercent: Int = 10,
    val chargingOnly: Boolean = false,
    val pocketProtection: Boolean = true,
    val qualityMode: QualityMode = QualityMode.WARN,
    val storageMode: StorageMode = StorageMode.GALLERY,
    val locationMode: LocationMode = LocationMode.EXACT,
    val mirrorFrontCamera: Boolean = true,
    val appLockEnabled: Boolean = false,
    val lastStatus: String = "Noch keine Aufnahme versucht",
    val lastStatusTime: Long = 0L
)

enum class NetworkMode { ANY, CELLULAR, ANY_WLAN, SPECIFIC_WLAN }
enum class CameraType { FRONT_ULTRA_WIDE, FRONT_NORMAL, BACK }
enum class QualityMode { OFF, WARN, STRICT }
enum class StorageMode { PRIVATE, GALLERY }
enum class LocationMode { OFF, APPROXIMATE, EXACT }
