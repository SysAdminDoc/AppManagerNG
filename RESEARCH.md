<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG

Research pass date: 2026-06-10 (pass 2; supersedes pass 1 of the same date).
Verification pass 2026-06-10 (late): all upstream/issue/dependency claims re-checked
against live sources (#1982/#1967/#1973 open as stated; v4.1.0 milestone 41 closed /
2 open — #1733 + #1982 — due 2026-06-21; sora-editor 0.24.6 confirmed last
minSdk-21 release; Magisk v30.7 caps-preserved confirmed; MDC 1.14.0 minSdk-23
confirmed). Two corrections applied: the minSdk decision memo exists on disk
(see §Architecture), and Pithus responded operational (see §Open Questions).
Repo state: main @ d692a5a0 (v0.5.0 shipped 2026-05-25).
Evidence labels: Verified / Likely / Assumption / Needs live validation.

## Executive Summary

AppManagerNG is a Java/Views Android package manager (fork of MuntashirAkon/AppManager, pinned 3d11bcb 2026-04-16) whose strongest current shape is "the only tool that does everything in one app, with an approachable front door": Permission Inspector, onboarding wizard, M3 dashboard, routine-scheduler executor, tags/filter-preset layers, snapshot bundles, app archiving (API 35, already shipped via AppArchiveManager — ahead of every OSS competitor), reproducible two-build releases. The highest-value direction remains **trust under pressure**, now sharpened by pass-2 findings: (1) upstream v4.1.0 tags ~2026-06-21 with only two issues open — the cleanest rebase/cherry-pick window the fork will get this year, and one of those two bugs (#1982 main-list infinite-load on Android 16 private space) is fixable fork-first at S cost; (2) backup-engine debt is now precisely located (AndroidBackupHeader v1 FIXME, missing overwrite option, the 2026-06-09 audit's deferred reliability batch); (3) the platform wave (Developer Verification advanced flow Aug 2026, Advanced Protection, ApplicationStartInfo, AppFunctions) gives NG cheap inspection/diagnostic surfaces nobody in the niche ships. Top opportunities in order: #1982 fork-first fix, upstream post-pin cherry-pick batch, v1-backup-header data-loss fix, root-detection retune for Magisk 30.7/KSU-Next 3.1 (upstream #1967), backup overwrite + deferred-audit reliability batch, sora-editor bump (0.24.6 is the last minSdk-21 release — time-boxed), ApplicationStartInfo panel, assistant-launched privileged services (#1973, accepted-unbuilt upstream).

Carried forward from pass 1 (already itemized in ROADMAP, still valid): jadx CVE-2024-32653 audit, Developer Verification installer UX, A16 hidden-API port, A17 behavior audits, backup verification, restore-fix ports, debloat safety net, wireless-ADB resilience, change auditing, reference states, Dhizuku parity.

## Product Map

- Core workflows: inspect app (details/components/permissions/app-ops/signatures) → act (block/freeze/revoke/uninstall/backup/archive) → automate (profiles, routine triggers, batch ops, Tasker/URI) → audit (tracker scan, op history, permission/signing monitors).
- Personas: privacy-focused power user (root/Shizuku/GrapheneOS-adjacent), debloating novice (NG's UX target), fleet/ROM tinkerer (preseed docs, snapshot bundles).
- Platforms: Android 5.0+ (minSdk 21 ceiling policy — one-way door, now time-pressured by sora-editor and MDC both ending minSdk-21 support), targetSdk 36; floss (offline) + full (opt-in online) flavors.
- Distribution: GitHub Releases (signed, reproducibility-gated) + Obtainium config; IzzyOnDroid aspirational (tracker now at codeberg.org/IzzyOnDroid/repo); Accrescent **closed to new submissions** (capacity) — drop it from distribution plans for now.
- Key data flows: RulesStorageManager, Room AppsDb v9, SharedPrefs-JSON stores (tags/filter presets/profile triggers — tags confirmed round-tripping through SnapshotBundle), encrypted backups (metadata v7), privileged ops via root/Shizuku/Dhizuku/ADB local server.

## Competitive Landscape

- **Upstream App Manager** — v4.1.0 milestone 41/43 closed, due 2026-06-21; latest master commit 133b5acb (2026-06-02). Learn: cherry-pick the uncatalogued post-pin fix batch (706c36fb APKS compile, daa54ac0 profile filter expressions, 4a25c3f0 intent resolution, 3bf97856/184df334 NPEs, 329b8dc1 debloater listing, 4d3da96b nav buttons, 0d1be565 editor crop); fix #1982/#1733 fork-first (still open upstream); implement accepted-unbuilt #1973 (assistant-launched privileged services/broadcasts). The M3-preferences commit set (e24eb8d0 et al.) will be the main rebase-conflict source — decide port-vs-skip deliberately. Shizuku/Dhizuku requests keep arriving monthly (#1970/#1972/#1979/#1981, all closed duplicate of rejected #55) — the fork's structural lane is confirmed again. Avoid: store-client ambitions.
- **Inure App Manager** (1.8k★, the biggest direct OSS competitor) — does well: usage-stats panel (1-year window), device-wide analytics charts (SDK/installer/signature distributions with tap-through filtered lists), battery-optimization panel, boot manager, deep per-app AppOps (UID-vs-package scoping), debloat badges on every App Info page + post-op state re-verification. Learn: analytics dashboard, boot-component manager, debloat-state badges. Avoid: media-player scope creep, trial-license model, fully custom non-Material UI (bus-factor and a11y trap), zero-open-issues policy (kills community signal).
- **AppDash (commercial)** — does well: configurable insight-card dashboard ("unused apps", "storage-heavy", "updates available"), color auto-tagging, versioned backups. Learn: insight cards as a main-screen surface. Avoid: Play-intelligence subscription angle, watchlist polling.
- **APKUpdater / UpgradeAll** — multi-source version-watch (F-Droid/IzzyOnDroid/GitHub) with scheduled checks proves real demand (3.8k★, active); UpgradeAll died when its cloud rules-hub maintenance stopped. Learn: version-watch from static indexes, full-flavor only. Avoid: cloud rules-hub dependency.
- **Brevent / Thanox / App Ops (Rikka)** — background policy via standby buckets (`am set-standby-bucket`), op templates applied to app groups, ops backup/restore. Learn: per-app standby-bucket control fits NG's ADB tier; op templates fit the existing rules store. Avoid: task-killer "boost" framing, Thanox's fake-data "privacy cheat" (legal/ethical surface), pro-gating.
- **TrackerControl / ClassyShark3xodus** — tracker reporting grouped company → category → jurisdiction with plain-language explanations; dynamic-vs-static manifest diff; per-app SELinux/sharedUserId badges. Learn: rollup presentation over flat library lists (NG's TrackerInfoDialog is the base). Avoid: live Exodus API calls in core flows (rate-limited 3 req/min; offline doctrine) — bundle signatures, deep-link out instead.
- **InstallerX-Revived (5.1k★) / Blocker (2.3k★)** — modern installer benchmark (silent install, downgrade, SDK-block bypass, Dhizuku); component control with MyAndroidTools-rule import and community rule sharing. Learn: installer option depth; rule-format import breadth. Avoid: nothing notable.
- **Shelter / Island / Insular** — work-profile freeze/clone tier. NG already ships the half worth having (FreezeUnfreezeService auto-refreezes on screen-off — verified); the profile-management half is fragile across OS updates (Island's A15 QPR2 breakage, XDA "ruined everything" threads). Avoid the cloning tier; keep it rejected.

## Security, Privacy, and Reliability

- **Resolved since pass 1 (no action)**: Bouncy Castle already at 1.84 with CVE ledger comments (versions.gradle:26 — CVE-2026-5588/-3505/-5598 cleared). Shizuku-API 13.1.5 is the current artifact. Negative CVE findings for zstd-jni 1.5.7-7, okhttp, AndroidX, XZ-for-Java, ARSCLib/apksig (Verified absence). commons-compress is not a dependency (no pin, no build.gradle reference) — scanner findings against it can be dismissed.
- **Still open, P0-class (already in ROADMAP)**: jadx 1.4.7 CVE-2024-32653 audit (versions.gradle:35).
- **#1982 exposure confirmed in NG**: ApplicationItem.java calls `UsageStatsManagerCompat.isAppInactive` (Verified); upstream repro: private-space profile on Android 16 throws `SecurityException: ... INTERACT_ACROSS_USERS`, swallowed in the main-list load path → list never renders. Accepted P1/S0 upstream, **no upstream fix committed yet** — fork-first window.
- **Data-loss class in tree**: backup/adb/AndroidBackupHeader.java:375 FIXME "May not work for backup file version 1" (since 2023); backup overwrite option still unimplemented (backup/dialog/ TODO since 2020); SessionMonitoringService.java:138 "Wipe memory?" on cryptographic material.
- **2026-06-09 audit deferred reliability batch (recovered from session record; classes verified present)**: AppUsageViewModel live-list CME; stale-position notifyItemChanged in AppDetails fragments; ApkWhatsNewFinder singleton shared temp-set; SAF pending-write fields lost on process death + profile-export main-thread IO; OneClickOps onPause busy-clear without scan-cancel; retention pruners leave stale Room rows and fire no broadcast; commit() can skip the previous-backup freeze flag; verify-backups surfaces raw getMessage(); tracker/perm badges below 48dp touch-target minimum.
- **Root-detection drift**: Magisk v30.7 (2026-02) now preserves capabilities by default (`--drop-cap` to opt out) — NG ships KernelSU/Magisk *drop-cap diagnostics* built on the old default; KernelSU-Next 3.1.0 and the A16 root-detection failure (upstream #1967, accepted P1/S0) hit the same probe stack (runner/RootManagerInfo). libsu 6.0.0 predates A16; topjohnwu's unreleased master has a pseudo-file NIO fix (4910d8dc) worth vendoring if /proc reads misbehave.
- **Developer Verification (since 2026-05-15)**: Limited Distribution Accounts (free, 20 devices) in early access now; the **"advanced flow" for installing unverified apps launches globally Aug 2026** (developer mode + anti-coaching confirm + reboot + one-day wait + biometric). Enforcement date and ADB exemption unchanged. NG should document the advanced flow and detect/surface Advanced Protection (AdvancedProtectionManager, API 36+) state, which blocks sideloading outright.
- **Guardrail notes carried forward**: Pithus keep-vs-remove still needs live validation; post-backup verification and debloat safety net remain itemized in ROADMAP.

## Architecture Assessment

- **Already shipped, under-marketed**: API-35 app archiving (AppArchiveManager + batch ops + App Info + main list) — no mainstream OSS manager ships this; it belongs in README/feature marketing, not the roadmap.
- **Refactor candidates**: AppDetailsPermissionsFragment → ViewModel (5 TODOs, privileged ops on the fragment thread, blocks error handling); settings/Ops.java:1030 duplicate revocation paths; terminal/TermActivity mock (4 TODOs) — finish or formally defer with a decision record; PackageInstallerCompat.java:685 task-wait race + :1198 HyperOS handling.
- **Test map**: ~303 unit-test classes concentrated in I/O/parsing; zero coverage in libserver/, server/, hiddenapi/, backup core, batchops/; androidTest has 3 classes (smoke/Room-migration/a11y). CI: tests, A17 emulator, dependency-scan, CodeQL, lint, release, two watch workflows, docs-link-check. Backup round-trip integration tests remain itemized in ROADMAP.
- **Dependency time-pressure**: sora-editor pinned at fork 0.22.2; Rosemoe 0.24.4–0.24.6 fix IME composing-text corruption, completion-list ANR, IOOB, emoji deletion; **0.24.6 (2026-06-10) is the last minSdk-21 release** (Verified: release notes state next release raises minSdk to 23). Since the floor decision is now "hold 21" (below), 0.24.6 is the terminal upstream version NG can take — bump now, don't wait. libadb-android: no 2026 commits (current); ARSCLib pin predates the 2026-05 sparse-resource and DEX-validation fixes on master (no tagged release — watch, don't chase).
- **minSdk decision: MADE, not blocked** (correction to pass-1/2 text). `docs/policy/2026-05-26-minsdk-23-decision.md` exists on disk (Verified) and recommends **holding minSdk 21 through at least v0.6.x**, with four forced-decision triggers (unbackportable CVE in a pinned-cluster dep, hard 1.14-component dependency, API-21/22 untestable in CI, external automation-API integrator need) and a five-step flip plan. What is actually missing is the *ledger* it references: `docs/policy/minsdk-21-ceiling.md` is absent while being linked from versions.gradle:39, the decision memo itself, and docs/architecture/README.md.
- **i18n**: 44 locales, no pipeline (Weblate item already in ROADMAP); ~48 "App Manager" references remain in default-locale strings.xml.
- **Docs drift**: CLAUDE.md's canonical-context pointer `PROJECT_CONTEXT.md` does not exist on disk (Verified) — per repo doc-hygiene policy it should not be recreated; the dangling references in CLAUDE.md should be repointed at ROADMAP/CHANGELOG/docs/architecture instead.

## Rejected Ideas

Carried forward from pass 1 (sources there): store client (#464), app cloning (#1029), Xposed auto-unfreeze, native cloud-target SDKs, accessibility-service fallback tier, store watchlist, crash-analytics auto-upload, parallel ADB-firewall build-out (#1754 — now confirmed slipped to upstream v4.1.1, due 2026-12-20), APK editor (#138), Titanium import, Secure Folder.

New this pass:
- App archiving feature build-out — already shipped (AppArchiveManager, verified); remaining work is marketing copy, not engineering.
- Unfreeze-and-launch with auto-refreeze (Shelter/Island pattern) — already shipped (FreezeUnfreezeService freezes all on ACTION_SCREEN_OFF, verified).
- Bouncy Castle 1.84 bump — already done (versions.gradle:26).
- Shizuku-API bump — 13.1.5 is the latest published API artifact; app v13.6.0 ≠ API version.
- "Nuke all trackers" one-shot (Warden) — NG's One-Click Ops tracker blocking with three-tier intensity already covers it.
- VirusTotal integration (Inure #276) — NG full flavor already ships it hash-first, opt-in.
- WebDAV/SMB backup targets — still rejected; SAF DocumentsProvider destinations reach Nextcloud et al. without new network SDKs in core.
- Work-profile cloning/sandbox tier (Shelter/Island/Insular) — fragile across OS updates (Island A15 QPR2 breakage); freeze half already shipped.
- Media/audio/image viewers, trial-license models, custom UI toolkits (Inure) — scope creep and bus-factor traps.
- Cloud rules-hub for update tracking (UpgradeAll) — died with maintenance; static indexes only.
- Thanox-style fake-device-data "incognito" — ethical/legal surface, misfit.
- commons-compress CVE remediation — not a dependency (verified).
- Levenshtein-ranked search, icon-PNG export, copyable batch package names, dynamic-vs-static manifest diff (Inure/UAD-NG/ClassyShark) — Under consideration, not itemized: individually trivial or L-effort with niche payoff; revisit as a single QoL batch if user requests surface.

## Sources

Upstream / companion libs:
- https://github.com/MuntashirAkon/AppManager/commits/master
- https://github.com/MuntashirAkon/AppManager/milestone/72
- https://github.com/MuntashirAkon/AppManager/issues/1982
- https://github.com/MuntashirAkon/AppManager/issues/1973
- https://github.com/MuntashirAkon/AppManager/issues/1967
- https://github.com/MuntashirAkon/AppManager/issues/1733
- https://github.com/Rosemoe/sora-editor/releases
- https://github.com/topjohnwu/libsu/commits
- https://github.com/REAndroid/ARSCLib/releases
- https://github.com/topjohnwu/Magisk/releases

Platform / policy:
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/reference/android/app/ApplicationStartInfo
- https://developer.android.com/ai/appfunctions
- https://android-developers.googleblog.com/2025/12/android-16-qpr2-is-released.html
- https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html
- https://android.gadgethacks.com/news/google-keeps-android-sideloading-for-power-users-in-2026/
- https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/
- https://commons.apache.org/proper/commons-compress/security.html
- https://accrescent.app/docs/guide/getting-started/new-app.html
- https://f-droid.org/2026/01/24/fdroid-basic-2.0-alpha.html

Competitors / community:
- https://github.com/Hamza417/Inure
- https://appdash.app/
- https://github.com/rumboalla/apkupdater
- https://github.com/brevent/Brevent
- https://trackercontrol.org/
- https://f-droid.org/packages/com.oF2pks.classyshark3xodus/
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://github.com/lihenggui/blocker
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/timschneeb/awesome-shizuku

## Open Questions

- Pithus: beta.pithus.org responded operational with an upload form on 2026-06-10 (Likely alive; one HTTP check, not a full submission round-trip). The keep-vs-remove item shifts toward "keep" — but NG's scanner/Pithus.java endpoint + network_security_config.xml cert pins must be validated against the current host, and upstream's removal rationale (commits 0e187e8/2c00f69) should be read before deciding.
- Per-ABI release APK sizes vs IzzyOnDroid's ~30 MB reservation — needs a local release build.
- minSdk-23: decision is MADE (hold 21 through v0.6.x, docs/policy/2026-05-26-minsdk-23-decision.md) — the remaining open question is only whether any forced-decision trigger has fired since 2026-05-26 (none observed in this pass: no unbackportable CVE in the pinned cluster, API-21 emulator images still published). The missing ceiling-ledger doc still needs restoring.
- Does `pm archive` via the privileged shell path work for apps NG did not install (installer-of-record routing on unarchive)? AppArchiveManager ships; the multi-installer unarchive matrix needs device validation.
- Whether the planned v0.5.x "background-run rule persistence" surface covers standby-bucket policy — decides the scope of the new standby-bucket item (kept narrow deliberately).
