# Research — AppManagerNG

## Executive Summary
AppManagerNG is a local-first Android app-management suite for advanced users who inspect packages, manage permissions/AppOps/components, run privileged operations, back up and restore state, debloat devices, automate routines, and diagnose root/ADB/Shizuku/Dhizuku modes. Verified: the project is strongest where it keeps risky operations explainable and offline-capable: the floss/full split, release CI, hidden-API boundaries, 16 KB scanning, app archiving, profile import hardening, debloat rollback hints, support bundles, and developer-verification handling are already serious. The highest-value direction is to close remaining trust gaps around privileged terminal access, snapshot restores, optional network features, support exports, release/version drift, dependency-floor drift, and exported-intent drift. Top opportunities in priority order: privileged terminal launch hardening; snapshot import preview with selective restore; release/version consistency gate; optional-network transparency ledger; support bundle preview/redaction controls; Android 16 strict-intent manifest audit; dependency floor/security-tool drift gate; translation and pseudolocale quality gate.

## Product Map
- Core workflows: package list/search/filter; package detail inspection; install/uninstall/archive; permission, AppOp, component, tracker, and rules management; backup/restore/snapshot import/export; debloat/freeze/profile/batch/routine automation; troubleshooting and privilege-mode diagnostics.
- User personas: rooted Android power users; privacy/de-Googled users; device maintainers and ROM testers; Android developers/reversers; cautious no-root users who rely on ADB/Shizuku fallbacks and clear destructive-action previews.
- Platforms and distribution: Android app using Views/XML and Material Components; Java/Kotlin; Gradle/AGP; minSdk 21, target/compile SDK 36; `floss` default flavor with optional network paths compiled out, plus `full` flavor for opt-in VirusTotal/Pithus/debloat/tracker freshness features; F-Droid/GitHub-style distribution with SBOM/release workflows.
- Key integrations and data flows: Android package manager, hidden APIs, AppOps, UsageStats, StorageStats, SAF, profiles/users, local privileged server, root/ADB/Shizuku/Dhizuku bridges, Termux, optional network scanners/updaters, local Room/XML/JSON/profile/snapshot artifacts, support-bundle export through Android share flows.

## Competitive Landscape
- AppDash: commercial app dashboard with usage, history, permissions, backups, tags, and watchlists in one high-confidence surface. Learn from its single-place summaries and history affordances; avoid account/cloud assumptions that conflict with AppManagerNG's offline-first posture.
- Upstream App Manager: broad OSS reference for package operations, backup/rules, terminal, and Android platform breakage. Learn from its active issue signal around terminal authentication, ADB mode confusion, TV/file-manager requests, and package-state edge cases; avoid porting features without NG-specific guardrails and tests.
- Inure and LibChecker: polished power-user Android references for analytics, terminal, native-library visibility, ABI inspection, theme controls, and source provenance. Learn from compact technical summaries and visual hierarchy; avoid a UI rewrite that would fight the current Views/Material architecture.
- Hail, Canta, and UAD-NG: focused freeze/debloat references with Shizuku/root support, community package recommendations, and rollback-sensitive workflows. Learn from simple freeze/debloat intent and recommendation provenance; avoid presenting community package advice without source/version/OEM scope and recovery language.
- Neo Backup and Swift Backup: strongest backup references for scheduled backup expectations, per-part visibility, encryption, external-storage warnings, cross-device restore failures, and cloud tradeoffs. Learn from previewable backup parts and restore confidence; avoid opaque cloud lock-in and all-or-nothing restores.
- SD Maid SE: mature maintenance-tool reference for storage jobs, scheduled operations, scan result preservation, TV launchers, low-storage prompts, and certificate fingerprints. Learn from job lifecycle feedback and resumable maintenance; avoid fragment-owned long-running work for destructive storage operations.
- PermissionManagerX and Blocker: focused permission/AppOp/component references with reference states, watcher behavior, intent filters, and import/export. Learn from exact desired-vs-actual state disclosure; avoid color-only status indicators or ambiguous component toggles.
- Obtainium, AppVerifier, and Shizuku: adjacent references for update provenance, APK signature verification, source tracking, Shizuku limitations, and privileged-mode education. Learn from explicit trust boundaries; avoid implying ADB/Shizuku can perform root-only operations or that source provenance equals safety.

