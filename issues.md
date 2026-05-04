# Selfie-Memory Bug Report

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 3 |
| HIGH | 10 |
| MEDIUM | 13 |
| LOW | 1 |
| **Total** | **27** |

---

## CRITICAL

### [#1](app/src/main/java/com/example/selfiememory/data/repository/SelfieRepository.kt) Race Condition in Daily Limit Enforcement
**File:** `SelfieRepository.kt:64-71`
**Description:** `enforceDailyLimit()` has a TOCTOU race condition. If two captures occur simultaneously when at the limit, both threads could pass the `count > limit` check before either deletes old selfies.
**Status:** OPEN

### [#2](app/src/main/java/com/example/selfiememory/service/NetworkMonitor.kt) Incorrect Network Check Logic
**File:** `NetworkMonitor.kt:59-61`
**Description:** `currentNetworks.any { isConditionMet(networkMode, specificSsid) }` calls `isConditionMet` which uses `connectivityManager.activeNetwork`, ignoring the `network` parameter passed to `any`. All networks are checked against the same active network.
**Status:** OPEN

### [#3](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) Daily Limit Check Bypass
**File:** `SelfieCaptureService.kt:227-232`
**Description:** Between checking `countToday >= settings.dailyLimit` and actual capture, another capture could occur in another service instance, exceeding limit. Combined with Bug #1.
**Status:** OPEN

---

## HIGH

### [#4](app/src/main/java/com/example/selfiememory/data/repository/SettingsRepository.kt) Crash on Invalid Enum Value
**File:** `SettingsRepository.kt:24,26`
**Description:** `NetworkMode.valueOf(networkMode)` and `CameraType.valueOf(cameraType)` throw `IllegalArgumentException` if stored preference value is not a valid enum name (e.g., after app downgrade or data corruption).
**Status:** OPEN

### [#5](app/src/main/java/com/example/selfiememory/data/local/SettingsDataStore.kt) Crash on DataStore Read Exception
**File:** `SettingsDataStore.kt:24-30`
**Description:** `context.dataStore.data` flow can throw `IOException` if DataStore file is corrupted. The `map` transformation doesn't handle exceptions.
**Status:** OPEN

### [#6](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) Incorrect Cooldown Time Calculation
**File:** `SelfieCaptureService.kt:219-225`
**Description:** `now` is captured at the START of `checkAndCapture()`, but used at the END for `setLastCaptureTime()`. If there's a long delay (capture delay, camera init), cooldown is calculated from wrong time.
**Status:** OPEN

### [#7](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) Receiver Registration Without Permission Check
**File:** `SelfieCaptureService.kt:167-183`
**Description:** `startUserPresentMonitoring()` calls `registerReceiver()` without checking if CAMERA permission is granted. The receiver will register but capture will fail later.
**Status:** OPEN

### [#8](app/src/main/java/com/example/selfiememory/service/CameraCapturer.kt) Camera Provider Future Get Blocks Main Thread
**File:** `CameraCapturer.kt:39-41`
**Description:** `cameraProviderFuture.get()` blocks the thread. Since this runs in `ContextCompat.getMainExecutor(context)`, it blocks the main thread during camera setup.
**Status:** OPEN

### [#9](app/src/main/java/com/example/selfiememory/service/CameraCapturer.kt) ImageProxy Not Closed on Error
**File:** `CameraCapturer.kt:88-91`
**Description:** If `cameraProviderFuture.get()` throws, any partially obtained resources leak. Also `image.close()` not called if continuation never resumes.
**Status:** OPEN

### [#10](app/src/main/java/com/example/selfiememory/service/NetworkMonitor.kt) SSID Returns "unknown ssid" Without Permission
**File:** `NetworkMonitor.kt:86`
**Description:** `wifiManager.connectionInfo.ssid` returns `<unknown ssid>` when location permission isn't granted (even if ACCESS_FINE_LOCATION is granted). Requires both location permission AND "Location enabled" setting.
**Status:** OPEN

### [#11](app/src/main/java/com/example/selfiememory/service/BootReceiver.kt) Service Started Without Settings Configuration
**File:** `BootReceiver.kt:15-28`
**Description:** On boot, starts `SelfieCaptureService` regardless of whether user has configured settings. Uses default values.
**Status:** OPEN

### [#12](app/src/main/java/com/example/selfiememory/ui/viewer/ViewerScreen.kt) Delete Navigation Race
**File:** `ViewerScreen.kt:101-104`
**Description:** `onNavigateBack()` called immediately after triggering delete, not waiting for delete completion. Could access deleted file.
**Status:** OPEN

---

## MEDIUM

