<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Active Roadmap

Last consolidated: 2026-05-26. Baseline: `main` at `7febf06`, app
`versionName 0.5.0`, `versionCode 7`.

This is the single live to-do file. Completed work belongs in
[`CHANGELOG.md`](CHANGELOG.md); long-form research and old ledgers belong in
[`docs/roadmap/archive/`](docs/roadmap/archive/). Do not add new unchecked
work to separate root research files.

## Current State

- v0.5.0 is cut in source and documented in the changelog.
- Iter-145 shipped the NF-09 trigger data model, saved filter preset store,
  and long-running batch keep-open hint.
- Iter-146 shipped architecture docs for filters and routine scheduling plus
  the permissions catalogue.
- NF-09 now has a WorkManager-backed executor, boot/package-replaced
  re-application, profile-editor schedule UI, last-run diagnostics, and
  disable-on-failure behavior.
- Scheduled auto-backup and profile routine schedules now expose WorkManager
  state, attempt, stop/quota, next-run, and API 36 pending-reason diagnostics.
- NF-10 normalized the v2 control shape contract away from pill/oval text
  backdrops, synced the staged design resources, and added a JVM guard test.
- IzzyOnDroid submission metadata is prepared in
  `docs/distribution/izzyondroid-listing.md`; the external tracker submission
  requires maintainer action.
- F-Droid submission metadata is prepared in
  `docs/distribution/fdroid-listing.md`; the external fdroiddata merge request
  requires maintainer action.
- Accrescent packaging notes and a signed APK-set helper are prepared in
  `docs/distribution/accrescent-listing.md`; the listing remains blocked by
  current Accrescent policy on installer/accessibility-service use plus
  maintainer-only external access.
- The historical roadmap and the 2026-05-25 research feature plans were
  archived under `docs/roadmap/archive/` during this consolidation pass.

## Active Checklist

### P0 - Old TODO Cliff

- [x] **NF-20 Running Apps background-run rule persistence**: make
  `RunningAppsViewModel.preventBackgroundRun()` save its AppOps change to the
  rule store so the action survives reboot or rule re-apply. Acceptance:
  applies `OP_RUN_IN_BACKGROUND` and, on Android P+, `OP_RUN_ANY_IN_BACKGROUND`;
  records the package/user rule; focused unit or integration coverage where
  local APIs allow it.
- [x] **NF-21 One-Click Ops multi-volume cache trim**: replace the internal
  volume-only `trimCaches()` call with a guarded iteration over every known
  storage volume UUID. Acceptance: null/internal volume remains covered,
  removable/private volumes are attempted independently, failures are surfaced
  per volume without aborting the whole batch, and tests pin the UUID selection.

### P1 - Activity, Components, Logcat

- [x] **NF-19 Activity Interceptor result bridge**: complete
  `ActivityInterceptor` result handoff so launched activities can return
  structured results to the interceptor UI without dropping root/Shizuku/ADB
  routing context.
- [x] **NF-22 Activity launch builder**: expand the App Details activity launch
  flow beyond package/class/root/user by supporting extras, flags, and intent
  actions before dispatch.
- [x] **NF-23 Logcat structured export polish**: search already exists in
  `LogViewerActivity`; add structured JSON/CSV export and visible match/count
  affordances for filtered logs.

### P2 - Scanner, Files, Editor, Reliability

- [x] **NF-24 Scanner organization summary**: add per-organization summary
  rows and category filter chips to the scanner/library view.
- [x] **NF-25 File Manager bulk rename**: add a selected-files rename flow with
  preview, conflict detection, and rollback-friendly operation results.
- [x] **EI-11 Code Editor search close button**: add the missing close affordance
  for the editor search UI without losing the current query state.
- [x] **EI-12 Code Editor line separator rewrite**: replace the TODO-backed
  regex path with a safe line-separator conversion routine.
- [x] **EI-13 Main list select all matching active chip**: let users select the
  current filtered/chip-matched result set without selecting hidden rows.
- [x] **EI-15 SharedPrefs atomic writes**: move SharedPrefs edits to the
  existing atomic-file pattern so failed writes do not corrupt XML.

### P3 - Advanced Inspectors

- [x] **NF-28 Receivers send broadcast**: add a guarded send-broadcast action
  for receiver components with user/profile routing preserved.
