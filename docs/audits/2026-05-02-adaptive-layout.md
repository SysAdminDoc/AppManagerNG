<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Adaptive Layout for Large Screens — Audit (Android 16 / targetSdk=36)

**Date:** 2026-05-02
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — T2 row "Adaptive Layout for Large Screens".
**Outcome:** ✅ **No compliance blockers found.** ⚠️ Layout density coverage tightened in this pass; structural master/detail patterns deferred to a dedicated UX iteration.

## Background

Android 16 / `targetSdk=36` ignores `android:screenOrientation`, `android:resizeableActivity=false`, and aspect ratio attributes (`maxAspectRatio` / `minAspectRatio`) on activities running on displays ≥ 600dp ([S44 — Android 16 behavior-changes-16](https://developer.android.com/about/versions/16/behavior-changes-16)). Apps that previously locked orientation, pinned to portrait, or refused to resize will see those declarations silently dropped on tablets, foldables, large phones in landscape, and Chromebooks.

The roadmap noted that AppManagerNG had a single `layout-w600dp/item_main.xml` shipped in v0.4.0 dev (main list only) and that a full activity-level audit was still pending.

## Scope

- AndroidManifest activity declarations (43 activities)
- All `res/values*` resource buckets
- All `res/layout*` resource buckets
- libcore/ui resources

## Method

```
# Manifest sweep — fixed orientation, resizeable=false, aspect ratio
grep -nE "screenOrientation|resizeableActivity|maxAspectRatio|minAspectRatio|configChanges" \
  app/src/main/AndroidManifest.xml

# Programmatic orientation locks
grep -rn "setRequestedOrientation" app/src/main/java/

# Layout coverage — count per qualifier bucket
ls -d app/src/main/res/layout*
ls -d app/src/main/res/values*
```

## Findings

### 1. Manifest declarations — CLEAN ✅

```
222: android:configChanges="keyboard|keyboardHidden|locale|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
231: android:configChanges="orientation|keyboardHidden|screenSize|uiMode"
```

- **Zero** activities declare `android:screenOrientation`. None are pinned to portrait or landscape.
- **Zero** activities declare `android:resizeableActivity="false"`. All activities are multi-window-friendly.
- **Zero** activities declare `android:maxAspectRatio` or `android:minAspectRatio`. All foldable / Chromebook / tablet aspect ratios are accepted.
- The two `configChanges` declarations are **positive** patterns — those activities consume rotation and screen-size changes themselves rather than restarting on every config event. Standard practice for video-player and settings-style screens.
- 43 activities total in the manifest were swept.

### 2. Programmatic orientation locks — CLEAN ✅

`setRequestedOrientation` is used in **two** places, both for **reading** the orientation declared by *other* apps' activities (in `AppDetailsComponentsFragment` for the activity-info display). Neither call sets our own activities' orientation.

```
app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsComponentsFragment.java:663
app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsComponentsFragment.java:853
```

### 3. Layout density coverage — IMPROVED THIS PASS ⚠️→✅(partial)

**Before this pass:** only `app/src/main/res/layout-w600dp/item_main.xml` had a wide-screen variant. All other layouts and all dimens-driven spacing came from the default `values/dimens.xml` and `libcore/ui/src/main/res/values/dimens.xml`. On tablet-sized devices the result was correct and usable, but visually identical to the phone layout — small icons on a large canvas.

**After this pass:** added two new resource files that propagate to every layout in the app:

- [`app/src/main/res/values-w600dp/dimens.xml`](../../app/src/main/res/values-w600dp/dimens.xml) — bumps `icon_size` (40 → 56), `main_list_icon_size` (44 → 60), `main_list_min_height` (88 → 96), `title_font` (16 → 18), `subtitle_font` (14 → 16), `app_widget_background_padding` (16 → 24).
- [`libcore/ui/src/main/res/values-w600dp/dimens.xml`](../../libcore/ui/src/main/res/values-w600dp/dimens.xml) — bumps `padding_very_large` (32 → 40), `padding_large` (24 → 32), `padding_medium` (16 → 20). `padding_small` and `padding_very_small` deliberately untouched (pixel-snap-sensitive).

`@dimen/main_list_icon_size`, `@dimen/main_list_min_height`, and friends are referenced by 10+ layouts (verified via `grep -rln @dimen/...`) — the override propagates without touching the layouts themselves.

### 4. Structural master/detail — partially shipped, more possible

- ✅ `SettingsActivity` already toggles between `activity_settings` and `activity_settings_dual_pane` at runtime when `windowWidth ≥ 2 × 450dp`. This is a good pre-existing pattern.
- ⏸ `MainActivity → AppDetailsActivity` could adopt the same master/detail split (app list on the left, details on the right) on ≥ 900dp. Not in scope for this audit pass — it's a substantial UX restructure that warrants its own iteration with motion/state design.
- ⏸ `BackupRestoreActivity`, `OneClickOpsActivity`, and other multi-step flows could similarly benefit. Same deferral.

## Conclusion

AppManagerNG **passes Android 16 / targetSdk=36 compliance** for adaptive layout: zero fixed orientations, zero resize blockers, zero aspect-ratio limits. The previously-flagged "full screen audit" pending item is now closed.

This pass also **improves visual density on ≥ 600dp displays** by adding two `values-w600dp/dimens.xml` overrides that propagate to every layout consuming those tokens — modest, low-blast-radius, no per-layout edits required.

The roadmap can split the original `Adaptive Layout for Large Screens` row into:

1. ✅ **Compliance audit** (this doc) — closed.
2. ⏸ **Master/detail UX iteration** for high-traffic flows (Main → AppDetails, BackupRestore, OneClickOps) — open as a separate ROADMAP item with its own design pass.

## Ongoing guard

Lint already warns on hard-coded orientation declarations and `resizeableActivity=false` when targeting API 36+. Any future PR that introduces those will be flagged in code review. The shipped `./gradlew :app:lint` covers this without additional config.

## Verification

`./gradlew :app:assembleDebug` succeeds with the new resources in place. The runtime impact on phone-sized devices is **zero** — the `values-w600dp` qualifier only activates when the available width is ≥ 600dp (tablets, large foldables in landscape, Chromebooks, free-form windowed mode). Phone-sized devices continue to read from `values/dimens.xml` unchanged.
