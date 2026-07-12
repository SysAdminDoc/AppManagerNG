<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions (2026-07-12)

Backing research: `RESEARCH.md` (this pass re-diffs against upstream App Manager
v4.1.0, 2026-06-29, and Android 17 / API 37, stable since June 2026). All items
below are host-verifiable (buildable + unit-testable offline); runtime confirmation
that requires an API-37 device stays in `Roadmap_Blocked.md`.

### P1

- [ ] P1 — Port upstream Android 17 `getInstalledPackages`/`PackageInfoList` enumeration fix
  Why: on now-stable Android 17, `getInstalledPackages(long,int)` returns a paginated
  `PackageInfoList` (a `ParceledListSlice` subclass); NG's reflected `.getList()` path has
  no API-37 branch, so the main app list comes back empty on API 37.
  Evidence: upstream MuntashirAkon/AppManager@836c7248ea; app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java:173-180 (highest branch is TIRAMISU); hiddenapi/ stubs.
  Touches: compat/PackageManagerCompat.java (`getInstalledPackagesInternal` — add `SDK_INT >= 37` branch), hiddenapi/ (new `IPackageManagerV37` @RefineAs + `PackageInfoList`), a `VersionCodes`/constant for API 37.
  Acceptance: a Robolectric/JVM unit test with a fake `IPackageManager` asserts the API-37 branch is selected and returns the full list for SDK_INT 37 while SDK_INT ≤ 36 behavior is unchanged; project compiles against compileSdk 37; the ≤36 path is byte-identical. (Real binder call stays device-gated — see the A17 enumeration item in `Roadmap_Blocked.md`.)
  Complexity: M

### P2

- [ ] P2 — Consolidate and pin Android 17 (targetSdk 37) behavior-change readiness
  Why: Android 17 is stable and compileSdk is already 37, but the individual A17 behavior-change
  audits (static-final immutability, `System.load` read-only, MessageQueue reflection, Keystore
  key cap, implicit-URI grants) are not tied to a single regression gate, leaving the eventual
  targetSdk 36→37 bump without a host-verifiable readiness pin.
  Evidence: RESEARCH.md Architecture Assessment; docs/audits/2026-06-13-*; developer.android.com/about/versions/17/behavior-changes-17.
  Touches: app/src/test/ (a consolidated A17 readiness assertion), docs/audits/ (refresh against stable A17), versions.gradle comment ledger.
  Acceptance: a single test/gate re-asserts each A17 targetSdk-37 audit is still clean and fails if a new violation is introduced; the readiness ledger names each audit and its status. (The bump itself stays device-gated in `Roadmap_Blocked.md`.)
  Complexity: S

- [ ] P2 — Evaluate and port upstream Searchable choice-dialog fix
  Why: upstream fixed single/multiple-choice selection behavior in the shared libcore/ui dialog
  builders NG also ships; the fix is in a shared, host-testable component.
  Evidence: upstream MuntashirAkon/AppManager@ca038d6611; libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableSingleChoiceDialogBuilder.java, SearchableMultiChoiceDialogBuilder.java.
  Touches: libcore/ui/.../SearchableSingleChoiceDialogBuilder.java, SearchableMultiChoiceDialogBuilder.java, related Robolectric coverage.
  Acceptance: NG's builders match the fixed upstream selection semantics with a Robolectric test covering the corrected single/multi-select case; no regression in existing dialog usages.
  Complexity: S

### P3

- [ ] P3 — Confirm parity for non-default-user inactive-app check
  Why: upstream fixed inactive-app detection for non-default users; NG's `ApplicationItem` already
  passes `userId` with a guard, so this needs a diff-and-confirm plus multi-user unit coverage
  rather than an assumed bug fix.
  Evidence: upstream MuntashirAkon/AppManager@916eeb85d5; app/src/main/java/io/github/muntashirakon/AppManager/main/ApplicationItem.java:353-357; compat/UsageStatsManagerCompat.
  Touches: main/ApplicationItem.java, compat/UsageStatsManagerCompat.java, unit test for non-default userId path.
  Acceptance: a unit test asserts `isAppInactive` resolves per-user for a non-default userId (no cross-user leakage); if a divergence from upstream exists it is corrected and attributed in CHANGELOG.md.
  Complexity: S
