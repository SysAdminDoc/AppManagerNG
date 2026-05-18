<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-18 iter 92

Iter 92 closed the core T6 Scheduled Auto-Backup implementation.

## Current state

- Settings -> Backup exposes:
  - Scheduled auto-backup enablement
  - Backup time picker
  - Require-charging switch
  - Network condition picker
  - Manual run-now action
  - Last-run/result status row
- `AutoBackupScheduler` owns WorkManager schedule/cancel/manual-run plumbing and pure helpers for delay calculation, value clamping, and network mapping.
- `AutoBackupWorker` enumerates installed package/user pairs and runs the existing backup engine with current backup flags plus `BACKUP_MULTIPLE`.
- WorkManager is pinned to 2.10.5 because newer lines raise the Android floor above NG's API-21 policy.

## Next exact step

Continue to the next unblocked roadmap row after the core scheduler slice. The
nearest scheduler-related tail is `WorkManager / JobDebugInfo Schedule
Diagnostics`, but it requires API 36/37-specific instrumentation and may need a
broader schedule-history UI. If that is too broad for the next slice, pick the
next smaller T6 row that can be completed independently.

## Open scheduler tail

- Record/display `WorkInfo.getStopReason()` and `JobScheduler#getPendingJobReasonsHistory()` where available.
- Surface API 37 `JobDebugInfo.getPendingJobReasonStats()` in schedule diagnostics.
- Consider a richer schedule-history model if single last-run/result prefs are insufficient.
