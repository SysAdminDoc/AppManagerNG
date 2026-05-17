<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# AppManagerNG minSdk 21 Dependency Ceiling Ledger

**Status:** Policy document — update with every dependency bump.
**Last reviewed:** 2026-05-16
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-23 / T4 / Now row "MinSdk-21 Dependency Ceiling Ledger"; closes the long-running Material 1.14 row from iter-19.
**Audience:** AppManagerNG contributors changing `versions.gradle`; reviewers of upgrade PRs; future maintainers deciding whether to raise the minSdk floor.

---

## TL;DR

- AppManagerNG ships with `min_sdk = 21` ([`versions.gradle:5`](../../versions.gradle#L5)). The product explicitly supports old power-user devices that no longer get OTAs but still benefit from a modern package manager / debloater / backup tool.
- Several upstream AndroidX, Material, and Jetpack lines have **already cut their last API-21–22-compatible version**. This document is the running ledger of those ceilings, so a maintainer running "bump everything to latest" never silently raises the minSdk floor.
- Bumping the floor is a one-way door: every device on API 21–22 stops receiving updates and stops being able to install the new release. Do not raise it as incidental cleanup — see "How to change the floor" below.

## The minSdk-21 contract

AppManagerNG keeps API 21 (Android 5.0 Lollipop) support because:

1. **Forked product promise.** Upstream App Manager has supported API 21 since fork baseline; raising it would be a visible regression for the same user segment NG advertises to.
2. **Power-user devices stay in service longer.** A custom-ROM Galaxy Note 4, a rooted Nexus 7, or a Shizuku-via-wireless-debug tablet running a vendor ROM stuck on API 22 is precisely the audience NG is for. They cannot leave the platform; we should not leave them.
3. **Cost is bounded.** The shared-effort drag is mostly already paid: AppCompat, RecyclerView, and the bulk of our UI work back to API 21 without per-call gating, and the few API-23+-only platform calls already sit behind `Build.VERSION.SDK_INT` guards.
4. **No telemetry obligation.** [`CONTRIBUTING.md`](../../CONTRIBUTING.md) explicitly notes minSdk 21 as a project constraint maintained unless a roadmap item explicitly changes the support contract.

## Current dependency ceiling ledger

The table below lists every dependency in [`versions.gradle`](../../versions.gradle) that has a known minSdk floor ahead of where AppManagerNG ships. "Pin reason" is the last commit / comment trail that froze the version; "Action when floor moves" is the maintainer step required if AppManagerNG ever raises `min_sdk` past the floor.

| Dependency | Current pin | Next-version minSdk | Pin reason | Action when minSdk moves |
|------------|-------------|---------------------|------------|--------------------------|
| `androidx.activity:activity` | 1.11.0 | 23 in 1.12.0 ([release notes](https://developer.android.com/jetpack/androidx/releases/activity)) | Comment in [`versions.gradle:14`](../../versions.gradle#L14): "API 21-22 support dropped in 1.12.x" | Bump to current `1.12.x` line; verify predictive back + activity-embedding code paths against the new API requirements. |
| `androidx.biometric:biometric` | 1.4.0-alpha04 | 23 in 1.4.0-alpha05 ([release notes](https://developer.android.com/jetpack/androidx/releases/biometric)) | Comment in [`versions.gradle:21`](../../versions.gradle#L21): "API 21-22 support dropped in 1.4.0-alpha05" | Decide between alpha track vs. stable; review the lock-screen-fallback strings that change at the API 23 boundary. |
| `androidx.room:room-*` | 2.7.2 | 23 in 2.8.0 ([release notes](https://developer.android.com/jetpack/androidx/releases/room)) | Comment in [`versions.gradle:36`](../../versions.gradle#L36): "API 21-22 support dropped in 2.8.x" | Re-audit migrations and the schema-export contract before the bump; new Room features (auto-migrations + room-paging-v3) only apply post-floor-bump. |
| `androidx.webkit:webkit` | 1.14.0 | 23 in 1.15.0 ([release notes](https://developer.android.com/jetpack/androidx/releases/webkit)) | Comment in [`versions.gradle:45`](../../versions.gradle#L45): "API 21-22 support dropped in 1.15.x" | Re-verify the WebView-backed surfaces (changelog, "What's new", license dialogs) against the new compat surface. |
| `com.google.android.material:material` | 1.13.0 | 23 in 1.14.0 ([release notes](https://github.com/material-components/material-components-android/releases)) | Material Components 1.14 raised minSdk to 23. Holding 1.13.0 keeps API 21 support without per-component fallbacks. | Re-verify M3 token application, dynamic color, predictive-back glue, and the V2 layouts in [`app/src/main/res/values/themes-v2.xml`](../../app/src/main/res/values/themes-v2.xml) under the new BottomSheet / Carousel APIs. |
| `androidx.window:window` | 1.4.0 | Activity Embedding / posture line; comment in [`versions.gradle:46`](../../versions.gradle#L46) notes 1.4.0 is "the latest Activity Embedding stable line compatible with minSdk 21". Newer lines tighten posture APIs. | Hold until the floor moves; track [Window release notes](https://developer.android.com/jetpack/androidx/releases/window). | Re-test the dual-pane / large-screen layouts and the foldable posture callbacks. |

Other AndroidX / Jetpack dependencies (`appcompat 1.7.x`, `androidx.core 1.17.x`, `annotation 1.9.x`, `documentfile 1.1.x`, `preferences 1.2.x`, `splashscreen 1.2.x`, `swipe_refresh 1.2.x`, `fastscroll 1.3.x`) are currently floor-compatible with API 21 and can be bumped within their major lines without touching this ledger.

Third-party libraries we vendor under MuntashirAkon forks (`apksig`, `arsclib`, `duration_picker`, `jadx`, `libadb`, `sora_editor`, `sun-security`, `unapkm`) are bound to upstream `min_sdk = 21` in their build files; bumps land alongside the upstream PR that raises their floor.

## Cascade analysis: what `minSdk = 23` would unlock

If `min_sdk` is ever taken to **23** (Android 6.0 Marshmallow), the following dep
lines can move in lockstep — the same single decision unblocks the entire pinned
cluster, which is why the Material Components 1.14 row is the natural pressure
point for the floor change (not e.g. Activity 1.12 alone).

| Pinned now | Unblocks line | Notes |
|---|---|---|
| `activity` 1.11.0 | → 1.12.x and beyond | Activity Embedding API surface cleaned up; predictive-back lifecycle additions land |
| `biometric` 1.4.0-alpha04 | → 1.4.0-alpha05+ / 1.4.0 stable | Newer biometric prompt APIs + decoupled crypto-object lifecycle |
| `room` 2.7.2 | → 2.8.x | KSP-only paths, KMP support, new auto-migration coverage |
| `webkit` 1.14.0 | → 1.15.x | WebView API additions for the in-app HelpActivity surface |
| `material` 1.13.0 | → 1.14.0+ | M3 Expressive component variants, `FocusRingDrawable`, `SplitButton` RTL |

**Decision pressure (as of 2026-05-17)**: Material Components 1.14.0 is **still
alpha** (alpha06 / alpha07 / alpha08 — see [S325]). The 1.14.0 line has not
progressed to stable; the ceiling can stay deferred until either:

1. Material 1.14 ships stable (estimated Q3-Q4 2026 based on M3 alpha → stable cadence), **and** the maintainer judges that the M3 Expressive component surface is worth the floor lift;
2. A different pinned dep cuts a release that drops API 21-22 support and forces a decision (e.g. Room 2.8.x for a security fix; AndroidX core for a CVE);
3. Google Play minSdk policy forces an industry-wide floor lift (unlikely for the NG distribution channels: F-Droid, IzzyOnDroid, Obtainium, Accrescent — none impose a Play minSdk policy).

When the decision is forced, the floor lift lands as a single `min_sdk = 23` PR that
also bumps the five pinned-cluster deps in lockstep. **Don't bump one pinned dep at a
time** — each individual bump would force `minSdk = 23` for one feature while
leaving the others frozen, fragmenting the post-floor codebase.

If the floor lift goes further (e.g. to API 24 or 26), the pinned cluster expands; a
follow-on cascade analysis should be added here at that time.

## How to change the floor

Do **not** raise `min_sdk` as part of an unrelated PR. The change is irreversible from the user's point of view (devices on the old floor lose updates the next release).

To raise the floor:

1. Open a dedicated roadmap row under the active iteration, naming the floor (`21 → 23` etc.) and the user-segment impact (count of stargazers / Obtainium installs that report old SDKs, if available).
2. Land the dependency upgrades that the new floor unlocks **as a separate PR** so the floor-raise commit only touches `versions.gradle`, `app/build.gradle`, and this document.
3. Update [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — the "Project constraints" list currently calls out `minSdk 21` explicitly.
4. Add a banner in the onboarding sheet for the prior release: users on the dropped floor should see a clear "this is the last release that runs on Android 5.0/5.1" message before they're cut off from updates.
5. Re-run the reproducible-release verification ([`scripts/verify_reproducible_release.sh`](../../scripts/verify_reproducible_release.sh)) so the floor-raise release is byte-identical across two clean builds.

To raise a *dependency* without raising the floor:

1. Confirm the dependency's `minSdkVersion` in its `AndroidManifest.xml` / `build.gradle` is `<= 21`. The "Next-version minSdk" column in the ledger is authoritative for the listed dependencies; for others, check the release notes.
2. If the dependency is ceiling-bound (listed above), do **not** bump. If you have a strong reason, follow the "raise the floor" steps instead.
3. If the dependency is not ceiling-bound, update [`versions.gradle`](../../versions.gradle), keep the existing comment style (link + reason), and run `./gradlew :app:assembleFlossDebug :app:assembleFullDebug` + `./gradlew test` + `./gradlew :app:lint` before opening the PR.

## How to discover a new ceiling

If a new dependency starts requiring a higher minSdk than 21:

1. The Gradle build typically surfaces this as `Manifest merger failed : uses-sdk:minSdkVersion 21 cannot be smaller than version N declared in library`.
2. Add a row to the ledger table above with the actual floor and the upstream release-notes link.
3. Leave a comment in [`versions.gradle`](../../versions.gradle) next to the pin matching the existing comment style.
4. Open or update the relevant ROADMAP iteration row so the upgrade is tracked, not silently lost.

## References

- [Google Play Console — minimum target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878) — confirms `minSdk` is independent of `targetSdk`; we already track `targetSdk = 36`.
- AppManagerNG roadmap row "MinSdk-21 Dependency Ceiling Ledger" — Iter-23 / T4 / Now. Source: `[S307]` (AndroidX Activity release notes API floor pressure).
- AppManagerNG roadmap row "Material Components 1.14 minSdk Bump" — Iter-19. Source: `[S201]`.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — project constraints (`minSdk 21` floor, no broad rename, no telemetry default).
