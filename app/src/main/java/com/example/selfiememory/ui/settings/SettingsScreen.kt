package com.example.selfiememory.ui.settings

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selfiememory.domain.model.CameraType
import com.example.selfiememory.domain.model.NetworkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    val savedSsids = remember {
        wifiManager.configuredNetworks
            ?.mapNotNull { it.SSID?.removeSurrounding("\"")?.takeIf { s -> s.isNotBlank() } }
            ?.distinct()
            ?: emptyList()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
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
                        text = if (hasPermissions) "✓ All permissions granted" else "⚠ Some permissions missing",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Network Trigger
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Network Trigger", style = MaterialTheme.typography.titleMedium)
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
                        if (savedSsids.isEmpty()) {
                            OutlinedTextField(
                                value = settings.specificSsid,
                                onValueChange = { viewModel.setSpecificSsid(it) },
                                label = { Text("WLAN SSID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = settings.specificSsid.ifBlank { "Select a saved network" },
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
                                    savedSsids.forEach { ssid ->
                                        DropdownMenuItem(
                                            text = { Text(ssid) },
                                            onClick = {
                                                viewModel.setSpecificSsid(ssid)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
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