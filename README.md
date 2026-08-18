# Selfie Memory

Selfie Memory is a privacy-first Android app that automatically captures a selfie after device unlock — but only when your configured network, cooldown, daily limit, and quality checks allow it.

Everything stays on the device. There is no account, backend, analytics service, or cloud upload.

## What makes 2.0 different

- **Unlock-only capture** — network changes never take a photo by themselves.
- **Pocket protection** — proximity and ambient-light sensors suppress captures when the phone is likely covered.
- **Black-frame rejection** — an additional luminance check discards overwhelmingly dark images before they enter the gallery or cooldown history.
- **Reliable exposure** — CameraX gets time to settle auto-exposure before a quality-first JPEG is taken.
- **System gallery support** — photos are published to `Pictures/Selfie Memory` through MediaStore and can be opened in Google Photos or another photo app.
- **No double storage** — MediaStore is the single image source; verified legacy duplicates are reclaimed from app-private storage.
- **Modern gallery** — adaptive tiles, day grouping, swipe navigation, pinch-to-zoom, sharing, and “Open with” support.
- **Network rules** — cellular, any Wi-Fi, or a specific SSID.
- **Capture controls** — configurable delay, cooldown, daily limit, camera direction, and optional location metadata.

## Data safety

Version 2.0 includes a non-destructive Room migration from schema 1 to 2 and a resumable storage migration.

- Existing unindexed JPEGs are recovered instead of deleted.
- Each private JPEG is published to MediaStore and read back for a byte-for-byte SHA-256 verification.
- The private duplicate is removed only after the verified MediaStore URI is safely recorded in Room.
- Interrupted or failed migrations keep the private original and safely resume on the next start.
- New photos are written directly to MediaStore. A private safety copy is used only if MediaStore is unavailable.
- An app update must use the same package name and signing key. Do not uninstall the app before upgrading.

After migration, the app and other gallery apps display the same single file in `Pictures/Selfie Memory`. Deleting it from Selfie Memory also removes it from the system gallery.

On the Pixel 10 Pro validation device, 675 photos (788.5 MiB) migrated successfully and app-private data fell from roughly 790 MiB to under 1 MiB without changing the gallery count.

## Android reboot behavior

Android 15 and newer do not allow a camera foreground service to be launched directly from `BOOT_COMPLETED`. Selfie Memory therefore shows a one-tap reactivation notification after a reboot instead of attempting a prohibited background start and crashing. Opening the app also reactivates capture monitoring.

## Install a debug release

Download `selfie-memory-v2.0.0-debug.apk` from the GitHub release and install it over the existing debug build.

For an ADB update that preserves app data:

```bash
adb install -r selfie-memory-v2.0.0-debug.apk
```

Never uninstall first if the private app data has not been backed up.

## Build

Requirements:

- JDK 17
- Android SDK 34

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- Kotlin 1.9 and Jetpack Compose
- CameraX
- Room with explicit migrations
- Hilt
- Preferences DataStore
- Coroutines and Flow
- Android MediaStore
- Fused Location Provider

Core capture flow:

```text
Device unlocked
  → permission and network checks
  → cooldown and daily-limit checks
  → configurable delay
  → pocket check
  → CameraX warm-up and capture
  → black-frame quality analysis
  → verified MediaStore save
  → optional location metadata
```

## Permissions

| Permission | Purpose |
| --- | --- |
| `CAMERA` | Capture a photo after an eligible unlock |
| `ACCESS_FINE_LOCATION` | Optional location metadata and SSID access |
| `POST_NOTIFICATIONS` | Foreground-service and post-reboot notifications |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Evaluate the selected network rule |
| `FOREGROUND_SERVICE_CAMERA` | Keep unlock monitoring and camera access reliable |
| `RECEIVE_BOOT_COMPLETED` | Offer safe one-tap reactivation after reboot |

## Privacy

Selfie Memory performs all capture decisions, image analysis, and storage migration locally. Photos are stored through Android MediaStore in `Pictures/Selfie Memory`; no network permission is used to upload images.
