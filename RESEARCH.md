# Research — AppManagerNG

## Executive Summary
AppManagerNG is a privacy-first Android package manager and maintenance tool for advanced users who need package inspection, privileged app operations, backups, debloating, profiles, automation, and recovery workflows across root, ADB, Shizuku, Dhizuku, and no-root modes. Its strongest current shape is a broad local-first power-user surface with a serious release and policy discipline: Gradle/AGP are current, target SDK 36 is in place, Android 15 archiving and Android 17 local-network work are already tracked or implemented, and the floss/full split keeps optional network lookups out of the default build. The highest-value direction is not a visual rewrite; it is making privileged, destructive, scheduled, and cross-profile actions visibly safer and more explainable. Priority opportunities: make backup storage warnings user-visible and scheduler-aware; scope keystore import instead of replacing the whole store; harden local privileged server sessions; add a multi-user/work-profile/private-space capability matrix; move file-manager mutations into cancellable recoverable jobs; finish routine trigger history and governance; show debloat recommendation provenance and rollback plans; watch package removal from app details; add component intent-action discovery.

## Product Map
- Core workflows: app/package search and inspection; install/uninstall/archive; backup/restore/import/export; permission, AppOp, component, tracker, and rules management; debloat, freeze, profile, batch, routine, and automation operations.
- User personas: rooted Android power users; de-Googled/privacy users; device maintainers and ROM testers; Android developers/reversers; cautious users who need no-root or ADB/Shizuku fallbacks before committing destructive actions.
- Platforms and distribution: Android app with minSdk 21, target/compile SDK 36, Android Views/XML plus Material Components; floss/full Gradle flavors; F-Droid/GitHub-style distribution; release reproducibility, SBOM, and permission-parity checks in docs and CI.
- Key integrations and data flows: Android package manager and hidden APIs; AppOps, UsageStats, StorageStats, SAF, profiles/users, local privileged server, root/ADB/Shizuku/Dhizuku bridges, Termux, optional VirusTotal/Pithus/debloat/tracker lookups in full builds, local Room/JSON/XML/profile/backup artifacts.

## Competitive Landscape
- Upstream App Manager: broadest OSS feature parity and issue signal for package operations, backups, rules, and Android 16/17 breakage. Learn from its active pain points around app-list reliability, backup restore clarity, root detection, and precise AppOp controls; avoid inheriting open TODOs without NG-specific tests.
- AppDash: strong commercial packaging of app history, dashboards, backup/watchlist workflows, widgets, and clear permission/usage panels. Learn from its trust-building summaries and history surfaces; avoid cloud/account assumptions that conflict with AppManagerNG's local-first stance.
- Swift Backup and Neo Backup: best competitive references for backup scheduling, backup-part visibility, external storage, encryption expectations, and FAT/FAT32 failure warnings. Learn from preflight clarity, scheduled history, and restore confidence; avoid opaque cloud lock-in and overbroad restore automation.
- Inure: demonstrates a polished power-user Android UI with theme controls, usage analytics, terminal, reproducible builds, animation reduction, and permission explanations. Learn from configurable density and accessibility affordances; avoid custom UI novelty that would fight the existing Views/Material stack.
- Hail, Canta, and UAD-NG: strongest references for freeze/debloat simplicity, package risk databases, rollback expectations, and community-maintained package notes. Learn from one-tap freeze/debloat flow and data provenance; avoid showing success when the package state is unknown or the recommendation source is stale.
- SD Maid SE: sets expectations for storage maintenance, scheduled cleaning, TV support, profile storage, low-storage prompts, and keeping previous scan results during refresh. Learn from scheduled maintenance diagnostics and resumable storage jobs; avoid scanner UI that discards useful prior results while work is in progress.
- PermissionManagerX and Blocker: focused references for desired-vs-actual permission/AppOp state, component search, intent filters, and reset workflows. Learn from reference states and component/action discovery; avoid hiding powerful toggles behind ambiguous color-only indicators.
- Obtainium, LibChecker, and Shizuku: adjacent references for update provenance, APK/AppVerifier integration, native/library inspection, Shizuku limitations, and privileged-mode education. Learn from source provenance and capability disclosure; avoid implying ADB/Shizuku can perform root-only operations.

