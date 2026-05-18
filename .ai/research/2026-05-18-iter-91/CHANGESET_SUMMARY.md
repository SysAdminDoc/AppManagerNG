<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-18 iter 91

## Created

- `app/src/main/java/io/github/muntashirakon/AppManager/dhizuku/DhizukuBridge.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/dhizuku/DhizukuBridgeTest.java`
- `.ai/research/2026-05-18-iter-91/*`

## Modified

- `AndroidManifest.xml` — declares Dhizuku API permission plus package/provider/request-action visibility.
- `OnboardingFragment` / `fragment_onboarding.xml` — surfaces a Dhizuku status signal when an install or active owner exists.
- `PrivilegeHealthPreferences` / `preferences_privilege_health.xml` — adds a Settings -> Privileges Dhizuku row and details dialog.
- `PrivilegeModeDoctor` — adds a Dhizuku probe to the copyable doctor report.
- `strings.xml` — adds Dhizuku status, diagnostics, and dialog copy.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — records the provider-detection slice and keeps full DPM operations blocked by the API/minSdk strategy.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.dhizuku.DhizukuBridgeTest --console=plain`

Note: a first test invocation ran concurrently with compile and failed with Gradle intermediate missing-class races. The same targeted test passed when rerun serially.
