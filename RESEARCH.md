# Research — AppManagerNG

## Executive Summary
AppManagerNG is the most comprehensive open-source Android package manager: 928 Java source files, 143 layouts, 375 test files with 1088+ test methods, 39 ViewModels, 30+ Finder filters, permission/component/signing-cert reference states, IFW component blocking, Tasker automation, QS tiles, widgets, scheduled backups/routines, and operation-history rollback — all under a Material 3 Views surface with AMOLED/dark/light themes, floss/full flavor split, reproducible release CI with SBOM attestation, and weekly OWASP dependency scans. No single competitor matches this breadth. The project has undergone four autonomous drain passes since 2026-06-17, fixing main-thread blocking, UAD-ng safety rating bugs, replacing 113 printStackTrace calls, and bumping compileSdk to 37.

The highest-value direction is Android 17 forward-compatibility hardening (HiddenApiBypass deprecation, BAL changes, upstream app-list regression), thread-safety fixes in the component blocking path, and early preparation for Android 18's URI grant restriction. These are all host-verifiable without device access.

Top opportunities in priority order:
1. HiddenApiBypass `addHiddenApiExemptions` deprecated for Android 17 — audit + migration prep
2. Android 17 Background Activity Launch (BAL) hardening audit
3. Android 18 implicit URI grant preparation (ACTION_SEND flows)
4. AppDetailsViewModel.setPackageChanged() thread-safety fix (GuardedBy mismatch)
5. Replace 6 raw `new Thread()` creations with executor/ThreadUtils patterns
6. clearApplicationUserData → IActivityManager stability migration
7. ZipFileSystem test stub completion (8+ empty tests)
8. Gradle 9.5.1 → 9.6.0 bump

## Product Map
- Core workflows: package list/search/filter/tag; package detail inspection with component/permission/AppOp/tracker management; install/uninstall/archive; backup/restore/snapshot import-export; debloat/freeze/profile/batch/routine automation; code editor with diff; file manager; terminal with history/completion; logcat; troubleshooting and privilege-mode diagnostics.
- User personas: rooted Android power users; privacy/de-Googled users (floss flavor); device maintainers and ROM testers; Android developers and reversers; cautious no-root users who rely on ADB/Shizuku/Dhizuku fallbacks.
- Platforms and distribution: Android Views/XML + Material Components 1.13.0; Java (928 files, 0 Kotlin in app); Gradle 9.5.1/AGP 9.2.1; minSdk 21, target 36, compileSdk 37; `floss` (default, F-Droid-compatible) and `full` (opt-in VirusTotal/Pithus/debloat-definition freshness) flavors; GitHub Releases + Obtainium distribution with Fastlane metadata ready for IzzyOnDroid/F-Droid.
- Key integrations: Android PackageManager, hidden APIs (via HiddenApiBypass 6.1), AppOps, UsageStats, StorageStats, SAF, profiles/users, local privileged server, root/ADB/Shizuku/Dhizuku/KernelSU/APatch bridges, Magisk/Zygisk detection, Termux, Tasker `am://` intents, optional network scanners/updaters, Room DB (v10, 7 entities), WorkManager workers, QS tiles, home-screen widgets, DocumentsProvider, AdvancedProtectionManager detection.

