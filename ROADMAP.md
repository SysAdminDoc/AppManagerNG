<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Active Roadmap

Last consolidated: 2026-05-28. Baseline: `main` at `08239cb`, app
`versionName 0.5.0`, `versionCode 7`.

This is the single live to-do file and holds **only open work**. Completed
rows moved to [`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md);
full per-release prose is in [`CHANGELOG.md`](CHANGELOG.md); long-form research
and old ledgers are under
[`docs/roadmap/archive/`](docs/roadmap/archive/). Do not add new unchecked work
to separate root research files.

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

### T19 — Package-aware storage analysis

- [x] **T19-B Leftover detection after uninstall**: One-Click Ops "Detect
  leftover folders" entry shipped 2026-05-28 — `OneClickOpsViewModel.scanLeftovers`
  builds the installed set, scans `Android/{data,obb,media}` (and, when
  privileged, the root `/data/data` stubs), precomputes per-folder sizes off the
  main thread, and posts a `LeftoverEntry` list; the Activity shows a searchable
  multi-choice review dialog ("pkg · kind · size"), gates deletion behind
  `ActionAuthGate`, and `deleteLeftovers` removes each folder via the privileged
  `Paths.get(...).delete()` (recursive) with per-folder audit lines through the
  app `Log` and a "reclaimed X" result toast. _Data layer: `LeftoverScanner`
  (scan/scanInternalDataStubs/selectOrphans/sizeOnDisk; 15 JVM tests).
  **Follow-up: App Details uninstalled-package convenience entry, a result-list
  export action, and migrating the audit capture to a dedicated `op_history` DB
  type (shared with T21-F).**_
- [x] **T19-C APK duplicate finder**: One-Click Ops "Find duplicate APK files"
  entry shipped 2026-05-28 — `OneClickOpsViewModel.scanApkDuplicates` walks
  external storage with `ApkFileScanner`, fingerprints each `.apk` via
  `PackageManager.getPackageArchiveInfo` + `PackageUtils.getSigningCertSha256Checksum`
  (cert re-derived from the archive path), runs `ApkDuplicateSelector` keeping
  the largest copy, and posts duplicate groups. The review dialog flattens the
  drop set ("file · pkg vN · size · keeping <keeper>"), gates deletion behind
  `ActionAuthGate`, deletes via the privileged `Paths.get(...).delete()` with
  audit logging + a reclaimed-bytes toast. _Data layer: `ApkDuplicateSelector`,
  `ApkFileScanner`, `ApkBundleHeaderParser`; 39 JVM tests. **Follow-up: base-APK
  extraction so `.apks`/`.apkm`/`.xapk` bundles (not parseable by
  `getPackageArchiveInfo`) can be deduped; a File Manager selection action; and
  scanning configured backup destinations beyond external storage.**_
- [x] **T19-D Backup duplicate cleaner**: One-Click Ops "Delete duplicate
  backups" entry shipped 2026-05-28 — offers "keep newest"/"keep oldest" and
  runs `BackupRetentionPolicy.pruneVersionDuplicates(strategy)` on a worker
  thread, reporting the removed count. Same-version duplicates across backup
  folders/names collapse to one copy per package. _Data layer:
  `selectVersionDuplicates`/`pruneVersionDuplicates` (NEWEST/OLDEST/LARGEST/
  LARGEST_THEN_NEWEST), `BackupSizeResolver`, `reclaimableBytes`; 11 JVM tests.
  **Follow-up: "keep largest" needs a backup-size accessor on `BackupItem`;
  reclaimable-bytes hint and a dedicated `op_history` DB type remain.**_

### T20 — Performance and profiling (system-level)

- [x] **T20-A Perfetto system-trace export**: App Details overflow ->
  "Export Perfetto trace" shipped 2026-05-28 — gated on root/Shizuku/ADB (with
  an "Open developer options" fallback when unavailable), confirms, then
  `AppProfileCapture.capturePerfettoTrace` pipes the
  `PerfettoTraceConfigBuilder` text-proto to `perfetto -c - --txt -o` via
  `Runner` (argv validated by `PrivilegedRunnerArgValidator`), saving a
  `.perfetto-trace` to Downloads and offering an "Open Perfetto UI" button
  (`perfettoUiUrl()`). _Data layer: `PerfettoTraceConfigBuilder`,
  `PerfettoCommandBuilder`, `PerfettoConfigInspector`. **Follow-up: duration
  picker, a pre-capture config preview chip via `PerfettoConfigInspector`, and
  true mid-capture cancellation (device-verified).**_
- [x] **T20-B simpleperf CPU profile capture**: App Details overflow ->
  "Record CPU profile" shipped 2026-05-28 — gated on root/Shizuku/ADB, confirms
  (explaining the DWARF call-graph + platform `simpleperf`), then
  `AppProfileCapture.captureCpuProfile` runs the `CpuProfileCommandBuilder`
  argv (validated) via `Runner`, saving raw `perf.data` to Downloads.
  _Data layer: `CpuProfileCommandBuilder`, `CpuProfileEventCatalog`,
  `PrivilegedRunnerArgValidator`. **Follow-up: duration/event picker (gated by
  `CpuProfileEventCatalog`), on-device flame-graph SVG conversion, and true
  mid-capture cancellation (device-verified).**_
- [x] **T20-C Memory allocations inspector**: App Details overflow ->
  "Memory snapshot" shipped 2026-05-28 — `AppMemorySnapshotLoader.load` runs
  `dumpsys meminfo`/`gfxinfo` plus `pidof` -> `/proc/<pid>/status` + `/maps`
  through `Runner`, feeds the JVM-tested parsers, composes via
  `MemorySnapshotComposer`, and `format()` renders a provenance-tagged
  ("via /proc/maps · virtual", "via /proc/status") scrollable block using
  `MemoryFormat`. The action is gated on root/Shizuku/ADB and degrades
  gracefully when the app is not running or a source is truncated. _Data layer:
  the four parsers + composer + `MemoryFormat`; 50+ JVM tests, plus 11 new tests
  for `firstPid`/`provenanceFor`. **Follow-up: streaming/refresh and a richer
  per-region chart beyond the point-in-time text snapshot.**_

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
  `OsRevertCountTracker`. **Follow-up: wire `OsRevertMonitor.watch*` ->
  `OsRevertCountTracker.recordRevert` (currently the OS-revert count is passed
  as 0; perm/disabled signals already drive the badge) and add the eviction
  heartbeat to the `MainViewModel` refresh.**_
- [ ] **T21-H Material 3 Adaptive layouts for tablets / large screens**: App
  List + App Details master/detail and Settings two-pane via `androidx.window`
  + `SlidingPaneLayout`. Acceptance: compatible with existing View-based
  layouts, all view IDs preserved per the `codexprompt.md` contract, gated on
  available-width thresholds not fixed device classes. _Width-class resolver
  shipped: `WindowWidthSizeClass.resolve(int)` + `supportsTwoPane`/
  `requiresTwoPane` (COMPACT <600 / MEDIUM 600–840 / EXPANDED ≥840); 7 JVM
  tests. `androidx.window 1.4.0` is already a dependency.
  **Open: two-pane layouts + activity integration.**_
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
- [ ] **EI-07 Scheduled-backup "Why did this skip?" bottom sheet**: capture
  per-package skip-reason in the backup worker and surface it in a bottom sheet
  from the scheduled-backup screen.
- [ ] **NF-06 Pro Mode hero card**: the Pro Mode explainer copy shipped in
  v0.5.0; the deferred onboarding hero card remains.
- [ ] **NF-08 tag UI follow-up**: `AppTagStore` + `TagsOption` Finder predicate
  shipped (data layer). Verify/finish the App Details tag editor and the
  main-list tag chip so user-authored tags round-trip through the
  SharedPreferences store (preserve `AppTagStore.normaliseTag` semantics).

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
