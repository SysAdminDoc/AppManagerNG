<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P1

### P2

### P3

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P2

- [ ] P2 — Master-key checksum is computed over a lossy String round-trip
  Why: backup and restore both digest `masterKey.getContentAsString().getBytes()`, which mangles binary key bytes; it only matches because both sides are equally lossy, so it silently breaks if the platform default charset ever changes. Fixing requires a versioned scheme (binary digest for new backups, still accept the legacy digest for old ones) to avoid breaking existing backups.
  Where: `backup/RestoreOp.java` (~323), `backup/BackupOp.java` (~527)

- [ ] P2 — Snapshot import can create arbitrary preference files and unknown keys
  Why: import applies any `prefs/<leaf>.xml` whose name isn't in the small exclusion list and any key inside `preferences`, with no allowlist of known pref files/keys, so a crafted bundle can drop junk pref files or set unintended flags. Needs an allowlist design.
  Where: `snapshot/SnapshotBundle.java` (~474, ~991)

- [ ] P2 — Rule value parsing collapses empty fields via StringTokenizer
  Why: `RuleEntry.valueTokenizer` re-tokenizes the value with `StringTokenizer`, which drops empty tokens and cannot represent an empty intermediate field, misaligning positional value parsing. Latent today (no rule type serializes an empty field) but fragile; fixing requires reworking every `RuleEntry` subclass to index the split array.
  Where: `rules/struct/RuleEntry.java` (~127-135)

- [ ] P2 — Rule/config I/O uses the platform default charset
  Why: `PathReader`, `RulesImporter`, and the IFW/TSV writers use the default charset on both read and write, so non-ASCII component/package names round-trip only by luck and can corrupt across a JVM host or a different locale. Pin `StandardCharsets.UTF_8` at every site.
  Where: `rules/PathReader.java` (~23), `rules/RulesImporter.java` (~65,76), `rules/compontents/ComponentUtils.java` (~213), `rules/compontents/ComponentsBlocker.java` (~356)

- [ ] P2 — UriGrant flatten/unflatten doesn't validate package fields
  Why: `UriGrant` joins 9 comma-separated fields with only the trailing URI protected by the split limit; a corrupt/hand-edited grant with a comma in a package field shifts the remaining fields and can parse into a different valid-looking grant (and UriGrantRule is the one non-unique rule type). Validate `sourcePkg`/`targetPkg` against a package-name pattern before constructing.
  Where: `uri/UriManager.java` (~205-227)

- [ ] P2 — RulesImporter registers a ComponentsBlocker before the type filter
  Why: `importRow` creates/records a `ComponentsBlocker` for every target user before checking `mTypesToImport`, so a deselected type still re-commits/normalizes that package's existing on-disk rules. Register the blocker only when the type is selected.
  Where: `rules/RulesImporter.java` (~104-113)

- [ ] P2 — AutomationReceiver accepts negative user IDs (unlike the URI path)
  Why: `AutomationReceiver.getUsers` accepts any int; a negative user is then silently dropped downstream (`isValidTarget` requires `userId >= 0`), causing silent partial execution. Mirror `AutomationRequest.validatePackages` and reject negatives.
  Where: `automation/AutomationReceiver.java` (~248-283)

- [ ] P2 — SplitApkExporter bundles unrelated sibling .apk files
  Why: `getAllApkFiles` adds every `*.apk` in the source directory to the exported bundle without verifying it belongs to the package, so on some OEM layouts a foreign or stray APK is folded into the `.apks`. Filter siblings by parsed manifest package. Needs device layouts to verify.
  Where: `apk/splitapk/SplitApkExporter.java` (~131-140)

- [ ] P2 — LogLine.LOG_PATTERN risks catastrophic backtracking
  Why: the `(.+\d+)\s+(\d+)` UID/PID group forces heavy backtracking on long non-conforming log lines (native crash dumps), spiking the reader thread. Anchor/possessive the UID-PID group. Needs sample device logs to validate the pattern change.
  Where: `logcat/struct/LogLine.java` (~36-48)

### P3

- [ ] P3 — Restore accepts a CRC32 "checksum" as integrity verification
  Why: a backup whose `checksum_algo` is `crc32` passes verification and restores as if integrity were guaranteed, though CRC32 is trivially forgeable; reject or loudly warn on non-cryptographic checksum algorithms on the restore/verify path.
  Where: `backup/struct/BackupMetadataV5.java` (~219-222), `backup/VerifyOp.java`, `backup/RestoreOp.java`

- [ ] P3 — Legacy 32-bit GCM tag restore has no user-visible integrity-downgrade signal
  Why: restoring a pre-v4 encrypted backup silently drops to a 32-bit auth tag (forgeable ~2^-32) with only a logcat warning; surface it as a restore-time warning like the existing API-level warnings.
  Where: `crypto/AESCrypto.java` (~161-171), `backup/struct/BackupMetadataV5.java` (~263-268)

- [ ] P3 — restoreExtras reports success even when every rule fails
  Why: each per-rule failure is warned-and-continued with no threshold, so a restore where all permission/appop/URI grants fail still completes as success; track applied-vs-failed and escalate on a high failure ratio.
  Where: `backup/RestoreOp.java` (~957-982)

- [ ] P3 — restoreApkFiles passes split names to TarUtils as uncompiled regex filters
  Why: APK/split names from metadata are used as regex filters (`Pattern.compile`), so `.` and other metacharacters make the filter over-permissive; `Pattern.quote` them for literal matching.
  Where: `backup/RestoreOp.java` (~418)

- [ ] P3 — Snapshot has two divergent pref-merge code paths
  Why: `mergeSharedPreferencesXml` is exercised only by tests while the shipping import uses the editor path, giving false test confidence; delete the unused method (and its tests) or route the real import through it.
  Where: `snapshot/SnapshotBundle.java` (~1266-1297)

- [ ] P3 — BatchQueueItem.getUsers() mutates fields as a side effect
  Why: the lazy getter rewrites `mPackages`/`mUsers` via `sanitizeTargets`, but `serializeToJson` reads the fields directly, so the journal snapshot can diverge from the executed target set depending on call order; sanitize once at construction/deserialization.
  Where: `batchops/BatchQueueItem.java` (~102-115, 167-175)

- [ ] P3 — LogViewerViewModel uses notify() where notifyAll() is expected elsewhere
  Why: resume/restart use `mLock.notify()` while teardown uses `notifyAll()`; safe today with a single waiter but fragile if a second waiter is ever added. Use `notifyAll()` consistently.
  Where: `logcat/LogViewerViewModel.java` (~213, 243)

- [ ] P3 — ApkWhatsNewFinder lazy singleton is unsynchronized
  Why: `getInstance()` has a check-then-set data race; benign while stateless but a landmine if state is added. Make the field `static final` or the method `synchronized`.
  Where: `apk/whatsnew/ApkWhatsNewFinder.java` (~65-68)

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3
