# Research — AppManagerNG

## Executive Summary
AppManagerNG is a Windows-maintained Android power-user package-manager fork built on Java, Android Views, Material Components, root/Shizuku/ADB privileged helpers, local Gradle builds, and a `floss`-first distribution model. Verified: its strongest current shape is a broad offline app-inspection/control surface with backup, permissions, app-ops, debloat, file-manager, logcat, APK inspection, and local-server tooling; the highest-value direction is to keep tightening trust, recovery, documentation truth, and host-side regression coverage before adding larger device-gated features. Priority opportunities: bound privileged shell output; replace privileged server stack traces with bounded diagnostics; rebaseline packaged manual source/generated docs truth; restore archive-link truth for tracked docs; remove stale CI/release-gate claims; extend release consistency checks to planning docs; re-enable stale ignored Robolectric fixtures; finish app/libcore stack-trace cleanup; make backup delete scope explicit; surface unsupported intent extras; cancel stale file-manager attribute jobs; ignore local JVM crash/replay logs.

## Product Map
- Core workflows: browse/filter installed apps, inspect manifests/components/trackers/signatures/SDKs, run batch package actions, manage permissions/app-ops/rules, back up/restore APK/data, inspect files/logs/APKs, and operate root/Shizuku/ADB-backed privileged actions.
- User personas: rooted-device power users, Shizuku/ADB users without root, privacy/debloat operators, Android app reverse-engineering/debug users, and maintainers doing local reproducible builds.
- Platforms and distribution: Android minSdk 21/targetSdk 36/compileSdk 37, Gradle 9.6.0 wrapper with AGP 9.2.1, Java-only app/lib modules, `floss` and `full` flavors, GitHub/Fastlane/Obtainium docs, local-only build/release workflow.
- Key integrations and data flows: PackageManager/AppOps/UsageStats/StorageStats/DevicePolicy hidden APIs through compat layers, libserver over local privileged channels, Shizuku/libsu/libadb elevation, Room preferences/state, Gson/JSON static datasets, local/SAF backup paths, generated offline manual assets, and app-private diagnostics/log exports.

## Competitive Landscape
- Upstream App Manager: does breadth well and remains the closest feature comparator; learn from accepted restore, backup, app-op, and documentation issues; avoid importing upstream privileged-channel changes without NG-specific trust-model and device validation.
- Canta and UAD-ng: make destructive debloat risk visible through package safety lists, restore warnings, and issue-driven package breakage tracking; learn from unclear uninstall-state and bootloop reports; avoid presenting success when package state cannot be verified.
- Hail: wins daily freeze/unfreeze ergonomics with a dedicated freeze model; keep that as a device-gated UX reference; avoid moving freeze launch-through into the active queue before root/Shizuku lifecycle verification is available.
- LibChecker, Inure, and AppVerifier: provide dense APK/library/verification views for power users; learn from aggregate discovery, signature, and source-truth affordances; avoid adding analytics dashboards until charting/tap-through can be visually verified on device.
- Neo Backup and Swift Backup: show backup/restore is a trust-critical niche with storage, restore, and verification complaints; learn from verify-backup requests and storage warnings; avoid custom backup-schedule expansion until NG's existing restore/delete/fixture coverage is stronger.
- AppDash: validates paid-market demand for tags, insight cards, versioned backups, widgets, and polished dashboards; learn from explicit insight-to-filter and backup-version affordances; avoid network-backed Play intelligence in the `floss` flavor.
- SD Maid SE, Amaze, and Material Files: set expectations for resilient file operations, cancellation, staged deletion, and clear recovery; learn from foreground/cancellable operation models; avoid deep file-manager service rewrites without device recovery testing.

## Security, Privacy, and Reliability
- Verified: active P1 items already cover privileged shell output bounding and `server/`/`libserver/` stack traces; duplicate security roadmap entries would fragment the same fix.
- Verified: `CHANGELOG.md:348` says packaged manuals were rebased to AppManagerNG, but source docs still route users to upstream and stale version claims: `docs/raw/en/intro/main.tex:4`, `:12`, `:40`, `:52`, `:54`, `:79`; generated XML repeats the same strings at `docs/raw/en/strings.xml:21`, `:23`, `:24`, `:26`; generated HTML is mixed, with a fork notice at `docs/raw/en/index.html:156` and stale supported version at `docs/raw/en/index.html:185`.
- Verified: tracked documentation links archive paths that are absent from tracked Git content: `README.md:96`, `ROADMAP.md:5`, and `docs/roadmap/README.md:14` reference `docs/archive/` or `docs/roadmap/archive/`, while `git ls-files docs/archive docs/roadmap/archive docs/patch-references docs/raw/changelog_old.md` returns no paths and `git status --short` reports those directories as untracked.
- Verified: local JVM crash/replay logs are not ignored: `app/hs_err_pid10832.log` and `app/replay_pid10832.log` appear in `git status --short`, while `.gitignore` has no `app/hs_err_pid*.log` or `app/replay_pid*.log` patterns.
- Verified: five class-level ignored tests still use `@Ignore("env-fixture missing pre-2026-05-25; tracked in ROADMAP.md Test Suite Hygiene")` even though the active roadmap no longer has that item: `ZipDocumentFileTest.java:32`, `ZipFileSystemTest.java:32`, `TarUtilsTest.java:37`, `OABConverterTest.java:31`, and `SettingsSearchIndexTest.java:21`.
- Verified: production `printStackTrace()` still exists outside the current server-focused roadmap item in app/libcore file, proc, logging, and UI utility paths; the active P2 item correctly scopes that cleanup.
- Missing guardrails: source-contract tests should enforce no production direct stack traces outside explicitly allowed fixtures, no stale upstream support links in packaged English manual sources, no archive links to untracked paths, and no ignored host tests without an active roadmap reason.
- Recovery and rollback needs: keep destructive package/backup flows verifiable before user-facing success, preserve local-only release verification, and keep device-gated privileged trust-model work in `Roadmap_Blocked.md` until runtime validation exists.

