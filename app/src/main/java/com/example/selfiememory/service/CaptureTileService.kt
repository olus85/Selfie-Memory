package com.example.selfiememory.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.selfiememory.data.local.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CaptureTileService:TileService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onStartListening(){scope.launch{update(SettingsDataStore(applicationContext).settings.first().enabled)}}
    override fun onClick(){scope.launch{val store=SettingsDataStore(applicationContext);val enabled=!store.settings.first().enabled;store.setEnabled(enabled);store.setPausedUntil(0);if(enabled)ContextCompat.startForegroundService(applicationContext,Intent(applicationContext,SelfieCaptureService::class.java).setAction(SelfieCaptureService.ACTION_START))else startService(Intent(applicationContext,SelfieCaptureService::class.java).setAction(SelfieCaptureService.ACTION_STOP));update(enabled)}}
    private fun update(enabled:Boolean){qsTile?.apply{state=if(enabled)Tile.STATE_ACTIVE else Tile.STATE_INACTIVE;label=if(enabled)"Selfie aktiv" else "Selfie aus";updateTile()}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
