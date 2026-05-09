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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import java.util.*
import javax.inject.Inject

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

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userPresentReceiver: BroadcastReceiver? = null
    private var captureJob: Job? = null
    private val isMonitoring = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithProperType()
                startUserPresentMonitoring()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithProperType() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ requires explicit foreground service type
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.capture_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Selfie capture service - runs when device is unlocked"
            setShowBadge(false)
            setBypassDnd(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(false)
            }
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SelfieCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startUserPresentMonitoring() {
        if (!isMonitoring.compareAndSet(false, true)) return

        if (!checkPermissions()) {
            Log.w(TAG, "Required permissions not granted, skipping USER_PRESENT monitoring")
            isMonitoring.set(false)
            return
        }

        Log.i(TAG, "Starting USER_PRESENT monitoring")

        userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "USER_PRESENT received, checking conditions...")
                checkAndCapture()
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)

        registerReceiver(userPresentReceiver, filter)
    }

    private fun stopUserPresentMonitoring() {
        userPresentReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Receiver not registered", e)
            }
        }
        userPresentReceiver = null
        isMonitoring.set(false)
        Log.i(TAG, "Stopped USER_PRESENT monitoring")
    }

    private fun checkAndCapture() {
        captureJob?.cancel()
        captureJob = lifecycleScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val dayStart = getDayStartMillis()

                if (!checkPermissions()) {
                    Log.w(TAG, "Required permissions not granted, skipping capture")
                    return@launch
                }

                val networkConditionMet = try {
                    networkMonitor.isConditionMetPublic(settings.networkMode, settings.specificSsid)
                } catch (e: Exception) {
                    Log.w(TAG, "Network condition check failed", e)
                    false
                }

                if (!networkConditionMet) {
                    Log.i(TAG, "Network condition not met, skipping capture")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val lastCapture = settingsRepository.lastCaptureTime.first()
                val cooldownMillis = settings.cooldownMinutes * 60 * 1000L
                if (lastCapture > 0 && now - lastCapture < cooldownMillis) {
                    Log.i(TAG, "Cooldown active (${(cooldownMillis - (now - lastCapture)) / 1000}s left), skipping capture")
                    return@launch
                }

                val countToday = selfieRepository.getCountSince(dayStart)
                if (countToday >= settings.dailyLimit) {
                    Log.i(TAG, "Daily limit reached ($countToday), skipping")
                    return@launch
                }

                Log.i(TAG, "Waiting ${settings.captureDelaySeconds}s before capture")
                delay(settings.captureDelaySeconds * 1000L)

                val captureTime = System.currentTimeMillis()
                Log.i(TAG, "Starting capture with camera ${settings.cameraType}")
                val imageBytes = cameraCapturer.captureImage(this@SelfieCaptureService, settings.cameraType)

                val location = getCurrentLocation()
                val lat = location?.latitude
                val lon = location?.longitude
                Log.i(TAG, "Location: $lat, $lon")

                val selfie = selfieRepository.saveSelfie(imageBytes, lat, lon)
                Log.i(TAG, "Selfie saved: ${selfie.id}")

                settingsRepository.setLastCaptureTime(captureTime)

                selfieRepository.enforceDailyLimit(settings.dailyLimit, dayStart)

            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener { e ->
                    Log.w(TAG, "Location failed", e)
                    continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location error", e)
            null
        }
    }

    private fun getDayStartMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    override fun onDestroy() {
        stopUserPresentMonitoring()
        captureJob?.cancel()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }
}
