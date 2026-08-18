package com.example.selfiememory

import android.app.Application
import com.example.selfiememory.data.repository.SelfieRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SelfieMemoryApp : Application() {

    @Inject
    lateinit var selfieRepository: SelfieRepository

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            selfieRepository.reconcileAndPublishPhotos()
        }
    }
}
