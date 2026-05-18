<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 121 - Per-App Language Picker

## What changed

- Added `AppLocaleManagerCompat`, a small Android 13+ compat wrapper around the
  hidden `ILocaleManager` service for selected package/user app locale reads and
  writes.
- Added the `android.app.ILocaleManager` hidden API stub and regenerated
  `api-versions-appmanagerng-hiddenapi.json` so the baseline tracks the new
  framework dependency.
- App Info More Info now shows the selected app language, falls back to System
  default when there is no override, and exposes a searchable inline language
  picker when the current privilege path has `CHANGE_CONFIGURATION`.
- Added `AppLocaleOptions` and focused tests for option de-duplication and
  display summaries.

## Verification

- `.\scripts\generate-hidden-api-baseline.ps1`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppLocaleOptionsTest --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppLocaleOptionsTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Notes

- The roadmap's claim that App Info already read per-app locale was stale in
  the live source. This pass added both the read row and the write picker.
- The picker intentionally applies to `mPackageName` + `mUserId`, matching App
  Info's current selected-user model instead of assuming the owner user.
- `HiddenApiDescriptorBaselineTest` now parses the descriptor with Gson instead
  of Android's local-JVM `org.json` stub, so the baseline coverage test can run
  under `testFlossDebugUnitTest`.
