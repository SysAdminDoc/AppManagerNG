# Research — AppManagerNG

## Executive Summary

AppManagerNG is a GPL-3.0-or-later Android package-manager fork (minSdk 21, target 36) built on Android Views, Material Components 1.13.0, Java/Kotlin, Room, WorkManager, Gradle 9.4.1/AGP 9.2.0, `floss`/`full` flavors, and root/ADB/Shizuku/Dhizuku privilege lanes. 910 source files (237 NG-only additions since the 3d11bcb fork), 354 unit tests, 4 instrumented tests, 142 layout files.

Its strongest current shape is a local-first power-user control plane: app inspection, package-state editing, split-APK installation, backup/restore, profile automation, component blocking, debloating, and permission auditing — with NG-specific additions like Permission Inspector, app archiving, scheduled auto-backup, per-app tags/notes, debloat risk tables, package-event profile triggers, and a routine scheduler.

The highest-value direction is not more breadth; it is **trust under pressure and workflow friction reduction**: make component blocking belt-and-suspenders reliable (IFW+PM dual-mode), give freeze workflows a dedicated surface, make split-APK installs self-explanatory, let debloat presets survive OTA updates, warn before backup restores cross API boundaries, and extend the routine scheduler to cover cache/data clearing. The reliability, privilege-integrity, and distribution-documentation items from prior research passes remain load-bearing.

