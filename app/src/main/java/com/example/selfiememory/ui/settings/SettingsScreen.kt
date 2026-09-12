package com.example.selfiememory.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selfiememory.domain.model.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(onNavigateBack:()->Unit,vm:SettingsViewModel=hiltViewModel()){
    val s by vm.settings.collectAsState();val scans by vm.availableSsids.collectAsState();val message by vm.message.collectAsState();val context=LocalContext.current
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.refreshAvailableSsids()}
    val backup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let(vm::exportBackup)}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importBackup)}
    LaunchedEffect(Unit){val p=buildList{add(Manifest.permission.CAMERA);add(Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)};permissions.launch(p.toTypedArray())}
    message?.let{LaunchedEffect(it){vm.clearMessage()}}
    Scaffold(topBar={TopAppBar(title={Text("Einstellungen")},navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Zurück")}})}){pad->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            StatusCard(s)
            SettingsCard("Automatik"){
                Toggle("Aufnahmen beim Entsperren",s.enabled,vm::enabled)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.pause(3_600_000)},enabled=s.enabled){Text("1 Std. Pause")};OutlinedButton(onClick={vm.pause(0)}){Text("Fortsetzen")};Button(onClick=vm::testPhoto,enabled=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){Text("Testfoto")}}
            }
            SettingsCard("Auslöser und Profile"){
                EnumDropdown("Netzwerk",s.networkMode,NetworkMode.entries,{when(it){NetworkMode.ANY->"Überall";NetworkMode.CELLULAR->"Nur Mobilfunk";NetworkMode.ANY_WLAN->"Jedes WLAN";NetworkMode.SPECIFIC_WLAN->"Ausgewählte WLANs"}},vm::network)
                if(s.networkMode==NetworkMode.SPECIFIC_WLAN){
                    var manual by remember(s.allowedSsids){mutableStateOf(s.allowedSsids.joinToString(", "))}
                    OutlinedTextField(manual,{manual=it},Modifier.fillMaxWidth(),label={Text("WLANs, durch Komma getrennt")},supportingText={Text("Gefunden: ${scans.take(4).joinToString().ifBlank{"keine"}}")})
                    Button(onClick={vm.ssids(manual.split(',').map(String::trim).filter(String::isNotBlank).toSet())}){Text("WLAN-Liste übernehmen")}
                }
                ValueSlider("Verzögerung",s.captureDelaySeconds,0..15,"s",vm::delay)
                ValueSlider("Cooldown",s.cooldownMinutes,0..240,"min",vm::cooldown)
                ValueSlider("Tageslimit",s.dailyLimit,1..100,"Fotos",vm::limit)
                Text("Aktiv ${s.startHour}:00 bis ${s.endHour}:00")
                ValueSlider("Start",s.startHour,0..23,"Uhr",{vm.time(it,s.endHour)})
                ValueSlider("Ende",s.endHour,1..24,"Uhr",{vm.time(s.startHour,it)})
                Text("Wochentage")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Mo","Di","Mi","Do","Fr","Sa","So").forEachIndexed{i,n->FilterChip(selected=s.weekdaysMask and (1 shl i)!=0,onClick={vm.weekdays(s.weekdaysMask xor (1 shl i))},label={Text(n)})}}
                ValueSlider("Mindestakku",s.minBatteryPercent,0..90,"%",vm::battery);Toggle("Nur beim Laden",s.chargingOnly,vm::charging)
            }
            SettingsCard("Kamera und Qualität"){
                EnumDropdown("Kamera",s.cameraType,CameraType.entries,{when(it){CameraType.FRONT_ULTRA_WIDE->"Front weit";CameraType.FRONT_NORMAL->"Front normal";CameraType.BACK->"Rückkamera"}},vm::camera)
                Toggle("Frontfoto spiegeln",s.mirrorFrontCamera,vm::mirror);Toggle("Hosentaschenschutz",s.pocketProtection,vm::pocket)
                EnumDropdown("Dunkle Fotos",s.qualityMode,QualityMode.entries,{when(it){QualityMode.OFF->"Immer speichern";QualityMode.WARN->"Speichern + Hinweis";QualityMode.STRICT->"Verwerfen"}},vm::quality)
            }
            SettingsCard("Datenschutz und Speicher"){
                EnumDropdown("Ablage",s.storageMode,StorageMode.entries,{if(it==StorageMode.PRIVATE)"Nur in der App" else "Auch in Fotogalerie"},vm::storage)
                EnumDropdown("Standort",s.locationMode,LocationMode.entries,{when(it){LocationMode.OFF->"Aus";LocationMode.APPROXIMATE->"Ungefähr";LocationMode.EXACT->"Genau"}},vm::location)
                Toggle("App mit Gerätesperre schützen",s.appLockEnabled,vm::appLock)
                OutlinedButton(onClick={backup.launch("selfie-memory-backup-${System.currentTimeMillis()}.zip")}){Text("Fotos & Tagebuch sichern")}
                OutlinedButton(onClick={restore.launch(arrayOf("application/zip","application/octet-stream"))}){Text("Backup wiederherstellen")}
                Text("Gelöschte Einträge bleiben 30 Tage im Papierkorb. Fotos und Funktionen laufen komplett lokal.",style=MaterialTheme.typography.bodySmall)
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
        }
    }
}

@Composable private fun StatusCard(s:Settings)=Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(16.dp)){Text(if(s.enabled)"● Automatik aktiv" else "○ Automatik aus",style=MaterialTheme.typography.titleMedium);Text(s.lastStatus);if(s.lastStatusTime>0)Text(DateFormat.getDateTimeInstance().format(Date(s.lastStatusTime)),style=MaterialTheme.typography.bodySmall)}}
@Composable private fun SettingsCard(title:String,content:@Composable ColumnScope.()->Unit)=Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);content()}}
@Composable private fun Toggle(text:String,value:Boolean,onChange:(Boolean)->Unit)=Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(text,Modifier.weight(1f));Switch(value,onChange)}
@Composable private fun ValueSlider(name:String,value:Int,range:IntRange,suffix:String,onChange:(Int)->Unit){Text("$name: $value $suffix");Slider(value.toFloat(),{onChange(it.toInt())},valueRange=range.first.toFloat()..range.last.toFloat())}
@OptIn(ExperimentalMaterial3Api::class) @Composable private fun <T> EnumDropdown(label:String,value:T,values:List<T>,name:(T)->String,onChange:(T)->Unit){var open by remember{mutableStateOf(false)};ExposedDropdownMenuBox(open,{open=it}){OutlinedTextField(name(value),{},Modifier.menuAnchor().fillMaxWidth(),readOnly=true,label={Text(label)});ExposedDropdownMenu(open,{open=false}){values.forEach{DropdownMenuItem({Text(name(it))},{onChange(it);open=false})}}}}
