package com.example.selfiememory.ui.settings

import android.Manifest
import android.os.Build
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selfiememory.domain.model.CameraType
import com.example.selfiememory.domain.model.NetworkMode
import com.example.selfiememory.service.SelfieCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val availableSsids by viewModel.availableSsids.collectAsState()
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            viewModel.refreshAvailableSsids()
            ContextCompat.startForegroundService(
                context,
                Intent(context, SelfieCaptureService::class.java).setAction(SelfieCaptureService.ACTION_START)
            )
        }
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Permissions section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasPermissions) "All permissions granted" else "Some permissions missing",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Network Trigger
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Network Trigger", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.refreshAvailableSsids() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh networks"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    NetworkMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = settings.networkMode == mode,
                                onClick = { viewModel.setNetworkMode(mode) }
                            )
                            Text(
                                text = when (mode) {
                                    NetworkMode.CELLULAR -> "Cellular Data"
                                    NetworkMode.ANY_WLAN -> "Any WLAN"
                                    NetworkMode.SPECIFIC_WLAN -> "Specific WLAN"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    if (settings.networkMode == NetworkMode.SPECIFIC_WLAN) {
                        var expanded by remember { mutableStateOf(false) }
                        val displaySsids = if (availableSsids.isEmpty()) {
                            listOf(settings.specificSsid).filter { it.isNotBlank() }
                        } else {
                            availableSsids
                        }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = settings.specificSsid.ifBlank { "Select a network" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("WLAN SSID") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                if (displaySsids.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No networks found") },
                                        onClick = { expanded = false },
                                        enabled = false
                                    )
                                } else {
                                    displaySsids.forEach { ssid ->
                                        DropdownMenuItem(
                                            text = { Text(ssid) },
                                            onClick = {
                                                viewModel.setSpecificSsid(ssid)
                                                expanded = false
                                            },
                                            modifier = Modifier.clickable {
                                                viewModel.setSpecificSsid(ssid)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (displaySsids.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No WiFi networks detected. Make sure location permission is granted and WiFi is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Camera
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Camera", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    CameraType.entries.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = settings.cameraType == type,
                                onClick = { viewModel.setCameraType(type) }
                            )
                            Text(
                                text = when (type) {
                                    CameraType.FRONT_ULTRA_WIDE -> "Front Ultra-Wide (Default)"
                                    CameraType.FRONT_NORMAL -> "Front Normal"
                                    CameraType.BACK -> "Back Camera"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // Capture Delay
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Capture Delay: ${settings.captureDelaySeconds} seconds", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = settings.captureDelaySeconds.toFloat(),
                        onValueChange = { viewModel.setCaptureDelay(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9
                    )
                }
            }

            // Cooldown
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cooldown: ${settings.cooldownMinutes} minutes", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = settings.cooldownMinutes.toFloat(),
                        onValueChange = { viewModel.setCooldownMinutes(it.toInt()) },
                        valueRange = 1f..120f,
                        steps = 118
                    )
                }
            }

            // Daily Limit
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Limit: ${settings.dailyLimit} selfies", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = settings.dailyLimit.toFloat(),
                        onValueChange = { viewModel.setDailyLimit(it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                }
            }
        }
    }
}
