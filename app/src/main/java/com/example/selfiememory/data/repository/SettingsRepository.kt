package com.example.selfiememory.data.repository

import com.example.selfiememory.data.local.SettingsDataStore
import com.example.selfiememory.domain.model.CameraType
import com.example.selfiememory.domain.model.NetworkMode
import com.example.selfiememory.domain.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SettingsRepository(private val dataStore: SettingsDataStore) {

    val settings: Flow<Settings> = combine(
        dataStore.networkModeFlow,
        dataStore.specificSsid,
        dataStore.cameraTypeFlow,
        combine(
            dataStore.captureDelay,
            dataStore.cooldownMinutes,
            dataStore.dailyLimit
        ) { delay, cooldown, limit -> Triple(delay, cooldown, limit) }
    ) { networkMode, ssid, cameraType, extras ->
        val (delay, cooldown, limit) = extras
        Settings(
            networkMode = runCatching { NetworkMode.valueOf(networkMode) }.getOrDefault(NetworkMode.CELLULAR),
            specificSsid = ssid,
            cameraType = runCatching { CameraType.valueOf(cameraType) }.getOrDefault(CameraType.FRONT_ULTRA_WIDE),
            captureDelaySeconds = delay,
            cooldownMinutes = cooldown,
            dailyLimit = limit
        )
    }

    val lastCaptureTime: Flow<Long> = dataStore.lastCaptureTime

    suspend fun setNetworkMode(mode: NetworkMode) { dataStore.setNetworkMode(mode.name) }
    suspend fun setSpecificSsid(ssid: String) { dataStore.setSpecificSsid(ssid) }
    suspend fun setCameraType(type: CameraType) { dataStore.setCameraType(type.name) }
    suspend fun setCaptureDelay(delay: Int) { dataStore.setCaptureDelay(delay) }
    suspend fun setCooldownMinutes(minutes: Int) { dataStore.setCooldownMinutes(minutes) }
    suspend fun setDailyLimit(limit: Int) { dataStore.setDailyLimit(limit) }
    suspend fun setLastCaptureTime(time: Long) { dataStore.setLastCaptureTime(time) }
}
