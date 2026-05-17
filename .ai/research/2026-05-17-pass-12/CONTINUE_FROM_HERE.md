<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 12

Pass 12 handled:

- T5 `VPN Plugin Flags Control` audited and parked as blocked.
- T7 `Finder: Description-Field Search` closed.

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.filters.options.BloatwareOptionTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify Finder -> filter editor:
   - add `bloatware` + `description_contains` with prose terms such as `nfc tags`;
   - verify matching packages appear when their debloat description contains the
     term;
   - verify regex mode uses the raw regex pattern.
3. Continue roadmap work with the next non-blocked row. Likely next candidates:
   - T7 `Finder: Uninstalled App Backups`;
   - T7 `Finder: Relevance-Based Search Scoring`;
   - T8 `Tasker Parameterized Intent API`.

## Known limitation

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
