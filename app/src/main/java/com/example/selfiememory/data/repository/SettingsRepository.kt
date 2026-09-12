package com.example.selfiememory.data.repository

import com.example.selfiememory.data.local.SettingsDataStore
import com.example.selfiememory.domain.model.*

class SettingsRepository(private val store: SettingsDataStore) {
    val settings=store.settings; val lastCaptureTime=store.lastCaptureTime
    suspend fun setEnabled(v:Boolean)=store.setEnabled(v); suspend fun setPausedUntil(v:Long)=store.setPausedUntil(v)
    suspend fun setNetworkMode(v:NetworkMode)=store.setNetworkMode(v.name); suspend fun setSpecificSsid(v:String)=store.setSpecificSsid(v)
    suspend fun setAllowedSsids(v:Set<String>)=store.setAllowedSsids(v); suspend fun setCameraType(v:CameraType)=store.setCameraType(v.name)
    suspend fun setCaptureDelay(v:Int)=store.setCaptureDelay(v); suspend fun setCooldownMinutes(v:Int)=store.setCooldownMinutes(v)
    suspend fun setDailyLimit(v:Int)=store.setDailyLimit(v); suspend fun setTimeWindow(s:Int,e:Int)=store.setTimeWindow(s,e)
    suspend fun setWeekdays(v:Int)=store.setWeekdays(v); suspend fun setMinBattery(v:Int)=store.setMinBattery(v); suspend fun setChargingOnly(v:Boolean)=store.setChargingOnly(v)
    suspend fun setPocket(v:Boolean)=store.setPocket(v); suspend fun setQuality(v:QualityMode)=store.setQuality(v.name); suspend fun setStorage(v:StorageMode)=store.setStorage(v.name)
    suspend fun setLocation(v:LocationMode)=store.setLocation(v.name); suspend fun setMirror(v:Boolean)=store.setMirror(v)
    suspend fun setAppLock(v:Boolean)=store.setAppLock(v)
    suspend fun setLastCaptureTime(v:Long)=store.setLastCaptureTime(v); suspend fun setStatus(v:String)=store.setStatus(v)
}