## Competitive Landscape
- **Blocker** (2.3k stars, Compose, Apache-2.0): gold standard for IFW component blocking. Recent PRs (Jun 15-20) push Shizuku system-UID support for PM component control (#1548) and IFW rule writing (#1550). Learn from: community rule sync and Shizuku system-UID privilege escalation pattern. Avoid: Firebase Analytics.
- **Hail** (6k stars, Views, GPL-3.0): freeze/unfreeze specialist. No new releases since Jan 2026 (only translation commits). Learn from: transparent launch-through UX. Avoid: competing on freeze-mode breadth.
- **Canta** (5k stars, Compose, LGPL-3.0): Shizuku uninstaller + UAD-ng badges. Most-requested: "allow disabling apps" (#148, 14 reactions) — Canta only uninstalls, not disables. NG already has both. Learn from: UAD-ng safety badge UX. Avoid: single-purpose scope.
- **LibChecker** (6.9k stars, Views, Apache-2.0): released v2.5.4 (Jun 17) with Android 17 adaptation, compileSdk 37, Core 1.19.0, AGP 9.2.1, Material 1.14.0, 16KB page alignment detail, WebUI. Confirms compileSdk 37 + AGP 9.2.1 are production-ready. Learn from: 16KB alignment detection granularity. Avoid: matching LibChecker's library-ID depth.
- **Neo Backup** (3.7k stars, Compose, AGPL-3.0): no updates since May. Learn from: scheduling robustness. Avoid: out-featuring on backup scheduling.
- **SD Maid SE** (7k stars, Compose, GPL-3.0): released v1.7.5-rc0 (Jun 17) with Samsung OneUI force-stop fix (clicking title not button), Realme Android 15 Compose Settings fix, Samsung wifi.intelligence→wifi.ai CorpseFinder mapping. Learn from: OEM-specific workaround catalog depth.
- **Upstream App Manager** (8.3k stars): zero commits since Jun 2, 194 open issues. v4.1.0 was projected for Jun 21 but hasn't shipped. #1948 (Android 17 app list empty, Jun 19 update) confirmed as live regression — maintainer says "cannot proceed until Android 17 source code is available." Issues #1987/#1988 (main-thread blocking) labeled "AI Slop" despite being valid bugs. NG has already fixed the main-thread patterns and shipped Android 17 reflection audit — significant differentiation opportunity.
- **UAD-ng** (8k stars): Xiaomi China ROM "recommended" preset causes bootloop (#1400, 15 comments). #1 request: split "Recommended" into "Recommended" and "Safe" tiers (#583, 10 reactions, 20 comments). NG's 4-tier safety ratings (Safe/Replace/Caution/Unsafe) plus the UAD-ng "delete"→REMOVAL_SAFE fix already address this.

## Security, Privacy, and Reliability
- **Verified:** HiddenApiBypass 6.1 `addHiddenApiExemptions` method is deprecated in commits on main (Jun 5, commit `bac48e5`). Android 17 changes hidden API enforcement — the old exemption approach is being phased out. NG uses this in `AppManager.attachBaseContext()` (`HiddenApiBypass.addHiddenApiExemptions("L")`). No tagged release yet; watch for v6.2+.
- **Verified:** Android 17 BAL hardening: `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated in favor of `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`. NG's routine scheduler, batch operations, and Tasker intent handlers may trigger background activity starts. Source: developer.android.com behavior-changes-17.
- **Verified:** Android 18 preview restricts implicit URI grants on `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_IMAGE_CAPTURE` — auto-grant of URI read/write permissions removed. NG's APK sharing, installer flows, and support bundle sharing use ACTION_SEND and must add explicit `FLAG_GRANT_READ_URI_PERMISSION`. Source: developer.android.com behavior-changes-all.
- **Verified:** `AppDetailsViewModel.setPackageChanged()` has a thread-safety bug. The method is annotated `@GuardedBy("blockerLocker")` but does NOT acquire the lock — `setPackageInfo(true)` at line 1432 runs outside the synchronized block at line 1435. The lambda at line 1434 correctly acquires `mBlockerLocker`, but the preceding call doesn't.
- **Verified:** 6 raw `new Thread()` creations bypass the project's `ThreadUtils.postOnBackgroundThread()` pattern: OpenPGPCrypto.java:73, DebloatDefinitionsUpdater.java:63, TrackerDatabaseFreshnessChecker.java:47, LocalServerManager.java:224+331, OpenPgpKeySelectionDialogFragment.java:54. These are uncaught-exception-unsafe and bypass StrictMode detection.
- **Verified:** BouncyCastle 1.84 remains current. CVE-2026-8149 affects only BC-FIPS (bc-fips), not bcprov-jdk15to18/bcpkix-jdk15to18. No action needed.
- **Verified:** Gradle 9.6.0 released Jun 19 with configuration cache improvements. No security fixes. Optional upgrade from 9.5.1.
- **Verified:** Paparazzi 2.0.0-alpha05 does NOT support AGP 9.2.1. Built against AGP 8.13.2 (android-tools 31.13.2). Release notes explicitly state "supports pre-AGP 9.0 consumers." PR #2318 (AGP 9 migration) is still open, milestoned to alpha05.2. Screenshot testing remains blocked.
- **Verified:** WorkManager 2.11.1 fixes network constraint bypass on Android 15+; 2.11.2 handles SecurityException in NetworkStateTracker. Both require minSdk 23 — blocked by minsdk-21 policy. Security-relevant but unfixable without policy change.
- **Verified:** All other dependencies current: zstd-jni 1.5.7-11, OWASP 12.2.2, Robolectric 4.16.1, Gson 2.14.0, libsu 6.0.0, Shizuku-API 13.1.5. No new CVEs.
- **Verified:** Android Security Bulletin July 2026 not yet published (expected ~Jul 7). June bulletin contains no PackageManager/installer/backup CVEs.
- **Verified:** Upstream #1948 (Android 17 empty app list) confirmed as hidden API breakage. NG's Android17BehaviorContractTest and TypefaceUtil fix address the known static-final-field pattern, but a broader hidden API audit is needed for Android 17 QPR1 Beta.

## Architecture Assessment
- **HiddenApiBypass migration needed:** NG's `AppManager.attachBaseContext()` calls `HiddenApiBypass.addHiddenApiExemptions("L")` — a blanket exemption. The upstream HiddenApiBypass library is deprecating this method for Android 17. The new approach uses mmap-based DEX reading. When v6.2+ ships, NG must update the dependency and potentially adjust its exemption strategy.
- **Thread safety debt in component blocking:** `AppDetailsViewModel.setPackageChanged()` is annotated `@GuardedBy("blockerLocker")` but the method body doesn't acquire the lock before calling `setPackageInfo(true)`. This creates a race where component reload and package info refresh can interleave.
- **Raw Thread creation patterns:** 6 sites create `new Thread()` instead of using `ThreadUtils.postOnBackgroundThread()` or an executor. These threads have no uncaught exception handler, bypass StrictMode detection, and are not lifecycle-aware.
- **TODO: clearApplicationUserData stability:** PackageManagerCompat.java:582 notes that `IActivityManager#clearApplicationUserData()` is more stable than the current `IPackageManager` path. This is a targeted improvement for data-clear reliability.
- **Test coverage gaps by module:** magisk (0/4 files tested), compat (7/32), logcat (7/30), rules (6/26), runningapps (2/8). ZipFileSystem has 8+ empty test stubs marked TODO since 2022.
- **Paparazzi still blocked:** 2.0.0-alpha05 was built against AGP 8.13.2. PR #2318 (AGP 9 migration) targets alpha05.2, no release date. The Roadmap_Blocked.md entry is accurate.

## Rejected Ideas
- Full Compose or Material 3 rewrite: rejected. 143 layouts, Material 1.14.0 is the final Views release (stable, not abandoned). Source: Material Components release notes.
- Immediate minSdk 23 bump: rejected. Policy holds per docs/policy/minsdk-21-ceiling.md. WorkManager 2.11.x security fixes are real but not critical enough to trigger the floor lift. Source: minsdk-21-ceiling.md.
- Cloud backup sync: rejected. Conflicts with floss/default local-first privacy posture. Source: Neo Backup ecosystem analysis.
- JUnit 5 migration: rejected. Robolectric has no first-party JUnit 5 support. Source: Robolectric 4.16 release notes.
- APK Editor / decompile-recompile: rejected. Multi-year effort, upstream #138 stalled since 2020. Source: upstream issue tracker.
- Roborazzi screenshot testing: rejected. Compose transitive dependency. Paparazzi is the alternative but blocked on AGP 9 support. Source: Roborazzi artifact inspection, Paparazzi PR #2318.
- CorpseFinder / orphaned file detection: rejected. SD Maid SE's domain. Source: SD Maid SE feature set.
- App store unification: rejected. Massive scope, upstream #464 Priority 5. Source: upstream issue tracker.
- LibChecker-depth library analysis: rejected. Library ID is LibChecker's core product (6.9k stars). Source: LibChecker 2.5.4.
- Room 3.0 migration: rejected. Requires KSP, new Maven group. Room 2.7.x is stable. Source: Room 3.0 release notes.
- Amazon Appstore: rejected. Discontinued for new Android apps Aug 2025. Source: Amazon developer docs.
- REUSE.toml migration: rejected. Per-file SPDX headers are REUSE 3.3 compliant. Source: REUSE spec.
- Accrescent listing: rejected. Prohibits REQUEST_INSTALL_PACKAGES, ADB, root. Source: Accrescent docs.
- Meta Quest / XR support: rejected for this pass. Upstream #1975 (wireless debugging on Quest 3) is a niche segment. Source: upstream issue tracker.
- Profile UUID migration: rejected for this pass. Legacy name-based IDs work; migration requires data conversion. Low user impact. Source: BaseProfile.java:49 TODO.

## Sources
Official/platform:
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/developer-verification
- https://developer.android.com/jetpack/androidx/releases/core
- https://developer.android.com/jetpack/androidx/releases/work
- https://docs.gradle.org/9.6.0/release-notes.html
- https://source.android.com/docs/security/bulletin/2026/2026-06-01

Dependencies/security:
- https://github.com/LSPosed/AndroidHiddenApiBypass/commit/bac48e5
- https://github.com/LSPosed/AndroidHiddenApiBypass/commit/6b5c485
- https://github.com/dependency-check/DependencyCheck/releases/tag/v12.2.2
- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://github.com/cashapp/paparazzi/releases/tag/2.0.0-alpha05
- https://github.com/cashapp/paparazzi/issues/2095
- https://github.com/gradle/gradle/releases/tag/v9.6.0
- https://github.com/bcgit/bc-java/wiki

Competitors:
- https://github.com/MuntashirAkon/AppManager/issues/1948
- https://github.com/lihenggui/blocker/pull/1548
- https://github.com/lihenggui/blocker/pull/1550
- https://github.com/LibChecker/LibChecker/releases/tag/2.5.4
- https://github.com/d4rken-org/sdmaid-se/releases/tag/v1.7.5-rc0
- https://github.com/samolego/Canta/issues/148
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/583

Community:
- https://www.androidauthority.com/google-sideloading-android-high-friction-process-3633468/
- https://9to5google.com/2026/02/27/samsung-galaxy-update-android-recovery-menu-removed/
- https://f-droid.org/en/docs/Inclusion_Policy/

## Open Questions
- **HiddenApiBypass v6.2 release timing:** The deprecation of `addHiddenApiExemptions` is on main but untagged. When will v6.2+ ship? NG should monitor the repo and prepare migration before adopting.
- **Android 17 hidden API scope:** Upstream #1948 confirms hidden API changes break the app list. NG's existing audit covers static-final-field reflection but may not cover all hidden API changes. A broader hidden API audit against Android 17 QPR1 Beta is needed — requires an Android 17 device or emulator.
- **Google Developer Verification:** Operator decision — register SysAdminDoc ($25, real-identity disclosure) before Sep 2026 enforcement in 4 countries, or accept the 24-hour advanced sideloading flow. EU DMA may modify enforcement in Europe. Business decision, not technical.
