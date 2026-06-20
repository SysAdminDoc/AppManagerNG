# Research — AppManagerNG

## Executive Summary
AppManagerNG is the most comprehensive open-source Android package manager: 926 source files, 39 ViewModels, 30+ Finder filter options, permission/component/signing-cert monitoring with reference states, IFW component blocking, Tasker automation, QS tiles, home-screen widgets, scheduled backup/routines, and an operation-history rollback system — all under a Material 3 Views surface with AMOLED/dark/light themes, a floss/full flavor split, reproducible release CI with SBOM attestation, and weekly OWASP dependency scans. No single competitor matches this breadth. The highest-value direction is hardening the build/test pipeline (dependency tooling, screenshot regression, ViewModel thread safety), unblocking distribution (IzzyOnDroid/F-Droid), and closing the remaining narrow feature gaps where single-purpose competitors (Hail's auto-freeze, Blocker's community IFW rules) out-depth NG on their core feature. Top opportunities in priority order: OWASP Dependency-Check upgrade to 12.2.2, AGP 9.3.0 + Gradle 9.5.1 build-infra upgrade, Roborazzi screenshot regression testing, ViewModel extraction for permission operations, minor dependency bumps (zstd-jni, AndroidX Core), terminal history/completion, and auto-freeze on screen lock.

## Product Map
- Core workflows: package list/search/filter/tag; package detail inspection with component/permission/AppOp/tracker management; install/uninstall/archive; backup/restore/snapshot import-export; debloat/freeze/profile/batch/routine automation; code editor with diff; file manager; terminal; logcat; troubleshooting and privilege-mode diagnostics.
- User personas: rooted Android power users; privacy/de-Googled users (floss flavor); device maintainers and ROM testers; Android developers and reversers; cautious no-root users who rely on ADB/Shizuku/Dhizuku fallbacks.
- Platforms and distribution: Android Views/XML + Material Components; Java/Kotlin; Gradle 9.4.1/AGP 9.2.0; minSdk 21, target/compileSdk 36; `floss` (default, F-Droid-compatible) and `full` (opt-in VirusTotal/Pithus/debloat-definition freshness) flavors; GitHub Releases + Obtainium distribution with Fastlane metadata ready for IzzyOnDroid/F-Droid.
- Key integrations: Android PackageManager, hidden APIs, AppOps, UsageStats, StorageStats, SAF, profiles/users, local privileged server, root/ADB/Shizuku/Dhizuku/KernelSU/APatch bridges, Magisk/Zygisk detection, Termux, Tasker `am://` intents, optional network scanners/updaters, Room DB (v10, 7 entities), WorkManager workers, QS tiles, home-screen widgets, DocumentsProvider.

## Competitive Landscape
- **Blocker** (2.3k stars, Compose, Apache-2.0): the gold standard for IFW component blocking — full AOSP IFW parameter support with simple/advanced editor UI, combined IFW+PM dual-mode blocking, and JGit-based online rule sync from community sources. Learn from its community rule-sharing model and full IFW parameter coverage. Avoid its Firebase Analytics (controversial in a privacy tool).
- **Hail** (6k stars, Compose, GPL-3.0): freeze/unfreeze specialist with auto-freeze on screen lock, multi-tag system, Xposed hook for suspended-app unfreezing, KernelSU App Profile support, URI schema for external automation, and digital-assistant launch. Learn from the auto-freeze UX (most-requested freeze pattern). Avoid competing on freeze-mode breadth — NG's strength is comprehensive management, not single-feature depth.
- **Inure** (1.8k stars, custom Views, GPL-3.0): premium custom UI, VirusTotal integration, batch installer, MIUI AppOps descriptions. Learn from compact technical summaries and polished visual hierarchy. Avoid the custom UI framework maintenance burden — stick with Material Components.
- **Neo Backup** (3.4k+ stars, Compose, GPL-3.0): backup/restore specialist with robust scheduling, launcher shortcuts for schedules, blocklists, storage pre-checks, and multi-ViewModel split. Learn from scheduling robustness and previewable backup parts. Avoid trying to out-feature Neo Backup on backup scheduling depth.
- **SD Maid SE** (7k stars, Compose, GPL-3.0): storage maintenance with OEM-specific handling (Samsung One UI, Honor, Realme, Compose-based Settings), CorpseFinder, Android TV launcher support, freemium Pro model. Learn from OEM-specific knowledge and TV support patterns. Avoid accessibility-service-based cleaning (fragile, breaks with every OEM UI update).
- **LibChecker** (6.9k stars, Views, Apache-2.0): deepest library/ABI inspection — native library identification, SDK/framework detection, snapshot/diff across app updates, ABI distribution statistics. Learn from the library identification database concept. Avoid trying to match LibChecker's depth — integrate basic library analytics within NG's broader inspection surface.
- **Upstream App Manager** (8.3k stars, Views, GPL-3.0): NG's baseline. Milestone v4.1.0 (due 2026-06-21) addresses Private Space infinite-load (#1982, P1/Sev0). Milestone v4.2.0 (due 2027-06-20, 24 items) includes systemless features, APK editor, backup extras, custom backup folders. Shizuku support rejected upstream twice (#55, #1042) — NG's key differentiator. Learn from active issue signal. Avoid porting features without NG-specific guardrails and tests.
- **PermissionManagerX** (695 stars, Java, AGPL-3.0): focused permission/AppOp manager with reference states. NG already has more comprehensive reference-state infrastructure (PermissionReferenceRule, AppOpReferenceRule, PermissionSnapshotStore, ComponentSnapshotStore, SigningCertSnapshotStore — 2048 lines of monitoring code).

