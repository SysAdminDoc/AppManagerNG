<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research - AppManagerNG

## Executive Summary

AppManagerNG is a Java/Android Views package-management fork aimed at power users who need package inspection, backup/restore, app-ops, component rules, debloat, root/ADB/Shizuku operation, APK installation, file access, and offline-first documentation in one app. Verified local code and docs show recent work already closed the most obvious privileged-output and app-list failure classes, so the strongest next opportunities are trust and recovery improvements: archived-app backup safety, bounded component-rule reset diagnostics, explicit backup deletion scope, restored ignored host tests, documentation truth, structured logging cleanup, unsupported intent-extra reporting, stale file-manager job cancellation, and Gradle/local artifact hygiene.

Top opportunities by expected user impact:

1. Add backup/restore guards for Android archived-app state before APK/data operations.
2. Add bounded progress, cancellation, and partial-failure reporting to component-rule bulk reset.
3. Make destructive backup deletion scope explicit for base backups versus all named versions.
4. Re-enable stale ignored Robolectric fixture tests and keep fixture failures visible.
5. Rebaseline packaged manual source and archive links so published docs match tracked content.
6. Replace remaining production `printStackTrace()` calls in app/libcore utility paths.
7. Report unsupported intent extras instead of silently dropping them from generated commands.
8. Cancel or generation-skip stale file-manager attribute loads during fast navigation.
9. Keep the Gradle wrapper current and ignore local JVM crash/replay artifacts.

## Product Map

Core workflows: main app inventory and search, package detail inspection, APK install/export, one-click operations, batch archive/freeze/backup/delete flows, backup/restore and conversion, app-ops and permission review, component blocking/IFW rules, debloat definitions, Activity Interceptor command generation, file manager browsing, docs/manual access, and privileged-mode diagnostics.

Primary personas: rooted Android power users, ADB/Shizuku users without root, ROM/device maintainers, privacy-focused users auditing permissions and trackers, app developers debugging manifests and intents, and offline users who need FLOSS distribution without network dependencies.

Platforms and distribution: Android minSdk 21 with targetSdk 36 and compileSdk 37, FLOSS and FULL flavors, F-Droid/GitHub-style side loading, GPL-3.0-or-later source obligations, Java/Kotlin Android Views, Material Components 1.13.0, Gradle 9.6.1/AGP 9.2.1, native/server helpers, and local-only build/release verification.

Key integrations and data flows: Android package APIs, hidden/privileged compat layers, root/libsu, Shizuku, ADB pairing, Dhizuku, PackageInstaller archive/unarchive APIs, Room metadata, backup archive storage, local docs generation, GitHub-hosted releases and raw docs, optional updater channels, and third-party signature/debloat/reference data where pinned and allowed by flavor.

## Competitive Landscape

- Upstream App Manager - best breadth and issue signal; learn from current bug reports and API coverage; avoid blind upstream ports without NG hardening and minSdk/license review.
- Canta and UAD-ng - strong debloat safety posture and user-facing package recommendations; learn conservative defaults and reversible operations; avoid network-dependent debloat behavior in FLOSS paths.
- Neo Backup and Swift Backup - set expectations for clear backup versioning, restore risk, and schedulers; learn visible backup state and delete/version semantics; avoid expanding backup features before archived-state and destructive-scope guards are clear.
- Hail and Blocker - focused freeze/component ergonomics; learn fast single-purpose controls; avoid adding device-gated UI polish before bulk operations have progress, cancellation, and recovery ledgers.
- LibChecker, Inure, and AppVerifier - strong inspection, signing, and dense technical presentation; learn scannable evidence-first detail pages; avoid duplicating dashboards without reliable source truth.
- SD Maid SE, Amaze, and Material Files - mature cancellation, file operation, and local storage patterns; learn stale-job prevention and recoverable operation logs; avoid a file-manager rewrite when targeted adapter lifecycle fixes are enough.
- AppDash - polished commercial package inventory, notes, tags, backup, and insights; learn organization and operation history affordances; avoid Play/network-backed intelligence in FLOSS builds.

## Security, Privacy, Reliability

Verified high-confidence issues:

- Archived-app backup safety is not yet explicit in backup/restore paths. App archiving is exposed in the main UI and batch operations, and archive state is tested in package-state verification, but backup entry points do not appear to preflight archived package availability before APK/data work. Treating an archived package as fully installed can create incomplete backups or misleading restore choices.
- Component-rule reset collapses privileged/IFW failures into a boolean. `ComponentsBlocker.applyRules(false)` walks all rules and catches `Throwable` around per-component state changes; failures are not surfaced as a bounded, retryable ledger for users.
- Backup deletion scope remains destructive and ambiguous where base backup deletion and named backup retention can diverge.
- Several pre-2026-05-25 `@Ignore` markers still suppress host-side regression coverage, reducing confidence in ZIP/VFS/TAR/OAB/settings-search code.
- Production `printStackTrace()` calls remain in shared file/proc/UI utility paths, which can leak noisy diagnostics and bypass structured logging.
- Activity Interceptor drops unsupported extras from command/export output without a user-visible count or key/type warning.
- File-manager attribute caching can outlive recycled rows or dataset swaps without an owned cancellation/generation token.
- Local JVM crash/replay logs under `app/` are unignored and easy to stage accidentally.

