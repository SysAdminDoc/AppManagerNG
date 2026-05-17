<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 6

Pass 6 closed T5 "Installer APK SHA256 Toast" and documented why the next T5
Dhizuku row should not be implemented as a direct dependency drop.

## Next exact steps

1. Install or configure a JDK, then run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.installer.InstallChecksumDisplayTest`
   - the pass-4 tests listed in `.ai/research/2026-05-17-pass-4/CONTINUE_FROM_HERE.md`
2. For Dhizuku, do an explicit design spike before code:
   - Official `io.github.iamr0s:Dhizuku-API` latest is `2.5.4`.
   - Upstream Dhizuku-API declares `MIN_SDK = 26`.
   - AppManagerNG still has a load-bearing API-21 floor.
   - Viable options are reflection/optional-provider integration, a split artifact,
     or a future minSdk decision. Do not add the AAR directly without resolving this.
3. If continuing with code before Dhizuku is resolved, the next low-risk T5 rows are
   Android 16 capability-dropping UI, VPN plugin flags control, or the root/ADB
   battery-optimization auto-fix. The Privilege Health-Check Screen is the natural
   container for several of those rows but is medium-sized.

## Known limitation

No Android device/emulator and no local JDK were available in this shell.
