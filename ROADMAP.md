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
- [ ] **T19-C APK duplicate finder**: index Downloads / backup destinations /
  external storage for `.apk`/`.apks`/`.apkm`/`.xapk`, dedupe by package name +
  signing cert + version code, surface cleanup in File Manager and the backup
  list. Acceptance: ignores zero-byte / partial downloads, respects cancelled
  scans, telemetry only in the op log. _Data layer shipped:
  `ApkDuplicateSelector` (bucket by `(pkg, versionCode, certSha256)`,
  LARGEST/SMALLEST keep, `reclaimableBytes`), `ApkFileScanner` (DFS walk,
  symlink-cycle defence, cancellation), `ApkBundleHeaderParser` (APKS/APKM/XAPK
  classification from ZIP central directory); 39 JVM tests.
  **Open: `PackageManager.getPackageArchiveInfo` + signing-cert extraction
  glue, One-Click Ops UI, File Manager action.**_
- [ ] **T19-D Backup duplicate cleaner**: detect duplicate
  `<package>@<version>@<variant>` archives across backup roots; per-pair "keep
  newest"/"keep largest" with manual override. Acceptance: extends T6 retention
  policy, never deletes the only good checksum-verified copy, writes to
  `op_history`. _Data layer shipped: `BackupRetentionPolicy`
  `selectVersionDuplicates`/`pruneVersionDuplicates` with NEWEST/OLDEST/LARGEST/
  LARGEST_THEN_NEWEST, `BackupSizeResolver` SAM, `reclaimableBytes`; 11 JVM
  tests. **Open: duplicate-cleaner UI + op_history wiring.**_

### T20 — Performance and profiling (system-level)

- [ ] **T20-A Perfetto system-trace export**: App Details "Export trace" action
  gated on Shizuku/ADB; trace persists to Downloads as `.perfetto-trace`,
  optional `ui.perfetto.dev` deep link, falls back to Developer Options when
  unavailable. _Data layer shipped: `PerfettoTraceConfigBuilder` (app-targeted
  text-proto), `PerfettoCommandBuilder` (argv + shell-metachar guard),
  `PerfettoConfigInspector` (preview parser), `perfettoUiUrl()`; shared
  `PrivilegedRunnerArgValidator.validateArgv`. **Open: App Details action +
  privileged-runner integration.**_
- [ ] **T20-B simpleperf CPU profile capture**: App Details "Record CPU
  profile" wrapping `simpleperf` for a bounded window, gated on
  root/Shizuku/ADB; output as flame-graph SVG + raw `perf.data`; cancellable;
  explains binary source/version. _Data layer shipped:
  `CpuProfileCommandBuilder.build` (canonical `simpleperf record` argv,
  duration clamp, event allow-list, injection guard), `CpuProfileEventCatalog`
  (per-API/per-ABI event availability), shared `PrivilegedRunnerArgValidator`.
  **Open: App Details output capture + cancellation surface.**_
- [ ] **T20-C Memory allocations inspector**: parse `dumpsys meminfo`,
  `dumpsys gfxinfo`, and procfs snapshots for the target package; surface in
  App Details. Acceptance: works on non-debuggable apps where data is
  privileged-readable, point-in-time snapshot, notes truncated `system_server`
  data. _Data layer shipped: `AppMemoryInfoParser`, `GfxInfoParser`,
  `ProcStatusParser`, `ProcMapsSummary`, `MemorySnapshotComposer` (per-field
  provenance via `FieldSource`), `MemoryFormat` unit-ladder; 50+ JVM tests.
  **Open: App Details UI.**_

### T21 — UI/design polish and premium feel

- [ ] **T21-E Discreet / generic launcher-icon mode**: extra launcher
  activity-alias icons (neutral system-styled square + monochrome + NG mark),
  switchable via Settings -> Appearance. Acceptance: exactly one alias enabled
  at a time, no extra `BOOT_COMPLETED` side-effects, widget icon palette
  unaffected. Design contract:
  [`docs/architecture/launcher-icon-aliases.md`](docs/architecture/launcher-icon-aliases.md).
  _Data layer shipped: `LauncherIconAliasPlan.plan(current, target)` +
  `resolveCurrent`; 11 JVM tests. **Open: manifest activity-aliases, neutral /
  monochrome drawables, `LauncherIconAliasController`
  (`setComponentEnabledSetting` + `DONT_KILL_APP`), Settings entry, glossary
  strings.**_
- [ ] **T21-F Undo SnackBar for destructive operations**: wrap freeze,
  uninstall, force-stop, clear-data, and component-state writes in a
  short-lived "Undo" SnackBar before commit. Acceptance: cancellation skips the
  privileged write entirely, an unactioned SnackBar still records `op_history`,
  duration follows the reduced-motion gate. _Data layer shipped:
  `UndoableActionQueue` (deferred-commit, `defer`/`cancel`/`pollExpired`/
  `drainAll`, injectable clock), `SnackbarDurationPolicy.windowFor`,
  `UndoOpHistoryRecorder.record`/`recordCommittedBatch`/`recordShutdownFlush`;
  27 JVM tests. **Open: SnackBar wiring per destructive surface.**_
- [ ] **T21-G Attention badges on app list rows**: tiny badge counter on rows
  with actionable state (pending dangerous-permission grants, disabled
  components, recent OS revert). Acceptance: single source of truth in the app
  cache, no list-scroll regression, glossary-documented. Design contract:
  [`docs/architecture/attention-badges.md`](docs/architecture/attention-badges.md).
  _Data layer shipped: `AttentionBadgeCalculator` (priority OS_REVERT >
  DANGEROUS_PERMISSION > DISABLED_COMPONENT > NONE, `formatCount` 99+),
  `AttentionBadgeSource.forApp`/`badgeFor`, `OsRevertCountTracker`
  (7-day TTL, bounded); 19+ JVM tests. **Open: `MainRecyclerAdapter`
  rendering, `OsRevertCountTracker.recordRevert` call sites + eviction
  heartbeat, glossary entry.**_
- [ ] **T21-H Material 3 Adaptive layouts for tablets / large screens**: App
  List + App Details master/detail and Settings two-pane via `androidx.window`
  + `SlidingPaneLayout`. Acceptance: compatible with existing View-based
  layouts, all view IDs preserved per the `codexprompt.md` contract, gated on
  available-width thresholds not fixed device classes. _Width-class resolver
  shipped: `WindowWidthSizeClass.resolve(int)` + `supportsTwoPane`/
  `requiresTwoPane` (COMPACT <600 / MEDIUM 600–840 / EXPANDED ≥840); 7 JVM
  tests. `androidx.window 1.4.0` is already a dependency.
  **Open: two-pane layouts + activity integration.**_
- [ ] **T21-I Fast list rendering for 10k+ installed apps**: profile + optimize
  the main app-list adapter/cache; baseline target <2 s cold filter on 10k
  apps. Acceptance: before/after numbers recorded in `docs/audits/`, no
  behavior change for typical (<300-app) devices, Robolectric coverage where
  reachable.

### Discovery & Polish carry-overs (re-surfaced from iter-143 handoff)

These were noted open in `.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md`
but were dropped from the 2026-05-26 consolidation. Folded back in here.

- [ ] **EI-04 Permission Inspector chip-row filter**: add a toolbar chip row to
  the permissions screen (e.g. Dangerous / Granted / Denied / Signature) backed
  by ViewModel filter state, mirroring the main-list quick-filter chip pattern.
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
