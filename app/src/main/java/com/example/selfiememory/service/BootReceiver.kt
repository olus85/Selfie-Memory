package com.example.selfiememory.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.selfiememory.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {

            if (context.checkCallingOrSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECEIVE_BOOT_COMPLETED permission not granted")
                return
            }

            if (context.checkCallingOrSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "CAMERA permission not granted, skipping service start")
                return
            }

            val pendingResult = goAsync()
            val dataStore = SettingsDataStore(context)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val networkMode = dataStore.networkMode.first()

                    if (networkMode.isEmpty()) {
                        Log.i(TAG, "Network mode not configured, skipping service start")
                    } else {
                        Log.i(TAG, "Boot completed, starting service")
                        val serviceIntent = Intent(context, SelfieCaptureService::class.java).apply {
                            action = SelfieCaptureService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking configuration", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