## Security, Privacy, and Reliability
- **Verified (previous pass, still current):** privileged terminal launch hardening, snapshot import preview with selective restore, optional-network transparency ledger, support bundle preview with per-section export controls, release/version consistency gate, dependency floor drift gate, Android 16 strict-intent manifest audit tests, and translation/pseudolocale quality gate are all implemented and tested.
- **Verified:** Private Space handling is defensively coded — `ApplicationItem.isAppInactive()` wraps the cross-user query in `try/catch(RuntimeException)` with warning log, returning `false` on failure. The upstream #1982 P1/Sev0 bug (Pixel 10 Pro Fold, Android 16) is already handled.
- **Verified:** MIUI and HyperOS installer workarounds exist in `PackageInstallerCompat.java` (MIUI 12.5+ retry logic, HyperOS 2.0+ system-app installer constraint handling).
- **Verified:** Android 17 (API 37, stable June 2026) introduces static-final-field reflection restrictions (`IllegalAccessException` on `Field.set()`), mandatory `ACCESS_LOCAL_NETWORK` runtime permission, native dynamic code loading must be read-only, and `AdvancedProtectionManager` API for detecting Advanced Protection Mode (which blocks sideloading entirely). NG already declares `ACCESS_LOCAL_NETWORK` in the manifest. The reflection restriction needs an audit of root/Shizuku code paths that modify framework fields. AAPM detection should gate install operations with clear messaging.
- **Verified:** Gradle 9.3.0 patches CVE-2026-22816 (malicious artifact serving during build) and CVE-2026-22865 (dependency resolution exploit). Combined with the Windows file handle leak fix, the AGP 9.3.0 + Gradle 9.5.1 upgrade is both a security and productivity improvement.
- **Verified:** OWASP Dependency-Check is pinned at 10.0.3, which lacks CVSS v4 scoring (shipped in 12.0), grouped suppression rules (12.1), and suppression-failure flags for unused rules (12.2). The weekly CI scan still runs but misses newer vulnerability database features. Upgrade is safe and host-verifiable.
- **Verified:** AGP 9.2.0 has a known file handle leak on `classes.jar` that prevents rebuilds on Windows (documented in AGP 9.3.0 release notes). AGP 9.3.0 + Gradle 9.5.1 fixes this — directly relevant to NG's Windows development environment.
- **Verified:** zstd-jni 1.5.7-7 has JNI-level bug fixes available through 1.5.7-11 (same underlying Zstd 1.5.7 algorithm, patch-level platform fixes only). Safe to bump.
- **Likely:** Material Components 1.14.0 is the FINAL feature release for Android Views — the library is now in maintenance mode (critical bug fixes only), with Compose designated as the path forward. The gap between 1.13.0 and 1.14.0 (SplitButton, FocusRingDrawable, expressive typography, motion tokens) will NOT widen further. This changes the cost-benefit of a future minSdk 23 lift slightly: the feature unlock from Material 1.14.0 is capped.
- **Likely:** Robolectric 4.16 dropped SDK 21/22 test simulation — but NG's tests already target SDK 23+ (`@Config(sdk = Build.VERSION_CODES.N)` / `@Config(sdk = Build.VERSION_CODES.M)` / `@Config(sdk = 35)`). No breakage expected.
- **Verified:** 7 TODO comments in `AppDetailsPermissionsFragment.java` mark permission toggle operations that run on the fragment's thread instead of through the ViewModel. These are thread-safety risks for concurrent permission changes.