## Security, Privacy, and Reliability
- Verified: backup preflight warnings are detected but not surfaced as decisions. `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupManager.java` blocks only `INSUFFICIENT`, while `BackupStorageCheck.java` also returns `WARN_LOW_HEADROOM` and `WARN_MAX_FILE_SIZE`; Swift Backup documents FAT/FAT32 truncation risks and Neo Backup users request per-part size and splitting.
- Verified: keystore import is broader than needed. `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/ImportExportKeyStoreDialogFragment.java` backs up and overwrites the full `AM_KEYSTORE_FILE`, then has a TODO to import only AppManager-used keys; failure rollback exists, but successful imports can replace unrelated BKS entries.
- Verified: privileged local-server transport relies on a cleartext socket plus random token. `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java` states there is no SSL because a random localhost port is authenticated, while `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java` generates a 256-bit token; the same file has a TODO for a random per-session SSL certificate. Android 17 local-network changes and the local ADB advisory make the boundary worth tightening.
- Verified: multi-user and private-profile support is broad but hard to reason about. The manifest declares `ACCESS_HIDDEN_PROFILES`, docs explain privileged profile visibility, and many flows carry `userId`; competitor issues in Canta, Neo Backup, and SD Maid SE show work/private profile operations commonly fail or partially work unless the UI exposes capability and unsupported states.
- Verified: file-manager mutations are still fragment/thread-bound. `FmFragment.java` has a TODO to move copy/move/delete into a bound service, and `FmAdapter.java` notes thread cancellation cleanup; long copy/move/delete/archive jobs need cancellation, foreground progress, and recovery state.
- Verified: routine scheduling shipped with known open decisions. `docs/architecture/05-routine-scheduler.md` explicitly leaves trigger-bound filters, maximum trigger count, and history rotation beyond `profile_trigger_runs` unresolved; Hail, Neo Backup, and Swift Backup show scheduled operations need visible history and skip reasons.
- Verified: debloat recommendations need provenance and rollback before more surface area is added. `docs/debloat-definitions/manifest.json` tracks source definitions, while UAD-NG issue traffic is heavily package documentation and breakage; destructive debloat should show source/version/OEM scope and produce a rollback plan.
- Verified: `AppInfoFragment.java` still has a TODO to watch for package uninstallation, so details can become stale when a package disappears while the screen is open.
- Verified: release provenance, permission parity, Android 15 archiving, and Android 17 `ACCESS_LOCAL_NETWORK` are already covered in docs, CI, or roadmap; new roadmap items should not duplicate those.
- Verified: Bouncy Castle 1.84 is already pinned and addresses current upstream CVEs. OWASP Dependency-Check 10.0.3 is behind 12.2.2, but security-tool churn should be treated as CI maintenance unless a live advisory or repository failure makes it product-critical.

## Architecture Assessment
- Backup and restore need a policy layer between estimation and execution. The existing `BackupStorageCheck` result model is useful; the missing boundary is UI/scheduler decision handling, tests for filesystem max-file-size warnings, and diagnostics for scheduled skip/proceed outcomes.
- Crypto import/export needs alias-level merge semantics. `KeyStoreManager` and import dialogs should preview aliases, preserve unrelated store entries, handle collisions deliberately, and test failure rollback plus successful scoped import.
- Privileged transport needs a documented trust boundary. Either enforce loopback-only sessions or add per-session authenticated encryption/TLS with host/token/replay tests; do not leave the cleartext assumption implicit.
- User/profile handling needs a capability matrix service rather than scattered `userId` checks. Backup, install, freeze, app-ops, cache clearing, storage, and automation screens should consume one source that explains what works in main, work, hidden, and private spaces under each privilege lane.
- File-manager copy/move/delete/archive should become operations with lifecycle, cancellation, and recovery. Fragment-owned threads are the wrong boundary for long-running destructive storage work.
- Routine scheduler needs durable history and bounded trigger configuration before adding more operation types. A Room-backed last-N run log with skip/failure reasons would also improve observability and user trust.
- Debloat import/display should keep source metadata with each recommendation and generate a rollback bundle before action. This is a reliability feature, not just documentation.
- Testing gaps: add targeted Robolectric/unit tests for warning decisions, scoped keystore import, local-server session rejection, routine history retention, and package-removed app-details state. Existing visual/a11y roadmap items should remain separate instead of being duplicated here.
- Documentation gaps: update docs only as part of the implementing changes for backup warning policy, local-server trust model, private-space support, and debloat provenance. Do not create additional planning documents.
- Coverage by category: security, reliability, multi-user, migration, observability, testing, distribution, docs, offline/resilience, and plugin/integration boundaries are covered above; accessibility, settings IA, theme polish, TV/mobile adaptive work, permission reference states, trash, freeze, cache routines, dashboards, version watch, and tracker rollups are already present in `ROADMAP.md` and should not be duplicated.

