<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 110 — SD-Maid-Style Warn-Before-Volume-Scan

## Roadmap item

Shipped T19 **SD-Maid-Style Warn-Before-Volume-Scan** for the implemented
recursive File Manager search path.

## Implementation

- Added `FmVolumeScanWarning`, a small File Manager helper that detects likely
  storage-volume roots before recursive search starts.
- Covered primary external storage, removable `/storage/XXXX-XXXX`, and
  `/mnt/media_rw/*` style roots while ignoring nested folders such as
  `/storage/emulated/0/Download`.
- Added a confirmation dialog in `FmFragment` before applying a non-empty search
  query on a volume root.
- Used `StorageStatsManager.getTotalBytes()` through `StorageManager` on
  Android O+ to estimate scan duration when the platform exposes the backing
  volume size, with a filesystem-total fallback.
- Added focused Robolectric coverage for root detection and duration rounding.

## Scope note

Storage Analysis is still a future T19 surface in this repository, so there was
no active Storage Analysis scan trigger to wire. The new helper is intentionally
package-private in the File Manager package today; a future Storage Analysis
screen should either reuse it directly after moving it to shared storage
utilities or mirror the same root-detection policy before whole-volume scans.
