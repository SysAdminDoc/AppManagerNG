<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 16

Pass 16 handled:

- Iter-22 / T8 `Broadcast Intent API`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.automation.AutomationIntentsTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify a same-signature/debug caller can send:
   - `io.github.sysadmindoc.AppManagerNG.action.FREEZE`
   - `io.github.sysadmindoc.AppManagerNG.action.UNFREEZE`
   - `io.github.sysadmindoc.AppManagerNG.action.RUN_PROFILE`
   - `io.github.sysadmindoc.AppManagerNG.action.DISABLE_COMPONENT`
   - `io.github.sysadmindoc.AppManagerNG.action.INSTALL_FROM_URI`
3. Verify an unsigned third-party caller cannot reach `AutomationReceiver` because
   the receiver requires `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION`.
4. Continue roadmap work with the next non-blocked row. Likely candidates:
   - iter-22/T8 `Tasker Plugin (In-App, No Separate APK)`;
   - iter-22/T8 `Per-App Pinned QS TileService`;
   - T8 `Routine Operations / Scheduler`;
   - T8 `Saved Filter Presets`.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