## Security, Privacy, and Reliability
- Verified: privileged terminal launch is too dependent on an optional global action-auth setting. `app/src/main/java/io/github/muntashirakon/AppManager/terminal/TermActivity.java` can route local, root, Shizuku, and ADB sessions; `app/src/main/java/io/github/muntashirakon/AppManager/misc/LabsActivity.java` gates the terminal through `ActionAuthGate`, but `app/src/main/java/io/github/muntashirakon/AppManager/crypto/auth/ActionAuthGate.java` intentionally bypasses credential prompting when action auth is disabled. Upstream issue `MuntashirAkon/AppManager#1738` confirms user demand for biometric gating of terminal access.
- Verified: snapshot import has capability the UI does not expose. `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java` uses a single confirmation before import; `app/src/main/java/io/github/muntashirakon/AppManager/snapshot/SnapshotBundle.java` already has `ImportOptions` flags for prefs, profiles, rules, tags, operation history, and merge behavior. Neo Backup issues around restore failures and Swift/Neo Backup feature sets show backup tools need previewable, section-aware restore paths.
- Verified: optional network features lack one consolidated audit surface. `FeatureController.java`, `PrivacyPreferences.java`, `VirusTotal.java`, `Pithus.java`, `DebloatDefinitionsUpdater.java`, and `TrackerDatabaseFreshnessChecker.java` separate compile availability, opt-in state, endpoint class, and last-fetched status; the full flavor would be more trustworthy if users could see what may talk to the network and what category of data is sent or fetched.
- Verified: support-bundle redaction is strong but export control is coarse. `app/src/main/java/io/github/muntashirakon/AppManager/misc/SupportInfoBundle.java` builds a zero-network bundle and redacts sensitive identifiers, but support actions share the generated text directly from troubleshooting/mode-doctor flows without a user-visible section preview or exclusion control.
- Verified: version-bearing release surfaces are distributed across `app/build.gradle`, `README.md`, `fastlane/metadata`, `.github/workflows/release.yml`, SBOM generation, and release assets. Release provenance exists, but a preflight consistency gate would reduce accidental version drift before publishing.
- Verified: minSdk 21 remains a deliberate policy while key dependencies now have minSdk-23 or security-tool drift pressure. `versions.gradle` pins Material, Activity, Room, WebKit, WorkManager, Sora Editor, Bouncy Castle, and OWASP Dependency-Check; `docs/policy/minsdk-21-ceiling.md` keeps API 21 through v0.7.x; current dependency changelogs make this a managed-floor problem rather than an immediate platform bump.
- Verified: exported and intent-filtered surfaces need a static drift gate for Android 16 safer-intent behavior. The manifest, automation URI flow, app-detail deep links, and prior changelog fixes show broad public entry points; Android 16 shifts more strictness to receiver-side intent handling, so CI should reject broad filters without parser-side constraints.
- Likely: translation quality and pseudolocale coverage are a maintainability/product-quality gap. The repo has many `values-*` resources and `pseudoLocalesEnabled true`; competitors such as Canta and Hail highlight community translation paths. This is roadmap-worthy only as a lightweight gate, not a platform migration.

