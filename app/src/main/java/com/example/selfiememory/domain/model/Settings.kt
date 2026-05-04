package com.example.selfiememory.domain.model

data class Settings(
    val networkMode: NetworkMode = NetworkMode.CELLULAR,
    val specificSsid: String = "",
    val cameraType: CameraType = CameraType.FRONT_ULTRA_WIDE,
    val captureDelaySeconds: Int = 3,
    val cooldownMinutes: Int = 10,
    val dailyLimit: Int = 10
)

enum class NetworkMode { CELLULAR, ANY_WLAN, SPECIFIC_WLAN }
enum class CameraType { FRONT_ULTRA_WIDE, FRONT_NORMAL, BACK }