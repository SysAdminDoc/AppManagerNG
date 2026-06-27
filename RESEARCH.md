# Research — AppManagerNG

## Executive Summary
AppManagerNG is a GPL Android power-user package manager built on Java/XML Views, Material Components, Gradle/AGP, and a local-first floss/full flavor split. Its strongest current shape is breadth plus safety: package inspection, backup/restore, debloat/freeze, file management, logcat, code editing, profiles, privileged root/ADB/Shizuku/Dhizuku paths, and recent host-side hardening around archive bounds, release gates, manifest audits, StrictMode, dependency ceilings, and Android 17/18 preparation. The highest-value next direction is not another large feature surface; it is tightening the remaining host-verifiable reliability gaps that can break privileged sessions, mislead releases, or silently lose user intent. Top opportunities: bound privileged shell output; finish privileged server diagnostic hygiene; repair local-release/docs truth after GitHub Actions removal; expand consistency gates to catch stale version/SDK surfaces; add backup delete scope control; report unsupported intent extras instead of dropping them; cancel stale file-manager attribute work; refresh Gradle 9.6.1; and finish the remaining non-dedicated raw-thread cleanup.

## Product Map
- Core workflows: enumerate and filter installed packages; inspect app details, components, permissions, AppOps, signatures, trackers, storage, and usage; install/uninstall/archive/freeze/debloat; backup, restore, import, export, schedule, and roll back operations; browse files, edit code, inspect logs, run terminal commands, and generate diagnostics.
- User personas: rooted Android power users; Shizuku/ADB users who want privileged control without root; de-Googled/floss users; ROM testers and device maintainers; Android developers/reversers; cautious users who need reversible debloat and backup operations.
- Platforms and distribution: Android app with minSdk 21, targetSdk 36, compileSdk 37, Java source, XML layouts, Material Components 1.13.0, Gradle 9.6.0/AGP 9.2.1, NDK/server modules, `floss` default offline flavor, and opt-in `full` network features.
- Key integrations and data flows: Android PackageManager/AppOps/UsageStats/StorageStats/SAF; hidden APIs through HiddenApiBypass 6.1; privileged local server over socket/parcel protocol; root/ADB/Shizuku/Dhizuku bridges; Room databases; WorkManager jobs; Tasker/`am://` intents; Fastlane metadata; local release and dependency verification scripts.

## Competitive Landscape
- Upstream App Manager: remains the closest functional baseline and has current Android 17 app-list breakage in issue #1948. Learn from upstream fixes when v4.1.0 lands; avoid waiting for device-gated upstream work when NG can harden host-verifiable code now.
- Blocker: strongest focused component-blocking competitor, with recent Shizuku system-UID work in PR #1548 and warning signals around system-owned app data. Learn from its privilege-mode caution and component UX; avoid expanding privileged modes without rollback and ownership diagnostics.
- Hail: best focused freeze/unfreeze UX, including launch-through automation and a clear privilege capability matrix. Learn from its mode transparency; avoid duplicating its narrow freeze-first product instead of using NG's broader batch/profile system.
- Canta and UAD-ng: strongest Shizuku debloat + crowd-sourced safety signal pair, but issues show unclear success states and OEM bootloop risk for recommended packages. Learn from their simple labels and community data; keep NG's stricter safety ratings, rollback, and action verification.
- LibChecker: best native/library/signature inspection specialist and already ships Android 17/compileSdk 37 adaptation. Learn from its architecture/library visibility; avoid competing on deep library taxonomy when NG's edge is actionability.
- SD Maid SE: best adjacent Android maintenance/cleanup app, with fast OEM workaround tracking. Learn from its device-specific workaround catalog discipline; avoid building a full cleanup suite that dilutes package-manager trust.
- Neo Backup: focused backup/restore competitor with scheduling and versioned backup expectations. Learn from explicit backup-set lifecycle controls; avoid cloud-first sync because NG's default posture is local/offline.
- AppDash and APKUpdater: commercial/adjacent proof that dashboards, watchlists, update intelligence, and versioned backups are valued. Use them as long-term product signals; keep network intelligence opt-in and full-flavor-only.

## Security, Privacy, and Reliability
- Verified: `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/Shell.java:201` appends every shell output line into one unbounded `StringBuilder`, then `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerHandler.java:134` parcels the result back to the app. `DataTransmission.MAX_MESSAGE_LENGTH` caps framed IPC reads, not source-side shell accumulation.
- Verified: production privileged paths still contain direct `printStackTrace()` and broad `catch (Throwable)` usage in `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerRunner.java`, `ServerHandler.java`, `BroadcastSender.java`, and `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/{FLog,Shell,DataTransmission,ClassUtils,ParamsFixer}.java`.
- Verified: local release truth is stale after `4ebc3f9ec` removed GitHub Actions. `README.md:161`, `build.gradle:19`, `build.gradle:52`, `build.gradle:63`, `versions.gradle:15`, and `scripts/verify-release-consistency.sh:55` still describe CI/workflow behavior or emit GitHub Actions error syntax.
- Verified: version/SDK planning surfaces drift. `app/build.gradle:18-19` says versionCode 11/versionName 0.6.3 and `versions.gradle:4` says compileSdk 37, but `CLAUDE.md:20`, `CLAUDE.md:131`, and `PROJECT_CONTEXT.md:13-14` still describe older Gradle/AGP/version/SDK state. The release gate does not check those surfaces.
- Verified: `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogFragment.java:382` deletes only base backups, with an explicit TODO for including named backups; this is a user data-retention and recovery clarity gap distinct from the blocked custom-name overwrite item.
- Verified: `app/src/main/java/io/github/muntashirakon/AppManager/intercept/IntentCompat.java:538`, `:641`, and `:687` silently skip unsupported extras while creating command/export descriptions, so Activity Interceptor output can lose values without a warning.
- Verified: `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmAdapter.java:158` posts attribute cache work for uncached file rows and only guards final binding by tag. It does not cancel or generation-skip obsolete jobs during rapid dataset changes.
- Verified: Android 17 adds app memory limits, Android 18 removes implicit URI grants for ACTION_SEND/ACTION_SEND_MULTIPLE/ACTION_IMAGE_CAPTURE, and Android 17 target changes harden BAL. NG already has many explicit URI grant callsites and existing Android 17 blocked runtime items, so the new active work should focus on local code paths not requiring API 37 devices.
- Verified: WorkManager 2.11.x contains Android 15+ network-constraint fixes but raises minSdk from 21 to 23. Material Components 1.14.0 also requires minSdk 23. The minSdk-21 ceiling remains a real dependency constraint, not a stale pin.

