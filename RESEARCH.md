<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research - AppManagerNG

Pass date: 2026-07-12. Baseline: v0.6.5 (versionCode 13), compileSdk 37 / targetSdk 36 /
minSdk 21. This pass re-diffs against upstream App Manager **v4.1.0 (shipped 2026-06-29)**
and against Android 17 (API 37), which reached **stable in June 2026**.

## Executive Summary

AppManagerNG is a GPL Android package-manager fork for power users who inventory, install,
freeze, archive, back up, restore, block, and audit apps across normal, root, ADB, Shizuku,
and Dhizuku paths. The prior research cycle (through 2026-07-02) was fully drained into
v0.6.4/v0.6.5 — privileged-server secret exclusion, port-rebind reconciliation, interceptor
recovery, structured logging, the SDK36/JDK21 test matrix, and runtime system-feature truth
all shipped. The project is strongest where it keeps privileged work offline-first, testable,
and reversible.

The one high-value regression risk introduced by the calendar: **Android 17 is now stable and
`getInstalledPackages` returns a new paginated `PackageInfoList` type on API 37.** NG's
`PackageManagerCompat` has no API-37 branch (highest TIRAMISU branch), so on Android 17 the
reflected `.getList()` call returns the wrong type / an empty set — an **empty main app list**.
Upstream fixed this in commit `836c7248ea`; the structural port (hidden-API stubs + an
`SDK_INT >= 37` branch) is host-buildable and unit-testable, with only runtime confirmation
device-gated. This is the single highest-value item this pass.

Dependency posture is verified clean (no CVE action). The competitive "rule/policy engine"
the whole field is converging on is already largely built here (profiles + routine triggers +
rules export/import + tags), so the remaining opportunities are targeted upstream ports and an
Android-17 targetSdk-readiness consolidation, not new subsystems.

Top opportunities (priority order):
1. Port upstream Android 17 `getInstalledPackages`/`PackageInfoList` enumeration fix (`836c7248ea`).
2. Consolidate and pin the Android 17 (targetSdk 37) behavior-change audit coverage to de-risk the eventual bump.
3. Evaluate/port upstream `Searchable{Single,Multi}ChoiceDialogBuilder` fix (`ca038d6611`, shared libcore/ui).
4. Confirm parity / add multi-user coverage for the non-default-user inactive-app check (`916eeb85d5`).

## Product Map

- Core workflows: app inventory/search/filter/sort, app details, APK install/export/verify,
  Activity Interceptor command generation, backup/restore/conversion, archive/freeze/unfreeze,
  component/app-op/permission rules, debloat guidance, profiles + routine triggers, file
  management, local docs, and privilege health diagnostics.
- User personas: rooted power users, Shizuku/ADB/Dhizuku users without root, ROM/device
  maintainers, privacy auditors, APK/app developers, offline FLOSS users.
- Platforms and distribution: Android minSdk 21 / targetSdk 36 / compileSdk 37, FLOSS+FULL
  flavors, Java/Kotlin Android Views, Material Components 1.13.0, Gradle 9.6.1 / AGP 9.2.1,
  native + server helper modules, GPL-3.0-or-later.
- Key integrations and data flows: PackageManager/PackageInstaller, root/libsu, Shizuku,
  ADB pairing/local server, Dhizuku, app archiving, Room metadata, backup archives/manifests,
  OpenPGP/Bouncy Castle, tracker/library scanners, optional FULL-flavor network sources.

## Competitive Landscape

- **Upstream App Manager (v4.1.0, 2026-06-29):** breadth benchmark; the release is mostly a
  4.0.x rollup NG already tracks, but carries three host-relevant fixes worth porting — the
  Android 17 enumeration fix (`836c7248ea`), a Searchable choice-dialog fix in shared libcore/ui
  (`ca038d6611`), and a non-default-user inactive-app fix (`916eeb85d5`). Avoid blind ports of
  the visual/scroll commits (NG already reworked scroll-restore and the Log Viewer).
- **Hail (1.10.0) + Process Warden + blocker:** the field is converging on a rule/policy layer
  (rule-based auto-freeze, auto-apply-on-install, "temporary lift then re-restrict", per-tag
  actions). Learn the ergonomics; NG already has the substrate (profiles, `RoutinePackageChangeReceiver`,
  rules export/import, tags) so this is extension work, not a new subsystem. Avoid untestable
  device-coupled features — keep privileged apply behind the existing Ops adapter.
- **Canta (3.2.2) / Thor / UAD-NG (1.2.0):** debloat lesson remains reversible, risk-labeled,
  pinned presets with a guaranteed restore path. NG already ships risk chips + dependency edges;
  avoid mutable/auto-fetched safety data in FLOSS builds (already a rejected idea — pinned data
  contract only).
- **Neo Backup (8.3.15):** users want rolling/versioned/mirror backups, filter-by-last-backup,
  and restore that survives a locked screen. NG's backup trust work is the durable value center;
  avoid cloud/sync expansion.
- **LibChecker (2.5.4) / Inure (build107):** dense inspection stays usable when evidence is
  grouped and source is explicit; both shipped Android 17 adaptation — a currency signal NG
  should match. Avoid duplicate dashboards where the scanner already covers detection.
- **PermissionManagerX (1.31):** focused, idempotent "already applied" feedback and per-mode
  clarity; NG should keep collapsing partial privileged failures into booleans off the table.

## Security, Privacy, and Reliability

- Verified: `PackageManagerCompat.getInstalledPackagesInternal` (app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java:173-180)
  has no API-37 branch. On Android 17 the hidden `getInstalledPackages(long,int)` returns a
  `PackageInfoList` (paginated `ParceledListSlice` subclass); the current reflected `.getList()`
  path yields an empty/wrong result → empty main list. Upstream fix: `836c7248ea`.
