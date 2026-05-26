<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# 05 — Routine scheduler

Architecture note for the Routine Operations / Scheduler feature (ROADMAP
**T8**, upstream issue [#61], 21 reactions). The data layer landed in
iter-145; the NF-09 executor/UI slice landed on 2026-05-26. This doc records
the shipped shape and the remaining validation gaps.

## 1. What exists today

| Component | File | Role |
|---|---|---|
| Trigger value type | `profiles/trigger/ProfileTrigger.java` | Immutable record. Five `Type` constants: `TYPE_TIME_OF_DAY`, `TYPE_ON_CHARGING`, `TYPE_ON_NETWORK_WIFI`, `TYPE_ON_NETWORK_ANY`, `TYPE_ON_BOOT`. Carries opaque `profileId` plus optional `hourOfDay` / `minuteOfHour` for time triggers. UUID id; created-at timestamp. |
| Trigger store | `profiles/trigger/ProfileTriggerStore.java` | SharedPreferences-backed JSON-array store. `all / find / forProfile / put / remove / removeForProfile / toggleEnabled / setEnabled / hasAnyEnabled`. Mirrors the `AppTagStore` shape from NF-08. |
| JSON round-trip helpers | `ProfileTrigger.toJson()` / `ProfileTrigger.fromJson()` | Type discriminator is the lowercase-snake-case string returned by `typeAsString(int)`; `parseTypeString` is the inverse. Adding a new type appends a case to both. |
| Scheduler bridge | `profiles/trigger/RoutineScheduler.java` | Maps enabled triggers to unique WorkManager requests, handles boot-trigger one-shots, stores last-run diagnostics, and formats schedule labels for UI. |
| Worker | `profiles/trigger/RoutineWorker.java` | Resolves the trigger/profile, starts `ProfileApplierService` with `BaseProfile.STATE_ON`, records the last result, and disables orphaned/failing triggers. |
| Boot plumbing | `self/BootReceiver.java` + `AndroidManifest.xml` | Existing receiver now re-applies periodic schedules after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, then enqueues enabled boot triggers on boot. |
| Profile UI | `profiles/ConfPreferences.java` + `preferences_profile_config.xml` | Profile editor Schedules row lists trigger state/last run, adds the five trigger types, and supports enable/disable/delete actions. |
| Tests | `ProfileTriggerStoreTest`, `RoutineSchedulerTest`, `RoutineWorkerTest` | Store persistence, timing/constraint mapping, stable input data, worker no-op, and missing-profile disable paths. |

## 2. Worker side

`Constraints.Builder` maps `ProfileTrigger.Type` to the existing
`AutoBackupScheduler.NETWORK_*` shape:

| Type | Constraint |
|---|---|
| `TYPE_TIME_OF_DAY` | None; periodic 24h request whose initial delay aligns to the next `hourOfDay:minuteOfHour`. |
| `TYPE_ON_CHARGING` | `.setRequiresCharging(true)`; periodic 15-min request (the WorkManager minimum). |
| `TYPE_ON_NETWORK_WIFI` | `.setRequiredNetworkType(NetworkType.UNMETERED)`; periodic 15-min. |
| `TYPE_ON_NETWORK_ANY` | `.setRequiredNetworkType(NetworkType.CONNECTED)`; periodic 15-min. |
| `TYPE_ON_BOOT` | One-shot `OneTimeWorkRequest` enqueued from `BootReceiver` after boot. |

`RoutineWorker` intentionally returns `Result.success()` for removed, disabled,
or orphaned triggers. The user-visible failure state is persisted through
`RoutineScheduler.recordRunResult(...)`; repeated failure loops are avoided by
disabling missing-profile and exception paths before returning success.

## 3. Boot trigger plumbing

```xml
<receiver android:name=".self.BootReceiver"
          android:enabled="true"
          android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
    </intent-filter>
</receiver>
```

`MY_PACKAGE_REPLACED` is critical: it covers the case where AppManagerNG
updates on-device. Without it the user's persisted periodic triggers go silent
until the next reboot.

The receiver re-walks the trigger store and re-enqueues every enabled trigger
through `RoutineScheduler.applyAll(context)`. On `BOOT_COMPLETED` it also calls
`RoutineScheduler.enqueueBootTriggers(context)` so boot-only triggers run once.

`LOCKED_BOOT_COMPLETED` is intentionally not registered in the shipped slice:
the trigger store and profile definitions are credential-encrypted app prefs,
so direct-boot execution would not have reliable profile data until a separate
device-protected migration lands.

## 4. Profile editor Schedules UI

The shipped surface is inside the profile configuration screen:

```
Profiles › <Profile name> › Schedules
  ┌──────────────────────────────────────────────┐
  │ ⏰ Every day at 03:00                  [on] │
  │      Time of day                              │
  ├──────────────────────────────────────────────┤
  │ 🔌 When charging                        [on] │
  │      On charging                              │
  ├──────────────────────────────────────────────┤
  │ ▶ Add trigger                                 │
  └──────────────────────────────────────────────┘
```

The "Add trigger" sheet has five rows (one per `ProfileTrigger.Type`); time-of-day
opens an Android `TimePickerDialog`; the other four are immediate adds.

Implementation notes:

- The profile config screen is already a `PreferenceFragmentCompat`; the new
  Schedules row opens a Material list dialog instead of adding another nested
  fragment.
- Each stored trigger is shown with a formatted title, enabled/disabled state,
  and last-run result.
- Tapping a trigger opens actions for enable/disable, delete, and close.
- Enabling/disabling routes through `ProfileTriggerStore.setEnabled(...)` and
  `RoutineScheduler.scheduleOrCancel(...)`.
- The screen lives in the Profile editor, not Settings root, because triggers
  belong to a profile.

## 5. Open decisions

1. **Trigger-bound app filter** — can a trigger carry an extra
   `FilterItem` that, when set, applies the profile only to apps matching
   the filter? This pairs neatly with NF-08 tags ("Freeze every `:throwaway`
   app at 11pm"). The data shape would extend `ProfileTrigger` with an
   optional serialised `FilterItem` JSON. Parking this until the basic
   five trigger types are validated; adding it later is purely additive.
2. **Maximum trigger count per profile** — five (one per type)? Unlimited?
   The current `ProfileTriggerStore` has no cap; the UI should soft-cap at
   a reasonable number for readability.
3. **History rotation** — NF-09 records only the last result per trigger in
   `profile_trigger_runs` SharedPreferences. A Room `routine_history` table is
   still the right shape if users need multiple past runs or exported history.

## 6. Verification

- JVM/Robolectric: `ProfileTriggerStoreTest`, `RoutineSchedulerTest`, and
  `RoutineWorkerTest` cover set-enabled persistence, WorkManager interval /
  constraint mapping, stable input data, disabled-trigger no-op, and
  missing-profile disable behavior.
- Compile: `compileFullDebugJavaWithJavac` passed after the NF-09 executor
  landed.
- **Pixel 9a (Android 17)** — Time-of-day trigger fires within 5 minutes
  of the configured time.
- **Samsung S25 Ultra (One UI 8.x)** — On-charging trigger fires within
  15 minutes of cable insertion.
- **Moto g22 (Android 12, Unisoc T606)** — `BOOT_COMPLETED` receiver
  registers; reboot fires the trigger after device unlock.
- **Galaxy A57 (One UI 8.5, aggressive battery)** — Self-battery-
  optimization exempt before scheduling; otherwise periodic requests get
  killed.

## 7. Cross-references

- iter-92 → iter-99 — `AutoBackupScheduler` / `AutoBackupWorker` (the
  template for `RoutineScheduler`).
- iter-145 — `ProfileTrigger` + `ProfileTriggerStore` (the data layer this
  feature builds on).
- 2026-05-26 NF-09 — `RoutineScheduler`, `RoutineWorker`, boot re-apply, profile
  editor schedule UI, last-run diagnostics, and disable-on-failure behavior.
- `.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md` §"NF-09 next-slice
  suggestion" — pre-implementation plan superseded by this implementation note.
- ROADMAP **T8** — Routine Operations / Scheduler row.
