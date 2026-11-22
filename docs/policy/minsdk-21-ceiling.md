<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# minSdk 21 Dependency Ceiling Ledger

Last updated: 2026-11-21.

This ledger tracks every dependency pinned to its last API-21-compatible
release. If any of these pins become untenable (security advisory, blocking
feature), the forced-decision triggers in
[`2026-05-26-minsdk-23-decision.md`](2026-05-26-minsdk-23-decision.md) fire
and the floor lift proceeds.

## Machine-checked policy

The source of truth for the pinned cluster is
[`minsdk-21-ceiling.json`](minsdk-21-ceiling.json). It records the project
floor, each version variable, the last allowed version, and the first known
incompatible release. The release gate reads this file and `versions.gradle`
locally, so it does not need network credentials.

Run the normal check with:

```text
scripts/verify-dependency-floor.sh
```

Before changing a dependency, inspect each resolved AAR manifest by passing a
candidate. The candidate name is included in a failure message when its
manifest requires a newer API level:

```text
scripts/verify-dependency-floor.sh --candidate "Material Components=build/aar/material.aar"
```

The checker reads both text and compiled Android binary manifests. A candidate
that declares `minSdkVersion` 23 or higher fails before the dependency can be
bumped. Tests cover the allowlist, malformed archives, text manifests, and
compiled manifest attributes.

## Pinned cluster (minSdk 23 unlocks all simultaneously)

These dependencies dropped API 21-22 support in the listed version. They are
pinned in [`versions.gradle`](../../versions.gradle) to the last compatible
release.

| Dependency | Pinned version | First minSdk-23 version | What unlocks |
|---|---|---|---|
| **Material Components** | 1.13.0 | 1.14.0 | SplitButton, FocusRingDrawable, expressive typography, motion-token refresh. This is the ceiling dependency. Unblocking it lets the rest move in lockstep. |
| **Activity** | 1.11.0 | 1.12.x | Compose-focused APIs (limited non-Compose benefit for NG). |
| **Biometric** | 1.4.0-alpha04 | 1.4.0-alpha05+ | Stable BiometricPrompt API surface, key-attestation improvements. |
| **Room** | 2.7.2 | 2.8.x | KMP support, performance improvements. No security-relevant changes at time of writing. |
| **WebKit** | 1.14.0 | 1.15.x | CrUX-only WebView APIs. Minimal impact for NG's limited WebView use. |
| **Sora Editor** | 0.24.6 | 0.25.x+ | Newer editor APIs. 0.24.6 is the final API-21-compatible release. |
| **WorkManager** | 2.10.5 | 2.11.x | minSdk 23 required. Keep 2.10.x for API 21-22 compatibility. 2.11.1 and 2.11.2 stop the network constraint passing while Android blocks the app's network (b/465016918, b/445324855). Scheduled backups check for that themselves in `AutoBackupWorker.hasUsableNetwork` on every Android version. 2.11.2 also keeps periodic work that throws on its schedule (b/443879071). |
| **AndroidX Core** | 1.17.0 | 1.19.0 | core-ktx 1.19.0 raises minSdk to 23. Core (non-ktx) 1.17.0 works with compileSdk 37. |

## Not pinned (compatible with minSdk 21)

These dependencies currently support API 21 and do not gate the floor
decision. They are bumped on their normal cadence.

- AppCompat 1.7.1
- Annotation 1.9.1
- BouncyCastle 1.84
- Desugar JDK Libs 2.1.5
- DocumentFile 1.1.0
- Gson 2.14.0
- HiddenApiBypass 6.1
- libsu 6.0.0
- Preferences 1.2.1
- SplashScreen 1.2.0
- SwipeRefreshLayout 1.2.0
- Window 1.4.0
- Guava 32.1.3-android
- All native/vendored deps (apksig, libadb, jadx, ARSCLib, etc.)

## Above the floor, behind a version check

These libraries declare a higher `minSdkVersion` than the app. They don't
gate the floor decision because the manifest lists their packages in
`tools:overrideLibrary` and every call into them sits behind an `SDK_INT`
check, so an API 21-22 device never loads their classes.

| Dependency | Version | Declared minSdk | Guard |
|---|---|---|---|
| **Shizuku API** (`dev.rikka.shizuku`) | 13.1.5 | 24 | `ShizukuBridge.isBinderAlive()` returns false below `SDK_INT` 24, so nothing reaches the Shizuku binder there. |
| **Dhizuku API** (`io.github.iamr0s:Dhizuku-API`) | 2.7.0 | 23 | Only `DhizukuApiClient` touches the library, and `DhizukuSession` creates it only when `SDK_INT >= 23`. On Android 5.x the Dhizuku status reads "needs Android 6.0 or later". |

Dhizuku decision (2026-11-21): the Dhizuku-API 2.7.0 AAR manifest declares
`minSdkVersion` 23, and the Dhizuku app itself supports Android 6.0 and later.
An older ROADMAP note assumed Android 8.0, which matched neither artifact, so
NG's Dhizuku floor is API 23. Raising the app floor for one optional
privilege path would cost API 21-22 users everything else, so the library goes
in `overrideLibrary` instead. The checksums in
`gradle/verification-metadata.xml` match the `.sha256` files Maven Central
publishes beside the AAR and its Gradle module file.

## Trigger status

As of 2026-06-13, **no forced-decision trigger has fired**:

- [ ] Security advisory in a pinned-cluster dep requiring an unpinnable bump
- [ ] NG feature hard-blocked on a 1.14.0+ Material component with no polyfill
- [ ] API 21-22 emulator images dropped from CI (Android SDK / AGP)
- [ ] External integrator reports real-world minSdk 23 dependency

The recommendation remains: **hold minSdk 21 through v0.7.x**. Next
scheduled review: **2026-09-01** (see decision memo).

## How to use this ledger

1. **Before bumping a pinned dep**: check this table. If the bump crosses the
   minSdk-23 boundary, do not land it. Evaluate whether a
   forced-decision trigger has fired per the decision memo.
2. **When a new dep is added**: check its minSdk requirement. If it requires
   23+, add it to the pinned cluster table and note the minimum version that
   supports API 21.
3. **When a trigger fires**: open the decision memo, file the floor-lift
   roadmap row, and follow the plan in the "When the decision flips" section.
