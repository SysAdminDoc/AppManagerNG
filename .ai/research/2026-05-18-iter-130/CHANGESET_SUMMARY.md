<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 130 — CIFS / SMB backup streaming hardening

## Roadmap row

T6 **CIFS / SMB Backup Streaming Hardening** is shipped.

## What changed

- Added `TarUtils.createDurable()` for backup archive creation.
- Routed APK, data, and keystore backup tar creation through the durable path.
- Hardened `SplitOutputStream` so writes are bounded to 256 KiB provider chunks
  and split precisely across configured split-file boundaries.
- Durable split-file writes now try to open a provider file descriptor, flush and
  fsync each chunk when that descriptor is available, and verify the final
  provider-reported byte count on close.
- Descriptor-less providers still use bounded writes and close-time byte-count
  verification through the regular `Path.openOutputStream()` fallback.
- Preserved the existing large APK split-hash fixture test and added a boundary
  regression that writes one oversized provider buffer, re-reads all split
  files, and verifies byte-for-byte reconstruction.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.io.SplitOutputStreamTest" --console=plain` passed.
- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.

## Notes

- No SMB client dependency was added. The hardening lives at the provider stream
  boundary used by SAF-backed SMB/CIFS document providers.
- `SplitOutputStream` keeps the old constructor behavior for non-backup call
  sites; only backup tar creation opts into descriptor fsync and byte-count
  verification.
