# Changelog

All notable changes to **Selfie-Memory** are documented here.

## [1.3.0] - 2026-05-09

### Fixed

#### Critical Fixes
- **SettingsViewModel Redundant Service Starts**: Removed automatic service start from settings collector. Service is now only started via explicit user action, not on every settings change. This prevents excessive battery drain from repeated service restarts.
- **BootReceiver No Configuration Validation**: BootReceiver now uses `goAsync()` with coroutine to check DataStore settings before starting service. Service only starts if a valid network mode configuration exists, preventing blind service starts after boot.
- **USER_PRESENT Receiver Registration Before Permission Check**: Moved permission check before receiver registration in `startUserPresentMonitoring()`. Receiver is now only registered if CAMERA and ACCESS_FINE_LOCATION permissions are granted.

#### High Priority Fixes
- **NetworkMonitor Trigger Logic**: Added SSID retry logic (500ms delay, 3 retries) to handle WiFi SSID propagation delays. Added active network validation to prevent stale SSID data from triggering incorrect results.
- **Cooldown Time Calculation**: Fixed cooldown block on first capture by adding `lastCapture > 0` check. First capture (when lastCapture is 0) is now never blocked by cooldown.
- **Daily Limit Race Condition**: Wrapped entire count-check-and-delete operation in `withContext(Dispatchers.IO)` inside the mutex lock to ensure atomicity of database modifications.

#### Medium Priority Fixes
- **Enum Parsing Crash**: Added `runCatching` validation in SettingsDataStore for NetworkMode and CameraType enum parsing to handle corrupt/invalid stored values gracefully.
- **ImageProxy Memory Leak**: Changed `image.close()` to `image.use {}` block to ensure ImageProxy is always closed even if exceptions occur during buffer processing.
- **Storage Leak / File Orphaning**: Added `cleanupOrphanedFiles()` method to SelfieRepository that scans storage directory for orphaned files not in database and deletes them. Integrated into Application.onCreate() for startup cleanup. Also improved error logging in enforceDailyLimit.

#### Low Priority Fixes
- **ViewerViewModel ExperimentalCoroutinesApi**: Added `@OptIn(ExperimentalCoroutinesApi::class)` annotation for `flatMapLatest` usage.

---

## [1.2.0] - 2026-05-04

### Added
- **Saved WLAN SSID Dropdown**: Users can now select from their saved WiFi networks instead of typing SSIDs manually (Android Q+)

### Changed
- **Photo Storage Location**: Photos now saved to `/sdcard/Pictures/.SelfieMemory/` instead of internal app storage, preserving photos across reinstalls
- **Storage Permissions**: Added WRITE_EXTERNAL_STORAGE permission for Android < Q

### Fixed

#### Critical Fixes
- **Service Crash on Permission Denial**: Service now gracefully handles missing CAMERA permission by stopping itself instead of crashing
- **Camera Thread Error**: Fixed `Not in application's main thread` error - `ProcessCameraProvider.unbindAll()` and `bindToLifecycle()` must run on main thread

#### High Priority Fixes
- **Service Auto-Start Prevention**: Service no longer starts automatically on app launch without user configuration. Only starts when user explicitly selects a network mode in settings
- **Network Monitoring with `Any WLAN`**: Fixed service not starting when `Any WLAN` mode selected (previously only worked with `Specific WLAN`)

---

## [1.1.0] - 2026-05-04

### Added
- `issues.md` documenting all known bugs and their status

### Fixed

#### Critical Fixes
- **Race Condition in Daily Limit**: Added mutex lock to prevent concurrent deletions when at daily limit
- **Network Check Logic**: Fixed `isConditionMet()` to properly use the `network` parameter instead of always checking `activeNetwork`
- **Cooldown Time Calculation**: Now uses actual capture time instead of start-of-check time for accurate cooldown tracking
- **Permission Check Before Capture**: Added `checkPermissions()` validation before attempting capture

#### High Priority Fixes
- **Enum ValueOf Safety**: `NetworkMode.valueOf()` and `CameraType.valueOf()` now use `runCatching().getOrDefault()` to handle corrupt/invalid stored values
- **DataStore Exception Handling**: Added `IOException` handling to prevent crashes on corrupted preferences
- **Camera Thread Safety**: Changed from `ContextCompat.getMainExecutor()` to dedicated `cameraExecutor` to avoid blocking main thread
- **Camera Unbind on Error**: Properly calls `cameraProvider?.unbindAll()` in all error paths
- **BootReceiver Permissions**: Added `RECEIVE_BOOT_COMPLETED` and `CAMERA` permission checks before starting service
- **Delete Navigation Race**: Fixed `onNavigateBack()` being called before `deleteSelfie()` completes

#### Medium Priority Fixes
- **SelfieDatabase Singleton**: Fixed double-checked locking pattern
- **File Delete Error Handling**: `deleteSelfie()` now throws on file delete failure instead of silently ignoring
- **Service Restart Prevention**: `SettingsViewModel` now checks if service is already running via `ActivityManager.getRunningServices()`
- **Thread-Safe Monitoring Flag**: Changed `isMonitoring` to `AtomicBoolean` for thread safety
- **Gallery File Existence Check**: Shows error indicator when image file is missing
- **BootReceiver Protection**: Added `android:protectionLevel="signature"` to prevent other apps from triggering
- **Missing Import**: Added `Box` and `background` imports to `GalleryScreen.kt`

#### Low Priority Fixes
- **Invalid SelfieId Handling**: `MainActivity` now logs warning and navigates back when selfieId is invalid
- **Camera Shutdown Cleanup**: `shutdown()` now calls `unbindAll()` before terminating executor

### Security
- BootReceiver now requires signature-level permission to prevent spoofing

---

## [1.0.0] - 2026-05-04

### Added
- Initial release
- Automatic selfie capture on device unlock
- Network-based trigger system (Cellular, Any WLAN, Specific WLAN SSID)
- Configurable capture delay (0-10 seconds)
- Cooldown system (1-120 minutes)
- Daily limit (1-50 selfies)
- Location tagging with GPS coordinates
- Local gallery with chronological grid view
- Full-screen viewer with date/time/location overlay
- Material 3 with dynamic colors (Material You)
- Boot receiver for auto-start