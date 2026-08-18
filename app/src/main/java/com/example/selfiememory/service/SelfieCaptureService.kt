package com.example.selfiememory.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.selfiememory.MainActivity
import com.example.selfiememory.R
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.data.repository.SettingsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class SelfieCaptureService : LifecycleService() {
    companion object {
        private const val TAG = "SelfieCaptureService"
        private const val CHANNEL_ID = "selfie_capture_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.selfiememory.START_CAPTURE_SERVICE"
        const val ACTION_STOP = "com.example.selfiememory.STOP_CAPTURE_SERVICE"
    }

    @Inject lateinit var selfieRepository: SelfieRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var cameraCapturer: CameraCapturer
    @Inject lateinit var pocketDetector: PocketDetector
    @Inject lateinit var imageQualityAnalyzer: ImageQualityAnalyzer

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userPresentReceiver: BroadcastReceiver? = null
    private var captureJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasCameraPermission()) {
            Log.w(TAG, "Camera permission missing; service will not start")
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            startForegroundSafely()
            registerUnlockReceiver()
            START_NOT_STICKY
        } catch (error: SecurityException) {
            // Android 14+ rejects camera FGS starts from disallowed background states.
            Log.e(TAG, "Camera foreground service start rejected", error)
            stopSelf()
            START_NOT_STICKY
        }
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundSafely() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.capture_notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SelfieCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .build()
    }

    private fun registerUnlockReceiver() {
        if (userPresentReceiver != null) return
        userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) captureAfterUnlock()
            }
        }
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "Unlock monitoring active")
    }

    private fun captureAfterUnlock() {
        if (captureJob?.isActive == true) return
        captureJob = lifecycleScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!hasCameraPermission()) return@launch
                if (!networkMonitor.isConditionMetPublic(settings.networkMode, settings.specificSsid)) return@launch

                val now = System.currentTimeMillis()
                val cooldown = settings.cooldownMinutes * 60_000L
                val lastCapture = settingsRepository.lastCaptureTime.first()
                if (lastCapture > 0 && now - lastCapture < cooldown) return@launch
                val dayStart = getDayStartMillis()
                if (selfieRepository.getCountSince(dayStart) >= settings.dailyLimit) return@launch

                delay(settings.captureDelaySeconds * 1_000L)
                // Conditions can change during the delay; do not photograph a pocket or wrong network.
                if (!networkMonitor.isConditionMetPublic(settings.networkMode, settings.specificSsid)) return@launch
                if (pocketDetector.isLikelyInPocket()) {
                    Log.i(TAG, "Capture suppressed: proximity + darkness indicate pocket")
                    return@launch
                }

                val jpeg = cameraCapturer.captureImage(this@SelfieCaptureService, settings.cameraType)
                val quality = imageQualityAnalyzer.analyze(jpeg)
                if (!quality.accepted) {
                    Log.i(TAG, "Discarded unusably dark frame")
                    return@launch
                }

                val location = withTimeoutOrNull(2_500L) { getCurrentLocation() }
                val selfie = selfieRepository.saveSelfie(jpeg, location?.latitude, location?.longitude)
                settingsRepository.setLastCaptureTime(selfie.timestamp)
                selfieRepository.enforceDailyLimit(settings.dailyLimit, dayStart)
                Log.i(TAG, "Saved and published selfie ${selfie.id}")
            } catch (error: Exception) {
                Log.e(TAG, "Capture failed without stopping monitoring", error)
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val token = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
            continuation.invokeOnCancellation { token.cancel() }
        }
    }

    private fun getDayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    override fun onDestroy() {
        captureJob?.cancel()
        userPresentReceiver?.let { runCatching { unregisterReceiver(it) } }
        userPresentReceiver = null
        cameraCapturer.shutdown()
        super.onDestroy()
    }
}
