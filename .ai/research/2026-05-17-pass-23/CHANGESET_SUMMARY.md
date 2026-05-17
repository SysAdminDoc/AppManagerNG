<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 23

## Roadmap item closed

- T5 Privileged-Shell Journal + DeathRecipient Replay

## Implementation

- Added `BatchOpsJournal`, a SharedPreferences-backed journal for active batch
  operations.
- `BatchOpsService` now records intent/executing state before `performOp(...)`
  and clears it only after the batch completes normally.
- Uncaught batch exceptions mark the journal interrupted and still surface the
  existing failed-result notification path.
- While Shizuku/Sui mode is active, `BatchOpsService` registers
  `Shizuku.addBinderDeadListener(...)` and marks the journal interrupted if the
  Shizuku binder dies during a batch.
- `MainActivity` checks for unfinished journal entries when no batch service is
  currently working and shows a recovery dialog with retry, not-now, and clear.
- Added `BatchOpsJournalTest` for persisted queue recovery and completion clear.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/batchops/BatchOpsJournal.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/batchops/BatchOpsService.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/batchops/BatchOpsJournalTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.batchops.BatchOpsJournalTest`

## External source used

- `https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/shizuku/Shizuku.java`
  confirms `OnBinderDeadListener`, `addBinderDeadListener(...)`, and
  `removeBinderDeadListener(...)`.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