## Architecture Assessment
- Privileged server boundary: the standalone server/common modules need bounded result transport, structured diagnostics, and source-contract tests before deeper HMAC/TLS work returns from `Roadmap_Blocked.md`.
- Release engineering boundary: GitHub-hosted CI is no longer part of the project policy, but root build comments, README copy, and shell gates still assume it. The release gate should become an explicitly local preflight that validates version, SDK, dependency-ceiling, Fastlane, README, changelog, and planning-surface drift.
- Backup boundary: destructive backup deletion lacks a user-visible base-only versus all-versions choice. Implement this in the existing dialog/view-model/batch-op path before broader backup overwrite work returns from device-gated status.
- Intent export boundary: unsupported extra handling belongs in `IntentCompat` so Activity Interceptor, shortcut export, and shell-command generation share one auditable result model.
- File-manager boundary: row attribute loading should have generation/cancellation ownership in `FmAdapter` or a small helper, not fire-and-forget background work attached to recycled holders.
- Test gaps: add host-side source/JVM tests for shell truncation, privileged diagnostic hygiene, release-gate drift, intent-extra reporting, and file-manager generation guards. Keep emulator/device tests reserved for items already in `Roadmap_Blocked.md`.
- Documentation gaps: `README.md`, `PROJECT_CONTEXT.md`, `CLAUDE.md`, build comments, and verification scripts disagree on current release and build process, but this research pass updates only `RESEARCH.md` and `ROADMAP.md` per file-hygiene rules.

## Rejected Ideas
- Full Compose rewrite: rejected because the repo is explicitly Views/Material Components, has 143 XML layouts, and Material 1.14.0's new Views features require minSdk 23.
- minSdk 23 bump just to chase AndroidX/Material: rejected for now; it would unblock WorkManager 2.11.x and Material 1.14.0, but the repo documents minSdk 21 as a user-support ceiling.
- Immediate Paparazzi screenshot regression work: rejected as an active item because `Roadmap_Blocked.md` already tracks it and Paparazzi AGP 9 support is still dependency-gated.
- DDG Tracker Radar runtime network monitoring: rejected as active work because `Roadmap_Blocked.md` already records the design decision and runtime VPN/network monitoring would expand NG away from static package management.
- Cloud backup sync: rejected because it conflicts with the floss/default local-first privacy posture and F-Droid's API-key/proprietary-service constraints.
- App store replacement or paid Play intelligence clone: rejected; AppDash/APKUpdater prove demand, but NG should keep any update intelligence opt-in, full-flavor-only, and non-installing.
- Corpse/orphan cleanup suite: rejected because SD Maid SE owns that problem space and it would dilute NG's package-manager trust model.
- Accessibility/visual/settings IA/i18n/distribution items as new active tasks: rejected as duplicates because `Roadmap_Blocked.md` already contains the device-, service-, or operator-gated versions.
- Android 17 app-list/root/backup runtime fixes as new active tasks: rejected as duplicates because `Roadmap_Blocked.md` already contains the device-gated runtime validation tasks.

## Sources
Official/platform:
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/developer-verification
- https://source.android.com/docs/security/bulletin/2026/2026-06-01
- https://f-droid.org/en/docs/Inclusion_Policy/

Dependencies:
- https://github.com/gradle/gradle/releases/tag/v9.6.1
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/core
- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://github.com/cashapp/paparazzi/issues/2095
- https://github.com/cashapp/paparazzi/pull/2318
- https://github.com/cashapp/paparazzi/releases/tag/2.0.0-alpha05
- https://github.com/LSPosed/AndroidHiddenApiBypass

Competitors and adjacent tools:
- https://github.com/MuntashirAkon/AppManager/issues/1948
- https://github.com/lihenggui/blocker/pull/1548
- https://github.com/lihenggui/blocker/issues/1547
- https://github.com/aistra0528/Hail
- https://github.com/samolego/Canta/issues/64
- https://github.com/samolego/Canta/issues/355
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1400
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/583
- https://github.com/LibChecker/LibChecker/releases/tag/2.5.4
- https://github.com/d4rken-org/sdmaid-se/releases/tag/v1.7.5-rc0
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/rumboalla/apkupdater
- https://appdash.app/

## Open Questions
- Does the maintainer want to keep minSdk 21 once WorkManager/Material/Core pressure accumulates further, or should a future milestone explicitly plan the minSdk 23 migration?
- What trust model should the privileged local server use after HMAC/native `run_server` work returns from device-gated status?
- Will SysAdminDoc register Android developer verification for full distribution before September 30, 2026, or document the advanced unverified install flow as the official path?
