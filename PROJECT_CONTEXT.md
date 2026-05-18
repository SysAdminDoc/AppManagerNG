<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# PROJECT_CONTEXT — AppManagerNG canonical project memory

> **Read me first.** This file is the canonical "where do I look?" index for AppManagerNG.
> It links to the load-bearing artifacts rather than duplicating them, because the
> primary documents (ROADMAP.md, CHANGELOG.md, CLAUDE.md, the audit/research dirs) are
> the source of truth and they update faster than this index does.
>
> Last consolidated: **2026-05-18 iter 137**. Iter-137 migrated the build to
> AGP 9.2.0 and Gradle 9.4.1, pinned NDK 28.2.13676358, converted build scripts
> to Gradle-10-safe assignment syntax, moved server packaging to
> `androidComponents` / `sdkComponents`, and restored green floss/full/unit-test
> verification on the new toolchain.
>
> Previous consolidated baseline: **2026-05-18 iter 136**. Iter-136 shipped the
> split APK cert-mismatch dialog: selected split APKs are checked against the
> base APK's current signing certs before the install session starts, and
> mismatches are shown in a Material dialog with per-split name, version, cert
> SHA-256, and reason rows plus optional-split removal.
> Run `git status --short --branch`
> for the exact current branch/ahead state before starting new code work.

---

## 1. What this project is, in one paragraph

