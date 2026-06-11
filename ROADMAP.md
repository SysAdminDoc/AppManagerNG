<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Research-Driven Additions

### P0

- [ ] P0 — Android 17 behavior-change audit batch (API 37)
  Why: A17 stable is imminent (Beta 4.1 2026-06-01); targetSdk-37 changes hit NG's core mechanics: static-final fields unmodifiable via reflection (hidden-API bypass stack), lock-free MessageQueue (reflection into privates breaks), ACCESS_LOCAL_NETWORK runtime permission (wireless-ADB mDNS discovery), cleartext-attribute deprecation (localhost carve-out).
  Evidence: https://developer.android.com/about/versions/17/behavior-changes-17 ; https://developer.android.com/about/versions/17/behavior-changes-all
  Touches: docs/audits/ (one dated audit per change), hiddenapi/, libserver/, adb/, app/src/main/res/xml/network_security_config.xml
  Acceptance: four dated audit docs with verdicts per docs/audits doctrine; confirmed-needs-fix findings get their own rows; app runs its privileged paths on an A17 emulator (android17-emulator.yml) without regressions.
  Complexity: M

### P1

- [ ] P1 — Backup/restore round-trip integration tests in emulator CI
  Why: The backup engine has the repo's highest debt concentration (10+ TODOs), zero integration coverage, and is the subsystem users distrust most; the android17-emulator.yml workflow already exists to ride on.
  Evidence: RESEARCH.md §Architecture (test gaps); backup/adb/AndroidBackupHeader.java:375 FIXME; .github/workflows/android17-emulator.yml
  Touches: app/src/androidTest/ (new backup round-trip suite), .github/workflows/android17-emulator.yml
  Acceptance: CI installs a fixture app, backs up (no-crypto + AES), uninstalls, restores, and asserts data equality; suite runs on every PR touching backup/.
  Complexity: M

- [ ] P1 — Port upstream restore fixes from the v4.1.0 milestone
  Why: Upstream closed 39 issues for v4.1.0 (due 2026-06-21) including #1286 (non-root restore SecurityException on Samsung/A14 — "package com.google.android.packageinstaller does not belong to 10053"); NG's restore path predates these fixes.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1286 ; upstream commits since 3d11bcb (RESEARCH.md §Competitive)
  Touches: backup/RestoreOp.java, apk/installer/, compat/PackageManagerCompat.java
  Acceptance: the #1286 reproduction (non-root restore with a session-based installer on API 34) succeeds; ported commits listed in CHANGELOG with upstream attribution.
  Complexity: M

- [ ] P1 — Pause target app during backup (suspend/SIGSTOP) for data consistency
  Why: Live app writes during backup produce silently inconsistent archives; Neo Backup pauses via `pm suspend`/`kill -STOP` and resumes after — a small change that removes a whole class of corrupt backups.
  Evidence: Neo Backup README/FAQ (RESEARCH.md §Competitive); backup/BackupOp.java has no suspend/STOP today (verified)
  Touches: backup/BackupOp.java, compat/PackageManagerCompat.java (suspend), runner/ (root SIGSTOP fallback)
  Acceptance: with root or privileged mode, the target app is suspended for the duration of data backup and resumed after (also on failure paths); behavior is a user-visible toggle defaulting on for privileged modes.
  Complexity: S

