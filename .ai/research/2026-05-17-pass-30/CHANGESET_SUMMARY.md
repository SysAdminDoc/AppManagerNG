<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 30 Changeset Summary — Shizuku Clear-Data Revoke Warning

Date: 2026-05-17

## Roadmap item

Closed the T5 **Shizuku Permission Auto-Revoke Warning** row from iter-19.

## What changed

- Extended `ShizukuBridge` with clear-data risk classification:
  - AppManagerNG package: clearing data can remove NG's own Shizuku grant.
  - Shizuku Manager package: clearing data can remove stored Shizuku authorization state.
  - Installed apps declaring `rikka.shizuku.ShizukuProvider`: clearing data can revoke that app's Shizuku grant while Shizuku Manager may temporarily show stale authorization state.
- App Info direct clear-data confirmation now appends the relevant Shizuku warning when the selected app matches one of those risk classes.
- Main-list batch clear-data confirmation now warns when any selected package matches the same risk classes.
- Direct privileged App Info clear-data captures `ShizukuBridge.hasPermission()` before the clear, re-checks after success, and opens Settings -> Mode of operation when AppManagerNG's own grant was revoked.
- Batch/profile clear-data records a log entry if AppManagerNG's Shizuku grant disappears during the batch, because that execution path has no foreground dialog surface.
- Added JVM coverage for risk classification, provider-name matching, and revoked-permission transition logic.

## Verification

- Static source review of the changed App Info, main-list batch, batch/profile operation, and Shizuku bridge paths.
- `git diff --check` completed cleanly apart from expected CRLF working-copy warnings.
- Attempted `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`.
  Full Gradle/JVM execution remains blocked in this shell because no JDK is installed and `JAVA_HOME` is unset.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/batchops/BatchOpsManager.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java`
- `ROADMAP.md`
- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`