Top opportunities in priority order:
1. IFW+PM dual-mode component blocking (reliability, competitive parity with Blocker).
2. Human-readable split APK labels in installer chooser (UX, competitive parity with InstallerX-Revived).
3. Debloat preset export/import for OTA re-application (workflow, competitive parity with UAD-NG/Canta).
4. Dedicated freeze surface with home-screen widget (UX, competitive parity with Hail).
5. Biometric gate option for privileged/destructive operations (trust, upstream #1738).
6. Backup restore API-level compatibility warnings (trust, community pain point).
7. Scheduled cache/data clearing as routine operation type (feature, competitive parity with SD Maid SE).
8. Prior-pass items remain: main-list load failure watchdog, distribution doc link-rot repair, backup round-trip CI, form-factor permission gating, hostile APK corpus, installer caller result support.

## Product Map

- **Core workflows**: app list inspection, app details (components/permissions/app-ops/signatures/usage), install/update/split-package handling, batch operations, backup/restore (multi-format, encrypted), profiles/routines, debloating, component blocking, tracker/library scanning, permission inspector, logcat viewer, file manager, code editor, terminal (stub).
- **User personas**: privacy-conscious power users, ROM/root tinkerers, corporate device maintainers, debloat-and-forget casual users, automation-heavy DevOps/sysadmin users.
- **Platforms and distribution**: Android minSdk 21 through target/compile 36 (API 37 audit pending), phone/tablet with declared leanback support, GitHub Releases/Obtainium (primary), F-Droid/IzzyOnDroid (planned), `floss` (offline-only) and `full` (opt-in network) flavors.
- **Key integrations**: PackageManager/PackageInstaller, AppOps, UsageStats, WorkManager, Room, SAF, split APK/APKS/APKM/XAPK parsing, root shell (libsu 6.0.0), ADB TCP, Shizuku 13.1.5, Dhizuku, OpenPGP (BouncyCastle 1.84), optional VirusTotal/Pithus (full flavor).

## Competitive Landscape

**Upstream App Manager (MuntashirAkon)** — The canonical implementation. v4.1.0 milestone (39 issues, due 2026-06-21) includes critical restore fixes (#1286 Samsung/A14 SecurityException), HMAC mutual auth for the local privileged channel, and native run_server. Permanently rejected Shizuku (#55) — rootless power is NG's structural lane. Key open issues for NG: ADB mode silently lost (#1596), one-tap freeze UX (#1558), root not detected on A16 (#1967), assistant-launched services (#1973), SAF documents provider (#516), crash monitor (#163), D-pad/TV navigation (#107). Learn from its accepted bug queue and v4.1.0 restore hardening. Avoid importing upstream behavior that hides exceptions behind stale UI.

**Blocker (lihenggui)** — Compose/M3 component blocker with IFW+PM dual-mode ("belt-and-suspenders") blocking — stronger than either alone. Ships community tracker-rule sync and batch search-result operations across multiple apps. MyAndroidTools import is broken in recent releases (v2.0.3058+). Learn the dual-mode blocking and cross-app search. Avoid the tracker-rule cloud sync complexity until the local rule store is fully mature.

**Hail (aistra0528)** — Freeze/suspend specialist supporting four privilege models (Device Owner, Dhizuku, Root, Shizuku) with graceful degradation. Ships a dedicated frozen-apps grid with one-tap toggle, home-screen widget, and grayscale launcher icons for suspended apps. Suspend vs. disable distinction is a UX win (app visible in launcher as gray, not hidden). Learn the freeze grid, widget, and multi-model privilege fallback. Avoid full device-owner mode until Dhizuku executor parity lands.

**Inure (Hamza417)** — The aesthetic competitor: per-app usage analytics (1-year history with granular drill-down), battery optimization panel, per-app notes/tags, VirusTotal hash lookup, boot manager, custom theme engine, tabbed app-detail layout. Paid unlocker model. Learn the analytics dashboard, tabbed detail layout, and boot-component view. Avoid the theme-engine expansion — NG's identity is control, not customization.

**InstallerX-Revived / SAI** — Split APK installer that auto-selects best ABI+density+language splits from APKS/APKM/XAPK bundles with human-readable descriptions (not raw split filenames). Silent install via Shizuku/root. Per-split UI with guidance. Learn the human-readable split labels and auto-selection. Avoid building a general-purpose installer — NG's installer serves its inspection workflows.

**Canta / UAD-NG** — Debloat tools with community-maintained per-OEM safety-tier databases (Safe/Caution/Expert/Unsafe). Canta issue #355 reveals success messages shown even when uninstall state is ambiguous. UAD-NG's package database JSON is the portable artifact. Learn the safety-tier presentation and the debloat-list export for OTA re-application. Avoid shipping "recommended" presets without device/vendor-specific rollback evidence.

**Neo-Backup** — Root-only backup with per-app custom schedules (unlimited), restore-without-reboot for system apps, custom backup filters (by install source, last-used date, size). Issue area #1027: storage backend flexibility. Learn scheduled backup flexibility and storage-location clarity. Avoid adding backup modes until overwrite, recovery, and CI round-trip evidence are stable.

**SD Maid 2/SE** — Trusted maintenance UX: app cleaner with per-app cache attribution, corpse detection (post-uninstall residue), scheduled cache clearing, Android TV launcher support, signing fingerprints, changelog discipline. Not an app manager — deliberately scoped to cleaning. Learn the scheduled cache-clearing pattern and TV support polish. Avoid general "cleaner" features that compete with SD Maid's core.

## Security, Privacy, and Reliability

- **Verified resolved**: BouncyCastle bcprov/bcpkix is at 1.84, closing CVE-2026-0636 (LDAP injection, critical) and CVE-2025-14813 (GOSTCTR broken block cipher). No action needed.
- **Verified risk**: `MainViewModel.java:593-619` catches app-list load failures and logs them but does not publish an explicit failed-load state with retry/support details. Upstream #1982/#1825/#1948 confirm this is a current reliability gap. (Existing roadmap item.)
- **Verified risk**: `RootServiceManager` external-storage staging TOCTOU issue is tracked in ROADMAP. (Existing roadmap item.)
- **Verified risk**: Reviewer-facing documentation references are stale — README/CLAUDE/workflow references point at missing distribution, package-visibility, reproducible-build, sideload, and project-context docs. (Existing roadmap item.)
- **Verified risk**: `ApkFile.java:236` carries an archive parsing FIXME; Android's zip path traversal guidance (CVE-2024-32653 pattern) and the Apktool CVE-2026-39973 (path traversal in resource decoding) reinforce the existing hostile APK corpus roadmap item.
- **Verified risk**: Component blocking uses only one mechanism (IFW or PM) per operation. Blocker's IFW+PM dual-mode proves combined blocking is more reliable: IFW rules survive factory reset while PM disables don't, and PM disables take effect immediately while IFW rules may need a reboot on some ROMs.
- **Verified present**: `LeftoverScanner.java` already detects post-uninstall orphan directories under `Android/{data,obb,media}/`. UI wiring (recursive size, deletion with op-history) is noted as pending on T19-B. Not a new gap.
- **Verified present**: `PackageStateVerifier.java` verifies post-operation package state. Future work should extend this verifier for debloat operations specifically (Canta #355 pattern).
- **Likely risk**: Wear/TV users hit permission/settings prompts that don't exist on their form factor. Manifest declares leanback support; upstream #1823 and PermissionManagerX #61 have related complaints. (Existing roadmap item.)
- **Likely risk**: SplitApkChooser presents raw split entry names in a multi-choice dialog. Users cannot distinguish `config.arm64_v8a` from `config.en` without Android packaging knowledge. InstallerX-Revived shows this is solvable with a human-readable label layer.
- **Platform risk (A17)**: Android 17 Beta 4.1 (2026-06-01) introduces: static-final field reflection ban (breaks hidden-API bypass), lock-free MessageQueue (breaks IPC shims), ACCESS_LOCAL_NETWORK runtime permission (mDNS discovery), APK Signature Scheme v3.2 (ML-DSA hybrid PQC), AdvancedProtectionManager (blocks sideloading), Certificate Transparency enforcement, native DCL hardening, BAL MODE_BACKGROUND_ACTIVITY_START_ALLOWED deprecation. (Existing A17 audit batch roadmap item covers this.)
- **Needs live validation**: exact TV/Wear behavior, release artifact sizes for IzzyOnDroid submission, RootService state after existing work finishes.

## Architecture Assessment

- **Component blocking should support dual-mode (IFW+PM)**: The existing blocking infrastructure writes IFW rules (root) or calls `setComponentEnabledSetting` (ADB/root) but never both for belt-and-suspenders reliability. Blocker's `IFW_PLUS_PM` mode applies both, ensuring the block survives both factory reset (IFW) and immediate-effect scenarios (PM). Implementation: extend `ComponentRule` with a combined mode, apply both in sequence, verify both states.
- **Split APK chooser needs a label-resolution layer**: `SplitApkChooser` passes raw `ApkFile.Entry` names to `SearchableMultiChoiceDialogBuilder`. A thin mapping layer (ABI → "ARM 64-bit", density → "High-DPI resources", locale → "French language pack") makes the chooser self-explanatory.
- **Freeze UX is buried**: Freeze/unfreeze works but is only accessible from app details or batch ops. Hail's dedicated grid (all frozen apps, one-tap toggle, widget) is the competitive standard. NG should add a dedicated freeze surface under the main menu, reusing existing freeze/unfreeze plumbing.
- **Routine scheduler (v0.6.0) should include cache/data clearing**: SD Maid SE's scheduled cache-clearing is the #1 feature users associate with automated maintenance. NG's `RoutineScheduler` already has the executor pattern; adding a `CLEAR_CACHE` operation type extends it without new plumbing.
- **Backup restore should warn about API-level boundaries**: Community complaints about Neo-Backup reliability across Android version jumps (e.g., A12 backup → A14 restore) apply equally to NG. Backup metadata already includes `targetSdk`; a pre-restore warning when the backup's API level differs significantly from the device's is a trust improvement.
- **Debloat presets need an export/import path**: Profiles handle per-app configuration, but debloat presets (the set of packages marked for removal) have no export/import flow. Users who debloat before an OTA must re-select everything manually. A JSON export of the current debloat selections, importable after OTA, is a workflow fix.
- **Tests**: 354 unit tests cover most packages. Key untested areas: `ipc/`, `logcat/`, `magisk/`, `servermanager/`, `intercept/` — all handle privileged operations. Instrumented tests (4 files) cover hidden-API compat, Room migration, and app-launch smoke. Backup round-trip and hostile-archive integration tests are roadmapped.

## Rejected Ideas

- **Post-uninstall orphan/residue detection**: Rejected — `LeftoverScanner.java` already implements this (orphan detection under `Android/{data,obb,media}/`). UI wiring is pending on T19-B; no new roadmap item needed.
- **Shizuku `newProcess` → `UserService` migration**: Rejected — `newProcess` in the codebase is NG's own `AMService.newProcess()` AIDL method for remote process spawning, not Shizuku's deprecated API. Not applicable.
- **Full device-owner/profile-owner mode**: Rejected — depends on Dhizuku executor parity (existing roadmap item) and would expand privilege semantics prematurely.
- **Generic cleaner/storage optimizer**: Rejected — SD Maid SE owns this better; NG's fit is package/rule/backup control. Cache clearing as a routine operation type is the bounded intersection.
- **Compose rewrite**: Rejected — repo instructions keep Android Views; Material 1.14.0 raises minSdk to 23 against the minSdk 21 ceiling policy.
- **Cloud backup/sync**: Rejected — contradicts local-first privacy posture and adds multi-user/cloud trust work.
- **Tracker-rule cloud sync (Blocker pattern)**: Rejected for now — adds network dependency and community-curation governance overhead; local rule store maturity comes first.
- **App update price-change tracking (AppDash pattern)**: Rejected — niche commercial feature that requires Play Store scraping; doesn't fit local-first posture.
- **Custom theme engine (Inure pattern)**: Rejected — NG's identity is control/trust, not visual customization; existing M3 dynamic color + AMOLED/dark/light is sufficient.
- **OEM bloatware safety-tier database (UAD-NG)**: Rejected as separate item — existing debloat work already added `OemBloatRiskTable`, OEM resolution, and bloatware filter predicates; full UAD model/region ingest is parked behind an external dataset blocker.
- **New ApplicationStartInfo, AppFunctions, standby bucket, Advanced Protection, Android 17, and installer-result items**: Rejected as duplicates — ROADMAP already contains actionable entries for each.

## Sources

Upstream and OSS:
- https://github.com/MuntashirAkon/AppManager (v4.1.0 milestone, issues #1982/#1967/#1973/#1596/#1558/#516/#163/#107/#55)
- https://github.com/lihenggui/blocker (IFW+PM dual-mode, component search)
- https://github.com/aistra0528/Hail (freeze grid, widget, multi-privilege)
- https://github.com/Hamza417/Inure (analytics, boot manager, tabbed detail)
- https://github.com/wxxsfxyzm/InstallerX-Revived (split APK UX, issue #672)
- https://github.com/samolego/Canta (issue #355: ambiguous success state)
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation (safety tiers, debloat export)
- https://github.com/NeoApplications/Neo-Backup (scheduled backup, issue #1027)
- https://github.com/mirfatif/PermissionManagerX (reference states, drift, issue #61)
- https://github.com/d4rken-org/sdmaid-se (TV support, cache clearing, corpse detection)
- https://github.com/rumboalla/apkupdater (multi-source update checking, TV support)
- https://github.com/RikkaApps/Shizuku (v13.6.0 auto-start, issue #1251)
- https://github.com/iamr0s/Dhizuku (v2.11.2, device-owner delegation)
- https://github.com/timschneeb/awesome-shizuku

Commercial and adjacent:
- https://appdash.app/ (pricing tiers, paywalled features)
- https://adbappcontrol.com/en/ (desktop ADB tool, $7 extended)
- https://sdmse.darken.eu/changelog

Android platform and policy:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal
- https://developer.android.com/ai/appfunctions
- https://developer.android.com/reference/android/app/ApplicationStartInfo
- https://developer.android.com/guide/practices/page-sizes

Distribution and advisories:
- https://f-droid.org/docs/All_our_APIs/ (index v2)
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
- https://advisories.gitlab.com/maven/org.bouncycastle/bcprov-jdk18on/CVE-2026-0636/
- https://advisories.gitlab.com/maven/org.apktool/apktool-lib/CVE-2026-39973/
- https://github.com/material-components/material-components-android/releases/tag/1.14.0

## Open Questions

- Does the existing `SplitApkChooser` show any human-readable labels, or purely raw split entry names? (Determines scope of the split-label item.)
- What is the current split between IFW-only and PM-only blocking across different privilege modes? (Determines the dual-mode rollout strategy.)
- Which specific routine operation types are planned for v0.6.0's `RoutineScheduler`? (Determines whether cache clearing is already scoped or genuinely new.)
- Needs live validation: exact TV/Wear behavior, release APK sizes for IzzyOnDroid submission, RootService state after existing dirty work finishes.
