<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 109 — Material Files Checksum Properties Tab Audit

## Roadmap item

Closed T13 **Material Files Checksum Properties Tab** as already implemented.

## Evidence

- `FilePropertiesDialogFragment` binds `R.id.checksums` in `dialog_file_properties.xml`.
- Tapping that properties-sheet action opens `ChecksumsDialogFragment.getInstance(path)` for the same arbitrary File Manager `Path`.
- `ChecksumsDialogFragment` computes CRC32, MD5, SHA-1, SHA-256, SHA-512, SHA3-256, and SHA3-512.
- MD5 and SHA-1 are labeled unreliable in the checksum dialog.

## Decision

No code change was required. The roadmap row was stale after the earlier File Hash Display work because the checksums entry is already available from the File Manager properties surface, not only from APK info.

