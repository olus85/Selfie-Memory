package com.example.selfiememory.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.data.repository.SettingsRepository
import com.example.selfiememory.domain.model.*
import com.example.selfiememory.service.NetworkMonitor
import com.example.selfiememory.service.SelfieCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class SettingsViewModel @Inject constructor(app:Application,private val repo:SettingsRepository,private val network:NetworkMonitor,private val photos:SelfieRepository):AndroidViewModel(app){
    val settings=repo.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),Settings())
    private val _ssids=MutableStateFlow<List<String>>(emptyList());val availableSsids:StateFlow<List<String>> = _ssids
    private val _message=MutableStateFlow<String?>(null);val message:StateFlow<String?> = _message
    init{refreshAvailableSsids()}
    private fun launch(block:suspend()->Unit)=viewModelScope.launch{block()}
    fun refreshAvailableSsids()=launch{_ssids.value=network.getAvailableSsids()}
    fun enabled(v:Boolean)=launch{repo.setEnabled(v);if(v)start()else stop()}
    fun pause(ms:Long)=launch{repo.setPausedUntil(if(ms==0L)0L else System.currentTimeMillis()+ms);if(ms==0L)start()else stop()}
    fun network(v:NetworkMode)=launch{repo.setNetworkMode(v)};fun ssids(v:Set<String>)=launch{repo.setAllowedSsids(v)}
    fun camera(v:CameraType)=launch{repo.setCameraType(v)};fun delay(v:Int)=launch{repo.setCaptureDelay(v)};fun cooldown(v:Int)=launch{repo.setCooldownMinutes(v)};fun limit(v:Int)=launch{repo.setDailyLimit(v)}
    fun time(s:Int,e:Int)=launch{repo.setTimeWindow(s,e)};fun weekdays(v:Int)=launch{repo.setWeekdays(v)};fun battery(v:Int)=launch{repo.setMinBattery(v)};fun charging(v:Boolean)=launch{repo.setChargingOnly(v)}
    fun pocket(v:Boolean)=launch{repo.setPocket(v)};fun quality(v:QualityMode)=launch{repo.setQuality(v)};fun storage(v:StorageMode)=launch{repo.setStorage(v)};fun location(v:LocationMode)=launch{repo.setLocation(v)};fun mirror(v:Boolean)=launch{repo.setMirror(v)}
    fun appLock(v:Boolean)=launch{repo.setAppLock(v)}
    fun testPhoto()=start(SelfieCaptureService.ACTION_CAPTURE_NOW)
    fun exportBackup(uri:Uri)=launch{runCatching{photos.exportBackup(uri)}.onSuccess{_message.value="Backup gespeichert"}.onFailure{_message.value="Backup fehlgeschlagen: ${it.message}"}}
    fun importBackup(uri:Uri)=launch{runCatching{photos.importBackup(uri)}.onSuccess{_message.value="$it Erinnerungen wiederhergestellt"}.onFailure{_message.value="Wiederherstellung fehlgeschlagen: ${it.message}"}}
    fun clearMessage(){_message.value=null}
    private fun start(action:String=SelfieCaptureService.ACTION_START){ContextCompat.startForegroundService(getApplication(),Intent(getApplication(),SelfieCaptureService::class.java).setAction(action))}
    private fun stop(){getApplication<Application>().startService(Intent(getApplication(),SelfieCaptureService::class.java).setAction(SelfieCaptureService.ACTION_STOP))}
}
