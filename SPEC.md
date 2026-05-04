# Selfie-Memory — Specification

## 1. Project Overview

**Name:** Selfie-Memory
**Type:** Native Android App (Kotlin, Jetpack Compose)
**Core Functionality:** Automatic selfie capture on device unlock, triggered only under user-defined network conditions (e.g., office WLAN). All data stored locally; no backend.

---

## 2. Technology Stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin 1.9.x |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 (Android 14) |
| UI | Jetpack Compose (Material 3) |
| Camera | CameraX (ImageCapture, no Preview) |
| Database | Room |
| Location | FusedLocationProviderClient |
| DI | Hilt |
| Architecture | MVVM + Clean Architecture |
| Async | Coroutines + Flow |

---

## 3. Feature List

### 3.1 Core Features

- **Network Trigger System**
  - Modes: Cellular, Any WLAN, Specific WLAN (SSID)
  - Passive monitoring via `ConnectivityManager.NetworkCallback`
  - Foreground Service (type: camera) starts only when condition met

- **Automatic Selfie Capture**
  - `ACTION_USER_PRESENT` broadcast detection inside Foreground Service
  - Configurable delay (0–10 seconds) before capture
  - CameraX `ImageCapture` use case (no Preview surface)
  - Configurable camera: Front Ultra-Wide (default), Front Normal, Back

- **Cooldown System**
  - Configurable cooldown (1–120 minutes)
  - Per-day counter with limit (1–50)
  - Oldest image auto-deleted when limit exceeded

- **Location Tagging**
  - GPS coordinates captured with each selfie
  - Stored in Room DB alongside timestamp and filepath

- **Local Gallery**
  - Chronological grid (newest first)
  - Full-screen view with date/time/location overlay
  - Thumbnails via Coil

- **Settings Screen**
  - Network trigger dropdown (Cellular / Any WLAN / Specific WLAN + SSID input)
  - Camera selector dropdown
  - Capture delay slider (0–10s)
  - Cooldown timer slider (1–120 min)
  - Daily limit slider (1–50)

### 3.2 Data Model

```
SelfieEntity:
  id: Int (PK, auto-generate)
  timestamp: Long (Unix ms)
  filePath: String
  latitude: Double?
  longitude: Double?
```

### 3.3 Storage Rules

- Images stored in `Context.getFilesDir()` → private, no MediaStore
- Room DB in standard app internal storage
- No `.nomedia` needed (internal dir not scanned)

---

## 4. UI/UX

### 4.1 Screens

1. **Gallery Screen** (Main/Root)
   - Grid of thumbnails (3 columns)
   - Top app bar with settings icon
   - Empty state if no selfies
   - Click → full-screen viewer

2. **Full-Screen Viewer**
   - Date, time, location text overlay
   - Back navigation
   - Optional: delete button

3. **Settings Screen**
   - All settings as described in 3.1
   - Immediate persistence via DataStore

### 4.2 Navigation

- Single-activity, Compose Navigation
- Two routes: `gallery` (start) and `settings`

### 4.3 Visual Style

- Material 3, Dynamic Color (Material You)
- Clean, minimal, utilitarian
- Light/Dark theme support (system default)

---

## 5. Permissions

| Permission | Purpose |
|------------|---------|
| `CAMERA` | Taking selfies |
| `ACCESS_FINE_LOCATION` | GPS coordinates |
| `ACCESS_COARSE_LOCATION` | Fallback |
| `FOREGROUND_SERVICE` | Background camera service |
| `FOREGROUND_SERVICE_CAMERA` | Type attribution |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `ACCESS_NETWORK_STATE` | Network monitoring |
| `ACCESS_WIFI_STATE` | SSID detection |

---

## 6. Architecture

```
app/
├── data/
│   ├── local/
│   │   ├── SelfieDatabase (Room)
│   │   ├── SelfieDao
│   │   └── SettingsDataStore
│   └── repository/
│       ├── SelfieRepository
│       └── SettingsRepository
├── domain/
│   ├── model/
│   │   ├── Selfie
│   │   └── Settings
│   └── usecase/
├── service/
│   ├── SelfieCaptureService (LifecycleService, ForegroundService)
│   ├── NetworkMonitor
│   └── CameraCapturer
├── ui/
│   ├── gallery/
│   ├── viewer/
│   └── settings/
├── di/
│   └── AppModule
└── MainActivity.kt
```

---

## 7. Background Service Flow

```
1. App start → register NetworkCallback (passive, no service started yet)
2. Network condition met (e.g., correct SSID) → start ForegroundService (type: camera)
3. Service registers BroadcastReceiver for ACTION_USER_PRESENT
4. User unlocks device → receiver fires
5. Check cooldown + daily limit
6. Wait [delay] seconds
7. Capture image via CameraX ImageCapture
8. Get location via FusedLocationProviderClient
9. Save to Room DB + internal storage
10. If daily limit exceeded → delete oldest entry + file
11. Service stops itself after capture (or stays running for next unlock within cooldown)
```