- [ ] P1 — Port HMAC mutual auth + native run_server for the local privileged channel
  Why: Upstream hardened the app↔ADB-server channel with HMAC challenge-response and converted run_server to a native executable (fixing root mode broken since 3.0.0, #948, and reducing detectable service footprint); NG's channel lacks both.
  Evidence: upstream commits 88eb453, 07c7199, b42efbb, f8d3126 (RESEARCH.md §Competitive); grep: no HMAC in adb/ or libserver/ (verified)
  Touches: libserver/, server/, adb/, servermanager/
  Acceptance: server rejects unauthenticated connections (negative test); root mode works on a rooted A16 emulator; ported commits attributed.
  Complexity: M

- [ ] P1 — Debloat safety net: critical-package guard + pre-op snapshot + ADB rescue script
  Why: Bricking fear is the #1 adoption blocker for debloaters (documented OneUI bootloop from a debloat list; F-Droid forum recovery thread); NG's critical-package guard exists only in Permission Inspector recovery, not in Debloater/batch uninstall; Canta wins recommendations on undo alone.
  Evidence: https://gitlab.com/W1nst0n/universal-android-debloater/-/issues/43 ; forum.f-droid.org/t/29341 ; upstream #1161; permissions/PermissionRecovery.java (existing guard)
  Touches: debloat/, batchops/, oneclickops/, permissions/PermissionRecovery.java (extract shared guard), snapshot/SnapshotBundle.java (pre-op auto-export)
  Acceptance: batch uninstall/disable of a guarded package requires an explicit second confirmation naming the risk; every batch system-app operation auto-exports a snapshot + generates a plain-text `adb shell cmd package install-existing ...` rescue script in the backup volume.
  Complexity: M

- [ ] P1 — Port upstream main-list performance/correctness batch
  Why: Upstream fixed the "app list loads forever" class (#1982 remains its last v4.1.0 blocker) with a search debouncer, ListAdapter migration, scroll-position restore, IME fixes, and filter-highlight fixes — all post-pin and directly applicable to NG's main list.
  Evidence: upstream commits bba53eb, 8cf2c1e, 69b28cb, 5418038, 886ad90, ab2b17f (RESEARCH.md §Competitive)
  Touches: main/ (MainActivity, adapters, view models)
  Acceptance: typing in main-list search does not re-filter per keystroke (debounced); rotation/return preserves scroll position; ported commits attributed.
  Complexity: M

### P2

- [ ] P2 — Malformed-APK parser robustness pass
  Why: The Konfety malware wave deliberately ships APKs (bogus encryption flag, declared-unsupported compression, malformed string pools) that crash OSS parsers; NG users feed it exactly such hostile APKs via installer/scanner/manifest viewer.
  Evidence: Zimperium Konfety report (RESEARCH.md Sources); apk/, ARSCLib usage
  Touches: apk/ (ApkFile, splitapk), scanner/, details/ManifestViewer paths; app/src/test/ corpus
  Acceptance: a regression corpus of malformed APKs (Konfety-style tricks) parses to a graceful per-file error — no crash, no hang — enforced by unit tests.
  Complexity: M

- [ ] P2 — Pithus integration decision: verify service, then remove or keep
  Why: Upstream deleted its Pithus scanner + pinned certificates on 2026-05-26; if the service is dead, NG's full flavor ships a dead online feature with stale cert pins (scanner/Pithus.java, network_security_config.xml).
  Evidence: upstream commits 0e187e8 + 2c00f69; scanner/Pithus.java (verified present)
  Touches: scanner/Pithus.java, scanner/ScannerViewModel.java, scanner/ScannerFragment.java, res/xml/network_security_config.xml, settings/PrivacyPreferences.java
  Acceptance: dated audit doc records the service status check; integration removed (with cert pins) or kept with a recorded working-endpoint verdict.
  Complexity: S

- [ ] P2 — Restore the missing minSdk-21 ceiling ledger (decision itself is already made)
  Why: The minSdk-23 decision EXISTS on disk (docs/policy/2026-05-26-minsdk-23-decision.md: hold 21 through v0.6.x, four forced-decision triggers) — but the dependency ledger it depends on, docs/policy/minsdk-21-ceiling.md, is absent while being linked from versions.gradle:39, the decision memo, and docs/architecture/README.md; without it the trigger watch has no bookkeeping.
  Evidence: docs/policy/2026-05-26-minsdk-23-decision.md (verified on disk, 2026-06-10); https://github.com/material-components/material-components-android/releases/tag/1.14.0 (minSdk 23 confirmed); versions.gradle:39
  Touches: docs/policy/minsdk-21-ceiling.md (recreate the ledger: material/activity/biometric/room/webkit/sora-editor pinned-cluster table + trigger status), versions.gradle (ledger comments)
  Acceptance: the ledger file exists with the current pinned-cluster table and a dated trigger-status section; all three inbound references resolve.
  Complexity: S

- [ ] P2 — App Change Auditor: component/tracker diffs + unified change feed
  Why: Change-over-time auditing is the ecosystem's 2025-26 innovation wave (Permission Pilot watcher, LibChecker snapshot diffs); NG already ships permission + signing-cert monitors (T9) — adding component/tracker diffing and one browsable feed makes NG first in its niche to unify install/update auditing.
  Evidence: permission/monitor/PermissionChangeMonitor.java (T9, verified); LibChecker snapshots, permission-pilot README (RESEARCH.md §Competitive)
  Touches: permission/monitor/ (new ComponentChangeMonitor/TrackerChangeMonitor + feed store), scanner/ (tracker sigs), main/ or settings/ (feed UI entry)
  Acceptance: updating a fixture app that adds a tracker + exported component produces a feed entry and notification listing both diffs; feed persists and is reachable from the main menu.
  Complexity: M

- [ ] P2 — Permission/app-op reference states (desired-vs-actual drift)
  Why: PermissionManagerX's reference-state model (pin desired value per permission/app-op, surface drift, restore references) is the only audit-grade permission pattern in the ecosystem and slots into NG's existing rule store + Permission Inspector.
  Evidence: https://github.com/mirfatif/PermissionManagerX (README, verified)
  Touches: rules/RulesStorageManager.java, rules/struct/, permissions/ (Inspector drift badges), details/AppDetailsPermissionsFragment
  Acceptance: user pins a reference for a permission/app-op; subsequent drift shows a visible indicator in Permission Inspector with one-tap restore-to-reference; references survive app reinstall via the rule store.
  Complexity: L

- [ ] P2 — Dhizuku freeze/suspend executor parity
  Why: Upstream permanently rejected Shizuku (issue #55, closed not_planned 2026-06-02) — rootless power is NG's structural lane; Hail proves device-owner delegation (Dhizuku) can freeze/suspend without root, and NG's DhizukuBridge currently feeds only the installer cascade + mode doctor.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/55 ; Hail README capability matrix; dhizuku/DhizukuBridge.java, apk/installer/InstallerPrivilegeCascade.java (verified)
  Touches: dhizuku/DhizukuBridge.java, batchops/, compat/ (freeze/suspend paths), settings/Ops.java
  Acceptance: with Dhizuku active and no root/Shizuku, freeze/unfreeze and suspend succeed from app details and batch ops; capability matrix in onboarding reflects it.
  Complexity: M

- [ ] P2 — Wireless-ADB resilience: trusted-network auto-reconnect + pairing-state surface
  Why: "ADB mode silently lost" is upstream's pinned unsolved bug (#1596, Samsung kills the server); Shizuku 13.6.0 already ships trusted-WLAN auto-restart and Android won't ship native auto-reconnect before QPR3/A17 (2027) — NG can close the gap now and own the most reliable on-device ADB mode.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1596 ; Shizuku 13.6.0 release notes; androidauthority wireless-adb-auto-reconnect (RESEARCH.md Sources); adb/ has no trusted-network logic (verified)
  Touches: adb/ (AdbPairingService, connection manager), servermanager/, settings/PrivilegeHealthPreferences.java, onboarding/
  Acceptance: on reconnecting to a user-designated trusted Wi-Fi, NG re-establishes its ADB connection unattended (Android 11+); pairing/connection state (paired, expired, server killed) is visible in Mode Doctor with recovery steps. Cross-check item "Android 17 audit batch" for ACCESS_LOCAL_NETWORK before targeting API 37.
  Complexity: L

- [ ] P2 — Fork-owned translation pipeline (Weblate) + NG-string catch-up
  Why: 44 inherited locales sit at 30-40% coverage and every NG-added string (Permission Inspector, onboarding, changelog viewer) is English-only; README says "Weblate (link TBD)" — the fork has no translation intake at all.
  Evidence: app/src/main/res/values-*/ counts (RESEARCH.md §Architecture); README.md:183
  Touches: .github/ (Weblate config/webhook), app/src/main/res/values-*/, CONTRIBUTING.md translation section
  Acceptance: a hosted Weblate (or equivalent) project is live and linked from README; at least the top-5 inherited locales receive NG-string components; CI accepts translation commits without manual XML fixes.
  Complexity: M

- [ ] P2 — SAF DocumentsProvider exposure of app-private directories (privileged)
  Why: Upstream's #516 (7 reactions) asks for third-party access to Android/data and app-private dirs via a documents provider when AM holds privilege; NG already ships AppManagerDocumentsProvider — extending it leapfrogs upstream's open request.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/516 ; fm/AppManagerDocumentsProvider (verified in manifest)
  Touches: fm/ (documents provider), ipc/ (privileged file streams), settings/ (opt-in toggle, default off)
  Acceptance: with the toggle on and privilege available, a third-party SAF file manager can browse/copy a test app's /data/data dir through NG's provider; toggle off = provider hides those roots.
  Complexity: M

- [ ] P2 — Per-app crash feed (system-wide crash monitor)
  Why: Upstream's accepted-but-unbuilt #163 (crash monitor with widget + viewer) and Inure's per-app battery/boot panels show demand for "what's wrong with this app" surfaces; NG already parses logcat and tracks ApplicationExitInfo-adjacent data in usage/.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/163 ; logcat/ package (verified)
  Touches: logcat/ (crash extraction), details/ (per-app crash tab/card), usage/ (exit info)
  Acceptance: app details shows recent crashes (timestamp + stack head) for the selected app sourced from ApplicationExitInfo (API 30+) and privileged logcat where available; empty state explains required privilege.
  Complexity: M

### P3

- [ ] P3 — APK Signature Scheme v3.2 (hybrid PQC) display/verify readiness
  Why: Android 17 introduces v3.2 hybrid post-quantum signing (classical + ML-DSA); NG's signature panels and apksig 4.4.0 line must at minimum not mislabel v3.2-signed APKs.
  Evidence: https://developer.android.com/about/versions/17/release-notes ; versions.gradle:21 (apksig 4.4.0)
  Touches: versions.gradle (apksig bump when available), details/ signature display, apk/signing/
  Acceptance: a v3.2-signed sample APK shows the correct scheme list (not "unknown"/crash); audit doc records the verdict if apksig upstream lags.
  Complexity: S

- [ ] P3 — AppVerifier-format share integration
  Why: NG's README already tells users to verify releases with AppVerifier; an in-app "share verification info" (package + SHA-256 cert hash in AppVerifier's expected text format) closes the loop for any installed app at S cost.
  Evidence: https://github.com/soupslurpr/AppVerifier (README format, verified); README.md:131
  Touches: details/info/AppInfoFragment.java (share action), utils/ (formatter)
  Acceptance: share sheet from an app's signature card produces text AppVerifier parses directly (package name + colon-separated SHA-256).
  Complexity: S

- [ ] P3 — APK export device-specificity labeling
  Why: Power users archive APKs ahead of verification enforcement and are burned by device-trimmed splits rendering wrong elsewhere — an XDA PSA exists solely to explain AM's extract vs Aurora's export; labeling exports (and warning on share) is cheap clarity.
  Evidence: XDA PSA thread 4784234 (RESEARCH.md Sources); apk/splitapk/SplitApkExporter.java
  Touches: apk/splitapk/SplitApkExporter.java, details/ share/export dialogs (string resources)
  Acceptance: export/share dialogs state which splits are included and that the set is device-specific; exported .apks filename or manifest notes the source device ABI/DPI.
  Complexity: S

- [ ] P3 — Per-app notes
  Why: Top-tier competitors (Inure, AppDash) and upstream request #1269 (tags + notes per app) treat notes as table stakes; NG shipped the tags data layer — notes is the missing sibling and feeds the planned tag UI.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1269 ; Inure feature list; tags/AppTagStore.java (pattern to clone)
  Touches: tags/ (new AppNoteStore, same SharedPrefs-JSON pattern), details/info/ (note card), snapshot/SnapshotBundle.java (include notes)
  Acceptance: a note set on an app persists across restarts, appears in app details, round-trips through snapshot bundles, and is searchable from the main list.
  Complexity: S

- [ ] P3 — Backup protect-flag and per-backup notes
  Why: Swift Backup v5's deletion-locked backups + notes are the cheap half of its premium tier; NG's retention pruning (BackupRetentionPolicy) needs a protect-flag anyway so rotation never deletes a backup the user marked keep-forever.
  Evidence: Swift Backup feature/FAQ pages (RESEARCH.md §Competitive); backup/BackupRetentionPolicy.java (verified — prunes without protect concept)
  Touches: backup/ (metadata field), backup/BackupRetentionPolicy.java (skip protected), backup/dialog/ (UI flag + note)
  Acceptance: a protected backup survives retention pruning and bulk delete prompts; notes display in the backup list.
  Complexity: S

- [ ] P3 — IzzyOnDroid submission readiness audit
  Why: IzzyOnDroid is the natural first repo for an Obtainium-era app (release-key signing and reproducible builds already in place) but enforces ~30 MB per-app reservation and zero-tracker scans — size per ABI split is unmeasured.
  Evidence: https://izzyondroid.org/docs/general/AppInclusionPolicy/ ; release.yml ABI splits
  Touches: docs/distribution/ (submission notes), possibly app/build.gradle (resource shrinking if over budget)
  Acceptance: dated audit doc records per-ABI release APK sizes and policy-compliance checklist; submission filed or blockers listed.
  Complexity: S

- [ ] P3 — File-manager trash bin (staged deletion)
  Why: NG's FM hard-deletes; Files-by-Google's staged trash with 30-day retention is the established data-safety pattern and FM batch ops magnify mistake cost.
  Evidence: Files by Google clean-flow walkthrough (RESEARCH.md Sources); fm/ has no trash concept (verified)
  Touches: fm/ (delete paths, trash root, restore UI), settings/ (retention pref)
  Acceptance: FM delete moves to a trash location with restore; trash auto-empties after the configured retention; "delete permanently" remains available.
  Complexity: M

- [ ] P3 — D-pad/TV navigation pass + Android TV banner
  Why: Upstream #107 (keyboard/remote navigation, "Partly Fixed") plus SD Maid SE's Android TV launcher support show the box-tinkerer segment is real (FireOS/Firestick issues already appear upstream: #1835, #1854); NG's M3 dashboard was not audited for focus traversal.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/107 ; SD Maid SE releases (TV support); upstream #1835/#1854 (FireOS users)
  Touches: app/src/main/res/ (focus order, leanback banner, manifest LEANBACK feature flags), main/, details/
  Acceptance: main list → app details → batch ops are fully operable with a D-pad on an Android TV emulator; app appears in the TV launcher with a banner.
  Complexity: M

## Research-Driven Additions (Pass 2 — 2026-06-10)

### P1

- [ ] P1 — Port upstream post-pin crash/correctness batch (2026-05-26 → 06-02)
  Why: Eight uncatalogued upstream fixes land cleanly on a 3d11bcb-pinned tree and close silent-corruption/crash classes: APKS compile regression, profile custom-expression filters matching wrong app sets, am-start link resolution, two NPEs, Debloater missing uninstalled system apps, broken Finder/Debloater nav, editor symbol cropping at large font scale.
  Evidence: upstream commits 706c36fb, daa54ac0 (closes #1718), 4a25c3f0, 3bf97856, 184df334, 329b8dc1, 4d3da96b, 0d1be565 — https://github.com/MuntashirAkon/AppManager/commits/master
  Touches: apk/ (APKS compile), filters/ + profiles/ (custom expressions), intercept/, debloat/, finder/, editor/
  Acceptance: each ported commit attributed in CHANGELOG; profile-with-custom-expression filter test added; APKS merge of a fixture split-bundle round-trips; v4.1.0 tag (due ~2026-06-21) re-diffed after release for stragglers.
  Complexity: M

- [ ] P1 — Root-detection retune for 2026 root managers (upstream #1967 + Magisk 30.7 caps change)
  Why: Upstream's accepted P1 "root not detected on Android 16" (#1967) hits the same probe stack NG owns (runner/RootManagerInfo); separately Magisk v30.7 now preserves capabilities by default, inverting the assumption behind NG's shipped KernelSU/Magisk drop-cap diagnostics, and KernelSU-Next 3.1.0 moved paths again.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1967 ; https://github.com/topjohnwu/Magisk/releases (v30.7 caps default); runner/RootManagerInfo (verified, probes /data/adb/{magisk,ksu,ap})
  Touches: runner/RootManagerInfo.java, the drop-cap diagnostic surfaces, settings/Ops.java (root mode init), docs/audits/ (dated probe-matrix audit)
  Acceptance: root detected on an A16 emulator rooted with current Magisk and with KSU-Next; drop-cap diagnostics show correct guidance for Magisk ≥30.7 (caps preserved by default); probe matrix documented.
  Complexity: M

### P2

- [ ] P2 — Backup overwrite option (close the 2020 TODO)
  Why: Users must delete an existing backup before re-backing-up to the same slot; the TODO has been open since 2020-09-18 and the delete-first dance multiplies data-loss windows (no backup exists between delete and new backup).
  Evidence: backup/dialog/ BackupFragment "TODO: Add overwrite option" (verified in tree); RESEARCH.md §Security
  Touches: backup/dialog/ (option UI), backup/BackupManager.java (atomic replace: write-new-then-swap, never delete-first)
  Acceptance: overwrite is offered when a same-name backup exists and is atomic — an interrupted overwrite leaves the previous backup intact (unit test with injected failure).
  Complexity: M

- [ ] P2 — Wipe cryptographic material in SessionMonitoringService
  Why: The service holds key material with a "TODO: Wipe memory?" (line 138) — char[]/byte[] secrets should be zeroed after use per the project's own keystore handling precedent (v0.3.0 zeroed keystore char[]s).
  Evidence: session/SessionMonitoringService.java:138 (verified); RESEARCH.md §Security
  Touches: session/SessionMonitoringService.java, crypto/ helpers if a shared zeroing util is extracted
  Acceptance: secrets are zeroed on session close/destroy paths (including error paths); no plaintext key material outlives its use scope.
  Complexity: S

- [ ] P2 — 2026-06-09 deferred-audit reliability batch
  Why: The deep audit deferred eight verified-real, low-individual-cost reliability bugs that were lost when the old ROADMAP section was replaced; re-itemizing them prevents silent drop: AppUsageViewModel live-list CME; stale-position notifyItemChanged in AppDetails fragments; ApkWhatsNewFinder singleton shared temp-set; SAF pending-write fields lost on process death + profile-export main-thread IO; OneClickOps onPause busy-clear without scan-cancel; retention pruners leaving stale Room rows with no broadcast; commit() skipping the previous-backup freeze flag; verify-backups surfacing raw getMessage().
  Evidence: 2026-06-09 audit session record (commits 4f46a0e9..079e96f1 shipped the non-deferred half); usage/AppUsageViewModel.java + apk/whatsnew/ApkWhatsNewFinder.java verified present
  Touches: usage/, details/ fragments, apk/whatsnew/, profiles/ (export IO), oneclickops/, backup/ (pruners, commit, verify), db/
  Acceptance: each sub-item fixed with a regression test where JVM-testable; batch may ship across multiple commits; none re-deferred without a dated decision note.
  Complexity: M

- [ ] P2 — sora-editor bump 0.22.2 → 0.24.6 (last minSdk-21 release — time-boxed)
  Why: The pinned fork build 0.22.2 misses upstream 0.24.4–0.24.6 fixes for IME composing-text corruption, completion-list scroll ANR, IndexOutOfBounds on completion, and emoji deletion; 0.24.6 (2026-06-10) is the final release supporting minSdk 21 (verified in release notes), and the minSdk decision is already settled at "hold 21" (docs/policy/2026-05-26-minsdk-23-decision.md) — so 0.24.6 is the terminal version NG can take; bump now or carry the bugs indefinitely.
  Evidence: https://github.com/Rosemoe/sora-editor/releases (0.24.4/0.24.5/0.24.6 notes); versions.gradle:45 (fork pin, verified)
  Touches: versions.gradle, editor/ (API drift), possibly the MuntashirAkon/sora-editor fork (rebase) or a switch to upstream artifacts
  Acceptance: editor opens/edits/saves java+xml+smali fixtures with completion and wordwrap working; the IME composing regression (type-with-gboard scenario) verified on device or emulator; pin decision recorded in the dependency ledger.
  Complexity: M

- [ ] P2 — ApplicationStartInfo "why did this app start" panel (API 35+)
  Why: ActivityManager.getHistoricalProcessStartReasons() exposes per-start reason (alarm/broadcast/push/job/launcher), start type, and create→first-frame timings — a forensic per-app surface that fits NG's inspection identity and that no manager in the niche ships; NG has zero usage of the API today (verified).
  Evidence: https://developer.android.com/reference/android/app/ApplicationStartInfo ; grep: no ApplicationStartInfo in tree (verified)
  Touches: details/info/ (new card or tab), usage/ (data layer), compat/ActivityManagerCompat.java
  Acceptance: on API 35+, app details shows recent starts with reason + latency; below API 35 the card is absent (not an error); zero-start apps show an empty state.
  Complexity: M

- [ ] P2 — Assistant-launched privileged services/broadcasts without root (upstream #1973)
  Why: Accepted-but-unbuilt upstream feature extending the proven secure-settings assistant trick (already used for non-exported activities) to services and broadcasts in no-root/WRITE_SECURE_SETTINGS mode — a genuine fork-first capability in NG's "rootless power" lane.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1973 (accepted, P3, no implementation)
  Touches: details/ (component launch actions), the assistant-launch helper used for activities, settings/Ops.java (mode gating)
  Acceptance: in WRITE_SECURE_SETTINGS mode a non-exported service can be started and a broadcast sent from the component list, with the same confirmation UX as the existing activity path; cleanly refused (with reason) where the mechanism is unavailable.
  Complexity: M

- [ ] P2 — Advanced Protection + Developer Verifier state surfacing (companion to the existing P0 installer item)
  Why: Android 16+ Advanced Protection (AdvancedProtectionManager) blocks sideloading outright and the verification "advanced flow" for power users goes global Aug 2026 (developer mode + one-day wait + biometric) — NG should detect both states and explain them before an install fails, and its sideload-verification doc predates both.
  Evidence: https://developer.android.com/about/versions/17/features (AdvancedProtectionManager); https://android.gadgethacks.com/news/google-keeps-android-sideloading-for-power-users-in-2026/ (advanced flow, Aug 2026); docs/sideload-verification.md (predates)
  Touches: apk/installer/ (pre-flight check + explainer), settings/ or onboarding Mode Doctor (state row), docs/sideload-verification.md
  Acceptance: on a device with Advanced Protection active, NG shows the state and the documented consequence before attempting an install; the in-app FAQ describes the advanced flow and Limited Distribution Accounts accurately.
  Complexity: M

### P3

- [ ] P3 — Device-wide analytics dashboard (install-source / SDK / signing distributions)
  Why: Inure's analytics panel and AppDash's insight cards ("unused apps", "storage-heavy") are the category's stickiest discovery surfaces; NG already computes every datapoint (installer source, target SDK, signing info, usage) but offers no aggregate view with tap-through to a filtered list.
  Evidence: https://github.com/Hamza417/Inure (FEATURES.md analytics panel); https://appdash.app/ (insight cards); NG filters already support these predicates (filters/options/)
  Touches: main/ or a new dashboard fragment, filters/ (reuse predicates as tap-through), existing chart utilities
  Acceptance: a dashboard screen shows at least installer-source, targetSdk, and signing distributions plus an "unused 30/60/90 days" card; tapping any segment opens the main list pre-filtered to it.
  Complexity: M

- [ ] P3 — Version-watch panel (full flavor): installed vs latest from static indexes
  Why: APKUpdater (3.8k★, active) proves demand for multi-source update awareness without being a store; AppDash paywalls it; checking F-Droid/IzzyOnDroid index-v2 + GitHub releases against installed versions fits the full flavor's opt-in network doctrine and NG stays a manager (notify, don't install).
  Evidence: https://github.com/rumboalla/apkupdater ; https://appdash.app/ ; f-droid index-v2 format (RESEARCH.md Sources)
  Touches: full-flavor source set (new updates/ package), settings/PrivacyPreferences (opt-in + source toggles), WorkManager scheduled check
  Acceptance: with the toggle on, a scheduled check lists apps whose installed version trails the chosen indexes, with a signing-cert mismatch warning where the index cert differs; floss flavor compiles the feature out entirely.
  Complexity: L

- [ ] P3 — Boot-component manager view
  Why: A dedicated "what starts at boot" surface (BOOT_COMPLETED receivers across all apps, batch-blockable) is a classic MyAndroidTools/Inure feature NG can build almost entirely from existing component-blocking plumbing; today only NG's own BootReceiver references BOOT_COMPLETED (verified).
  Evidence: Inure boot manager (FEATURES.md); https://github.com/lihenggui/blocker (MyAndroidTools rule import demand); grep: no cross-app boot view in tree (verified)
  Touches: new view under main menu (reuse component list UI), rules/compontents/ (existing IFW/disable paths), filters/
  Acceptance: a screen lists every app with BOOT_COMPLETED/LOCKED_BOOT_COMPLETED receivers and their enable state; per-row and batch block/unblock work through the existing rule store with undo.
  Complexity: M

- [ ] P3 — Per-app standby-bucket inspect/set (privileged)
  Why: Brevent built a product on `am set-standby-bucket` and Inure ships a battery panel; NG has the privilege plumbing but no standby-bucket surface (verified no usage) — a narrow inspect/set row in app details is the policy-not-boost framing that fits NG.
  Evidence: https://github.com/brevent/Brevent ; Inure battery panel; grep: no setAppStandbyBucket in tree (verified). Cross-check the v0.5.x "background-run rule persistence" scope before building (RESEARCH.md Open Questions).
  Touches: compat/UsageStatsManagerCompat.java (get/setAppStandbyBucket), details/info/ (row), batchops/ (optional batch set)
  Acceptance: app details shows the current bucket on API 28+; in root/ADB/Shizuku modes the user can pin a bucket (active/working set/frequent/rare/restricted) and the change persists across the details screen reload.
  Complexity: M

- [ ] P3 — Tracker report rollup: company → category → jurisdiction
  Why: TrackerControl's grouped presentation (parent company, ads/analytics/social category, HQ country) with plain-language blurbs is meaningfully more legible than flat library lists and is pure offline metadata; NG's TrackerInfoDialog already resolves tracker identity (verified) — the rollup is presentation work.
  Evidence: https://trackercontrol.org/ ; scanner/TrackerInfoDialog.java + rules/compontents/TrackerCategory.java (verified)
  Touches: scanner/ (report screen grouping), tracker metadata (extend the bundled dataset with company/category/country columns), strings (blurbs)
  Acceptance: the per-app tracker report groups findings by parent company with category chips and a one-line "what this category means"; flat list remains available as a toggle; works fully offline.
  Complexity: M

- [ ] P3 — AppFunctions exposure datapoint (API 36+)
  Why: Apps exposing agent-callable functions (android.app.appfunctions) are a new privacy-relevant surface; "this app exposes N agent functions" is a cheap, fork-first inspection datapoint consistent with NG's exported-component reporting.
  Evidence: https://developer.android.com/ai/appfunctions ; no AppFunctions reference in tree (Assumption: API surface stable at 36)
  Touches: details/info/ tag cloud or components list (new chip/section), compat/ (AppFunctionManager wrapper)
  Acceptance: on API 36+, apps declaring AppFunctions show a chip with the function count; tapping lists the declared functions; absent below API 36.
  Complexity: S

- [ ] P3 — Theme/a11y coherence pass (deferred-audit visual debt)
  Why: The 2026-06-09 audit verified divergent dark palettes across NG-added screens, dead premium design tokens, favorite_icon drawable misused as a debuggable indicator, fm_icon_background inconsistencies, and tracker/perm badges under the 48dp touch-target minimum — small fixes that compound into perceived quality.
  Evidence: 2026-06-09 audit session record (deferred list); res/ themes and the named drawables (spot-verified)
  Touches: app/src/main/res/ (themes, drawables, dimens), details/ badge layouts
  Acceptance: NG-added screens share one dark palette token set; the misused drawables are replaced with purpose-named assets; all interactive badges hit ≥48dp touch targets (a11y scanner clean on those screens).
  Complexity: M

- [ ] P3 — Terminal: implement or formally defer (decision item)
  Why: terminal/TermActivity has shipped as a 4-TODO mock since the fork (completion, history, init script absent); a stub feature in the menu erodes trust — either wire a real terminal (Termux-style PTY) or hide it behind Pro Mode with a "preview" label and a dated decision record.
  Evidence: terminal/TermActivity.java:49,95,104,181 (verified TODOs)
  Touches: terminal/, main menu registration, docs/ (decision record)
  Acceptance: a dated decision doc exists; the menu either offers a working PTY terminal (run `id` in privileged mode, see output) or labels the entry "preview" with the mock's limits stated in-UI.
  Complexity: S (defer) / XL (implement)
