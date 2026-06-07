<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Active Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research-driven additions.

Last consolidated: 2026-06-07. Baseline: `main` at `7128b70`, app
`versionName 0.5.0`, `versionCode 7`.

This is the single live to-do file and holds **only open work**. Completed
rows are in [`COMPLETED.md`](COMPLETED.md) (canonical) and its prior ledger at
[`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md); full per-release prose
is in [`CHANGELOG.md`](CHANGELOG.md); consolidated research is in
[`RESEARCH_REPORT.md`](RESEARCH_REPORT.md); long-form research and old ledgers
are under [`docs/roadmap/archive/`](docs/roadmap/archive/) and
[`docs/archive/`](docs/archive/). Do not add new unchecked work to separate root
research files.

> Last researched: Cycle 209 - 2026-06-07.

## Implementer Instructions

- Treat this roadmap as the only active checklist. Shipped items belong in
  `COMPLETED.md` / `docs/roadmap/COMPLETED.md` and release prose belongs in
  `CHANGELOG.md`; do not add new unchecked work to root research files.
- Preserve the GPL-3.0-or-later / REUSE posture: keep SPDX headers, `COPYING`,
  and `LICENSES/` intact, and use GPL-compatible assets/dependencies only.
- Respect the minSdk 21 ceiling. Material 1.14.0, Activity 1.12+, Room 2.8+,
  Work 2.11+, and similar dependency bumps are coordinated product decisions,
  not routine freshness churn.
- Prefer host-verifiable work from bucket A first. Device/manual rows stay open
  until SAF, Quick Settings, Tasker, accessibility, tablet, or Android 17 image
  behavior is actually exercised.
- Researcher-queue ownership tags: `🤖` means implementer-actionable, `🔧`
  means user/external/manual gated, `🔬` means researcher-added this cycle, and
  `✅` means implemented/closed by the build lane.

## Existing Planned Work

Open work is organized below by what is doable from a build host (A–D), then the
device-/maintainer-gated buckets. Research-derived candidate rows that have not
yet been scoped against current code live in `RESEARCH_REPORT.md` and are
surfaced under **Research-Driven Additions** at the end of this file.

## How this roadmap is organized

Open work is grouped by **what is actually doable from a build host** rather
than by historical priority tier:

- **A. Feature wiring — implementable now.** A pure-function, JVM-tested data
  layer already shipped for most of these; what remains is Android UI
  integration (Activities/Fragments/adapters/manifest/drawables). Verify with
  `./gradlew :app:compileFullDebugJavaWithJavac` and
  `./gradlew :app:testFullDebugUnitTest`. Runtime device exercise is a
  follow-up where a device is required.
- **B. Blocked on maintainer / external trackers.** Packets are prepared; the
  remaining step is an external submission only the maintainer can file.
- **C. Blocked on physical-device / OEM verification.** Source-level guards and
  static audits are recorded; only the manual on-device walkthrough remains.
- **D. Parked.** Needs explicit owner sign-off before starting.

## Current State

- v0.5.0 is cut in source (`versionCode 7`) and documented in the changelog.
- All P0–P3 rows, both v0.6.0 blockers (NF-09 routine scheduler, NF-10 premium
  polish phase 2), and the T18 / T19-A / T21-A–D / T21-J buckets are closed —
  see [`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md).
- The build toolchain is verified working on the current host (Gradle 9.4.1,
  AGP 9.2.0, NDK 28.2.13676358, JDK 21 toolchain):
  `:app:compileFullDebugUnitTestJavaWithJavac` and targeted
  `:app:testFullDebugUnitTest` both green as of 2026-05-28; focused installer
  icon-sanitizer coverage and `:app:compileFullDebugJavaWithJavac` were green
  in this checkout on 2026-06-05.
- Most T19 / T20 / T21 rows below already have their data layer + JVM tests
  shipped; the open part is the Android-side UI wiring, called out per row.
- Source-backed 2026-06-06/2026-06-07 audit closures now include file
  properties path display-name hardening, file breadcrumb display-name
  hardening, batch rename dialog display-name hardening, archive progress
  display-name hardening, archive extract conflict display-name hardening, file
  copy conflict display-name hardening, file drawer display-name hardening, file
  properties display-name hardening,
  one-click duplicate APK review display-name hardening, duplicate APK review
  display-name hardening, batch file operation display-name hardening,
  file-manager item display-name hardening, file dialog subtitle display-name
  hardening, saved log display filename hardening, saved log filename validator
  hardening, log export attachment-name hardening,
  saved log subtitle metadata hardening, batch installer URI list hardening,
  hex viewer external metadata hardening, code editor external metadata
  hardening, support-info share intent attachment hardening, Activity
  Interceptor share-details intent hardening, scanner
  missing-signature email intent hardening, operation-history share intent
  hardening, leftover export share intent hardening, provider-query share
  subject hardening, diagnostic ZIP share intent attachment hardening, crash
  share intent attachment hardening, APK share MIME hardening, editor share MIME
  hardening, logcat share attachment MIME hardening, open-with MIME fallback
  hardening, file-share helper input hardening, clipboard oversized fallback
  UTF-8 hardening, support-info bundle text line hardening, diagnostic ZIP text
  line hardening, logcat
  clipboard text hardening, component rules IFW clipboard hardening, Mode
  Doctor clipboard redaction, crash report share redaction, copyable error
  clipboard hardening, accessibility tracker clipboard hardening, file-manager
  path clipboard hardening, KernelSU diagnostics report hardening, root module
  report text hardening, manifest metadata copy TSV hardening, operation
  history text report hardening, profile
  clipboard label hardening, profile share metadata hardening, scanner
  missing-signature report hardening, Activity Interceptor intent-details export
  hardening, logcat saved-log UTF-8 export, installer diagnostic message
  redaction, diagnostic ZIP shared text redaction, profile JSON UTF-8 export,
  support bundle preamble redaction, TSV export formula hardening, structured
  export escaping utility,
  app-list CSV nullable-field hardening, app-list XML nullable-field hardening,
  app-list Markdown escaping, app-list CSV formula hardening, logcat export
  formula hardening, operation history export formula hardening, operation
  history
  replay identity validation, operation history target routing,
  operation history shared scalar normalization, operation history snapshot
  normalization, operation history failed cleanup matching,
  operation history replay payload validation,
  operation history row type/status fallback, operation history metadata scalar
  hardening, operation history metadata array hardening, duplicate backup
  cleanup history label hardening, backup base-name display label hardening,
  backup database backup-name projection hardening,
  backup metadata backup-name parser hardening, profile backup-data parser
  hardening, direct backup operation option parser hardening, batch backup
  string-array parser hardening, DexOpt option parser hardening, batch backup
  flag parser hardening, batch backup
  import option parser hardening,
  batch freeze option parser hardening, batch network-policy option parser
  hardening, batch permission option parser hardening, batch component option
  parser hardening, batch AppOps option parser hardening, batch queue operation
  parser hardening,
  batch queue target parser hardening, batch journal target parser hardening,
  profile trigger type
  parser hardening, backup schedule skipped-detail parser hardening, backup
  adoptable data-restore mapping, backup removable data-directory
  classification, backup metadata data-root
  hardening, backup metadata installer-package hardening, backup metadata
  data-directory hardening, backup metadata version-code hardening, backup
  metadata APK-filename hardening, backup metadata package-name hardening,
  backup metadata checksum-algorithm hardening, backup metadata archive-type
  hardening, backup metadata timestamp hardening, backup metadata user-id
  hardening, backup crypto mode parsing hardening, backup metadata parse-error
  wrapping, backup checksum row generation hardening, backup checksum duplicate
  row hardening, intent long extra literal parsing, default-role metadata
  sanitization, backup checksum row parser hardening, Activity Interceptor
  pasted USER header hardening, Activity Interceptor pasted ROOT header
  hardening, backup archive filename filtering, ADB backup category path
  hardening, SSAID rule value hardening, URI grant scalar parser hardening, rule
  numeric negativity hardening, app-op rule numeric hardening, freeze rule
  method hardening, component rule status-token hardening, rule import boolean
  token hardening, ADB encrypted key-blob length hardening, ADB backup
  compression flag hardening, ADB backup numeric header hardening, manifest
  intent-filter name hardening, manifest intent-filter priority hardening,
  dynamic shortcut candidate validation, app action shortcut target validation,
  profile URI package override validation, automation negative user-id
  validation, automation component-name validation, package-name empty-segment
  validation, Hail profile empty-import rejection, external profile
  package-name validation, logcat search criteria hardening, logcat numeric
  field parser hardening, Titanium Backup metadata parser hardening,
  default-app role holder parser hardening, selected-user preference parser
  hardening, rule import TSV required-field hardening, URI grant flattened
  parser hardening, Activity Interceptor pasted header hardening,
  intent empty primitive-array serialization, intent `CharSequence` collection
  serialization, intent `CharSequence` extra serialization, APKS duplicate
  split validation, SysConfig permission label display, SysConfig named-actor
  conversion hardening, SysConfig associated-package label display, File
  properties shared-UID label display, intent flattened string empty/tab extra
  parsing, Finder matched result evidence display, ADB backup header hex
  validation, split APK chooser type tracking, sysconfig runtime RAM feature
  injection, owner UID parser hardening, sysconfig low-RAM feature filtering,
  VFS permission mutation result reporting, intent empty-list extra
  serialization, intent extra edit-mode prefill formatting, intent
  escaped-comma extra parsing, intent string null-extra parsing, SAF VFS parent
  URI mapping, batch clear-cache multi-volume trimming, nullable last path
  segment semantics, certificate extension OID descriptions, OpenPGP backup
  provider availability checks, multithreaded executor factory synchronization,
  NoOps annotation detector coverage, running-service client counts from
  dumpsys, external key import algorithm guardrails, APK export filename
  placeholder substitution, Running Apps VirusTotal command-line file
  selection, changelog inline markup support, logcat shared-UID package
  attribution, Titanium Backup import timestamp preservation, file content MIME
  detection for mismatched extensions, Dex VFS API-level mount options, File
  Manager VFS read-only mount mapping, main-list split/SAF filter conversion,
  main-list user-filter persistence, data-only split reinstall routing, and
  APKS split-source export fallback; closed details live in `COMPLETED.md`.

## A. Feature wiring — implementable now

### Build-host hygiene

- [x] **P1 Repair `scripts/android-libraries` submodule tracking**
  - Why: `.gitmodules`, `CLAUDE.md`, and `PROJECT_CONTEXT.md` all state that
    `scripts/android-libraries` and `scripts/android-debloat-list` are required
    build-time submodules, but the superproject currently tracks only
    `scripts/android-debloat-list` as a gitlink. A fresh checkout running
    `git submodule update --init --recursive` will not fetch
    `scripts/android-libraries`, while this working tree has it only as an
    untracked local clone at `8fb3919`.
  - Shipped 2026-06-04: added the missing `scripts/android-libraries` gitlink
    at upstream `8fb3919828e9c9f6e75faaaa322c5af59c6d05fa` so the existing
    `.gitmodules` entry materializes the scanner library dataset from a fresh
    checkout.
  - Evidence: `git ls-files scripts/android-libraries scripts/android-debloat-list`
    returns both gitlinks; `.gitmodules` declares both.
  - Touches: submodule/gitlink setup for `scripts/android-libraries`; README /
    BUILDING / project-context wording if the intended source of the library DB
    changes.
  - Acceptance: a fresh clone plus `git submodule update --init --recursive`
    materializes both dataset directories at reviewed commits.
  - Verify: clone into an empty temp directory, run the documented submodule
    command, and confirm both `scripts/android-libraries/libs.json` and
    `scripts/android-debloat-list/*.json` exist without manual cloning.

### T19 — Package-aware storage analysis

- [x] **T19-B Leftover detection after uninstall**: One-Click Ops "Detect
  leftover folders" entry shipped 2026-05-28 — `OneClickOpsViewModel.scanLeftovers`
  builds the installed set, scans `Android/{data,obb,media}` (and, when
  privileged, the root `/data/data` stubs), precomputes per-folder sizes off the
  main thread, and posts a `LeftoverEntry` list; the Activity shows a searchable
  multi-choice review dialog ("pkg · kind · size"), gates deletion behind
  `ActionAuthGate`, and `deleteLeftovers` removes each folder via the privileged
  `Paths.get(...).delete()` (recursive) with per-folder audit lines through the
  app `Log` and a "reclaimed X" result toast. The review dialog now also has
  an "Export results" action that shares selected rows as TSV; App Details now
  opens data-only PackageManager records and offers a guarded Clear data action
  for the same leftover-data cleanup path. _Data layer: `LeftoverScanner`
  (scan/scanInternalDataStubs/selectOrphans/sizeOnDisk; 15 JVM tests),
  `LeftoverExportFormatter` (stable TSV + formula-field defusing; 2 JVM tests),
  `LeftoverCleanupHistoryItem` (cleanup op-history rows with high-risk,
  non-replayable metadata; 2 focused tests), and `ApplicationInfoCompatTest`
  package-state coverage._
- [x] **T19-C APK duplicate finder**: One-Click Ops "Find duplicate APK files"
  entry shipped 2026-05-28 — `OneClickOpsViewModel.scanApkDuplicates` walks
  external storage with `ApkFileScanner`, fingerprints each `.apk` via
  `PackageManager.getPackageArchiveInfo` + `PackageUtils.getSigningCertSha256Checksum`
  (cert re-derived from the archive path), runs `ApkDuplicateSelector` keeping
  the largest copy, and posts duplicate groups. The review dialog flattens the
  drop set ("file · pkg vN · size · keeping <keeper>"), gates deletion behind
  `ActionAuthGate`, deletes via the privileged `Paths.get(...).delete()` with
  audit logging + a reclaimed-bytes toast. _Data layer: `ApkDuplicateSelector`,
  `ApkFileScanner`, `ApkBundleHeaderParser`, `ApkBundleBaseExtractor`,
  `ApkDuplicateOperations`, `ApkDuplicateScanRoots`,
  `FmDuplicateApkSelectionUtils`; 53 JVM tests.
  File Manager multi-select also offers "Find duplicate APK files" for at
  least two readable local APK-like files and reuses the same parse/review/
  authenticated-delete path. One-Click Ops also scans the configured
  AppManager backup directory when it resolves to a local filesystem root,
  deduplicating overlap with external storage._
