<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 131 — Profile blocklist backup-root enumeration

## Roadmap row

T6 **Profile Blocklist Editor Enumerates Backup Roots** is shipped.

## What changed

- Replaced the Profiles add-dialog data model with `SelectablePackageItem`, so
  installed apps, backup-only apps, and already-selected missing packages can
  share one picker path.
- `AppsProfileViewModel.loadInstalledApps()` now refreshes latest validated
  backup metadata through `BackupUtils.storeAllAndGetLatestBackupMetadata()`
  and merges backup-only package rows without duplicating installed packages.
- Existing selected packages that are missing from both the live package list
  and backup metadata remain visible, preserving delete/review affordances for
  stale blocklist entries.
- The add dialog labels backup-only rows as **Backup only** instead of **User**
  or **System**.
- Profile list rows now render a fallback icon when no live app or filterable
  metadata exists, and the delete long-press is available for stale entries.
- Added focused JVM coverage for backup-only merge behavior and selected
  missing-package retention.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.profiles.AppsProfileViewModelTest" --console=plain` passed.

## Notes

- This does not add a new backup-root scanner. It reuses the existing validated
  backup metadata refresh path that already enumerates configured backup roots.
- Backup-only profile rows intentionally use a neutral fallback icon because
  the app icon is not available after uninstall.
