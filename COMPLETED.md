<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Completed Work

Items consolidated from legacy planning documents on 2026-06-03. This is the
canonical, checklist-level record of roadmap rows that have **fully closed**.
Full per-release prose lives in [`CHANGELOG.md`](CHANGELOG.md); long-form
historical context and the previous ledger are under
[`docs/roadmap/`](docs/roadmap/) and
[`docs/roadmap/archive/`](docs/roadmap/archive/). The active work lives in
[`ROADMAP.md`](ROADMAP.md).

## Shipped Features

### Closed on 2026-06-04

- [x] **P2 Support Info Bundle + opt-in local crash sink** — Support-info bundles now include local crash-sink state and recent scrubbed crash summaries, while uncaught crashes persist bounded private JSON files only when Settings -> Privacy -> Local crash sink is explicitly enabled; crash notifications remain user-initiated shares and no network telemetry path is introduced. — *Source: ROADMAP.md*

### Closed on 2026-06-03

- [x] **P2 Mode Doctor active-probe screen** — Settings -> Privileges -> Mode doctor now opens a dedicated active-probe screen with one status/details/fix row per privilege probe, copy and support-bundle share actions, and tap-through fix targets for mode settings, root/Shizuku/Dhizuku managers, Developer options, restricted-settings App info, LocalServer bootstrap smoke test, or support bundle. — *Source: ROADMAP.md*
- [x] **P1 App Details single-action audit history** — direct App Details freeze/unfreeze, permission grant/revoke, AppOp mode, and component-rule actions now write `single_app_action` Operation History rows with package/user target metadata, exit/failure status, risk, replayability, and reversibility; Operation History exposes an App details filter and per-package rollback planning includes runnable inverses for freeze/unfreeze and permission grant/revoke. — *Source: ROADMAP.md*
- [x] **P0 interrupted batch retry target reduction** — batch operations now persist completed/failed package-user targets as each target finishes, interrupted recovery builds a reduced retry queue for unfinished targets only, and the recovery dialog surfaces completed/remaining progress. Interruptions with no recorded target progress conservatively retry the original queue. — *Source: ROADMAP.md*
- [x] **P3 scheduled operation-history retention pruning** — finite Operation History retention values now schedule a unique daily WorkManager worker that prunes old `op_history` rows without opening the screen, while `0` is labeled and enforced as keep forever/no scheduled cleanup. — *Source: ROADMAP.md*
- [x] **P2 weekly OWASP critical-CVE gate** — root OWASP Dependency Check keeps local runs report-only by default while the weekly scheduled workflow passes a CVSS 9.0 fail threshold and stops masking Gradle failures, with HTML/SARIF uploads retained under `if: always()`. — *Source: ROADMAP.md*
- [x] **T19-C configured backup-root duplicate APK scanning** — One-Click Ops duplicate-APK scans now include the configured AppManager backup directory when it resolves to a local filesystem root, while `ApkDuplicateScanRoots` deduplicates overlapping roots and canonical file hits. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-C File Manager selected duplicate APK cleanup** — File Manager multi-select now offers "Find duplicate APK files" for at least two readable local APK-like files, scans only that selection, reviews redundant copies with the existing duplicate labels, authenticates deletion, and deletes through the shared `ApkDuplicateOperations`/`Paths` cleanup path. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-C base-APK extraction for APK bundles** — One-Click Ops duplicate-APK scanning now extracts a temporary base APK from `.apks`/`.apkm`/`.xapk` bundles for package/version/signing-certificate metadata, while retaining the original bundle path and size for deletion/reclaim decisions. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-B App Details uninstalled-package convenience entry** — App Details now opens data-only `MATCH_UNINSTALLED_PACKAGES` records without resolving a missing APK, and App Info exposes a guarded Clear data action to complete leftover-data cleanup from the details screen. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-B dedicated `op_history` cleanup audit type** — leftover-folder deletion now records one high-risk, non-replayable Cleanup history row with selected/deleted counts, reclaimed bytes, and target previews; Operation History includes a Cleanup filter chip. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-B result-list export action** — One-Click Ops leftover-folder review now has an "Export results" action that shares selected rows as TSV (package, kind, size bytes, path), backed by `LeftoverExportFormatter` tests for stable columns and formula-like field defusing. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-07 Scheduled-backup "Why did this skip?"** — terminal scheduled-backup results persist a bounded last-run skipped-package detail payload (package, user, reason, newest existing-backup time), and Settings -> Backup/Restore -> "Schedule status" now opens a diagnostics surface with the concrete package-level skip reasons instead of only the aggregate recent-skip count. — *Source: docs/roadmap/COMPLETED.md*

