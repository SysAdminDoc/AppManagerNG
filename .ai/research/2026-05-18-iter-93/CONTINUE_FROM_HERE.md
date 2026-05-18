<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-18 iter 93

Iter 93 closed the T6 Scheduler Battery Optimization Auto-Fix row.

## Current state

- Scheduled Auto-Backup enablement now checks `SelfBatteryOptimization`.
- Privileged sessions silently attempt the existing `DEVICE_POWER` Doze exemption helper.
- No-privilege or failed auto-fix cases show a scheduled-backup-specific Android exemption prompt.
- The schedule status row shows battery optimization state as Exempt, Optimized, or Not applicable.

## Next exact step

Continue through the next unblocked roadmap row after scheduler battery handling.
Near-term T6 candidates are:

- `Launcher Shortcuts for Backup Schedules` — likely a one-tap "run scheduled backup now" shortcut unless NG first adds multiple schedule profiles.
- `Backup Progress Notifications` — needs deeper progress plumbing from the backup engine into notifications.
- `WorkManager / JobDebugInfo Schedule Diagnostics` — API 36/37 guarded history/diagnostic tail for the scheduler.

Pick the smallest row that can be completed without inventing a larger schedule-profile model.
