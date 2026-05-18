<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 128 — Default-app role restore rebinds

## Roadmap row

T6 **Default-App Role Re-Binding After Restore** is shipped.

## What changed

- Added `DefaultAppRoleBackupHelper`, covering the four default-app roles NG
  can safely restore for app packages: Dialer, SMS, Home, and Browser.
- `BackupMetadataV5.Metadata` now serializes `default_roles`; old backups load
  with an empty role list.
- `BackupOp` captures package role ownership at backup time through
  `cmd role get-role-holders --user`.
- `RestoreOp` attempts to rebind captured roles after restore through
  `cmd role add-role-holder --user`.
- `BackupManager` and `BatchOpsManager.Result` now carry pending default-role
  rebind requests back to the completion notification.
- `BatchOpsService` adds a **Review defaults** notification action when Android
  rejects any automatic role rebind.
- Added `DefaultAppRoleRestoreActivity`, an authenticated prompt listing the
  affected apps/roles and opening Android Default apps settings.
- Added focused JVM tests for role sanitization, supported-role filtering, and
  role-label mapping.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.DefaultAppRoleBackupHelperTest" --console=plain` passed.
- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.

## Notes

- The roadmap originally mentioned `RoleManager.createRequestRoleIntent(...)`.
  Android documents that public API as prompting for the calling application, so
  using it for restored packages would be wrong. NG now uses the documented
  `cmd role` arbitrary-holder path and falls back to a user-facing Default apps
  settings prompt when Android rejects the shell rebind.
