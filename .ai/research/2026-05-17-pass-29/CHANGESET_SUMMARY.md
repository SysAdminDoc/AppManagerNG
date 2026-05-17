<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 29 Changeset Summary — Hidden-API Compatibility Harness

Date: 2026-05-17

## Roadmap item

Closed the Eng-Debt **Hidden-API Compatibility Harness** row from iter-19.

## What changed

- Added `scripts/generate-hidden-api-baseline.ps1`.
  - Parses `hiddenapi/src/main/java`.
  - Skips helper/annotation-only sources that are not runtime hidden APIs.
  - Writes `app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json`.
- Added the initial androidTest baseline:
  - 128 hidden-API class descriptors.
  - 1,120 runtime members.
- Added `HiddenApiDescriptorBaselineTest`.
  - Verifies the baseline schema.
  - Verifies the baseline covers every relevant hiddenapi source file.
  - Keeps future stub refreshes from silently omitting a file.
- Added `HiddenApiCompatibilityInstrumentedTest`.
  - Loads the baseline from androidTest assets.
  - Applies `HiddenApiBypass.addHiddenApiExemptions("L")` on API 28+.
  - Reflectively probes active-SDK classes, fields, and methods.
  - Writes `hidden-api-compat-sdk<api>.json` under the test app's external files or cache dir.
  - Fails on required missing APIs; reports deprecated removals as warnings.
- Updated `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and
  `docs/architecture/03-hidden-api-bypass.md`.

## Important correction

The roadmap and architecture doc previously claimed `app/src/main/assets/api/api-versions-*.json`
assets were already bundled. That was stale. The current repo had no `app/src/main/assets/api`
directory and no `api-versions` JSON. The harness now uses `app/src/androidTest/assets/api/`
because these descriptors are test data, not runtime app assets.

## Verification

- Regenerated the descriptor with:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/generate-hidden-api-baseline.ps1`
- Parsed the generated descriptor with PowerShell `ConvertFrom-Json`:
  - `classes=128`
  - `members=1120`
- Full Gradle/JVM/instrumented execution is still blocked in this shell because no JDK is installed and `JAVA_HOME` is unset.
  - Attempted `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest`.
  - Attempted `.\gradlew.bat :app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.muntashirakon.AppManager.compat.HiddenApiCompatibilityInstrumentedTest`.

## Files changed

- `scripts/generate-hidden-api-baseline.ps1`
- `app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json`
- `app/src/androidTest/java/io/github/muntashirakon/AppManager/compat/HiddenApiCompatibilityInstrumentedTest.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/compat/HiddenApiDescriptorBaselineTest.java`
- `docs/architecture/03-hidden-api-bypass.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`
