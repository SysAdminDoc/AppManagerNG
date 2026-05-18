<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 130

## Current state

- Branch: `main`
- Latest completed row: T6 **CIFS / SMB Backup Streaming Hardening**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.io.SplitOutputStreamTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Backup tar creation now opts into durable split-stream writes. APK, data, and
keystore archives write bounded 256 KiB chunks to SAF-backed providers, fsync
descriptor-backed destinations per chunk, verify final split-file byte counts,
and keep split boundary tests green.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 130 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Profile Blocklist Editor Enumerates Backup Roots** | T6 | **Next** |

## Notes for the next pass

- Start in the profile editor/blocklist UI and model code, then trace how backup
  roots are discovered through `BackupUtils` / `BackupManager`.
- The row is narrow: include packages represented only by existing backup-root
  directories so a user can re-blocklist a currently uninstalled package.
