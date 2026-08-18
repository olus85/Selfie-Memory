# Selfie Memory 2.0 — technical audit

## Resolved in 2.0

| Area | Previous problem | Resolution |
| --- | --- | --- |
| Post-reboot startup | Android 15+ can reject a camera foreground service started from `BOOT_COMPLETED` | The boot receiver posts a one-tap reactivation notification on Android 15+ |
| False capture trigger | Network state changes could initiate a capture | Only `ACTION_USER_PRESENT` initiates the capture flow; network state is a condition only |
| Pocket photos | Covered phone could produce black frames | Proximity plus ambient-light check before opening the camera |
| Camera exposure | Capture happened immediately after CameraX binding | 1.1-second warm-up and quality-first JPEG capture |
| Black frames | Every JPEG was saved | Conservative luminance and dark-pixel analysis rejects unusable frames |
| Private-only photos | Other photo apps could not access the files | MediaStore publishing to `Pictures/Selfie Memory` with stored content URIs |
| Doubled storage | A private original and public gallery copy consumed roughly twice the space | MediaStore is now canonical; SHA-256-verified private duplicates are reclaimed resumably |
| Existing photo safety | Startup cleanup deleted unindexed JPEGs | Startup reconciliation recovers them into Room instead |
| Room upgrade | No schema path for gallery URIs | Explicit, non-destructive migration from schema 1 to 2 |
| Oldest-photo deletion | Query sorted newest-first | Query now sorts ascending and removes the matching MediaStore copy |
| Location coupling | Missing location permission blocked the camera | Location metadata is optional; only camera permission is required to capture |
| Main-thread SSID wait | Synchronous retry used `Thread.sleep` | SSID is read from active `NetworkCapabilities` with a legacy fallback |
| Viewer usability | One static image with delete only | Swipe, pinch-to-zoom, share, and external-open actions |

## Validation

- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` complete successfully.
- Lint reports zero errors.
- Version 2.0.0 was installed as an in-place debug update on a Pixel 10 Pro running API 37.
- Room schema migration and database integrity were verified on-device.
- On the Pixel test device, all 675 private photos passed byte verification and migrated to 675 non-empty MediaStore items; no source had to be retained.
- App-private data dropped from roughly 790 MiB to under 1 MiB after migration, while the 788.5 MiB gallery remained intact.
- The running capture service was verified as a `camera|specialUse` foreground service.
- No app crash or ANR appeared during installation and migration testing.

## Platform constraints

- Android 15+ requires one user interaction after a reboot before camera foreground monitoring can be reactivated. This is an Android background-camera restriction, not an app preference.
- Reading the current SSID requires location permission on supported Android versions.
- MediaStore publishing on Android 9 and older additionally requires the legacy storage permission at runtime. A publishing failure never deletes the private original.
- “Front ultra-wide” and “front normal” currently resolve through CameraX's default front-camera selector; explicit physical-lens selection remains future work.