- Verified: dependency stack is CVE-clean at current pins. BouncyCastle 1.84 is the latest tag
  and already carries the 2026 OpenPGP/timing fixes; gson 2.14.0, Guava 32.1.3, zstd-jni 1.5.7-11,
  libsu 6.0.0, Room 2.7.2 have no applicable open CVE. jadx advisories are jadx-gui only (NG ships
  jadx-core/dex-input). No security-driven bump is warranted; the weekly OWASP dependency-check
  gate remains the right guard.
- Verified: no APK Signature Scheme v3.2 / ML-DSA path exists in AOSP as of mid-2026 (only v3/v3.1);
  the existing blocked "v3.2 display" item remains correctly gated on apksig upstream, not urgent.
- Reliability: backup/restore round-trip and privileged-mode paths remain the highest device-gated
  risk (tracked in `Roadmap_Blocked.md`); nothing new host-verifiable surfaced there this pass.

## Architecture Assessment

- The A17 enumeration fix should live behind the existing compat boundary: add hidden-API stubs
  (`IPackageManagerV37`, `PackageInfoList`) mirroring upstream, a `VersionCodes` constant for 37,
  and an `SDK_INT >= 37` branch in `getInstalledPackagesInternal`. Gating behind 37 keeps ≤36
  behavior byte-identical (zero regression), and the branch selection is unit-testable with a
  fake `IPackageManager`.
- Choice-dialog fix (`ca038d6611`) touches shared `libcore/ui` builders that already exist in NG
  (`SearchableSingleChoiceDialogBuilder`, `SearchableMultiChoiceDialogBuilder`); a diff-and-port
  is low-risk and Robolectric-testable.
- Doc truth is already current: `BUILDING.rst` states JDK 21+ and documents the Robolectric
  SDK36/JDK21 requirement — no action (prior gap closed).
- Test/readiness gap: there is no consolidated assertion tying the Android 17 targetSdk behavior
  audits (static-final immutability, `System.load` read-only, MessageQueue reflection, Keystore
  key cap, implicit-URI grants) to a single regression gate, so the eventual targetSdk 36→37 bump
  lacks a host-verifiable readiness pin.

## Rejected Ideas

- targetSdk 36→37 bump itself (this pass): the behavior-change audits are host-verifiable and can
  be pinned now, but the bump's runtime effects require an API-37 device/emulator — the bump stays
  in `Roadmap_Blocked.md`; only the readiness consolidation is actionable. (Source: Android 17
  behavior-change docs.)
- Dependency/CVE-driven bumps: rejected — stack is verified clean at current pins; BouncyCastle
  1.84 is latest. (Source: GHSA/NVD advisories for BC/gson/Guava/jadx/libsu.)
- APK Signature Scheme v3.2 / PQC (ML-DSA) code path: rejected — AOSP APK signing is still v3/v3.1;
  the premise is unsubstantiated. Keep the display item blocked on apksig. (Source: source.android.com apksigning v3-1.)
- New rule/policy "engine" subsystem: rejected as largely duplicate — NG already ships profiles,
  `RoutinePackageChangeReceiver`, rules export/import, and tags; extend those, don't rebuild.
  (Source: rules/RulesExporter.java, profiles/trigger/RoutinePackageChangeReceiver.java.)
- Frozen-list export as a new feature: rejected as duplicate — rules export + snapshot import with
  selective section restore already cover portable managed state. (Source: rules/, prior snapshot work.)
- Log Viewer scroll/filter rewrite port (`936cb3021b`): rejected — NG already reworked the Log
  Viewer (regex-brick + supersede-loop fixes); porting upstream's rewrite risks conflict for
  marginal gain. (Source: upstream commit, NG CHANGELOG.)
- Compose rewrite / Material 1.14+ migration: rejected — CONTRIBUTING forbids Compose; minSdk-21
  policy pins Material 1.13.0. (Source: CONTRIBUTING.md, docs/policy/minsdk-21-ceiling.md.)
- Cloud backup/sync, plugin marketplace, broad mutable UAD ingestion: rejected as before — conflict
  with the FLOSS/offline privacy model and pinned-data doctrine.

## Sources

Upstream:
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.0
- https://github.com/MuntashirAkon/AppManager/commit/836c7248eafe5d7b73e21d919eb31c34dd06a348
- https://github.com/MuntashirAkon/AppManager/commit/ca038d6611
- https://github.com/MuntashirAkon/AppManager/commit/916eeb85d5

Platform:
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://android-developers.googleblog.com/2026/06/Android-17.html
- https://developer.android.com/guide/practices/page-sizes
- https://source.android.com/docs/security/features/apksigning/v3-1

Competitors/community:
- https://github.com/samolego/Canta
- https://github.com/aistra0528/Hail
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/LibChecker/LibChecker
- https://github.com/Hamza417/Inure
- https://github.com/lihenggui/blocker
- https://github.com/trinadhthatakula/Thor
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/RikkaApps/Shizuku/discussions/462

Security:
- https://github.com/advisories/GHSA-c3fc-8qff-9hwx
- https://nvd.nist.gov/vuln/detail/cve-2023-2976
- https://github.com/skylot/jadx/security/advisories/GHSA-hvp5-5x4f-33fq
- https://github.com/topjohnwu/libsu/releases

## Open Questions

- After the A17 enumeration port lands structurally, which single privileged mode (root vs
  Shizuku vs Dhizuku) should be the first device/emulator validation target, given the paginated
  `PackageInfoList` binder call cannot be exercised offline?
