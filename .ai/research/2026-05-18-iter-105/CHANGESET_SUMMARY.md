# Iter 105 Changeset Summary

## Roadmap item

T8 - Per-App Rollback / "Revert All Changes"

## What changed

- Added `PerAppRollbackManager`, a history-backed rollback planner that scans successful `batch_ops` history newest-first and emits one-target inverse `BatchQueueItem`s for the selected package/user.
- Wired App Details overflow to a confirmation-gated "Revert AppManager changes" action.
- Supported automatic inverses for freeze/unfreeze, tracker and component block toggles, explicit permission grant/revoke rows, AppOps reset-to-default, background-disable AppOps reset, and network-policy reset.
- Kept destructive or state-poor cases in manual-review accounting instead of pretending they are safely reversible.
- Added focused Robolectric/JVM coverage for ordering, permission/network policy inversion, AppOps defaults, and manual-only row handling.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.history.ops.PerAppRollbackManagerTest --console=plain`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

