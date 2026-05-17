<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 13

## Roadmap item closed

- T7 `Finder: Uninstalled App Backups`

## Implementation

- `FinderViewModel` now opts into backup-only rows through
  `FilteringUtils.loadFilterableAppInfo(userIds, true)` after the PackageManager
  scan.
- Backup-only candidates come from AppManagerNG's backup metadata DB, are filtered
  to user IDs the current privilege path may inspect, and are validated by checking
  that the archive still exists.
- The selection skips package/user pairs already returned by
  `MATCH_UNINSTALLED_PACKAGES` and keeps only the latest backup per remaining
  package/user pair.
- Added `BackupFilterableAppInfo`, a synthetic `FilterableAppInfo` wrapper for
  archived uninstalled apps. It preserves backup label, version, system/has-code,
  keystore, and rule signals while reporting `isInstalled() == false`.
- Removed the stale FinderViewModel TODO now that backup-only rows are loaded by
  Finder's explicit filtering-utility opt-in.
- Added `FilteringUtilsTest` coverage for latest-backup selection, existing-row
  dedupe, missing-archive filtering, and per-user handling.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/filters/BackupFilterableAppInfo.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FilteringUtils.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FinderViewModel.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/filters/FilteringUtilsTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Targeted Gradle test attempt remained blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