## Architecture Assessment
- **Build tooling gap:** OWASP Dependency-Check 10.0.3 is 2 major versions behind (12.2.2 available). CVSS v4, grouped suppressions, and unused-rule detection are missing from the weekly CI scan. AGP 9.3.0 fixes Windows file handle leak. Both are safe, no-risk upgrades.
- **Test infrastructure gap:** No screenshot regression testing. The V2 premium design token system, AMOLED/dark/light themes, and ongoing layout polish create a continuous visual regression risk. Roborazzi integrates with the existing JUnit 4 + Robolectric setup and runs in CI without emulators.
- **Thread-safety debt in AppDetailsPermissionsFragment:** 7 inline TODOs mark permission operations that should route through the ViewModel for proper thread management. Current code performs permission grants/revokes, AppOp changes, and runtime permission updates directly in the fragment on the calling thread. Risk: concurrent operations on the same permission can race.
- **Terminal maturity gap:** `TermActivity.java` line 49 says `// TODO: 11/9/23 Replace it with an actual terminal`. The current implementation is a minimal shell wrapper with ANSI parsing, no command history persistence, no tab completion, and no init-script support. Three additional TODOs at lines 98, 107, and 184 mark these specific capabilities.
- **Android 17 reflection audit needed:** Android 17's static-final-field restriction throws `IllegalAccessException` when any code attempts `Field.set()` on a `static final` field. Root/Shizuku/hidden-API code paths that modify framework constants at runtime will crash. Need a systematic grep for `Field.set` patterns across all source roots, particularly in `compat/`, `hiddenapi/`, `servermanager/`, and `ipc/`.
- **Advanced Protection Mode detection:** New `AdvancedProtectionManager` API (permission `QUERY_ADVANCED_PROTECTION_MODE`, already declared in manifest) enables checking whether sideloading is blocked. When AAPM is active, all install operations should gracefully degrade to read-only mode with clear user messaging.
- **ADB mode reconnection UX:** Upstream #1596 (22 comments) is the single most-discussed usability complaint. Android 17's ADB Wi-Fi 2.0 with auto-reconnect will help on newer devices, but current users need better mode-loss detection and a clear "Tap to reconnect" action instead of the confusing init spinner.
- **Distribution readiness:** Fastlane metadata is complete (title, descriptions, icon, 9 phone screenshots, changelogs). IzzyOnDroid submission requires only the external submission step (no code changes). F-Droid main repo requires a fdroiddata YAML metadata file and build recipe. Google Developer Verification Program enforcement begins 2026-09-30 in Brazil/Indonesia/Singapore/Thailand — an operator-level strategic decision.
- **Dependency floor stability:** Material 1.14.0 being the FINAL Views release means the minSdk-21 ceiling dependency gap is permanently capped. The pinned-cluster cost is fixed and will not increase. No forced-decision trigger has fired as of 2026-06-19.

