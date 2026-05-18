# Iter-95 Changeset Summary

Date: 2026-05-18

Roadmap item closed: T6 `Backup Progress Notifications`.

## Code

- `AutoBackupWorker` now creates a Worker-specific `ProgressHandler` and passes it into `BatchOpsManager.performOp(...)`.
- The scheduled-backup foreground notification now shows app-count progress, current app stage/label, and ETA when enough elapsed runtime exists.
- API 36+ foreground notifications use platform `Notification.ProgressStyle` segments and current-progress point markers; lower APIs keep the existing `NotificationCompat#setProgress()` fallback.
- `BatchOpsManager.newSubProgress(...)` initializes non-`NotificationProgressHandler` sub-progress handlers with the current operation title, allowing Worker progress to display the active app label without changing `BackupManager`.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`
