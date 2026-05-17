<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 16

## Roadmap item closed

- Iter-22 / T8 `Broadcast Intent API`

## Implementation

- Added `AutomationIntents`, the canonical action/extra/permission constants for
  `io.github.sysadmindoc.AppManagerNG.action.*`.
- Added `AutomationReceiver`, exported behind the manifest-level signature
  permission `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION`.
- Batch package actions route through `BatchOpsService`:
  - freeze / unfreeze;
  - force stop;
  - clear cache / clear data;
  - uninstall;
  - backup / restore;
  - component disable / enable.
- Profile execution routes through `ProfileApplierService` by loading the
  requested profile id and building a `ProfileQueueItem`.
- Install-from-URI handoff opens the existing package installer activity.
- Tracker-scan handoff opens App Details with the tracker-sort view.
- `docs/intent-api.md` now documents the shipped action namespace, extras,
  signature-permission gate, and Tasker-plugin broker limitation.
- Added `AutomationIntentsTest` coverage for action recognition, batch-op mapping,
  and component-name normalization.

## Files changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationIntents.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationReceiver.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfileQueueItem.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/automation/AutomationIntentsTest.java`
- `docs/intent-api.md`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed after code and documentation updates.
- Targeted Gradle test attempt remained blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
