<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 129 — Backup scheduler newest-age gate

## Roadmap row

T6 **Backup Scheduler Newest-Age Gate** is shipped.

## What changed

- Added a Scheduled Auto-Backup freshness window preference in Settings ->
  Backup: **Skip if backup is newer than**.
- Added `Prefs.BackupRestore.getScheduledBackupMinimumAgeDays()` backed by a
  new persisted preference key and a one-day default.
- `AutoBackupScheduler` now has a pure selection helper that:
  - clamps the configured freshness window,
  - picks the newest valid backup per package/user,
  - keeps stale, missing, and unknown-timestamp backups due,
  - treats `0` days as the explicit "Always back up" opt-out.
- `AutoBackupWorker` now filters installed package/user pairs through that
  selector before invoking the existing batch backup engine.
- Scheduled-backup result messages now include skipped-recent counts for
  all-skipped, success-with-skips, and partial-with-skips cases.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.schedule.AutoBackupSchedulerTest" --console=plain` passed.
- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.

## Notes

- The gate is per `(packageName, userId)`, not package-only, so a recent owner
  backup for one Android user does not suppress a stale or missing backup for
  another user.
- Existing backup rows with `backupTime <= 0` are deliberately not treated as
  fresh. They remain due instead of becoming permanent skip records.
