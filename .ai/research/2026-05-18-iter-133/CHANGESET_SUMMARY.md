<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 133 - SquashFS writer header-validation triage

## Roadmap row

T6 **Squashfs Writer Header Validation** is parked.

## Audit result

- Source and Gradle searches found no SquashFS writer, `mksquashfs` /
  `unsquashfs` integration, `.sqfs` / `.sqsh` extension handling, or
  file-manager mount path.
- The live backup format stores `tar_type` in `BackupMetadataV5`.
- APK, app data, keystore, and Android System data backup paths write archive
  files through `TarUtils.createDurable()`.
- Restore paths read those same archive families through `TarUtils.extract()`.

## Decision

The upstream App Manager issue remains useful context, but AppManagerNG does
not currently emit SquashFS images. A magic-byte writer fix or mksquashfs
round-trip test would be dead coverage until a future restic/SquashFS-style
backup backend exists.

The roadmap now preserves the requirement as a blocked acceptance criterion:
if NG adds a SquashFS writer later, that backend must ship a header fixture and
external-tool round-trip validation against the relevant `mksquashfs` binary.

## Verification

- `rg -n "squash|mksquash|unsquash|sqfs|sqsh|SQUASH" app libcore libserver server hiddenapi docs research .ai ROADMAP.md CHANGELOG.md PROJECT_CONTEXT.md -S`
- `rg -n "squash|mksquash|commons-compress|filesystem" build.gradle settings.gradle versions.gradle app/build.gradle libcore app/src/main -S`
- `rg -n "createDurable|TarUtils\\.create|TarUtils\\.extract|tarType|DATA_BACKUP_SPECIAL_SYSTEM_PREFIX" app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupOp.java app/src/main/java/io/github/muntashirakon/AppManager/backup/RestoreOp.java app/src/main/java/io/github/muntashirakon/AppManager/backup/struct/BackupMetadataV5.java app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupManager.java -S`
