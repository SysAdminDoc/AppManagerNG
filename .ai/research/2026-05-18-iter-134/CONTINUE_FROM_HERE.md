<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here - after iter 134

## Current state

- Branch: `main`
- Latest completed row: T9 **Per-App Volume via AppOps `OP_AUDIO_VOLUME`**
- Focused validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.compat.AppOpsManagerCompatTest" --console=plain`

## What just shipped

AppManagerNG now treats Android's audio-volume AppOps family as first-class:
`AppOpsManagerCompat` names and groups the supported stream-volume ops, AppOps
mode writes stay UID-scoped for normal AOSP ops, and App Details -> App ops can
apply one mode to the full audio-volume group for the selected app.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 134 is:

| Row | Tier | Status |
| --- | --- | --- |
| **InstallerX-Revived Privilege-Elevation Cascade** | T11 | **Next** |

## Notes for the next pass

- Start in the installer session/options flow, especially
  `PackageInstallerActivity`, installer services, and privilege-selection code.
- Preserve existing destructive install/reinstall safety prompts.
- The target is a fallback/cascade UX, not a rewrite of package parsing or
  split-selection logic.
