<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 7

Pass 7 closed T5 "Auto-fix Battery Optimization (Root/ADB Path)" by adding
`SelfBatteryOptimization` and wiring it into routine/profile execution plus
backup/import/restore batch execution.

## Next exact steps

1. Install or configure a JDK, then run at least:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.installer.InstallChecksumDisplayTest`
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.utils.UtilsCertificateAlgorithmTest`
2. If continuing code work before JDK/device setup, the next code-addressable
   roadmap item is likely T5/T7 cross-user package state detection:
   - T5 row: `Cross-User Package State Detection`
   - T7 row: `Finder: Multi-User Scope`
   - Start by reading `ApplicationItem`, `FilterableAppInfo`, `PackageManagerCompat`,
     `Users`, and `FinderViewModel`.
3. Keep T6 `Scheduler Battery Optimization Auto-Fix` open until the future
   Scheduled Auto-Backup UI exists and can show the schedule-enable prompt plus
   unprivileged fallback copy.

## Known limitation

No Android device/emulator and no local JDK were available in this shell. Push is
also blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
