package com.example.selfiememory.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.selfiememory.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private object K {
        val enabled = booleanPreferencesKey("enabled"); val pausedUntil = longPreferencesKey("paused_until")
        val networkMode = stringPreferencesKey("network_mode"); val ssid = stringPreferencesKey("specific_ssid")
        val allowedSsids = stringSetPreferencesKey("allowed_ssids"); val camera = stringPreferencesKey("camera_type")
        val delay = intPreferencesKey("capture_delay"); val cooldown = intPreferencesKey("cooldown_minutes")
        val limit = intPreferencesKey("daily_limit"); val lastCapture = longPreferencesKey("last_capture_time")
        val startHour = intPreferencesKey("start_hour"); val endHour = intPreferencesKey("end_hour")
        val weekdays = intPreferencesKey("weekdays_mask"); val minBattery = intPreferencesKey("min_battery")
        val chargingOnly = booleanPreferencesKey("charging_only"); val pocket = booleanPreferencesKey("pocket_protection")
        val quality = stringPreferencesKey("quality_mode"); val storage = stringPreferencesKey("storage_mode")
        val location = stringPreferencesKey("location_mode"); val mirror = booleanPreferencesKey("mirror_front"); val appLock = booleanPreferencesKey("app_lock")
        val lastStatus = stringPreferencesKey("last_status"); val lastStatusTime = longPreferencesKey("last_status_time")
    }
    private val safeData = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val settings: Flow<Settings> = safeData.map { p ->
        val legacySsid = p[K.ssid].orEmpty()
        Settings(
            enabled = p[K.enabled] ?: true, pausedUntil = p[K.pausedUntil] ?: 0L,
            networkMode = enumValue(p[K.networkMode], NetworkMode.CELLULAR), specificSsid = legacySsid,
            allowedSsids = p[K.allowedSsids] ?: legacySsid.takeIf(String::isNotBlank)?.let(::setOf).orEmpty(),
            cameraType = enumValue(p[K.camera], CameraType.FRONT_ULTRA_WIDE),
            captureDelaySeconds = (p[K.delay] ?: 2).coerceIn(0, 15), cooldownMinutes = (p[K.cooldown] ?: 10).coerceIn(0, 240),
            dailyLimit = (p[K.limit] ?: 15).coerceIn(1, 100), startHour = (p[K.startHour] ?: 0).coerceIn(0, 23),
            endHour = (p[K.endHour] ?: 24).coerceIn(1, 24), weekdaysMask = p[K.weekdays] ?: 0x7f,
            minBatteryPercent = (p[K.minBattery] ?: 10).coerceIn(0, 90), chargingOnly = p[K.chargingOnly] ?: false,
            pocketProtection = p[K.pocket] ?: true, qualityMode = enumValue(p[K.quality], QualityMode.WARN),
            storageMode = enumValue(p[K.storage], StorageMode.GALLERY), locationMode = enumValue(p[K.location], LocationMode.EXACT),
            mirrorFrontCamera = p[K.mirror] ?: true, appLockEnabled = p[K.appLock] ?: false, lastStatus = p[K.lastStatus] ?: "Noch keine Aufnahme versucht",
            lastStatusTime = p[K.lastStatusTime] ?: 0L
        )
    }
    val lastCaptureTime: Flow<Long> = safeData.map { it[K.lastCapture] ?: 0L }
    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    private suspend fun change(block: MutablePreferences.() -> Unit) { context.dataStore.edit(block) }
    suspend fun setEnabled(v:Boolean)=change{this[K.enabled]=v}; suspend fun setPausedUntil(v:Long)=change{this[K.pausedUntil]=v}
    suspend fun setNetworkMode(v:String)=change{this[K.networkMode]=v}; suspend fun setSpecificSsid(v:String)=change{this[K.ssid]=v;this[K.allowedSsids]=setOf(v).filter(String::isNotBlank).toSet()}
    suspend fun setAllowedSsids(v:Set<String>)=change{this[K.allowedSsids]=v;this[K.ssid]=v.firstOrNull().orEmpty()}
    suspend fun setCameraType(v:String)=change{this[K.camera]=v}; suspend fun setCaptureDelay(v:Int)=change{this[K.delay]=v.coerceIn(0,15)}
    suspend fun setCooldownMinutes(v:Int)=change{this[K.cooldown]=v.coerceIn(0,240)}; suspend fun setDailyLimit(v:Int)=change{this[K.limit]=v.coerceIn(1,100)}
    suspend fun setTimeWindow(start:Int,end:Int)=change{this[K.startHour]=start;this[K.endHour]=end}; suspend fun setWeekdays(v:Int)=change{this[K.weekdays]=v}
    suspend fun setMinBattery(v:Int)=change{this[K.minBattery]=v.coerceIn(0,90)}; suspend fun setChargingOnly(v:Boolean)=change{this[K.chargingOnly]=v}
    suspend fun setPocket(v:Boolean)=change{this[K.pocket]=v}; suspend fun setQuality(v:String)=change{this[K.quality]=v}
    suspend fun setStorage(v:String)=change{this[K.storage]=v}; suspend fun setLocation(v:String)=change{this[K.location]=v}; suspend fun setMirror(v:Boolean)=change{this[K.mirror]=v}
    suspend fun setAppLock(v:Boolean)=change{this[K.appLock]=v}
    suspend fun setLastCaptureTime(v:Long)=change{this[K.lastCapture]=v}; suspend fun setStatus(v:String)=change{this[K.lastStatus]=v;this[K.lastStatusTime]=System.currentTimeMillis()}
}
