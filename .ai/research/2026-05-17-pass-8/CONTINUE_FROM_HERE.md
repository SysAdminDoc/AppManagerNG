<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 8

Pass 8 closed:

- T5 `Cross-User Package State Detection`
- T7 `Finder: Multi-User Scope`

## Next exact steps

1. Install/configure a JDK and run a broader app-list/Finder test slice. Minimum:
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On a multi-user Android device or emulator, verify:
   - app installed/enabled for user 0 and disabled for user 10 shows both states
     in the main list;
   - tapping a multi-user app shows each selectable user with state;
   - Finder returns matches from work/secondary profiles and labels each result
     with the correct user id.
3. Continue roadmap work with the next non-blocked row. Good candidates:
   - T5 `Auto-Update Debloat Definitions`
   - T4 `Mode Self-Test "Doctor"` (larger)
   - T5 `Restricted Settings Unlock Walkthrough` (depends on diagnostics surface)

## Known limitation

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
