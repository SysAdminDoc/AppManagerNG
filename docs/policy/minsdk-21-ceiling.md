<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# minSdk 21 Dependency Ceiling Ledger

Last updated: 2026-06-16.

This ledger tracks every dependency pinned to its last API-21-compatible
release. If any of these pins become untenable (security advisory, blocking
feature), the forced-decision triggers in
[`2026-05-26-minsdk-23-decision.md`](2026-05-26-minsdk-23-decision.md) fire
and the floor lift proceeds.

## Pinned cluster (minSdk 23 unlocks all simultaneously)

These dependencies dropped API 21-22 support in the listed version. They are
pinned in [`versions.gradle`](../../versions.gradle) to the last compatible
release.

| Dependency | Pinned version | First minSdk-23 version | What unlocks |
|---|---|---|---|
| **Material Components** | 1.13.0 | 1.14.0 | SplitButton, FocusRingDrawable, expressive typography, motion-token refresh. Ceiling dep — unblocking this lets the rest move in lockstep. |
| **Activity** | 1.11.0 | 1.12.x | Compose-focused APIs (limited non-Compose benefit for NG). |
| **Biometric** | 1.4.0-alpha04 | 1.4.0-alpha05+ | Stable BiometricPrompt API surface, key-attestation improvements. |
| **Room** | 2.7.2 | 2.8.x | KMP support, performance improvements. No security-relevant changes at time of writing. |
| **WebKit** | 1.14.0 | 1.15.x | CrUX-only WebView APIs. Minimal impact for NG's limited WebView use. |
| **Sora Editor** | 0.24.6 | 0.25.x+ | Newer editor APIs. 0.24.6 is the final API-21-compatible release. |
| **WorkManager** | 2.10.5 | 2.11.x | minSdk 23 required. Keep 2.10.x for API 21-22 compatibility. |

## Not pinned (compatible with minSdk 21)

These dependencies currently support API 21 and do not gate the floor
decision. They are bumped on their normal cadence.

- AppCompat 1.7.1
- AndroidX Core 1.17.0
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
   minSdk-23 boundary, do not land it — instead evaluate whether a
   forced-decision trigger has fired per the decision memo.
2. **When a new dep is added**: check its minSdk requirement. If it requires
   23+, add it to the pinned cluster table and note the minimum version that
   supports API 21.
3. **When a trigger fires**: open the decision memo, file the floor-lift
   roadmap row, and follow the plan in the "When the decision flips" section.
