<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 7

## Created

- `app/src/main/java/io/github/muntashirakon/AppManager/self/SelfBatteryOptimization.java`
- `.ai/research/2026-05-17-pass-7/*`

## Modified

- `TroubleshootingPreferences.java` — refactored the manual battery-optimization
  entry onto the shared self-exemption helper while preserving the system Settings
  fallback for unprivileged devices.
- `ProfileApplierService.java` — attempts the privileged Doze whitelist auto-fix
  before profile/routine execution begins.
- `BatchOpsService.java` — attempts the privileged Doze whitelist auto-fix before
  long-running backup, backup-APK, restore, and backup-import batch operations.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — marks T5's root/ADB
  battery-optimization auto-fix row shipped and records the remaining T6 scheduler
  prompt gap.

## Verification

- XML parse check passed for `app/src/main/res/values/strings.xml`.
- `git diff --check` passed before documentation updates.
- Gradle tests could not run locally: no `JAVA_HOME` / no `java` command available.
