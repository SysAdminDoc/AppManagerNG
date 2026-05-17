<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 22

## Roadmap item closed

- T9 Privileged Op Audit Log

## Implementation

- Audited the existing `op_history` implementation and closed the stale roadmap
  gap: Room entity/DAO, manager, activity, viewer filters, rerun/share/delete
  actions, JSON/CSV/text export, and Settings -> Privacy retention controls were
  already present.
- Added `exit_code` to `OperationJournalMetadata`:
  - batch/profile histories store `0` for success and `1` for failure;
  - installer histories store the platform `PackageInstallerCompat` status code.
- Added `bootstrap_signature` to new operation-history metadata when a remembered
  LocalServer bootstrap signature is available.
- Surfaced `exit_code` and `bootstrap_signature` in operation details plus
  JSON/CSV exports.
- Extended `OperationHistoryExporterTest` fixtures/assertions for the new fields.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OperationJournalMetadata.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OpHistoryItem.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OperationHistoryExporter.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/history/ops/OperationHistoryExporterTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.history.ops.OperationHistoryExporterTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