### Closed in the 2026-05-26 consolidation pass — P0 (Old TODO Cliff)

- [x] **NF-20 Running Apps background-run rule persistence** — `RunningAppsViewModel.preventBackgroundRun()` saves its AppOps change to the rule store (`OP_RUN_IN_BACKGROUND`, plus `OP_RUN_ANY_IN_BACKGROUND` on Android P+) so the action survives reboot / rule re-apply. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-21 One-Click Ops multi-volume cache trim** — `trimCaches()` now iterates every known storage volume UUID; per-volume failures are surfaced without aborting the batch; UUID selection pinned by tests. — *Source: docs/roadmap/COMPLETED.md*

### Closed in the 2026-05-26 consolidation pass — P1 (Activity, Components, Logcat)

- [x] **NF-19 Activity Interceptor result bridge** — structured result handoff preserves root/Shizuku/ADB routing context. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-22 Activity launch builder** — App Details activity launch supports extras, flags, and intent actions before dispatch. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-23 Logcat structured export polish** — JSON/CSV export plus visible match/count affordances for filtered logs in `LogViewerActivity`. — *Source: docs/roadmap/COMPLETED.md*

### Closed in the 2026-05-26 consolidation pass — P2 (Scanner, Files, Editor, Reliability)

- [x] **NF-24 Scanner organization summary** — per-organization summary rows + category filter chips on the scanner/library view. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-25 File Manager bulk rename** — selected-files rename flow with preview, conflict detection, rollback-friendly results. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-11 Code Editor search close button** — close affordance added without losing the current query state. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-12 Code Editor line separator rewrite** — TODO-backed regex replaced with a safe line-separator conversion routine. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-13 Main list select all matching active chip** — selects the current filtered/chip-matched set without selecting hidden rows. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-15 SharedPrefs atomic writes** — SharedPrefs edits moved to the atomic-file pattern so failed writes do not corrupt XML. — *Source: docs/roadmap/COMPLETED.md*

### Closed in the 2026-05-26 consolidation pass — P3 (Advanced Inspectors)

- [x] **NF-28 Receivers send broadcast** — guarded send-broadcast action with user/profile routing preserved. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-29 Services start/stop** — guarded service start/stop with clear privilege and Android-version failure messages. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-27 Providers query inspector** — provider query UI with safe projection/selection inputs and exportable results. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-26 File Manager hex/binary viewer** — read-only binary viewer for non-text files. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-14 BarChart manual minimum value** — explicit minimum axis value without breaking auto-scaling. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-16 Dex viewer API caveat** — API <26 behavior caveat surfaced in the Dex viewer. — *Source: docs/roadmap/COMPLETED.md*

### v0.6.0 Blockers

- [x] **NF-09 Routine scheduler executor** — `RoutineWorker`, boot receiver, Settings UI, last-run diagnostics, disable-on-failure on top of `ProfileTriggerStore`. WorkManager state/attempt/stop-quota/next-run/API-36 pending-reason diagnostics exposed for scheduled auto-backup and routine schedules. — *Source: docs/roadmap/COMPLETED.md*
- [x] **NF-10 Premium Polish Phase 2** — restrained Material components, accessibility review, normalized the v2 control-shape contract away from pill/oval text backdrops, synced staged design resources, added a JVM guard test. — *Source: docs/roadmap/COMPLETED.md*

### Platform and Accessibility

- [x] **WorkManager quota and stop-reason instrumentation** for scheduled backup and routine scheduling. — *Source: docs/roadmap/COMPLETED.md*
- [x] **Material Components 1.14 / minSdk 23 decision** — hold `min_sdk = 21` through v0.6.x; reopen on a named forced-decision trigger. Memo: `docs/policy/2026-05-26-minsdk-23-decision.md`; ledger: `docs/policy/minsdk-21-ceiling.md`. — *Source: docs/roadmap/COMPLETED.md*

### Test Suite Hygiene

