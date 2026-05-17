<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 36 changeset summary — OS-revert detection banner

Date: 2026-05-17

Implementation commit: `feat(revert): detect OS reverted state changes`

## Roadmap item closed

- T9 `OS-Revert Detection Banner` in `ROADMAP.md`.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/revert/OsRevertMonitor.java`
  - Added the generic 30-second delayed verifier.
  - Added expected-vs-current probes for Doze allowlist state, freeze state,
    component enabled state, and AppOps mode.
  - Emits a single app-wide event with target, operation, expected state,
    current state, and a context hint when a re-poll disagrees.
- `app/src/main/java/io/github/muntashirakon/AppManager/BaseActivity.java`
  - Observes OS-revert events and surfaces an "OS reverted your change - see why"
    Snackbar.
  - The Snackbar action opens a Material detail dialog.
- `app/src/main/java/io/github/muntashirakon/AppManager/compat/DeviceIdleManagerCompat.java`
  - Schedules Doze allowlist re-polls after add/remove writes.
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/FreezeUtils.java`
  - Adds package/user freeze-state probing.
  - Schedules freeze/unfreeze re-polls after hide, suspend, and enabled-state
    freeze paths.
- `app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java`
  - Schedules component enabled-state re-polls after successful component writes.
- `app/src/main/java/io/github/muntashirakon/AppManager/compat/AppOpsManagerCompat.java`
  - Schedules AppOps mode re-polls after successful mode writes.
- `app/src/main/res/values/strings.xml`
  - Added Snackbar, detail-dialog, operation, state, and hint copy.
- `app/src/test/java/io/github/muntashirakon/AppManager/revert/OsRevertMonitorTest.java`
  - Added unit coverage for the monitor state-match predicates.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`
  - Recorded shipped behavior and the remaining deeper Doze-specific follow-up.

## Evidence

- `ROADMAP.md` S182: App Manager `#1956`, battery-optimization state reverts quickly after write.
- `ROADMAP.md` S183: Hail `#387`, frozen app state can be lost after unfreeze / launcher restore paths.

## Verification

- `strings.xml` parsed successfully through PowerShell's XML reader before commit.
- `git diff --check` passed before commit with only CRLF normalization warnings.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.revert.OsRevertMonitorTest`
- Gradle could not run because no JDK is available in this shell:
  `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`
- `git diff --cached --check` should pass before committing this batch.
- Post-commit verification should confirm the commit hash, branch-ahead state, and shared-folder fsck state.
