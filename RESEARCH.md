<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG
Date: 2026-08-02 — replaces all prior research.

Confidence labels: [Verified] found in the repository or an authoritative source;
[Likely] supported by reachable code and ecosystem evidence but not yet reproduced in a
release/device run; [Assumption] a design choice to validate during implementation;
[Needs live validation] requires an OEM/device, release artifact, or external service.

## Executive Summary

[Verified] AppManagerNG is a local-first, GPL-3.0-or-later Android package-management suite
with Java/XML Material Views, native/JNI support, eleven Gradle modules, `minSdk 21`, and
`floss`/`full` network boundaries. It already combines inspection, filtering, privileged
operations, APK/split installation, backups, profiles, routines, file tools, diagnostics,
and reproducible release checks. The highest-value direction is to close a few unverified
state and trust seams around data mutation and to finish data layers that already have no
UI, rather than add another broad subsystem.

Top opportunities, in priority order:

1. [P1, Likely] Serialize rules/components read-modify-write operations across all callers;
   the only current lock is caller-local in `MainPreferencesViewModel`, while
   `RulesStorageManager` instances can independently load and commit the same TSV state.
2. [P1, Verified] Add package/version/manifest-consistency preflight for APKM/APKS/XAPK
   containers; `ApkFile` already bounds archives and rejects duplicate/missing base entries,
   but its APKM classification still carries `FIXME(#227)` and does not compare every split
   against the base identity.
3. [P1, Verified] Remove the literal development-keystore credential from `app/build.gradle`;
   the keystore is ignored, but the tracked build script still embeds a reusable password.
4. [P1, Verified] Expose the existing `FilterPresetStore` through Finder so named filters can
   be saved, loaded, renamed, and deleted instead of remaining an unreachable data layer.
5. [P2, Verified] Bind an optional `FilterItem` to non-package-event profile routines; the
   architecture document names this open decision and `RoutineWorker` currently applies the
   selected profile without a target filter.
6. [P2, Verified] Turn translation missingness into a documented host-side baseline/ratchet;
   the current script warns on missing locale strings and the release gate checks regressions,
   but neither prevents new NG-owned strings from silently losing coverage.

Existing P0/device, hosted-service, dependency-gated, visual, and documentation-truth work is
already represented in `Roadmap_Blocked.md`; it is intentionally not repeated in the active
roadmap additions below.

## Product Map

- **Core workflows:** inventory and search installed packages; build Finder predicates and
  batch actions; inspect manifests, permissions, AppOps, signatures, trackers, native libraries,
  and components; install/export APK, split, OBB, and archive inputs; create/restore backups and
  profiles; freeze, debloat, schedule, and audit operations; browse files and collect diagnostics.
- **Personas:** privacy/offline Android users; root, ADB, Shizuku, Dhizuku, or privileged-server
  power users; ROM/OEM debloaters; app developers and mobile-security analysts; users managing
  multiple profiles or devices.
- **Platforms and distribution:** Android API 21+, compile SDK 37, target SDK 36, Java/XML
  Material Components, four-ABI native packaging, and `floss`/`full` product flavors. The
  repository documents GitHub/direct distribution and F-Droid/Izzy/other catalog paths; the
  hosted submission row remains external-gated.
- **Integrations and data flows:** Android `PackageManager`/`PackageInstaller`, AppOps and
  hidden-API compatibility; root/ADB/Shizuku/Dhizuku/local privileged AIDL; WorkManager routine
  execution; SAF and the app file-provider layer; Room, SharedPreferences, local files, OpenPGP,
  APK signing/metadata libraries, JNI/native helpers, and optional `full`-flavor network lookups.

## Competitive Landscape

