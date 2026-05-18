<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 129

## Current state

- Branch: `main`
- Latest completed row: T6 **Backup Scheduler Newest-Age Gate**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.schedule.AutoBackupSchedulerTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Scheduled Auto-Backup now filters the installed package/user list before
running the backup engine. The filter loads validated backup metadata, computes
the newest backup per `(packageName, userId)`, and skips only packages whose
newest backup is younger than the configured freshness window. Users can set
that window in Settings -> Backup; `0` days keeps the old "always back up"
behavior.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 129 is:

| Row | Tier | Status |
| --- | --- | --- |
| **CIFS / SMB Backup Streaming Hardening** | T6 | **Next** |

## Notes for the next pass

- Start by tracing backup archive writes through the current `Path`/SAF output
  stack and any stream/channel helpers used by `BackupOp`.
- The row is about SMB/CIFS provider corruption through short writes or
  buffering behavior. Prefer a narrow writer hardening/helper test over adding a
  protocol-specific SMB client.
