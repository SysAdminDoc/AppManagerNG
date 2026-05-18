<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 132 — Wi-Fi configurations backup (root)

## Roadmap row

T6 **Wi-Fi Configurations Backup (Root)** is shipped.

## What changed

- Added `BackupFlags.BACKUP_SYSTEM_DATA` and surfaced it only when NG is
  operating as root/system.
- Bound system-data backup/restore to the Android System package (`android`) so
  normal app backups cannot accidentally duplicate global Wi-Fi, Bluetooth, or
  account state.
- Added `SystemDataBackup` descriptors for:
  - `/data/misc/wifi`
  - `/data/misc/apexdata/com.android.wifi`
  - `/data/misc/bluetooth`
  - `/data/misc/bluedroid`
  - `/data/misc/apexdata/com.android.btservices`
  - `/data/system_ce/<user>` filtered to `accounts_ce.db*`
  - `/data/system_de/<user>` filtered to `accounts_de.db*`
- `BackupOp` now stores system-data descriptors as special data tokens and
  writes them through the existing durable tar/encryption/checksum path.
- `RestoreOp` now recognizes system-data tokens, skips
  `clearApplicationUserData("android")`, extracts only the captured roots, and
  runs `restorecon` for the restored system locations.
- Backup filters can now match the System data backup flag.
- Added focused `SystemDataBackupTest` coverage for token detection,
  user-scoped account path resolution, and app-scoped flag sanitization.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.SystemDataBackupTest" --tests "io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptionsTest" --console=plain` passed.

## Notes

- This preserves the existing backup file format shape by using `special:`
  data-directory tokens, matching the existing ADB-data marker pattern.
- The implementation intentionally does not broaden ordinary app backups:
  `BackupManager` strips System data from non-`android` packages and strips
  app-scoped content flags from `android` System data jobs.