- **[Upstream App Manager](https://github.com/MuntashirAkon/AppManager)** — does broad package
  inspection, filtering, backup, and automation well. Learn: keep Finder and inspection
  primitives composable. Avoid: importing upstream behavior without rechecking NG's local-first,
  reversible-operation contracts.
- **[Universal Installer](https://github.com/pass-with-high-score/universal-installer)** — makes
  package format, split, SDK, ABI, permissions, OBB, storage, progress, and history visible.
  Learn: put identity and consequence review before commit. Avoid: remote reputation checks or
  advanced install flags becoming implicit trust decisions.
- **[Hail](https://github.com/aistra0528/Hail)** — clearly separates root, device-owner,
  Shizuku, and Dhizuku capabilities and exposes reversible freeze automation. Learn: show the
  actual privilege mode and provide an explicit recovery path. Avoid: implying OEM-independent
  behavior where the platform cannot guarantee it.
- **[Canta](https://github.com/samolego/Canta) +
  [Universal Android Debloater NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ)**
  — make debloat recommendations approachable, warn about boot-critical removals, and support
  state rechecks/recovery. Learn: versioned safety context and post-action verification. Avoid:
  unmoderated crowdsourced votes without OEM/version context or poisoning resistance.
- **[Neo Backup](https://github.com/NeoApplications/neo-backup) +
  [SD Maid SE](https://github.com/d4rken-org/sdmaid-se)** — cover scheduled backup/restore,
  cancellation, leftover cleanup, filters, and recovery-oriented operations. Learn: stage
  destructive work and report partial outcomes. Avoid: treating a still-bootable app as the only
  place where recovery instructions exist.
- **[AppDash](https://appdash.app/) +
  [Swift Backup](https://www.swiftbackup.app/faq)** — validate demand for tags, saved filters,
  notes, history, widgets, and versioned local backup. Learn: make “what changed and what can I
  recover?” actionable. Avoid cloud/account coupling and telemetry that conflict with NG's
  offline/floss posture.
- **[Blocker](https://github.com/lihenggui/blocker/releases) +
  [LibChecker](https://github.com/LibChecker/LibChecker)** — provide detailed IFW/component
  controls and static library/native inspection. Learn: show provenance and uncertainty beside
  every analysis result. Avoid turning “no known match” into a claim that an app is clean.

## Security, Privacy, and Reliability

- [Verified] `app/build.gradle` reads an ignored `dev_keystore.jks` when present, but the
  tracked debug signing block also contains a literal `storePassword`/`keyPassword`. This is
  not a release-key leak, but it violates the repository's own no-secret guidance in
  `CONTRIBUTING.md` and makes the credential reusable by anyone who reads the source. Android's
  signing guidance keeps keystore properties outside source control and permits the normal debug
  keystore path ([Android app signing](https://developer.android.com/studio/publish/app-signing)).
- [Likely] `RulesStorageManager` synchronizes its in-memory `mEntries`, not the file-level
  read-modify-write. `MainPreferencesViewModel.applyAllRules()` documents that synchronization
  belongs in `ComponentsBlocker`, yet multiple operations construct separate mutable managers
  before committing. Parallel component/AppOps/permission updates could therefore lose one
  writer's change or expose a partially replaced TSV. This needs a failing concurrent test first,
  then a keyed per-user/per-rules-file transaction and atomic replacement.
- [Verified] `apk/ApkFile.java` rejects duplicate split names, duplicate bases, missing bases,
  malformed bounded metadata, and overlarge entry counts, while the installer separately checks
  split certificate mismatch. The remaining `APKM`/DRM-free `FIXME(#227)` path does not establish
  package name, version, split identity, or container provenance for every embedded APK before
  selection. Android's `PackageInstaller.Session` requires coherent split sets and a base when
  the package is absent ([Session reference](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session));
  APKMirror and Universal Installer expose why a bundle cannot be installed. Add consistency
  preflight without duplicating the existing certificate check.
- [Verified] Existing trust controls are strong in several areas: no app `WebView`; explicit
  `PendingIntent` mutability at factory call sites; backup exclusions for server secrets; release
  dependency floors for Bouncy Castle, Guava, and protobuf; installer transcripts/history; and
  optional network behavior isolated to `full`. Preserve these boundaries rather than adding
  mandatory cloud reputation or analytics calls. Android's exported-component guidance supports
  continuing to gate intent entry points in both manifest and code ([exported components](https://developer.android.com/privacy-and-security/risks/android-exported),
  [access control](https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components)).
- [Verified] Current Android 17 behavior changes include target API 37 requirements for local
  network access and stricter native dynamic-code loading. The project targets 36 and already has
  Android 17/device work in `Roadmap_Blocked.md`; this is a release constraint, not a duplicate
  feature row ([Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17),
  [local network permission](https://developer.android.com/privacy-and-security/local-network-permission)).
- **Recovery needs:** the new rules transaction must retain the last valid rules file on write
  failure; bundle preflight must fail before the installer session is committed; the signing fix
  must leave ordinary debug builds usable; preset corruption must remain an empty, recoverable
  state; and routine filter failures must record a skipped/failed result rather than silently
  applying the full profile.

## Architecture Assessment

- **Boundaries to improve:** extract a pure bundle-identity/preflight layer from `ApkFile`; make
  `RulesStorageManager` expose one transaction boundary around load, mutate, save, and apply;
  give Finder a small preset-facing ViewModel/controller over `FilterPresetStore`; and add a
  versioned filter reference to `ProfileTrigger` rather than embedding UI-only state in
  `RoutineWorker`.
- **Refactor candidates:** `RulesStorageManager.java` and `ComponentsBlocker.java` are the
  correct place for a shared lock/atomic writer; `ApkFile.java` is a concentration point where
  identity validation should be testable without starting an install; `FinderActivity.java` and
  `FinderViewModel.java` need preset actions; `ProfileTrigger.java`/`RoutineWorker.java` need
  schema-compatible selection semantics. Avoid broad rewrites of the existing Java/XML shell.
- **Test gaps:** add malformed/mixed-package/mixed-version/split-name and DRM-free APKM
  fixtures to `ApkFileTest`; add a parallel rules writer test that proves no lost updates or
  truncated TSV; add store/UI-facing Finder tests for save/load/rename/delete and malformed
  entries; add routine tests for no filter, matching filter, empty result, missing preset, and
  old trigger JSON; add a static build test that no tracked file contains a literal signing
  password. Android's Room guidance is a useful model for preserving schema history and testing
  every migration ([Room migration testing](https://developer.android.com/training/data-storage/room/migrating-db-versions)).
- **Release and upgrade constraints:** `minSdk 21` is intentional; current Activity, Room, and
  WorkManager upgrade lines that require API 23 cannot be adopted blindly. Keep the existing
  dependency-lock/CVE/SBOM/reproducibility gates and document any new preset/trigger schema
  version. Do not make AndroidX upgrades a roadmap item without first resolving the min-SDK
  policy ([Activity releases](https://developer.android.com/jetpack/androidx/releases/activity),
  [Room releases](https://developer.android.com/jetpack/androidx/releases/room),
  [Work releases](https://developer.android.com/jetpack/androidx/releases/work)).
- **Coverage decisions:** security and data safety are addressed by the first three additions;
  accessibility/device themes and form factors remain in `Roadmap_Blocked.md`; i18n is addressed
  by the host-side ratchet while hosted translation intake stays blocked; observability already
  has install transcripts, operation history, routine results, and release receipts; testing is
  attached to every new item; documentation truth is already a blocked cross-document task;
  distribution remains local/reproducible and `floss`-compatible; a plugin marketplace, iOS,
  desktop, and remote fleet control do not fit the Android/local-first boundary; offline
  operation, multi-user support, and privileged-mode matrices remain explicit constraints; and
  migration/upgrade work must preserve existing SharedPreferences/Room schemas.

## Rejected Ideas

- **Cloud backup, remote fleet control, or mandatory online reputation checks** — AppDash and
  Swift Backup show commercial demand, but NG's `floss`/local-first boundary and existing
  optional-network separation make this a purpose and privacy expansion
  ([AppDash](https://appdash.app/), [Swift Backup FAQ](https://www.swiftbackup.app/faq)).
- **Public plugin/extension marketplace** — Thor demonstrates the mechanism, not enough evidence
  of demand to justify a new signed-code trust boundary, compatibility policy, review process,
  and incident-response owner ([Thor](https://github.com/trinadhthatakula/Thor)).
- **Crowdsourced debloat safety scores** — Canta/UAD evidence supports warnings and curated
  definitions, not an untrusted vote database; firmware context, moderation, and poisoning
  resistance would be prerequisites ([Canta](https://github.com/samolego/Canta),
  [UAD-NG FAQ](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ)).
- **AI privacy scores, telemetry dashboards, Play watchlists, or VirusTotal-by-default** — the
  repository already has optional network boundaries and an analytics row is blocked; adding
  remote scoring would create an unverifiable classification and privacy dependency.
- **Compose rewrite, iOS/desktop ports, or Android TV as an active feature bet** — the current
  Java/XML/min-21 architecture and device/form-factor work already in `Roadmap_Blocked.md` make
  these XL scope with no evidence that they solve a current trust or workflow gap.
- **PQC/v3.2 signing display and Android 17 target migration as new rows** — both are already
  dependency/device-gated in `Roadmap_Blocked.md`; the official Android 17 announcement and the
  `PackageUtils.java` TODO are evidence for sequencing, not duplicate active work
  ([Android 17 announcement](https://android-developers.googleblog.com/2026/06/Android-17.html)).
- **VFS timestamps, receiver-flag display, and routine-history rotation now** — the TODOs in
  `io/fs/VirtualFileSystem.java`, `ActivityInterceptor.java`, and the routine architecture notes
  are real, but no comparable-product or user-signal evidence makes them more valuable than the
  six bounded items above. Revisit after the trust and preset foundations land.

## Sources

### Direct OSS
https://github.com/MuntashirAkon/AppManager
https://github.com/NeoApplications/neo-backup
https://github.com/NeoApplications/neo-backup/blob/main/FAQ.md
https://github.com/pass-with-high-score/universal-installer
https://github.com/aistra0528/Hail
https://github.com/aistra0528/Hail/blob/master/README_EN.md
https://github.com/samolego/Canta
https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ
https://github.com/d4rken-org/sdmaid-se
https://github.com/lihenggui/blocker/releases
https://github.com/LibChecker/LibChecker
https://github.com/trinadhthatakula/Thor
https://github.com/wxxsfxyzm/InstallerX-Revived
https://github.com/RikkaApps/Shizuku
https://github.com/ImranR98/Obtainium
https://github.com/soupslurpr/AppVerifier

### Commercial and adjacent
https://appdash.app/
https://appdash.app/faq/
https://www.swiftbackup.app/faq
https://www.swiftbackup.app/roadmap
https://adbappcontrol.com/en/docs/
https://github.com/awesome-android-root/awesome-android-root
https://github.com/timschneeb/awesome-shizuku

### Community signal
https://www.reddit.com/r/androidapps/comments/1r0s7fl/your_top_shizuku_applications_and_why/
https://www.reddit.com/r/androidapps/comments/1gzitpm/any_backup_app_which_back-ups_all_apps-files-using-shizuku/
https://www.reddit.com/r/androidapps/comments/1stw3mp/best-way-to-backup-restore-apks-without-root/
https://news.ycombinator.com/item?id=41616023
https://news.ycombinator.com/item?id=39730962
https://stackoverflow.com/questions/78830768/install-apk-with-packageinstaller-fails-on-android-api-35-device
https://stackoverflow.com/questions/77782143/not-getting-status-updates-from-packageinstaller
https://stackoverflow.com/questions/78865465/unable-back-up-and-restore-android-app-data-with-adb

### Platform and standards
https://developer.android.com/studio/publish/app-signing
https://developer.android.com/reference/android/content/pm/PackageInstaller
https://developer.android.com/reference/android/content/pm/PackageInstaller.Session
https://developer.android.com/google/play/publishing/multiple-apks
https://developer.android.com/guide/app-bundle/app-bundle-format
https://developer.android.com/about/versions/17/behavior-changes-17
https://android-developers.googleblog.com/2026/06/Android-17.html
https://developer.android.com/privacy-and-security/local-network-permission
https://developer.android.com/privacy-and-security/risks/android-exported
https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components
https://developer.android.com/guide/topics/ui/accessibility/testing
https://developer.android.com/training/data-storage/shared/documents-files
https://developer.android.com/training/data-storage/room/migrating-db-versions
https://developer.android.com/developer-verification/guides/faq
https://theupdateframework.io/specification/latest/
https://developers.google.com/android/binary_transparency/google_apk/verification_details

### Research, advisories, and dependency direction
https://arxiv.org/abs/2605.27667
https://arxiv.org/abs/2508.02008
https://arxiv.org/abs/2504.13547
https://conf.researchr.org/details/icse-2026/icse-2026-research-track/189/An-Empirical-Study-on-the-Robustness-of-Android-Third-Party-Library-Detection-Tools-A
https://nvd.nist.gov/vuln/detail/CVE-2026-5588
https://nvd.nist.gov/vuln/detail/CVE-2024-7254
https://github.com/advisories/GHSA-4h8f-2wvx-gg5w
https://advisories.gitlab.com/pkg/maven/com.google.guava/guava/CVE-2023-2976/
https://developer.android.com/jetpack/androidx/releases/activity
https://developer.android.com/jetpack/androidx/releases/room
https://developer.android.com/jetpack/androidx/releases/work

## Open Questions

None block implementation of the six host-verifiable additions. Device/OEM behavior, hosted
translation intake, Android 17 rollout, privileged transport, and documentation ownership remain
explicitly tracked in `Roadmap_Blocked.md`.
