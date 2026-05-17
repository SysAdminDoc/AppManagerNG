<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 35 changeset summary — Shizuku root-backed avoidance

Date: 2026-05-17

Implementation commit: `feat(shizuku): avoid root-backed auto mode`

## Roadmap item closed

- T5 `Shizuku Root-Backed Avoidance for Banking Apps` in `ROADMAP.md`.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
  - Added Shizuku uid classification helpers for root-backed sessions.
  - Added an Auto-mode avoidance helper that only skips root-backed Shizuku when ADB is available.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java`
  - Updated Auto mode so it does not default to root-backed Shizuku when local ADB is available.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/ModeOfOpsPreference.java`
  - Labels authorized Shizuku as `root-backed` when the binder reports uid 0.
  - Reuses the Shizuku hint/action row for the banking / Play Integrity side-effect warning.
  - Adds a one-tap switch into Wireless ADB or ADB-over-TCP.
- `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java`
  - Shows the root-backed Shizuku warning before lower-severity Shizuku warnings.
  - Routes the warning action into the Wireless ADB setup flow.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
  - Appends root-backed Shizuku guidance to the Shizuku health summary.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeModeDoctor.java`
  - Emits a WARN probe for root-backed Shizuku sessions with the ADB fallback hint.
- `app/src/main/res/values/strings.xml`
  - Added root-backed Shizuku status, warning, action, tooltip, and health-copy strings.
- `app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java`
  - Added unit coverage for uid classification and Auto-mode avoidance.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `docs/architecture/01-privilege-providers.md`
  - Recorded shipped behavior and architecture notes.

## Evidence

- `ROADMAP.md` S181: `RikkaApps/Shizuku#2052`, KernelSU + root-backed Shizuku service causing banking / Play Integrity-strict app crashes even without app-level Shizuku permission.

## Verification

- `strings.xml` parsed successfully through PowerShell's XML reader before commit.
- `git diff --check` passed before commit.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
- Gradle could not run because no JDK is available in this shell:
  `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`
- `git diff --cached --check` passed before commit.
- Post-commit verification should confirm the commit hash, branch-ahead state, and shared-folder fsck state.
