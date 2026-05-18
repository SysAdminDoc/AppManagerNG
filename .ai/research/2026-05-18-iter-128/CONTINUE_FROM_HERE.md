<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 128

## Current state

- Branch: `main`
- Latest completed row: T6 **Default-App Role Re-Binding After Restore**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.backup.DefaultAppRoleBackupHelperTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Backup metadata now captures Dialer, SMS, Home, and Browser role ownership.
Restore attempts privileged `cmd role add-role-holder` rebinding for the
restored package/user after package/data/rules restore completes. If Android
rejects a rebind, batch restore completion exposes a **Review defaults**
notification action that lists the affected packages/roles and opens Android
Default apps settings.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 128 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Backup Scheduler Newest-Age Gate** | T6 | **Next** |

## Notes for the next pass

- Start in the scheduled backup code added in iter 92, especially
  `AutoBackupWorker` and any helper that decides whether a package already has
  a current backup.
- The intended gate is "newest backup is younger than the configured minimum
  age" rather than "any backup exists".
