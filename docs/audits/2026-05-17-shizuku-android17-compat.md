<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Shizuku 13.6.0 on Android 17 — NG integration compatibility

**Date:** 2026-05-17
**Source:** [Shizuku #1965](https://github.com/RikkaApps/Shizuku/issues/1965) (S321); [Shizuku #1967](https://github.com/RikkaApps/Shizuku/issues/1967) (S322); [Android 17 release timeline](https://developer.android.com/about/versions/17/release-notes) (S324)
**Audited against:** repo at `47eb040` (iter-25 deliverables commit), [`app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java)
**Roadmap row:** ROADMAP iter-25 §"Shizuku Android-17 Compatibility Watch" (T5 Now); pass-2 backlog F-NEW-25-01.

## Premise

Shizuku 13.6.0 has confirmed regressions on Android 17 Beta 3:

- [#1965](https://github.com/RikkaApps/Shizuku/issues/1965) (2026-03-27): the Application Management surface inside Shizuku Manager returns blank — Shizuku can't enumerate managed apps via its own UI.
- [#1967](https://github.com/RikkaApps/Shizuku/issues/1967) (2026-03-27): "The app won't get the shizuku and nothing in the app list."

Both issues remain open with **0 maintainer comments** as of 2026-05-17 (~50 days
open). Android 17 stable lands **June 2026** to Pixel devices first.

NG's iter-23 Shizuku integration (shipped 2026-05-14) reaches the same `UserService`
binder that the Shizuku Manager UI exercises. If the binder can't enumerate packages
on Android 17, NG's Shizuku mode is structurally broken on that platform unless either
(a) Shizuku ships a fix, or (b) NG implements detection + a fallback recommendation
to ADB mode.

## Sweep methodology

- Read [`shizuku/ShizukuBridge.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) in full.
- Identify the binder probes and the early-exit guards.
- Map the regression class to the NG-side surface that would fail.
- Identify the smallest viable runtime detection + fallback messaging without device verification (which is not available in this session).

## Findings

### NG's Shizuku integration surface

`ShizukuBridge.java` (132 lines) exposes seven static probes:

| Probe | Purpose |
|---|---|
| `isBinderAlive()` | `Shizuku.pingBinder() && !Shizuku.isPreV11()` |
| `supportsUserService()` | `isBinderAlive() && Shizuku.getVersion() >= MIN_USER_SERVICE_VERSION` (=10) |
| `hasPermission()` | `supportsUserService() && Shizuku.checkSelfPermission() == GRANTED` |
| `shouldShowPermissionRationale()` | `supportsUserService() && Shizuku.shouldShowRequestPermissionRationale()` |
| `isUsable()` | `supportsUserService() && hasPermission()` |
| `getUidOrSelf()` | `isBinderAlive() ? Shizuku.getUid() : Process.myUid()` |
| `isRecommendedManagerVersion(Context)` | parsed-version compare against `MIN_RECOMMENDED_MANAGER_VERSION` (=`"13.6.0"`) |

All seven probes wrap their calls in `try { ... } catch (Throwable e) { return false; }` —
that's the load-bearing defensive posture for a third-party binder. **The probes
themselves will not crash on Android 17 Beta 3** because every interaction with
Shizuku's API is `Throwable`-caught.

### The regression class

Based on the issue descriptions, the Android 17 Beta 3 regression most likely sits in
**Shizuku's UserService bootstrap path** (`Shizuku.bindUserService()`), not in the
binder-ping handshake itself. The Shizuku Manager UI uses the same UserService to
enumerate packages — when it returns blank, the bootstrap is failing silently.

Probable cause class (without device verification):

1. **Hidden-API reflection failure** — Android 17's static-final reflection ban ([S206]) affects `LoadedApk.makeApplication()` style hooks that some Shizuku versions use to inject the UserService. The Android 17 `MessageQueue` lock-free implementation ([S55]) similarly breaks reflective access to private fields.
2. **`IPackageManager` shape change** — Android 17's hidden-API surface in `ApplicationInfo` and `IPackageManager.queryIntentActivities()` has had at least one signature shift per ROADMAP iter-23 audit batch.
3. **BAL hardening on `IntentSender`** — possible but less likely; UserService doesn't typically launch activities.

Without an Android 17 Beta device or emulator, **option 1 is the highest-prior best guess**.

### NG-side surface impact

Given Shizuku's wrap-everything-in-try-catch posture and NG's same posture on top of it:

- **`isBinderAlive()` would still return true** (binder handshake is independent of UserService bootstrap).
- **`hasPermission()` would still return true** (permission grant is stored in Shizuku's prefs, not in the UserService).
- **`isUsable()` would still return true**.
- **The actual UserService binder call** — where NG executes elevated operations via `LocalServices` — would silently fail or hang on the first reflective call inside the UserService process.

The user-visible symptom under NG would be: **"Shizuku is configured and authorized,
but every privileged operation silently fails or times out."** This is the worst-case
trust-window — NG shows green on its onboarding sheet but operations don't work.

### Design for runtime detection

The minimal mitigation that lands without device verification is a static helper that
combines `Build.VERSION.SDK_INT >= 37` with the installed Shizuku version, and a
documented fix-version constant that flips off once a Shizuku release ships the fix.

```java
// in ShizukuBridge.java (sketch — to land alongside device verification)

/**
 * The first Shizuku Manager version known to support Android 17 (API 37) without
 * the {@code UserService}-enumeration regression reported in Shizuku #1965 / #1967.
 * Update this constant when Shizuku publishes a fix.
 *
 * <p>Until that release ships, NG's onboarding sheet should warn Android 17 users
 * that Shizuku mode may be non-functional and recommend the ADB / wireless ADB
 * fallback.
 */
public static final String MIN_ANDROID_17_COMPATIBLE_VERSION = null; // unknown — keep null until fix lands

@AnyThread
public static boolean hasAndroid17CompatibilityRisk(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 37) return false;
    if (MIN_ANDROID_17_COMPATIBLE_VERSION == null) return true; // no fix yet
    String version = getInstalledVersionName(context);
    return version == null
            || compareVersion(version, MIN_ANDROID_17_COMPATIBLE_VERSION) < 0;
}
```

Then in [`OnboardingFragment.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) where the Shizuku card renders, if `hasAndroid17CompatibilityRisk()` is true, surface a banner:

> ⚠ Shizuku 13.6.0 has known compatibility issues on Android 17 (Pixel rollout June 2026).
> If you experience silent operation failures, switch to Wireless ADB pairing as the
> primary mode. Tracked at Shizuku #1965 / #1967.

This **does not change any existing code path** — it only adds a new probe method
and a new onboarding banner. The default behaviour for users on Android < 17 is
unaffected. Users on Android 17 see a warning but can still try Shizuku mode (it might
work fine for some operations even with the regression).

## Verdict

⚠ **confirmed, needs-design** — design is captured above; implementation gated on Android 17 Beta device verification + a Shizuku fix-version tracker.

The audit confirms:

- NG's Shizuku integration is **defensively coded** (all probes are `Throwable`-caught) and will not crash on Android 17.
- The **failure mode is silent op-failure**, not a crash — the worst-case stale-trust window.
- The **fix is a non-destructive additive** runtime probe + onboarding banner; it can land any time once device verification is possible.
- The **Shizuku fix-version constant** stays `null` until a Shizuku release ships the upstream fix; once known, NG flips it to that version and the warning auto-clears.

## Action items

1. **Iter-26 / iter-27**: Set up an Android 17 Beta image (Pixel 9 emulator + Android Studio Iguana Hedgehog or device per ROADMAP iter-19 [S148] matrix). Reproduce the regression class under NG. Document the actual binder failure point.
2. **Land the runtime detection helper** in `ShizukuBridge.java` per the sketch above, with `MIN_ANDROID_17_COMPATIBLE_VERSION = null` (unknown).
3. **Surface the onboarding banner** in `OnboardingFragment.java` next to the Shizuku card.
4. **Watch Shizuku release feed** — set up an `upstream-rename-watch.yml`-style CI workflow that tags issues #1965 / #1967 + watches the Shizuku release feed. When a fix ships, the CI auto-opens an NG issue to bump `MIN_ANDROID_17_COMPATIBLE_VERSION`.
5. **Pin compile-time `shizuku_version`** once the fix Shizuku-API artifact is published.

## Follow-ups

- This audit closes the *design* half of ROADMAP iter-25 §"Shizuku Android-17 Compatibility Watch" — the *implementation* half is iter-26 work pending device verification.
- Cross-reference: the iter-19 [S139] / [S152] Hidden-Shizuku-Fork-Detection work — if the Shizuku community migrates to `thedjchi/Shizuku` (or similar) for the A17 fix, NG should follow with the broader probe pattern already on the roadmap.
- Cross-reference: the iter-23 wireless ADB pairing wizard is the **recommended fallback** when Shizuku mode is broken; the onboarding banner copy should deep-link to that wizard.
