<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 19

Pass 19 handled:

- T1 `floss` vs `full` Build Flavors (F-Droid Clean Path)

## Result

The app now builds in two distribution flavors. `floss` is the default flavor for
F-Droid/Izzy-style builds and disables optional online surfaces at compile time.
`full` keeps those surfaces available behind existing opt-in settings for GitHub
Releases / Obtainium users.

The implementation intentionally does **not** remove the manifest `INTERNET`
permission from `floss`, because both flavors still need local networking for
ADB-over-TCP, wireless pairing, and localhost privileged-server flows. The
feature flag only gates optional remote report/update surfaces.

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.DistributionFlavorTest`
   - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.DistributionFlavorTest`
   - `.\gradlew.bat :app:assembleFlossRelease`
   - `.\gradlew.bat :app:assembleFullRelease`
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify_reproducible_release.ps1`
2. On device/emulator, verify:
   - `floss` Settings -> Privacy disables "Use the Internet" with the FLOSS
     explanation;
   - `floss` VirusTotal settings show the FLOSS-disabled warning;
   - `floss` scanner does not attempt Pithus/VirusTotal/debloat-definition fetches;
   - `full` keeps the prior opt-in Internet behavior.
3. Continue roadmap work with the next non-blocked `Now` row. Good candidates:
   - T11 `Snapshot Bundle Export/Import`;
   - T4 `LocalServer Bootstrap Smoke Test`;
   - T4 `Support Info Bundle Composer`;
   - T9 `Privileged Op Audit Log`.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
