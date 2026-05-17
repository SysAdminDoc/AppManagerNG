<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 28

Pass 28 handled:

- T8 Hail-Style Auto-Freeze QuickSettings Tile

## Result

AppManagerNG now declares a Quick Settings tile named "Freeze profile". Users can
select a freeze-enabled profile from the Profiles list popup. The tile then runs
that profile in ON/freeze state through `ProfileApplierService`, preserving the
existing profile progress, notification, history, and freeze implementation.

## Verification still needed

1. Install/configure a local JDK and run:
   - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.profiles.QuickFreezeTileControllerTest`
2. On Android 7.0+:
   - create or edit a profile with Freeze enabled;
   - set it from Profiles -> profile overflow -> "Use for Quick Settings freeze tile";
   - add the "Freeze profile" QS tile;
   - tap it unlocked and verify the profile runs;
   - tap it from the lock screen and verify the unlock prompt appears before work starts;
   - clear the selected profile and verify the tile becomes unavailable / opens Profiles.

## Next exact steps

Continue roadmap work with the next non-blocked `Now` row. Good candidates:

- T5 Shizuku 13.6.0 OEM Allowlist;
- Eng-Debt Hidden-API Compatibility Harness;
- T11 GrapheneOS A16 background install confirmation patch reference follow-up
  if device verification becomes available.

## Known limitations

No local JDK is available in this shell, so Gradle verification remains blocked.
Push remains blocked because the remote is `SysAdminDoc/AppManagerNG` while the
current GitHub credentials authenticate as `MavenImaging` and `gh auth status`
reports that token as invalid.
