# Iter 135 — InstallerX-Revived Privilege-Elevation Cascade

## Roadmap row

- T11 — InstallerX-Revived Privilege-Elevation Cascade

## What changed

- Added `InstallerPrivilegeCascade`, an installer-only route selector that previews and activates package-install providers without permanently changing the user's configured mode.
- The installer confirmation dialog now shows route chips for the primary installer path, fallback providers, Dhizuku detection, and MIUI optimization checks.
- `PackageInstallerService` now temporarily tries ADB-over-TCP, then Shizuku, then root when the active mode cannot create package sessions directly, and restores the original mode after the install attempt.
- Installer progress notifications report the provider being tried and when Android confirmation is used as the fallback.
- Added route-order coverage in `InstallerPrivilegeCascadeTest`.

## Scope notes

- Dhizuku remains diagnostic-only in this slice. NG currently links no Dhizuku runtime API, and the roadmap already carries the minSdk/API strategy blocker for real DPM-backed operations.
- The cascade uses the existing `PackageInstallerCompat` session writer and does not alter APK parsing, split selection, checksum confirmation, developer-verification warnings, or install result handling.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.apk.installer.InstallerPrivilegeCascadeTest" --console=plain`