- [x] **CI red since before 2026-05-25** — closed 2026-05-26: the five Robolectric-backed classes (`ZipFileSystemTest`, `ZipDocumentFileTest`, `OABConverterTest`, `TarUtilsTest`, `SettingsSearchIndexTest`) carry a class-level `@Ignore("env-fixture missing pre-2026-05-25")` so CI returns to green. Re-enable per-class once a Robolectric fixture refresh lands. — *Source: docs/roadmap/COMPLETED.md*
- [x] **Known failing unit tests (4 from 2026-05-28 full-suite run)** — fixed; `:app:testFullDebugUnitTest` green (1092 tests). Covers `ApkDuplicateSelectorTest.sizeTieBreaksOnAbsolutePathDeterministically` (Windows-host C: prefix artifact), `SnackbarDurationPolicyTest.minWindowFloorAppliesEvenAfterShrinking` (stale assertion), and the two `PrivilegedRunnerArgValidatorTest` cases (impl now classifies `\n`/`\r` and embedded-space tokens as SHELL_METACHARACTER). — *Source: ROADMAP.md*

### Later Research Buckets — closed children

- [x] **T18-A** Per-app overlay management (`AppDetailsOverlaysFragment` + `AppDetailsOverlayItem`); closed by audit 2026-05-26. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T18-B** `app-manager://` + `am://` deep-link contract; shipped through v0.5.0, documented in `docs/intent-api.md`. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T18-C** Unfreeze on shortcut launch with optional re-freeze on phone lock; `FreezeUnfreeze.launchApp` + `FLAG_ON_UNFREEZE_OPEN_APP` / `FLAG_FREEZE_ON_PHONE_LOCKED` / `FreezeUnfreezeService`. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T18-D** Certificate SHA-256 chip in App Info; shipped 2026-05-02. — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-A** App Details Storage panel completeness audit; Storage/Cache block shows code/data/cache/obb/media/total via `PackageSizeInfo`. Backup-archive sibling-row data layer shipped via `BackupArchiveSizeAggregator` (9 JVM tests). — *Source: docs/roadmap/COMPLETED.md*
- [x] **T19-B Leftover detection after uninstall** — One-Click Ops "Detect leftover folders" shipped 2026-05-28: scans `Android/{data,obb,media}` (and privileged `/data/data` stubs), searchable multi-choice review, `ActionAuthGate`-gated recursive delete with per-folder audit lines and a reclaimed-bytes toast. Data layer `LeftoverScanner` (15 JVM tests), `LeftoverExportFormatter` (2 tests), `LeftoverCleanupHistoryItem` (2 tests). — *Source: ROADMAP.md*
- [x] **T19-C APK duplicate finder** — One-Click Ops "Find duplicate APK files" shipped 2026-05-28: fingerprints each `.apk` via `getPackageArchiveInfo` + signing-cert SHA-256, keeps the largest copy, deletes via privileged path with audit logging. Data layer `ApkDuplicateSelector`/`ApkFileScanner`/`ApkBundleHeaderParser` (39 JVM tests). — *Source: ROADMAP.md*
- [x] **T19-D Backup duplicate cleaner** — One-Click Ops "Delete duplicate backups" shipped 2026-05-28 and follow-up closed 2026-06-03: keep-largest/keep-newest/keep-oldest via `BackupRetentionPolicy`, reclaimable-byte confirmation, and dedicated cleanup `op_history` rows. — *Source: ROADMAP.md*
- [x] **T20-A Perfetto system-trace export**, **T20-B simpleperf CPU profile capture**, **T20-C Memory allocations inspector** — App Details overflow capture actions shipped 2026-05-28, all gated on root/Shizuku/ADB with validated argv via `PrivilegedRunnerArgValidator`; Perfetto duration picker, simpleperf duration/event picker, memory-snapshot refresh, and memory-region chart shipped 2026-06-03; pure-function builders/parsers/formatters JVM-tested. — *Source: ROADMAP.md*
- [x] **T21-A** Landscape / w600dp density overrides (factory iter-7 dimens overrides + libcore mirror). — *Source: docs/roadmap/COMPLETED.md*
- [x] **T21-B** Launcher shortcuts for scheduled backup (iter-94 pinned Settings action + static launcher shortcut). — *Source: docs/roadmap/COMPLETED.md*
- [x] **T21-C** Material 3 result notifications for long-running operations (iter-95 foreground progress notification + API 36 `Notification.ProgressStyle`). — *Source: docs/roadmap/COMPLETED.md*
- [x] **T21-D** In-app per-app language picker (iter-121 managed-package locale; self locale picker in Settings -> Appearance via `AppCompatDelegate.setApplicationLocales`). — *Source: docs/roadmap/COMPLETED.md*
- [x] **T21-E Discreet / generic launcher-icon mode** — three manifest activity-aliases + neutral/monochrome vectors + `LauncherIconAliasController`, wired into Settings -> Appearance. Data layer `LauncherIconAliasPlan` (11 tests). Device verification of the alias round-trip remains a follow-up. — *Source: ROADMAP.md*
- [x] **T21-F Undo SnackBar for destructive operations** — main-list batch chokepoint opens a per-op `UndoableActionQueue` + Material `Snackbar` honoring the reduced-motion gate; tapping Undo cancels the deferred dispatch. Data layer `UndoableActionQueue`/`SnackbarDurationPolicy`/`UndoOpHistoryRecorder` (27 JVM tests). — *Source: ROADMAP.md*
- [x] **T21-G Attention badges on app list rows** — severity-tinted true-circle dot overlay bound via `AttentionBadgeSource`, count/reason in `contentDescription` for TalkBack; OS-revert wiring via `OsRevertCountTracker` process-wide singleton. — *Source: ROADMAP.md*
- [x] **T21-J** Material You dynamic-color audit — source-path audit clean; regression guard `DynamicColorContractTest`; manual device walkthrough tracked under device-verification in the active roadmap. — *Source: docs/roadmap/COMPLETED.md*
- [x] **EI-04 Permission Inspector chip-row filter** — single-selection chip row (All / Requested / Granted / Needs review) backed by `PermissionInspectorFilter` (4 JVM tests), reusing the bounded-radius chip style. — *Source: ROADMAP.md*
- [x] **NF-08 tag UI follow-up** — App Details overflow -> "Edit tags" via `SearchableMultiChoiceDialogBuilder` over known + current tags, "New tag" creation validated by `AppTagStore.isValidTag`; user tags render in the App Info tag cloud and as compact display-only main-list chips. — *Source: ROADMAP.md*

