<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 14

Pass 14 handled:

- T7 `Filter: Permission Flags`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.filters.options.PermissionsOptionTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify Finder -> filter editor:
   - `permissions` + `granted` matches apps with granted requested permissions;
   - `permissions` + `denied` matches requested permissions without the granted
     bit;
   - `permissions` + `custom` matches non-platform or unknown requested
     permissions;
   - `permissions` + `fixed` / `with_flags` / `without_flags` behave when the
     active privilege path can read runtime permission flags.
3. Continue roadmap work with the next non-blocked row. Likely next candidates:
   - T7 `Finder: Relevance-Based Search Scoring`;
   - T8 `Tasker Parameterized Intent API`;
   - T8 `Dynamic Quick Settings Tiles`.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
