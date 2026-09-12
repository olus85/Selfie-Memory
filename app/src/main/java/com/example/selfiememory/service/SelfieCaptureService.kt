package com.example.selfiememory.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.selfiememory.MainActivity
import com.example.selfiememory.R
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.data.repository.SettingsRepository
import com.example.selfiememory.domain.model.*
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class SelfieCaptureService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID="selfie_capture_channel"; private const val NOTIFICATION_ID=1001
        const val ACTION_START="com.example.selfiememory.START_CAPTURE_SERVICE"
        const val ACTION_STOP="com.example.selfiememory.STOP_CAPTURE_SERVICE"
        const val ACTION_PAUSE="com.example.selfiememory.PAUSE_CAPTURE_SERVICE"
        const val ACTION_CAPTURE_NOW="com.example.selfiememory.CAPTURE_NOW"
    }
    @Inject lateinit var selfieRepository: SelfieRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var cameraCapturer: CameraCapturer
    @Inject lateinit var pocketDetector: PocketDetector
    @Inject lateinit var imageQualityAnalyzer: ImageQualityAnalyzer
    private lateinit var locationClient:FusedLocationProviderClient
    private var receiver:BroadcastReceiver?=null; private var captureJob:Job?=null

    override fun onCreate(){ super.onCreate(); locationClient=LocationServices.getFusedLocationProviderClient(this); createChannel() }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        super.onStartCommand(intent,flags,startId)
        when(intent?.action){
            ACTION_STOP -> { lifecycleScope.launch{settingsRepository.setEnabled(false);settingsRepository.setStatus("Automatik ausgeschaltet")}; stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> { lifecycleScope.launch{settingsRepository.setPausedUntil(System.currentTimeMillis()+3_600_000);settingsRepository.setStatus("Für eine Stunde pausiert")}; stopSelf(); return START_NOT_STICKY }
        }
        if(!hasCamera()){ lifecycleScope.launch{settingsRepository.setStatus("Kameraberechtigung fehlt")}; stopSelf(); return START_NOT_STICKY }
        return try { startForegroundSafe(); registerReceiver(); if(intent?.action==ACTION_CAPTURE_NOW) capture(manual=true); START_NOT_STICKY }
        catch(e:SecurityException){ lifecycleScope.launch{settingsRepository.setStatus("Android blockiert den Kameradienst – App einmal öffnen")}; stopSelf(); START_NOT_STICKY }
    }

    @SuppressLint("InlinedApi") private fun startForegroundSafe(){
        val type=if(Build.VERSION.SDK_INT>=34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if(Build.VERSION.SDK_INT>=29) ServiceCompat.startForeground(this,NOTIFICATION_ID,notification(),type) else startForeground(NOTIFICATION_ID,notification())
    }
    private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,"Selfie Memory Automatik",NotificationManager.IMPORTANCE_LOW))}
    private fun pi(action:String,id:Int)=PendingIntent.getService(this,id,Intent(this,javaClass).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification():Notification=NotificationCompat.Builder(this,CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Selfie Memory ist aktiv")
        .setContentText("Tippe für Status und Einstellungen").setOngoing(true).setSilent(true)
        .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(android.R.drawable.ic_menu_camera,"Testfoto",pi(ACTION_CAPTURE_NOW,2))
        .addAction(android.R.drawable.ic_media_pause,"1 Std. Pause",pi(ACTION_PAUSE,3))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Aus",pi(ACTION_STOP,4)).build()

    private fun registerReceiver(){
        if(receiver!=null)return
        receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){if(i?.action==Intent.ACTION_USER_PRESENT)capture(false)}}
        // USER_PRESENT is protected by Android but is sent by SystemUI's own UID on Pixel devices.
        ContextCompat.registerReceiver(this,receiver,IntentFilter(Intent.ACTION_USER_PRESENT),ContextCompat.RECEIVER_EXPORTED)
        lifecycleScope.launch{settingsRepository.setStatus("Bereit – wartet auf Entsperren")}
    }

    private fun capture(manual:Boolean){
        if(captureJob?.isActive==true){lifecycleScope.launch{settingsRepository.setStatus("Aufnahme läuft bereits")};return}
        captureJob=lifecycleScope.launch{
            try{
                val s=settingsRepository.settings.first(); val now=System.currentTimeMillis()
                if(!manual){
                    reject(!s.enabled,"Automatik ausgeschaltet")?.let{return@launch}
                    reject(s.pausedUntil>now,"Pausiert")?.let{return@launch}
                    reject(!timeAllowed(s),"Außerhalb des Zeitplans")?.let{return@launch}
                    reject(!batteryAllowed(s),"Akku-/Laderegel nicht erfüllt")?.let{return@launch}
                    reject(!networkMonitor.isConditionMetPublic(s.networkMode,s.allowedSsids),"Netzwerkregel nicht erfüllt")?.let{return@launch}
                    val last=settingsRepository.lastCaptureTime.first(); reject(last>0&&now-last<s.cooldownMinutes*60_000L,"Cooldown aktiv")?.let{return@launch}
                    reject(selfieRepository.getCountSince(dayStart())>=s.dailyLimit,"Tageslimit erreicht")?.let{return@launch}
                    delay(s.captureDelaySeconds*1000L)
                    val km=getSystemService(KeyguardManager::class.java); reject(km.isKeyguardLocked||!getSystemService(PowerManager::class.java).isInteractive,"Gerät wieder gesperrt")?.let{return@launch}
                    reject(!networkMonitor.isConditionMetPublic(s.networkMode,s.allowedSsids),"Netzwerk hat gewechselt")?.let{return@launch}
                    if(s.pocketProtection) reject(pocketDetector.isLikelyInPocket(),"Hosentaschenschutz")?.let{return@launch}
                }
                settingsRepository.setStatus("Kamera wird geöffnet …")
                val jpeg=cameraCapturer.captureImage(this@SelfieCaptureService,s.cameraType,s.mirrorFrontCamera)
                val quality=imageQualityAnalyzer.analyze(jpeg)
                if(s.qualityMode==QualityMode.STRICT && !quality.accepted){settingsRepository.setStatus("Foto zu dunkel – verworfen");return@launch}
                val loc=if(s.locationMode==LocationMode.OFF)null else withTimeoutOrNull(1800){location()}
                val lat=when(s.locationMode){LocationMode.APPROXIMATE->loc?.latitude?.let{Math.round(it*100.0)/100.0};LocationMode.EXACT->loc?.latitude;else->null}
                val lon=when(s.locationMode){LocationMode.APPROXIMATE->loc?.longitude?.let{Math.round(it*100.0)/100.0};LocationMode.EXACT->loc?.longitude;else->null}
                val selfie=selfieRepository.saveSelfie(jpeg,lat,lon,s.storageMode);settingsRepository.setLastCaptureTime(selfie.timestamp)
                settingsRepository.setStatus(if(!quality.accepted)"Foto gespeichert (sehr dunkel)" else "Foto erfolgreich gespeichert")
            }catch(e:CancellationException){throw e}catch(e:Exception){settingsRepository.setStatus("Aufnahmefehler: ${e.javaClass.simpleName}")}
        }
    }
    private suspend fun reject(condition:Boolean,message:String):Unit?=if(condition){settingsRepository.setStatus(message);Unit}else null
    private fun timeAllowed(s:Settings):Boolean{val c=Calendar.getInstance();val bit=1 shl ((c.get(Calendar.DAY_OF_WEEK)+5)%7);val h=c.get(Calendar.HOUR_OF_DAY);val hours=if(s.startHour<s.endHour)h in s.startHour until s.endHour else h>=s.startHour||h<s.endHour;return s.weekdaysMask and bit!=0&&hours}
    private fun batteryAllowed(s:Settings):Boolean{val i=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?:return true;val level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,100)*100/i.getIntExtra(BatteryManager.EXTRA_SCALE,100).coerceAtLeast(1);val status=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);val charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;return level>=s.minBatteryPercent&&(!s.chargingOnly||charging)}
    private fun hasCamera()=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission") private suspend fun location():Location?{if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;return suspendCancellableCoroutine{c->val token=CancellationTokenSource();locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,token.token).addOnCompleteListener{if(c.isActive)c.resume(it.result)};c.invokeOnCancellation{token.cancel()}}}
    private fun dayStart()=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
    override fun onDestroy(){captureJob?.cancel();receiver?.let{runCatching{unregisterReceiver(it)}};receiver=null;cameraCapturer.shutdown();super.onDestroy()}
}