- [x] **T19-D Backup duplicate cleaner**: One-Click Ops "Delete duplicate
  backups" entry shipped 2026-05-28 and follow-up closed 2026-06-03 — offers
  "keep largest", "keep newest", and "keep oldest". The ViewModel now builds a
  size-aware duplicate plan, confirms with an estimated reclaimable-byte hint,
  gates deletion behind `ActionAuthGate`, records a dedicated cleanup
  `op_history` payload, and reports deleted-count + reclaimed-bytes toasts.
  Same-version duplicates across backup folders/names collapse to one copy per
  package. _Data layer:
  `selectVersionDuplicates`/`pruneVersionDuplicates` (NEWEST/OLDEST/LARGEST/
  LARGEST_THEN_NEWEST), `BackupItem.getTotalSize()`,
  `backupItemSizeResolver`, `reclaimableBytes`,
  `DuplicateBackupCleanupHistoryItem`; focused JVM coverage pins selector,
  size, reclaimable-byte, and cleanup-history serialization behavior._

### T20 — Performance and profiling (system-level)

- [x] **T20-A Perfetto system-trace export**: App Details overflow ->
  "Export Perfetto trace" shipped 2026-05-28 — gated on root/Shizuku/ADB (with
  an "Open developer options" fallback when unavailable), confirms, then
  `AppProfileCapture.capturePerfettoTrace` pipes the
  `PerfettoTraceConfigBuilder` text-proto to `perfetto -c - --txt -o` via
  `Runner` (argv validated by `PrivilegedRunnerArgValidator`), saving a
  `.perfetto-trace` to Downloads and offering an "Open Perfetto UI" button
  (`perfettoUiUrl()`). The confirm dialog shows a pre-capture config preview
  ("10s · 64 MB ring · N ftrace events · pkg") via `PerfettoConfigInspector`
  (shipped 2026-05-28). Follow-up picker shipped 2026-06-03: the action now
  opens a duration dropdown (`5s`/`10s`/`30s`/`1m`/`2m`) before the final
  confirmation, and the config preview reflects the selected duration. _Data
  layer: `PerfettoTraceConfigBuilder`, `PerfettoCommandBuilder`,
  `PerfettoConfigInspector`, `ProfileCaptureOptionCatalog`. **Follow-up:
  true mid-capture cancellation remains device-verified.**_
- [x] **T20-B simpleperf CPU profile capture**: App Details overflow ->
  "Record CPU profile" shipped 2026-05-28 — gated on root/Shizuku/ADB, confirms
  (explaining the DWARF call-graph + platform `simpleperf`), then
  `AppProfileCapture.captureCpuProfile` runs the `CpuProfileCommandBuilder`
  argv (validated) via `Runner`, saving raw `perf.data` to Downloads.
  Follow-up picker shipped 2026-06-03: the action now opens duration + event
  dropdowns, with events filtered by device API level and primary ABI through
  `CpuProfileEventCatalog`; the command-builder allow-list is aligned so every
  offered event is honored. _Data layer: `CpuProfileCommandBuilder`,
  `CpuProfileEventCatalog`, `ProfileCaptureOptionCatalog`,
  `PrivilegedRunnerArgValidator`. **Follow-up: on-device flame-graph SVG
  conversion and true mid-capture cancellation remain device-verified.**_
- [x] **T20-C Memory allocations inspector**: App Details overflow ->
  "Memory snapshot" shipped 2026-05-28 — `AppMemorySnapshotLoader.load` runs
  `dumpsys meminfo`/`gfxinfo` plus `pidof` -> `/proc/<pid>/status` + `/maps`
  through `Runner`, feeds the JVM-tested parsers, composes via
  `MemorySnapshotComposer`, and `format()` renders a provenance-tagged
  ("via /proc/maps · virtual", "via /proc/status") scrollable block using
  `MemoryFormat`. The action is gated on root/Shizuku/ADB and degrades
  gracefully when the app is not running or a source is truncated. Follow-up
  refresh/chart shipped 2026-06-03: the dialog now offers a Refresh action that
  reloads the snapshot for the same package and the formatted output includes a
  per-region proportional text chart for Dalvik, native, stack, code, and
  library virtual-memory buckets. _Data layer: the four parsers + composer +
  `MemoryFormat` + `MemoryRegionChart`; 50+ JVM tests, plus focused chart and
  `firstPid`/`provenanceFor` coverage. **Follow-up: true live streaming /
  auto-refresh remains device-verified.**_

### T21 — UI/design polish and premium feel

- [x] **T21-E Discreet / generic launcher-icon mode**: shipped 2026-05-28 —
  three manifest activity-aliases (`SplashAliasNgMark`/`Neutral`/`Monochrome`,
  all `enabled="false"`, targeting `SplashActivity` with `LAUNCHER` filters),
  neutral + monochrome vector icons, and `LauncherIconAliasController` that maps
  each `LauncherIconStyle` to its component, reads the live enabled-set as the
  source of truth, and applies the `LauncherIconAliasPlan` diff via
  `setComponentEnabledSetting(..., DONT_KILL_APP)`. Wired into Settings ->
  Appearance -> "Launcher icon" (single-choice, reflects current style). aapt2
  link + JVM tests green. _Data layer: `LauncherIconAliasPlan` (plan +
  resolveCurrent; 11 tests) + 3 new controller-mapping tests._
  **Device verification still required:** the alias enable/disable round-trip
  and launcher re-enumeration (incl. the disabled-target interaction) can't be
  exercised on a CI host. **Designer follow-up:** polished adaptive-icon assets
  to replace the functional neutral/mono vectors; glossary strings.
- [x] **T21-F Undo SnackBar for destructive operations**: shipped 2026-05-28 at
  the main-list batch chokepoint (`MainActivity.handleBatchOpAfterAuth` ->
  `dispatchBatchOpOrUndo`). Destructive batch ops (uninstall/clear-data ->
  CRITICAL, freeze/component-block -> HIGH, force-stop -> NORMAL) now open a
  per-op `UndoableActionQueue` + a Material `Snackbar` whose duration is
  `SnackbarDurationPolicy.windowFor(severity, ANIMATOR_DURATION_SCALE)` (so it
  honors the reduced-motion gate). Tapping **Undo** `cancel()`s the deferred
  dispatch entirely; a non-action dismiss (timeout / navigate-away) `drainAll()`s
  and commits via `startForegroundService`. A fresh per-op queue prevents a
  later op's SnackBar (CONSECUTIVE-dismissing this one) from committing it early.
  op_history is still written by `BatchOpsService` on dispatch (no
  double-record). compile + aapt2 link green. _Data layer: `UndoableActionQueue`,
  `SnackbarDurationPolicy`, `UndoOpHistoryRecorder`; 27 JVM tests._ **Follow-up
  (device-gated): verify the SnackBar↔dispatch timing on-device; extend the
  same gate to App Details single-app destructive actions + One-Click Ops.**
- [x] **T21-G Attention badges on app list rows**: shipped 2026-05-28 — the
  main-list row icon now overlays a single severity-tinted **true-circle dot**
  (12 dp, `bg_attention_dot`, recoloured per `Severity`) bound in
  `MainRecyclerAdapter.bindAttentionBadge` via a new
  `AttentionBadgeSource.forItem`/`badgeFor(ApplicationItem,int)` overload
  (reusing the `ApplicationItem` perm/rule counts already loaded — single
  source of truth, no extra query in the hot path). The exact count + reason
  live in the `contentDescription` for TalkBack, so the visible indicator stays
  a compliant circle rather than a stadium count chip. Glossary entry added
  (Settings -> Glossary -> "Attention badges"). aapt2 link + compile green.
  _Data layer: `AttentionBadgeCalculator`, `AttentionBadgeSource`,
  `OsRevertCountTracker`. OS-revert wiring completed 2026-05-28:
  `OsRevertCountTracker.getInstance()` is now a process-wide singleton;
  `OsRevertMonitor.schedule` records into it (per package) on every detected
  revert (freeze / component / app-op / doze), the main-list adapter reads
  `countRecent(pkg, now, DEFAULT_TTL_MILLIS)` per row, and `MainActivity.onResume`
  runs `evictExpired` as the prune heartbeat. compile green._
- [ ] **T21-H Material 3 Adaptive layouts for tablets / large screens**: App
  List + App Details master/detail and Settings two-pane via `androidx.window`
  + `SlidingPaneLayout`. Acceptance: compatible with existing View-based
  layouts, all view IDs preserved per the `codexprompt.md` contract, gated on
  available-width thresholds not fixed device classes. _Width-class resolver
  shipped: `WindowWidthSizeClass.resolve(int)` + `supportsTwoPane`/
  `requiresTwoPane` (COMPACT <600 / MEDIUM 600–840 / EXPANDED ≥840); 7 JVM
  tests. `androidx.window 1.4.0` is already a dependency. Host-prep shipped
  2026-06-06: `SettingsActivity` now gates its existing two-pane layout with
  `WindowWidthSizeClass.requiresTwoPane(screenWidthDp)` instead of raw pixel
  width, with focused JVM coverage in `SettingsActivityLayoutModeTest`.
  Additional host-prep shipped 2026-06-06: `MainActivityEmbeddingContractTest`
  pins the existing MainActivity/AppDetails `androidx.window` split pair,
  placeholder, split width/ratio, finish semantics, and manifest exposure._
  **Device-gated** — a layout restructure whose correctness (view-ID
  preservation + nav/back behavior) cannot be CI-verified; must be exercised on
  a tablet/foldable before shipping, so it is not landed blind. **Concrete
  plan for the device pass:**
  1. **Settings first (lowest risk)**: add `layout-w840dp/activity_settings.xml`
     that wraps the existing `FragmentContainerView` in a `SlidingPaneLayout`
     (left: a preferences-headers list; right: the detail container, keeping the
     existing fragment-container view ID). `SettingsActivity` reads
     `WindowWidthSizeClass.resolve(config.screenWidthDp)`; on EXPANDED it opens
     the two-pane (header click -> replace detail pane) else the current
     single-pane. No existing IDs change — the `-w840dp` resource qualifier
     swaps the layout only on wide screens.
  2. **App List + App Details master/detail**: `layout-w840dp/activity_main.xml`
     hosting a `SlidingPaneLayout` (left: the existing `R.id.item_list`
     RecyclerView untouched; right: a `FragmentContainerView` for an embedded
     `AppInfoFragment`). On EXPANDED, a row tap binds the detail fragment instead
     of launching `AppDetailsActivity`; on COMPACT it keeps the current launch.
     All current main-list IDs preserved; the detail pane is additive.
  3. Verify on Pixel Fold / Tab S9: rotation + fold/unfold keeps state, back
     from detail collapses the pane, and COMPACT phones are visually unchanged.
- [~] **T21-I Fast list rendering for 10k+ installed apps**: source audit +
  safe optimizations shipped 2026-05-28 in
  [`docs/audits/2026-05-28-large-list-rendering.md`](docs/audits/2026-05-28-large-list-rendering.md).
  Confirmed the list already uses stable IDs + `DiffUtil` (not blanket
  `notifyDataSetChanged`) and async icon loading. Applied behavior-preserving
  `setHasFixedSize(true)` + `setItemViewCacheSize(15)`. The audit identifies the
  **main-thread `DiffUtil.calculateDiff` in `setDefaultList` as the dominant
  cold-filter cost** and specifies the async-diff fix + a Perfetto-based
  measurement plan. **Open (device-gated): move the diff off-main
  (AsyncListDiffer / background `calculateDiff`) and record the real before/after
  numbers — both require a seeded 10k-app device profile, so this is not landed
  blind.**

### Discovery & Polish carry-overs (re-surfaced from iter-143 handoff)

These were noted open in `.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md`
but were dropped from the 2026-05-26 consolidation. Folded back in here.

- [x] **EI-04 Permission Inspector chip-row filter**: shipped 2026-05-28 — a
  single-selection chip row (All / Requested / Granted / Needs review) in the
  Permission Inspector header, backed by the pure-function
  `PermissionInspectorFilter` (`matches`/`apply`; 4 JVM tests) and re-filtering
  the group catalog on chip change in `PermissionInspectorActivity`. Reuses the
  bounded-radius `Widget.AppTheme.Chip.MainFilter` style (no pill backdrops).
  "Needs review" surfaces groups where some requesting app hasn't granted
  (`requested > granted`). compile + aapt2 link + filter tests green.
- [x] **EI-07 Scheduled-backup "Why did this skip?"**: fully shipped
  2026-06-03 — `AutoBackupScheduler.BackupSelection` carries
  `getSkippedDetails()` (package, user, `SkipReason`, newest existing-backup
  time), `AutoBackupWorker` logs skipped packages, terminal results persist a
  bounded skipped-package JSON payload in `AppPref`, and Settings ->
  Backup/Restore -> "Schedule status" is selectable with a diagnostics surface
  that lists package-level skip reasons from the last run. Focused JVM coverage
  pins selection, round-trip serialization, bounded storage, and malformed-input
  handling.
- [ ] **NF-06 Pro Mode hero card**: **blocked on a product/UX decision** (no
  acceptance spec). The onboarding flow is a privilege-*mode* picker
  (Auto/Root/Shizuku/ADB); "Pro Mode" is the orthogonal advanced-features toggle
  and is already explained via the Settings -> Glossary "Pro Mode" entry. A
  "hero card" has no defined placement (onboarding step? dashboard? a Pro Mode
  settings screen?) or content, and inventing one would be speculative UI
  against the "no unrequested features" rule. Needs a one-line decision on
  *where* the hero card lives and *what* it says before implementation.
- [x] **NF-08 tag UI follow-up**: App Details overflow -> "Edit tags" shipped
  2026-05-28 — a `SearchableMultiChoiceDialogBuilder` over all known + current
  tags (current pre-selected) assigns/removes user tags via `AppTagStore`, and a
  "New tag" neutral button opens a `TextInputDialogBuilder` (validated by
  `AppTagStore.isValidTag`) to create one. This closes the loop so the Finder
  "Tags" filter (`TagsOption`) finally has a way to create the tags it matches.
  User tags now also render in the App Info tag cloud (tappable -> editor),
  shipped 2026-05-28. Main-list tag chips shipped 2026-06-03: list loads attach
  the `AppTagStore` snapshot once per refresh, rows show a compact display-only
  first-tag + remainder-count chip, and the row accessibility label includes the
  full tag list. compile + aapt2 link green. _Data layer/display formatter:
  `AppTagStore`, `MainListTagChipFormatter`; focused JVM coverage._

## B. Blocked on maintainer / external trackers

- [ ] **IzzyOnDroid listing** — packet ready in
  `docs/distribution/izzyondroid-listing.md`; blocked on a maintainer filing
  the external inclusion request and confirming the `floss` APK asset pattern.
