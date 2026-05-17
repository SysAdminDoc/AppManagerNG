<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 13

Pass 13 handled:

- T7 `Finder: Uninstalled App Backups`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.filters.FilteringUtilsTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify Finder with at least one AppManagerNG backup whose
   app is no longer installed:
   - backup-only package appears in Finder results;
   - the row shows `not installed` with the correct user ID;
   - package-name, app-label, version, app-type, and backup filters match the
     synthetic row as expected;
   - installed or PackageManager data-only rows are not duplicated.
3. Continue roadmap work with the next non-blocked row. Likely next candidates:
   - T7 `Finder: Relevance-Based Search Scoring`;
   - T7 `Filter: Permission Flags`;
   - T8 `Tasker Parameterized Intent API`.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
