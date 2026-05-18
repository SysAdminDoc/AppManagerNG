<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-18 iter 93

## Modified

- `BackupRestorePreferences.java` — scheduled-backup enablement now checks AppManagerNG's battery-optimization exemption. It reuses `SelfBatteryOptimization.autoFixIfPossible()` when `DEVICE_POWER` is available and falls back to a scheduled-backup-specific Android exemption prompt when it is not.
- `strings.xml` — adds scheduled-backup battery status labels, prompt copy, and auto-fix success copy.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — closes the T6 Scheduler Battery Optimization Auto-Fix row and records the follow-up state.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Carryover

- WorkManager / JobDebugInfo schedule diagnostics are still open.
- Per-schedule launcher shortcuts remain open if NG later grows more than one schedule profile.
