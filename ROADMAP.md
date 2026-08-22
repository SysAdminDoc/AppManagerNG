# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

### P2

### P3

- [ ] P3 — Per-app notes
  Why: tags shipped (NF-08) but free-text notes are the standard companion in every app organizer (Inure build107.1.0 notes editor, AppDash, Hail) and pair naturally with Finder.
  Evidence: Inure build107.1.0 (2026-07-12); AppsDb tag infrastructure already in place.
  Touches: Room schema (explicit migration, see above), App Details, optional Finder "has note / note contains" predicate.
  Acceptance: a note is editable from App Details, persists across restarts, exports/imports with snapshot bundles, and is queryable from Finder; migration test included.
  Complexity: M

- [ ] P3 — Batch component-blocking performance baseline and coalescing
  Why: bulk IFW writes are the field's known slow path (Blocker #1565: "extremely slow processing" on block-all-matched), and v0.6.10's per-package serialized rules transactions add per-package lock/commit overhead to exactly that path — measure before it becomes a complaint.
  Evidence: lihenggui/blocker#1565; v0.6.10 rules transaction serialization (CHANGELOG).
  Touches: rules commit path (ComponentsBlocker/RulesStorageManager), batchops block/unblock flow, benchmark/ module.
  Acceptance: a benchmark records blocking N components across M packages; the batch path commits once per package (not per component); benchmark result recorded in the benchmark module and no correctness test regresses.
  Complexity: M

- [ ] P3 — Surface API 36/37 diagnostics in App Details (pending-job reasons, MemoryLimiter exits)
  Why: two cheap extensions of panels that already exist: JobScheduler pending-job reasons (API 36) explain stuck background work, and Android 17's memory-limiter kills appear in ApplicationExitInfo descriptions the app already renders.
  Evidence: developer.android.com Android 16 features (getPendingJobReasons/History); behavior-changes-17 (MemoryLimiter:AnonSwap in ApplicationExitInfo.getDescription()); existing recent-exits rendering at details/info/AppInfoFragment.java:3768.
  Touches: details/info, compat wrapper for the JobScheduler API.
  Acceptance: on API 36+ pending-job reasons render per app; MemoryLimiter exit descriptions render distinctly in recent exits; both guarded on older APIs; Robolectric coverage.
  Complexity: S

- [ ] P3 — Detect Advanced Protection Mode and explain blocked installs
  Why: API 37's AdvancedProtectionManager lets the installer name why sideloading is refused instead of failing opaquely — the same explain-the-gate pattern as the shipped restricted-settings detector.
  Evidence: developer.android.com Android 17 features (AAPM); v0.6.7 restricted-settings detector precedent.
  Touches: installer preflight, details/info diagnostics, strings.
  Acceptance: with APM active (simulated), install preflight and sideload diagnostics name Advanced Protection with guidance; behavior unchanged below API 37; unit-tested via compat seam.
  Complexity: S

- [ ] P3 — Validate Swift Backup imports via the zip-comment manifest
  Why: SBConverter ignores the format's own embedded JSON manifest, so corrupt or mismatched imports fail late instead of at selection; fixing it also revives the disabled converter test.
  Evidence: backup/convert/SBConverter.java:243 (TODO: parse zip comment); app/src/test/.../SBConverterTest.java:158 (test commented out).
  Touches: SBConverter, converter tests with parser-level fixtures.
  Acceptance: malformed or package-mismatched zip-comment JSON is rejected with a clear error before restore begins; valid imports unchanged; the disabled test is re-enabled or replaced at the parser level.
  Complexity: S

- [ ] P3 — Drop Pithus from the bundled user manual
  Why: the scanner integration was removed in code, but the manual shipped as a raw resource still describes fetching Pithus reports, in English and eleven translations.
  Evidence: docs/raw/en/pages/scanner-page.tex, settings-page.tex, app-details-page.tex, docs/raw/en/strings.xml, and the generated docs/raw/<lang>/index.html for each locale.
  Touches: docs/raw/**, docs/src/main/res/raw*/index.html (generated output must be regenerated, not hand-edited).
  Acceptance: no locale of the shipped manual describes Pithus as a current feature; the historical changelog appendix entry stays as-is because it records what a past release did; the docs module builds.
  Complexity: M

