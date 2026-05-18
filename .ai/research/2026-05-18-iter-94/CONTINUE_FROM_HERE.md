# Continue From Here

Iter-94 closed the scheduled-backup launcher shortcut row.

Recommended next roadmap candidates:

1. T6 `Backup Progress Notifications`
   - Add richer ongoing foreground progress for scheduled/manual backups.
   - Likely touches `AutoBackupWorker`, `BatchOpsManager`, and notification helpers.
2. T6 `WorkManager / JobDebugInfo Schedule Diagnostics`
   - Add stop-reason and quota/defer diagnostics for scheduled backup runs.
   - API 36/37 guards are required.
3. T6 `Separated Active/Paused Schedule Lists`
   - Park or reframe unless multiple schedule profiles are added first; current implementation has one global schedule.

