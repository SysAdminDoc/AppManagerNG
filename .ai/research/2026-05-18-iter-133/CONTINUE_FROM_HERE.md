<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here - after iter 133

## Current state

- Branch: `main`
- Latest completed row: T6 **Squashfs Writer Header Validation** parked as
  blocked by absent SquashFS backend.
- Validation completed:
  - SquashFS source/dependency searches.
  - Backup archive path search confirming `TarUtils` is the current writer and
    extractor boundary.

## What just changed

No production code changed. `ROADMAP.md`, `CHANGELOG.md`,
`PROJECT_CONTEXT.md`, and this research handoff now record that the SquashFS
row is not actionable against current NG source because the project has no
SquashFS writer, external binary integration, or mount/read path.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 133 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Per-App Volume via AppOps `OP_AUDIO_VOLUME`** | T9 | **Next** |

## Notes for the next pass

- Start in the existing AppOps editor and compat layers.
- Verify whether NG already distinguishes package UID, shared UID, and
  per-user AppOps rows before wiring `OP_AUDIO_VOLUME`.
- Preserve current AppOps UX patterns; this should be a targeted binder/op
  capability slice, not a new standalone volume manager.
