<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 25

Pass 25 handled:

- T5 Shizuku Trusted-WLAN Auto-Start Awareness

## Result

When Android 13+ has Shizuku Manager 13.6.0+ installed but the Shizuku binder is
not running, AppManagerNG now shows a "Configure auto-start in Shizuku" action in
both Settings -> Operating Mode and the replayable onboarding Shizuku card. The
action attempts the roadmap component first and falls back to Shizuku's launcher
or Android's app-info screen if the installed Shizuku build does not expose a
stable auto-start activity.

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
2. On an Android 13+ device with Shizuku Manager 13.6.0+ installed but stopped:
   - open Settings -> Operating Mode;
   - verify the Shizuku capability row shows the auto-start hint and button;
   - tap the button and confirm it opens Shizuku or its app-info fallback.
3. Replay onboarding and verify the Shizuku card shows the same action only in
   the stopped-binder state.
4. Continue roadmap work with the next non-blocked `Now` row. Good candidates:
   - T6 JobScheduler quota stop-reason surfacing;
   - T8 Hail-style Auto-Freeze QuickSettings Tile;
   - Eng-Debt Apktool 3.0.2 migration.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
