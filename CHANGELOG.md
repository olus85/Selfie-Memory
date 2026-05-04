# Changelog

All notable changes to **Selfie-Memory** are documented here.

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
- **Thread-Safe Monitoring Flag**: Changed `isMonitoring` to `AtomicBoolean` for proper thread safety
- **Gallery File Existence Check**: Shows error indicator when image file is missing
- **BootReceiver Protection**: Added `android:protectionLevel="signature"` to prevent other apps from triggering
- **Missing Import**: Added `Box` and `background` imports to `GalleryScreen.kt`

#### Low Priority Fixes
- **Invalid SelfieId Handling**: `MainActivity` now logs warning and navigates back when selfieId is invalid
- **Camera Shutdown Cleanup**: `shutdown()` now calls `unbindAll()` before terminating executor

### Changed
- Moved `captureTime` calculation to immediately before the actual image capture for accuracy
- Improved logging in `NetworkMonitor` to show current vs expected SSID

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