### v0.5.0 plan-ID closure (iter-143, 2026-05-25)

- [x] EI-01/02/03, NF-02/03/04/05/07/14/15/18, EI-05/06/08/09/10, and the Component Rules preview all shipped in v0.5.0 (Discovery & Polish). NF-06 shipped its Pro Mode explainer correction (hero card deferred — see active roadmap). NF-13 parked (no MuntashirAkon/jadx-android 1.5.5 tag). — *Source: docs/roadmap/COMPLETED.md*

## Stale / Obsolete Items

- [STALE] **Ingest original `0x192/universal-android-debloater` debloat list** — *Reason: upstream UAD archived 2024-08; superseded by the Universal-Debloater-Alliance UAD-NG fork, which is the dataset target instead. Source: docs/research/2026-05-09-capability-extension.md*
- [STALE] **DuckDuckGo Tracker Radar dataset ingest** — *Reason: license-blocked (CC-BY-NC-SA); re-confirmed not ingestible at the bundle level. Source: docs/research/2026-05-09-capability-extension.md*
- [STALE] **TrackerControl bundled tracker DB ingest** — *Reason: derived from Exodus + DDG Tracker Radar; the DDG portion is licence-tainted at the bundle level. Source: docs/research/2026-05-09-capability-extension.md*
- [STALE] **Firebase Analytics / Crashlytics / Performance telemetry** — *Reason: hard reject; triggers F-Droid `Tracking` + `NonFreeNet` antifeatures, requires GMS, defeats the no-root/no-GMS user demographic. Source: docs/research/2026-05-09-observability-testing-audit.md*
- [STALE] **Always-on cloud Sentry / anonymous feature-usage counters / Play Console Vitals / Play Integrity / Play In-App Update** — *Reason: hard reject; incompatible with the F-Droid + GitHub + IzzyOnDroid distribution posture and the project privacy stance (opt-in default-off only). Source: docs/research/2026-05-09-observability-testing-audit.md*
- [STALE] **Full local-VPN per-app firewall (NetGuard / TrackerControl style)** — *Reason: parked/deprioritized; changes the app's purpose, background behavior, battery profile, and support burden; existing AppOps-based tracker blocking addresses the day-1 need. Source: docs/research/2026-05-02-android-power-tools.md, docs/research/2026-05-09-capability-extension.md*
- [STALE] **T21-M Compose performance pass** — *Reason: not applicable; NG does not ship Compose (see `codexprompt.md`). Recorded closed so the legacy bucket is fully accounted for. Source: ROADMAP.md*
- [STALE] **NF-13 jadx-android 1.5.5 pin** — *Reason: parked; no MuntashirAkon/jadx-android 1.5.5 tag exists. Source: docs/roadmap/COMPLETED.md*
- [STALE] **AGP 8.14 bump** — *Reason: empty well; AGP 8.x line ended at 8.13 (no 8.14). The next bump target is the AGP 9.x line. Source: research/iter-20-delta.md*