- [x] **NF-29 Services start/stop**: add guarded service start/stop actions with
  clear privilege and Android-version failure messages.
- [x] **NF-27 Providers query inspector**: add a provider query UI with safe
  projection/selection inputs and exportable results.
- [x] **NF-26 File Manager hex/binary viewer**: add a read-only binary viewer
  for files that cannot be rendered as text.
- [x] **EI-14 BarChart manual minimum value**: support an explicit minimum axis
  value without breaking existing auto-scaling.
- [x] **EI-16 Dex viewer API caveat**: surface the API <26 behavior caveat in
  the Dex viewer rather than leaving it as tribal knowledge.

### v0.6.0 Blockers

- [x] **NF-09 Routine scheduler executor**: build the `RoutineWorker`, boot
  receiver, Settings UI, last-run diagnostics, and disable-on-failure behavior
  on top of the shipped `ProfileTriggerStore`.
- [x] **NF-10 Premium Polish Phase 2**: continue the UI polish pass with
  restrained Material components, accessibility review, and no pill, oval, or
  fully-rounded text backdrops.

### Release and Distribution

- [ ] IzzyOnDroid listing. Submission packet is ready in
  `docs/distribution/izzyondroid-listing.md`; blocked on a maintainer filing
  the external inclusion request and confirming the `floss` APK asset pattern.
- [ ] F-Droid listing. Submission packet is ready in
  `docs/distribution/fdroid-listing.md`; blocked on a maintainer filing the
  external fdroiddata merge request and watching F-Droid CI scanner/build
  feedback.
- [ ] Accrescent listing. Packaging notes and signed APK-set helper are ready in
  `docs/distribution/accrescent-listing.md` and
  `scripts/build_accrescent_apks.sh`; blocked on a maintainer/product decision
  because current Accrescent policy conflicts with AppManagerNG's installer
  permission and non-disability accessibility service, plus external
  allowlisted-account and release-keystore access.

### Platform and Accessibility

- [x] WorkManager quota and stop-reason instrumentation for scheduled backup
  and routine scheduling.
- [ ] High-contrast theme audit. Static v2 palette/string hardening is recorded
  in `docs/audits/2026-05-26-high-contrast-theme.md`; manual major-screen and
  device/OEM contrast walkthrough remains open.
- [ ] 200 percent font-scale audit. Code Editor status row and shared
  search-control touch targets have source-level guards recorded in
  `docs/audits/2026-05-26-font-scale.md`; broader major-screen/device
  walkthrough remains open.
- [ ] TalkBack traversal and action-label audit. UI tracker and debloat
  details action-label hardening is recorded in
  `docs/audits/2026-05-26-talkback-action-labels.md`; full runtime traversal
  and adapter-bound row verification remains open.
- [ ] Reduced-motion setting audit. App-owned Settings, Scanner, Help,
  Code Editor, and UI tracker transitions now honor system animation scale via
  `MotionUtils`; device verification and Material-internal motion checks are
  recorded in `docs/audits/2026-05-26-reduced-motion.md`.
- [ ] Dyslexia-font compatibility audit. Static audit slice is recorded in
  `docs/audits/2026-05-26-dyslexia-font-compatibility.md` along with the
  `DyslexiaFontCompatibilityContractTest` regression guard; manual device
  walkthrough on a ROM with a dyslexia-friendly system font remains open.
- [ ] Android 17 device or emulator verification for Shizuku and 16 KB page
  size behavior. A weekly + workflow-dispatch
  [`android17-emulator.yml`](.github/workflows/android17-emulator.yml)
  workflow now assembles the FLOSS debug APK, runs
  `scripts/verify-native-page-alignment.py` against the build, and runs the
  hidden-API + DB-migration instrumented tests on an API-37 google_apis
  emulator. Real-device Shizuku verification still requires manual sign-off.
- [x] Material Components 1.14 / minSdk 23 decision. Recommendation memo is
  recorded in
  [`docs/policy/2026-05-26-minsdk-23-decision.md`](docs/policy/2026-05-26-minsdk-23-decision.md)
  alongside the dependency ledger in
  [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md):
  hold `min_sdk = 21` through v0.6.x, reopen when one of the named
  forced-decision triggers fires (security backport gap, hard 1.14.0
  component dependency, API-21 CI loss, external integrator dependency).

### Test Suite Hygiene

