<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Active Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Last consolidated: 2026-06-03. Baseline: `main` at `a54ae5f`, app
`versionName 0.5.0`, `versionCode 7`.

This is the single live to-do file and holds **only open work**. Completed
rows are in [`COMPLETED.md`](COMPLETED.md) (canonical) and its prior ledger at
[`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md); full per-release prose
is in [`CHANGELOG.md`](CHANGELOG.md); consolidated research is in
[`RESEARCH_REPORT.md`](RESEARCH_REPORT.md); long-form research and old ledgers
are under [`docs/roadmap/archive/`](docs/roadmap/archive/) and
[`docs/archive/`](docs/archive/). Do not add new unchecked work to separate root
research files.

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
  an "Export results" action that shares selected rows as TSV. _Data layer:
  `LeftoverScanner` (scan/scanInternalDataStubs/selectOrphans/sizeOnDisk; 15
  JVM tests), `LeftoverExportFormatter` (stable TSV + formula-field defusing;
  2 JVM tests), and `LeftoverCleanupHistoryItem` (cleanup op-history rows with
  high-risk, non-replayable metadata; 2 focused tests). **Follow-up: App
  Details uninstalled-package convenience entry.**_
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
  (`perfettoUiUrl()`). The confirm dialog shows a pre-capture config preview
  ("10s · 64 MB ring · N ftrace events · pkg") via `PerfettoConfigInspector`
  (shipped 2026-05-28). _Data layer: `PerfettoTraceConfigBuilder`,
  `PerfettoCommandBuilder`, `PerfettoConfigInspector`. **Follow-up: a duration
  picker and true mid-capture cancellation (device-verified).**_
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
  shipped 2026-05-28. compile + aapt2 link green. **Follow-up: a main-list tag
  chip (display only; creation/filtering/App-Details-display now work).**

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
- [`COMPLETED.md`](COMPLETED.md) — canonical completed/stale ledger.
- [`RESEARCH_REPORT.md`](RESEARCH_REPORT.md) — consolidated research notes.
- [`docs/archive/`](docs/archive/) — the dated research notes folded into
  `RESEARCH_REPORT.md` on 2026-06-03, preserved verbatim.

---

## Research-Driven Additions

Candidate rows surfaced from the 2026-06-03 research consolidation
(see [`RESEARCH_REPORT.md`](RESEARCH_REPORT.md) for full context and sources).
These are research findings, not yet committed work — promote into the
appropriate bucket above after scoping each against current code. Items already
rejected on license/privacy/scope grounds are recorded in
[`COMPLETED.md`](COMPLETED.md) under *Stale / Obsolete Items*.

### Privileged-action accountability & reliability

- [ ] P1 — Privileged Op Audit Log (research O-02)
  - Why: append-only accountability log is the unclaimed differentiator vs. upstream AM; UAD-NG/Magisk/Shizuku log grants, AM logs nothing.
  - Touches: new SQLite `(ts, mode, op, target, exit_code, signature)` table; viewer screen + JSON export; retention slider.
  - Acceptance: freeze/unfreeze/uninstall/permission-grant/component-toggle each write a row; viewer + export work; retention prunes.
  - Source: RESEARCH_REPORT.md §4 (O-02); §3 Stream 1.
- [ ] P1 — Privileged-shell journal + DeathRecipient replay
  - Why: today a mid-batch shell death leaves a half-applied batch with no recovery hint — the #1 reliability gap.
  - Touches: per-op journal (write intent -> execute -> mark done); Shizuku binder `DeathRecipient` + libsu `Shell.isAlive()`; reattach replay UI.
  - Acceptance: interrupting a batch op surfaces "N ops interrupted, retry?" and replays unfinished entries on reattach.
  - Source: RESEARCH_REPORT.md §3 Stream 4.
- [ ] P2 — Mode Self-Test "Doctor" active-probe screen (research O-03)
  - Why: catches "works on Magisk, fails on KernelSU 1.0.4" before users hit it; distinct from the display-only T5 Health-Check.
  - Touches: ordered probes (root binary, su grant, Shizuku binder ping, Sui, ADB pairing, ABI/SELinux, KSU API) with pass/fail/cause + fix-it deep-links.
  - Acceptance: each probe reports status + cause; failures deep-link to the relevant settings.
  - Source: RESEARCH_REPORT.md §4 (O-03); §3 Stream 1.
- [ ] P2 — Support Info Bundle + opt-in local crash sink (research O-01, O-05, O-11, O-12)
  - Why: matches the dominant privacy-respecting peer pattern (user-initiated support bundle, no remote telemetry).
  - Touches: one-tap PII-scrubbed support-info composer; ACRA-style local-file crash sink (default OFF, no network); structured log viewer w/ UID/path redaction; per-package "was it me?" reverse audit over the op log.
  - Acceptance: composer writes `support-info-<device>-<ts>.txt`; crash sink writes local JSON only; redaction + reverse audit work.
  - Source: RESEARCH_REPORT.md §4.

### Datasets feeding debloat / tracker verdicts

- [ ] P2 — Ingest UAD-NG dependency graph + OEM-provenance + apkscanner signatures (research A1–A3)
  - Why: dense `dependencies`/`neededBy` edges enable honest "removing X breaks Y/Z" warnings; OEM provenance powers Finder chips; apkscanner adds ~30–40% library coverage over Exodus.
  - Touches: `tools/import_uad.py` -> sidecar `dependencies.json` (edges only); `preinstalled.json` + `IFilterableAppInfo.getKnownPreinstallOems()`; second tracker-scan asset/pass.
  - Acceptance: removal UI shows downstream breakage; Finder shows preinstalled-OEM chips; scan flags apkscanner-only signatures. License posture GPL-2.0+/3.0 verified.
  - Source: RESEARCH_REPORT.md §2 Part A.

### Modern Android API exposures

- [ ] P2 — Privacy & Security API surfaces (research B1–B7)
  - Why: surface runtime-truth privacy/security signals AM currently can't show.
  - Touches: SDK Sandbox row (`getSandboxedSdks`); Domain Verification audit + deep-link-conflict finder; App Archiving action (Active/Frozen/Archived); MTE status chip; Health Connect + Credential Manager Privacy Dashboard; Restricted Settings unlock walkthrough in T5 Health-Check.
  - Acceptance: each surface renders on supported API levels and degrades to "not supported" below; gated correctly.
  - Source: RESEARCH_REPORT.md §2 Part B.
- [ ] P3 — MMRL + LSPosed module browser (research B8)
  - Why: power users manage modules in a separate app; read-only listing is one tab away from existing root detection.
  - Touches: read `/data/adb/modules/` + `/data/adb/lspd/` `module.prop`; "Modules" entry under Privilege Health-Check, gated on root.
  - Acceptance: lists installed modules read-only on rooted devices; hidden otherwise.
  - Source: RESEARCH_REPORT.md §2 Part B (B8).

### Automation, form-factor & migration surfaces

- [ ] P2 — In-app Tasker plugin + Quick Settings tile suite + DocumentsProvider
  - Why: no app in the AM space ships these; leapfrog opportunity at low cost (Tasker plugin ~120 KB, in-app, effort 2/5).
  - Touches: one `Activity` + one `BroadcastReceiver` for the Tasker plugin; `TileService.requestAddTileService()` for "Run Freeze Profile" + "Force-Stop Pinned App"; DocumentsProvider exposing `am://backups` + `am://profiles`.
  - Acceptance: a Tasker task can run a profile; tiles install + fire; backups/profiles appear in SAF pickers.
  - Source: RESEARCH_REPORT.md §3 Stream 2.
- [ ] P2 — Snapshot bundle export/import before the applicationId rename
  - Why: critical data-loss guard — once renamed off `io.github.muntashirakon.AppManager`, NG installs next to upstream and inherits no data.
  - Touches: `{prefs/, profiles/, tags/, history.db}` ZIP with a schema-version header; export + import flows; Canta/UAD-NG/Hail importers as cheap add-ons.
  - Acceptance: a bundle round-trips prefs/profiles/tags/history; ships before the rename.
  - Source: RESEARCH_REPORT.md §3 Stream 4.
- [ ] P3 — Large-screen / foldable adaptive scaffolds + Wear OS phone-side companion
  - Why: table-stakes adaptive layouts keep NG from looking dated on tablets/foldables; a Wear OS phone-side package manager is a no-FOSS-competitor banner feature (effort 4/5).
  - Touches: `NavigationSuiteScaffold`/`ListDetailPaneScaffold`/`SupportingPaneScaffold`, 5-breakpoint WindowSizeClass incl. XL, `FoldingFeature` posture; ADB-over-WiFi + `WearableListenerService`/`MessageClient` + ~200 KB watch companion.
  - Acceptance: layouts adapt across COMPACT/MEDIUM/EXPANDED/XL + folded postures; watch packages can be queried/operated from the phone.
  - Source: RESEARCH_REPORT.md §3 Stream 3.

### Testing, CI & build hardening

- [ ] P2 — Macrobenchmark + Espresso/UI-Automator smoke pack + CVE scan + `floss`/`full` flavors (research O-07–O-10)
  - Why: pure-local performance + regression coverage with zero privacy cost; keeps the F-Droid listing antifeature-flag-free.
  - Touches: `:benchmark` module + Baseline Profile (app-list path); `connectedCheck` smoke suite on API 26/30/34/35; `dependency-check-gradle` + `dependency-review-action` (fail on HIGH/CRITICAL); `floss` (no optional network) vs `full` flavors with F-Droid pinned to `floss`.
  - Acceptance: benchmarks + smoke suite run in CI; PRs fail on HIGH/CRITICAL CVEs; F-Droid build uses `floss`.
  - Source: RESEARCH_REPORT.md §4 (O-07–O-10); §3 Stream 1.

### Android 17 / dependency compliance (from iter-20 delta)

- [ ] P0 — Android 17 (target SDK 37) compliance audit
  - Why: several behavior changes are hard breaks before targeting SDK 37.
  - Touches: reflection-on-static-final audit (every `Field.setAccessible(true)` vs a static-final target); BAL allow-flag migration (profile-trigger Activity-launch-from-Service -> `_ALLOW_IF_VISIBLE` or a BroadcastReceiver); HKDF-from-master backup key derivation (Keystore 50k-key limit, audit `crypto/AESCrypto`); explicit `FLAG_GRANT_READ_URI_PERMISSION` + `grantUriPermission()` on Save-APK/Share-backup before the Android 18 cliff; remove `screenOrientation` locks on sw>=600dp paths.
  - Acceptance: each item verified on an Android 17 image before bumping `targetSdk` to 37.
  - Source: RESEARCH_REPORT.md §5 (Android 17).
- [ ] P0 — BouncyCastle 1.83 -> 1.84 dependency bump
  - Why: closes 4 CVEs incl. the PGP AEAD chunk DoS (CVE-2026-3505) relevant to GPG-encrypted backups.
  - Touches: `bouncyCastleVersion = 1.84` in `libs.versions.toml`; GPG backup smoke test.
  - Acceptance: bump lands, GPG backup smoke test green.
  - Source: RESEARCH_REPORT.md §5 (deps).
- [ ] P1 — AGP 8.13 -> 9.x bump before the next major release
  - Why: AGP 10 (mid-2026) removes the old `BaseExtension` DSL; there is no AGP 8.14.
  - Touches: AGP version + any deprecated DSL usage.
  - Acceptance: build green on AGP 9.x.
  - Source: RESEARCH_REPORT.md §5 (deps).
- [ ] P2 — Upstream-issue fixes carried into NG (iter-20)
  - Why: high-signal upstream bug reports map to concrete NG actions.
  - Touches: `ProfileApplierReceiver` w/ `extra_pkg` (#1968); KernelSU detect + force-re-grant (#1967); App Info popup restructure (#1966); `pm clear --user N` fallback + disk-delta (#1965); File Manager search (#1964); shortcut-target CI lint + missing `<activity-alias>` (#1963); Android 16 compat `IBinder` API-gating (#1961/#1962); squashfs writer validation (#1957); OS-revert banner for battery-optimization (#1956).
  - Acceptance: each mapped fix lands with a regression test where applicable.
  - Source: RESEARCH_REPORT.md §5 (upstream issues).
- [ ] P3 — `docs/SIDELOAD_VERIFICATION.md` position document
  - Why: preempt user confusion as Google Developer Verification for sideloading rolls out.
  - Touches: new doc explaining what AppManagerNG does/doesn't do re: verification.
  - Acceptance: doc published and linked from the README/release notes.
  - Source: RESEARCH_REPORT.md §5 (strategic).
