# Research — AppManagerNG

## Executive Summary
AppManagerNG is the most comprehensive open-source Android package manager: 1300+ source files, 143 layouts, 374 tests, 39 ViewModels, 30+ Finder filters, permission/component/signing-cert reference states, IFW component blocking, Tasker automation, QS tiles, widgets, scheduled backups/routines, and operation-history rollback — all under a Material 3 Views surface with AMOLED/dark/light themes, floss/full flavor split, reproducible release CI with SBOM attestation, and weekly OWASP dependency scans. No single competitor matches this breadth. The highest-value direction is closing build-infrastructure gaps (compileSdk 37, screenshot regression testing, reproducible builds prep), then absorbing the narrow feature moats where single-purpose competitors (Canta's UAD-ng badges, Hail's transparent freeze-launch, Blocker's MAT import) out-depth NG on their core feature.

Top opportunities in priority order:
1. compileSdk 37 bump → AndroidX Core 1.19.0 with API 37 compat fixes
2. Paparazzi screenshot regression testing (Views-compatible, no Compose deps)
3. F-Droid reproducible builds hardening (build-tools 34 pin, VCS info, PNG crunch)
4. Main-thread blocking operations audit (upstream #1987/#1988 signal)
5. UAD-ng debloat safety cross-reference for debloater view
6. DDG Tracker Radar as supplementary tracker source
7. MyAndroidTools rule import format
8. APK Signature Scheme v3.2 display in signing cert chip
9. Transparent launch-through frozen apps (Hail's auto-unfreeze/launch/refreeze)
10. Samsung dexopt "Clear Compiler Artifacts" batch operation

## Product Map
- Core workflows: package list/search/filter/tag; package detail inspection with component/permission/AppOp/tracker management; install/uninstall/archive; backup/restore/snapshot import-export; debloat/freeze/profile/batch/routine automation; code editor with diff; file manager; terminal with history/completion; logcat; troubleshooting and privilege-mode diagnostics.
- User personas: rooted Android power users; privacy/de-Googled users (floss flavor); device maintainers and ROM testers; Android developers and reversers; cautious no-root users who rely on ADB/Shizuku/Dhizuku fallbacks.
- Platforms and distribution: Android Views/XML + Material Components 1.13.0; Java/Kotlin; Gradle 9.5.1/AGP 9.2.1; minSdk 21, target/compileSdk 36; `floss` (default, F-Droid-compatible) and `full` (opt-in VirusTotal/Pithus/debloat-definition freshness) flavors; GitHub Releases + Obtainium distribution with Fastlane metadata ready for IzzyOnDroid/F-Droid.
- Key integrations: Android PackageManager, hidden APIs, AppOps, UsageStats, StorageStats, SAF, profiles/users, local privileged server, root/ADB/Shizuku/Dhizuku/KernelSU/APatch bridges, Magisk/Zygisk detection, Termux, Tasker `am://` intents, optional network scanners/updaters, Room DB (v10, 7 entities), WorkManager workers, QS tiles, home-screen widgets, DocumentsProvider, AdvancedProtectionManager detection.

## Competitive Landscape
- **Blocker** (2.3k stars, Compose, Apache-2.0): gold standard for IFW component blocking — full AOSP IFW parameter support with simple/advanced editor UI, IFW+PM dual-mode, and JGit-based online community rule sync. Ships MyAndroidTools backup import and conversion. Learn from: community rule repository model and MAT import path. Avoid: Firebase Analytics in a privacy tool.
- **Hail** (6k stars, Views, GPL-3.0): freeze/unfreeze specialist with auto-freeze on screen lock, transparent launch-through (tap frozen icon → unfreeze → launch → refreeze), multi-tag system, Xposed suspended-app hook, KernelSU App Profile, URI automation schema. Learn from: the transparent launch-through UX (most-requested freeze pattern) and tag-based group toggle. Avoid: competing on freeze-mode breadth — NG's strength is comprehensive management.
- **Canta** (5k stars, Compose, LGPL-3.0): Shizuku uninstaller integrated with the UAD-ng (Universal Android Debloater) crowdsourced database, showing Recommended/Advanced/Expert/Unsafe safety badges per package. Learn from: UAD-ng safety badge integration — the community-maintained database is the killer differentiator. Avoid: the single-purpose scope limitation.
- **LibChecker** (6.9k stars, Views, Apache-2.0): deepest library/ABI inspection — native library identification, 16KB page alignment detection, symbol table stripping, snapshot diff across app updates, versioned rules database (v44), WebUI. Learn from: versioned rules bundle pattern and snapshot comparison. Avoid: matching LibChecker's depth — integrate basic library analytics within NG's broader surface.
- **Neo Backup** (3.7k stars, Compose, AGPL-3.0): backup/restore specialist with scheduled per-tag/per-filter backups, multiple retention, SAF destinations, launcher shortcuts for schedules, split ViewModel architecture. Recently migrated to Nav3 + Compose. Learn from: scheduling robustness and launcher shortcuts for schedules. Avoid: out-featuring Neo Backup on backup scheduling depth.
- **SD Maid SE** (7k stars, Compose, GPL-3.0): storage maintenance with OEM-specific handling (Samsung One UI, Honor, Realme), CorpseFinder, Android TV support, freemium Pro model. Learn from: OEM-specific workaround catalog (Samsung force-stop, Realme cache paths). Avoid: accessibility-service-based cleaning.
- **Upstream App Manager** (8.3k stars, Views, GPL-3.0): NG's baseline. v4.1.0 (imminent) fixes Private Space infinite-load (#1982). Shizuku support explicitly rejected (#55, 31 reactions, closed 2026-06-02) — NG's #1 differentiator. Most-requested features upstream: Shizuku (#55, 37+ reactions), APK Editor (#138, 57 signal), Routine Operations (#61, 26 signal), ADB reconnection (#1596, 23 signal). Emerging upstream issues: Samsung dexopt batch ops gap (#1989), main-thread blocking (#1987/#1988).
- **APKUpdater** (3.8k stars, Compose, GPL-3.0): multi-source update checking (APKMirror, F-Droid, GitHub Releases, Play unofficial). Batch "Install All" button. Validates the version-watch panel concept already in Roadmap_Blocked.md.

## Security, Privacy, and Reliability
- **Verified:** Android 17 static-final-field reflection audit is complete — TypefaceUtil.java was fixed to use in-place Map mutation instead of Field.set(); RootServiceMain.java retains Field.set() but runs in the root server process with ReflectiveOperationException catch. Android17BehaviorContractTest covers both patterns.
- **Verified:** AdvancedProtectionManager detection is implemented (AdvancedProtectionCompat.java, PackageInstallerActivity gating, PrivilegeModeDoctor integration).
- **Verified:** BouncyCastle 1.84 patches all 2026 CVEs (CVE-2026-0636 LDAP injection, CVE-2026-3505 PGP AEAD DoS, CVE-2026-5588 composite verifier empty sig, CVE-2026-5598 FrodoEngine timing). No action needed.
- **Verified:** Gradle 9.3.0 patches CVE-2026-22816 (malicious artifact serving) and CVE-2026-22865 (dependency resolution exploit). NG uses Gradle 9.5.1 — already protected.
- **Verified:** zstd-jni 1.5.7-11 has no disclosed CVEs. Matches latest upstream.
- **Verified:** OWASP Dependency-Check 12.2.2 is current (CVSS v4 scoring, grouped suppressions, unused-rule detection).
- **Verified:** Room 2.7.x and WorkManager 2.10.x have no disclosed CVEs. Both are on pinned legacy lines due to minSdk 21 ceiling. Bug fixes in WorkManager 2.11.x (network constraint failures, periodic work rescheduling) are NOT backported to 2.10.x.
- **Verified:** Android Security Bulletins (April-June 2026) contain no CVEs directly targeting PackageManager, PackageInstaller, backup/restore, or APK handling. CVE-2025-48595 (SQLite, actively exploited) and CVE-2026-0073 (adbd RCE) are notable but do not require AppManagerNG code changes.
- **Verified:** Material Components 1.14.0 is confirmed as the FINAL feature release for Android Views (maintenance mode: critical bug fixes only). Raises minSdk to 23. Adds SplitButton, FocusRingDrawable, expressive typography, ListItemLayout. The gap between 1.13.0 and 1.14.0 is permanently capped.
- **Verified:** Google Developer Verification Program enforcement begins 2026-09-30 in Brazil/Indonesia/Singapore/Thailand. ADB-based installs remain exempt. The "Advanced Flow" for non-ADB sideloading requires Developer Mode, restart, 24-hour wait, biometric confirmation. AppManagerNG's README already documents this.
- **Likely:** compileSdk 37 is needed to unlock AndroidX Core 1.19.0 (API 37 compat fixes, BuildCompat deprecation cleanup). Core 1.18+ requires compileSdk 36.1+; Core 1.19.0 requires compileSdk 37. No minSdk change needed — only compileSdk.
- **Likely:** APK Signature Scheme v3.2 (hybrid PQC with ML-DSA) will ship with Android 17 stable. The apksig-android fork doesn't yet support `isVerifiedUsingV32Scheme()`. v3.2-signed APKs display as v3 on current code (no crash). When the upstream fork adds support, NG should display the PQC indicator.
- **Verified:** Upstream #1987/#1988 (opened 2026-06-18) flag main-thread keystore reads and time-consuming operations. The patterns are likely shared with NG's codebase given the common upstream origin.
- **Verified:** F-Droid reproducible builds require build-tools 34 (35+ breaks apksigcopier verification), disabled VCS info metadata (`vcsInfo.include false` in AGP 8.3+), disabled PNG crunching (`aaptOptions { cruncherEnabled = false }`), and deterministic baseline.prof handling. NG's CI uses the release workflow but hasn't pinned these specific flags.

## Architecture Assessment
- **compileSdk ceiling:** compileSdk 36 blocks AndroidX Core beyond 1.17.0. Core 1.19.0 (released 2026-06-03) has API 37 compatibility fixes needed for proper Android 17 support. Bumping compileSdk to 37 requires no minSdk change and is a low-risk infrastructure upgrade.
- **Screenshot testing gap (UNBLOCKED):** The Roadmap_Blocked.md item cites Roborazzi's Compose transitive dependency as the blocker. **Paparazzi** (cashapp/paparazzi, 2.6k stars, v2.0.0-alpha05) is a Views-compatible alternative: JVM-based (no emulator), supports XML layout inflation via `paparazzi.inflate<MyView>(R.layout.my_layout)`, and renders Material Components correctly since v1.2+ (historic issue #219 resolved). This unblocks screenshot regression testing without adding Compose dependencies.
- **Reproducible builds gap:** NG's CI builds reproducible APKs and publishes `.sha256` sidecars, but hasn't pinned the specific flags F-Droid's infrastructure requires: build-tools 34 (vs default 36.1.0), VCS info disable, PNG crunch disable. These are prerequisites for IzzyOnDroid/F-Droid listing.
- **Main-thread I/O debt:** Upstream #1987 (keystore input-stream read on main thread) and #1988 (time-consuming operations on UI thread) were opened 2026-06-18 against upstream. Given NG's shared codebase origin, the same patterns likely exist. A systematic audit of `StrictMode.ThreadPolicy` violations would surface them.
- **Debloat data source gap:** NG uses the `android-debloat-list` submodule for debloat definitions. Canta's integration with UAD-ng (Universal Android Debloater, community-maintained with Recommended/Advanced/Expert/Unsafe safety ratings) and TrackerControl's layering of DDG Tracker Radar on top of Exodus signatures represent data enrichment opportunities that require no architectural change — just additional data source parsing alongside existing infrastructure.
- **Rule import gap:** Blocker supports importing MyAndroidTools backup files and converting them to IFW rules. Many power users migrating from MAT or Blocker have existing rule sets. NG's component blocking infrastructure supports IFW natively but doesn't accept MAT's export format.
- **Terminal maturity:** Terminal now has command history persistence, tab completion, and init-script support (landed 2026-06-19). Remaining upstream TODOs at `TermActivity.java` are minor polish items, not architectural gaps.
- **ViewModel threading:** Permission/AppOp toggle extraction to ViewModel landed 2026-06-19, resolving the 7 inline TODOs in AppDetailsPermissionsFragment.

## Rejected Ideas
- Full Compose or Material 3 rewrite: rejected. 143 layouts, Material 1.14.0 being the final Views release means the framework is stable, not abandoned. Source: Material Components release notes.
- Immediate minSdk 23 bump: rejected. Policy holds, Material 1.14.0 gap is permanently capped, no security trigger fired. Source: `docs/policy/minsdk-21-ceiling.md`.
- Cloud backup sync: rejected. Conflicts with floss/default local-first privacy posture. Source: Neo Backup ecosystem analysis.
- JUnit 5 migration: rejected. Robolectric has no first-party JUnit 5 support. Source: Robolectric 4.16 release notes.
- APK Editor / decompile-recompile: rejected. Multi-year effort, upstream #138 stalled since 2020. Code editor with diff is the practical alternative. Source: upstream issue tracker.
- Systemless features (Magisk module integration): rejected. Requires deep Magisk internals. Source: upstream #150.
- Backup extras (SMS, call logs, WiFi, BT): rejected. Out of package-manager scope. Source: upstream #568.
- Force-revoke normal permissions via runtime-permissions.xml: rejected. Can brick devices. Source: upstream #725.
- ADB firewall (per-app network blocking): rejected for this pass. Upstream #1754 WIP. Source: upstream issue tracker.
- Accrescent store listing: rejected. Prohibits REQUEST_INSTALL_PACKAGES, ADB, root. Source: Accrescent docs.
- Per-app firewall surface: rejected for this pass. Upstream #1754 WIP, ShizuWall proves concept. Source: upstream #1754, ShizuWall.
- APK clone via package-name-swap: rejected. High complexity, code-signing trust issues. Source: upstream #1029.
- App Details tab reordering: rejected. Pro Mode toggle already handles progressive disclosure. Source: upstream #1353.
- ADB mode reconnection UI: rejected as standalone item. Root cause is OS-level wireless debugging timeout; Android 17 ADB Wi-Fi 2.0 addresses at platform level. Source: upstream #1596.
- Notification.ProgressStyle: rejected. Designed for rideshare/delivery, not package management. Source: Android 16 API docs.
- CorpseFinder / orphaned file detection: rejected. Storage cleaning is SD Maid SE's domain, not a package manager's. Adding it dilutes NG's focus. Source: SD Maid SE feature set.
- App store unification (Aurora/F-Droid): rejected. Massive scope, upstream #464 is Priority 5 with no progress. Source: upstream issue tracker.
- LibChecker-depth library analysis: rejected. Library identification is LibChecker's core product (6.9k stars, dedicated rules DB). NG should integrate basic analytics, not compete. Source: LibChecker/LibChecker.
- Room 3.0 migration: rejected for this pass. Room 3.0 requires KSP (no KAPT), new Maven group (`androidx.room3`). Room 2.7.x is stable and adequate. Source: Room 3.0 release notes.
- Roborazzi screenshot testing: rejected due to Compose transitive dependency. Paparazzi is the viable alternative. Source: Roborazzi core artifact inspection, Paparazzi #219 resolution.
- Amazon Appstore: rejected. Amazon discontinued support for new Android apps August 2025. Source: Amazon developer docs.
- REUSE.toml migration: rejected for this pass. Current per-file SPDX headers are compliant with REUSE 3.3. Migration to REUSE.toml is cosmetic. Source: REUSE spec 3.3.

## Sources
Official/platform:
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/developer-verification
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/biometric
- https://docs.gradle.org/9.5.1/release-notes.html
- https://source.android.com/docs/security/bulletin/2026/2026-06-01

Dependencies/security:
- https://github.com/dependency-check/DependencyCheck
- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://github.com/bcgit/bc-java/wiki
- https://github.com/robolectric/robolectric/releases/tag/robolectric-4.16
- https://github.com/cashapp/paparazzi
- https://m3.material.io/blog/material-is-compose-first

Competitors and adjacent projects:
- https://github.com/MuntashirAkon/AppManager
- https://github.com/lihenggui/blocker
- https://github.com/aistra0528/Hail
- https://github.com/Hamza417/Inure
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/LibChecker/LibChecker
- https://github.com/samolego/Canta
- https://github.com/rumboalla/apkupdater
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/AhmetCanArslan/ShizuWall
- https://github.com/nicofrost/Dhizuku
- https://github.com/DUpdateSystem/UpgradeAll

Debloat/tracker data:
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/nicofrost/TrackerControl
- https://staticcdn.duckduckgo.com/trackerblocking/v6/current/android-tds.json

Distribution:
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
- https://f-droid.org/en/docs/Reproducible_Builds/

## Open Questions
- **AndroidX Core 1.19.0 compileSdk requirement:** Verify whether compileSdk 37 is available in current build-tools or requires a separate SDK platform install. If 37 isn't available yet, hold at 1.17.0.
- **Paparazzi platform rendering consistency:** Paparazzi issue #1465 documents rendering differences between macOS and Linux CI. Pinning CI runner OS is the workaround — verify this works before adopting.
- **UAD-ng data format stability:** The Universal Debloater list format has changed across major versions. Verify the current JSON schema is stable enough for automated parsing, or plan for a schema version check.
- **Google Developer Verification:** Operator decision — register ($25, real-identity disclosure) or accept the 24-hour advanced sideloading flow starting Sep 2026 in 4 countries. EU DMA may modify enforcement in Europe. Business decision, not technical.