### [#13](app/src/main/java/com/example/selfiememory/data/local/SelfieDatabase.kt) Incomplete Singleton Pattern
**File:** `SelfieDatabase.kt:16-26`
**Description:** Singleton uses double-checked locking but doesn't handle case where `INSTANCE` is set but instance hasn't fully initialized.
**Status:** OPEN

### [#14](app/src/main/java/com/example/selfiememory/data/repository/SelfieRepository.kt) No Transaction Wrapper for Save + Delete
**File:** `SelfieRepository.kt:28-48`
**Description:** `saveSelfie` writes file first, then inserts to DB. If DB insert fails, file is orphaned on disk. No rollback.
**Status:** OPEN

### [#15](app/src/main/java/com/example/selfiememory/data/repository/SelfieRepository.kt) Silent File Delete Failure
**File:** `SelfieRepository.kt:54`
**Description:** `file.delete()` returns boolean but result is ignored. If delete fails, orphan file remains.
**Status:** OPEN

### [#16](app/src/main/java/com/example/selfiememory/ui/settings/SettingsViewModel.kt) Service Started on Every ViewModel Init
**File:** `SettingsViewModel.kt:28-37`
**Description:** `init` block calls `startService()` every time ViewModel is created. No check if service is already running.
**Status:** OPEN

### [#17](app/src/main/java/com/example/selfiememory/ui/settings/SettingsViewModel.kt) Race Condition in Settings Updates
**File:** `SettingsViewModel.kt:52-86`
**Description:** All setter functions are `suspend` but fire-and-forget from UI. User can change settings rapidly causing interleaved/corrupted updates.
**Status:** OPEN

### [#18](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) Receiver Not Unregistered on Some Paths
**File:** `SelfieCaptureService.kt:185-196`
**Description:** `stopUserPresentMonitoring()` only called in `onDestroy()`. If service crashes, receiver leaks.
**Status:** OPEN

### [#19](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) `isMonitoring` Flag Not Thread-Safe
**File:** `SelfieCaptureService.kt:169`
**Description:** `isMonitoring` accessed from different threads without synchronization.
**Status:** OPEN

### [#20](app/src/main/java/com/example/selfiememory/service/SelfieCaptureService.kt) Missing Null Check for Location on SDK < 31
**File:** `SelfieCaptureService.kt:264-290`
**Description:** `fusedLocationClient.getCurrentLocation()` returns nullable `Location?` but only checked for null on API 31+.
**Status:** OPEN

### [#21](app/src/main/java/com/example/selfiememory/service/NetworkMonitor.kt) Missing ACCESS_WIFI_STATE Permission Check
**File:** `NetworkMonitor.kt:85`
**Description:** `wifiManager.connectionInfo` requires ACCESS_WIFI_STATE permission. Runtime grant not verified.
**Status:** OPEN

### [#22](app/src/main/java/com/example/selfiememory/service/BootReceiver.kt) No Boot Permission Handling
**File:** `BootReceiver.kt:16`
**Description:** `RECEIVE_BOOT_COMPLETED` permission should be checked before attempting to start service.
**Status:** OPEN

### [#23](app/src/main/java/com/example/selfiememory/ui/gallery/GalleryScreen.kt) Missing File Existence Check
**File:** `GalleryScreen.kt:131`
**Description:** `AsyncImage` with `File(selfie.filePath)` shows nothing if file doesn't exist, no error handling.
**Status:** OPEN

### [#24](app/src/main/java/com/example/selfiememory/ui/viewer/ViewerScreen.kt) No Delete Completion State
**File:** `ViewerScreen.kt:102`
**Description:** `deleteSelfie` is suspend but called without coroutine scope. No way to show progress or handle errors.
**Status:** OPEN

### [#25](app/src/main/AndroidManifest.xml) BootReceiver Exported Without Permission
**File:** `AndroidManifest.xml:48`
**Description:** `BootReceiver` with `android:exported="true"` but no permission requirement. Any app can trigger this receiver.
**Status:** OPEN

---

## LOW

### [#26](app/src/main/java/com/example/selfiememory/MainActivity.kt) Silent Failure on Invalid selfieId
**File:** `MainActivity.kt:59`
**Description:** `backStackEntry.arguments?.getString("selfieId")?.toIntOrNull()` silently returns without UI if selfieId is invalid.
**Status:** OPEN

### [#27](app/src/main/java/com/example/selfiememory/service/CameraCapturer.kt) No Camera Shutdown on Error Path
**File:** `CameraCapturer.kt:95-97`
**Description:** `shutdown()` only terminates executor. If camera was bound but error occurred, resources may not be released.
**Status:** OPEN