## Architecture Assessment
- Verified: module boundaries are clear but old: app UI/domain code and shared `libcore` Java utilities rely on Views, Java executors, hidden API stubs, and local server/common modules rather than Compose or Kotlin.
- Verified: the docs module has a source/generated asset boundary problem: `docs/raw/en/intro/main.tex`, generated `docs/raw/en/strings.xml`, and generated `docs/raw/en/index.html` can disagree, so fork identity fixes need source rebuilds plus grep/source-contract coverage rather than generated HTML edits only.
- Verified: release metadata is split across `app/build.gradle`, `versions.gradle`, README/Fastlane/docs, and planning notes; active roadmap already has the consistency-gate fix, so this pass should not duplicate it.
- Verified: `versions.gradle` documents deliberate minSdk 21 ceiling pins for Activity, Material, Room, WebKit, WorkManager, Sora editor, and biometric dependencies; large dependency upgrades require the existing minSdk policy rather than opportunistic bumps.
- Refactor candidates: shared logging/diagnostic abstraction for app/libcore utility paths; fixture-backed test-resource setup for ZIP/VFS/TAR/OAB/settings-search tests; a docs source-contract check for generated manual truth; a docs-link check that treats untracked archive links as failures; `.gitignore` cleanup for repeat JVM crash artifacts.
- Test gaps: ignored fixture tests mask file-system/archive/backup/settings-search regressions; packaged docs can drift between TeX/XML/HTML; several high-risk runtime flows remain blocked because they require rooted/Shizuku/SAF/multi-profile/device verification.
- Documentation gaps: README still has stale local/CI wording and planning surfaces have drift; active P2 roadmap items already cover those docs and gate changes, while archive-link truth is a new host-verifiable documentation gap.
- Category coverage: security, observability, testing, docs, distribution/packaging, offline/resilience, migration, and upgrade strategy map to active host-verifiable items; accessibility, i18n/l10n, mobile/form-factor, multi-user, and screenshot/visual work are already in `Roadmap_Blocked.md`; a plugin ecosystem is not recommended because local privileged APIs and `floss` constraints favor first-party modules over third-party extension loading.

## Rejected Ideas
- Broad in-app "App Manager" string sweep, source: `app/src/main/res/values/strings.xml` and `Roadmap_Blocked.md`. Reason: many references are inherited/manual/upstream-context strings; the verified user-facing regression is the packaged manual source/generated truth, not a blind rename.
- Tracking every local archive markdown file as published documentation, source: `docs/archive/`, `docs/roadmap/archive/`, and docs hygiene rules. Reason: implementation should first decide link truth versus local-only archives; committing all local archives would add stale planning surfaces.
- Deleting local top-level ignored markdown artifacts, source: `.gitignore` and `git status --ignored`. Reason: they are ignored local artifacts, not a tracked product bug; active work should focus on tracked links and generated docs.
- Compose rewrite or Kotlin-first rewrite, source: Android stack conventions and current Java/View modules. Reason: contradicts the repo architecture and would delay reliability fixes.
- minSdk 23 dependency wave, source: `versions.gradle` and `docs/policy/minsdk-21-ceiling.md`. Reason: API 21-22 support is an explicit product constraint.
- Network-backed Play intelligence in `floss`, source: AppDash feature set and fork flavor docs. Reason: `floss` expectations favor offline/no-proprietary-network behavior.
- New analytics dashboard, freeze widget, TV/D-pad pass, multi-user matrix, screenshot/Paparazzi pass, translation pipeline, and visual token polish, source: `Roadmap_Blocked.md`. Reason: already tracked behind device/design verification or explicit runtime blockers.
- Privileged-channel HMAC/TLS/native server port, source: upstream App Manager commits and `Roadmap_Blocked.md`. Reason: already blocked pending trust-model and rooted/runtime verification.

## Sources
OSS competitors and issue signal:
- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/issues/1980
- https://github.com/MuntashirAkon/AppManager/issues/1986
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1315
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1400
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/NeoApplications/Neo-Backup/issues/814
- https://github.com/aistra0528/Hail
- https://github.com/lihenggui/blocker
- https://github.com/LibChecker/LibChecker
- https://github.com/Hamza417/Inure
- https://github.com/soupslurpr/AppVerifier
- https://github.com/ImranR98/Obtainium
- https://github.com/zacharee/InstallWithOptions
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/TeamAmaze/AmazeFileManager
- https://github.com/zhanghai/MaterialFiles

Commercial, adjacent, awesome lists, and community:
- https://appdash.app/
- https://www.swiftapps.org/faq
- https://github.com/timschneeb/awesome-shizuku
- https://www.reddit.com/r/PocoPhones/comments/1ng1gyi/how_to_debloat_poco_phones_stepbystep_guide_with/

Platform, testing, dependencies, and security:
- https://developer.android.com/developer-verification
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://docs.gradle.org/current/release-notes.html
- https://robolectric.org/getting-started/
- https://junit.org/junit4/javadoc/4.13/org/junit/Ignore.html
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://docs.github.com/articles/ignoring-files

## Open Questions
- Needs live validation: whether API 21-22 usage still justifies holding the minSdk 21 ceiling after the current reliability backlog is drained.
- Needs live validation: final privileged-channel trust model for non-loopback local-server sessions.
- Needs live validation: Android developer-verification and Android 17 behavior effects on Obtainium/F-Droid-style installs for this specific package.
