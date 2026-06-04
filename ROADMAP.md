<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Active Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Last consolidated: 2026-06-04. Baseline: `main` at `9cdbb22`, app
`versionName 0.5.0`, `versionCode 7`.

This is the single live to-do file and holds **only open work**. Completed
rows are in [`COMPLETED.md`](COMPLETED.md) (canonical) and its prior ledger at
[`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md); full per-release prose
is in [`CHANGELOG.md`](CHANGELOG.md); consolidated research is in
[`RESEARCH_REPORT.md`](RESEARCH_REPORT.md); long-form research and old ledgers
are under [`docs/roadmap/archive/`](docs/roadmap/archive/) and
[`docs/archive/`](docs/archive/). Do not add new unchecked work to separate root
research files.

> Last researched: Cycle 1 - 2026-06-04.

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
  `:app:testFullDebugUnitTest` both green as of 2026-05-28.
- Most T19 / T20 / T21 rows below already have their data layer + JVM tests
  shipped; the open part is the Android-side UI wiring, called out per row.

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
  tests. `androidx.window 1.4.0` is already a dependency._
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

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing
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
- [ ] P2 — Privacy & Security API surfaces (research B1–B7)
  - Why: surface runtime-truth privacy/security signals AM currently can't show.
  - Shipped 2026-06-04 (B4 slice): App Details now surfaces an MTE/memory-tagging chip from `ApplicationInfo`. API 30 reads the native-heap pointer-tagging private flag; API 31+ reads `getMemtagMode()` for default/off/async/sync; below Android 11 degrades to "not supported". `MemoryTaggingInfoTest` pins the API-level mapping.
  - Shipped 2026-06-04 (B1 manifest slice): App Details now surfaces target-scoped SDK Runtime manifest declarations by parsing the base APK `uses-sdk-library` rows, shows unsupported/none/count chip states, and explains that this is not a live loaded-SDK list because public `SdkSandboxManager.getSandboxedSdks()` is caller-scoped.
  - Shipped 2026-06-04 (B2 slice): App Details now shows per-host Domain Verification state plus same-user deep-link conflicts in the "Open links" dialog, and Finder gained a `domain_links` predicate family for claimed domains, conflicted hosts, host matching, and conflicting-package matching.
  - Shipped 2026-06-04 (B3 App Info slice): App Details now detects archived packages via `PackageInfo.getArchiveTimeMillis()`, shows an Archived tag, and gates Archive/Unarchive actions to Android 15+ current-user, non-system, non-static-library apps. The action dispatches `PackageInstaller.requestArchive()`/`requestUnarchive()` through a private result receiver, starts pending-user-action intents, and reports success/failure.
  - Shipped 2026-06-04 (B3 batch/listing slice): main-list multi-select now exposes Archive/Unarchive batch requests on Android 15+, runs each eligible current-user, non-system package through the same `AppArchiveManager` request path, and Finder's app-state filter gained active/archived predicates backed by package archive timestamps.
  - Shipped 2026-06-04 (B5/B6 manifest slice): App Details now surfaces Health Connect `android.permission.health.*` manifest requests with read/write counts and a package-scoped Health Connect permissions deep link, plus Credential Manager provider service declarations for `android.service.credentials.CredentialProviderService` / system-provider actions with missing `BIND_CREDENTIAL_PROVIDER_SERVICE` warnings. Both dialogs label the data as manifest metadata rather than live grants, stored credentials, or enabled-provider state.
  - Shipped before active-roadmap reconciliation and reverified 2026-06-04 (B7): Restricted Settings diagnostics are already folded into the privilege health/mode-doctor path through `RestrictedSettingsDiagnostics`, `PrivilegeHealthPreferences`, and `PrivilegeModeDoctor` fix targets.
  - Evidence: SDK Runtime manifest declarations are covered by `ManifestParser.parseUsesSdkLibraries`, `SdkSandboxInfo`, and `SdkSandboxInfoTest`; no target-scoped live loaded-SDK source is currently used. Domain Verification user-state/link-handling is shipped in App Details via `DomainVerificationManagerCompat`; `DomainLinkConflictDetector`, `DomainLinksOption`, and `FilteringUtils.attachDomainLinkConflicts` cover the deep-link-conflict finder path. `AppArchiveManager`, `AppArchiveResultReceiver`, and `AppArchiveManagerTest` cover the App Info archiving slice; `BatchOpsManager.OP_ARCHIVE` / `OP_UNARCHIVE`, `activity_main_selection_actions`, `FreezeOption`, and `FreezeOptionTest` cover the batch/listing slice. Health Connect/Credential Manager manifest posture is covered by `HealthConnectInfo`, `CredentialProviderManifestInfo`, `HealthConnectInfoTest`, and `CredentialProviderManifestInfoTest`. Restricted Settings is covered by `RestrictedSettingsDiagnosticsTest`.
  - Remaining touches: SDK Sandbox live loaded-SDK state only if a truthful target-scoped source is found; App Archiving API-35 device verification; Health Connect live granted-permission state and Credential Manager enabled-provider state only if truthful target-scoped or privileged sources are found.
  - Acceptance: each remaining surface renders on supported API levels and degrades to "not supported" below; gated correctly.
  - Verify: `MemoryTaggingInfoTest`, `SdkSandboxInfoTest`, `DomainLinkConflictDetectorTest`, `DomainLinksOptionTest`, `AppArchiveManagerTest`, `FreezeOptionTest`, `HealthConnectInfoTest`, `CredentialProviderManifestInfoTest`, and `RestrictedSettingsDiagnosticsTest`; on an API-35 image, assert archive/unarchive rows render and complete the user-action flow; on API-30, assert graceful "not supported"; SDK Sandbox / Health Connect / Credential Manager live-state work must first prove a truthful per-target data source or be scoped to self/app-owned state only.
  - Complexity: L
- [ ] P2 — In-app Tasker plugin + Quick Settings tile suite + DocumentsProvider
  - Why: no app in the AM space ships these; leapfrog opportunity at low cost (Tasker plugin ~120 KB, in-app, effort 2/5). NG already has the `am://` deep-link contract (`docs/intent-api.md`) these can wrap.
  - Shipped before active-roadmap reconciliation: public confirmation-gated `AutomationUriActivity`, signature-gated `AutomationReceiver`, and `QuickFreezeTileService` for running the selected freeze profile.
  - Shipped 2026-06-04 (DocumentsProvider slice): `AppManagerDocumentsProvider` is registered at `${applicationId}.documents` and exposes read-only SAF roots for the configured local backup volume plus app-private profiles, with canonical-root traversal guards and deterministic child ordering.
  - Shipped 2026-06-04 (Quick Settings tile suite slice): freeze-profile selection now requests the Android 13+ `StatusBarManager.requestAddTileService()` one-tap add flow, and `ForceStopTileService` adds a "Force-stop app" QS tile whose pinned package/user target is set from the App Details force-stop long-press menu.
  - Shipped 2026-06-04 (Tasker/Locale plugin slice): `TaskerPluginEditActivity` and `TaskerPluginFireReceiver` implement the Locale plug-in edit/fire protocol, store signed `am://` automation bundles, reject unsigned/tampered runtime calls, and hand trusted plugin fires to the signature-gated in-app `AutomationReceiver`.
  - Evidence: `AutomationUriActivity`, `AutomationReceiver`, `TaskerPluginEditActivity`, `TaskerPluginFireReceiver`, `QuickFreezeTileService`, `ForceStopTileService`, and `AppManagerDocumentsProvider` are in the manifest; `AppManagerDocumentsProviderTest`, `AutomationRequestTest`, `AutomationIntentsTest`, `TaskerPluginBrokerTest`, `QuickFreezeTileControllerTest`, `QuickFreezeTileServiceTest`, `ForceStopTileControllerTest`, and `ForceStopTileServiceTest` cover the host-verifiable contract.
  - Remaining touches: device SAF/QS/Tasker manual verification.
  - Acceptance: a Tasker task can run a profile; tiles install + fire; backups/profiles appear in SAF pickers.
  - Verify: configure and fire the AppManagerNG Tasker plugin, add both tiles, fire them, assert the profile runs and the pinned force-stop target stops under a privileged mode; open a SAF picker, assert backups/profiles appear; run `AppManagerDocumentsProviderTest`, `AutomationRequestTest`, `AutomationIntentsTest`, `TaskerPluginBrokerTest`, `QuickFreezeTileControllerTest`, `QuickFreezeTileServiceTest`, `ForceStopTileControllerTest`, and `ForceStopTileServiceTest`.
  - Complexity: L
- [x] P2 — Snapshot bundle export/import before the applicationId rename
  - Why: critical data-loss guard — the install identity is already `io.github.sysadmindoc.AppManagerNG` (`app/build.gradle:15`) while the source namespace stays `io.github.muntashirakon.AppManager`, so an NG install does **not** inherit an upstream AM user's prefs/profiles/tags/history. A one-shot import bundle is the only migration path.
  - Shipped before this active-roadmap reconciliation and reverified 2026-06-04: `snapshot/SnapshotBundle` writes a schema-versioned ZIP of shared preferences (excluding `keystore`), profiles, rule TSVs, optional tags, and operation-history JSON; Settings -> Privacy exposes SAF export/import with a pre-import confirmation; `profiles/importers/ExternalProfileImporter` imports Canta JSON, UAD-NG settings JSON, and Hail line lists into profiles.
  - Evidence: `SnapshotBundle`, `SnapshotImportException`, `PrivacyPreferences`, `preferences_privacy.xml`, `ExternalProfileImporter`; archived ledger entries iter-64, iter-65, and iter-106 already recorded this as shipped.
  - Acceptance: bundle round-trips prefs/profiles/tags/history/rules with schema guardrails and path-traversal/size defenses; Canta/UAD-NG/Hail importers create App profiles.
  - Verify: `SnapshotBundleTest` and `ExternalProfileImporterTest`.
  - Complexity: L
- [ ] P2 — Macrobenchmark module + Baseline Profile + Espresso/UI-Automator smoke pack (research O-07, O-08)
  - Why: pure-local performance + regression coverage with zero privacy cost; cold-start is dominated by `PackageManager` enumeration on the app-list path. (The CVE-scan and `floss`/`full` halves of the original O-07–O-10 row are already shipped — see the removed-as-shipped note above — so only the benchmark + smoke-test work remains.)
  - Shipped 2026-06-04 (host-verifiable scaffold slice): `settings.gradle` now includes `:benchmark`; the module compiles AndroidX Macrobenchmark cold-start and BaselineProfileRule startup tests against `io.github.sysadmindoc.AppManagerNG.debug`; `app/src/main/baseline-prof.txt` carries a seed startup Baseline Profile; app androidTest now has a LargeTest UIAutomator launch smoke test for the main app list.
  - Evidence: `:benchmark:compileDebugJavaWithJavac` and `:app:compileFullDebugAndroidTestJavaWithJavac` pass on the host JDK 21 toolchain. AndroidX Baseline Profile Gradle plugin wiring was tested but is not compatible with the current AGP 9.2.0 module model in this checkout, so the shipped slice uses plain benchmark rules plus a checked-in seed profile.
  - Touches: `:benchmark` module + Baseline Profile (app-list path); `connectedCheck` smoke suite (open list, freeze/unfreeze, component blocker, one-shot rule) on API 26/30/34/35.
  - Remaining touches: run macrobenchmark/profile generation on an online device; add list-scroll and Backups TTI benchmarks; expand smoke coverage from app-launch to freeze/unfreeze, component blocker, and one-shot rule flows; wire a device CI lane once the emulator matrix is available.
  - Acceptance: benchmarks + smoke suite run in CI; a Baseline Profile ships in the release APK.
  - Verify: run the macrobenchmark locally, assert it records cold-start and refreshes the profile; run the smoke suite on API 26/30/34/35 emulators. Host compile gates: `:benchmark:compileDebugJavaWithJavac`, `:app:compileFullDebugAndroidTestJavaWithJavac`, `:benchmark:assembleDebug`, `:app:assembleFullDebugAndroidTest`, and `:app:mergeFlossReleaseArtProfile`.
  - Complexity: L
- [ ] P2 — Upstream-issue fixes carried into NG (iter-20)
  - Why: high-signal upstream bug reports map to concrete NG actions; several are also Android-16/17 hard breaks.
  - Shipped 2026-06-04 (#1967 slice): KernelSU diagnostics now classify package-only, unknown, and restricted App Profile states into recovery actions; the details dialog can request a fresh root grant, refresh diagnostics, and point restricted profiles back to KernelSU Manager policy review.
  - Shipped 2026-06-04 (#1968 slice): `ProfileApplierReceiver` now converts authenticated automatic profile triggers into the existing automation receiver instead of starting `ProfileApplierActivity`; profile automation accepts `extra_pkg` as a one-shot package override; the signed Locale/Tasker plugin can merge `extra_pkg` at fire time so Tasker variables do not invalidate the configured URI signature.
  - Evidence: tracked per-issue in `RESEARCH_REPORT.md` §5; #1967 and #1968 are scoped and shipped, the remaining issue fixes still need source-specific scoping before promotion.
  - Touches remaining: App Info popup restructure (#1966); `pm clear --user N` fallback + disk-delta (#1965); File Manager search (#1964); shortcut-target CI lint + missing `<activity-alias>` (#1963); Android 16 compat `IBinder` API-gating (#1961/#1962); squashfs writer validation (#1957); OS-revert banner for battery-optimization (#1956).
  - Acceptance: each mapped fix lands with a regression test where applicable.
  - Verify: per-issue — scope against current source first, then test the specific fix. #1967 host gate: `KernelSuDiagnosticsTest`. #1968 host gate: `AutomationRequestTest`, `AutomationIntentsTest`, `TaskerPluginBrokerTest`, and `ProfileApplierReceiverTest`.
  - Complexity: L
- [ ] P0 — Android 17 (target SDK 37) pre-bump compliance gate
  - Why: the individual Android 17 audits are written (`docs/audits/2026-05-*`) and the HKDF + reflection items are already implemented, but the **bump itself** (`compile_sdk`/`target_sdk` 36 -> 37) has not landed and needs a final on-image gate before flipping. This row is the bump checklist, not the (now-shipped) per-item audits.
  - Evidence: `versions.gradle:8-9` still `compile_sdk = 36` / `target_sdk = 36`; the audit docs exist but the bump is gated on an Android 17 image run.
  - Touches: `versions.gradle` `compile_sdk`/`target_sdk` -> 37 after the BAL allow-flag + explicit-URI-grant items are device-confirmed; re-run the `android17-emulator.yml` matrix on an API-37 image.
  - Acceptance: each audited behavior change passes on an Android 17 image before `targetSdk` flips to 37.
  - Verify: `android17-emulator.yml` green on API-37 with the bumped SDK.
  - Complexity: L

### Novelty (P3, no FOSS competitor)

- [ ] P3 — MMRL + LSPosed module browser (research B8)
  - Why: power users manage modules in a separate app; read-only listing is one tab away from existing root detection.
  - Evidence: no `/data/adb/modules/` reader in source.
  - Touches: read `/data/adb/modules/` + `/data/adb/lspd/` `module.prop`; "Modules" entry under Privilege Health-Check, gated on root.
  - Acceptance: lists installed modules read-only on rooted devices; hidden otherwise.
  - Verify: on a rooted device with a Magisk module, assert the module appears read-only.
  - Complexity: M
- [ ] P3 — Wear OS phone-side companion + foldable posture awareness
  - Why: a Wear OS phone-side package manager is a no-FOSS-competitor banner feature (effort 4/5). (Note: the View-based tablet two-pane work is already tracked as **T21-H** in Existing Planned Work; this row is the *additive* Wear OS companion + foldable-posture awareness only, not the phone two-pane.)
  - Evidence: T21-H covers `SlidingPaneLayout` two-pane for phone/tablet; no `WearableListenerService`/`MessageClient`/watch companion in source.
  - Touches: ADB-over-WiFi + `WearableListenerService`/`MessageClient` + ~200 KB watch companion; `FoldingFeature` posture awareness layered on the T21-H panes.
  - Acceptance: watch packages can be queried/operated from the phone; folded postures are handled.
  - Verify: pair a watch emulator, list its packages from the phone, run one op.
  - Complexity: XL
