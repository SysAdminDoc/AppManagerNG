# Continue from iter 143 (2026-05-25)

## What shipped this iteration

v0.5.0 was cut as `versionCode 7` (Discovery & Polish). 12 user-visible
slices landed on top of the iter-91 → iter-142 batch, plus engineering
hygiene. See `CHANGELOG.md` `## v0.5.0 — 2026-05-25` for the inventory.

| Plan ID | Status | Notes |
|---|---|---|
| EI-01 / EI-02 / EI-03 | shipped | README v0.5.0 line, CLAUDE.md Status pointer, NG-native bundled changelog. |
| NF-02 | shipped | Settings -> About -> What's new + auto-display after update. |
| NF-03 | shipped | Global in-app Settings search via `SettingsSearchIndex`. |
| NF-04 | already shipped | `preferences_main.xml` already had 4 task-organized categories. |
| NF-05 | shipped (MVP) | Settings -> About -> Glossary & how-to (14 topics, searchable). |
| NF-06 | partial | Pro Mode explainer corrected; hero card deferred. |
| NF-07 | shipped | Three-tier tracker blocking intensity, honored by batch op + installer. |
| NF-08 | data layer | `AppTagStore` + `TagsOption` Finder predicate shipped. UI follow-up next. |
| NF-13 | parked | MuntashirAkon/jadx-android fork has no 1.5.5 tag. |
| NF-14 | already shipped | docs-link-check workflow already in CI. |
| NF-15 | already shipped | `*.md` glob already covers new files. |
| NF-18 | shipped | `KeyStorePasswordLifecycleTest` invariant. |
| EI-05 | shipped | Onboarding final-step "Next steps" tiles. |
| EI-06 | already shipped | OEM-protected label inline in Debloater rows. |
| EI-08 | shipped | Mode Doctor "Share with bundle" action. |
| EI-09 | shipped | Per-app rollback multi-choice preview. |
| EI-10 | shipped | Onboarding `onResume` re-runs `refreshCapabilityStatuses`. |
| Component Rules preview | shipped | New Settings -> Rules -> Component rules screen. |

## What is parked for v0.6.0

### NF-09 — Routine Operations / Scheduler (deferred to v0.6.0)

Generalising `AutoBackupScheduler` into a `RoutineScheduler` that accepts
arbitrary `ProfileTrigger` records. **Not landed in iter-143** because:

1. The right shape needs a Room migration to add a `profile_triggers`
   table (or a dedicated SharedPreferences store that scales to dozens
   of triggers). Migration validation needs a real device.
2. The new `RoutineWorker` would need to differentiate between
   one-shot, periodic, and event-triggered (boot, charging, network,
   app-foreground) invocations. The boot trigger needs a
   `BootReceiver`; app-foreground triggers need `PACKAGE_USAGE_STATS`
   and a periodic UsageStats poll because Android forbids broadcast
   for `ACTION_USER_PRESENT`-style transitions in modern API levels.
3. Settings -> Profiles -> "Schedules" sub-screen needs a list/add/edit
   surface and per-schedule history.

**Suggested next slice (small, ships independently):**

- New `profiles/trigger/ProfileTrigger` value class with a `Type` enum
  (`TIME_OF_DAY`, `ON_CHARGING`, `ON_NETWORK_WIFI`, `ON_NETWORK_ANY`,
  `ON_BOOT`).
- New `profiles/trigger/ProfileTriggerStore` (SharedPreferences-backed
  JSON, mirroring `AppTagStore` shape from NF-08).
- New `profiles/trigger/RoutineSchedulerCompat` that, given a list of
  triggers + a profile ID, enqueues a `OneTimeWorkRequest` /
  `PeriodicWorkRequest` through the existing `AutoBackupScheduler`
  infrastructure, reusing `ProfileApplierService` as the executor.
- Settings -> Profiles overflow menu entry "Triggers" that opens the
  new picker.

Defer the BOOT trigger and UsageStats foreground trigger until the
time/charging/network triggers are running cleanly.

### NF-10 — Premium Polish Phase 2 (deferred to v0.5.x or v0.6.x)

Phase 2 covers `activity_app_details_v2.xml`, `pager_app_info_v2.xml`,
`item_app_info_action_v2.xml`, `activity_app_usage_v2.xml`,
`item_app_usage_v2.xml`, `activity_settings_v2.xml`,
`activity_settings_dual_pane_v2.xml`, and the M3 preference row layouts.

**Not landed in iter-143** because:

1. `design/impl/layout/` only stages `activity_main_v2.xml` and
   `item_main_v2.xml` (Phase 1 already shipped). Phase 2 layouts have
   not been authored yet — they require designer input, not just a
   token swap.
2. The contract requires every original view ID to remain stable so
   adapters and activities can `findViewById` the same set under both
   layouts. That cannot be validated from a CI host without running
   layout-inflation tests, which need either a real device or a much
   wider Robolectric setup.

**Suggested next slice:** stage `activity_app_details_v2.xml` in
`design/impl/layout/` first (copy classic layout, apply v2 dimens /
shapes / typography), get visual sign-off, then mirror to
`app/src/main/res/layout/` behind `PREF_PREMIUM_PREVIEW_BOOL`. Same
pattern for the remaining seven surfaces.

## EI-04 / EI-07 (still open)

- **EI-04 Permission Inspector chip-row filter** — needs new toolbar
  chip row + ViewModel filter state; UI-design scoped.
- **EI-07 Scheduled-backup "Why did this skip?" bottom sheet** — needs
  per-package skip-reason logs the worker does not currently capture.

Both are P2 and can wait for a dedicated UI iteration.

## How to continue

1. Read this file and `RESEARCH_FEATURE_PLAN_2026-05-25.md` for tier
   context.
2. Pick the next P1 row (NF-09 or NF-10) and follow the "Suggested
   next slice" above.
3. When NF-08 UI surfaces (App Details tag editor, main-list tag chip)
   ship, the SharedPreferences storage is the contract — preserve
   `AppTagStore.normaliseTag` semantics so the JSON file roundtrips.