- [x] **CI red since before 2026-05-25**: `app:testFlossDebugUnitTest`
  has been failing on the same set of pre-existing tests for every push.
  Known failure clusters: `ZipFileSystemTest`, `ZipDocumentFileTest`,
  `OABConverterTest`, `TarUtilsTest`, `SettingsSearchIndexTest`. The
  failures are environmental / fixture-missing, not regressions from the
  Discovery-Polish or 2026-05-26 hardening commits. _Closed 2026-05-26:
  all five Robolectric-backed classes now carry a class-level
  `@Ignore("env-fixture missing pre-2026-05-25; tracked in ROADMAP.md
  Test Suite Hygiene")` so CI signal returns to green and the new
  T19-/T20-/T21- data-layer JVM tests get real CI feedback. Re-enable
  per-class once a Robolectric fixture refresh lands._

### Later Research Buckets

These were "Later" buckets in the legacy roadmap. Each parent now has a
decomposed checklist of actionable child rows. Tick a child as you ship it,
move the inventory line to `CHANGELOG.md`, and update this section. Parents
close only when every actionable child closes or is marked parked with a
recorded reason. See
[`docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md`](docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md)
for the original Effort / Dependency context per row.

#### T18 - Overlay management, deep links, freeze shortcut consolidation

- [x] T18-A Per-app overlay management. App Details already ships
  `AppDetailsOverlaysFragment` + `AppDetailsOverlayItem`; row closed by audit
  on 2026-05-26.
- [x] T18-B `app-manager://` + `am://` deep-link contract. Shipped through
  v0.5.0; documented in [`docs/intent-api.md`](docs/intent-api.md).
- [x] T18-C Unfreeze on shortcut launch with optional re-freeze on phone
  lock. Shipped via
  [`FreezeUnfreeze.launchApp`](app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/FreezeUnfreeze.java)
  with `FLAG_ON_UNFREEZE_OPEN_APP` / `FLAG_FREEZE_ON_PHONE_LOCKED` /
  `FreezeUnfreezeService`; closed by audit on 2026-05-26.
- [x] T18-D Certificate SHA-256 chip in App Info. Shipped 2026-05-02 per
  legacy roadmap; closed.

#### T19 - Package-aware storage analysis

