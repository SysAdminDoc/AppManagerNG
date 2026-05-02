<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 `MessageQueue` Compatibility Audit

**Date:** 2026-05-02
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Now / T2 row "Android 17 MessageQueue Compatibility".
**Outcome:** ✅ **CLEAN — no remediation required.**

## Background

[Android 17 ships a lock-free `MessageQueue` implementation for `targetSdk=37` apps](https://developer.android.com/about/versions/17/behavior-changes-all). Apps that reach into `MessageQueue` private fields via reflection (commonly `mMessages`, `mNextBarrierToken`, `nativePollOnce`) will hit `IllegalReflectiveAccess` errors that escalate to crashes under the lock-free implementation.

The roadmap flagged this as a potential blocker for raising `targetSdkVersion` to 37 because root-shell polling code in App Manager-derived projects has historically reached into `Looper`/`MessageQueue` internals to coordinate process synchronization.

## Scope

Recursive search for any direct or reflective access to `MessageQueue` across every source root of AppManagerNG, excluding generated build outputs and lint caches.

Source roots searched:

- `app/src/`
- `libcore/`
- `libserver/`
- `libopenpgp/`
- `hiddenapi/`
- `server/`

## Method

```
grep -rn "MessageQueue\b" app/src/ libserver/ libcore/ server/ libopenpgp/ hiddenapi/ \
  | grep -v "/build/"

grep -rln "Looper.*reflection|getDeclaredField.*Message|HiddenApi.*MessageQueue" \
  app/src/ libserver/ libcore/ server/

grep -rln "nativePollOnce|pollOnce" \
  app/src/ libserver/ libcore/ server/
```

## Result

```
(no source matches in any of the three queries)
```

Only repository match: `server/build/intermediates/lint-cache/lintAnalyzeDebug/private-apis-18-7541949.bin` — a binary Lint analysis cache containing strings from the deprecation database, NOT source code.

The `MessageQueue` private API is not referenced anywhere in AppManagerNG's source tree. Root-shell coordination uses `libsu` shell-process IPC, not `MessageQueue` reflection.

## Conclusion

AppManagerNG declares zero direct or reflective use of `MessageQueue`. The Android 17 lock-free `MessageQueue` change has no impact on this app's runtime behaviour, and the roadmap item that gated raising `targetSdkVersion` to 37 on this audit is now closed.

The `targetSdkVersion = 37` bump itself is still gated on broader Android 17 behaviour-change coverage (`SDK_INT_FULL`, JobScheduler quota changes, broadcast priority scope, and a few other items already tracked separately in ROADMAP T2), but `MessageQueue` is no longer one of those gates.

## Ongoing guard

Any future PR that introduces `MessageQueue` reflection should be flagged in code review. The shipped Lint pipeline (`./gradlew :app:lint`) already reports private-API access when `targetSdkVersion = 37`; no additional configuration required.