- [ ] P3 — Map AID numbers to names in file properties
  Why: the file manager shows raw numeric AIDs where android_filesystem_config.h names exist — small readability/trust win for the audience this app serves.
  Evidence: fm/dialogs/FilePropertiesDialogFragment.java:584 (TODO).
  Touches: fm/dialogs, a static AID table.
  Acceptance: known AIDs render as "name (uid)", unknown values fall back to the number; unit test covers both.
  Complexity: S

- [ ] P3 — Per-folder file-manager view preferences
  Why: FmListOptions declares OPTIONS_ONLY_FOR_THIS_FOLDER but the flag does nothing, so per-folder sort/hidden-files choices are silently global.
  Evidence: fm/FmListOptions.java:35 (declared, unimplemented).
  Touches: fm/ list options, per-path preference storage.
  Acceptance: enabling "only for this folder" persists sort/filter for that path and restores it on return; global default unaffected; unit test.
  Complexity: S

- [ ] P3 — Ship code-editor language definitions as assets
  Why: language definitions are built at runtime with a serializer the code itself calls stale — startup cost and drift risk flagged in-code.
  Evidence: editor/CodeEditorViewModel.java:68 and :219 (TODOs).
  Touches: editor/, packaged assets, build script step.
  Acceptance: language defs load from packaged assets; the stale serializer path is gone; highlighting output unchanged against a fixture file.
  Complexity: M

## Research-Driven Additions (2026-08-11)

### P0

- [ ] P0 — Break the release gate's circular dependency on the published-release receipt
  Why: the consistency stage asserts that `docs/distribution/release-receipt.json` — a record of the *last published* release, carrying the published artifact's size, sha256 and download URL — equals the working tree's versionName/versionCode, which cannot be true until after that artifact exists. So the gate fails closed for every new version and blocks its own release. `consistency` is stage 2 of 8, so nothing downstream runs either: tests, lint, the two-build reproducibility check, and the dependency CVE scan (which lives inside `verify_reproducible_release`, stage 7) are all unreachable on the gate path while it fails. v0.6.8, v0.6.9, v0.6.10, v0.6.11 and v0.6.13 have CHANGELOG entries with dates but no git tag and no GitHub release; only v0.6.12 was ever published. If the intended workflow really is publish-then-sync-the-receipt, the gate must say so rather than failing on the ordinary path.
  Evidence: live run of `scripts/verify-release-consistency.sh` at HEAD emits `versionName 0.6.13 != receipt 0.6.12`, `versionCode 21 != receipt 20`, `FAILED`; scripts/verify-release-consistency.sh:146-165; scripts/verify_release_metadata.py; scripts/release_gate.py:685 (`ALL_STAGES`); scripts/verify_reproducible_release.sh:117 (CVE gate invocation); `git tag` (v0.6.12, v0.6.7, v0.6.5, v0.6.1, v0.5.0, v0.4.2); `gh release list` (latest = v0.6.12); receipt git history (synced only on publish days 2026-07-29 and 2026-08-08).
  Touches: scripts/verify_release_metadata.py (separate "the receipt describes a published release" from "the tree is ready to release" — assert the receipt is internally consistent and that the tree version is >= the receipt version, and require equality only when HEAD carries a matching tag), scripts/verify-release-consistency.sh, scripts/release_gate.py (consistency stage), docs/distribution/reproducible-builds.md and the three listing packets that describe the receipt contract.
  Acceptance: the consistency script passes on an untagged working tree whose version is ahead of the receipt, and still fails when the tree version is behind the receipt, when a tag disagrees with versionName, or when a listing packet disagrees with the receipt; the release gate runs end to end on a version that has not yet been published; a regression test covers the ahead / equal / behind / tagged cases. Publishing the outstanding versions is a separate maintainer action, not part of this row.
  Complexity: M

### P1

### P2

### P3





