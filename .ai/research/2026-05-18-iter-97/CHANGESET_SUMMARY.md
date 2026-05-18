# Iter-97 Changeset Summary

Date: 2026-05-18

Roadmap item closed: T6 `WorkManager / JobDebugInfo Schedule Diagnostics` API-36 slice.

## Implementation

- Added `AutoBackupDiagnostics` to summarize the latest scheduled and manual WorkManager requests for Scheduled Auto-Backup.
- Settings -> Backup now includes diagnostics in the schedule status row and refreshes them when schedule settings change, on resume, and after worker completion.
- API 36+ builds match WorkManager's generated `JobInfo` by its WorkSpec extras and show current plus recent JobScheduler pending reasons.
- API 37 `JobDebugInfo.getPendingJobReasonStats()` remains split into a blocked roadmap tail because the project currently compiles with SDK 36.

## Files

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupDiagnostics.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupScheduler.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupWorker.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/BackupRestorePreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/AppPref.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupDiagnosticsTest.java`
- `ROADMAP.md`
- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.schedule.AutoBackupDiagnosticsTest --console=plain`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`