AppManagerNG is a **continuation/fork of [MuntashirAkon/AppManager](https://github.com/MuntashirAkon/AppManager)**
— a full-featured root/ADB/Shizuku-aware Android package manager — bootstrapped on
2026-04-30 from upstream commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(post-v4.0.5). The NG fork's product thesis is **"all the power, half the friction"**:
keep every power-user capability (component blocking, tracker scanning, backup/restore,
app-ops editing, hidden-API reach), but rebrand under `io.github.sysadmindoc.AppManagerNG`,
ship a Material 3 + onboarding-led UX, and treat **Shizuku as a first-class privilege
path** alongside root and ADB.

Current release: **v0.4.2** (2026-05-13). Code 6.

License: **GPL-3.0-or-later**, REUSE-compliant per-file SPDX headers; vendored deps keep
their own licenses (Apache-2.0, BSD-2/3, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL).

---

## 2. Where things live — entry points for a new AI session

Read these in order. Do **not** rewrite them as a drive-by; they are mature.

| Document | Lines | What it gives you |
|----------|------:|-------------------|
| [`CLAUDE.md`](CLAUDE.md) | 129 | Stack, build commands, origin, gotchas, version status. Tool-specific working notes. |
| [`AGENTS.md`](AGENTS.md) | 9 | Pointer to `CLAUDE.md` + shared codex memory dir. |
| [`README.md`](README.md) | 185 | Public user-facing surface — features, install, signing fingerprint. |
| [`ROADMAP.md`](ROADMAP.md) | large | The plan. Tier-organised (Now / Next / Later / Under Consideration / Rejected) with an Engineering Debt Register, Upstream Sync Strategy, and iter-18 -> iter-99 follow-through context inline. Cites **363 numbered external sources** in a Source Appendix at the bottom. |
| [`CHANGELOG.md`](CHANGELOG.md) | large | Per-release notes back to v0.1.0; "Unreleased" section currently holds 2026-05-14 -> 2026-05-18 shipped work. |
| [`docs/research/`](docs/research/) | 4 files | `2026-05-02-android-power-tools.md`, `2026-05-09-capability-extension.md`, `2026-05-09-observability-testing-audit.md`, `2026-05-09-roadmap-extension-phase-2.md`. Plus `iter-6-delta.md`. |
| [`docs/audits/`](docs/audits/) | 20 files + README | Per-audit verdicts for Android 16/17/18 platform changes, crypto/dependency bumps, predictive back, Play policy, and Shizuku Android-17 compatibility. Read `docs/audits/README.md` first for verdict vocabulary. |
| [`research/iter-20-delta.md`](research/iter-20-delta.md) | — | Free-form 2026-05-08 issue-mining notes from the iter-20 sweep. Subsequent iters live inline in ROADMAP. |
| [`design/`](design/) | 7 files | Premium-facelift design system (`spec/1-design-system.md`, `impl/values/*-v2.xml`, `impl/layout/*_v2.xml`, `plan/3-rollout.md`, `audit/0-recon.md`, `audit/4-painpoints.md`). Driven by [`codexprompt.md`](codexprompt.md) at repo root. |
| [`docs/distribution/`](docs/distribution/) | 6 files | Obtainium config (`obtainium-config.json` + `.license`), reproducible-builds doc, backup-destinations matrix, package-visibility dossier, build-flavor contract. |
| [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md) | — | Running ledger of every dep that has dropped (or imminently drops) API 21-22 support. Read this **before** bumping `min_sdk` in [`versions.gradle`](versions.gradle). |
| [`docs/security-advisories/`](docs/security-advisories/) | 1 file | CVE-2026-0073 ADB-mode advisory. |
| [`docs/intent-api.md`](docs/intent-api.md) | — | `app-manager://` + `am://` deep-link contract. |
| [`docs/sideload-verification.md`](docs/sideload-verification.md) | — | Position document for Google's 2026-09-30 Brazil/Indonesia/Singapore/Thailand developer-verification enforcement. |
| [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) | pass 1 | Foundation research-run audit; STATE_OF_REPO, MEMORY_CONSOLIDATION, SOURCE_REGISTER, FEATURE_BACKLOG, etc. |
| [`.ai/research/2026-05-17-pass-2/`](.ai/research/2026-05-17-pass-2/) | pass 2 | Source fixes + architecture docs follow-through. |
| [`.ai/research/2026-05-17-pass-3/`](.ai/research/2026-05-17-pass-3/) | pass 3 | Android 17 targetSdk=37 audit batch + CI/docs hygiene follow-through. |
| [`.ai/research/2026-05-17-pass-4/`](.ai/research/2026-05-17-pass-4/) | pass 4 | Shizuku Android-17 runtime warning, release watcher, ML-DSA display-name map, and updated source register. |
| [`.ai/research/2026-05-17-pass-5/`](.ai/research/2026-05-17-pass-5/) | pass 5 | T5 USB-debugging preflight for Wireless ADB / Shizuku setup and next-run handoff. |
| [`.ai/research/2026-05-17-pass-6/`](.ai/research/2026-05-17-pass-6/) | pass 6 | Installer session SHA-256 confirmation and Dhizuku minSdk integration constraint. |
| [`.ai/research/2026-05-17-pass-7/`](.ai/research/2026-05-17-pass-7/) | pass 7 | Root/ADB battery-optimization auto-fix helper wired into profile routines and long-running backup batch operations. |
| [`.ai/research/2026-05-17-pass-8/`](.ai/research/2026-05-17-pass-8/) | pass 8 | Cross-user package-state buckets in the main list and Finder multi-user scope. |
| [`.ai/research/2026-05-17-pass-9/`](.ai/research/2026-05-17-pass-9/) | pass 9 | Opt-in debloat-definition auto-update cache, manifest/checksum verifier, and app-private fallback path. |
| [`.ai/research/2026-05-17-pass-10/`](.ai/research/2026-05-17-pass-10/) | pass 10 | Settings → Privileges health-check screen for mode, root/Shizuku/ADB, remote services, and battery optimization. |
| [`.ai/research/2026-05-17-pass-11/`](.ai/research/2026-05-17-pass-11/) | pass 11 | Android 16 capability-dropping diagnostic in Settings -> Privileges, backed by active-shell UID + `CapEff` parsing. |
| [`.ai/research/2026-05-17-pass-12/`](.ai/research/2026-05-17-pass-12/) | pass 12 | VPN plugin flag blocker audit and Finder debloat-description search predicates. |
| [`.ai/research/2026-05-17-pass-13/`](.ai/research/2026-05-17-pass-13/) | pass 13 | Finder backup-only result rows for validated uninstalled app backups. |
| [`.ai/research/2026-05-17-pass-14/`](.ai/research/2026-05-17-pass-14/) | pass 14 | Permission-state filter predicates backed by `FilterablePermissionInfo`. |
| [`.ai/research/2026-05-17-pass-15/`](.ai/research/2026-05-17-pass-15/) | pass 15 | Finder relevance scoring for literal package/component/tracker search filters. |
| [`.ai/research/2026-05-17-pass-16/`](.ai/research/2026-05-17-pass-16/) | pass 16 | Signature-gated automation broadcast API for existing batch/profile/installer/tracker operations. |
| [`.ai/research/2026-05-17-pass-17/`](.ai/research/2026-05-17-pass-17/) | pass 17 | Stale APK share-target roadmap row closed by manifest/installer audit. |
| [`.ai/research/2026-05-17-pass-18/`](.ai/research/2026-05-17-pass-18/) | pass 18 | Dynamic and pinned per-app launcher shortcuts for freeze, force-stop, and clear-cache actions. |
| [`.ai/research/2026-05-17-pass-19/`](.ai/research/2026-05-17-pass-19/) | pass 19 | `floss` / `full` build flavors, optional-network feature gating, Obtainium full-build targeting, and release/test artifact path updates. |
| [`.ai/research/2026-05-17-pass-20/`](.ai/research/2026-05-17-pass-20/) | pass 20 | LocalServer bootstrap smoke test in Settings -> Privileges plus shared success/failure bootstrap signature formatter. |
| [`.ai/research/2026-05-17-pass-21/`](.ai/research/2026-05-17-pass-21/) | pass 21 | Support-info text bundle composer in Settings -> Troubleshooting with scrubbed logcat tail and LocalServer signature capture. |
| [`.ai/research/2026-05-17-pass-22/`](.ai/research/2026-05-17-pass-22/) | pass 22 | Privileged operation audit-log closure: existing op-history surface audited, exit-code metadata, and LocalServer bootstrap-signature details/export. |
| [`.ai/research/2026-05-17-pass-23/`](.ai/research/2026-05-17-pass-23/) | pass 23 | Privileged batch journal and reattach recovery dialog, including Shizuku/Sui binder-death journal marking. |
| [`.ai/research/2026-05-17-pass-24/`](.ai/research/2026-05-17-pass-24/) | pass 24 | Mode Doctor active probe report in Settings -> Privileges. |
| [`.ai/research/2026-05-17-pass-25/`](.ai/research/2026-05-17-pass-25/) | pass 25 | Shizuku trusted-WLAN auto-start affordance in Operating Mode and onboarding, with launcher/app-info fallback. |
| [`.ai/research/2026-05-17-pass-26/`](.ai/research/2026-05-17-pass-26/) | pass 26 | JobScheduler quota stop-reason row parked as a Scheduled Auto-Backup acceptance criterion after confirming no WorkManager/JobScheduler surface exists yet. |
| [`.ai/research/2026-05-17-pass-27/`](.ai/research/2026-05-17-pass-27/) | pass 27 | Apktool 3.0.2 migration row parked after confirming NG has no Apktool dependency/call site to migrate and would need a future T12 backend first. |
| [`.ai/research/2026-05-17-pass-28/`](.ai/research/2026-05-17-pass-28/) | pass 28 | Quick Settings freeze profile tile backed by selected freeze-enabled profiles and `ProfileApplierService`. |
| [`.ai/research/2026-05-17-pass-29/`](.ai/research/2026-05-17-pass-29/) | pass 29 | Hidden-API compatibility harness baseline, generator, JVM baseline coverage test, and instrumented active-SDK runtime probe/report. |
| [`.ai/research/2026-05-17-pass-30/`](.ai/research/2026-05-17-pass-30/) | pass 30 | Shizuku clear-data warning/re-probe path for direct and batch clear-data operations. |
| [`.ai/research/2026-05-17-pass-31/`](.ai/research/2026-05-17-pass-31/) | pass 31 | Freeze / operation audit-log row closed by existing op-history UI plus Settings entry and per-row recovery guidance. |
| [`.ai/research/2026-05-17-pass-32/`](.ai/research/2026-05-17-pass-32/) | pass 32 | Android 17 / 16 KB native page-size remediation: CMake linker flags, ELF `PT_LOAD.p_align` parsing, release APK alignment gate, and audit doc. |
| [`.ai/research/2026-05-17-pass-33/`](.ai/research/2026-05-17-pass-33/) | pass 33 | Android Developer Verification guardrails: verifier service detection, App Details unknown-status chip, installer warning gate, and `PackageInstaller` failure-reason diagnostics. |
| [`.ai/research/2026-05-17-pass-34/`](.ai/research/2026-05-17-pass-34/) | pass 34 | Shizuku 13.6.0 OEM compatibility warning: Transsion Android 15, Mediatek, and Pixel 9 / Android 16 QPR1 known-bad detection with a Shizuku 13.5.4 archive link. |
| [`.ai/research/2026-05-17-pass-35/`](.ai/research/2026-05-17-pass-35/) | pass 35 | Shizuku root-backed avoidance: Auto mode skips root-backed Shizuku when ADB is available, and Settings / onboarding / Privileges / Mode Doctor surface the banking-app side-effect warning. |
| [`.ai/research/2026-05-17-pass-36/`](.ai/research/2026-05-17-pass-36/) | pass 36 | OS-revert detection: 30-second post-write checks for Doze, freeze, component state, and AppOps mutations with a BaseActivity Snackbar/details surface. |
| [`.ai/research/2026-05-17-pass-37/`](.ai/research/2026-05-17-pass-37/) | pass 37 | Backup-aware Doze allowlist diagnostics: 60-second Doze re-polls with `device_idle_constants` / `DeviceConfig device_idle` one-line diffs and OEM-policy hints. |
| [`.ai/research/2026-05-17-pass-38/`](.ai/research/2026-05-17-pass-38/) | pass 38 | Achno Samsung debloat cross-check: audit-clean comparison against local debloat datasets; no data mutation because exact misses were typos/activity names/unverified single-source IDs. |
| [`.ai/research/2026-05-17-pass-39/`](.ai/research/2026-05-17-pass-39/) | pass 39 | Restricted Settings unlock walkthrough: install-source-aware Privileges row, Mode Doctor probe, App info / Accessibility deep-links, and classification tests. |
| [`.ai/research/2026-05-18-iter-91/`](.ai/research/2026-05-18-iter-91/) | iter 91 | Dhizuku provider detection slice: no direct API AAR, Settings -> Privileges row, Mode Doctor probe, onboarding status, and minSdk-blocked DPM carryover. |
| [`.ai/research/2026-05-18-iter-92/`](.ai/research/2026-05-18-iter-92/) | iter 92 | Scheduled Auto-Backup core: WorkManager 2.10.5 scheduler/worker, Backup settings controls, run-now/status history, and API 36/37 diagnostics carryover. |
| [`.ai/research/2026-05-18-iter-93/`](.ai/research/2026-05-18-iter-93/) | iter 93 | Scheduler battery-optimization guardrail: privileged auto-fix on schedule enable, no-privilege Android exemption prompt, and status-row battery state. |
| [`.ai/research/2026-05-18-iter-94/`](.ai/research/2026-05-18-iter-94/) | iter 94 | Scheduled backup launcher shortcuts: pinned Settings action, static launcher shortcut, authenticated no-UI dispatch, and manual WorkManager enqueue reuse. |
| [`.ai/research/2026-05-18-iter-95/`](.ai/research/2026-05-18-iter-95/) | iter 95 | Scheduled backup progress notifications: foreground WorkManager progress, current app label/stage, ETA, API 36 ProgressStyle segments/point markers, and NotificationCompat fallback. |
| [`.ai/research/2026-05-18-iter-96/`](.ai/research/2026-05-18-iter-96/) | iter 96 | Separated active/paused schedule lists parked as blocked by future multiple-schedule profiles; current scheduler is one global preference surface. |
| [`.ai/research/2026-05-18-iter-97/`](.ai/research/2026-05-18-iter-97/) | iter 97 | Scheduled-backup diagnostics: Settings status row shows WorkManager state/attempt/stop details, next run time, and API-36 JobScheduler pending-reason snapshots; API-37 `JobDebugInfo` remains blocked by compile SDK 36. |
| [`.ai/research/2026-05-18-iter-98/`](.ai/research/2026-05-18-iter-98/) | iter 98 | App-list import/export workflow: visible/filtered list export from the main menu, selected-list export preserved in selection mode, and JSON imports selecting matching installed apps for existing batch actions. |
| [`.ai/research/2026-05-18-iter-99/`](.ai/research/2026-05-18-iter-99/) | iter 99 | Provider-backed network backup destination: Settings -> Backup/Restore Network backup destination action persists a selected DocumentsProvider tree as the active backup volume and test-covers tree URI normalization. |
| [`.ai/research/2026-05-18-iter-100/`](.ai/research/2026-05-18-iter-100/) | iter 100 | WebDAV self-signed certificate trust closure: no native WebDAV/SMB client exists today, so provider-backed backups delegate TLS/user-CA trust to the selected DocumentsProvider; native `KeyChain` handling is reserved for future first-party protocol work. |
| [`.ai/research/2026-05-18-iter-101/`](.ai/research/2026-05-18-iter-101/) | iter 101 | Backup path exclusion patterns: glob parser, default throwaway directory filters, global/per-run/profile custom globs, and focused JVM coverage for matching plus batch-option serialization. |
| [`.ai/research/2026-05-18-iter-102/`](.ai/research/2026-05-18-iter-102/) | iter 102 | Tasker parameterized intent API: public confirmation-gated `am://` operation URIs, Tasker/MacroDroid start-activity action constants, profile JSON overrides, and parser coverage. |
| [`.ai/research/2026-05-18-iter-103/`](.ai/research/2026-05-18-iter-103/) | iter 103 | Hidden-Shizuku fork detection: Shizuku manager package discovery through the owner of `moe.shizuku.manager.permission.API_V23`, with legacy service-permission and canonical package fallbacks. |
| [`.ai/research/2026-05-18-iter-104/`](.ai/research/2026-05-18-iter-104/) | iter 104 | OEM debloat-blocker bypass: manufacturer/build keyed uninstall fallback policy for Samsung SmartSuggestions, MIUI core, and OPlus uninstall-guarded packages, wired into Debloater card/details warnings and safe freeze-by-default batch handling. |
| [`.ai/research/2026-05-18-iter-105/`](.ai/research/2026-05-18-iter-105/) | iter 105 | Per-app rollback: App Details "Revert AppManager changes" action, newest-first inverse planner over operation history, manual-review accounting for non-invertible rows, and focused rollback planner tests. |
| [`.ai/research/2026-05-18-iter-106/`](.ai/research/2026-05-18-iter-106/) | iter 106 | Settings portability: snapshot schema v2 exports rule TSVs, merges imported preferences/rule rows, and updates Settings copy/counts for the full migration bundle. |
| [`.ai/research/2026-05-18-iter-107/`](.ai/research/2026-05-18-iter-107/) | iter 107 | Install-date filtering: Finder `install_date` option, main-list persisted install-date range, and visible active-filter count chips with clear actions. |
| [`.ai/research/2026-05-18-iter-108/`](.ai/research/2026-05-18-iter-108/) | iter 108 | File Manager recursive in-folder search: toolbar SearchView, 250 ms debounce, active-search clear chip, nested match location subtitles, and hidden dot-path option parity. |
| [`.ai/research/2026-05-18-iter-109/`](.ai/research/2026-05-18-iter-109/) | iter 109 | Checksum properties audit: `FilePropertiesDialogFragment` already links arbitrary File Manager paths to `ChecksumsDialogFragment`, so the Material Files checksum properties row was stale. |
| [`.ai/research/2026-05-18-iter-110/`](.ai/research/2026-05-18-iter-110/) | iter 110 | Whole-volume search warning: recursive File Manager search warns on storage-volume roots, estimates scan time from platform volume size, and records Storage Analysis as the future reuse point. |
| [`.ai/research/2026-05-18-iter-111/`](.ai/research/2026-05-18-iter-111/) | iter 111 | Magisk drop-cap diagnostics: Privileges capability row now captures Magisk version/version-code, v30.7+ opt-in semantics, bounded live-policy context, and expanded parser tests. |
| [`.ai/research/2026-05-18-iter-112/`](.ai/research/2026-05-18-iter-112/) | iter 112 | KernelSU sulog/seccomp diagnostics: Privileges KernelSU row reads current-process seccomp, tails recent sulog denials, exposes copyable details, and links to KernelSU Manager / KernelSU Next. |
| [`.ai/research/2026-05-18-iter-113/`](.ai/research/2026-05-18-iter-113/) | iter 113 | Android 16 full-SDK plumbing: shared `AndroidUtils.sdkAtLeast(major, minor)` helper, API-36 scheduled-backup gate migration, and explicit audit boundary for older major-only guards. |
| [`.ai/research/2026-05-18-iter-114/`](.ai/research/2026-05-18-iter-114/) | iter 114 | Smali decode options: File Manager settings for `none` / `basic` / `verbose`, default `basic`, annotation stripping for common nullability/API annotations, and `DexFileSystem` propagation into `DexClasses`. |
| [`.ai/research/2026-05-18-iter-115/`](.ai/research/2026-05-18-iter-115/) | iter 115 | KernelSU App Profile awareness: active `su` UID/GID/groups/SELinux/CapEff details in Privileges, missing expected root-capability warnings, and parser coverage for default vs restricted profiles. |
| [`.ai/research/2026-05-18-iter-116/`](.ai/research/2026-05-18-iter-116/) | iter 116 | File Manager batch APK install: selected readable `.apk` / `.apks` / `.apkm` / `.xapk` files launch installer batch mode through content URIs, with default split selection and focused intent/policy coverage. |
| [`.ai/research/2026-05-18-iter-117/`](.ai/research/2026-05-18-iter-117/) | iter 117 | App Info SELinux context display: policy info, data/source file contexts, live process contexts, and focused package/process matching coverage. |
| [`.ai/research/2026-05-18-iter-118/`](.ai/research/2026-05-18-iter-118/) | iter 118 | JADX 1.5.5 `.apks`/zoom and FlatLaf CJK rows parked as blocked by the absent T12 JADX viewer surface; acceptance criteria preserved for future viewer work. |
| [`.ai/research/2026-05-18-iter-119/`](.ai/research/2026-05-18-iter-119/) | iter 119 | Digital Assistant quick actions: dedicated assist activity, foreground target resolver from assist extras or usage events, Force Stop / Freeze / App Details sheet, and Running Apps fallback. |
| [`.ai/research/2026-05-18-iter-120/`](.ai/research/2026-05-18-iter-120/) | iter 120 | Amarok-style `pm hide` toggle: App Info Hide/Unhide quick action backed by `PackageManagerCompat.hidePackage()`, existing Hidden badge preserved, and freeze method state left untouched. |
| [`.ai/research/2026-05-18-iter-121/`](.ai/research/2026-05-18-iter-121/) | iter 121 | Per-app language picker: App Info locale summary plus Android 13+ privileged `ILocaleManager` read/write for the selected package/user, searchable language options, and hidden API baseline coverage. |
| [`.ai/research/2026-05-18-iter-122/`](.ai/research/2026-05-18-iter-122/) | iter 122 | Sensitive action authentication gate: optional Privacy toggle, shared `ActionAuthGate`, installer commit prompt, direct/batch uninstall prompt, and direct/batch clear-data prompt. |
| [`.ai/research/2026-05-18-iter-123/`](.ai/research/2026-05-18-iter-123/) | iter 123 | F-Droid 2.0 ROM preseed docs: JSON and legacy XML repository templates, global/app-specific path guidance, README distribution link, and parser validation. |
| [`.ai/research/2026-05-18-iter-124/`](.ai/research/2026-05-18-iter-124/) | iter 124 | Android 17 targetSdk=37 audit batch: Wireless ADB local-network permission preflight, legacy-gated static-final `Resources.mSystem` workaround, and clean Keystore/MemoryLimiter/native-DCL/IntentSender BAL findings. |
| [`.ai/research/2026-05-18-iter-125/`](.ai/research/2026-05-18-iter-125/) | iter 125 | Android 17 ML-DSA certificate OID closure: verified existing OID display-name mapping in Package Info and Scanner, kept OID display, and fixed `UtilsCertificateAlgorithmTest` JVM class initialization. |
| [`.ai/research/2026-05-18-iter-126/`](.ai/research/2026-05-18-iter-126/) | iter 126 | Android 17 cleartext deprecation badge: App Info warns when manifest-wide cleartext is enabled without a Network Security Config, with hidden `networkSecurityConfigRes` compat access and predicate coverage. |
| [`.ai/research/2026-05-18-iter-127/`](.ai/research/2026-05-18-iter-127/) | iter 127 | Material You widget theming: shared AppWidget palette helper, dynamic RemoteViews tints for usage / clear-cache / log widgets, and Android 12+ system color XML fallback mappings. |
| [`.ai/research/2026-05-18-iter-128/`](.ai/research/2026-05-18-iter-128/) | iter 128 | Default-app role restore rebinds: backup metadata `default_roles`, privileged restore-time `cmd role add-role-holder`, fallback Default apps review prompt, and focused role helper coverage. |
| [`.ai/research/2026-05-18-iter-129/`](.ai/research/2026-05-18-iter-129/) | iter 129 | Scheduled-backup newest-age gate: configurable freshness window, newest valid backup per package/user selection, skipped-recent result counts, and focused scheduler coverage. |
| [`.ai/research/2026-05-18-iter-130/`](.ai/research/2026-05-18-iter-130/) | iter 130 | CIFS/SMB backup streaming hardening: durable tar creation, bounded 256 KiB SAF-provider writes, descriptor fsync when available, close-time byte-count verification, and split-stream boundary coverage. |
| [`.ai/research/2026-05-18-iter-131/`](.ai/research/2026-05-18-iter-131/) | iter 131 | Profile blocklist backup-root enumeration: backup-only package choices in the Profiles picker, selected missing-package retention, fallback stale-row rendering, and focused merge coverage. |
| [`.ai/research/2026-05-18-iter-132/`](.ai/research/2026-05-18-iter-132/) | iter 132 | Root-only Android System data backups: System data flag gated to root/system mode, Android-System-only backup/restore sanitization, Wi-Fi/Bluetooth/account root descriptors, and focused token/path coverage. |
| [`.ai/research/2026-05-18-iter-133/`](.ai/research/2026-05-18-iter-133/) | iter 133 | SquashFS header-validation row parked: no SquashFS writer/dependency/mount path exists in current source; NG backup archives are tar-family outputs through `TarUtils`, so the header fixture belongs to a future backend. |
| [`.ai/research/2026-05-18-iter-134/`](.ai/research/2026-05-18-iter-134/) | iter 134 | Per-app audio-volume AppOps: named audio-volume op family, UID-mode writer assertion, App Details group action, custom AppOps helper copy, and focused compat coverage. |
| [`.ai/research/2026-05-18-iter-135/`](.ai/research/2026-05-18-iter-135/) | iter 135 | Installer privilege cascade: dialog route chips, temporary ADB -> Shizuku -> root install-provider activation, configured-mode restore, Dhizuku/MIUI diagnostics, and focused route-order coverage. |
| [`.ai/research/2026-05-18-iter-136/`](.ai/research/2026-05-18-iter-136/) | iter 136 | Split APK cert-mismatch dialog: selected split APK signing certs are compared against the base APK before session writes, with optional bad-split removal and required-split blocking. |
| [`.ai/research/2026-05-18-iter-137/`](.ai/research/2026-05-18-iter-137/) | iter 137 | AGP 9.2.0 / Gradle 9.4.1 migration: Gradle-10-safe build scripts, `androidComponents` server packaging, explicit test classpath hardening, and floss/full/unit-test verification. |

**The full external-source corpus the project relies on is in `ROADMAP.md` -> "Source Appendix" (S01-S364).** Do not start a new external-research pass without scanning that table first — most modern Android-power-tool ground has been mined.

---

## 3. Stack, build, code stats

- **Language**: Java + Kotlin (Java in core, Kotlin in newer additions). **629 `.java` files** under `app/src/main/java/io/github/muntashirakon/AppManager/`. Kotlin file count is lower.
- **UI**: Android Views + Material Components **1.13.0**. Compose is not planned for this codebase — see `codexprompt.md` ("DO NOT propose Jetpack Compose. Compose migration is a multi-year project").
- **Build**: Gradle 9.4.1, AGP `9.2.0`, Java 8 source/target with desugaring, NDK `28.2.13676358` + CMake for native.
- **min/target SDK**: **21 / 36**.
- **Modules** (top-level): `app/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/`, `libs/`, `scripts/`, `docs/`, `fastlane/`, `LICENSES/`.
- **app package tree** (excerpt — what each folder does is mostly inferrable from the name): `accessibility`, `adb`, `apk`, `app`, `backup`, `batchops`, `changelog`, `compat`, `crypto`, `db`, `debloat`, `details`, `dex`, `editor`, `filters`, `fm` (file manager), `history`, `intercept`, `ipc`, `logcat`, `logs`, `magisk`, `main`, `misc`, `miui`, `onboarding`, `oneclickops`, `permission`, `permissions`, `profiles`, `progress`, `rules`, `runner`, `runningapps`, `scanner`, `self`, `servermanager`, `session`, `settings`, `sharedpref`, `shizuku`, `shortcut`, `ssaid`, `sysconfig`, `terminal`, `types`, `uri`, `usage`, `users`, `utils`, `viewer`.
- **CI workflows** ([`.github/workflows/`](.github/workflows/)): `codeql.yml`, `dependency-scan.yml`, `lint.yml`, `release.yml`, `tests.yml`, `upstream-rename-watch.yml`.

### Key dependency pins (from [`versions.gradle`](versions.gradle))
| Dep | Pinned | Notes |
|-----|--------|-------|
| `compile_sdk` | 36 | Android 16 |
| `agp_version` | `9.2.0` | AGP 9.2 migration complete; wrapper pinned to Gradle 9.4.1 |
| `ndk_version` | `28.2.13676358` | Pinned during AGP 9.2 migration so native debug builds do not float |
| `json_version` | `20251224` | Host JVM `org.json` implementation for unit tests |
| `material_version` | `1.13.0` | **Ceiling** — `1.14.0-rc01` requires minSdk 23 |
| `bouncycastle_version` | `1.84` | CVE-2026-3505 / 5588 / 5598 closed |
| `gson_version` | `2.14.0` | Built-in `java.time` adapters, strict duplicate-JSON-key handling |
| `libsu_version` | `6.0.0` | `Shell.cmd` migration audit clean |
| `shizuku_version` | `13.1.5` | Shizuku-API floor for compile-time; runtime tested against Shizuku Manager 13.6.0+ |
| `work_version` | `2.10.5` | Scheduled Auto-Backup core uses this API-21-compatible WorkManager line; 2.11.x raises the Android floor. |
| `guava_version` | `32.1.3-android` | Compile-only pin for WorkManager's exposed `ListenableFuture`; runtime already packages Guava via APK-editing/editor dependencies. |
| `jadx_version` | `1.4.7` | **7 releases behind** 1.5.5. Upgrade before T12 APK editing work. |
| `min_sdk` | 21 | Bump gated by [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md). |
| `activity_version` | `1.11.0` | API 21-22 dropped in 1.12.x |
| `biometric_version` | `1.4.0-alpha04` | API 21-22 dropped in 1.4.0-alpha05 |
| `room_version` | `2.7.2` | API 21-22 dropped in 2.8.x |
| `webkit_version` | `1.14.0` | API 21-22 dropped in 1.15.x |

The minSdk-21 floor is a load-bearing decision; the ledger documents which deps it freezes.

---

## 4. Current pass-39 state as of 2026-05-17

The stale pass-1 "uncommitted work" list is resolved. The Finder regex fix, install-transcript
redactor, and onboarding detach fix all landed in local commits (`73387cd`, `bcb2874`,
`25c629a`) and are documented in `CHANGELOG.md`.

Pass 4 added three small implementation follow-ups:

| File / area | Type | Diff highlight |
|-------------|------|----------------|
| [`ShizukuBridge.java`](app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) + [`OnboardingFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) | platform-risk mitigation | Android-17-only `hasAndroid17CompatibilityRisk(Context)` warning while Shizuku's fixed-version floor is unknown; warning launches the existing Wireless ADB setup flow. |
| [`.github/workflows/shizuku-release-watch.yml`](.github/workflows/shizuku-release-watch.yml) | process automation | Weekly official Shizuku release watcher opens a maintainer issue when 13.6.x / 13.7.x release notes mention Android 17 or #1965 / #1967. |
| [`Utils.java`](app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java), [`PackageUtils.java`](app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java), [`ScannerFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java) | Android 17 polish | ML-DSA-65 / ML-DSA-87 certificate OIDs render as readable Dilithium algorithm names instead of raw OIDs where the platform provider lacks a friendly name. |

Pass 5 closed the T5 "USB Debugging Prompt in Shizuku Setup" row: onboarding's
Wireless ADB setup flow now preflights the `adb_enabled` Developer Options flag
and prompts users to enable both USB debugging and Wireless debugging before
pairing. The Shizuku / Wireless ADB / pairing instructions now name both toggles
so the silent `adb pair` / `adb connect` failure path is visible before setup.

Pass 6 closed T5's installer checksum row: `PackageInstallerCompat` hashes bytes
as they stream into `PackageInstaller.Session.openWrite()`, passes the digest
through `PackageInstallerBroadcastReceiver`, and `PackageInstallerActivity`
shows a pre-system-prompt checksum dialog. The same pass left Dhizuku open after
checking primary sources: Dhizuku-API `2.5.4` is current, but its upstream module
declares `MIN_SDK = 26`; NG is still API 21, so a reflection/optional-provider
design or minSdk decision is needed before integration.

Pass 7 closed T5's root/ADB battery-optimization auto-fix row: new
`SelfBatteryOptimization` centralizes NG's own Doze-exemption state and privileged
`DEVICE_POWER` auto-fix, `ProfileApplierService` calls it for routine/profile
execution, `BatchOpsService` calls it for long-running backup/import/restore
operations, and `TroubleshootingPreferences` reuses the same helper for the
manual UI path. Iter-93 wired the same helper into Scheduled Auto-Backup enablement
and added the no-privilege Android exemption prompt from Settings -> Backup.
Iter-94 then added the scheduled-backup launcher shortcut surface without adding a
second execution engine: shortcut launches queue the same manual scheduled-backup
WorkManager request as Settings -> Backup's "Run scheduled backup now" row.
Iter-95 wired scheduled/manual auto-backup runs into the existing batch/backup
progress hooks so the foreground notification reflects current app progress
instead of a generic "running" message.
Iter-96 parked the active/paused schedule-list row because the current scheduler
has no list model; reopen only if NG adds multiple named schedule profiles.

Pass 8 closed cross-user package state and Finder multi-user scope: `ApplicationItem`
now keeps per-user enabled/disabled/uninstalled buckets, the main list and
multi-user picker show explicit user-state labels, and Finder loads all selected
users through `Users.getUsersIds()` while labeling each result with user/state.

Pass 9 closed T5's auto-update debloat-definition row: Settings → Privacy now has a
default-off "Update debloat definitions on launch" opt-in that only runs when the
existing Internet feature gate is enabled. `DebloatDefinitionsUpdater` fetches the
pinned AppManagerNG raw-GitHub manifest, verifies `debloat.json` and
`suggestions.json` by byte length and SHA-256, schema-smoke-tests both payloads,
writes them atomically to app-private storage, and `StaticDataset` falls back to
bundled assets when no valid cache exists.

Pass 10 closed T5's Privilege Health-Check Screen row: Settings now has a
`PrivilegeHealthPreferences` page titled **Privileges** with current-mode diagnostics,
root manager detection, Shizuku API/min-version status, USB/Wireless ADB status,
remote server/service status, a mode self-test row, and the same
`SelfBatteryOptimization` one-tap battery whitelist path used by Troubleshooting.

Pass 11 closed T5's Android 16 capability-dropping UI row: Settings -> Privileges
now has a "Capability dropping (--drop-cap)" row backed by
`RootCapabilityDiagnostics`, which runs through the active `Runner` privilege path,
reads `id -u` plus `CapEff` from `/proc/$$/status`, and reports root, dropped,
present, unavailable, or unknown states. The remaining deeper Magisk-version or
root-manager config parsing belongs to the separate T9 provider-introspection row.

Pass 12 audited T5's VPN plugin flags row and parked it as blocked because the repo
has no `VpnService`, `BIND_VPN_SERVICE`, or Shizuku VPN binding/session surface to
control. The same pass closed T7's Finder description-field search row by extending
`BloatwareOption` with `description_*` predicates over `DebloatObject.getDescription()`,
so Finder can match plain-language debloat prose from the bundled or cached
definition set.

Pass 13 closed T7's Finder uninstalled-app-backups row. `FinderViewModel` now opts
into backup-only rows after its PackageManager pass by reading the AppManagerNG
backup metadata DB, validating each archive's presence, selecting the newest backup
per package/user pair, and skipping pairs already returned by
`MATCH_UNINSTALLED_PACKAGES`.
`BackupFilterableAppInfo` is the synthetic row adapter for archived uninstalled apps;
it preserves label/version/system/has-code/keystore/rule signals while reporting
`isInstalled() == false` so Finder's visible user/state line and existing filters
continue to behave like normal package rows.

Pass 14 closed T7's Filter: Permission Flags row. `FilterablePermissionInfo` is the
shared permission-state model for requested permissions: grant state comes from
`PackageInfo.requestedPermissionsFlags`, runtime permission flags come from
`PermissionCompat.getPermissionFlags()` when the active privilege path can read
them, and custom-source/fixed-state helpers feed `PermissionsOption`. Both
`FilterableAppInfo` and `ApplicationItem` now expose this model through
`IFilterableAppInfo.getAllPermissionDetails()`.

Pass 15 closed T7's Finder relevance-scoring row. `FinderViewModel` still uses the
existing `FilterItem` evaluator for inclusion, then sends results through
`FinderRelevanceScorer` when literal package-name, component-name, or tracker-name
search predicates are present. The scorer ranks by Levenshtein distance against
full package names, simple package names, tokens, sliding windows, and matched
component/tracker class names; regex/negative predicates are skipped and unrelated
filter matches keep their original scan order.

Pass 16 closed the iter-22/T8 Broadcast Intent API row. `AutomationIntents` defines
the stable `io.github.sysadmindoc.AppManagerNG.action.*` action/extra constants,
`AutomationReceiver` is exported behind the signature-only
`io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION` permission, and dispatch
reuses existing execution paths: `BatchOpsService` for package operations,
`ProfileApplierService` for profiles, `PackageInstallerActivity` for install URI
handoff, and `AppDetailsActivity.getIntentForTrackers()` for tracker review.

Iter-102 added the public side of that surface. `AutomationUriActivity` is exported
for BROWSABLE `am://` operation hosts and for `startActivity` calls using the same
automation action constants, but it stays behind `BaseActivity` authentication and
an explicit confirmation dialog. `AutomationRequest` normalizes URI params plus
Tasker-style string extras, and `ProfileQueueItem.fromProfile(..., overrides)`
creates temporary one-run profile snapshots so `EXTRA_PROFILE_OVERRIDES` can alter
packages/backup flags without mutating saved profile files.

Pass 17 closed the T11 APK Share-Target Receiver row as stale. The manifest already
exports `PackageInstallerActivity` for shared APK/APKM/XAPK payloads via
`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`, and `ACTION_INSTALL_PACKAGE`;
`ApkQueueItem.fromIntent()` already maps those incoming URIs into installer queue
items while preserving URI permission grants; and `PackageInstallerActivity` already
surfaces tracker counts, dependency warnings, session SHA-256 confirmation, and
signature-mismatch handling in the install flow.

Pass 18 closed the T8 App Shortcut: Freeze / Force-Stop / Clear Cache Per-App row.
`AppActionShortcutPublisher` refreshes dynamic launcher shortcuts from the loaded
main-list app snapshot, ranking recent installed apps and only exposing actions
the current privilege path supports. `AppActionShortcutActivity` is non-exported
and authenticated through `BaseActivity` before it dispatches to `FreezeUtils` or
`PackageManagerCompat`. App Details long-press affordances now pin force-stop and
clear-cache shortcuts, and freeze/unfreeze shortcuts use explicit action labels by
default.

Pass 19 closed the T1 `floss` vs `full` Build Flavors row. `app/build.gradle`
now defines a `distribution` flavor dimension with default `floss` and optional
`full`; `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES` gates the optional online
surfaces through `FeatureController`. `floss` keeps ADB / localhost networking
available but disables the user-facing Internet feature switch, VirusTotal,
Pithus lookups, and debloat-definition auto-updates. `full` keeps the existing
opt-in behavior. Release scripts now collect recursive flavor APK outputs, CI
artifact globs handle flavored reports, and Obtainium targets `full` assets.
F-Droid metadata should build `flossRelease`; see
[`docs/distribution/build-flavors.md`](docs/distribution/build-flavors.md).

Pass 20 closed the T4 LocalServer Bootstrap Smoke Test row. `LocalServer` now has
a shared `buildBootstrapSignature(...)` formatter used by the existing failure log
and by the success-path smoke test. Settings -> Privileges adds a "LocalServer
bootstrap smoke test" row that runs the privileged-shell handshake plus `id -u`,
then shows the device/build/mode/UID/LineageOS/probe/exception signature in a
copyable dialog. This intentionally lands in the existing Privileges diagnostics
screen rather than a new Developer page because pass 10 made that screen the
project's mode and remote-service health-check home.

Pass 21 closed the T4 Support Info Bundle Composer row. `SupportInfoBundle`
creates a zero-network `support-info-<device>-<timestamp>.txt` file from Settings
-> Troubleshooting, sharing it through `FmProvider` with explicit ClipData grants.
The bundle records app/build version, Android/ROM fields, configured/inferred
mode, provider status, root-manager/Sui/ZygiskNext markers, feature flags, the
remembered LocalServer bootstrap signature, and a 120-line logcat tail scrubbed
for package-like tokens, URIs, storage paths, emails, UIDs, and large numeric IDs.
`LocalServer` now persists the latest bootstrap signature for this bundle.

Pass 22 closed the T9 Privileged Op Audit Log row. The row was partly stale:
`op_history` already existed as a Room append-only log with viewer/search/filter,
rerun/share/delete actions, JSON/CSV/text export, and Settings -> Privacy
retention. The follow-through added the two explicit roadmap fields that were
missing from exported metadata: normalized `exit_code` values for batch/profile
success/failure and installer platform status codes, plus the remembered
LocalServer bootstrap signature. Details and JSON/CSV exports now carry those
fields so support reports can correlate privileged actions with the most recent
bootstrap context. Per-command interrupted-shell replay remains the separate
Privileged-Shell Journal row.

Pass 23 closed the T5 Privileged-Shell Journal + DeathRecipient Replay row.
`BatchOpsJournal` stores active `BatchQueueItem` intent/executing state in
app-private preferences before batch execution and clears it only after
`BatchOpsManager.performOp(...)` completes. `BatchOpsService` registers a
Shizuku `OnBinderDeadListener` while Shizuku/Sui-backed batches run, marks the
journal interrupted on binder death or uncaught batch exceptions, and leaves the
entry behind if the process is killed mid-batch. `MainActivity` checks the
journal on reattach when no batch service is active and offers retry/not-now/clear
actions. Normal per-package failures still use the existing failed-app retry
screen; the journal is for ambiguous interrupted batches.

Pass 24 closed the T4 Mode Self-Test "Doctor" row. Settings -> Privileges now
has a "Mode doctor" action that runs active probes distinct from the passive
health rows. `PrivilegeModeDoctor` reports configured/inferred mode, root
grant/root manager/Sui, Shizuku binder/UserService/permission, ADB
USB/wireless/pairing state, LocalServer `id -u`, SELinux domain, and ABIs as a
copyable PASS/WARN/FAIL/SKIP report with fix hints for each provider path.

Pass 25 closed the T5 Shizuku Trusted-WLAN Auto-Start Awareness row.
`ShizukuBridge` now treats trusted-WLAN auto-start as supported only on Android
13+ with Shizuku Manager `>=13.6.0`, and offers it only when the Shizuku binder
is stopped. Settings -> Operating Mode and onboarding both show a "Configure
auto-start in Shizuku" button in that state. The intent tries the roadmap
`moe.shizuku.privileged.api/.AUTO_START` component first, then falls back to
Shizuku's launcher or Android app-info screen because the v13.6.0 manifest does
not expose that component universally.

Iter 103 updated that package-facing Shizuku path for renamed/obfuscated
managers. `ShizukuBridge.getManagerPackageName(Context)` resolves the manager
from the package declaring `moe.shizuku.manager.permission.API_V23`, falls back
to the roadmap's legacy service permission owner, and only then uses the
canonical package. The trusted-WLAN auto-start/app-info fallback, manager version
checks, and clear-data manager warning all read through the resolver.

Pass 26 parked the T6 JobScheduler Quota Stop-Reason Surfacing row after a fresh
source and Gradle audit found no `androidx.work`, WorkManager, JobScheduler,
JobService, JobParameters, or Schedules-screen implementation in current NG
source. The requirement now lives on the T6 Scheduled Auto-Backup row: when the
scheduler is implemented, it must capture WorkManager / JobScheduler stop and
pending reasons in schedule history/result notifications from the start.

Pass 27 parked the Eng-Debt Apktool 3.0.2 migration row as stale for current
source. AppManagerNG has no `org.apktool` / `brut.apktool` dependency or call
site to migrate; APK editing currently uses ARSCLib, apksig, Google
smali/baksmali, and JADX. If T12 later adds an Apktool-backed decode/rebuild
backend, target `org.apktool:apktool-lib:3.0.2+` only after duplicate-class
testing against the existing Google smali/baksmali classpath.

Pass 28 closed the T8 Hail-style Auto-Freeze QuickSettings Tile row.
`QuickFreezeTileService` is an exported platform QS tile gated by
`android.permission.BIND_QUICK_SETTINGS_TILE`; profile-list popup menus now let
users select or clear the freeze-enabled profile that the tile runs. Tapping the
tile unlocks first if necessary, then starts the existing `ProfileApplierService`
with that profile in `BaseProfile.STATE_ON` so existing progress/history/freeze
logic stays centralized.

Pass 29 closed the Eng-Debt Hidden-API Compatibility Harness row. The old roadmap
claim that `app/src/main/assets/api/api-versions-*.json` already existed was stale;
NG now checks in `app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json`
instead. `scripts/generate-hidden-api-baseline.ps1` regenerates that baseline from
`hiddenapi/src/main/java`; `HiddenApiDescriptorBaselineTest` verifies source-file
coverage; and `HiddenApiCompatibilityInstrumentedTest` loads the asset on-device,
applies HiddenApiBypass exemptions, probes required hidden classes/methods/fields
against the active SDK, and writes a JSON diff report before failing on missing
required APIs.

Pass 30 closed the T5 Shizuku Permission Auto-Revoke Warning row. `ShizukuBridge`
now detects clear-data targets that are AppManagerNG, Shizuku Manager, or installed
apps declaring `rikka.shizuku.ShizukuProvider`. App Info and main-list batch
clear-data confirmations surface the warning; direct privileged App Info clears
capture AppManagerNG's Shizuku permission before the clear, re-check after success,
and deep-link to Settings -> Mode of operation if the grant vanished. Batch/profile
clear-data has no foreground recovery dialog, so it records the same revoked-grant
signal in the operation log.

Pass 31 closed the T8 Freeze / Operation Audit Log row. The underlying history
surface already existed: Room-backed `OpHistoryActivity`, filters, risk/reversible
chips, JSON/CSV/text export/share, target opening, rerun preflight, rollback
guidance, and batch/profile/installer journaling. The pass added Settings ->
Privacy -> History and a per-row "Recovery guidance" action for reversible
operations. Automatic inverse replay remains in the separate Per-App Rollback row.

Pass 32 closed the Eng-Debt Android 17 16 KB Page-Size Compatibility row. The
native build now adds 16 KB ELF linker flags, `NativeLibraries` parses
ELF32/ELF64 program headers and surfaces per-library 16 KB alignment status, and
the reproducible-release scripts run `scripts/verify-native-page-alignment.py`
against every publish APK. The audit lives at
[`docs/audits/2026-05-17-android17-16kb-native-page-size.md`](docs/audits/2026-05-17-android17-16kb-native-page-size.md).

Pass 33 closed the T1 Android Developer Verification guardrails row. The
installer now warns when Android exposes the `developer_verifier` service and
propagates `PackageInstaller` developer-verification failure reasons into result
diagnostics. App Details surfaces verifier status as unknown rather than
overclaiming a public preflight verdict that Android does not expose.

Pass 34 closed the T5 Shizuku 13.6.0 OEM Allowlist row. `ShizukuBridge` now
returns a typed OEM compatibility warning for Shizuku 13.6.0-era runtimes on
Transsion Android 15 ROMs, Mediatek platform tags, and Pixel 9 / Android 16
QPR1-class builds. Onboarding and Settings -> Operating Mode expose tappable /
button deep links to the Shizuku 13.5.4 IzzyOnDroid/F-Droid archive, while
Settings -> Privileges and Mode Doctor include the same downgrade guidance in
their diagnostics.

Pass 35 closed the T5 Shizuku Root-Backed Avoidance for Banking Apps row.
`ShizukuBridge` now classifies uid-0 Shizuku sessions as root-backed, Auto mode
skips that provider when local ADB is available, and Settings -> Mode of
Operation offers a one-tap switch to Wireless ADB or ADB-over-TCP with a tooltip
explaining the banking / Play Integrity side effect. Onboarding, Settings ->
Privileges, and Mode Doctor now surface matching root-backed warnings.

Pass 36 closed the T9 OS-Revert Detection Banner row. `OsRevertMonitor` now
schedules 30-second expected-vs-current probes after Doze allowlist mutations,
freeze/unfreeze, component enabled-state writes, and AppOps mode writes. The
event surface lives in `BaseActivity` as an "OS reverted your change - see why"
Snackbar that opens a detail dialog with target, operation, expected state,
current state, and a short context hint.

Pass 37 closed the T5 Backup-Aware Doze Allowlist Diff Banner row.
`DozeAllowlistDiagnostics` now enriches the Doze branch of `OsRevertMonitor`:
it waits 60 seconds after a battery-optimization allowlist write, snapshots
legacy `device_idle_constants` plus `DeviceConfig device_idle`, and adds a
one-line config diff plus user-app / Samsung-Knox / system-app / unknown-policy
hint to the Doze revert detail dialog.

Pass 38 closed the T7 Achno Samsung Debloat List Cross-Check row as
audit-clean. The audit extracted 82 package-like tokens from the Achno README,
found 76 already covered by local debloat datasets, and documented why the six
exact misses are not safe to add without stronger evidence.

Pass 39 closed the T5 Restricted Settings Unlock Walkthrough row.
`RestrictedSettingsDiagnostics` now reads AppManagerNG's install-source metadata
and classifies Android 13+ installs as trusted-store, likely sideloaded, unknown,
or review-recommended. Settings -> Privileges exposes the walkthrough with App
info and Accessibility deep-links, while Mode Doctor includes the same
install-source details and fix hint. The wording stays "likely/recommended"
because Android does not expose a public per-app restricted-settings-blocked bit.

Iter 104 closed the T7 OEM Debloat-Blocker Bypass row. `OemBloatRiskTable`
now covers uninstall-fallback policy for Samsung One UI 8.5
SmartSuggestions, MIUI core, and exact OPlus/ColorOS/OxygenOS/Realme
uninstall-guarded package IDs. Debloater rows/details show protected-package
warnings; batch remove confirmations count affected selections and default to
the existing freeze batch path while preserving explicit removal as the
override.

Unit-test files from passes 4-39 and iter-91 onward cover the new helpers
where runtime code changed. As of 2026-05-18 iter-104, this Windows checkout
can run focused Floss JVM tests plus `compileFlossDebugJavaWithJavac` and
`assembleFlossDebug` successfully.

---

## 5. Hard project facts (don't relearn these)

These are decisions, gotchas, and non-obvious facts already documented elsewhere in the
repo. Reading them here saves a fresh AI session a re-discovery pass.

### License & redistribution
- **GPL-3.0-or-later** throughout. New files must carry `// SPDX-License-Identifier: GPL-3.0-or-later` (or `<!-- … -->` for XML/HTML).
- **`COPYING` and `LICENSES/` directory are load-bearing** — never delete or "tidy". F-Droid / IzzyOnDroid review fails without REUSE compliance.
- Vendored dep licenses (Apache-2.0, BSD-2/3, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL) live under `LICENSES/`. Each must remain.

### Identity vs source namespace
- **applicationId** = `io.github.sysadmindoc.AppManagerNG` (Android install identity → installs side-by-side with upstream AppManager).
- **Java/Kotlin package namespace** stays `io.github.muntashirakon.AppManager` — do **not** rename as a drive-by; it'd be a high-risk churn commit for no behavior win.
- `app_name` resolves to "AppManagerNG"; many internal dialogs still say "App Manager" (copy debt scheduled for v0.3.0+ sweep).

### Debug keystore is intentionally checked in
- `app/dev_keystore.jks` + plaintext password in `app/build.gradle` is upstream's deliberate shared debug-signing config so devs can install debug builds over each other. **Not a leak.** Do not "fix".
- Release signing uses a separate keystore — released APK SHA-256 fingerprint is `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`. Published at the stable URL [`docs/fingerprints.txt`](docs/fingerprints.txt) for programmatic verification (AppVerifier, etc.).

### Submodules required at build time
- `scripts/android-libraries` and `scripts/android-debloat-list` — fetched from upstream-maintained repos via `git submodule update --init --recursive`. Tracker scanner won't ship its database without them.
- `.gitmodules` currently points at upstream MuntashirAkon repos for these — that is a build-dependency pointer, not a fork relationship.
- A SysAdminDoc fork of `android-debloat-list` (`+112 entries` from S22 Ultra US scrape, then `+562` UAD-NG delta sync) is referenced from commit `c3fb75b` onward.
- Runtime debloat-definition updates are now decoupled from APK releases through [`docs/debloat-definitions/manifest.json`](docs/debloat-definitions/manifest.json). Future list-refresh-only work should regenerate `app/src/main/assets/debloat.json` / `suggestions.json`, update the manifest byte counts + SHA-256s, and let app clients fetch the verified snapshot when the user has opted in.
- The Achno Samsung prose list was cross-checked in
  [`docs/audits/2026-05-17-achno-samsung-debloat-cross-check.md`](docs/audits/2026-05-17-achno-samsung-debloat-cross-check.md);
  do not add the six exact misses from that list without a second independent
  package dump because they currently look like typos, activity names, or
  uncorroborated single-source IDs.

### Pull policy vs upstream (`MuntashirAkon/AppManager`)
- **Security fixes from upstream**: pull immediately regardless of conflict cost.
- **Bug fixes**: cherry-pick within one upstream release cycle.
- **Upstream "Upcoming Features"** (Finder, APK editing, Routine Ops, Crash Monitor, Database Viewer, Terminal): pull when upstream ships; NG reimplements only if upstream stalls for >12 months.
- A weekly CI workflow [`upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml) opens an auto-issue if `MuntashirAkon/AppManager` changes slug.

### "Pro Mode" / Premium facelift
- The design facelift in [`design/`](design/) ships behind **`PREF_PREMIUM_PREVIEW_BOOL`** (Settings → Appearance → "Preview new design (BETA)", default OFF as of v0.4.x). The v0.5.x phase is in progress — `activity_main_v2.xml` and `item_main_v2.xml` are wired behind the toggle. v0.6.x flips default to ON; v0.7.x removes the toggle and retires classic themes.

### Hardware verification context
- Primary on-device test target: **Samsung S25 Ultra (SM-S938B)** — One UI 8.x / Android 16.
- Landscape rotation triggers the `values-w600dp/dimens.xml` adaptive overrides (833dp).
- Test matrix that bugs are reported against: Pixel 9a (Android 17, 16-KB page size), Poco F3 (Infinity-X 3.9, Android 16 QPR2, Root), LineageOS 23.2 / Android 16, Moto g22 (Unisoc T606 / Android 12, pre-A13 activity-alias bug), Galaxy A57 (One UI 8.5), Redmi Note 13 Pro 5G (crDroid 12.9 + KernelSU). All sourced from upstream issues mined in iter-18 → iter-23.

### Privilege provider matrix (per-user mode)
- **Root** — historical default. libsu 6.0.0 backed.
- **Shizuku / Sui** — first-class as of 2026-05-14 (`feat: add Shizuku privilege provider`). Compile-time pinned at `shizuku_version = 13.1.5`; onboarding wizard checks for manager version ≥13.6.0 (Android 16 QPR1 guidance).
- Shizuku 13.6.0 is now treated as known-bad on Transsion/Infinix/Tecno/Itel
  Android 15 ROMs, Mediatek platform tags, and Pixel 9 / Android 16 QPR1-class
  builds. `ShizukuBridge.getOemCompatibilityWarning(Context)` is the canonical
  detector and links affected users to the Shizuku 13.5.4 archive.
- On Android 17+, onboarding now warns that Shizuku has unresolved compatibility reports
  (#1965 / #1967 / #1988) and offers Wireless ADB setup while
  `MIN_ANDROID_17_COMPATIBLE_VERSION` remains unknown.
- **ADB** — wireless ADB pairing wizard shipped 2026-05-14.
- **KernelSU / APatch / Magisk / ZygiskNext** — detected by `runner/RootManagerInfo`, surfaced as suffix on the onboarding sheet's Root status line.
- **Dhizuku (DPM via Binder proxy)** — `dhizuku/DhizukuBridge` detects installed
  Dhizuku, official DeviceOwner/ProfileOwner component, API provider visibility,
  API permission, and Android 8-16 bounds in onboarding, Settings -> Privileges,
  and Mode Doctor without linking the Dhizuku-API AAR. Full DPM operations remain
  blocked until the API-21 floor conflict is resolved; upstream Dhizuku-API `2.5.4`
  currently declares `MIN_SDK = 26`.
- **Battery optimization auto-fix** — `self/SelfBatteryOptimization.java` is the canonical
  helper for AppManagerNG's own Doze exemption state. Use it instead of adding new
  direct `PowerManager` / `DeviceIdleManagerCompat` checks for NG's package.
  Scheduled Auto-Backup enablement now calls it before falling back to Android's
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt.
- **Doze allowlist revert diagnostics** — `revert/DozeAllowlistDiagnostics.java`
  is the Doze-specific companion to `OsRevertMonitor`. It owns
  `device_idle_constants` / `DeviceConfig device_idle` snapshotting and policy
  hints; keep future OEM allowlist-revert explanations there.
- **Privilege health diagnostics** — `settings/PrivilegeHealthPreferences.java` is now the
  persistent Settings surface for mode/provider diagnostics. `runner/RootCapabilityDiagnostics.java`
  is the canonical active-shell capability probe. Add future VPN plugin flag checks
  and provider health probes there rather than rebuilding a new diagnostics page.
- **Restricted Settings diagnostics** — `settings/RestrictedSettingsDiagnostics.java`
  owns Android 13+ install-source classification for the manual "Allow restricted
  settings" walkthrough. Keep the wording probabilistic: Android exposes install
  source metadata, not a public per-app restricted-settings-blocked bit.
- **FireOS SYSTEM USER** — Under Consideration (T11 row; ~1M Fire devices have no AM-class power tool).

### Backup engine
- Crypto modes: AES / RSA / ECC / OpenPGP. AES is Android Keystore-backed (hardware-isolated where TEE available) — **not** the original roadmap's PBKDF2 sketch.
- Metadata v6 (2026-05-16) introduces per-file AES-GCM IV derivation while keeping v5-and-older backups restorable.
- Integrity verification (`BackupItems.Checksum` + per-file SHA-256) was already shipped — closed as "pre-existing" in 2026-05-16 hygiene pass.
- Data tar creation uses `BackupPathExclusionPatterns` to apply default throwaway-folder globs plus Settings, per-run, and profile-specific custom glob lists. Cache-like defaults are gated by the existing `BACKUP_CACHE` flag.
- Multi-format ingest: APK / APKS / APKM / XAPK with OBB support.

### Don'ts
- Don't add code via blanket AI generation that bypasses review — NG's `CONTRIBUTING.md` replaces upstream's flat AI ban with a tool-assisted-but-reviewed contract; the rule still constrains how AI work lands.
- Don't bundle CC-BY-NC-SA datasets (e.g. DuckDuckGo Tracker Radar) — incompatible with GPL-3.0 redistribution. Roadmap UC row documents this.
- Don't introduce a new third-party dep without checking F-Droid Anti-Features rules (`Tracking` / `NonFreeNet` / `NonFreeAdd` / `NonFreeAssets`) — see ROADMAP source [S244].

---

## 6. The next things on the runway

(See [`ROADMAP.md`](ROADMAP.md) for the full picture — these are just the headline targets.)

- **v0.5.0** — Settings reorganization by task; global in-app search; contextual help tooltips; **in-app changelog viewer** (replaces bundled upstream v4.0.5 changelog).
- **v0.6.0** — Rootless Power: Shizuku integration polish + wireless ADB auto-pairing polish + rootless debloat. (Most of the engine work shipped 2026-05-14 — v0.6.0 is the user-visible roll-up.)
- **Now / Eng-Debt** — Android 17 device verification for Shizuku's fixed-version floor and 16 KB page-size install testing on a real/emulated 16 KB image. The hidden-API harness shipped in pass 29, the Shizuku clear-data warning shipped in pass 30, the freeze / operation audit-log UX closure shipped in pass 31, and Android 17 16 KB native-page remediation shipped in pass 32.
- **Distribution next** — IzzyOnDroid listing, F-Droid listing, Accrescent listing. All gated on the rename being public + reproducible builds (both done).

---

## 7. How to keep this file useful

- This is an **index**, not a memory dump. If you have a new fact to record, ask first whether it belongs in `ROADMAP.md` (planned work), `CHANGELOG.md` (shipped work), `docs/audits/<date>-<topic>.md` (audit verdict), `docs/research/<date>-<topic>.md` (research delta), or `CLAUDE.md` (tool gotcha). Only update this file when the **entry point** changes — e.g. a new top-level directory, a new mandatory read order, a load-bearing convention flip.
- Tool-specific instruction files (`CLAUDE.md`, `AGENTS.md`) **must not be merged away**. They remain the tool entry points; this file is the project-state consolidation they both point at.
- Source citations live in the `ROADMAP.md` Source Appendix (S01–S340). Add new sources there, then reference by `[Sxxx]` in roadmap rows / changelog entries / audit docs.

---

**Maintainer note**: this file was reconciled by autonomous deep-research passes on
2026-05-17. The audit artifacts are split across [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/),
[`.ai/research/2026-05-17-pass-2/`](.ai/research/2026-05-17-pass-2/),
[`.ai/research/2026-05-17-pass-3/`](.ai/research/2026-05-17-pass-3/),
[`.ai/research/2026-05-17-pass-4/`](.ai/research/2026-05-17-pass-4/),
[`.ai/research/2026-05-17-pass-5/`](.ai/research/2026-05-17-pass-5/),
[`.ai/research/2026-05-17-pass-6/`](.ai/research/2026-05-17-pass-6/),
[`.ai/research/2026-05-17-pass-7/`](.ai/research/2026-05-17-pass-7/),
[`.ai/research/2026-05-17-pass-8/`](.ai/research/2026-05-17-pass-8/),
[`.ai/research/2026-05-17-pass-9/`](.ai/research/2026-05-17-pass-9/),
[`.ai/research/2026-05-17-pass-10/`](.ai/research/2026-05-17-pass-10/),
[`.ai/research/2026-05-17-pass-11/`](.ai/research/2026-05-17-pass-11/),
[`.ai/research/2026-05-17-pass-12/`](.ai/research/2026-05-17-pass-12/),
[`.ai/research/2026-05-17-pass-13/`](.ai/research/2026-05-17-pass-13/),
[`.ai/research/2026-05-17-pass-14/`](.ai/research/2026-05-17-pass-14/),
[`.ai/research/2026-05-17-pass-15/`](.ai/research/2026-05-17-pass-15/), and
pass-specific follow-through directories through
[`.ai/research/2026-05-17-pass-39/`](.ai/research/2026-05-17-pass-39/).
