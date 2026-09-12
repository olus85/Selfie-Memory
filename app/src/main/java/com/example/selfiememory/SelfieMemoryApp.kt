package com.example.selfiememory

import android.app.Application
import com.example.selfiememory.data.repository.SelfieRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.example.selfiememory.data.repository.SettingsRepository
import com.example.selfiememory.domain.model.StorageMode
import javax.inject.Inject

@HiltAndroidApp
class SelfieMemoryApp : Application() {

    @Inject
    lateinit var selfieRepository: SelfieRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            if (settingsRepository.settings.first().storageMode == StorageMode.GALLERY) {
                selfieRepository.reconcileAndPublishPhotos()
            }
            selfieRepository.emptyExpiredTrash()
        }
    }
}