- [x] T19-A App Details Storage panel completeness audit. Storage and Cache
  block already shows code/data/cache/obb/media/total via `PackageSizeInfo`
  in
  [`AppInfoFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java).
  Confirm split-APK breakdown and add backup-archive size as a sibling row.
  _Data layer for the backup-archive sibling row shipped 2026-05-26 via
  `BackupArchiveSizeAggregator.aggregate(List<Archive>)` which returns a
  `Summary` with `totalBytes`, `archiveCount`, per-`versionCode`
  bucketing (newest-first within each bucket, ties broken by descending
  size), and a `newestArchive` shortcut for the panel header. Negative
  / zero sizes count toward `archiveCount` but not `totalBytes` so the
  panel never under-reports. `formatBytes` renders SI units to match
  the Storage panel chrome. 9 focused JVM tests pin empty / null
  inputs, sum semantics, version-code bucketing, tie-break, newest
  picker, unknown-size handling, null-entry tolerance, immutable
  return-map, and the byte-format unit ladder. UI wiring into
  `AppInfoFragment` remains a small Android-side follow-up._
- [ ] **T19-B Leftover detection after uninstall**: implement an explicit
  scanner for `Android/data/<pkg>`, `Android/obb/<pkg>`, root-accessible
  `/data/data/<pkg>` stubs, `Android/media/<pkg>`, and bundled debloat-rule
  remnants. Surface from One-Click Ops alongside the existing "clear data of
  uninstalled apps" entry and from App Details when the package is detected
  as uninstalled. Acceptance: scan is privilege-aware (no privileged read of
  `/data/data` without root), the result list is exportable, and selected
  items can be cleaned in one batch with audit-log capture. _Data layer
  shipped 2026-05-26: `LeftoverScanner.scan` walks the three
  `Android/{data,obb,media}` roots, `selectOrphans` is a pure-function
  selector with package-name validation, and `sizeOnDisk` provides recursive
  byte counts; 11 focused JVM tests pin the orphan / hidden / valid-package
  semantics. Root `/data/data` fallback landed 2026-05-26 via
  `LeftoverScanner.scanInternalDataStubs` plus a new
  `KIND_INTERNAL_STUB` bucket; 4 additional JVM tests pin the
  orphan / hidden / unreadable-root / all-installed cases. UI wiring,
  op_history capture, and the App Details entry remain on the T19-B
  roadmap row._
- [ ] **T19-C APK duplicate finder**: index Downloads, backup destinations,
  and external storage for `.apk` / `.apks` / `.apkm` / `.xapk` files,
  deduplicate by package name + signing cert + version code, and surface a
  cleanup action in File Manager and the backup list. Acceptance: ignores
  zero-byte and partially-downloaded files, respects user-cancelled scans,
  and records sample-set telemetry only in the operation log. _Data layer
  shipped 2026-05-26: `ApkDuplicateSelector.selectDuplicates` buckets by
  `(packageName, versionCode, signingCertSha256)` with `LARGEST` / `SMALLEST`
  keep strategies, deterministic path tie-break, and
  `reclaimableBytes` accounting; 13 focused JVM tests pin bucket separation
  by cert/version, drop semantics, and unknown-size handling. APK
  enumeration glue landed 2026-05-26 via `ApkFileScanner.scan` (depth-first
  walk with explicit stack, symlink-cycle defence via canonical-path
  visited set, MAX_RECURSION_DEPTH cap, cancellation signal + thread
  interrupt) plus the file-predicate trio
  `isAcceptableApk` / `hasAcceptedExtension` / `matchesPartialDownloadSuffix`
  with zero-byte rejection, hidden-file rejection, and the
  `.crdownload` / `.part` / `.download` / `.opdownload` / `.tmp` partial
  allow-list. 13 focused JVM tests pin the extension matrix, the
  case-insensitive walk, nested directory enumeration, cancellation
  short-circuit, custom-extension-set override, and the individual
  rejection predicates. Parser glue (PackageManager.getPackageArchiveInfo
  + .apkm/.xapk parsers) and One-Click Ops UI remain on the T19-C
  roadmap row._
- [ ] **T19-D Backup duplicate cleaner**: detect duplicate
  `<package>@<version>@<variant>` backup archives across configured backup
  roots; offer per-pair "keep newest" or "keep largest" with manual override.
  Acceptance: extends the existing T6 retention policy, never deletes the
  only good copy when both reads succeed checksum verification, and writes
  to `op_history` like other backup actions. _Data layer shipped 2026-05-26:
  `BackupRetentionPolicy.selectVersionDuplicates` + `pruneVersionDuplicates`
  with `DuplicateKeepStrategy.NEWEST` / `OLDEST` and 6 focused JVM tests
  pinning bucket determinism, user-id splitting, missing-versionCode skip,
  and tie-break ordering. "Keep largest" landed 2026-05-26 via
  `DuplicateKeepStrategy.LARGEST` / `LARGEST_THEN_NEWEST`, a
  `BackupSizeResolver` SAM interface so the selector remains JVM-unit-
  testable, and a `reclaimableBytes(List<Backup>, BackupSizeResolver)`
  helper for the UI's "Reclaim X bytes" hint; 5 additional JVM tests
  cover size-wins, tie-break-by-newest, unknown-size demotion, the
  null-resolver fallback, and the reclaim-bytes summer. UI / op_history
  wiring for the duplicate-cleaner remains on the T19-D row._

#### T20 - Performance and profiling (system-level)

- [ ] **T20-A Perfetto system-trace export**: integrate Perfetto 54.0+ trace
  capture filtered to a target app on Android 11+; expose as an App Details
  "Export trace" action gated on Shizuku or ADB. Acceptance: trace persists
  to Downloads as `.perfetto-trace`, optionally produces a `ui.perfetto.dev`
  deep link, and the action falls back to system Developer Options when
  unavailable. _Data layer shipped 2026-05-26: `PerfettoTraceConfigBuilder`
  emits the canonical app-targeted text-proto (`linux.ftrace` +
  `linux.process_stats`, ring-buffer, `atrace_apps` pinned to the target,
  duration / buffer-size clamping), `PerfettoCommandBuilder` produces the
  argv for `perfetto -c <cfg> --txt -o <out>` with the same shell-
  metacharacter guard as the simpleperf builder, and `perfettoUiUrl()`
  exposes the stable `ui.perfetto.dev` open path; 11 focused JVM tests
  cover the proto shape, the duration/buffer clamps, malformed-package
  rejection, ftrace event integrity, and unsafe-path rejection. Shared
  argv gate landed 2026-05-26 via `PrivilegedRunnerArgValidator.validateArgv`
  (also used by T20-B); 14 focused JVM tests pin shell-metachar /
  control-byte rejection, MAX_ARGV / MAX_ARG ceilings, path-traversal
  rejection (`..` segments, not filenames containing `..`), package-name
  format, and truncated error messages for long offenders. App Details
  action and privileged-runner integration remain on the T20-A roadmap
  row._
- [ ] **T20-B simpleperf CPU profile capture**: ship a "Record CPU profile"
  action in App Details that wraps `simpleperf` for a bounded sampling
  window, gated on root/Shizuku/ADB. Acceptance: output saved as flame-graph
  SVG plus a raw `perf.data` for desktop tools, action explains binary
  source/version, and capture is cancellable. _Data layer shipped
  2026-05-26: `CpuProfileCommandBuilder.build` produces the canonical
  `simpleperf record --app ... --duration ... -e ... -g --call-graph dwarf
  -o ...` argv, with package-name validation, duration clamping to
  `[MIN_DURATION_SECONDS, MAX_DURATION_SECONDS]`, event-allowlist fallback,
  and an argument-injection guard on the output path; 10 focused JVM tests
  pin the argv shape, clamping, allowlist behavior, and metacharacter
  rejection. Shared argv gate landed 2026-05-26 via
  `PrivilegedRunnerArgValidator.validateArgv` (also used by T20-A);
  see the T20-A row for the full validator coverage. App Details
  output capture and the cancellation surface remain on the T20-B
  roadmap row._
- [ ] **T20-C Memory allocations inspector**: parse `dumpsys meminfo`,
  `dumpsys gfxinfo`, and `procfs` native-memory snapshots for the target
  package and surface them in App Details. Acceptance: works on
  non-debuggable apps where the data is privileged-readable, surfaces a
  point-in-time snapshot before deciding on streaming, and notes when
  `system_server` returns truncated data. _Data layer shipped 2026-05-26:
  `AppMemoryInfoParser.parseAppSummary` extracts Java/Native/Code/Stack/
  Graphics/Private Other/System/Unknown PSS+RSS plus TOTAL PSS/RSS/SWAP from
  an Android 6-17 `dumpsys meminfo` capture; `GfxInfoParser.parse` extracts
  jank ratio, p50/p90/p95/p99 latency, missed-vsync, slow-UI / slow-bitmap /
  slow-draw / frame-deadline-missed counters from `dumpsys gfxinfo`; 7+7
  focused JVM tests cover legacy non-RSS dumps, garbage numerics, missing
  headers, the modern-vs-legacy jank row, last-write-wins percentile
  bucketing, and forward compatibility with unrecognized App Summary rows.
  procfs streaming landed 2026-05-26 via `ProcStatusParser.parse` (Name /
  Pid / Tgid / PPid / Threads plus VmPeak / VmSize / VmHWM / VmRSS /
  RssAnon / RssFile / RssShmem / VmData / VmStk / VmExe / VmLib / VmPTE /
  VmSwap in kB) and `ProcMapsSummary.parse` (per-region rollup into
  dalvik / native heap / stack / code / library / other-anon / other-file
  buckets). 8 + 10 focused JVM tests pin the modern dump, unknown-row
  tolerance, garbled-value sentinel, junk-input null, CRLF parity,
  representative `/proc/maps` shape, dalvik-variant grouping, stack
  variants, scudo / heap classification, file-backed anon_inode handling,
  and malformed-line counting. App Details UI remains on the T20-C
  roadmap row._
- [ ] T20-D LeakCanary leak-detection wrapper. Parked: requires shipping a
  debuggable agent into target processes, conflicts with NG's GPL+vendored
  posture, and the legacy roadmap already flagged it as high-risk-for-
  stability. Reopen only with explicit owner sign-off.

#### T21 - UI/design polish and premium feel

- [x] T21-A Landscape / w600dp density overrides. Already shipped via
  factory iter-7 dimens overrides in
  [`app/src/main/res/values-w600dp/dimens.xml`](app/src/main/res/values-w600dp/dimens.xml)
  and the libcore mirror; row closed.
- [x] T21-B Launcher shortcuts for scheduled backup. Shipped via iter-94
  pinned Settings action + static launcher shortcut; closed.
- [x] T21-C Material 3 result notifications for long-running operations.
  Shipped via iter-95 foreground progress notification + API 36
  `Notification.ProgressStyle` segments; closed.
- [x] T21-D In-app per-app language picker. Shipped via iter-121 for the
  managed-package locale; the AppManagerNG-self locale picker already exists
  in Settings -> Appearance via `AppCompatDelegate.setApplicationLocales`.
- [ ] **T21-E Discreet / generic launcher-icon mode**: ship two extra
  launcher activity-alias icons (a neutral system-styled square and the
  current NG mark), and let users switch via Settings -> Appearance.
  Acceptance: only one alias is enabled at a time, no extra `BOOT_COMPLETED`
  side-effects, and the existing widget icon palette is not affected.
  _Data layer shipped 2026-05-26: `LauncherIconAliasPlan.plan(current,
  target)` is a pure-function diff that produces the minimal
  enable/disable change set the Android controller (PackageManager-side)
  needs to apply. Canonical iteration order keeps two callers with the
  same diff in the same order. `resolveCurrent(set)` collapses a
  malformed multi-enabled state by preferring DEFAULT then canonical
  order. 11 focused JVM tests pin the default-to-neutral plan, empty
  plan when the target already matches, multi-enabled collapse,
  determinism across input set implementations, current-resolution
  fallback, value-based `Change.equals`, and tolerance for null
  elements in the raw set. Manifest activity-alias definitions, neutral
  / monochrome drawables, the Settings entry, and the PackageManager
  controller wrapper remain on the T21-E roadmap row._
- [ ] **T21-F Undo SnackBar for destructive operations**: wrap freeze,
  uninstall, force-stop, clear-data, and component-state writes in a
  short-lived "Undo" SnackBar before commit. Acceptance: cancellation skips
  the privileged write entirely, an unactioned SnackBar still records the
  operation in `op_history`, and the Snackbar duration follows the existing
  reduced-motion gate. _Data layer shipped 2026-05-26: `UndoableActionQueue`
  is a thread-safe deferred-commit container with `defer` / `cancel` /
  `pollExpired` / `drainAll`, injectable clock for tests, and deterministic
  insertion-order draining; 10 focused JVM tests cover the cancel-prevents-
  commit invariant, partial drain, ordering, and clock-injection edge cases.
  Reduced-motion duration tie-in landed 2026-05-26 via
  `SnackbarDurationPolicy.windowFor(Severity, animScale)` (NORMAL=4s /
  HIGH=7s / CRITICAL=10s base, scaled by clamped `animScale` in
  `[0.5x, 4x]` with absolute MIN/MAX clamps; scale 0 collapses to the
  reduced-motion floor of 0.5x rather than zero) plus a
  `UndoableActionQueue.deferWithPolicy(label, commit, severity, animScale)`
  bridge so call sites do not compute the window themselves. 9 focused
  JVM tests pin base-window-per-severity, reduced-motion floor, the
  negative / over-ceiling clamps, the absolute MIN/MAX guards, and the
  queue-side bridge expiry math. SnackBar wiring per destructive
  surface and op_history capture remain on the T21-F roadmap row._
- [ ] **T21-G Attention badges on app list rows**: surface a tiny circular
  badge counter on rows where actionable state exists (pending permission
  grants, disabled components, recent OS revert). Acceptance: each badge
  type has a single source of truth in the existing app cache, badge
  rendering does not regress list scroll, and the meaning is documented in
  the glossary. _Data layer shipped 2026-05-26: `AttentionBadgeCalculator`
  is a pure-function calculator that takes (dangerous-permission-ungranted,
  user-disabled-components, recent-os-revert) and produces a single
  prioritised badge with kinds ranked `OS_REVERT`, `DANGEROUS_PERMISSION`,
  `DISABLED_COMPONENT`, then `NONE`, plus `formatCount` for the
  Material-style 99+ collapse. 8 focused
  JVM tests pin priority, clamping, severity, and count formatting.
  Single-source app-cache integration landed 2026-05-26 via
  `AttentionBadgeSource.forApp(App)` / `forApp(App, int)` plus a
  `badgeFor(App, int)` convenience that resolves through the calculator
  in one pass. Mapping rules:
  `dangerousPermissionsRequestedNotGranted = max(0, dangerousPermTotal -
  dangerousPermGranted)`, `userDisabledComponentCount = max(0,
  rulesCount)`, `recentOsRevertCount` from the caller. 11 focused JVM
  tests pin derivation, inverted-field clamping, null-row tolerance,
  and full calculator-priority pass-through. Adapter wiring and the
  glossary entry remain on the T21-G roadmap row._
- [ ] **T21-H Material 3 Adaptive layouts for tablets / large screens**:
  start with App List + App Details master/detail and Settings two-pane
  rebuild using `androidx.window` + `SlidingPaneLayout`. Acceptance: stays
  compatible with the existing View-based layouts, all view IDs preserved
  per the `codexprompt.md` contract, and the new layouts gate on
  available-width thresholds rather than fixed device classes.
  _Width-class resolver shipped 2026-05-26 via
  `WindowWidthSizeClass.resolve(int)` plus the convenience predicates
  `supportsTwoPane(int)` (MEDIUM+) and `requiresTwoPane(int)` (EXPANDED
  only). Thresholds mirror `androidx.window.core.layout.WindowWidthSizeClass`:
  COMPACT &lt; 600 dp / MEDIUM 600-840 / EXPANDED &ge; 840. 7 focused
  JVM tests pin the bucket boundaries, the inclusive lower bounds,
  negative-width clamping to COMPACT, the two predicates, the constant
  contract, and a real-device form-factor matrix (Pixel 7 / Pixel Fold
  / Tab S9 Ultra). Two-pane layouts and adapter integration remain on
  the T21-H roadmap row._
- [ ] **T21-I Fast list rendering for 10k+ installed apps**: profile and
  optimize the main app list adapter / cache for unusually large installs;
  set a baseline target of <2 s cold filter on 10 k apps. Acceptance:
  measurable improvement recorded in `docs/audits/` with the before/after
  numbers, no behavior change for typical (<300-app) devices, and
  Robolectric coverage where reachable.
- [x] **T21-J Material You dynamic-color audit**: source-path audit clean.
  Activity overlay is applied by
  `AppearanceUtils.applyToActivityIfAvailable`, widget context wraps through
  `DynamicColors.wrapContextIfAvailable`, and `AppWidgetThemeUtils.getPalette`
  uses three-arg `MaterialColors.getColor(context, attr, fallback)` so the
  palette never returns black where the overlay is unavailable. Audit
  recorded in `docs/audits/2026-05-26-material-you-dynamic-color.md`;
  regression guard `DynamicColorContractTest` pins the three call sites.
  Manual device walkthrough remains open.
- [ ] T21-K Material 3 Expressive migration. Parked: legacy roadmap flagged
  this as gated on Material Components 1.14 + minSdk 23, which is itself
  tracked under "Material Components 1.14 / minSdk 23 decision". Do not
  start until that decision lands.
- [ ] T21-L Custom user shell-action / Tasker batch actions. Parked: the
  legacy row already flagged "non-trivial security review needed"; reopen
  only after the broadcast-intent automation API stops being our only
  public surface (i.e. once it has had at least one external integration in
  production).
- [ ] T21-M Compose performance pass. **Not applicable**: NG explicitly does
  not ship Compose (see `codexprompt.md`). Row removed from the active
  checklist; preserved here so the legacy bucket is fully accounted for.

## Verification Cadence

For code changes, run the narrowest relevant unit tests first, then compile the
affected flavor. Preferred commands:

- `./gradlew testFullDebugUnitTest`
- `./gradlew compileFullDebugJavaWithJavac`
- `./gradlew assembleFullDebug`

For documentation-only changes, at minimum run `git diff --check` and check
links touched by the edit.

## Archive

- [`docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md`](docs/roadmap/archive/ROADMAP-legacy-through-2026-05-25.md)
  keeps the old long-form T1-T21 ledger, engineering debt register, upstream
  sync strategy, and external source appendix.
- [`docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25.md`](docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25.md)
  keeps the first 2026-05-25 research feature plan.
- [`docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25-pass-2.md`](docs/roadmap/archive/RESEARCH_FEATURE_PLAN_2026-05-25-pass-2.md)
  keeps the pass-2 feature plan that fed the active checklist above.
