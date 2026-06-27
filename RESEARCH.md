# Research — AppManagerNG

## Executive Summary
AppManagerNG is a Windows-maintained Android power-user package manager fork built on Java, Android Views, Material Components, root/Shizuku/ADB privileged helpers, local Gradle builds, and a `floss`-first distribution model. Verified: its strongest current shape is a broad offline app-inspection/control surface with backup, permissions, app-ops, debloat, file-manager, logcat, APK inspection, and local-server tooling; the highest-value direction is to tighten trust and recovery around destructive operations, backup/restore, privileged diagnostics, and host-side regression coverage before adding larger device-gated features. Priority opportunities: re-enable the five stale `@Ignore` host tests; finish direct-stack-trace cleanup outside `server/` and `libserver/`; keep the active P1 privileged-shell output cap and server diagnostic work; remove stale CI/release-gate claims; extend release consistency checks to planning docs; make backup delete scope explicit; surface unsupported intent extras; cancel stale file-manager attribute jobs; refresh Gradle to 9.6.1; defer device-only UI, multi-user, privileged, and visual work to `Roadmap_Blocked.md`.

## Product Map
- Core workflows: browse/filter installed apps, inspect manifests/components/trackers/signatures/SDKs, run batch package actions, manage permissions/app-ops/rules, back up/restore APK/data, inspect files/logs/APKs, and operate root/Shizuku/ADB-backed privileged actions.
- User personas: rooted-device power users, Shizuku/ADB users without root, privacy/debloat operators, Android app reverse-engineering/debug users, and maintainers doing local reproducible builds.
- Platforms and distribution: Android minSdk 21/targetSdk 36/compileSdk 37, Gradle 9.6.0 wrapper with AGP 9.2.1, Java-only app/lib modules, `floss` and `full` flavors, GitHub/Fastlane/Obtainium docs, no active GitHub Actions workflows.
- Key integrations and data flows: PackageManager/AppOps/UsageStats/StorageStats/DevicePolicy hidden APIs through compat layers, libserver over local privileged channels, Shizuku/libsu/libadb elevation, Room preferences/state, Gson/JSON static datasets, local/SAF backup paths, and app-private diagnostics/log exports.

## Competitive Landscape
- Upstream App Manager: does breadth well and remains the closest feature comparator; learn from accepted restore, backup, app-op, and documentation issues; avoid importing upstream privileged-channel changes without NG-specific trust-model and device validation.
- Canta and UAD-ng: make destructive debloat risk visible through package safety lists, restore warnings, and issue-driven package breakage tracking; learn from their public ambiguity around unclear uninstall state and bootloop reports; avoid presenting success when package state cannot be verified.
- Hail: wins daily freeze/unfreeze ergonomics with a dedicated freeze model; keep that as a device-gated UX reference; avoid moving freeze launch-through into the active queue before root/Shizuku lifecycle verification is available.
- LibChecker and Inure: provide dense APK/library/analytics views for power users; learn from aggregate discovery and inspection affordances; avoid adding analytics dashboards until charting/tap-through can be visually verified on device.
- Neo Backup: shows backup/restore is a trust-critical niche with many restore, storage, and verification complaints; learn from verify-backup requests and restore hang reports; avoid custom backup-schedule expansion until NG's existing restore/delete/fixture coverage is stronger.
- AppDash: validates paid-market demand for tags, insight cards, versioned backups, widgets, and polished dashboards; learn from explicit insight-to-filter and backup-version affordances; avoid network-backed Play intelligence in the `floss` flavor.
- SD Maid SE and Material Files: set expectations for resilient file operations, cancellation, and clear storage cleanup recovery; learn from foreground/cancellable operation models; avoid deep file-manager service rewrites without device recovery testing.

## Security, Privacy, and Reliability
- Verified: five class-level ignored tests still use `@Ignore("env-fixture missing pre-2026-05-25; tracked in ROADMAP.md Test Suite Hygiene")` even though the active roadmap no longer has that item: `app/src/test/java/androidx/documentfile/provider/ZipDocumentFileTest.java:32`, `app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java:32`, `app/src/test/java/io/github/muntashirakon/AppManager/utils/TarUtilsTest.java:37`, `app/src/test/java/io/github/muntashirakon/AppManager/backup/convert/OABConverterTest.java:31`, and `app/src/test/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndexTest.java:21`.
- Verified: production `printStackTrace()` still exists outside the current server-focused roadmap item in `app/src/main/java/io/github/muntashirakon/AppManager/logs/Log.java:51`, `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:621`, `PathImpl.java:1494`, `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java:123`, `ProcFs.java:170`, `ProcFs.java:189`, `ProcFs.java:242`, `app/src/main/java/io/github/muntashirakon/proc/ProcMappedFiles.java:49`, `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java:575`, `Path.java:599`, and `libcore/ui/src/main/java/io/github/muntashirakon/view/AutoCompleteTextViewCompat.java:30,45`.
- Verified: active P1 items already cover privileged shell output bounding and `server/`/`libserver/` stack traces; duplicate security roadmap entries would fragment the same fix.
- Verified: backup/delete/restore trust remains the main destructive-action risk. Active and blocked items already cover explicit backup delete scope, device round-trip restore tests, upstream restore fix ports, SAF overwrite semantics, and custom-name collision behavior.
- Missing guardrails: source-contract tests should enforce no production direct stack traces outside explicitly allowed fixtures and should keep ignored host tests from reappearing without an active roadmap reason.
- Recovery and rollback needs: keep destructive package/backup flows verifiable before user-facing success, preserve local-only release verification, and keep device-gated privileged trust-model work in `Roadmap_Blocked.md` until runtime validation exists.

