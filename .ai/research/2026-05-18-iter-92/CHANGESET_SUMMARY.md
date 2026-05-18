<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-18 iter 92

## Created

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupScheduler.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupWorker.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupSchedulerTest.java`
- `.ai/research/2026-05-18-iter-92/*`

## Modified

- `versions.gradle` / `app/build.gradle` — add WorkManager 2.10.5 and a compile-only Guava pin so app code can compile against WorkManager's exposed `ListenableFuture` API without packaging a duplicate standalone `listenablefuture` jar or raising minSdk.
- `AppPref` / `Prefs.BackupRestore` — add scheduled-backup enabled, time, charging, network, last-run, and last-result preferences.
- `BackupRestorePreferences` / `preferences_backup_restore.xml` — expose enable, time, charging, network, run-now, and status rows in Settings -> Backup.
- `strings.xml` — add scheduled-backup settings, worker result, and notification copy.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — record the core scheduler slice and keep API 36/37 quota diagnostics open as the next tail.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.schedule.AutoBackupSchedulerTest --console=plain`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Carryover

- API 36+ WorkManager / JobScheduler stop and pending-reason history still needs to be surfaced in scheduled-backup history/result diagnostics.
- API 37+ `JobDebugInfo.getPendingJobReasonStats()` remains a guarded follow-up.