Rejected as duplicates or already tracked:

- Backup extras warning: already implemented through `BackupExtrasCoverage` strings and tests.
- Android 17/private-space app-list failures: locally guarded in main-list load status and still tracked as device-gated in `Roadmap_Blocked.md`.
- Wireless ADB/Quest pairing UX: pairing support exists and reconnect/pairing-state resilience is already in `Roadmap_Blocked.md`.
- Samsung "Clear Compiler Artifacts": already tracked as device/design-gated in `Roadmap_Blocked.md`.
- Domain link inspection: code already includes domain verification compat, filters, and detail rendering; no fresh high-confidence gap exceeded the current roadmap items.
- Compose rewrite, Material 1.14 migration, and minSdk 23 dependency wave: conflict with repo constraints and current minSdk 21 policy.

## Architecture Assessment

The architecture is intentionally large but coherent: package state is represented through app-detail/main-list models, privileged access is isolated through compat/server/root abstractions, batch operations centralize user-visible work, and host tests increasingly pin behavior that can be verified without a device. The best roadmap items are small seams that strengthen existing paths instead of adding new product surfaces.

Strengths:

- Existing package/archive support is localized enough to add backup preflights without a broad UI rewrite.
- Recent main-list load status and privileged-output fixes show a pattern for bounded diagnostics that can be reused.
- Backup extras coverage, storage checks, and convert/verify tests provide a good foundation for more backup truth-state tests.
- Domain links, freeze/archived filters, app notes/tags, and finder scoring already give the inventory experience depth compared with single-purpose competitors.
- `ROADMAP.md` and `Roadmap_Blocked.md` split host-verifiable work from device-gated work, which keeps planning actionable.

Risks:

- Many features cross root/ADB/Shizuku/device-policy boundaries; new operations must default to explicit preflights and recoverable errors.
- Docs generation has multiple source/generated surfaces, so fork identity can regress if only generated HTML is edited.
- Backlog duplication is easy because upstream issues often map to work already implemented or moved to blocked status.
- Host tests cannot fully prove multi-user, archived-app, Shizuku, Dhizuku, or OEM behavior; local tests should pin contracts and leave device matrices in `Roadmap_Blocked.md`.

## New Roadmap Additions

The refreshed roadmap adds two items:

- P1 - Guard backups and restores for Android archived-app state.
- P2 - Add bounded progress and partial-failure reporting to component-rule reset.

Both are high-confidence because they are supported by local code evidence and current platform/upstream signals, while avoiding duplicates already implemented or parked in `Roadmap_Blocked.md`.

## Sources

OSS and upstream:

- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/issues/1980
- https://github.com/MuntashirAkon/AppManager/issues/1982
- https://github.com/MuntashirAkon/AppManager/issues/1986
- https://github.com/MuntashirAkon/AppManager/issues/1992
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/aistra0528/Hail
- https://github.com/lihenggui/blocker
- https://github.com/LibChecker/LibChecker
- https://github.com/Hamza417/Inure
- https://github.com/soupslurpr/AppVerifier
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/TeamAmaze/AmazeFileManager
- https://github.com/zhanghai/MaterialFiles

Commercial:

- https://appdash.app/
- https://www.swiftapps.org/

Platform, build, security, and docs:

- https://developer.android.com/reference/android/content/pm/ApplicationInfo
- https://developer.android.com/reference/android/content/pm/ArchivedPackageInfo
- https://developer.android.com/developer-verification
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://docs.gradle.org/current/release-notes.html
- https://robolectric.org/getting-started/
- https://junit.org/junit4/javadoc/4.13/org/junit/Ignore.html
- https://docs.github.com/en/get-started/git-basics/ignoring-files
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://github.com/jeremylong/DependencyCheck
- https://github.com/material-components/material-components-android

## Open Questions

- Which archived-app backup behavior should be default for scheduled backups: skip with explicit result, metadata-only capture, or automatic unarchive where the platform permits it?
- Should component-rule reset failures be stored in existing operation history, a dedicated rule-reset report, or both?
- Should backup deletion include an authenticated "all versions" path for every storage backend or only for local/backed backup stores that can count named versions reliably?
- Which ignored host tests need fixture regeneration versus narrower replacement tests?
