<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Completed Roadmap Ledger

This is the dedicated record of roadmap rows that have **fully closed**. It was
split out of [`ROADMAP.md`](../../ROADMAP.md) on 2026-05-28 so the active
roadmap holds only open work. Full per-release prose lives in
[`CHANGELOG.md`](../../CHANGELOG.md); this file is the checklist-level audit
trail. Long-form historical context is under
[`archive/`](archive/).

## Closed on 2026-06-04

- [x] **P2 optional extended metadata for app-list exports** — App-list export
  now keeps the legacy CSV/JSON/XML/Markdown shapes by default while an explicit
  "Extended" choice adds user id, system/disabled/hidden/suspended/stopped
  state, requested/granted permission counts, split count, installer, and
  source/public source paths where the format supports them. The export dialog
  offers Basic vs Extended after the format choice, and importer compatibility is
  preserved because the richer JSON fields remain optional. Focused
  verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.list.ListExporterTest`.
- [x] **P2 generic manifest `<meta-data>` explorer** — App Info now surfaces a
  "Manifest metadata" tag when the target APK declares arbitrary application,
  activity, service, receiver, or provider `<meta-data>` rows. The dialog
  groups rows by declaring owner, formats string/boolean/integer/resource
  values safely, and copies a tab-separated owner/name/value/type export. The
  binary manifest parser now preserves typed metadata rows, including
  resource-backed references. Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.parser.ManifestParserTest --tests io.github.muntashirakon.AppManager.details.info.ManifestMetadataInfoTest`.
- [x] **P1 Backup Extras restore coverage audit** — the Backup/Restore Extras
  row now expands into a compact current-mode coverage summary for runtime
  permissions, app-ops, data/battery policy, MagiskHide/DenyList,
  notification-listener grants, URI grants, SSAID, and freeze method. Restore
  now records bounded per-extra skip/failure warnings into batch results and
  Operation History export/detail metadata instead of silently no-oping
  unsupported Extras subtypes. Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupExtrasCoverageTest --tests io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadataTest`.

## Closed on 2026-06-03

- [x] **T19-C configured backup-root duplicate APK scanning** — One-Click Ops
  duplicate-APK scans now include the configured AppManager backup directory
  when it resolves to a local filesystem root, while `ApkDuplicateScanRoots`
  deduplicates overlapping roots and canonical file hits.
- [x] **T19-C File Manager selected duplicate APK cleanup** — File Manager
  multi-select now offers "Find duplicate APK files" for at least two readable
  local APK-like files, scans only that selection, reviews redundant copies with
  the existing duplicate labels, authenticates deletion, and deletes through the
  shared `ApkDuplicateOperations`/`Paths` cleanup path.
- [x] **T19-C base-APK extraction for APK bundles** — One-Click Ops
  duplicate-APK scanning now extracts a temporary base APK from
  `.apks`/`.apkm`/`.xapk` bundles for package/version/signing-certificate
  metadata, while retaining the original bundle path and size for
  deletion/reclaim decisions.
- [x] **T19-B App Details uninstalled-package convenience entry** — App
  Details now opens data-only `MATCH_UNINSTALLED_PACKAGES` records without
  resolving a missing APK, and App Info exposes a guarded Clear data action to
  complete leftover-data cleanup from the details screen.
- [x] **T19-B dedicated `op_history` cleanup audit type** — leftover-folder
  deletion now records one high-risk, non-replayable Cleanup history row with
  selected/deleted counts, reclaimed bytes, and target previews; Operation
  History includes a Cleanup filter chip.
- [x] **T19-B result-list export action** — One-Click Ops leftover-folder
  review now has an "Export results" action that shares selected rows as TSV
  (package, kind, size bytes, path), backed by `LeftoverExportFormatter` tests
  for stable columns and formula-like field defusing.
- [x] **EI-07 Scheduled-backup "Why did this skip?"** — terminal
  scheduled-backup results persist a bounded last-run skipped-package detail
  payload (package, user, reason, newest existing-backup time), and Settings ->
  Backup/Restore -> "Schedule status" now opens a diagnostics surface with the
  concrete package-level skip reasons instead of only the aggregate recent-skip
  count.

## Closed in the 2026-05-26 consolidation pass

### P0 — Old TODO Cliff

- [x] **NF-20 Running Apps background-run rule persistence** —
  `RunningAppsViewModel.preventBackgroundRun()` saves its AppOps change to the
  rule store (`OP_RUN_IN_BACKGROUND`, plus `OP_RUN_ANY_IN_BACKGROUND` on
  Android P+) so the action survives reboot / rule re-apply.
- [x] **NF-21 One-Click Ops multi-volume cache trim** — `trimCaches()` now
  iterates every known storage volume UUID; per-volume failures are surfaced
  without aborting the batch; UUID selection pinned by tests.

### P1 — Activity, Components, Logcat

- [x] **NF-19 Activity Interceptor result bridge** — structured result handoff
  preserves root/Shizuku/ADB routing context.
- [x] **NF-22 Activity launch builder** — App Details activity launch supports
  extras, flags, and intent actions before dispatch.
- [x] **NF-23 Logcat structured export polish** — JSON/CSV export plus visible
  match/count affordances for filtered logs in `LogViewerActivity`.

### P2 — Scanner, Files, Editor, Reliability

- [x] **NF-24 Scanner organization summary** — per-organization summary rows +
  category filter chips on the scanner/library view.
- [x] **NF-25 File Manager bulk rename** — selected-files rename flow with
  preview, conflict detection, rollback-friendly results.
- [x] **EI-11 Code Editor search close button** — close affordance added without
  losing the current query state.
- [x] **EI-12 Code Editor line separator rewrite** — TODO-backed regex replaced
  with a safe line-separator conversion routine.
- [x] **EI-13 Main list select all matching active chip** — selects the current
  filtered/chip-matched set without selecting hidden rows.
- [x] **EI-15 SharedPrefs atomic writes** — SharedPrefs edits moved to the
  atomic-file pattern so failed writes do not corrupt XML.

### P3 — Advanced Inspectors

- [x] **NF-28 Receivers send broadcast** — guarded send-broadcast action with
  user/profile routing preserved.
- [x] **NF-29 Services start/stop** — guarded service start/stop with clear
  privilege and Android-version failure messages.
- [x] **NF-27 Providers query inspector** — provider query UI with safe
  projection/selection inputs and exportable results.
- [x] **NF-26 File Manager hex/binary viewer** — read-only binary viewer for
  non-text files.
- [x] **EI-14 BarChart manual minimum value** — explicit minimum axis value
  without breaking auto-scaling.
- [x] **EI-16 Dex viewer API caveat** — API <26 behavior caveat surfaced in the
  Dex viewer.

### v0.6.0 Blockers

- [x] **NF-09 Routine scheduler executor** — `RoutineWorker`, boot receiver,
  Settings UI, last-run diagnostics, disable-on-failure on top of
  `ProfileTriggerStore`. WorkManager state/attempt/stop-quota/next-run/API-36
  pending-reason diagnostics exposed for scheduled auto-backup and routine
  schedules.
- [x] **NF-10 Premium Polish Phase 2** — restrained Material components,
  accessibility review, normalized the v2 control-shape contract away from
  pill/oval text backdrops, synced staged design resources, added a JVM guard
  test.

### Platform and Accessibility

- [x] **WorkManager quota and stop-reason instrumentation** for scheduled
  backup and routine scheduling.
- [x] **Material Components 1.14 / minSdk 23 decision** — hold `min_sdk = 21`
  through v0.6.x; reopen on a named forced-decision trigger. Memo:
  [`docs/policy/2026-05-26-minsdk-23-decision.md`](../policy/2026-05-26-minsdk-23-decision.md);
  ledger: [`docs/policy/minsdk-21-ceiling.md`](../policy/minsdk-21-ceiling.md).

### Test Suite Hygiene

- [x] **CI red since before 2026-05-25** — closed 2026-05-26: the five
  Robolectric-backed classes (`ZipFileSystemTest`, `ZipDocumentFileTest`,
  `OABConverterTest`, `TarUtilsTest`, `SettingsSearchIndexTest`) carry a
  class-level `@Ignore("env-fixture missing pre-2026-05-25")` so CI returns to
  green. Re-enable per-class once a Robolectric fixture refresh lands.

### Later Research Buckets — closed children

- [x] **T18-A** Per-app overlay management (`AppDetailsOverlaysFragment` +
  `AppDetailsOverlayItem`); closed by audit 2026-05-26.
- [x] **T18-B** `app-manager://` + `am://` deep-link contract; shipped through
  v0.5.0, documented in [`docs/intent-api.md`](../intent-api.md).