## Rejected Ideas
- Full Compose or Material 3 rewrite: rejected. Mature Views/XML codebase with 143 layouts, and Material 1.14.0 being the final Views release means the framework is stable, not abandoned. Source: Material Components release notes.
- Immediate minSdk 23 bump: rejected. Policy holds through v0.7.x, Material 1.14.0 gap is permanently capped, no security trigger fired. Source: `docs/policy/minsdk-21-ceiling.md`.
- Cloud backup sync: rejected. Conflicts with floss/default local-first privacy posture. Source: Neo Backup/Swift Backup ecosystem analysis.
- JUnit 5 migration: rejected. Robolectric has no first-party JUnit 5 support; the community extension (`junit5-robolectric-extension`) is unstable. Source: Robolectric 4.16 release notes, JUnit 5 extension repo.
- APK Editor / decompile-recompile: rejected. Upstream #138 (50 comments) is partially implemented upstream but remains a multi-year effort. NG's code editor with diff view is the practical alternative. Source: upstream issue tracker.
- Systemless features (Magisk module integration): rejected. Requires deep Magisk internals knowledge and per-module testing. Upstream #150 is marked v4.2.0. Source: upstream milestones.
- Backup extras (SMS, call logs, WiFi, Bluetooth): rejected. Out of package-manager scope, requires content-provider access and per-OEM testing. Upstream #568 is marked v4.2.0. Source: upstream milestones.
- Force-revoke normal permissions via runtime-permissions.xml: rejected. Upstream #725 (25 comments) requires direct XML manipulation that can brick devices. Source: upstream issue analysis.
- ADB firewall (per-app network blocking without root): rejected for this pass. Upstream #1754 is WIP. Net policy management already exists in NG. Source: upstream issue tracker.
- Accrescent store listing: rejected. Accrescent prohibits `REQUEST_INSTALL_PACKAGES`, ADB access, and root utilization — all core NG functionality. Source: Accrescent requirements docs.
- Amazon Appstore: rejected. Amazon discontinued support for new Android apps as of August 2025. Source: Amazon developer docs.
- Per-app firewall / network blocking surface: rejected for this pass. Upstream #1754 (WIP) and ShizuWall prove the concept works via AppOps `OP_WIFI`/`OP_DATA`, but AppManagerNG already shows net policy in App Details and the debloater. A dedicated firewall surface is a product decision, not a research gap. Source: upstream #1754, ShizuWall, Athena.
- APK clone via package-name-swap: rejected. Upstream #1029 (2 reactions) requests cloning via APK modification + resign. Requires apktool-level integration, high complexity, and introduces code-signing trust issues. Source: upstream issue tracker.
- App Details tab reordering/visibility: rejected. Upstream #1353 requests configurable tab order. Pro Mode toggle already provides progressive disclosure; per-tab visibility preferences add settings complexity without proportional user value for a power-user tool. Source: upstream issue tracker.
- ADB mode reconnection UI: rejected as standalone roadmap item. The "Init..." spinner complaint (upstream #1596, 22 comments) is a real UX issue, but the root cause is an OS-level wireless debugging timeout, not an AppManagerNG bug. Android 17's ADB Wi-Fi 2.0 with auto-reconnect addresses this at the platform level. Source: upstream #1596, Android 17 ADB Wi-Fi 2.0 docs.
- Notification.ProgressStyle adoption: rejected. Android 16's new progress notification style is designed for rideshare/delivery flows, not package manager operations. Existing notification patterns are adequate. Source: Android 16 API docs.
- Items already in Roadmap_Blocked.md: dedicated freeze surface/widget, boot-component manager, FM trash bin, version-watch panel, analytics dashboard, profile sharing QR/deep link, TV navigation, FM operations service, local server secure session, visual/a11y/theme polish passes, settings IA cleanup, multi-user capability matrix.

## Sources
Official/platform:
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/developer-verification
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/biometric
- https://docs.gradle.org/9.5.1/release-notes.html

Dependencies/security:
- https://github.com/dependency-check/DependencyCheck
- https://github.com/material-components/material-components-android/releases
- https://www.bouncycastle.org/download/bouncy-castle-java/
- https://github.com/robolectric/robolectric/releases/tag/robolectric-4.16
- https://github.com/takahirom/roborazzi/
- https://m3.material.io/blog/material-is-compose-first

Competitors and adjacent projects:
- https://github.com/MuntashirAkon/AppManager
- https://github.com/lihenggui/blocker
- https://github.com/aistra0528/Hail
- https://github.com/Hamza417/Inure
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/LibChecker/LibChecker
- https://github.com/mirfatif/PermissionManagerX
- https://github.com/rumboalla/apkupdater
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/samolego/Canta

Platform/Android:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/about/versions/17/changes/messagequeue
- https://source.android.com/docs/security/bulletin/2026/2026-06-01

Distribution:
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://accrescent.app/docs/guide/publish/requirements.html

## Open Questions
- **AndroidX Core 1.19.0 minSdk:** Verify whether 1.18+ or 1.19.0 raised minSdk past 21 before bumping. If still 21, safe to take.
- **Google Developer Verification:** Operator decision — register ($25, real-identity disclosure, Google ToS acceptance) or accept the 24-hour advanced sideloading flow starting Sep 2026. EU DMA challenge may modify the policy in Europe. This is a business decision, not a technical one.
