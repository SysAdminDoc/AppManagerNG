<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# minSdk 21 -> 23 Decision Memo

Date: 2026-05-26.
Owners: project maintainers.
Status: **recommend deferring the floor lift through v0.6.x; reopen when a
forced-decision trigger fires (defined below)**.

## Why this memo exists

[`docs/policy/minsdk-21-ceiling.md`](minsdk-21-ceiling.md) catalogs every
pinned dependency that would unlock under a `minSdk = 23` floor. It is a
reference ledger and ends at "decision pressure," not a recommendation. The
"Material Components 1.14 / minSdk 23 decision" row on
[`ROADMAP.md`](../../ROADMAP.md) asks for an explicit sign-off. This memo
provides that recommendation and the conditions under which it should flip.

## Recommendation

**Hold `min_sdk = 21` through at least v0.6.x.** Reopen the decision when any
one of the forced-decision triggers in the last section fires.

This is not a "do nothing" recommendation: the ledger does the bookkeeping
that lets us hold the line cheaply. The forced-decision triggers ensure we
flip when the cost of holding actually rises, not on a fixed calendar.

## Pros of lifting now (`minSdk = 23` in v0.6.x)

1. **Material Components 1.14.0 unlocks** alongside Activity 1.12.x, Biometric
   1.4.0-stable, Room 2.8.x, and WebKit 1.15.x. The legacy roadmap lists
   five "Premium Polish Phase 2" surfaces (App Details / Settings / Usage v2
   layouts plus the M3 preference rows) that benefit from the new M3
   component variants (`SplitButton`, `FocusRingDrawable`, expressive
   typography). Today these surfaces are layout-staged but un-shipped because
   the M3 1.13.0 component set forces ad-hoc fallbacks.
2. **One-time review pass instead of N**: each ceiling-bound dep gets one
   floor-bump pass with co-located test work, instead of one-off "we'd love
   to bump X but it'd raise the floor" carries.
3. **CI plumbing simplifies**: instrumented tests on API 23+ can drop the
   API-21-22 conditional code paths NG currently maintains.
4. **Removes a planning block** for the T21-K "Material 3 Expressive
   migration" row, which is hard-gated on Material 1.14.

## Cons of lifting now

1. **Product promise.** NG's stated audience is "all the power, half the
   friction" *for users running ROMs that no longer get OTAs*. A custom-ROM
   Galaxy Note 4, a rooted Nexus 7, a Shizuku-via-wireless-debug tablet on a
   vendor ROM stuck at API 22 - these devices are precisely what NG is for.
   Bumping the floor cuts them off from future security and feature work.
2. **No telemetry, no data**. NG does not ship analytics. We do not know how
   many users sit on API 21-22 today. Google stopped publishing the
   Distribution Dashboard after April 2020; back then API 21 + 22 still held
   single-digit percent share, and the same user segment is exactly the
   group most likely to stay on those builds rather than buy new hardware.
   Without telemetry we cannot quantify the user impact a floor lift would
   cause, only hand-wave it.
3. **Distribution policy does not force the lift.** F-Droid, IzzyOnDroid,
   Obtainium, and Accrescent do not impose a Play-style minSdk policy. Our
   release pipeline is unaffected by Play target-SDK requirements.
4. **The 1.14.0 features NG can actually use today are bounded.** NG ships
   `androidx.recyclerview.widget` views, not Compose, and most premium-polish
   work the M3 1.14.0 line ships is Compose-flavored. The non-Compose
   subset NG would consume (FocusRingDrawable, SplitButton, motion-token
   refresh) is shippable but not a step-change.
5. **The ledger is cheap to maintain.** "Hold five pins for one decision" is
   the smallest possible cost of holding the floor. Bumping every other
   floor-compatible dep continues uninterrupted.

## Decision criteria

A floor lift becomes the right call when **at least one** of the following is
true:

- A pinned cluster dep cuts a release that contains a security advisory that
  cannot be backported (e.g. Room 2.8.x with a Room CVE, BiometricPrompt
  1.4.0 with a key-attestation fix).
- A new piece of user-visible NG work has a hard dependency on a 1.14.0+
  Material component variant that cannot be polyfilled with 1.13.0 + custom
  drawables / styling.
- A future Android platform change makes API 21-22 untestable in CI (e.g.
  Google stops publishing the API-21 emulator system image, breaks AGP
  support, or removes the SDK component from the Maven distribution). The
  Android 17 emulator gate
  ([`.github/workflows/android17-emulator.yml`](../../.github/workflows/android17-emulator.yml))
  is the canary for this trigger.
- An external integrator on the broadcast-intent automation API reports a
  real-world dependency on minSdk 23 features and provides a usage trail.

A floor lift is **not** the right call when:

- The motivation is "stay current". Currency is not a decision criterion;
  unlock is.
- A pinned dep gets a feature release that NG does not need (e.g. Activity
  1.12.x adds a Compose-only API, Room 2.8.x adds KMP, WebKit 1.15.x adds
  CrUX-only WebView APIs).
- The PR description for the bump says "while we're at it" - the floor
  change must justify itself on its own merits.

## When the decision flips, the plan is

1. Open a dedicated roadmap row under the active iteration named
   `minSdk 21 -> 23 floor lift` with the trigger that fired in the body.
2. Land the floor-bump as a single PR that touches `versions.gradle`,
   `app/build.gradle`, and [`minsdk-21-ceiling.md`](minsdk-21-ceiling.md).
   No dependency bumps in the same commit.
3. As a follow-up PR, bump the five pinned-cluster deps in lockstep:
   `material 1.13.0 -> 1.14.0+`, `activity 1.11.0 -> 1.12.x+`,
   `biometric 1.4.0-alpha04 -> 1.4.0`, `room 2.7.2 -> 2.8.x`,
   `webkit 1.14.0 -> 1.15.x`. Run the
   `./gradlew testFullDebugUnitTest` -> `compileFullDebugJavaWithJavac` ->
   `assembleFullDebug` triad after each bump, then run the Android 17
   emulator gate manually.
4. Update [`CONTRIBUTING.md`](../../CONTRIBUTING.md) (the "Project
   constraints" list currently calls out `minSdk 21`).
5. Ship a final API-21-22-compatible "this is the last release that runs on
   Android 5.0/5.1" maintenance build *before* the floor-lift release goes
   live, so devices on the dropped floor see one explicit notice.

## References

- [`docs/policy/minsdk-21-ceiling.md`](minsdk-21-ceiling.md) - dependency
  ledger and cascade analysis.
- [`.github/workflows/android17-emulator.yml`](../../.github/workflows/android17-emulator.yml)
  - Android 17 emulator gate; doubles as the API-floor canary.
- ROADMAP rows "Material Components 1.14 / minSdk 23 decision" and "T21-K
  Material 3 Expressive migration".
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) - project constraints.
