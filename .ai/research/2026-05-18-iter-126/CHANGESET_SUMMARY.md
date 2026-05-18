<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 126 — Android 17 cleartext deprecation badge

## Roadmap row

T9 **Android 17 cleartext Deprecation Warning** is shipped.

## What changed

- Added hidden-API shim coverage for `ApplicationInfo.networkSecurityConfigRes`.
- Added `ApplicationInfoCompat.getNetworkSecurityConfigRes()` so app-detail code
  can distinguish manifest-wide cleartext from scoped Network Security Config
  usage.
- Added a test-covered `AppInfoViewModel.shouldWarnCleartextDeprecation()`
  predicate and a `warnsCleartextDeprecation` tag-cloud bit.
- Added an App Info caution tag and dialog copy for packages that set
  `android:usesCleartextTraffic="true"` without a Network Security Config.
- Updated `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and the Android
  17 cleartext audit note.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.details.info.AppInfoViewModelCleartextTest" --console=plain` passed.
- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.

## Notes

- NG's own targetSdk=37 cleartext posture remains clean: it already declares a
  Network Security Config, rejects cleartext by default, and scopes cleartext to
  loopback only.
