<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 25

## Roadmap item closed

- T5 Shizuku Trusted-WLAN Auto-Start Awareness

## Implementation

- Added trusted-WLAN auto-start helpers to `ShizukuBridge`.
- The helper gates the affordance on:
  - Android 13+;
  - installed Shizuku Manager version `>=13.6.0`;
  - Shizuku binder currently stopped.
- Operating Mode now shows a trusted-WLAN hint and "Configure auto-start in
  Shizuku" action when the gate passes.
- The replayable onboarding Shizuku card shows the same action beside the
  existing v13.6.0+ auto-start tip.
- The launch intent first tries the roadmap-tracked
  `moe.shizuku.privileged.api/.AUTO_START` component and falls back to Shizuku's
  launcher or Android app-info screen when that component is not exported in the
  installed build.
- Extended `ShizukuBridgeTest` for Android-version, Shizuku-version, and
  stopped-binder gating.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/ModeOfOpsPreference.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java`
- `app/src/main/res/layout/fragment_mode_of_ops.xml`
- `app/src/main/res/layout/fragment_onboarding.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`

## External sources used

- `https://github.com/RikkaApps/Shizuku/releases` confirms v13.6.0 added
  Android 13+ trusted-WLAN auto-start without root.
- `https://raw.githubusercontent.com/RikkaApps/Shizuku/v13.6.0/manager/src/main/AndroidManifest.xml`
  was checked for exported Shizuku activities and justifies the
  launcher/app-info fallback.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
