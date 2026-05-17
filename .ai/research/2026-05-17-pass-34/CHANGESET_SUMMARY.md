<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 34 changeset summary — Shizuku 13.6.0 OEM allowlist

Date: 2026-05-17

Implementation commit: `feat(shizuku): warn on known-bad 13.6.0 OEMs`

## Roadmap item closed

- T5 `Shizuku 13.6.0 OEM Allowlist` in `ROADMAP.md`.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
  - Added `OemCompatibilityWarning`.
  - Added Shizuku 13.6.0-era runtime detection from installed Manager version first, falling back to the live Shizuku API version when package metadata is unavailable.
  - Added known-bad matrix checks for Transsion/Infinix/Tecno/Itel Android 15 ROMs, Mediatek platform tags, and Pixel 9 / Android 16 QPR1-class builds.
  - Added a pinned Shizuku 13.5.4 archive intent targeting the IzzyOnDroid/F-Droid repo APK path.
- `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java`
  - Reused the Shizuku warning banner for OEM compatibility warnings before the Android 17 generic warning.
  - Banner tap opens the Shizuku 13.5.4 archive.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/ModeOfOpsPreference.java`
  - Reused the existing Shizuku hint/action row to show OEM downgrade guidance with an "Open Shizuku 13.5.4 archive" action.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
  - Added the same compatibility guidance to the Shizuku provider health summary.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeModeDoctor.java`
  - Added a WARN probe when the active device/runtime matches the known-bad Shizuku 13.6.0 matrix.
- `app/src/main/res/values/strings.xml`
  - Added banner/summary/action/error copy for each known-bad family.
- `app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java`
  - Added unit coverage for the Shizuku runtime gate plus Transsion, Mediatek, Pixel 9, and old-platform negative cases.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `docs/architecture/01-privilege-providers.md`
  - Recorded shipped behavior and source evidence.

## Evidence

- `ROADMAP.md` S179: `RikkaApps/Shizuku#2048`, Transsion Android 15 / Shizuku 13.6.0 UserService crash.
- `ROADMAP.md` S180: XDA Pixel 9 Pro Android 16 QPR1 / Shizuku 13.6.0 stop reports and Mediatek downgrade note.
- `ROADMAP.md` S338: IzzyOnDroid F-Droid package page for Shizuku 13.6.0 and archived 13.5.4.r1049.0e53409.

## Verification

- `git diff --check` passed with only CRLF normalization warnings.
- `strings.xml` parsed successfully through PowerShell's XML reader.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
- Gradle could not run because no JDK is available in this shell:
  `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`
- Post-commit `git log -1 --oneline` confirmed the Shizuku OEM-warning commit.
- Post-commit `git fsck --no-progress --connectivity-only` reported only the known dangling blob/tag already present in this shared-folder checkout.
