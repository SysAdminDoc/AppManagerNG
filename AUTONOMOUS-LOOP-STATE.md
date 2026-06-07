<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 131 source-audit closure for direct backup operation
  option parser hardening.
- Updated: `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md` now record the
  direct backup operation option parser hardening and its verification target.
- Code: backup, restore, and delete operation options now trim and validate
  package names, reject negative user IDs and backup flag masks, and normalize
  backup-name, relative-directory, and exclusion-glob fields before Parcel or
  JSON restoration can reach backup execution; delete operation Parcel
  restoration now preserves a null relative-directory list as the base-backup
  selector.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.struct.BackupOperationOptionsTest --tests io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptionsTest`;
  `rtk git diff --check`; and prohibited tool/attribution diff scan.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next roadmap target: inspect the next host-verifiable source-backed batch or
  persisted-state parser edge, starting with backup/profile persisted metadata
  fields after the direct operation option structs.
- Start by checking whether backup metadata backup names, profile backup
  exclusion globs, profile backup names, or related persisted string fields can
  reach runtime or display paths without normalization, and choose a small
  source-backed risk that can be tightened without device-only claims.
- Verification target: focused JVM/static tests for any source change, Java
  compile for touched app code, docs/state update, and `rtk git diff --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  foreground tracker overlay device walkthroughs, distribution submissions, and
  Android 17 target-SDK gate.
