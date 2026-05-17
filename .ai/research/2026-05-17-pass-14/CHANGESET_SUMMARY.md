<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 14

## Roadmap item closed

- T7 `Filter: Permission Flags`

## Implementation

- Added `FilterablePermissionInfo`, the shared filter-facing model for requested
  permissions.
- `FilterablePermissionInfo.fromPackageInfo()` reads:
  - requested permission names;
  - `PackageInfo.REQUESTED_PERMISSION_GRANTED` grant state;
  - runtime permission flags through `PermissionCompat.getPermissionFlags()` when
    the active privilege path can read them;
  - permission source package and declaration/protection metadata through
    `PermissionCompat.getPermissionInfo()`.
- `IFilterableAppInfo` now exposes `getAllPermissionDetails()` with an empty
  default for older consumers.
- `FilterableAppInfo` and `ApplicationItem` both implement `getAllPermissionDetails()`
  so Finder/profile/main-list filter consumers share the same permission-state
  model.
- `PermissionsOption` now supports:
  - `granted`
  - `denied`
  - `custom`
  - `fixed`
  - `with_flags`
  - `without_flags`
- Added `PermissionsOptionTest` coverage for grant state, custom source, fixed
  flags, and raw flag include/exclude matching.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FilterablePermissionInfo.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FilterableAppInfo.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/IFilterableAppInfo.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/options/PermissionsOption.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/main/ApplicationItem.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/filters/options/PermissionsOptionTest.java`
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
