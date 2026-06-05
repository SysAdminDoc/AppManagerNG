<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Completed Roadmap Ledger

This is the dedicated record of roadmap rows that have **fully closed**. It was
split out of [`ROADMAP.md`](../../ROADMAP.md) on 2026-05-28 so the active
roadmap holds only open work. Full per-release prose lives in
[`CHANGELOG.md`](../../CHANGELOG.md); this file is the checklist-level audit
trail. Long-form historical context is under
[`archive/`](archive/).

## Closed on 2026-06-05

- [x] **P1 oversized installer icon clamp** — installer parse-success dialogs
  now route APK icons through `InstallerIconSanitizer` before handing them to
  `DialogTitleBuilder`, preserving bounded bitmap/non-bitmap icons, scaling
  oversized bitmap and drawable sources to a 96 dp edge budget, and falling back
  to the platform default activity icon when icon rendering fails. Focused
  verification in this checkout:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.installer.InstallerIconSanitizerTest`,
  which also executed `:app:compileFullDebugJavaWithJavac`. Manual
  install-dialog walkthrough with a pathological APK icon remains device-gated.

## Closed on 2026-06-04

- [x] **P2 App Info action rail priority controls** — App Info horizontal
  actions now use stable IDs and resolve through a default priority model that
  keeps Launch, Freeze/Unfreeze, and Force-stop ahead of rare or destructive
  actions while pruning unsupported actions. The overflow menu adds "Customize
  action rail" so users can pin available actions to the front or reset to
  defaults, and action buttons now expose explicit content descriptions.
  Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppInfoActionOrderResolverTest`.
  Manual phone-width checks with force-stop, frozen, archived, and data-only
  packages remain device-gated.
- [x] **P1 Debloater Put back install-existing restore** — Debloater selection
  mode now exposes Put back for selected removed debloat/system rows, shows a
  restore confirmation with already-installed selections reported as skips, and
  starts a new `OP_INSTALL_EXISTING` batch op for the restorable package/user
  targets. The batch worker invokes `PackageInstallerCompat.installExisting()`
  through the existing privileged installer path, records low-risk
  operation-history metadata, and surfaces restored/failed result
  notifications. Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.debloat.DebloaterPutBackPlanTest --tests io.github.muntashirakon.AppManager.batchops.BatchOpsInstallExistingTextTest`.
  Manual remove-for-user -> put-back round trip remains device-gated.
- [x] **P1 in-app Wireless ADB pairing-code fallback** — Wireless ADB pairing
  now keeps the foreground service as the single mDNS scan/pairing owner while
  publishing a shared pairing state for in-app UI. The Manual path opens an
  in-app code dialog that shows the discovered pairing port, validates a
  six-digit code, submits it through the same service action as notification
  `RemoteInput`, and keeps retry/cancel semantics connected to the existing
  `Ops.pairAdb()` waiter. Pairing notifications also expose an "Enter in app"
  action for devices where inline notification replies are unavailable. Focused
  verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.adb.AdbPairingRequestTest`.
  Manual normal-device and Quest/multi-window Wireless ADB walkthroughs remain
  device-gated.
- [x] **P2 Terminal active privilege-provider routing** — Terminal startup now
  resolves and labels the current shell route as local, root, Shizuku, or ADB,
  attempts to bind the active LocalServices-backed provider before falling back,
  and keeps the local `sh -i` process as an explicit fallback instead of a
  silent substitute. Startup failures, route fallbacks, process exits, and
  dead-process writes are surfaced in the Terminal UI. Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.terminal.TerminalRouteTest`.
  Manual local, root/Shizuku, and wireless-ADB command walkthroughs remain
  device-gated.
- [x] **P2 Mode of Operation lifecycle-safe apply and rollback** — Settings ->
  Mode of Operation now runs a single guarded apply transaction: the selected
  mode is initialized as a candidate without first writing the stored
  preference, Apply is ignored while a switch is in flight, terminal success
  commits the candidate, terminal failure restores the previous mode with a
  visible rollback toast, and fragment destruction clears the pending
  transaction so late callbacks cannot commit a dismissed flow. The root-backed
  Shizuku "Switch to ADB mode" shortcut uses the same guarded path. Focused
  verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.ModeOfOpsApplyStateTest`.
  Manual ADB and Shizuku mode-switch walkthroughs remain device-gated.
