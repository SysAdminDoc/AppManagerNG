<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 9

Pass 9 closed:

- T5 `Auto-Update Debloat Definitions`
- Duplicate iter-addition row `UAD-Style Auto-Fetch Debloat Definitions`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.debloat.DebloatDefinitionsUpdaterTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify:
   - Settings -> Privacy shows `Update debloat definitions on launch` disabled
     until "Use the Internet" is enabled.
   - With the opt-in enabled, launch performs at most one update check per 24h.
   - Debloater still loads bundled recommendations when the manifest URL is
     unavailable or checksum validation fails.
3. Continue roadmap work with the next non-blocked row. Good candidates:
   - T5 `Privilege Health-Check Screen` (parent for Android 16 capability dropping
     and VPN plugin flag rows).
   - T7 `Finder: Description-Field Search` (now easier because debloat metadata
     can refresh independently).
   - T7 `Multi-Mirror Debloat-Defs Fetcher` (follow-up to this pass; requires
     signed/multi-origin manifest design).

## Known limitation

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
