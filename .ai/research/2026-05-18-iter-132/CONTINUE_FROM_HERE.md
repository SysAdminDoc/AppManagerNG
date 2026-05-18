<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 132

## Current state

- Branch: `main`
- Latest completed row: T6 **Wi-Fi Configurations Backup (Root)**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.SystemDataBackupTest" --tests "io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptionsTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Root/system mode now exposes a System data backup category for the Android
System package. System data backups capture supported Wi-Fi, Bluetooth, and
account database roots through NG's existing durable tar, encryption, checksum,
metadata, and retention path. Restore handles those special roots without
clearing `android` package data.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 132 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Squashfs Writer Header Validation** | T6 | **Next** |

## Notes for the next pass

- Start by locating NG's squashfs writer and any existing fixture or
  round-trip tests before adding new binary comparisons.
- The row calls for validating AM-generated squashfs headers against a
  `mksquashfs 4.6` reference; confirm whether the binary is available locally
  before deciding between an executable-backed test and a fixture-backed parser
  test.
