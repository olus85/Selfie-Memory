# Selfie-Memory

Android app for automatic selfie capture on device unlock, triggered only under user-defined network conditions.

## Features

- **Network Trigger System** - Capture only on specific WiFi (SSID), any WiFi, or cellular
- **Automatic Selfie Capture** - Triggers when device is unlocked
- **Configurable Delay** - Set 0-10 second delay before capture
- **Cooldown & Daily Limits** - Prevent excessive captures (1-120 min cooldown, 1-50 per day)
- **Location Tagging** - GPS coordinates stored with each selfie
- **Local Gallery** - Chronological grid view with full-screen viewer
- **Material 3** - Dynamic colors with light/dark theme support

## Tech Stack

- Kotlin 1.9.x / Jetpack Compose
- CameraX for image capture
- Room for local database
- Hilt for dependency injection
- DataStore for settings
- FusedLocationProviderClient for GPS

## Build

```bash
./gradlew assembleDebug
```

APK located at: `app/build/outputs/apk/debug/app-debug.apk`

## Permissions

- CAMERA - Taking selfies
- ACCESS_FINE_LOCATION - GPS coordinates
- FOREGROUND_SERVICE_CAMERA - Background capture
- ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE - Network monitoring
- POST_NOTIFICATIONS - Android 13+