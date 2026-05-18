<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 113 — Android 16 `SDK_INT_FULL` Plumbing Audit

## Roadmap item

Shipped T2 **Android 16 `SDK_INT_FULL` Plumbing Audit**.

## Implementation

- Added `utils/AndroidUtils` as the central platform-version helper for Android
  16 minor releases:
  - `sdkAtLeast(int major, int minor)`
  - `getSdkIntFull()`
  - `getSdkMajor()`
  - `getSdkMinor()`
- `getSdkIntFull()` uses `Build.VERSION.SDK_INT_FULL` on Android 16+ and
  encodes pre-Android-16 devices as `SDK_INT.0`, preserving old major-only
  behavior at minor `0`.
- Migrated the two current raw Android-16 gates:
  - scheduled-backup foreground `Notification.ProgressStyle`
  - scheduled-backup JobScheduler pending-reason diagnostics
- Left older `SDK_INT` checks unchanged intentionally: they are API-availability
  branches and do not need Android 16 minor-release semantics.
- Added focused `AndroidUtilsTest` coverage for major-only parity, minor-release
  comparisons, encoding/decoding, and invalid minor values.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.utils.AndroidUtilsTest --tests io.github.muntashirakon.AppManager.backup.schedule.AutoBackupDiagnosticsTest --console=plain`

## Sources used

- Roadmap source S124: `https://developer.android.com/about/versions/16/behavior-changes-16`
- Local Android SDK 36.1 stubs for `Build.VERSION.SDK_INT_FULL`,
  `VERSION_CODES_FULL`, and `Build.getMinorSdkVersion()`.