- [ ] **F-Droid listing** — packet ready in
  `docs/distribution/fdroid-listing.md`; blocked on a maintainer filing the
  external fdroiddata merge request and watching F-Droid CI feedback.
- [ ] **Accrescent listing** — notes + signed APK-set helper ready in
  `docs/distribution/accrescent-listing.md` and
  `scripts/build_accrescent_apks.sh`; blocked on a maintainer/product decision
  (current Accrescent policy conflicts with the installer permission +
  non-disability accessibility service) plus allowlisted-account / keystore
  access.
- [ ] **UAD-NG model/region preinstalled-list ingest** — blocked until
  `universal-android-preinstalled-lists` publishes machine-readable
  package/OEM/model/region data. Current upstream has no usable list files as
  of 2026-06-04, so NG uses bundled debloat metadata plus conservative
  package/description inference for known-preinstall-OEM chips.
- [ ] **Privacy/security target-scoped live-state sources** — blocked until a
  public or privileged per-target source is proven for SDK Runtime loaded-SDK
  state, Health Connect granted-permission state, or Credential Manager
  enabled-provider state. The shipped App Info surfaces intentionally stay on
  target-scoped manifest metadata where platform APIs are caller-scoped.

## C. Blocked on physical-device / OEM verification

Source-level guards and static audits are recorded; only the manual on-device
walkthrough remains open for each.

- [ ] **High-contrast theme audit** — static v2 palette/string hardening in
  `docs/audits/2026-05-26-high-contrast-theme.md`; manual device/OEM walkthrough
  open.
- [ ] **200% font-scale audit** — source guards in
  `docs/audits/2026-05-26-font-scale.md`; broader major-screen/device walkthrough
  open.
- [ ] **TalkBack traversal and action-label audit** — hardening in
  `docs/audits/2026-05-26-talkback-action-labels.md`; full runtime traversal +
  adapter-bound row verification open.
- [ ] **Reduced-motion setting audit** — app-owned transitions honor system
  animation scale via `MotionUtils`; device verification + Material-internal
  motion checks in `docs/audits/2026-05-26-reduced-motion.md` open.
- [ ] **Dyslexia-font compatibility audit** — static slice +
  `DyslexiaFontCompatibilityContractTest` in
  `docs/audits/2026-05-26-dyslexia-font-compatibility.md`; manual device
  walkthrough on a dyslexia-font ROM open.
- [ ] **Android 17 device/emulator verification (Shizuku + 16 KB page size)** —
  weekly + dispatch
  [`android17-emulator.yml`](.github/workflows/android17-emulator.yml) assembles
  the FLOSS debug APK, runs `scripts/verify-native-page-alignment.py`, and runs
  hidden-API + DB-migration instrumented tests on API-37. Real-device Shizuku
  sign-off open.
- [ ] **Material You dynamic-color manual device walkthrough** — source audit is
  clean and guarded (`DynamicColorContractTest`); only the on-device check
  remains.
- [ ] **App Archiving API-35 user-action walkthrough** — App Info and batch
  Archive/Unarchive requests are implemented and guarded by
  `AppArchiveManagerTest` / `FreezeOptionTest`; manual verification on an
  Android 15/API-35 image remains open for the pending-user-action flow and
  archived-state refresh.
- [ ] **Tasker / Quick Settings / SAF manual walkthrough** — the in-app Locale
  plug-in, freeze-profile and force-stop Quick Settings tiles, and
  read-only `DocumentsProvider` roots are implemented and covered by host tests.
  Manual verification remains open for a real Tasker fire, tile add/fire flows,
  privileged force-stop behavior, and SAF picker visibility for backups and
  profiles.
- [ ] **Macrobenchmark/profile/smoke device matrix** — list-scroll and Backup
  settings TTI macrobenchmarks, core-journey Baseline Profile collection, and
  app-list/selection/One-Click Ops UIAutomator smoke scaffolds are implemented.
  Remaining work is running the benchmark/profile generation on an online
  device and running the smoke suite across API 26/30/34/35 before enabling a
  device CI lane.
- [ ] **Root module inventory rooted-device walkthrough** — Settings ->
  Privileges -> Modules is implemented as a root-gated, read-only
  Magisk/MMRL + LSPosed `module.prop` inventory and host-covered by
  `RootModuleInfoTest`. Manual verification remains open on a rooted device
  with at least one Magisk or LSPosed module to confirm the row is hidden
  without a privileged shell and read-only when modules are present.

## D. Parked (needs explicit owner sign-off)

- [ ] **T20-D LeakCanary leak-detection wrapper** — requires a debuggable agent
  in target processes; conflicts with the GPL+vendored posture; legacy roadmap
  flagged high-risk-for-stability.
- [ ] **T21-K Material 3 Expressive migration** — gated on Material Components
  1.14 + minSdk 23 (held at 21 through v0.6.x).
- [ ] **T21-L Custom user shell-action / Tasker batch actions** — non-trivial
  security review; reopen after the broadcast-intent automation API has at
  least one external production integration.
- [x] **T21-M Compose performance pass** — **Not applicable**: NG does not ship
  Compose (see `codexprompt.md`). Recorded closed so the legacy bucket is fully
  accounted for.

## Known failing unit tests — RESOLVED 2026-05-28

The 4 failures from the 2026-05-28 full-suite run are fixed; `:app:testFullDebugUnitTest`
is green (1092 tests).

- [x] `ApkDuplicateSelectorTest.sizeTieBreaksOnAbsolutePathDeterministically`
  — was a Windows-host artifact (the `C:` drive prefix). Test now compares the
  keeper basename (`getName()`), independent of the host's absolute-path form.
- [x] `SnackbarDurationPolicyTest.minWindowFloorAppliesEvenAfterShrinking`
  — was a stale assertion; the impl is correct (scale clamps to 0.5× → 2000 ms,
  which stays above the 1500 ms floor). Test now asserts the documented
  two-clamp behavior.
- [x] `PrivilegedRunnerArgValidatorTest.rejectsShellMetacharacters` and
  `…isSafePathAndIsSafeArgumentMatchClassifierOk` — fixed in the **impl**:
  `\n`/`\r` are now classified `SHELL_METACHARACTER` (command separators) before
  the generic control-byte check, and a space in a single argv token is rejected
  as `SHELL_METACHARACTER` (word-splitting / injection signal; no legitimate
  perfetto/simpleperf argv element contains a space). Strengthens the gate;
  no behavior change for valid argv.

## Verification Cadence

For code changes, run the narrowest relevant unit tests first, then compile the
affected flavor. `JAVA_HOME` must point at the real JDK 21 (the machine env var
may be stale — the toolchain JDK is at
`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`).

- `./gradlew :app:testFullDebugUnitTest --tests "*YourNewTest*"`
- `./gradlew :app:compileFullDebugJavaWithJavac`
- `./gradlew :app:assembleFullDebug`

For documentation-only changes, at minimum run `git diff --check` and check
links touched by the edit.

## Archive & Completed