## Architecture Assessment
- Verified: module boundaries are clear but old: app UI/domain code and shared `libcore` Java utilities rely on Views, Java executors, hidden API stubs, and local server/common modules rather than Compose or Kotlin.
- Verified: release metadata is split across `app/build.gradle`, `versions.gradle`, README/Fastlane/docs, and planning notes; active roadmap already has the consistency-gate fix, so this pass should not duplicate it.
- Verified: `versions.gradle` documents deliberate minSdk 21 ceiling pins for Activity, Material, Room, WebKit, WorkManager, Sora editor, and biometric dependencies; large dependency upgrades require the existing minSdk policy rather than opportunistic bumps.
- Refactor candidates: shared logging/diagnostic abstraction for `PathImpl`, `ProcFs`, `ProcMappedFiles`, `Path`, `AutoCompleteTextViewCompat`, and `Log`; fixture-backed test-resource setup for ZIP/VFS/TAR/OAB/settings-search tests; a source-contract test that separates server diagnostic work from app/libcore diagnostic work.
- Test gaps: the ignored fixture tests mask file-system/archive/backup/settings-search regressions; several high-risk runtime flows remain blocked because they require rooted/Shizuku/SAF/multi-profile/device verification.
- Documentation gaps: README still has stale local/CI wording and planning surfaces have drift; active P2 roadmap items already cover those docs and gate changes.
- Category coverage: security, observability, testing, docs, distribution/packaging, offline/resilience, migration, and upgrade strategy map to active host-verifiable items; accessibility, i18n/l10n, mobile/form-factor, multi-user, and screenshot/visual work are already in `Roadmap_Blocked.md`; a plugin ecosystem is not recommended because the local privileged APIs and `floss` constraints favor first-party modules over third-party extension loading.

## Rejected Ideas
- Compose rewrite or Kotlin-first rewrite, source: Android stack conventions and Android docs. Reason: contradicts the repo's Java/View architecture and would delay reliability fixes.
- minSdk 23 dependency wave, source: `versions.gradle` and `docs/policy/minsdk-21-ceiling.md`. Reason: API 21-22 support is an explicit product constraint.
- Network-backed Play intelligence in `floss`, source: AppDash feature set. Reason: `floss` docs and build flavor expectations favor offline/no-proprietary-network behavior.
- New analytics dashboard, freeze widget, TV/D-pad pass, multi-user matrix, screenshot/Paparazzi pass, translation pipeline, and visual token polish, source: `Roadmap_Blocked.md`. Reason: already tracked behind device/design verification or explicit runtime blockers.
- Privileged-channel HMAC/TLS/native server port, source: upstream App Manager commits and `Roadmap_Blocked.md`. Reason: already blocked pending trust-model and rooted/runtime verification.
- Vendor BZip2 TODO update, source: `app/src/main/java/org/apache/commons/compress/compressors/bzip2/BZip2CompressorOutputStream.java:106`. Reason: no verified current CVE or user-visible failure was found during this pass, and archive fixture tests should be restored first.

## Sources
OSS competitors and issue signal:
- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/issues/1980
- https://github.com/MuntashirAkon/AppManager/issues/1986
- https://github.com/MuntashirAkon/AppManager/issues/1958
- https://github.com/samolego/Canta
- https://github.com/samolego/Canta/issues/355
- https://github.com/samolego/Canta/issues/362
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/559
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1315
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1400
- https://github.com/aistra0528/Hail
- https://github.com/lihenggui/blocker
- https://github.com/LibChecker/LibChecker
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/NeoApplications/Neo-Backup/issues/814
- https://github.com/NeoApplications/Neo-Backup/issues/912
- https://github.com/rumboalla/apkupdater
- https://github.com/zacharee/InstallWithOptions
- https://github.com/Hamza417/Inure

Commercial and adjacent products:
- https://appdash.app/
- https://sdmaid.darken.eu/
- https://github.com/d4rken-org/sdmaid-se
- https://support.google.com/files/answer/9716049

Platform, testing, dependencies, and security:
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/build/releases/gradle-plugin
- https://developer.android.com/jetpack/androidx/releases/work
- https://docs.gradle.org/current/release-notes.html
- https://robolectric.org/getting-started/
- https://junit.org/junit4/javadoc/4.13/org/junit/Ignore.html

## Open Questions
- Needs live validation: whether API 21-22 usage still justifies holding the minSdk 21 ceiling after the current reliability backlog is drained.
- Needs live validation: final privileged-channel trust model for non-loopback local-server sessions.
- Needs live validation: Android developer-verification and Android 17 behavior effects on Obtainium/F-Droid-style installs for this specific package.
