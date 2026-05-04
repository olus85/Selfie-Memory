package com.example.selfiememory.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val NETWORK_MODE = stringPreferencesKey("network_mode")
        val SPECIFIC_SSID = stringPreferencesKey("specific_ssid")
        val CAMERA_TYPE = stringPreferencesKey("camera_type")
        val CAPTURE_DELAY = intPreferencesKey("capture_delay")
        val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val DAILY_LIMIT = intPreferencesKey("daily_limit")
        val LAST_CAPTURE_TIME = longPreferencesKey("last_capture_time")
    }

    private fun <T> Flow<Preferences>.safeMap(transform: (Preferences) -> T): Flow<T> = this
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(transform)

    val networkMode: Flow<String> = context.dataStore.data.safeMap { it[NETWORK_MODE] ?: "CELLULAR" }
    val specificSsid: Flow<String> = context.dataStore.data.safeMap { it[SPECIFIC_SSID] ?: "" }
    val cameraType: Flow<String> = context.dataStore.data.safeMap { it[CAMERA_TYPE] ?: "FRONT_ULTRA_WIDE" }
    val captureDelay: Flow<Int> = context.dataStore.data.safeMap { it[CAPTURE_DELAY] ?: 3 }
    val cooldownMinutes: Flow<Int> = context.dataStore.data.safeMap { it[COOLDOWN_MINUTES] ?: 10 }
    val dailyLimit: Flow<Int> = context.dataStore.data.safeMap { it[DAILY_LIMIT] ?: 10 }
    val lastCaptureTime: Flow<Long> = context.dataStore.data.safeMap { it[LAST_CAPTURE_TIME] ?: 0L }

    suspend fun setNetworkMode(mode: String) { context.dataStore.edit { it[NETWORK_MODE] = mode } }
    suspend fun setSpecificSsid(ssid: String) { context.dataStore.edit { it[SPECIFIC_SSID] = ssid } }
    suspend fun setCameraType(type: String) { context.dataStore.edit { it[CAMERA_TYPE] = type } }
    suspend fun setCaptureDelay(delay: Int) { context.dataStore.edit { it[CAPTURE_DELAY] = delay } }
    suspend fun setCooldownMinutes(minutes: Int) { context.dataStore.edit { it[COOLDOWN_MINUTES] = minutes } }
    suspend fun setDailyLimit(limit: Int) { context.dataStore.edit { it[DAILY_LIMIT] = limit } }
    suspend fun setLastCaptureTime(time: Long) { context.dataStore.edit { it[LAST_CAPTURE_TIME] = time } }
}