- [`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md) — checklist-level
  record of fully-closed rows.
- [`docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md`](docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md)
  — old long-form T1–T21 ledger, engineering-debt register, upstream sync
  strategy, external source appendix.
- [`docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25.md`](docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25.md)
  and
  [`docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25-pass-2.md`](docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25-pass-2.md)
  — the 2026-05-25 research feature plans that fed the NF/EI backlog.
- [`COMPLETED.md`](COMPLETED.md) — canonical completed/stale ledger.
- [`RESEARCH_REPORT.md`](RESEARCH_REPORT.md) — consolidated research notes.
- [`docs/archive/`](docs/archive/) — the dated research notes folded into
  `RESEARCH_REPORT.md` on 2026-06-03, preserved verbatim.

---

## Research-Driven Additions

### Researcher Queue (Cycle 1 - 2026-06-04)

- [x] 🔬 `toolchain-ceiling-device-gate-refresh-2026-06-04` - rechecked the
  post-Tasker/SAF/QS `main` state, Android/Google Maven metadata, and the
  minSdk-21 ceiling pins. The active queue is now mostly device/manual gated;
  Material 1.14.0 and other newer AndroidX lines stay behind the documented API
  21-22 ceiling, so no new dependency row was promoted.

### Researcher Queue (Cycle 2 - 2026-06-04)

### Researcher Queue (Cycle 3 - 2026-06-04)

### Researcher Queue (Cycle 4 - 2026-06-04)

### Researcher Queue (Cycle 5 - 2026-06-04)

- [x] 🔬 `release-trust-overlay-profile-gap-refresh-2026-06-04` - rechecked
  current `main`, release/distribution workflows, Gradle supply-chain controls,
  upstream AppManager issue deltas, Android Private Space/profile docs, overlay
  security docs, Shizuku/UAD/Canta release state, and the completed ledger. Five
  net-new rows were promoted; AppOps UID/mode handling and recent upstream
  recovery/action-rail issues were not duplicated because they are already
  shipped or represented by existing gates.

### Cycle 5 Promoted Items (2026-06-04)

- [x] P1 - Add Gradle dependency verification and dependency locking
  - Why: AppManagerNG pins the Gradle wrapper hash and runs OWASP CVE audits, but
    plugin/application/test/benchmark artifacts still resolve from Google Maven,
    Maven Central, and JitPack without checked dependency metadata or lockfiles.
    A privacy/security package manager should fail closed on unreviewed binary
    drift, not only on known CVEs after resolution succeeds.
  - Evidence URL or file:line:
    `gradle/wrapper/gradle-wrapper.properties:3`, `build.gradle:24-29`,
    `build.gradle:52-68`;
    https://docs.gradle.org/current/userguide/dependency_verification.html and
    https://docs.gradle.org/current/userguide/dependency_locking.html.
  - Touches: Gradle verification metadata, dependency lockfiles for root/app/
    benchmark/test configurations, JitPack dependency review notes, CI
    dependency jobs, and distribution docs.
  - Shipped 2026-06-06: root Gradle now locks all project configurations in
    strict mode and explicitly locks the root buildscript classpath. Checked-in
    lockfiles cover the app, benchmark, docs, hiddenapi, libcore modules,
    libopenpgp, libserver, server, and buildscript classpath. Checked-in
    `gradle/verification-metadata.xml` enables strict dependency verification
    with SHA-256 checksums for plugin/buildscript/application/test/benchmark
    artifacts, including Android Gradle Plugin detached AAPT2 tool artifacts.
    `docs/distribution/dependency-verification.md` documents the refresh and
    validation commands plus JitPack/key-review expectations.
  - Acceptance: normal CI fails on missing/changed artifact checksums or
    unreviewed lock drift; all production, test, benchmark, plugin, and build
    logic dependencies are covered; the update process is documented so
    maintainers can intentionally refresh metadata.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest`
    under strict verification and locking with no write flags, after generating
    metadata/locks through dependency reports and the same focused app test path.
    A deliberate bad-checksum drill remains a low-cost future hardening check in
    a throwaway copy.

- [x] P2 - Publish release SBOM and provenance attestation
  - Why: the release workflow performs a strong two-build reproducibility check
    and publishes `.sha256` sidecars, but release consumers still lack a
    machine-readable SBOM and a provenance attestation tying each APK digest to
    the tag, workflow, and repository state.
  - Evidence URL or file:line: `.github/workflows/release.yml:1-4`,
    `.github/workflows/release.yml:19-20`,
    `.github/workflows/release.yml:73-94`,
    `docs/distribution/reproducible-builds.md:5-8`,
    `docs/distribution/reproducible-builds.md:23-41`;
    https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations,
    https://github.com/actions/attest, and
    https://github.com/CycloneDX/cyclonedx-gradle-plugin.
  - Touches: release workflow permissions (`id-token: write`), SBOM generation
    task/plugin or init-script path, release asset upload list, provenance
    attestation step, reproducible-build docs, and verification instructions.
  - Shipped 2026-06-06: `scripts/generate-cyclonedx-sbom.py` now emits a
    CycloneDX 1.6 aggregate SBOM from the checked Gradle lockfiles and validates
    the generated shape, duplicate references, Maven purls, aggregate app
    component, and dependency coverage. Both reproducible-release wrappers add
    `AppManagerNG-reproducible.cdx.json` beside the publish APKs and include it
    in `release-assets.txt`. The GitHub release workflow now copies the SBOM to
    a versioned `AppManagerNG-<version>.cdx.json` release asset, requires
    `id-token: write` plus `attestations: write`, and uses
    `actions/attest@v4` to publish APK provenance and CycloneDX SBOM predicate
    attestations.
  - Acceptance: every tag release publishes APKs, `.sha256` files, a named SBOM
    for each shipped flavor or a clear aggregate, and a verifiable provenance
    attestation for each APK digest; docs show how to verify the checksum,
    attestation, and SBOM.
  - Verify: passed 2026-06-06:
    `python scripts\generate-cyclonedx-sbom.py --version 0.5.0 --output build\reproducible-release\publish\AppManagerNG-reproducible.cdx.json`;
    `python scripts\generate-cyclonedx-sbom.py --check build\reproducible-release\publish\AppManagerNG-reproducible.cdx.json`;
    official CycloneDX 1.6 JSON schema validation via Python `jsonschema`;
    `bash -n scripts/verify_reproducible_release.sh`;
    PowerShell script parse check for `scripts/verify_reproducible_release.ps1`;
    and `rtk git diff --check`. A real tag run must still verify the published
    APK with `gh attestation verify` after GitHub records the attestations.

- [x] P1 - Harden the foreground UI tracker overlay against device-freeze reports
  - Why: upstream reports the UI Tracker window freezing a rooted Android 11
    OnePlus device. NG's tracker is driven from an accessibility service but
    creates an application overlay/phone window with no-limit layout, a large
    fixed width calculation, and direct add/update calls. That combination needs
    a conservative bounds/throttle/failsafe pass before treating the tracker as
    safe across OEMs.
  - Evidence URL or file:line:
    https://github.com/MuntashirAkon/AppManager/issues/1848,
    `app/src/main/java/io/github/muntashirakon/AppManager/accessibility/activity/TrackerWindow.java:73-84`,
    `app/src/main/java/io/github/muntashirakon/AppManager/accessibility/activity/TrackerWindow.java:183-186`,
    `app/src/main/java/io/github/muntashirakon/AppManager/accessibility/activity/TrackerWindow.java:225`,
    `app/src/main/java/io/github/muntashirakon/AppManager/accessibility/activity/TrackerWindow.java:269-271`;
    https://developer.android.com/reference/android/view/WindowManager.LayoutParams
    and
    https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events.
  - Touches: `TrackerWindow`, `NoRootAccessibilityService`, tracker settings/
    copy, extracted overlay layout policy helpers, and accessibility/overlay
    regression tests.
  - Shipped 2026-06-06: `TrackerWindow` now uses the accessibility-service
    overlay type on API 22+ instead of the untrusted application-overlay type,
    keeps only focus/pass-outside-touch flags instead of `FLAG_LAYOUT_NO_LIMITS`,
    clamps expanded/iconified size and centered offsets to display margins,
    refreshes metrics before layout updates, throttles `updateViewLayout`
    storms, and disables the tracker with an explanation after repeated
    `WindowManager` add/update failures. `TrackerOverlayPolicyTest` pins window
    type selection, flags, width clamps, centered-offset clamps, throttling, and
    repeated-failure disable thresholds.
  - Acceptance: tracker windows use the accessibility-service-appropriate window
    type when available, clamp size/position through current window metrics, keep
    a visible pause/dismiss affordance, throttle update storms, degrade to
    disabled-with-explanation after repeated add/update failures, and do not
    block unrelated app touches outside the intended handle/content bounds.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.accessibility.activity.TrackerOverlayPolicyTest --tests io.github.muntashirakon.AppManager.accessibility.ActionLabelAccessibilityContractTest`.
    Manual Android 11, Android 12+, and current-target walkthroughs enabling
    tracker, dragging/iconifying, interacting with underlying apps, and checking
    logcat for blocked untrusted-touch or WindowManager failures remain
    device-gated.

- [x] P2 - Add Private Space/profile visibility diagnostics
  - Why: the manifest declares `ACCESS_HIDDEN_PROFILES`, and package-management
    users need honest visibility across personal, work, hidden, locked, and
    private profiles. Current profile discovery falls back to `UserManager`
    profile lists and `LauncherApps` launchability without source-visible labels
    or unavailable-state diagnostics for Private Space.
  - Evidence URL or file:line: `app/src/main/AndroidManifest.xml:15`,
    `app/src/main/java/io/github/muntashirakon/AppManager/users/Users.java:89-93`,
    `app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java:345-355`;
    https://source.android.com/docs/security/features/private-space,
    https://developer.android.com/about/versions/15/behavior-changes-all#private-space,
    https://developer.android.com/reference/android/os/UserManager, and
    https://developer.android.com/reference/android/content/pm/LauncherApps.
  - Touches: `Users`, user/profile labels, package-loading summaries, main-list
    user/profile selector copy, privilege/mode diagnostics, and Private Space
    device-verification notes.
  - Shipped 2026-06-06: `ProfileVisibilityDiagnostics` now centralizes
    user/profile metadata mapping for classic flags plus reflected current
    platform `userType`/private-profile signals. `UserInfo` labels now
    distinguish Private Space, work, clone, guest, restricted, generic profile,
    quiet/locked, disabled, and ephemeral states across the existing main-list,
    backup/installer/profile, App Details, and device-info user pickers that
    already call `toLocalizedString()`. Advanced -> Selected users now explains
    when Android 15+ Private Space/hidden profiles may be not visible from the
    current mode/state because the default launcher role or unlocked profile is
    missing, and the empty app-list copy now points users at profile visibility
    instead of implying a complete scan.
  - Acceptance: when platform APIs expose the state, private/hidden/quiet/locked
    profiles are labeled distinctly; inaccessible profiles produce a clear
    "not visible from current mode/state" diagnostic instead of implying the app
    list is complete; normal work-profile behavior and unprivileged fallback are
    preserved.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.users.ProfileVisibilityDiagnosticsTest`.
    Manual Android 15+ Private Space locked/unlocked/hidden walkthrough on a
    device or image that exposes the feature remains device-gated.

- [x] P2 - Add profile membership inverse filters
  - Why: the current app-list profile picker turns a selected Apps profile into
    positive include filters only. Users auditing backup/debloat/profile coverage
    also need "not in this profile" to find omissions, stale profile membership,
    and apps excluded from an automated set.
  - Evidence URL or file:line:
    https://github.com/MuntashirAkon/AppManager/issues/1755,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainListOptions.java:250-258`,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java:617-632`.
  - Touches: `MainListOptions`, `MainViewModel`, profile filter state, Finder/
    filter option model if reused, strings, and focused filter tests.
  - Shipped 2026-06-06: the main app-list profile picker now persists an
    `Exclude selected profile` option beside the selected profile, evaluates
    profile membership separately from the normal filter expression, and then
    applies the remaining main-list filters, install-date filter, selected-user
    filter, and search query as before. Static package-list profiles and
    filter-based profiles share `ProfileMembershipFilter`, which supports
    include and inverse membership without changing profile apply behavior.
    `FilterItem` now exposes single-item match helpers so filter-based profile
    membership can be evaluated without materializing a temporary list. The
    filtered empty-state label now distinguishes `In profile: ...` from
    `Not in profile: ...`.
  - Acceptance: users can filter apps included in a selected profile and apps not
    included in that profile; both static package-list profiles and filter-based
    profiles behave correctly; active-filter labels distinguish include vs
    exclude; the feature does not change existing profile apply behavior.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.main.ProfileMembershipFilterTest --tests io.github.muntashirakon.AppManager.filters.FilterItemTest`.

- [x] P2 - Persist the main-list selected-user filter
  - Why: `MainViewModel` already supports a selected-user filter, but its state
    was process-local (`TODO: Load from prefs?` / `Store value to prefs`), so a
    restart lost the active user subset while other main-list filters persisted.
  - Evidence URL or file:line:
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java:120`,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java:437`,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainListOptions.java:287-320`.
  - Touches: `AppPref`, `Prefs.MainPage`, `MainViewModel`, focused view-model
    preference tests, and roadmap/completed/changelog state.
  - Shipped 2026-06-06: added a dedicated main-window selected-user filter
    preference, hydrated `MainViewModel` from it, persisted filter-sheet
    changes, preserved empty user selections as an active "no selected users"
    filter, and cleared the persisted value when Clear filters resets the main
    list. The Advanced app-wide selected-users preference remains separate and
    is not mutated by main-list filtering.
  - Acceptance: selected-user main-list filters survive activity/process
    recreation; Clear filters removes that persisted main-list filter; empty
    selections remain active instead of reloading as "all users"; app-wide
    selected-users settings remain unchanged by main-list filter changes.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.main.MainViewModelSelectedUsersTest`.

- [x] P2 - Include split APKs in data-only reinstall fallback
  - Why: the main-list data-only fallback used only
    `info.publicSourceDir` when offering to reinstall an uninstalled app from
    APK files, leaving a source FIXME because split APK packages need the base
    and split sources together.
  - Evidence URL or file:line:
    `app/src/main/java/io/github/muntashirakon/AppManager/main/MainRecyclerAdapter.java:765`,
    `app/src/main/java/io/github/muntashirakon/AppManager/apk/ApplicationInfoApkSource.java:34-41`,
    `app/src/main/java/io/github/muntashirakon/AppManager/apk/ApkFile.java:368-381`.
  - Touches: `MainRecyclerAdapter`, installer launch intent routing, and a
    focused adapter reinstall intent test.
  - Shipped 2026-06-06: the data-only reinstall fallback now builds the
    installer launch intent from `ApkSource.getApkSource(ApplicationInfo)`
    instead of `Uri.fromFile(info.publicSourceDir)`. That keeps the installer on
    the existing `ApplicationInfoApkSource` path, where APK resolution can
    include `splitPublicSourceDirs` when the package has splits.
  - Acceptance: a readable base APK still opens the installer; split APK
    packages route through `ApplicationInfoApkSource` rather than a base-only
    file URI; missing base APKs still fall through to backup/not-installed
    handling.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.main.MainRecyclerAdapterReinstallIntentTest`.

- [x] P2 - Fall back to package-directory splits during APKS export
  - Why: `SplitApkExporter` trusted `ApplicationInfo.splitPublicSourceDirs`,
    but the source already carried a FIXME that this path does not work for some
    disabled apps. The adjacent `ApkFile` resolver already falls back to sibling
    APK files in the package source directory when split paths are unavailable.
  - Evidence URL or file:line:
    `app/src/main/java/io/github/muntashirakon/AppManager/apk/splitapk/SplitApkExporter.java:117-120`,
    `app/src/main/java/io/github/muntashirakon/AppManager/apk/ApkFile.java:368-381`.
  - Touches: `SplitApkExporter` and focused split-export source enumeration
    tests.
  - Shipped 2026-06-06: APKS export now adds the base APK, explicit
    `splitPublicSourceDirs`, and sibling `.apk` files from the base APK's
    package directory into one de-duplicated source set. The old `/data/app`
    guard remains so legacy shared app directories are not swept.
  - Acceptance: packages with explicit split paths export the same APK set
    without duplicates; packages whose split paths are absent still include
    sibling split APKs from their package directory; unrelated non-APK files are
    ignored.
  - Verify: passed 2026-06-06:
    `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporterTest`.

### Researcher Queue (Cycle 6 - 2026-06-05)

- [x] 🔬 `installer-mode-recovery-action-gap-refresh-2026-06-05` - rechecked
  current `main`, the active roadmap/completed ledger, AppManager v4.0.5 release
  notes, recent upstream installer/startup/running-apps/assistant issues, and
  local installer, splash, AppOps, Running Apps, rollback, and assistant source
  paths. Four source-backed rows were promoted; File Manager archive, broad code
  editor launchability, pre-install DexOpt, and DPC mode were not duplicated or
  promoted without stronger source/design proof.

### Cycle 6 Promoted Items (2026-06-05)

- [x] P1 - Add a splash mode-initialization watchdog and recovery path
  - Why: upstream reports indefinite "Initializing" splash hangs around wireless
    debugging and ROM permission toggles. NG sets the splash state to
    initializing, then posts auth status only after migration and `Ops.init()`
    return. A blocked privileged-mode init leaves users no retry, mode switch, or
    diagnostic export path.
  - Evidence URL or file:line:
    https://github.com/MuntashirAkon/AppManager/issues/1825,
    https://github.com/MuntashirAkon/AppManager/issues/1829,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/SplashActivity.java:209-218`,
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/SecurityAndOpsViewModel.java:51-75`.
  - Touches: `SplashActivity`, `SecurityAndOpsViewModel`, `Ops` init status
    reporting, Mode Doctor/settings entry points, startup strings, stale-callback
    guards, and startup tests.
  - Shipped 2026-06-06: startup now has immutable attempt state, ViewModel-owned
    attempt ids, stale-status rejection, visible Splash stage text, a 45-second
    current-attempt timeout, and recovery buttons for retry, mode settings, Mode
    Doctor, support bundle sharing, local-network permission, Shizuku
    permission, and wireless-pairing cancellation. Root/local late binder
    callbacks were hardened first so retry churn does not crash the bind wrapper.
  - Acceptance: startup exposes meaningful init stages, times out a stalled
    mode probe, offers retry/safe-mode-switch/diagnostics actions, prevents a
    timed-out callback from overriding a newer attempt, and preserves the normal
    fast path when mode init succeeds.
  - Verify: focused host verification passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
    Manual no-root, ADB-with-wireless-debugging-off, Shizuku-stopped, and
    successful-mode startup walkthroughs remain device-gated.

- [x] P2 - Add Running Apps restore-background-operation action
  - Why: Running Apps can apply background-run restrictions but does not reveal a
    point-of-use inverse once the restriction is active. Operation-history
    rollback can synthesize a default-mode rollback after tracked operations, but
    the same Running Apps surface should let users restore the app they are
    inspecting.
  - Shipped 2026-06-06: Running Apps row popups now expose **Restore background
    operation** only when an app has a restricted background AppOp. The restore
    flow plans `RUN_IN_BACKGROUND` on Android N/O and both `RUN_IN_BACKGROUND`
    plus `RUN_ANY_IN_BACKGROUND` on Android P+, changes only ignored/errored
    current modes, falls back to `MODE_DEFAULT` because the current history
    schema has no structured previous-mode field, removes default-mode blocking
    rules from `ComponentsBlocker`, and records a single Operation History
    app-op row with per-op mode details.
  - Evidence URL or file:line:
    https://github.com/MuntashirAkon/AppManager/issues/1806,
    `app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsViewModel.java:244-287`,
    `app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsAdapter.java:319-330`,
    `app/src/main/java/io/github/muntashirakon/AppManager/batchops/BatchOpsManager.java:748-789`,
    `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/PerAppRollbackManager.java:233-240`.
  - Touches: Running Apps menu XML/adapter, `RunningAppsViewModel`, AppOps
    rule persistence, optional batch inverse operation, operation-history
    integration, strings, and AppOps tests.
  - Acceptance: an app with background-run AppOps ignored/errored shows a
    clearly labeled restore action; restore sets the relevant Android N/P+
    background AppOps back to the tracked previous mode when available or
    `MODE_DEFAULT` otherwise; blocking rules and history stay consistent; the
    existing disable action remains available only when the app can still run in
    background.
  - Verify: tests for background-op detection and restore modes on Android N/P+
    branches, adapter/menu visibility tests for disable vs restore states, and a
    manual Running Apps disable-then-restore walkthrough. Focused host
    verification passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.runningapps.RunningAppsViewModelTest`.

- [x] P2 - Design a guarded ADB assistant trampoline for services and broadcasts
  - Why: upstream accepted a request to extend assistant-based ADB routing beyond
    activity launch into privileged service and broadcast component actions. NG's
    assistant surface currently offers force-stop, freeze/unfreeze, and App Info
    only, while the existing component action lanes are not exposed as quick
    assistant actions.
  - Evidence URL or file:line:
    https://github.com/MuntashirAkon/AppManager/issues/1973,
    `app/src/main/java/io/github/muntashirakon/AppManager/assistant/AssistActionActivity.java:38-40`,
    `app/src/main/java/io/github/muntashirakon/AppManager/assistant/AssistActionActivity.java:108-117`,
    `app/src/main/java/io/github/muntashirakon/AppManager/assistant/AssistActionActivity.java:169-191`.
  - Touches: assistant target/action model, component metadata reuse, root/ADB/
    Shizuku route gating, secure-assistant setting restore logic, confirmations,
    operation history/audit entries, strings, and Android 16 device validation.
  - Shipped 2026-06-06: `AssistComponentActionPlan` defines the
    package-visible action model for this row, and `AssistActionActivity` now
    loads service/receiver metadata for the resolved target, appends safe
    service start/stop and declared-action receiver broadcast candidates to the
    visible quick-assist dialog, confirms component/user/route/permission
    details before execution, dispatches only explicit intents, and records one
    non-replayable single-app Operation History row per attempt.
  - Acceptance: only explicit user-selected service or receiver components can
    be launched; target package/component/type are visible before execution;
    temporary assistant settings are restored on success/failure/cancel; exported
    and permission-gated targets are handled honestly; no generic third-party raw
    intent ingress or bindService support is added.
  - Verify: focused host verification passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistComponentActionPlanTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest --tests io.github.muntashirakon.AppManager.history.ops.OpHistoryItemTest`.
    Manual Android assist invocation and Android 16 ADB-mode service/receiver
    walkthroughs remain device-gated.

### Researcher Queue (Cycle 7 - 2026-06-05)

- [x] ✅ 🔬 `upstream-issue-recheck-after-installer-cycle-2026-06-05` -
  rechecked the current upstream open/closed issue set, the active roadmap,
  completed ledgers, and local source hotspots after the Cycle 6 installer/mode
  queue landed. No new rows were promoted: #1833/#1825/#1829/#1806/#1973 are
  already represented by Cycle 6, #1980/#1975/#1969/#1967/#1964/#1963/#1956/
  #1953/#1948/#1848/#1817/#1810/#1805 are shipped or already represented by
  existing rows, and #1816 still needs a source-backed stale-state proof before
  it should become roadmap work. The local installer-icon source/test work was
  left unstaged for the build lane.

### Researcher Queue (Cycle 8 - 2026-06-06)

- [x] `privilege-startup-assistant-deepening-2026-06-06` - rechecked current
  `main` after the oversized installer icon clamp, the active roadmap and
  autonomous loop state, `SplashActivity`, `SecurityAndOpsViewModel`,
  `RunningAppsViewModel`, `RunningAppsAdapter`, and `AssistActionActivity`.
  External research focused on Android splash/startup guidance, Android
  service/foreground-service launch restrictions, Shizuku setup failure modes,
  and App Ops background-operation handling. No duplicate top-level feature was
  promoted; the Cycle 6 rows below were refined with implementation constraints,
  test targets, and source-backed acceptance details.

### Cycle 8 Refinements to Existing Open Items (2026-06-06)

- [x] P1 refinement - Startup watchdog should be an observable init-attempt
  state machine, not a longer hidden splash hold.
  - Applies to: **P1 - Add a splash mode-initialization watchdog and recovery
    path**.
  - Why: `SplashActivity.handleMigrationAndModeOfOp()` currently changes the
    visible text to `initializing` and calls `SecurityAndOpsViewModel.setModeOfOps()`;
    the ViewModel then runs migration and `Ops.init()` on an executor before
    posting a single terminal auth status. There is no attempt id, stage
    stream, timeout state, or stale-callback suppression, so a hung mode probe
    can look identical to normal startup latency.
  - Evidence URL or file:line:
    https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen#setKeepOnScreenCondition(androidx.core.splashscreen.SplashScreen.KeepOnScreenCondition),
    https://developer.android.com/develop/ui/views/launch/splash-screen,
    https://shizuku.rikka.app/guide/setup/,
    `app/src/main/java/io/github/muntashirakon/AppManager/main/SplashActivity.java:199-218`,
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/SecurityAndOpsViewModel.java:51-75`.
  - Implementation constraint: do not solve this by keeping the system splash
    screen indefinitely. AndroidX documents that `setKeepOnScreenCondition()` is
    evaluated before each draw and must stay fast. Show the existing
    authentication layout, expose progress stages there, and move the recovery
    decision into ViewModel-owned attempt state.
  - Acceptance additions: each startup attempt has a monotonically increasing
    token; migration, `Ops.init`, wireless auto-connect, ADB connect, ADB pair,
    Shizuku permission, and local-network-permission stages are individually
    observable; timeout actions include retry, mode selection, Mode Doctor, and
    support bundle; stale completions from a timed-out attempt cannot mark the
    app authenticated or start `MainActivity`.
  - Shipped 2026-06-06: `StartupInitState`, `SecurityAndOpsViewModel`, and
    `SplashActivity` now implement this observable attempt model and render the
    recovery actions in the authentication content instead of extending the
    AndroidX system splash hold.
  - Verify additions: JVM tests for timeout transition, retry token rollover,
    stale callback rejection, terminal-status passthrough, and stage label
    mapping; manual walkthroughs remain no-root, Shizuku-stopped, ADB
    wireless-off, local-network-permission-denied, and success paths.

- [x] P2 refinement - Running Apps restore should treat background AppOps as
  per-op state, even when the UI presents one "background run" concept.
  - Applies to: **P2 - Add Running Apps restore-background-operation action**.
  - Why: `preventBackgroundRun()` writes every background-run op returned by
    `getBackgroundRunAppOpsForSdk()` to `MODE_IGNORED`, and the adapter hides
    the disable action once `canRunInBackground()` is false. The inverse action
    must inspect both current ops and any tracked previous modes instead of
    assuming a single toggle state.
  - Evidence URL or file:line:
    https://appops.rikka.app/guide/technical/run_in_background/,
    `app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsViewModel.java:244-287`,
    `app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsAdapter.java:319-330`.
  - Implementation constraint: expose one plain-language restore action in the
    popup, but drive it from per-op current/previous state. Prefer restoring the
    operation-history previous mode where available; fall back to `MODE_DEFAULT`
    per op rather than hard-coded `MODE_ALLOWED`, so OEM/default behavior is not
    silently changed.
  - Acceptance additions: Android N devices restore only `RUN_IN_BACKGROUND`;
    Android P+ devices restore both ops independently; mixed states are shown
    honestly; ComponentsBlocker rule persistence is updated or removed to match
    the restored modes; operation history gets a non-replayable row that explains
    which op modes changed.
  - Shipped 2026-06-06: `BackgroundRunAppOpPlan` centralizes SDK-specific op
    selection, mixed-mode detection, and restore-mode fallback; the ViewModel
    uses it for both disable/restore visibility and restore execution. The
    planner accepts a future structured previous-mode source, but the live
    restore path correctly uses `MODE_DEFAULT` until such a source exists.
  - Verify additions: tests for N/P+ op selection, ignored/errored/default/allow
    mixed states, previous-mode fallback, adapter visibility for disable vs
    restore, and ComponentsBlocker persistence after restore.

- [x] P2 refinement - Assistant service/broadcast trampoline must reuse the
  App Details component action guardrails and stay user-visible.
  - Applies to: **P2 - Design a guarded ADB assistant trampoline for services
    and broadcasts**.
  - Why: `AssistActionActivity` currently offers only force-stop,
    freeze/unfreeze, and App Info. App Details already has explicit service and
    receiver action surfaces with target/component summaries; the assistant
    path should reuse those constraints rather than accepting arbitrary raw
    intents or hidden generic actions.
  - Evidence URL or file:line:
    https://developer.android.com/develop/background-work/services,
    https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start,
    `app/src/main/java/io/github/muntashirakon/AppManager/assistant/AssistActionActivity.java:38-40`,
    `app/src/main/java/io/github/muntashirakon/AppManager/assistant/AssistActionActivity.java:108-117`,
    `app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsComponentsFragment.java:936-1000`,
    `app/src/main/java/io/github/muntashirakon/AppManager/details/components/BroadcastSendDialogFragment.java:88-224`.
  - Implementation constraint: assistant-triggered service/broadcast actions
    must originate from a visible assistant dialog for the resolved foreground
    package, require explicit component choice, show exported/permission/user
    details before execution, and preserve the existing secure-assistant setting
    restore behavior. Do not add bindService support, raw third-party intent
    ingress, or a background-only execution path.
  - Acceptance additions: service start/stop and receiver broadcast candidates
    are filtered to components visible for the target package/user; Android O+
    service-start restrictions and Android 12+ foreground-service launch
    failures are surfaced as user-readable results; every attempt writes a
    single non-replayable audit row with package, component, component type,
    route, result, and failure text.
  - Shipped 2026-06-06: quick assist now reuses the planner and App Details
    service/receiver dispatch helpers, keeps the action chooser visible, shows
    route and permission details before dispatch, blocks raw/custom receiver
    action entry, and records non-replayable component-action audit metadata.
    The existing force-stop, freeze/unfreeze, and App Info assistant actions
    were not changed.
  - Verify additions: focused host verification passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistComponentActionPlanTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest --tests io.github.muntashirakon.AppManager.history.ops.OpHistoryItemTest`.
    Manual Android assist invocation remains device-gated.

### Researcher Queue (Cycle 9 - 2026-06-06)

- [x] `startup-watchdog-implementation-blueprint-2026-06-06` - inspected the
  rest of `Ops.init()`, Shizuku and root service bind wrappers, LocalServer ADB
  startup waits, Mode Doctor, support-bundle sharing, startup strings, and
  focused test coverage. External recheck added Android local-network
  permission guidance: target-SDK-37 local network denial can manifest as TCP
  timeout, so startup recovery needs to distinguish "waiting" from
  permission-blocked network access.

### Cycle 9 Promoted / Refined Items (2026-06-06)

- [x] P1 - Add a pure startup init-state reducer before wiring the splash
  watchdog UI.
  - Applies to: **P1 - Add a splash mode-initialization watchdog and recovery
    path**.
  - Why: the startup flow has multiple blocking or long-wait branches:
    `Ops.init()` routes through root, Shizuku, ADB-over-TCP, wireless ADB, or
    no-root; Shizuku service binding waits up to 45 seconds; root service
    binding waits up to 45 seconds; ADB server startup waits up to one minute;
    wireless pairing can wait in a one-hour observer loop. A UI watchdog bolted
    directly onto these calls would be hard to test and easy to race.
  - Evidence URL or file:line:
    https://developer.android.com/privacy-and-security/local-network-permission,
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:318-381`,
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:569-742`,
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:906-1006`,
    `app/src/main/java/io/github/muntashirakon/AppManager/ipc/ShizukuServiceConnectionWrapper.java:134-145`,
    `app/src/main/java/io/github/muntashirakon/AppManager/ipc/ServiceConnectionWrapper.java:116-128`,
    `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java:216-239`.
  - Proposed shape: add a small JVM-testable model such as `StartupInitState`
    / `StartupInitStage` / `StartupInitEvent` with fields for attempt id,
    configured mode, current stage, status, start/deadline timestamps, recovery
    actions, and optional diagnostic detail. `SecurityAndOpsViewModel` should
    translate the existing `Ops` callbacks into this model and keep the legacy
    integer `authenticationStatus()` only as the terminal compatibility signal.
  - Stage coverage: migration, auto-detect, root service bind, Shizuku
    capability/permission, Shizuku service bind, wireless ADB port discovery,
    ADB server restart, ADB service bind, ADB pairing wait, local-network
    permission required, incomplete USB debugging, no-root fallback, success,
    failure, timeout, and cancelled.
  - Acceptance: the reducer is testable without Android services; retry creates
    a new attempt id; late events from old attempts are ignored; a timeout never
    sets `Ops.setAuthenticated(..., true)`; long pairing waits are visible and
    cancellable instead of silently blocking the startup surface; local-network
    permission denial/timeout points users to the existing permission dialog
    and Mode Doctor/support bundle.
  - Verify: `StartupInitStateTest` for reducer transitions, stale-event
    rejection, retry token rollover, timeout action selection, local-network
    permission state, and pairing cancel state before Activity wiring. Shipped
    2026-06-06 as part of the startup watchdog host-state foundation.

- [x] P1 - Harden root/local service late binder callbacks before startup
  retry loops depend on them.
  - Why: `ShizukuServiceConnectionWrapper` already treats late or duplicate
    binder callbacks outside an active bind window as benign, but the root/local
    `ServiceConnectionWrapper` still throws `RuntimeException("Service watcher
    should never be null!")` when `onServiceDisconnected`, `onBindingDied`, or
    a delayed callback arrives with no watcher. A startup retry/watchdog loop
    will increase bind/unbind churn, so this crash class should be removed first.
  - Shipped 2026-06-06: `ServiceConnectionWrapper` now mirrors the hardened
    Shizuku callback contract for root/local services: binder and watcher fields
    are `volatile`, callbacks read the watcher once into a local, active bind
    waits still count down, and callbacks outside an active wait no longer throw
    from the binder thread. `ServiceConnectionWrapperTest` adds the first
    focused IPC wrapper JVM coverage and exercises both no-watcher late
    callbacks and with-watcher connected callbacks through the private
    `ServiceConnection`.
  - Evidence URL or file:line:
    `app/src/main/java/io/github/muntashirakon/AppManager/ipc/ServiceConnectionWrapper.java:30-74`,
    `app/src/test/java/io/github/muntashirakon/AppManager/ipc/ServiceConnectionWrapperTest.java:26-47`,
    `app/src/main/java/io/github/muntashirakon/AppManager/ipc/ShizukuServiceConnectionWrapper.java:64-79`.
  - Touched: `ServiceConnectionWrapper` and `ServiceConnectionWrapperTest`;
    startup watchdog failure handling can now depend on non-crashing root/local
    late callbacks.
  - Acceptance: delayed disconnect/binding-died/null-binding callbacks outside
    an active bind window do not crash the process; active bind still counts
    down the watcher; `mIBinder` is cleared on disconnect/death/null binding;
    existing root service bind behavior is preserved.
  - Verify: run `:app:testFullDebugUnitTest --tests
    io.github.muntashirakon.AppManager.ipc.ServiceConnectionWrapperTest` on a
    host with Android SDK configured. Attempted locally 2026-06-06: direct UNC
    Gradle launch failed because `cmd.exe` cannot use a UNC current directory;
    `cmd pushd` reached the Gradle project but failed before compilation because
    `local.properties` points at missing `C:\Users\Xray\.cache\android-sdk` and
    no active-user `ANDROID_HOME` / `%LOCALAPPDATA%\Android\Sdk` exists.

### Researcher Queue (Cycle 10 - 2026-06-06)

- [x] `ipc-root-service-late-callback-hardening-2026-06-06` - inspected the
  existing test tree for IPC/root-service coverage, confirmed there were no
  existing `app/src/test/java/**/ipc/**` tests, then implemented the P1
  root/local `ServiceConnectionWrapper` hardening before the startup watchdog
  retry loop starts increasing bind/unbind churn. The production change is
  intentionally narrow and follows the already-hardened Shizuku wrapper pattern.
  A new Robolectric/JUnit test file exercises stale callbacks without an active
  watcher and active connected callbacks with a latch/binder. Verification is
  implementation-ready but not locally completed because this machine has Java
  only and no usable Android SDK path.

### Cycle 10 Next Implementation Slice (2026-06-06)

- [x] P1 - Implement the pure `StartupInitState` reducer as the next host-side
  startup watchdog step.
  - Applies to: **P1 - Add a splash mode-initialization watchdog and recovery
    path** and the Cycle 9 `StartupInitState` blueprint above.
  - Why: the root/local callback crash class is now removed, so the next safe
    host-verifiable unit of work is the reducer that will let
    `SecurityAndOpsViewModel` reject stale terminal events, expose timeout
    recovery actions, and keep the splash/authentication UI honest before any
    Activity wiring touches the long-running `Ops.init()` branches.
  - Shipped 2026-06-06: added package-private
    `settings/StartupInitState.java` beside `SecurityAndOpsViewModel`, with
    nested `Status`, `Stage`, and `RecoveryAction` enums plus immutable reducer
    methods for attempt start, stage transition, `Ops` status receipt, timeout,
    cancel, retry, stale-event rejection, and terminal detection. Added
    `StartupInitStateTest` coverage for migration start, stale stage rejection,
    timeout recovery actions, retry token rollover with stale failure ignored,
    local-network-permission recovery, Shizuku-permission recovery, pairing
    cancel, and success terminal/no-recovery behavior. No `SplashActivity` or
    `Ops.init()` runtime behavior was changed in this slice.
  - Actual files:
    `app/src/main/java/io/github/muntashirakon/AppManager/settings/StartupInitState.java`,
    `app/src/test/java/io/github/muntashirakon/AppManager/settings/StartupInitStateTest.java`.
  - Required reducer semantics: `startAttempt(mode, now, deadline)`, `stage()`,
    `timeout()`, `cancel()`, `retry()`, and terminal `success/failure/status`
    events all carry an attempt id; events for old attempt ids return the
    current state unchanged; timeout/cancel never authenticate; retry creates a
    new attempt id and clears stale recovery detail; local-network-permission
    and pairing-wait states expose distinct recovery actions.
  - Acceptance: reducer tests cover happy path, timeout, cancel, retry token
    rollover, stale success ignored after timeout, stale failure ignored after
    retry, local-network-permission recovery action, Shizuku-permission
    recovery action, and pairing cancel state. No `SplashActivity` or
    `Ops.init()` behavior should be changed until the reducer tests pass.
  - Verify: `StartupInitStateTest` plus the existing
    `ServiceConnectionWrapperTest` once an Android SDK is available. Local
    verification remains blocked by the missing SDK path described in Cycle 10;
    static checks found no trailing whitespace in the new files and no remaining
    root/local `ServiceConnectionWrapper` watcher throw string in source.

### Researcher Queue (Cycle 11 - 2026-06-06)

- [x] `startup-init-state-reducer-implementation-2026-06-06` - continued from
  Cycle 10 and implemented the pure startup reducer before touching Activity or
  ViewModel wiring. The reducer is intentionally isolated under `settings/`
  because its first consumer is `SecurityAndOpsViewModel` and its status mapping
  is driven by `Ops.STATUS_*`. The new tests define the behavior that the future
  watchdog UI must preserve: attempt ids are authoritative, stale completions do
  nothing, timeout/cancel are terminal and non-authenticating, retry rolls to a
  fresh attempt id, and permission/pairing states carry specific recovery
  actions.

### Cycle 11 Next Implementation Slice (2026-06-06)

- [x] P1 - Wire `StartupInitState` into `SecurityAndOpsViewModel` before adding
  visible watchdog controls.
  - Applies to: **P1 - Add a splash mode-initialization watchdog and recovery
    path**.
  - Why: the reducer now exists but is not yet observed by the UI. The next
    narrow slice should add `LiveData<StartupInitState>` and attempt-id
    ownership in `SecurityAndOpsViewModel`, then route the existing
    `setModeOfOps()`, `autoConnectWirelessDebugging()`, `connectAdb()`,
    `pairAdb()`, and `onStatusReceived()` status posts through the reducer
    while keeping the legacy integer `authenticationStatus()` observer intact.
  - Shipped 2026-06-06: `SecurityAndOpsViewModel` now owns a
    `LiveData<StartupInitState>` stream, a synchronized startup attempt
    snapshot, and monotonic attempt ids. `setModeOfOps()`,
    `autoConnectWirelessDebugging()`, `connectAdb()`, `pairAdb()`, and
    `onStatusReceived()` now post reducer stages/statuses while preserving the
    legacy integer `authenticationStatus()` signal for the current attempt.
    Stale statuses from timed-out or retried attempts are ignored before they
    can post the legacy terminal status. `SecurityAndOpsViewModelTest` covers
    current-attempt passthrough, stale-status rejection, timeout-then-retry old
    success rejection, and observable stage publication.
  - Implementation constraints: do not add timeout UI or change the
    authentication success path yet; first expose state, post migration and
    `Ops.init` stages, map every `Ops.STATUS_*` through `statusReceived()`, and
    ensure stale statuses from old attempts cannot post a legacy terminal status
    after timeout/retry once timeout is wired.
  - Acceptance: ViewModel tests or a small package-visible coordinator prove
    new attempts get new ids, `LiveData<StartupInitState>` starts at migration,
    `Ops` statuses update reducer state and still post the same legacy integer
    values for current attempts, old-attempt statuses are ignored, and no call
    to `Ops.setAuthenticated()` moves out of `SplashActivity` in this slice.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.ipc.ServiceConnectionWrapperTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
    Manual UI watchdog work remains parked until visible controls are added.

### Researcher Queue (Cycle 12 - 2026-06-06)

- [x] `startup-init-viewmodel-wiring-2026-06-06` - verified the Cycle 11
  reducer slice against `SecurityAndOpsViewModel`, `SplashActivity`,
  `Ops.STATUS_*`, and the Android SDK/JDK environment. External recheck kept the
  implementation constrained to state exposure rather than splash-screen
  retention: AndroidX splash retention callbacks must stay lightweight, Android
  17 local-network permission can block ADB/wireless flows by default for target
  SDK 37+, Shizuku remains rooted in user-visible wireless/USB setup flows, and
  Android service-start restrictions still support the follow-up assistant
  guardrail direction. The current cycle completed the host-verifiable startup
  state foundation; visible watchdog controls remain a later UI slice.

### Cycle 12 Next Implementation Slice (2026-06-06)

- [x] P2 - Implement Running Apps restore-background-operation action.
  - Applies to: **P2 - Add Running Apps restore-background-operation action**
    and the Cycle 8 refinement above.
  - Why: `RunningAppsViewModel.preventBackgroundRun()` already persists
    `RUN_IN_BACKGROUND` / `RUN_ANY_IN_BACKGROUND` blocks, but the same surface
    has no inverse action. Users can create a background-run restriction from
    Running Apps and then must leave the flow to undo it.
  - Next constraints: inspect current ComponentsBlocker persistence and
    operation-history metadata before editing; expose one restore action in the
    row popup, but compute per-op modes independently. Prefer previous-mode
    history where available and fall back to `MODE_DEFAULT`, not hard-coded
    allowed.
  - Acceptance: Android N restores only `RUN_IN_BACKGROUND`; Android P+ restores
    both background ops independently; mixed current states are labelled
    honestly; ComponentsBlocker persistence matches the restored modes; the
    Operation History row records each changed op/mode.
  - Shipped 2026-06-06: added the `BackgroundRunAppOpPlan` planner, a Running
    Apps popup restore action, restore-result observer/toast handling,
    default-mode rule deletion through `RulesStorageManager.deleteAppOp()`, and
    focused planner/formatter tests. The live path falls back to `MODE_DEFAULT`
    because current AppOps history stores display detail rather than structured
    previous modes.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.runningapps.RunningAppsViewModelTest`.
    Manual Running Apps disable-then-restore walkthrough remains device-gated.

### Researcher Queue (Cycle 13 - 2026-06-06)

- [x] `running-apps-background-restore-2026-06-06` - implemented the
  host-verifiable restore-background slice after rechecking Android AppOps
  default/ignored semantics and the per-op `RUN_IN_BACKGROUND` /
  `RUN_ANY_IN_BACKGROUND` split. The shipped path keeps one user-facing action
  but plans and records each AppOp independently.

### Cycle 13 Next Implementation Slice (2026-06-06)

- [x] P2 - Implement the guarded assistant service/broadcast action model.
  - Applies to: **P2 - Design a guarded ADB assistant trampoline for services
    and broadcasts** and the Cycle 8 assistant guardrail refinement.
  - Why: the research row is already scoped: assistant-triggered service and
    broadcast actions must reuse App Details component constraints, remain
    visible and explicit, and avoid raw third-party intent ingress.
  - Next constraints: inspect `AssistActionActivity`, component details models,
    service start/stop and broadcast-send helpers, secure-assistant setting
    restore logic, and Operation History single-app action metadata before
    editing. Start with a package-visible planner/model and tests before adding
    UI wiring.
  - Acceptance: action availability is derived from target package components
    and privilege route; service/receiver candidates are explicit and
    exported/permission/user-aware; Android service-start failures surface as
    user-readable results; every attempt records one non-replayable audit row.
  - Shipped 2026-06-06: added `AssistComponentActionPlan` plus focused tests for
    unprivileged exported services, privileged-only services, running service
    start/stop actions, permission-protected services, disabled/blocked
    component suppression, declared-action receiver broadcasts, protected
    Android broadcast routing, and explicit intent construction.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistComponentActionPlanTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest`.
    Visible dialog wiring, dispatch, secure-assistant restoration, and audit
    metadata remain the next slice.

### Researcher Queue (Cycle 14 - 2026-06-06)

- [x] `assistant-component-action-model-2026-06-06` - implemented the
  host-verifiable planner foundation for assistant service/broadcast actions.
  The model intentionally accepts already-resolved component metadata and route
  capability booleans so it can stay deterministic under JVM tests and reuse
  App Details dispatch rules without opening a new raw intent surface.

### Cycle 14 Next Implementation Slice (2026-06-06)

- [x] P2 - Wire assistant component candidates into the visible quick-assist
  dialog.
  - Applies to: **P2 - Design a guarded ADB assistant trampoline for services
    and broadcasts**.
  - Why: the planner now provides safe service/receiver candidates, but
    `AssistActionActivity` still presents only force-stop, freeze/unfreeze, and
    App Info.
  - Next constraints: keep all actions visible and user-selected; reuse the
    planner and App Details dispatch utilities; do not add bindService, custom
    receiver action entry, raw external intent ingress, or background-only
    execution. Preserve the existing assistant setting restore behavior for
    activity-launch flows.
  - Acceptance: quick assist can show service start/stop and receiver
    send-broadcast entries for the resolved foreground app when candidates are
    available; privileged-only entries require a privileged route; confirmations
    include component, action, user, route, and permission details; each attempt
    records one non-replayable single-app Operation History row.
  - Shipped 2026-06-06: `AssistActionActivity` now loads target package
    services/receivers, parses receiver declared actions/categories, filters
    disabled or blocked components, appends component actions to the quick
    assist dialog, confirms details before dispatch, routes privileged and
    unprivileged service/broadcast execution, and records component-action
    Operation History rows. `AssistComponentActionPlanTest` now pins permission
    and category payloads; `OpHistoryItemTest` pins non-replayable audit
    metadata.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistComponentActionPlanTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest --tests io.github.muntashirakon.AppManager.history.ops.OpHistoryItemTest`.
    Manual Android assist invocation remains device-gated.

### Researcher Queue (Cycle 15 - 2026-06-06)

- [x] `assistant-visible-component-actions-2026-06-06` - implemented the
  visible quick-assist wiring for service start/stop and receiver broadcasts
  after the planner model landed. The follow-up queue returns to the startup
  watchdog work because its ViewModel state exists but the user-facing
  Splash/authentication controls still need to consume it.

### Cycle 15 Next Implementation Slice (2026-06-06)

- [x] P1 - Wire startup init state into visible Splash recovery controls.
  - Applies to: **P1 - Add a splash mode-initialization watchdog and recovery
    path** and the Cycle 8 startup-state refinement.
  - Why: `StartupInitState` and `SecurityAndOpsViewModel` now expose attempt,
    stage, timeout, retry, cancel, and recovery-action state, but
    `SplashActivity` still shows only the generic initializing text before a
    terminal status.
  - Next constraints: keep the AndroidX splash-screen keep condition fast, show
    recovery in the existing authentication content rather than holding a blank
    splash, preserve the normal success path, and route recovery buttons to the
    existing mode picker, Mode Doctor, support bundle, local-network permission,
    Shizuku permission, retry, and pairing-cancel paths where available.
  - Acceptance: startup displays stage-specific text, timeout/retry state, and
    recovery actions for stalled root/Shizuku/ADB/wireless-pairing attempts;
    stale callbacks from older attempts cannot launch `MainActivity`; successful
    startup remains visually unchanged apart from richer progress text.
  - Shipped 2026-06-06: `SplashActivity` now observes
    `startupInitState()`, maps it through `StartupInitUiState`, updates the
    authentication text with the current init stage, schedules the current
    attempt timeout, hides progress on terminal timeout/cancel/failure states,
    and renders only the recovery buttons exposed by the current attempt.
    Mode settings and Mode Doctor deep links authenticate the already-unlocked
    user before leaving Splash; support bundle sharing, Shizuku/local-network
    permission prompts, retry, and wireless-pairing cancellation reuse existing
    app flows.
  - Verify: passed 2026-06-06:
    `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
    Manual no-root, Shizuku-stopped, ADB-wireless-off, local-network
    permission, and successful startup walkthroughs remain device-gated.

*Research conducted 2026-06-03. Items below are new - not duplicates of Existing
Planned Work.*

This section was re-verified against the current code on 2026-06-03 (deep pass).
The earlier surfaced candidate list was a paper merge of `RESEARCH_REPORT.md`
without code checks; several of its rows had **already shipped** and were
removed (recorded in [`COMPLETED.md`](COMPLETED.md) under *Closed during the
2026-06-03 deep-research pass*). The rows below are split into (1) **code-verified
net-new** items found by reading the actual implementation, and (2) the
**still-valid** research candidates that survive a code check. Items already
rejected on license/privacy/scope grounds remain in `COMPLETED.md` under
*Stale / Obsolete Items*.

> **Removed as already-shipped (verified 2026-06-03):** BouncyCastle 1.84 bump
> (`versions.gradle` line 26 already `1.84`); AGP 9.x bump (`agp_version = '9.2.0'`);
> `floss`/`full` distribution flavors (`app/build.gradle` `productFlavors`);
> CI dependency CVE scan (`.github/workflows/dependency-scan.yml`);
> `docs/sideload-verification.md`; HKDF-from-master backup key derivation
> (`crypto/AESCrypto` already uses `HmacSHA256` HKDF + `deriveArchiveKey`);
> the Android 17 static-final-reflection / MessageQueue / keystore-key-cap /
> implicit-URI-grant audits (`docs/audits/2026-05-*`); the privileged-shell
> journal + Shizuku `OnBinderDeadListener` replay (`BatchOpsJournal` +
> `BatchOpsService` death-watch + `MainActivity.maybeShowInterruptedBatchOpRecovery`);
> and the structured operation log itself (`op_history` + `OperationJournalMetadata`
> with `mode`/`exit_code`/`reversible`, a viewer, CSV export, and retention) — the
> "Privileged Op Audit Log (O-02)" was largely built; what remains is the
> coverage gap captured in the P1 item below.

### Quick Wins (P2/P3, doable <1hr)

- [x] P2 — Fail the weekly OWASP CVE audit on CRITICAL findings
  - Why: `dependency-scan.yml` runs `./gradlew dependencyCheckAggregate` with `continue-on-error: true`, so a CRITICAL CVE disclosed against an already-pinned dep (the exact case the weekly job exists to catch) is uploaded as an artifact but **never fails the run** — nobody is paged. The PR-side `dependency-review-action` only fires on dependency *changes*, not on new CVEs against unchanged pins.
  - Shipped 2026-06-03: root `dependencyCheck.failBuildOnCVSS` is property-driven (`-PdependencyCheckFailBuildOnCvss`, default `11.0` for report-only local runs), and the weekly workflow now invokes OWASP with `-PdependencyCheckFailBuildOnCvss=9.0` without `continue-on-error`.
  - Evidence: `.github/workflows/dependency-scan.yml` still uploads HTML/SARIF with `if: always()`, so a failed scheduled audit keeps the reports available for triage.
  - Acceptance: a seeded CRITICAL CVE turns the weekly job red; HTML/SARIF artifacts still upload.
  - Verify: Gradle config parses with `-PdependencyCheckFailBuildOnCvss=9.0`; dry-run the workflow with a known-vulnerable test dep before relying on the first real alert.
  - Complexity: S
- [x] P3 — Schedule OpHistory retention prune as a periodic job (not only on screen open)
  - Why: `OpHistoryManager.pruneHistoryOlderThan(days)` is the only prune path and it is called **only** from `OpHistoryActivity` (manual + on-open). A user who never opens Operation History — and whose retention pref is 0/"keep forever" (the prune early-returns on `days <= 0`) — accumulates an unbounded `op_history` table that ships inside every backup/snapshot bundle.
  - Shipped 2026-06-03: `OpHistoryPruneScheduler` owns a unique daily WorkManager job and `OpHistoryPruneWorker` calls `pruneHistoryOlderThan(Prefs.Privacy.getOpHistoryRetentionDays())`; App startup, boot/package-replace, and Privacy retention changes reconcile schedule/cancel state.
  - Evidence: `0` retention now reads "Keep forever (no scheduled cleanup)" and the worker cancels/no-ops if the live preference is disabled.
  - Acceptance: with a finite retention, old rows are pruned without opening the screen; "keep forever" is explicitly labeled.
  - Verify: `OpHistoryPruneSchedulerTest`; compile `OpHistoryPruneWorker`/Privacy settings path.
  - Complexity: S

### Larger Bets (P1/P2 needing design / staged rollout)

- [x] P0 — Batch-op retry must resume from the failed target, not re-run the whole batch
  - Why: data-safety. The interrupted-batch recovery dialog re-dispatches the **entire original `BatchQueueItem`**, and `BatchQueueItem` carries only the full package/user list with **no completion cursor**. After a shell death partway through a destructive batch (e.g. clear-data or uninstall on 3 of 10 apps), tapping "Retry" re-applies the operation to the already-completed targets too. `BatchOpsManager.performOp` already computes a `failedPackages`/`failedPkgList` result per op, but that progress is never persisted to the journal, so it cannot drive a resume.
  - Evidence: `main/MainActivity.java:404-408` (retry re-sends the original `queueItem`); `batchops/BatchQueueItem.java` (no per-target done/failed state — `mPackages`/`mUsers` only); `batchops/BatchOpsManager.java` builds `new Result(failedPackages)` at every op branch, but `BatchOpsService.onHandleIntent` records only `markInterrupted`, not the partial result set.
  - Shipped 2026-06-03: `BatchOpsManager` now reports package-user target outcomes to `BatchOpsService`, and `BatchOpsJournal` persists completed/failed target arrays while the batch is still running. `MainActivity` recovery builds `entry.getRetryQueueItem()` so Retry starts only unfinished targets and shows completed/remaining progress. Interruptions with no recorded target progress keep the original retry queue because the service cannot prove completion.
  - Touches: persist per-target outcome into `BatchOpsJournal` (extend the entry with completed/failed package sets, written at interrupt from the `BatchOpsManager.Result`); on "Retry", construct a reduced `BatchQueueItem` containing only the not-yet-completed targets; surface "N of M already done — retry the remaining K?" in the recovery dialog.
  - Acceptance: a batch interrupted after k/N recorded targets retries only the remaining N-k targets; already-completed destructive ops are not re-applied when progress is known; idempotent ops are unaffected.
  - Verify: `BatchOpsJournalTest` forces both a partial-result interruption and a no-result interruption after target-progress recording, then asserts the retry queue equals the unfinished set; it also pins interruptions with no recorded progress to the original queue.
  - Complexity: M
- [x] P1 — Extend the operation audit log to single-app App Details privileged actions
  - Why: NG already has a structured operation log (`op_history` + `OperationJournalMetadata` carrying `mode`/`exit_code`/`targets`/`failures`/`replayable`/`reversible`, a viewer, CSV export, and retention) — but it only records ops that flow through a *queue/service*: `HISTORY_TYPE_BATCH_OPS`, `HISTORY_TYPE_INSTALLER`, `HISTORY_TYPE_PROFILE`, `HISTORY_TYPE_CLEANUP`. A single freeze/unfreeze, a single permission grant, a single AppOp toggle, or a single component block performed **directly from App Details** (not via batch) writes **no audit row**. This is the substance the surfaced "Privileged Op Audit Log (O-02)" candidate asked for — except the table already exists, so the work is closing coverage holes, not building it from scratch.
  - Evidence: `history/ops/OpHistoryManager.java:40-43` (only four `HISTORY_TYPE_*` constants); `addHistoryItem` callers are `BatchOpsService`, `PackageInstallerService`, `ProfileApplierService`, `OneClickOpsViewModel` only — no App-Details single-action call site. `OperationJournalMetadata` already models `exit_code`/`mode`/`reversible`, so the schema is ready.
  - Shipped 2026-06-03: added `HISTORY_TYPE_SINGLE_APP_ACTION` plus `SingleAppActionHistoryItem` and a `forSingleAppAction` metadata builder. App Details now records direct freeze/unfreeze, permission grant/revoke, AppOp mode changes, and component-rule changes with one non-replayable row carrying package/user, target preview, exit code, failure count, risk, and reversibility. Operation History exposes the new "App details" filter/type, and `PerAppRollbackManager` now includes these rows in per-package reverse-audit plans with runnable inverses for freeze/unfreeze and permission grant/revoke while leaving AppOp/component rows for manual review.
  - Touches: `HISTORY_TYPE_SINGLE_APP_ACTION`, `SingleAppActionHistoryItem`, `OperationJournalMetadata.forSingleAppAction`, App Details single-action chokepoints (`AppInfoFragment` freeze/unfreeze and `AppDetailsViewModel` permission/AppOp/component paths), Operation History filtering/details/export, and per-package rollback planning.
  - Acceptance: a single freeze, a single permission grant, a single AppOp change, and a single component block each write one queryable audit row in Operation History; the per-package "was it me?" reverse audit (research O-12) becomes complete because single-app actions are no longer invisible to it.
  - Verify: perform each single action under a privileged mode; assert exactly one `op_history` row with correct `op`/`target`/`exit_code`/`reversible`; assert no double-record when the same action is also run via batch.
  - Complexity: M
- [x] P2 — Mode Self-Test "Doctor" active-probe screen (research O-03)
  - Why: catches "works on Magisk, fails on KernelSU 1.0.4" before users hit it; distinct from the display-only T5 Health-Check (no active-probe screen exists in source).
  - Evidence: no `Doctor`/active-probe screen under `settings/`/`adb/`/`ipc/`; mode detection is display-only.
  - Shipped 2026-06-03: Settings -> Privileges -> Mode doctor now opens a dedicated active-probe screen instead of a report-only dialog. The screen reruns the root, root-manager/Sui, Shizuku, Dhizuku, ADB, Restricted Settings, LocalServer, SELinux, and ABI probes, renders one status/details/fix row per probe, keeps copy and support-bundle share actions, and attaches fix-it targets to actionable rows (mode picker, root manager, Shizuku manager/archive, Dhizuku, Developer options, App info restricted-settings unlock, LocalServer bootstrap smoke test, or support bundle).
  - Touches: ordered probes (root binary, su grant, Shizuku binder ping, Sui, ADB pairing, ABI/SELinux, KSU API) with pass/fail/cause + fix-it deep-links.
  - Acceptance: each probe reports status + cause; failures deep-link to the relevant settings.
  - Verify: `PrivilegeModeDoctorTest` pins status/fix report text and structured fix-target retention; compile/test graph covers the dynamic settings fragment and resource wiring. Device verification with a deliberately-broken mode remains recommended for the actual external settings targets.
  - Complexity: M
- [x] P2 — Support Info Bundle + opt-in local crash sink (research O-01, O-05, O-11, O-12)
  - Why: matches the dominant privacy-respecting peer pattern (user-initiated support bundle, no remote telemetry); the O-12 reverse audit depends on the P1 audit-coverage item above.
  - Shipped 2026-06-04: the existing `SupportInfoBundle` composer writes `support-info-<device>-<ts>.txt` bundles and now includes an opt-in local-crash-sink section with recent scrubbed JSON crash summaries. `AMExceptionHandler` no longer persists crash reports by default; private on-disk crash JSON is written only when Settings -> Privacy -> Local crash sink is enabled, and the notification share remains user-initiated. `LocalCrashSink` writes bounded local JSON files only, with no network path, using the existing support-info scrubber for thread names, messages, stack frames, cause messages, and embedded report text. The P1 App Details single-action audit work closed the per-package "was it me?" reverse-audit gap, while existing diagnostic/logcat exports continue to scrub UID/path/package/email data.
  - Touches: `LocalCrashSink`, `AMExceptionHandler`, Privacy settings key/toggle, support-info bundle crash summary, and redaction tests.
  - Acceptance: composer writes `support-info-<device>-<ts>.txt`; crash sink writes local JSON only; redaction + reverse audit work.
  - Verify: `LocalCrashSinkTest` asserts scrubbed local JSON and summary output; `SupportInfoBundleTest` pins package/path/URI/email/UID redaction. Device-only follow-up: manually trigger a crash with the sink enabled and confirm the local JSON share attachment.
  - Complexity: L
- [x] P2 — Ingest UAD-NG dependency graph + OEM-provenance + apkscanner signatures (research A1–A3)
  - Why: dense `dependencies`/`neededBy` edges enable honest "removing X breaks Y/Z" warnings; OEM provenance powers Finder chips; apkscanner adds ~30–40% library coverage over Exodus.
  - Shipped 2026-06-04: reverified the shipped debloat data path already carries `dependencies` / `required_by` edges from `android-debloat-list`, and Debloater cards/details already render downstream breakage context. Reverified scanner resources already carry the broader android-libraries / LibSmali-style signature set through `libs.xml` alongside tracker signatures. Added `preinstalled_oems` support, `IFilterableAppInfo.getKnownPreinstallOems()`, conservative OEM inference for existing data, Finder known-preinstall-OEM result rows, Debloater OEM chips, and bloatware Finder predicates for OEM labels.
  - Touches: `DebloatObject`, `PreinstalledOemResolver`, `IFilterableAppInfo`, Finder row binding, `BloatwareDetailsDialog`, `BloatwareOption`, docs.
  - Acceptance: removal UI shows dependency/required-by breakage context from the bundled debloat graph; Finder shows known-preinstall-OEM rows/chips; scanner library coverage continues to use the bundled android-libraries/LibSmali-style signature resources. Full UAD-NG model/region ingest is parked in the B bucket until upstream publishes machine-readable package/OEM/model/region data.
  - Verify: `PreinstalledOemResolverTest` pins explicit, package-prefix, and description-context OEM resolution; `BloatwareOptionTest` pins the new OEM predicates.
  - Complexity: L
- [x] P2 — Snapshot bundle export/import before the applicationId rename
  - Why: critical data-loss guard — the install identity is already `io.github.sysadmindoc.AppManagerNG` (`app/build.gradle:15`) while the source namespace stays `io.github.muntashirakon.AppManager`, so an NG install does **not** inherit an upstream AM user's prefs/profiles/tags/history. A one-shot import bundle is the only migration path.
  - Shipped before this active-roadmap reconciliation and reverified 2026-06-04: `snapshot/SnapshotBundle` writes a schema-versioned ZIP of shared preferences (excluding `keystore`), profiles, rule TSVs, optional tags, and operation-history JSON; Settings -> Privacy exposes SAF export/import with a pre-import confirmation; `profiles/importers/ExternalProfileImporter` imports Canta JSON, UAD-NG settings JSON, and Hail line lists into profiles.
  - Evidence: `SnapshotBundle`, `SnapshotImportException`, `PrivacyPreferences`, `preferences_privacy.xml`, `ExternalProfileImporter`; archived ledger entries iter-64, iter-65, and iter-106 already recorded this as shipped.
  - Acceptance: bundle round-trips prefs/profiles/tags/history/rules with schema guardrails and path-traversal/size defenses; Canta/UAD-NG/Hail importers create App profiles.
  - Verify: `SnapshotBundleTest` and `ExternalProfileImporterTest`.
  - Complexity: L
- [x] P2 — Upstream-issue fixes carried into NG (iter-20)
  - Why: high-signal upstream bug reports map to concrete NG actions; several are also Android-16/17 hard breaks.
  - Shipped before active-roadmap reconciliation (#1964 slice; reverified 2026-06-04): File Manager already has toolbar SearchView filtering, debounced recursive matching through `FmSearchUtils.searchRecursive()`, active search chips, whole-volume scan warnings, empty-result clear-search recovery, row subtitles with containing-folder context, hidden-dot-file option handling, and `FmSearchUtilsTest` coverage.
  - Shipped 2026-06-04 (#1965 slice): the clear-data path already snapshots storage stats before/after `IPackageManager.clearApplicationUserData()` and falls back to `pm clear --user N <pkg>` when the measured data/cache size fails to drop; this pass added focused regression tests for the delta decision and shell-output success parsing.
  - Shipped 2026-06-04 (#1966 slice): NG has no upstream `app_info_card.xml` popup, so the source-specific fix moves SDK bounds, SDK Runtime manifest state, and signing certificate identity into a compact first App Info metadata group while keeping full SDK Runtime and certificate dialogs one tap away.
  - Shipped 2026-06-04 (#1967 slice): KernelSU diagnostics now classify package-only, unknown, and restricted App Profile states into recovery actions; the details dialog can request a fresh root grant, refresh diagnostics, and point restricted profiles back to KernelSU Manager policy review.
  - Shipped 2026-06-04 (#1968 slice): `ProfileApplierReceiver` now converts authenticated automatic profile triggers into the existing automation receiver instead of starting `ProfileApplierActivity`; profile automation accepts `extra_pkg` as a one-shot package override; the signed Locale/Tasker plugin can merge `extra_pkg` at fire time so Tasker variables do not invalidate the configured URI signature.
  - Shipped 2026-06-04 (#1963 slice): the Debloater activity alias was already present for pre-Android-13 shortcut callers; this pass added a manifest/shortcuts contract test covering exported static shortcut targets, trampoline action filters, alias target existence, and the Debloater alias no-launcher shape.
  - Shipped 2026-06-04 (#1961/#1962 host-verifiable slice): `Android16BinderCompat` now centralizes raw `IBinder.transact()` calls from `ProxyBinder`, `AMService`, and `BaseParceledListSlice`, keeps pre-Android-16 direct behavior, and attempts a reflective fallback on Android 16+ runtime/linkage failures; `Android16BinderCompatTest` also rejects new raw transact call sites. LOS 23.2 device validation remains blocked on a matching online ROM/device.
  - Shipped 2026-06-04 (#1957 source-truth slice): upstream closed the Dolphin `sqfs_open_image` report as the wrong repository; NG still has no SquashFS writer/dependency and the backup engine remains tar-family through `TarUtils.createDurable()`. `BackupArchiveFormatContractTest` now locks that current source truth and requires a real header/round-trip test before any future SquashFS backend can land.
  - Shipped 2026-06-04 (#1960 source-truth slice): upstream closed Shizuku mode support as duplicate #55; NG already exposes `Ops.MODE_SHIZUKU` in Settings -> Mode of operation and the privilege-health probe stack.
  - Shipped 2026-06-04 (#1956 AppOps revert slice): upstream reports Android 16/NothingOS AppOps mode writes falling back to `ignore`; `AppOpsManagerCompat.setMode()` already schedules `OsRevertMonitor.watchAppOp()` after every AppOps write, and this pass added regression coverage for the allow -> ignore banner detail plus the watcher hook.
  - Evidence: tracked per-issue in `RESEARCH_REPORT.md` §5; #1956, #1957, #1960, #1961, #1962, #1963, #1964, #1965, #1966, #1967, and #1968 are scoped and shipped. Device-only validation caveats remain parked in their dedicated gated rows.
  - Touches remaining: none for this upstream-issue batch.
  - Acceptance: each mapped fix lands with a regression test where applicable.
  - Verify: per-issue — scope against current source first, then test the specific fix. #1956 host gate: `OsRevertMonitorTest` and `AppOpsManagerCompatTest`. #1957 host gate: `BackupArchiveFormatContractTest`. #1961/#1962 host gate: `Android16BinderCompatTest`. #1963 host gate: `ShortcutManifestContractTest`. #1964 host gate: `FmSearchUtilsTest`. #1965 host gate: `PackageManagerCompatClearDataTest`. #1966 host gate: `:app:compileFullDebugJavaWithJavac`, `AppInfoViewModelCleartextTest`, and `SdkSandboxInfoTest`. #1967 host gate: `KernelSuDiagnosticsTest`. #1968 host gate: `AutomationRequestTest`, `AutomationIntentsTest`, `TaskerPluginBrokerTest`, and `ProfileApplierReceiverTest`.
  - Complexity: L
- [ ] P0 — Android 17 (target SDK 37) pre-bump compliance gate
  - Why: the individual Android 17 audits are written (`docs/audits/2026-05-*`) and the HKDF + reflection items are already implemented, but the **bump itself** (`compile_sdk`/`target_sdk` 36 -> 37) has not landed and needs a final on-image gate before flipping. This row is the bump checklist, not the (now-shipped) per-item audits.
  - Evidence: `versions.gradle:8-9` still `compile_sdk = 36` / `target_sdk = 36`; the audit docs exist but the bump is gated on an Android 17 image run.
  - Blocker rechecked 2026-06-06: local SDK platforms are `android-34`,
    `android-35`, `android-36`, and `android-36.1`; system images are only
    `android-36/google_apis/x86_64` and
    `android-36.1/google_apis_playstore/x86_64`; the only AVD is
    `Medium_Phone_API_36.1`; `adb devices` is empty; no `android-37` platform
    or system image is installed; and no `sdkmanager.bat` exists in the standard
    SDK locations. Do not bump until an API 37 platform + emulator/device image
    is available and the workflow can run.
  - Touches: `versions.gradle` `compile_sdk`/`target_sdk` -> 37 after the BAL allow-flag + explicit-URI-grant items are device-confirmed; re-run the `android17-emulator.yml` matrix on an API-37 image.
  - Acceptance: each audited behavior change passes on an Android 17 image before `targetSdk` flips to 37.
  - Verify: `android17-emulator.yml` green on API-37 with the bumped SDK.
  - Complexity: L

### Novelty (P3, no FOSS competitor)

- [ ] P3 — Wear OS phone-side companion + foldable posture awareness
  - Why: a Wear OS phone-side package manager is a no-FOSS-competitor banner feature (effort 4/5). (Note: the View-based tablet two-pane work is already tracked as **T21-H** in Existing Planned Work; this row is the *additive* Wear OS companion + foldable-posture awareness only, not the phone two-pane.)
  - Evidence: T21-H covers `SlidingPaneLayout` two-pane for phone/tablet; no `WearableListenerService`/`MessageClient`/watch companion in source.
  - Blocker rechecked 2026-06-06: the host still has only the
    `Medium_Phone_API_36.1` AVD, no Wear OS system images under the local SDK,
    and no connected Android devices (`adb devices` empty). Do not scaffold this
    blind; acceptance requires a paired watch emulator/device plus at least one
    package query/operation from the phone.
  - Touches: ADB-over-WiFi + `WearableListenerService`/`MessageClient` + ~200 KB watch companion; `FoldingFeature` posture awareness layered on the T21-H panes.
  - Acceptance: watch packages can be queried/operated from the phone; folded postures are handled.
  - Verify: pair a watch emulator, list its packages from the phone, run one op.
  - Complexity: XL

## Continuation State

### Last Completed Cycle

Cycle 19 - Foreground tracker overlay hardening on 2026-06-06.

### Current Focus

Continue from the next host-verifiable profile-visibility row:
**P2 - Add Private Space/profile visibility diagnostics**. Start by reviewing
`Users`, package/profile discovery, manifest hidden-profile permissions, and
main-list user/profile selection copy before extracting a host-testable profile
label/state mapping.

### Important Findings So Far

- Current Cycle 13 pre-cycle baseline was `dae498c` (`feat(startup): track
  initialization attempts`); the branch is `main`.
- `SplashActivity` exposes only the generic `initializing` state before
  delegating to `SecurityAndOpsViewModel.setModeOfOps()`, which posts only a
  terminal auth status after migration and `Ops.init()`.
- AndroidX splash guidance supports short, fast splash retention checks; the
  recovery UI should live in the authentication content view rather than
  extending a hidden splash indefinitely.
- Shizuku setup docs explicitly call out OEM background/local-network behavior
  and limited ADB permission modes, matching the reported wireless-debugging
  startup hang class.
- Running Apps currently writes background AppOps to ignored mode but has no
  same-surface inverse; App Ops guidance treats `RUN_IN_BACKGROUND` and
  `RUN_ANY_IN_BACKGROUND` as related but independently important.
- Running Apps restore is now implemented as a same-surface inverse: ignored or
  errored background AppOps are restored independently to `MODE_DEFAULT`, and
  default-mode rules are removed from the persisted blocker file. The planner
  can reuse a structured unrestricted previous mode if one is added later.
- `AssistActionActivity` has no service/broadcast actions today; App Details
  already contains guarded service and receiver surfaces that should be reused
  instead of adding raw intent ingress.
- `AssistComponentActionPlan` now reuses App Details service and receiver route
  rules, accepts only explicit components and declared receiver actions, and
  suppresses disabled/blocked or privileged-unavailable candidates.
- `AssistActionActivity` now consumes that planner: it loads services and
  receivers for the resolved target app, appends service start/stop and receiver
  broadcast entries to the visible quick-assist dialog, confirms route/user/
  permission details, dispatches explicit intents, and records non-replayable
  single-app Operation History rows.
- `Ops.init()` has blocking boundaries at root service bind, Shizuku service
  bind, wireless ADB port discovery, LocalServer restart/bind, and ADB pairing;
  `pairAdbInternal()` can wait in one-hour chunks while the pairing service is
  retrying.
- No pre-existing IPC/root-service JVM tests were found under `app/src/test` or
  `app/src/androidTest`.
- `ShizukuServiceConnectionWrapper` already ignores late callbacks outside an
  active bind window; root/local `ServiceConnectionWrapper` now has parity for
  this crash class through volatile binder/watcher fields and a no-throw
  callback path.
- `ServiceConnectionWrapperTest` was added under
  `app/src/test/java/io/github/muntashirakon/AppManager/ipc/` and covers
  callbacks without an active watcher plus active connected callbacks with a
  latch/binder.
- `StartupInitState` was added under `settings/` with immutable attempt-state
  transitions for migration/stage/status/timeout/cancel/retry; it maps
  `Ops.STATUS_*` to stages and recovery actions while rejecting old attempt ids.
- `StartupInitStateTest` covers stale-event rejection, timeout recovery,
  retry-token rollover, local-network and Shizuku permission recovery,
  pairing cancel, and success terminal/no-recovery behavior.
- The ignored local `local.properties` now points at
  `C:\Users\--\AppData\Local\Android\Sdk`, which allowed focused Gradle
  verification to run on this host.
- `SecurityAndOpsViewModel` now owns `LiveData<StartupInitState>`, synchronized
  attempt snapshots, monotonic attempt ids, current-attempt legacy status
  passthrough, and stale-status rejection before legacy terminal status posts.
- `SecurityAndOpsViewModelTest` covers current-attempt status passthrough,
  stale-status rejection, timeout/retry stale success rejection, and observable
  stage publication.
- Focused verification passed:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.ipc.ServiceConnectionWrapperTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
- Running Apps restore focused verification passed:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.runningapps.RunningAppsViewModelTest`.
- Assistant component-action focused verification passed:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistComponentActionPlanTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest --tests io.github.muntashirakon.AppManager.history.ops.OpHistoryItemTest`.
- Startup Splash recovery focused verification passed:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
- `SplashActivity` now observes `StartupInitState`, shows stage-specific text
  in the authentication layout, schedules current-attempt timeouts, and renders
  recovery buttons for retry, mode settings, Mode Doctor, support bundle,
  local-network permission, Shizuku permission, and pairing cancel.
- Gradle dependency verification and locking are now enabled. The no-write
  focused app test passed under strict metadata/lock checks:
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.StartupInitUiStateTest`.
- Release SBOM generation is now host-verifiable without a release build:
  `scripts/generate-cyclonedx-sbom.py` emits a CycloneDX 1.6 aggregate from the
  committed Gradle lockfiles and `--check` validates 575 component references in
  this checkout. The generated document also passed the official CycloneDX 1.6
  JSON schema through Python `jsonschema`.
- The tag release workflow now uploads a versioned `.cdx.json` SBOM asset and
  publishes GitHub APK provenance plus CycloneDX SBOM predicate attestations via
  `actions/attest@v4`. Full attestation verification remains tag-run dependent.
- Android's current `WindowManager.LayoutParams` docs describe
  `TYPE_ACCESSIBILITY_OVERLAY` as the window type for a connected
  `AccessibilityService`; Android 12 behavior docs list accessibility windows as
  trusted pass-through-touch exceptions while `TYPE_APPLICATION_OVERLAY` windows
  are not trusted.
- `TrackerWindow` no longer uses `TYPE_APPLICATION_OVERLAY` on O+ or
  `FLAG_LAYOUT_NO_LIMITS`. The new `TrackerOverlayPolicy` clamps overlay size
  and centered offsets, throttles layout updates, and disables the tracker after
  repeated `WindowManager` failures.
- Android local-network-permission guidance says target-SDK-37 local network
  denial can present as TCP timeout, so watchdog diagnostics should identify
  missing local-network permission separately from generic ADB port timeouts.

### Next Best Actions

1. Inspect `app/src/main/java/io/github/muntashirakon/AppManager/users/Users.java`,
   `PackageManagerCompat`, and main-list profile/user selector code for how
   personal, work, hidden, quiet, locked, and unavailable profiles are surfaced.
2. Extract a host-testable profile visibility/label mapper that can classify
   hidden/private/quiet/locked/unknown states from public API signals without
   implying unavailable profiles are scanned.
3. Wire the diagnostics into the least invasive existing surface and add focused
   JVM coverage for state labels and fallback copy.

### Unprocessed Leads

- Real tag-run `gh attestation verify` after the next release is published.
- Android 17 local-network permission timeout diagnostics for ADB and wireless
  pairing flows.
- Manual Android assist invocation for the shipped service/broadcast quick
  actions.
- Manual foreground tracker overlay walkthroughs on Android 11, Android 12+,
  and current target devices.

### Files Still To Inspect

- `app/src/main/java/io/github/muntashirakon/AppManager/users/Users.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java`
- main-list user/profile selector code and profile diagnostics surfaces

### Searches Still To Run

- `rg -n "ACCESS_HIDDEN_PROFILES|Private Space|hidden profile|quiet mode|UserManager|LauncherApps|profile" app/src/main/java app/src/test/java app/src/main/AndroidManifest.xml`
- Web: Android Private Space, `UserManager`, and `LauncherApps` docs if API
  semantics need refreshed before editing.
