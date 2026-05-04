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

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                (settings as MutableStateFlow).value = s
                // For ANY_WLAN or CELLULAR mode, start the service
                // For SPECIFIC_WLAN, we need to check SSID match - service handles this
                if (s.networkMode != NetworkMode.SPECIFIC_WLAN || s.specificSsid.isNotBlank()) {
                    startService()
                }
            }
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
