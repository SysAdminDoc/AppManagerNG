<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 131

## Current state

- Branch: `main`
- Latest completed row: T6 **Profile Blocklist Editor Enumerates Backup Roots**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.profiles.AppsProfileViewModelTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

The Profiles add dialog now includes packages represented only by existing
backup roots. It merges live installed apps with validated latest backup
metadata, labels backup-only choices, keeps already-selected missing packages
visible, and renders stale profile rows with a fallback icon plus delete
affordance.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 131 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Wi-Fi Configurations Backup (Root)** | T6 | **Next** |

## Notes for the next pass

- Start in the backup type/category model and the root operation layer.
- The row is root-only system data backup. Keep it opt-in and avoid broadening
  normal app backup defaults.
- Check whether current backup metadata can represent non-package system-data
  entries before adding archive writers for Wi-Fi or Bluetooth directories.
