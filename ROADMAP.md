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
- [ ] **T19-B Leftover detection after uninstall**: implement an explicit
  scanner for `Android/data/<pkg>`, `Android/obb/<pkg>`, root-accessible
  `/data/data/<pkg>` stubs, `Android/media/<pkg>`, and bundled debloat-rule
  remnants. Surface from One-Click Ops alongside the existing "clear data of
  uninstalled apps" entry and from App Details when the package is detected
  as uninstalled. Acceptance: scan is privilege-aware (no privileged read of
  `/data/data` without root), the result list is exportable, and selected
  items can be cleaned in one batch with audit-log capture.
- [ ] **T19-C APK duplicate finder**: index Downloads, backup destinations,
  and external storage for `.apk` / `.apks` / `.apkm` / `.xapk` files,
  deduplicate by package name + signing cert + version code, and surface a
  cleanup action in File Manager and the backup list. Acceptance: ignores
  zero-byte and partially-downloaded files, respects user-cancelled scans,
  and records sample-set telemetry only in the operation log.
- [ ] **T19-D Backup duplicate cleaner**: detect duplicate
  `<package>@<version>@<variant>` backup archives across configured backup
  roots; offer per-pair "keep newest" or "keep largest" with manual override.
  Acceptance: extends the existing T6 retention policy, never deletes the
  only good copy when both reads succeed checksum verification, and writes
  to `op_history` like other backup actions. _Data layer shipped 2026-05-26:
  `BackupRetentionPolicy.selectVersionDuplicates` + `pruneVersionDuplicates`
  with `DuplicateKeepStrategy.NEWEST` / `OLDEST` and 6 focused JVM tests
  pinning bucket determinism, user-id splitting, missing-versionCode skip,
  and tie-break ordering. UI / op_history / "keep largest" follow-up tracked
  here._

#### T20 - Performance and profiling (system-level)

- [ ] **T20-A Perfetto system-trace export**: integrate Perfetto 54.0+ trace
  capture filtered to a target app on Android 11+; expose as an App Details
  "Export trace" action gated on Shizuku or ADB. Acceptance: trace persists
  to Downloads as `.perfetto-trace`, optionally produces a `ui.perfetto.dev`
  deep link, and the action falls back to system Developer Options when
  unavailable.
- [ ] **T20-B simpleperf CPU profile capture**: ship a "Record CPU profile"
  action in App Details that wraps `simpleperf` for a bounded sampling
  window, gated on root/Shizuku/ADB. Acceptance: output saved as flame-graph
  SVG plus a raw `perf.data` for desktop tools, action explains binary
  source/version, and capture is cancellable.
- [ ] **T20-C Memory allocations inspector**: parse `dumpsys meminfo`,
  `dumpsys gfxinfo`, and `procfs` native-memory snapshots for the target
  package and surface them in App Details. Acceptance: works on
  non-debuggable apps where the data is privileged-readable, surfaces a
  point-in-time snapshot before deciding on streaming, and notes when
  `system_server` returns truncated data.
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
- [ ] **T21-F Undo SnackBar for destructive operations**: wrap freeze,
  uninstall, force-stop, clear-data, and component-state writes in a
  short-lived "Undo" SnackBar before commit. Acceptance: cancellation skips
  the privileged write entirely, an unactioned SnackBar still records the
  operation in `op_history`, and the Snackbar duration follows the existing
  reduced-motion gate.
- [ ] **T21-G Attention badges on app list rows**: surface a tiny circular
  badge counter on rows where actionable state exists (pending permission
  grants, disabled components, recent OS revert). Acceptance: each badge
  type has a single source of truth in the existing app cache, badge
  rendering does not regress list scroll, and the meaning is documented in
  the glossary.
- [ ] **T21-H Material 3 Adaptive layouts for tablets / large screens**:
  start with App List + App Details master/detail and Settings two-pane
  rebuild using `androidx.window` + `SlidingPaneLayout`. Acceptance: stays
  compatible with the existing View-based layouts, all view IDs preserved
  per the `codexprompt.md` contract, and the new layouts gate on
  available-width thresholds rather than fixed device classes.
- [ ] **T21-I Fast list rendering for 10k+ installed apps**: profile and
  optimize the main app list adapter / cache for unusually large installs;
  set a baseline target of <2 s cold filter on 10 k apps. Acceptance:
  measurable improvement recorded in `docs/audits/` with the before/after
  numbers, no behavior change for typical (<300-app) devices, and
  Robolectric coverage where reachable.
- [ ] **T21-J Material You dynamic-color audit**: confirm both the v2
  design tokens and existing widget palette honor Android 12+ wallpaper
  derived `dynamic_*` colors and have a graceful fallback when dynamic
  colors are disabled or unsupported. Acceptance: dynamic colors disabled
  in the new high-contrast theme code path, audit doc lands under
  `docs/audits/`, and the existing M3 palette helpers gain test coverage.
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
