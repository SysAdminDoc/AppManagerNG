<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 12 implementation for the host-verifiable startup
  watchdog foundation.
- Updated: `ROADMAP.md` marks the root/local callback hardening,
  `StartupInitState` reducer, and `SecurityAndOpsViewModel` wiring slices as
  shipped; it also adds Cycle 12 research notes, queues the Running Apps
  restore-background action next, and refreshes `Continuation State`.
- Code: `ServiceConnectionWrapper` now tolerates late root/local binder
  callbacks outside an active bind wait. `StartupInitState` models startup
  attempt ids, stages, `Ops.STATUS_*` mapping, timeout, cancel, retry, and
  stale-event rejection. `SecurityAndOpsViewModel` now owns
  `LiveData<StartupInitState>`, monotonic attempt ids, current-attempt legacy
  status passthrough, and stale-status rejection before old terminal statuses
  can drive the legacy observer.
- Tests: added focused JVM/Robolectric coverage in
  `ServiceConnectionWrapperTest`, `StartupInitStateTest`, and
  `SecurityAndOpsViewModelTest`.
- Verification: passed
  `:app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.ipc.ServiceConnectionWrapperTest --tests io.github.muntashirakon.AppManager.settings.StartupInitStateTest --tests io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModelTest`.
- Environment note: the ignored local `local.properties` was updated to
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next host-verifiable roadmap target: `P2 - Implement Running Apps
  restore-background-operation action`.
- Start by inspecting `RunningAppsViewModel.preventBackgroundRun()`,
  `RunningAppsAdapter`, AppOps mode constants, `ComponentsBlocker` persistence,
  operation-history metadata, and any existing running-apps tests.
- Implementation constraint: expose one user-facing restore action, but compute
  `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` restoration independently;
  prefer previous mode from history where available and fall back to
  `MODE_DEFAULT`.
- Verification target: focused tests for Android N vs P+ op selection,
  previous/default fallback, adapter disable-vs-restore visibility,
  ComponentsBlocker persistence, and operation-history details.
- Parked follow-ups: visible startup watchdog controls, guarded assistant
  service/broadcast trampoline, device-only startup and accessibility
  walkthroughs, distribution submissions, and Android 17 target-SDK gate.
