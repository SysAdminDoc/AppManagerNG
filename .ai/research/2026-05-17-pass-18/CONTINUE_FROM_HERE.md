<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 18

Pass 18 handled:

- T8 `App Shortcut: Freeze / Force-Stop / Clear Cache Per-App`

## Result

Dynamic launcher shortcuts now refresh from the main-list app snapshot and expose
recent-app freeze, force-stop, and clear-cache actions when those operations are
available in the current privilege mode. App Details also pins force-stop and
clear-cache shortcuts from long-press action chips.

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.shortcut.AppActionShortcutPublisherTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify:
   - long-press AppManagerNG launcher icon shows dynamic app actions after the
     main list has loaded;
   - shortcut actions are absent in no-root/no-ADB mode when the underlying op
     is unavailable;
   - freeze, force-stop, and clear-cache shortcuts prompt for AppManagerNG auth
     when required and then run the action;
   - long-pressing App Details force-stop / clear-cache action chips pins an
     editable home-screen shortcut.
3. Continue roadmap work with the next non-blocked `Now` row. Good candidates:
   - T11 `Snapshot Bundle Export/Import`;
   - T1 `floss` vs `full` Build Flavors;
   - T9 `Privileged Op Audit Log`;
   - T8 `Tasker Plugin (In-App, No Separate APK)` if accepting the small Locale
     plugin dependency.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
