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
- [ ] **EI-12 Code Editor line separator rewrite**: replace the TODO-backed
  regex path with a safe line-separator conversion routine.
- [ ] **EI-13 Main list select all matching active chip**: let users select the
  current filtered/chip-matched result set without selecting hidden rows.
- [ ] **EI-15 SharedPrefs atomic writes**: move SharedPrefs edits to the
  existing atomic-file pattern so failed writes do not corrupt XML.

### P3 - Advanced Inspectors

- [ ] **NF-28 Receivers send broadcast**: add a guarded send-broadcast action
  for receiver components with user/profile routing preserved.
- [ ] **NF-29 Services start/stop**: add guarded service start/stop actions with
  clear privilege and Android-version failure messages.
- [ ] **NF-27 Providers query inspector**: add a provider query UI with safe
  projection/selection inputs and exportable results.
- [ ] **NF-26 File Manager hex/binary viewer**: add a read-only binary viewer
  for files that cannot be rendered as text.
- [ ] **EI-14 BarChart manual minimum value**: support an explicit minimum axis
  value without breaking existing auto-scaling.
- [ ] **EI-16 Dex viewer API caveat**: surface the API <26 behavior caveat in
  the Dex viewer rather than leaving it as tribal knowledge.

### v0.6.0 Blockers

- [ ] **NF-09 Routine scheduler executor**: build the `RoutineWorker`, boot
  receiver, Settings UI, last-run diagnostics, and disable-on-failure behavior
  on top of the shipped `ProfileTriggerStore`.
- [ ] **NF-10 Premium Polish Phase 2**: continue the UI polish pass with
  restrained Material components, accessibility review, and no pill, oval, or
  fully-rounded text backdrops.

### Release and Distribution

- [ ] IzzyOnDroid listing.
- [ ] F-Droid listing.
- [ ] Accrescent listing.

### Platform and Accessibility

- [ ] WorkManager quota and stop-reason instrumentation for scheduled backup
  and future routine scheduling.
- [ ] High-contrast theme audit.
- [ ] 200 percent font-scale audit.
- [ ] TalkBack traversal and action-label audit.
- [ ] Reduced-motion setting audit.
- [ ] Dyslexia-font compatibility audit.
- [ ] Android 17 device or emulator verification for Shizuku and 16 KB page
  size behavior.
- [ ] Material Components 1.14 / minSdk 23 decision, tracked against
  [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md).

### Later Research Buckets

- [ ] T18 overlays, deep links, and unfreeze shortcut consolidation.
- [ ] T19 storage analysis expansion.
- [ ] T20 system profiling expansion.
- [ ] T21 remaining polish items after the v0.6.0 blockers land.

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
