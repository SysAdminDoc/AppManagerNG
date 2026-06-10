<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG

Research pass date: 2026-06-10. Repo state: main @ 079e96f1 (v0.5.0 shipped 2026-05-25).
Evidence labels: Verified / Likely / Assumption / Needs live validation.

## Executive Summary

AppManagerNG is a Java/Views Android package manager (fork of MuntashirAkon/AppManager, pinned 3d11bcb 2026-04-16) whose strongest current shape is "the only tool that does everything in one app, now with an approachable front door": Permission Inspector, onboarding wizard, M3 dashboard, routine-scheduler executor, tags/filter-preset data layers, snapshot bundles, reproducible two-build releases. The highest-value direction for the next two quarters is **trust under pressure**: (1) backup/restore reliability — the loudest verified pain in the entire category, (2) readiness for Android Developer Verification enforcement (2026-09-30, first wave) where NG's ADB/wireless-ADB mode becomes the documented escape hatch (ADB installs are exempt), and (3) harvesting the 93 unported upstream commits (security hardening, Android 16 hidden-API refresh, main-list performance) before upstream's v4.1.0 ships ~2026-06-21. Differentiation vs upstream is structural: upstream **rejected Shizuku permanently** (issue #55 closed not_planned 2026-06-02) while NG ships Shizuku + Dhizuku — "Rootless Power" is a lane upstream will not enter. Top opportunities in priority order: jadx CVE audit (CVSS 9 line), verification-era installer handling, A16/A17 hidden-API + behavior-change compliance, backup verify-at-backup-time, upstream restore-fix ports, debloat safety net, wireless-ADB resilience, change-over-time auditing (the 2025-26 ecosystem innovation wave NG can leapfrog).

## Product Map

- Core workflows: inspect app (details/components/permissions/app-ops/signatures) → act (block/freeze/revoke/uninstall/backup) → automate (profiles, routine triggers, batch ops, Tasker/URI automation) → audit (tracker scan, op history, permission monitors).
- Personas: privacy-focused power user (root/Shizuku/GrapheneOS-adjacent), debloating novice (NG's UX-polish target), fleet/ROM tinkerer (preseed docs, snapshot bundles).
- Platforms: Android 5.0+ (minSdk 21 ceiling policy, one-way door), targetSdk 36; floss (offline) + full (opt-in online) flavors; arm/arm64/x86/x86_64 splits + universal.
- Distribution: GitHub Releases (signed, reproducibility-gated) + Obtainium config; IzzyOnDroid/F-Droid aspirational. Verification fingerprint published at stable URL.
- Key data flows: RulesStorageManager (component/permission rules), Room AppsDb v9, SharedPrefs-JSON stores (tags, filter presets, profile triggers), encrypted backup archives (metadata v7, 5 crypto modes), privileged ops via root/Shizuku/Dhizuku/ADB local server (libserver/server).

## Competitive Landscape

- **Upstream App Manager** — does well: relentless single-maintainer fix cadence; 93 commits since the fork pin including HMAC mutual auth for ADB connection, native run_server (fixes root mode broken since 3.0.0, #948), Android 16 hidden-API refresh, main-list perf batch, M3 preferences. Learn: port the security+perf+A16 batch promptly; watch v4.1.0 (due 2026-06-21) and v4.2.0 milestone (Finder, APK editor, backup extras). Avoid: its Shizuku rejection and store-client ambitions (#464) — NG's lane is rootless power, not app distribution.
- **SD Maid SE** — does well: graceful privilege degradation (root → Shizuku → accessibility-service fallback), scheduler that self-repairs its own battery-optimization exemption via root/ADB, storage trends over time. Learn: self-healing scheduler (NG's RoutineScheduler/AutoBackup depend on WorkManager surviving OEM battery managers). Avoid: cleaner-category scope creep.
- **Hail** — does well: freeze capability matrix across Root/DO/Shizuku/Dhizuku/Island; URI automation; tag-scoped actions. Learn: Dhizuku as a freeze/suspend *executor* (NG's DhizukuBridge currently feeds the installer cascade + mode doctor). Avoid: Xposed-dependent features (auto-unfreeze hook).
- **Neo Backup** — does well: retention policy (NG already adopted, [S41]), pause-app-during-backup (`kill -STOP`/`pm suspend`) for data consistency, special backups (SMS/call logs/Wi-Fi/Bluetooth). Learn: pause-during-backup; their #195 (schedule conditions, 15 reactions) validates NG's trigger work. Avoid: their multi-user refusal ("strange behavior") — NG already does multi-user; don't regress it.
- **PermissionManagerX** — does well: *reference states* — user-pinned desired value per permission/app-op with drift indicators and reference backup. Learn: this is the only audit-grade permission model in the ecosystem and fits NG's rule store. Avoid: nothing notable.
- **Permission Pilot / LibChecker** — do well: change-over-time auditing (permission watcher; snapshot diffs of components/libraries across app updates). NG already ships PermissionChangeMonitor (T9) + SigningCertChangeMonitor; the gap is component/tracker diffing and a browsable change feed. This is the 2025-26 innovation wave; NG can be first in its niche to unify it.
- **Canta / UAD-NG** — do well: safety-first debloating (unsafe-by-default unselectable, risk dialogs, replayable plain-text selection exports, bootloop honesty). Learn: extend NG's critical-package guard (currently only in Permission Inspector recovery) to Debloater/batch uninstall, plus pre-op snapshot + recovery script. Avoid: UAD's "Recommended is not actually recommended" trap — curate, don't inherit labels.
- **Swift Backup / Titanium (commercial bookends)** — paywall lines: cloud targets, scheduling, multi-version history, protected backups, notes. NG already matches Titanium's killer asymmetric-crypto model (RSA/ECC restore-only passphrase) — under-marketed. Learn: backup protect-flag + notes are cheap trust wins. Avoid: Google-sign-in-gated anything.

## Security, Privacy, and Reliability

- **jadx line CVE (P0)**: bundled `jadx_version = "1.4.7"` (MuntashirAkon/jadx-android, versions.gradle:35); CVE-2024-32653 (CVSS 9, command injection via unsanitized package name) was fixed in upstream jadx 1.5.0. Applicability to the Android fork's code path is unconfirmed — audit per docs/audits doctrine, then bump/patch. App feeds untrusted APKs to this surface (dex/DexClasses.java, scanner/). Verified CVE; applicability Needs live validation.
- **Unported upstream security hardening**: HMAC mutual challenge-response for ADB connection (upstream 88eb453) and native run_server (07c7199, fixes #948 root mode + reduces detectable service location). NG's local-server channel lacks the new auth. Verified.
- **Malformed-APK attack surface**: Konfety-wave evasions (bogus encryption flag, declared-unsupported compression, malformed manifest string pools) crash OSS parsers; NG parses hostile APKs in installer/scanner/manifest viewer (apk/, ARSCLib path). No fuzz coverage exists. Verified threat; NG exposure Assumption until tested.
- **Developer Verification (deadline 2026-09-30)**: verifier intercedes in `PackageInstallerSession.handleInstall()` on certified devices (BR/ID/SG/TH first); ADB installs exempt; new failure statuses surface through session callbacks. NG's installer must explain verifier blocks and offer the ADB-mode path; docs/sideload-verification.md exists but predates the QPR2 API details. Verified platform behavior.
- **Bugs/risks found in tree**: backup/adb/AndroidBackupHeader.java:375 FIXME (v1 backups may parse wrong — data loss); SessionMonitoringService.java:138 TODO memory wipe; settings/Ops.java:1030 duplicate revocation paths; AppDetailsPermissionsFragment.java (5 TODOs: privileged ops on fragment thread); backup dialog overwrite option unimplemented (BackupRestoreDialogFragment.java:379).
- **Guardrails present**: cleartext off + cert pinning (VirusTotal, Pithus — note upstream *removed* Pithus 2026-05-26; if the service is dead the full-flavor pin and scanner/Pithus.java are dead weight; Needs live validation); data-extraction rules exclude rules/db; crash sink local-only opt-in (matches F-Droid norms — ACRA-style auto-upload would draw Anti-Features flags).
- **Android 17 (API 37, stable imminent)**: targetSdk-37 behavior changes hit NG's core mechanics — static final fields unmodifiable via reflection (hidden-API bypass stack), lock-free MessageQueue, ACCESS_LOCAL_NETWORK runtime permission (wireless-ADB mDNS), cleartext-attribute deprecation (localhost carve-out), per-app Keystore key caps; A17 also adds APK Signature Scheme v3.2 (hybrid PQC) which NG's signature panels/apksig 4.4.0 must at least not mislabel. Verified (behavior-changes-17 / release notes).
- **Missing guardrails**: no critical-package guard outside Permission Inspector (Debloater/batch uninstall unguarded); no post-backup verification (corruption discovered at restore time — the #1 community complaint pattern); no app-pause during backup (live-write skew).
- **Recovery/rollback**: PermissionRecovery exists; per-app rollback partially landed (revert/, history/ops/PerAppRollbackManager.java); no pre-debloat snapshot/ADB rescue script.

## Architecture Assessment

- **Boundaries**: NG-layer stores (tags/AppTagStore, filters/preset/FilterPresetStore, profiles/trigger/ProfileTriggerStore) are consistent SharedPrefs-JSON with schema versions — good; planned Room migration contracts exist. RoutineScheduler executor complete; only UI missing (v0.6.0, planned — not re-proposed here).
- **Refactor candidates**: AppDetailsPermissionsFragment → ViewModel (5 TODO sites, blocks error handling); Ops.java duplicate permission-revocation methods; terminal/TermActivity mock (4 TODOs) — finish or formally defer.
- **Test gaps**: ~315 unit tests concentrated in I/O/parsing; backup/restore core, permissions revocation, libserver/server/hiddenapi at zero. CI runs unit tests only; android17-emulator.yml exists but no restore-path integration test rides it. Highest-risk untested surface = backup/restore (also the category's loudest user pain).
- **Docs gaps**: docs/policy/minsdk-21-ceiling.md referenced from versions.gradle and architecture README but absent on disk (gitignore casualty — restore it; the MDC-1.14 decision depends on it). NG-added strings English-only across 44 locales; README says "Weblate (link TBD)" — fork has no translation pipeline yet.
- **Accessibility**: NG-added layouts are well-annotated (decorative images marked `@null` + importantForAccessibility="no", info buttons labeled); legacy layouts are mixed (~62% of layout files carry any contentDescription); no focus-traversal/D-pad audit has been done on the M3 dashboard (relevant to the FireOS/TV user segment visible upstream: #1835, #1854).
- **Strategic platform fork-in-road**: material-components-android is in maintenance mode (1.14.0 = final feature release, requires minSdk 23; Views get critical fixes only; Compose-first announced 2026-05-19). NG must decide: stay on 1.13 (minSdk 21 forever) vs take 1.14 + minSdk 23 as the terminal Views platform. One-way door — needs a dated decision doc, not code.

## Rejected Ideas

- Unified store client (Aurora/F-Droid inside NG) — upstream #464 (11 reactions). Scope creep; Aurora/Droid-ify exist; NG is a manager, not a store. Installer-source awareness already shipped.
- App cloning without work profile — upstream #1029. Security surface + ROM-specific hacks; misfit.
- Xposed auto-unfreeze-on-launch — Hail v1.10. Adds a framework dependency contradicting NG's no-framework privilege model.
- Native cloud-target SDKs (Drive/Dropbox/SMB clients) — Swift Backup/DataBackup. Violates floss-flavor offline doctrine; SAF DocumentsProvider destinations already reach Nextcloud et al.; document that path instead.
- Accessibility-service fallback tier for force-stop/clear-cache — SD Maid SE. Heavy maintenance, a11y-abuse perception, marginal gain over NG's wireless-ADB tier; reconsider only if ADB adoption telemetry (which NG deliberately lacks) proves the gap.
- Store watchlist (price drops/delisting) — AppDash. Online polling service; misfit for privacy doctrine.
- Crash analytics (Sentry/Crashlytics/ACRA auto-upload) — F-Droid Anti-Features flag; local opt-in sink already correct.
- Parallel ADB-firewall build-out — upstream #1754 is the sole v4.1.1 milestone item (due 2026-12-20); building in parallel duplicates a funded upstream effort. Revisit as a port after upstream ships. (Demand is real: F-Droid forum "control internet traffic 2024" thread.)
- APK Editor — upstream #138 (50 comments). Defer: XL effort, abuse/legal surface; NG has ARSCLib/apksig foundations if revisited post-v0.7.
- Titanium Backup archive import — 3C All-in-One claims it; shrinking userbase, undocumented legacy format, and snapshot-bundle import already covers the fork's own migration story.
- Secure Folder operation — Knox container blocks third-party managers by design; not addressable.

## Sources

Upstream:
- https://github.com/MuntashirAkon/AppManager/releases
- https://github.com/MuntashirAkon/AppManager/commits/master
- https://github.com/MuntashirAkon/AppManager/issues/55
- https://github.com/MuntashirAkon/AppManager/issues/61
- https://github.com/MuntashirAkon/AppManager/issues/1286
- https://github.com/MuntashirAkon/AppManager/issues/1596
- https://github.com/MuntashirAkon/AppManager/issues/1754

Platform / policy:
- https://developer.android.com/developer-verification/guides/faq
- https://developer.android.com/about/versions/16/qpr2/release-notes
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/release-notes
- https://gist.github.com/agnostic-apollo/b8d8daa24cbdd216687a6bef53d417a6
- https://developer.android.com/sdk/api_diff/35/changes/android.content.pm.PackageInstaller
- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
- https://www.tenable.com/cve/CVE-2024-32653
- https://zimperium.com/blog/over-3000-android-malware-samples-using-multiple-techniques-to-bypass-detection
- https://www.androidauthority.com/android-wireless-adb-auto-reconnect-3624945/

Competitors:
- https://github.com/d4rken-org/sdmaid-se/wiki/AppControl
- https://github.com/aistra0528/Hail
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/mirfatif/PermissionManagerX
- https://github.com/d4rken-org/permission-pilot
- https://github.com/LibChecker/LibChecker
- https://www.titaniumtrack.com/titanium-backup.html

Community:
- https://xdaforums.com/t/search-for-an-application-to-back-up-android-applications-and-their-data.4783472/
- https://forum.f-droid.org/t/how-can-i-control-my-internet-traffic-in-2024/28797
- https://xdaforums.com/t/psa-muntashirakon-app-manager-extracting-vs-aurora-store-exporting-android-apps-split-bundled-apks-for-archival-reuse.4784234/

## Open Questions

- The live ROADMAP.md/CHANGELOG.md exist only on another machine (gitignored, absent here). The Research-Driven Additions below were deduplicated against README version targets, code-comment iter/[Sxxx] markers, and shipped code — but a final dedupe against the live roadmap file is required where it exists.
- Pithus service status (pithus.org reachable? accepting submissions?) — upstream deleted its integration 2026-05-26; decides keep-vs-remove. Needs live validation.
- Per-ABI release APK sizes vs IzzyOnDroid's ~30 MB reservation — needs a local release build to measure.
- Does the planned v0.5.x "background-run rule persistence" item cover boot-receiver/battery-optimization batch surfaces? If yes, the Inure-parity panel idea stays dropped; if no, it can be revisited.
- minSdk-23 decision (MDC 1.14): NG collects no usage telemetry by design, so the API 21-22 user share cannot be measured — the decision must be made on policy grounds (docs/policy/minsdk-21-ceiling.md), which first needs restoring to disk.
