<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 15

Pass 15 handled:

- T7 `Finder: Relevance-Based Search Scoring`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.filters.FinderRelevanceScorerTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify Finder result ordering with:
   - package-name `contains` / `starts_with` searches;
   - component-name `contains` / `starts_with` searches;
   - tracker-name searches where tracker matches are available;
   - mixed OR filters to confirm unrelated rows retain scan order when no
     relevance score is available.
3. Continue roadmap work with the next non-blocked row. Likely next candidates:
   - T8 `Routine Operations / Scheduler`;
   - T8 `Saved Filter Presets`;
   - T8 `Profile State Conditions`;
   - T8 `Schedule Result Notifications with Detail`.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
