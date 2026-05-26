<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# 05 — Routine scheduler

Architecture sketch for the Routine Operations / Scheduler feature (ROADMAP
**T8**, upstream issue [#61], 21 reactions). The data layer landed in
iter-145; the executor remains parked on real-device validation. This doc
locks the contract so the next session can pick up without re-deriving the
shape.

## 1. What exists today (iter-145)

| Component | File | Role |
|---|---|---|
| Trigger value type | `profiles/trigger/ProfileTrigger.java` | Immutable record. Five `Type` constants: `TYPE_TIME_OF_DAY`, `TYPE_ON_CHARGING`, `TYPE_ON_NETWORK_WIFI`, `TYPE_ON_NETWORK_ANY`, `TYPE_ON_BOOT`. Carries opaque `profileId` plus optional `hourOfDay` / `minuteOfHour` for time triggers. UUID id; created-at timestamp. |
| Trigger store | `profiles/trigger/ProfileTriggerStore.java` | SharedPreferences-backed JSON-array store. `all / find / forProfile / put / remove / removeForProfile / toggleEnabled / hasAnyEnabled`. Mirrors the `AppTagStore` shape from NF-08. |
| JSON round-trip helpers | `ProfileTrigger.toJson()` / `ProfileTrigger.fromJson()` | Type discriminator is the lowercase-snake-case string returned by `typeAsString(int)`; `parseTypeString` is the inverse. Adding a new type appends a case to both. |
| Tests | `ProfileTriggerStoreTest` | Round-trip across instances, dedup by id, `forProfile` filter, `toggleEnabled` on missing, `removeForProfile` sweep, builder rejection for invalid time-of-day, type-string round-trip. |

What the iter-145 work explicitly does **not** include:

- The `RoutineWorker` (WorkManager Worker that the scheduler enqueues).
- The `RoutineScheduler` (the WorkManager-facing class that maps triggers to
  `PeriodicWorkRequest` / `OneTimeWorkRequest` with Constraints).
- The boot receiver (`BOOT_COMPLETED` → re-enqueue all enabled triggers).
- The Settings → Profiles → Schedules UI surface.
- A `RoutineSchedulerCompat` adapter that reuses `ProfileApplierService` as the
  executor.

## 2. Target shape — Worker side

```java
public final class RoutineWorker extends Worker {
    // Constructed by WorkManager via the default ctor + Context + WorkerParameters.

    @NonNull
    public Result doWork() {
        String triggerId = getInputData().getString("trigger_id");
        ProfileTriggerStore store = new ProfileTriggerStore(getApplicationContext());
        ProfileTrigger trigger = store.find(triggerId);
        if (trigger == null || !trigger.enabled) {
            return Result.success();           // trigger removed / disabled
        }
        BaseProfile profile = BaseProfile.loadProfileById(getApplicationContext(), trigger.profileId);
        if (profile == null) {
            return Result.success();           // profile deleted; trigger orphan
        }
        Intent intent = ProfileApplierService.getIntent(getApplicationContext(),
                profile.profileId, BaseProfile.STATE_ON);
        ContextCompat.startForegroundService(getApplicationContext(), intent);
        // Don't await the service; the foreground notification reports
        // completion. Returning success() unblocks WorkManager so the next
        // periodic occurrence can re-fire on schedule.
        return Result.success();
    }
}
```

`Constraints.Builder` maps `ProfileTrigger.Type` to the existing
`AutoBackupScheduler.NETWORK_*` shape:

| Type | Constraint |
|---|---|
| `TYPE_TIME_OF_DAY` | None; periodic 24h request whose initial delay aligns to the next `hourOfDay:minuteOfHour`. |
| `TYPE_ON_CHARGING` | `.setRequiresCharging(true)`; periodic 15-min request (the WorkManager minimum). |
| `TYPE_ON_NETWORK_WIFI` | `.setRequiredNetworkType(NetworkType.UNMETERED)`; periodic 15-min. |
| `TYPE_ON_NETWORK_ANY` | `.setRequiredNetworkType(NetworkType.CONNECTED)`; periodic 15-min. |
| `TYPE_ON_BOOT` | One-shot `OneTimeWorkRequest` enqueued from `BootCompletedReceiver`. |

## 3. Boot trigger plumbing

```xml
<receiver android:name=".profiles.trigger.BootCompletedReceiver"
          android:exported="true"
          android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```

`MY_PACKAGE_REPLACED` is critical: it covers the case where AppManagerNG
updates on-device. Without it the user's persisted triggers go silent until
the next reboot.

The receiver re-walks the trigger store and re-enqueues every enabled trigger
through `RoutineScheduler.applyAll(context)`. `LOCKED_BOOT_COMPLETED` is for
direct-boot-aware execution; the trigger store lives in
`Context.createDeviceProtectedStorageContext()`-backed SharedPreferences if
this is wired (a future Eng-Debt row — today the prefs are credential-encrypted
and only available after the user unlocks the device).

## 4. Settings → Profiles → Schedules UI

The mockup that makes this discoverable:

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

- The screen is a `PreferenceFragment` so it slots into the iter-143 Settings
  search index automatically.
- Each row is a `SwitchPreferenceCompat` whose checked state mirrors
  `ProfileTrigger.enabled` and routes through `store.toggleEnabled(id)`.
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
3. **History rotation** — `AutoBackupScheduler` writes the last result to
   `PREF_BACKUP_SCHEDULE_LAST_RESULT_STR`. Triggers will multiply this by
   N; a Room `routine_history` table is probably the right shape.

## 6. Verification plan when the executor lands

- **Pixel 9a (Android 17)** — Time-of-day trigger fires within 5 minutes
  of the configured time.
- **Samsung S25 Ultra (One UI 8.x)** — On-charging trigger fires within
  15 minutes of cable insertion.
- **Moto g22 (Android 12, Unisoc T606)** — `BOOT_COMPLETED` receiver
  registers; reboot fires the trigger after device unlock.
- **Galaxy A57 (One UI 8.5, aggressive battery)** — Self-battery-
  optimization exempt before scheduling; otherwise periodic requests get
  killed.
- **Robolectric Worker** — `RoutineWorker.doWork()` returns `success()` for
  every (trigger present, trigger missing, profile missing, profile present)
  combination.

## 7. Cross-references

- iter-92 → iter-99 — `AutoBackupScheduler` / `AutoBackupWorker` (the
  template for `RoutineScheduler`).
- iter-145 — `ProfileTrigger` + `ProfileTriggerStore` (the data layer this
  doc covers).
- `.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md` §"NF-09 next-slice
  suggestion" — pre-implementation plan that this doc supersedes.
- ROADMAP **T8** — Routine Operations / Scheduler row.