## Rejected Ideas
- Immediate Compose or Material 3 rewrite: rejected because the repo is a Views/XML app with established Material Components patterns, minSdk 21 support, and active roadmap items for token/component polish.
- Immediate minSdk 23 bump: rejected because `docs/policy/minsdk-21-ceiling.md` says to hold API 21 through v0.7.x unless a trigger fires; Material 1.14, Room 2.8, WebKit 1.15, Activity 1.12, and WorkManager 2.11 are future-floor evidence, not a current product feature.
- Cloud backup sync: rejected because AppManagerNG's floss/default posture is local-first and privacy-preserving; Swift Backup proves demand but also brings account, lock-in, and policy complexity.
- Broad AppFunctions/plugin automation: rejected for now because Android AppFunctions is Android 16+ and private-preview-oriented, while AppManagerNG's privileged operations are too destructive for a broad automation surface without a mature capability/consent model.
- New Android 17 local-network permission item: rejected because this is already implemented and documented in the Android 17 audit.
- Generic release-provenance roadmap item: rejected because reproducible release scripts, SBOM, permission parity, and `actions/attest` are already documented or present in CI.
- Raw UAD list mirroring: rejected because UAD-NG issue traffic shows package data needs provenance, OEM scope, and rollback context, not just more package rows.
- Duplicates of existing roadmap items: permission reference states, file-manager trash, TV navigation/banner, analytics dashboard, version-watch panel, boot-component manager, tracker report rollup, theme/a11y coherence, freeze surface, and scheduled cache/data clearing are already tracked.

## Sources
Official/platform:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://android-developers.googleblog.com/2026/06/Android-17.html
- https://developer.android.com/developer-verification
- https://developer.android.com/about/versions/15/features
- https://developer.android.com/training/package-visibility
- https://developer.android.com/ai/appfunctions
- https://developer.android.com/reference/android/security/advancedprotection/package-summary
- https://developer.android.com/identity/data/autobackup
- https://bayton.org/blog/2024/10/actually-new-for-enterprise-android-15/

Dependencies/security:
- https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/
- https://plugins.gradle.org/plugin/org.owasp.dependencycheck
- https://github.com/dependency-check/dependency-check-gradle
- https://developer.android.com/jetpack/androidx/releases/work
- https://github.com/material-components/material-components-android/releases

Competitors and adjacent projects:
- https://github.com/MuntashirAkon/AppManager
- https://appdash.app/
- https://www.swiftapps.org/faq
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/Hamza417/Inure
- https://github.com/aistra0528/Hail
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/mirfatif/PermissionManagerX
- https://github.com/lihenggui/blocker
- https://github.com/ImranR98/Obtainium
- https://github.com/RikkaApps/Shizuku

Community:
- https://github.com/timschneeb/awesome-shizuku
- https://awesome-android-root.pages.dev/non-root-alternatives
- https://news.ycombinator.com/item?id=34199618

## Open Questions
- Must the privileged local server support non-loopback or Wi-Fi hosts for any current workflow, or can AppManagerNG enforce loopback-only sessions?
- Should max-file backup risk be handled by blocking, splitting, or warning with an explicit user/scheduler policy?
- What product milestone should turn the minSdk 23 dependency rehearsal into an actual platform-floor migration?
