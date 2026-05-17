<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 37 changeset summary — Backup-aware Doze allowlist diff banner

Date: 2026-05-17

Implementation commit: `feat(revert): explain Doze allowlist reverts`

## Roadmap item closed

- T5 `Backup-Aware Doze Allowlist Diff Banner` in `ROADMAP.md`.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/revert/DozeAllowlistDiagnostics.java`
  - Added Doze-specific config snapshotting for legacy `device_idle_constants`.
  - Added reflection-based snapshotting for `DeviceConfig device_idle` on Android Q+ without compile-time hidden API coupling.
  - Added one-line diff generation for changed, unchanged, empty, and DeviceConfig-backed Doze config states.
  - Added policy-hint classification for user apps, Samsung firmware, system apps, and unknown packages.
- `app/src/main/java/io/github/muntashirakon/AppManager/revert/OsRevertMonitor.java`
  - Doze allowlist probes now wait 60 seconds after the write and enrich only the Doze revert event with the new config diff.
  - Freeze, component, and AppOps probes keep the generic 30-second post-write verifier.
- `app/src/main/res/values/strings.xml`
  - Added Doze diff detail copy and policy-hint strings.
- `app/src/test/java/io/github/muntashirakon/AppManager/revert/DozeAllowlistDiagnosticsTest.java`
  - Added unit coverage for key-value parsing and diff summary generation.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`
  - Recorded shipped behavior, source appendix additions, and the pass-37 context.

## Evidence

- `ROADMAP.md` S182: App Manager `#1956`, battery-optimization state reverts shortly after a successful write.
- `ROADMAP.md` S339: AOSP `DeviceIdleController.Constants` documents the legacy `Settings.Global#DEVICE_IDLE_CONSTANTS` key-value list.
- `ROADMAP.md` S340: AOSP `DeviceConfig.NAMESPACE_DEVICE_IDLE = "device_idle"` documents the modern namespace AppManagerNG snapshots when present.

## Verification

- `strings.xml` parsed successfully through PowerShell's XML reader before commit.
- `git diff --check` passed before commit with only CRLF normalization warnings.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.revert.DozeAllowlistDiagnosticsTest`
- Gradle could not run because no JDK is available in this shell:
  `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`
- `git diff --cached --check` should pass before committing this batch.
- Post-commit verification should confirm the commit hash, branch-ahead state, and shared-folder fsck state.