## Architecture Assessment
- Terminal/security boundary: add a privileged-route auth policy that cannot be bypassed by disabling the general action-auth preference. Tests should assert local terminal behavior separately from root/ADB/Shizuku route behavior.
- Snapshot import boundary: split manifest parsing, dry-run validation, section selection, and write application. Reuse `SnapshotBundle.ImportOptions` instead of inventing a new import format.
- Optional-network boundary: centralize full-flavor network transparency behind a small read-only ledger that each network-capable feature updates with endpoint class, payload category, last request/fetch time, and opt-in state. Floss builds should compile hidden/no-op behavior.
- Support export boundary: separate bundle generation from share intent. Add preview metadata, section toggles, final byte size, and tests proving excluded sections are absent while existing redaction still runs.
- Release/distribution boundary: make version consistency and dependency-floor compatibility machine-checkable before release. Keep minSdk 21 until the documented policy changes; do not silently upgrade into minSdk-23 dependency lines.
- Intent/deep-link boundary: add a manifest/static test that enumerates exported components and asserts exact action/data/scheme/host expectations for public APIs, with targeted parser tests for automation/deep-link inputs.
- Testing gaps: add focused unit/Robolectric tests for privileged terminal auth, snapshot option application, support-section exclusion, optional-network ledger recording, version consistency parsing, dependency floor reports, and exported-intent drift.
- Documentation gaps: update docs only with the implementing changes: terminal trust model, snapshot restore behavior, optional-network ledger meaning, support bundle contents, release preflight usage, dependency floor policy, and strict-intent public API contracts. Do not create separate audit or planning documents.

## Rejected Ideas
- Full Compose or Material 3 rewrite: rejected because the app is a mature Views/XML + Material Components codebase with minSdk 21 policy and existing token/component polish work blocked on device verification.
- Immediate minSdk 23 bump: rejected because the project policy explicitly holds minSdk 21 through v0.7.x; dependency changelogs are evidence for a drift gate and future rehearsal, not an immediate product change.
- Cloud backup sync: rejected because Swift Backup proves demand but the feature conflicts with AppManagerNG's floss/default local-first privacy posture and would require account, encryption, support, and policy work out of proportion to this roadmap pass.
- Built-in version-watch/updater panel: rejected because `Roadmap_Blocked.md` already tracks a version-watch panel; duplicating it would split the task tracker.
- Full file-manager operations service, trash bin, TV navigation, freeze surface/widget, analytics dashboard, theme/a11y overhaul, boot-component manager, and local-server secure-session hardening: rejected from new additions because they already exist in `Roadmap_Blocked.md`.
- Raw UAD/package-list mirroring: rejected because competitor issue traffic shows package data needs provenance, OEM scope, and rollback context; more rows without trust metadata would increase destructive-action risk.
- Android AppFunctions assistant integration: rejected because the API is early/private-preview-oriented, Android 16+ scoped, and not aligned with AppManagerNG's current cautious privilege/consent model.
- Health Connect, Credential Manager, and SDK Runtime dashboards: rejected because they are not core app-manager workflows unless local package metadata exposes actionable state; proposing them now would be speculative.
- Broad plugin ecosystem: rejected because privileged package operations need a mature capability/consent model first; current integration value is better captured through strict-intent, optional-network, and automation-safety gates.

## Sources
Official/platform:
- https://developer.android.com/developer-verification
- https://developer.android.com/reference/kotlin/android/content/pm/PackageInstaller
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/15/features
- https://developer.android.com/training/package-visibility
- https://source.android.com/docs/security/bulletin/2026/2026-05-01
- https://shizuku.rikka.app/guide/setup/
- https://developer.android.com/ai/appfunctions

Dependencies/security:
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://github.com/material-components/material-components-android/releases
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://plugins.gradle.org/plugin/org.owasp.dependencycheck
- https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/

Competitors and adjacent projects:
- https://appdash.app/
- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/issues/1738
- https://github.com/Hamza417/Inure
- https://github.com/aistra0528/Hail/blob/master/README_EN.md
- https://samolego.github.io/Canta/
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/
- https://github.com/NeoApplications/neo-backup
- https://www.swiftapps.org/faq
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/ImranR98/Obtainium
- https://github.com/soupslurpr/AppVerifier
- https://f-droid.org/en/packages/com.mirfatif.permissionmanagerx/
- https://f-droid.org/en/packages/com.merxury.blocker/
- https://github.com/timschneeb/awesome-shizuku

## Open Questions
- None that block the roadmap additions above; each item can be implemented with local code, tests, and the cited public references.