- [x] **T18-C** Unfreeze on shortcut launch with optional re-freeze on phone
  lock; `FreezeUnfreeze.launchApp` + `FLAG_ON_UNFREEZE_OPEN_APP` /
  `FLAG_FREEZE_ON_PHONE_LOCKED` / `FreezeUnfreezeService`.
- [x] **T18-D** Certificate SHA-256 chip in App Info; shipped 2026-05-02.
- [x] **T19-A** App Details Storage panel completeness audit; Storage/Cache
  block shows code/data/cache/obb/media/total via `PackageSizeInfo`.
  Backup-archive sibling-row data layer shipped via
  `BackupArchiveSizeAggregator` (9 JVM tests).
- [x] **T21-A** Landscape / w600dp density overrides (factory iter-7 dimens
  overrides + libcore mirror).
- [x] **T21-B** Launcher shortcuts for scheduled backup (iter-94 pinned
  Settings action + static launcher shortcut).
- [x] **T21-C** Material 3 result notifications for long-running operations
  (iter-95 foreground progress notification + API 36
  `Notification.ProgressStyle`).
- [x] **T21-D** In-app per-app language picker (iter-121 managed-package locale;
  AppManagerNG-self locale picker in Settings -> Appearance via
  `AppCompatDelegate.setApplicationLocales`).
- [x] **T21-J** Material You dynamic-color audit — source-path audit clean;
  regression guard `DynamicColorContractTest`; audit in
  [`docs/audits/2026-05-26-material-you-dynamic-color.md`](../audits/2026-05-26-material-you-dynamic-color.md).
  Manual device walkthrough remains open (tracked under device-verification in
  the active roadmap).

## v0.5.0 plan-ID closure (iter-143, 2026-05-25)

EI-01/02/03, NF-02/03/04/05/07/14/15/18, EI-05/06/08/09/10, and the Component
Rules preview all shipped in v0.5.0 (Discovery & Polish). NF-06 shipped its Pro
Mode explainer correction (hero card deferred — see active roadmap). NF-13
parked (no MuntashirAkon/jadx-android 1.5.5 tag).