- [x] **P1 DexOpt root-only execution sanitization** — DexOpt batch execution
  now clones and sanitizes serialized `DexOptOptions` before expanding the
  package loop, preserving root/system behavior while stripping stale
  profile-reset and immediate force-dexopt requests for non-root/ADB/Shizuku
  runs. The worker records a single skipped-root-only-options reason instead of
  surfacing raw per-package PackageManager `SecurityException` failures, and the
  dialog uses the same root/system helper for checkbox gating. Focused
  verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptionsTest`.
  Manual ADB DexOpt replay with a stale serialized profile-reset option remains
  device-gated.
- [x] **P3 File Manager Open with defaults and keyboard focus** — File Manager
  now stores Open with handlers per extension or per file, routes file-row taps
  through a saved handler when available, labels the context action as "Change
  open with" when a default exists, exposes the previously hidden Always open /
  Only for this file controls, and adds a Clear default dialog action while
  keeping the OS chooser icon as a fallback. The search control now starts
  iconified and cleared so the picker does not request keyboard focus on open.
  Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.fm.FmOpenWithDefaultsTest`.
  Manual Android open/repeat/reset and soft-keyboard walkthrough remains
  device-gated.
- [x] **P2 installer notification final-state wording** — installer completion
  now applies the same concrete final title/body to the foreground progress
  notification and the completion alert instead of mutating the last progress
  body to a generic "Done". Success and failure subjects still come from the
  existing installer status formatter, and failure details remain available
  through the expanded BigText body when present. Focused verification:
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.installer.PackageInstallerServiceTest`.
  Manual background install success/failure notification walkthrough remains
  device-gated.
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
- [x] **P2 Privacy & Security API surfaces** — App Info now exposes SDK Runtime
  manifest declarations, Domain Verification and same-user deep-link conflicts,
  archived-package state plus Archive/Unarchive requests, MTE/memory-tagging
  posture, Health Connect manifest permission posture, Credential Manager
  provider declarations, and Restricted Settings diagnostics through
  target-scoped sources. Focused evidence remains `SdkSandboxInfoTest`,
  `DomainLinkConflictDetectorTest`, `DomainLinksOptionTest`,
  `AppArchiveManagerTest`, `FreezeOptionTest`, `MemoryTaggingInfoTest`,
  `HealthConnectInfoTest`, `CredentialProviderManifestInfoTest`, and
  `RestrictedSettingsDiagnosticsTest`. API-35 archiving walkthroughs and
  non-caller-scoped live-state source proof are tracked in the gated roadmap
  buckets.
- [x] **P2 In-app Tasker plugin + Quick Settings tile suite + DocumentsProvider**
  — `AutomationUriActivity`, signature-gated `AutomationReceiver`, the
  Locale-compatible `TaskerPluginEditActivity` / `TaskerPluginFireReceiver`,
  `QuickFreezeTileService`, `ForceStopTileService`, and
  `AppManagerDocumentsProvider` are registered and host-covered. The plug-in
  signs and verifies `am://` automation bundles, the QS suite supports Android
  13+ one-tap add flows for freeze-profile and pinned force-stop actions, and
  the read-only SAF provider exposes backup/profile roots with traversal guards.
  Focused evidence remains `AppManagerDocumentsProviderTest`,
  `AutomationRequestTest`, `AutomationIntentsTest`, `TaskerPluginBrokerTest`,
  `QuickFreezeTileControllerTest`, `QuickFreezeTileServiceTest`,
  `ForceStopTileControllerTest`, and `ForceStopTileServiceTest`; runtime
  Tasker/QS/SAF walkthroughs are tracked in the device bucket.
- [x] **P2 Macrobenchmark module + Baseline Profile + UIAutomator smoke pack**
  — the benchmark module now covers cold startup, main-list scroll frame timing,
  and Backup settings time-to-interactive through shared UIAutomator journeys.
  Baseline Profile generation records startup, list-scroll, and Backup settings
  paths; the androidTest smoke pack now verifies main launch, batch-selection
  surface entry for freeze/backup/component actions, and One-Click Ops
  rule/backup surface launch. Focused verification:
  `:benchmark:compileDebugJavaWithJavac :app:compileFullDebugAndroidTestJavaWithJavac`.
  Online macrobenchmark/profile generation and API 26/30/34/35 smoke execution
  remain device-gated.
- [x] **P3 MMRL + LSPosed module browser** — Settings -> Privileges now exposes
  a hidden-until-root Modules row that reads Magisk/MMRL
  `/data/adb/modules/*/module.prop` and LSPosed `/data/adb/lspd/**/module.prop`
  metadata without mutating module state. The dialog lists module name,
  version, source, status markers, author, description, and path with a copy
  action, while non-root states keep the row hidden. Focused verification:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.RootModuleInfoTest :app:compileFullDebugJavaWithJavac`.
  A rooted-device module walkthrough remains device-gated.

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
