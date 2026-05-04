package com.example.selfiememory.ui.settings

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.selfiememory.data.repository.SettingsRepository
import com.example.selfiememory.domain.model.CameraType
import com.example.selfiememory.domain.model.NetworkMode
import com.example.selfiememory.domain.model.Settings
import com.example.selfiememory.service.SelfieCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<Settings> = MutableStateFlow(Settings())
    private var serviceStartJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                (settings as MutableStateFlow).value = s
                if (s.networkMode != NetworkMode.SPECIFIC_WLAN || s.specificSsid.isNotBlank()) {
                    startServiceIfNotRunning()
                }
            }
        }
    }

    private fun startServiceIfNotRunning() {
        val context = getApplication<Application>()
        val activityManager = context.getSystemService(Application.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        val isServiceRunning = runningServices.any { it.service.className == SelfieCaptureService::class.java.name }

        if (!isServiceRunning) {
            startService()
        }
    }

    private fun startService() {
        val context = getApplication<Application>()
        val intent = Intent(context, SelfieCaptureService::class.java).apply {
            action = SelfieCaptureService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun setNetworkMode(mode: NetworkMode) {
        viewModelScope.launch {
            settingsRepository.setNetworkMode(mode)
        }
    }

    fun setSpecificSsid(ssid: String) {
        viewModelScope.launch {
            settingsRepository.setSpecificSsid(ssid)
        }
    }

    fun setCameraType(type: CameraType) {
        viewModelScope.launch {
            settingsRepository.setCameraType(type)
        }
    }

    fun setCaptureDelay(delay: Int) {
        viewModelScope.launch {
            settingsRepository.setCaptureDelay(delay)
        }
    }

    fun setCooldownMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setCooldownMinutes(minutes)
        }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyLimit(limit)
        }
    }
}
