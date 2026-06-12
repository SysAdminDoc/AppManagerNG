<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Active Build Initiative — Quality & Capability Push (2026-06-11)

Sequenced from the post-audit improvement review. Items already specced in detail
elsewhere in this file are cross-referenced rather than duplicated. "Building now"
items are verifiable headless (compile / resource merge / JVM tests) and are being
implemented this initiative; "device-gated" items carry full specs but are NOT
patched blind — they touch the privileged bootstrap, need an emulator/rooted
device, or need on-device visual verification.

### Building now (verifiable headless)

- [ ] INIT-1 — Main-list perceived performance: search debounce + scroll-position restore
  Why: the main list is the highest-friction screen; per-keystroke re-filtering and lost
  scroll position on rotation/return are the things users feel. (Subset of the upstream
  "main-list performance/correctness batch" that does not require the full ListAdapter swap.)
  Touches: main/MainActivity (search handler), main/MainViewModel (debounced filter),
  main/MainRecyclerAdapter / RecyclerView state save-restore
  Acceptance: typing debounces filtering (≈300 ms); rotation/return restores scroll
  position; a JVM test covers the debounce/coalescing helper. Visual confirmation of
  scroll restore is device-gated and noted as such.
  Complexity: M

- [x] INIT-2a — Lock in the backup commit data-safety property (DONE 2026-06-11)
  Finding: BackupItems.commit() already writes the new backup to a temp dir and swaps it
  into place BEFORE deleting previous backups (new-before-old), so a crash mid-commit
  leaves the previous backup intact — the commit path is already crash-safe for the common
  UUID-named flow. Added BackupItemsTest.commitSwapsNewPayloadIntoPlaceBeforeRemovingPreviousBackup
  to guard this property against regression.

- [ ] INIT-2b — Backup overwrite-option UI + move-aside for custom-name collisions (device-gated)
  Why: the net-new in the 2020 overwrite TODO is a UX feature — offer "overwrite" when a
  same-NAME backup exists instead of forcing a manual pre-delete (which opens a no-backup
  window). For custom-name collisions where mBackupPath can be pre-populated, harden
  commit() to move-the-existing-aside → swap → delete-aside (rollback on failure) so even
  that window closes. NOT patched blind: moveTo/rename-aside rollback semantics differ
  between file-backed and SAF-backed Path implementations and the JVM harness only exercises
  the file backend — a wrong rollback could corrupt backups on SAF storage.
  Touches: backup/dialog/ (overwrite option), backup/BackupItems.java (move-aside commit)
  Acceptance: overwrite offered on same-name collision; an injected mid-swap failure leaves
  the previous backup readable, verified on both file and SAF (Android/data) storage on a
  device.
  Complexity: M

- [ ] INIT-3 — Regression test safety net for the 2026-06-11 audit fixes
  Why: the lifecycle/data fixes have nothing stopping them regressing. Cover the
  JVM-testable data paths: ProfileApplierResult failure aggregation, BatchOpsManager
  grant/revoke single-package-per-failure, SplitInputStream 0xFF masking, and the
  AutoBackupWorker concurrency guard contract.
  Touches: app/src/test/ (new focused unit tests)
  Acceptance: tests fail against the pre-fix behaviour and pass against current; run in
  the existing :app:testFlossDebugUnitTest suite.
  Complexity: S

- [x] INIT-4a — Device-wide analytics aggregation data layer (DONE 2026-06-11)
  Shipped `analytics/DeviceAnalyticsAggregator` — a pure, Android-free aggregation turning a
  flat list of per-app datapoints (installer label, target SDK, installed, last-used millis)
  into installer-source / target-SDK distributions and cumulative "unused in 30/60/90 days"
  counts, with apps lacking usage data tracked separately as "unknown". `nowMillis` is
  injected for deterministic buckets. Covered by `DeviceAnalyticsAggregatorTest` (6 cases:
  ordering, ascending SDK, exact day boundaries, unknown-usage, uninstalled totals, empty).
  Follows the project's "ship the tested data layer, wire UI next" pattern.

- [ ] INIT-4b — Analytics / discovery dashboard screen (device-gated UI wiring)
  Why: render the INIT-4a summary as the discovery surface (Inure/AppDash-style) with
  tap-through to a pre-filtered main list — the highest-value "feels premium" feature.
  Touches: new dashboard fragment + menu entry under main/, build AppDatapoint list from the
  loaded ApplicationItem set (installer via getInstallerInfo, targetSdk, lastUsageTime),
  reuse filters/options/ predicates for segment tap-through, existing chart utilities
  Acceptance: a screen shows installer-source / targetSDK distributions + an "unused
  30/60/90 days" card; tapping a segment opens the main list pre-filtered to it; verified on
  a device in light/dark/AMOLED. (Visual + tap-through need on-device verification.)
  Complexity: M

- [ ] INIT-5 — i18n intake (maintainer-gated: external Weblate instance is the blocker)
  Why: 44 inherited locales at 30-40%, NG strings English-only, README:187 promises
  "Weblate (link TBD)". Re-scoped after inspection: the committable repo-side artifacts are
  thin and hollow without the actual hosted service — a `.weblate` component config pointing
  at a non-existent project would be fake polish, and CONTRIBUTING.md is gitignored here
  (`*.md` with only README/RESEARCH/ROADMAP excepted), so a tracked contributor translation
  doc would need a `.gitignore` exception first. The real blocker is standing up the hosted
  Weblate (or Crowdin) project — a maintainer/account action, not code.
  Touches (once the instance exists): `.gitignore` (un-ignore CONTRIBUTING.md), CONTRIBUTING.md
  (translation section), README:187 (replace "link TBD"), optional `.weblate` + sync workflow
  Acceptance: a hosted translation project is live and linked from README; top-5 inherited
  locales get the NG-string components; CI accepts translation commits. Until then this stays
  maintainer-gated rather than shipping placeholder config.
  Complexity: S (once unblocked)

### Device-gated (specced, not patched blind — see detailed entries below)

- [ ] INIT-D1 — Full main-list ListAdapter / DiffUtil migration (supersedes the manual
  adapter plumbing that generated many lifecycle bugs). View-ID preservation needs a
  device. See "Port upstream main-list performance/correctness batch".
- [ ] INIT-D2 — ADB-mode privileged backend off external storage + copyFile digest (LPE).
  See "Deep Audit Follow-ups (2026-06-11) → P1".
- [ ] INIT-D3 — HMAC mutual auth + native run_server port. See "Port HMAC mutual auth …".
- [ ] INIT-D4 — Suspend target app during backup (SIGSTOP). Needs privilege to verify.
  See "Pause target app during backup".
- [ ] INIT-D5 — Backup round-trip emulator CI. Needs the emulator runner. See "Backup/
  restore round-trip integration tests in emulator CI".

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

- [ ] P2 — 2026-06-09 deferred-audit reliability batch
  Why: The deep audit deferred verified-real, low-individual-cost reliability bugs that were lost when the old ROADMAP section was replaced; re-itemizing them prevents silent drop: SAF pending-write fields lost on process death + profile-export main-thread IO.
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
  Why: The 2026-06-09 audit verified divergent dark palettes across NG-added screens, dead premium design tokens, and tracker/perm badges under the 48dp touch-target minimum — small fixes that compound into perceived quality.
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

## Deep Audit Follow-ups (2026-06-11)

Deferred from the 2026-06-11 deep engineering/QA/UX audit pass. The fixed half
of that pass is in the commit history / CHANGELOG `Unreleased`. Items below were
verified real but are device-gated, design-verification-gated, or carry enough
regression risk to need their own change.

### P1

- [ ] P1 — ADB-mode privileged backend runs from world-accessible external storage (LPE)
  Why: The DE-private-storage hardening for the privileged server was applied only to the root path. ADB mode still launches `run_server.sh` + `am.jar` from `getExternalCachePath()` via the `getServerRunnerCommand(1) + " || " + getServerRunnerCommand(0)` fallback in `ServerConfig.getServerRunnerAdbCommand` — and because the ADB shell uid (2000) cannot read the DE-private index-1 copy, the index-0 external-storage branch is effectively always taken. On pre-scoped-storage devices (or via all-files access) another app can overwrite those artifacts and gain code execution in AM's ADB-privileged backend. Compounded by `AssetsUtils.copyFile` only refreshing on length mismatch (no digest, `force=BuildConfig.DEBUG`), so a same-length planted file is never overwritten.
  Evidence: servermanager/ServerConfig.java:54-71,91-99; servermanager/LocalServerManager.java:230-236; utils/AssetsUtils.java:36-43
  Touches: servermanager/ServerConfig.java, servermanager/LocalServerManager.java, utils/AssetsUtils.java
  Acceptance: the ADB-privileged backend is staged to `/data/local/tmp` (chown 2000:2000, chmod 700) or another non-world-writable path instead of `/sdcard`; the external index-0 copies are no longer written; `copyFile` verifies content (digest) and always refreshes the privileged artifacts on init. Verify root + ADB modes still start on a rooted A16 emulator. (Device-gated: touches the privileged bootstrap, cannot be validated headless.)
  Complexity: M

### P2

- [ ] P2 — RootService main.jar staged via external storage before privileged copy (TOCTOU)
  Why: For the ADB/non-root RootService channel, `main.jar` is written to `getExternalCachePath()` then a later privileged shell `cp` copies it into `/data/local/tmp` and runs it via `app_process`. Between the app write and the privileged `cp`, another app with external-storage write access can swap the jar, so the privileged backend loads attacker bytes.
  Evidence: ipc/RootServiceManager.java:170-201,228-268
  Touches: ipc/RootServiceManager.java
  Acceptance: `main.jar` is dumped to app-internal (DE cache) storage, never external, before the privileged `cp`; or a digest is verified inside the privileged script before `app_process` loads it. (Device-gated.)
  Complexity: S

- [ ] P2 — AppInfoFragment list build resolves strings on a background thread (silent stuck progress)
  Why: `setupVerticalView` and its `setAppIdentity`/`setMoreInfo`/`setStorageAndCache`/`setDataUsage` helpers call dozens of `Fragment.getString()` on the worker thread inside `mListFuture`. After `onDetach()` cancels with `cancel(true)`, the next `getString()` throws via `requireContext()`; because `ThreadUtils.postOnBackgroundThread` swallows the exception into the unobserved Future, the worker dies mid-list and `mLoadedItemCount` never reaches 4, so a tab revisit before a fresh emission can leave the progress indicator stuck.
  Evidence: details/info/AppInfoFragment.java:3576-3611 (and the setters invoked from the same worker)
  Touches: details/info/AppInfoFragment.java
  Acceptance: snapshot `Context appContext = requireContext().getApplicationContext()` before submitting and resolve all strings from it (not `Fragment.getString()`); the list builds fully even if the fragment detaches mid-load. (Large file, broad surface — wants its own focused change + manual walkthrough.)
  Complexity: M

- [ ] P2 — Main-list badge text colors fail 4.5:1 contrast in some themes
  Why: `MainRecyclerAdapter.applyBadgeStyle` sets the badge text to a saturated raw `ColorCodes` hue over a ~17%-alpha tint of the same hue, so the tracker-count badge text (#FF8017 orange) reads ~2.0:1 on a light card and the blocked-tracker badge (salem_green) ~3.3:1 on a dark card — both below the 4.5:1 text minimum.
  Evidence: MainRecyclerAdapter.java:630-642; ColorCodes.java:38-40,116-118; libcore colors.xml:18
  Touches: MainRecyclerAdapter.java (applyBadgeStyle), colors-v2.xml (night-aware on-container aliases)
  Acceptance: badge content colors route through night-aware semantic on-container aliases (e.g. premium_warning_content / premium_success_content) so text hits ≥4.5:1 in light, dark and AMOLED; verified with a contrast checker against rendered screens. (Needs on-device visual verification — colors can't be validated headless.)
  Complexity: S

### P3

- [ ] P3 — Clickable main-list badges have <48dp touch targets
  Why: `tracker_indicator` and `perm_indicator` are clickable (`setClickable(true)` + click listener) but render at `premium_badge_min_height/width` = 24dp with no TouchDelegate. Two clickable badges share one `FlowLayout` parent, so a single `TouchDelegate` (one target rect per view) can't cover both.
  Evidence: MainRecyclerAdapter.java:420-422,457-459; item_main_v2.xml:115-116,136-137; dimens-v2.xml:76-77
  Touches: MainRecyclerAdapter.java (composite touch delegate on the badge row), item_main_v2.xml
  Acceptance: each clickable badge has a ≥48dp effective hit rect via a composite/multi-target TouchDelegate posted on the parent FlowLayout (visual size stays 24dp); a11y scanner clean. (Needs on-device touch verification.)
  Complexity: S

- [ ] P3 — Dead V2 premium design tokens (colors / dimens / styles / attr)
  Why: The premium resource system shipped a number of tokens that have zero references: precomputed elevation-overlay colors (`premium_elevated_1..5_{dark,amoled}`), unused brand ramp steps (`premium_brand_25/50/400/500/700/800`), several `premium_*` dimens/type tokens, dead V2 styles (`SearchBarCard`, `Button.EFAB`, `BottomSheet.Content`, `Dialog.Content`, `EmptyState`, `Skeleton`, `ShapeAppearance.AppTheme.V2.Card`), and the unused `listItemIndicatorWidth` attr (declared 8dp in themes-v2 / 9dp in styles — also mutually inconsistent). Dead tokens inflate the resource table and mislead future work.
  Evidence: colors-v2.xml + values-night/colors-v2.xml; dimens-v2.xml; themes-v2.xml:135,197,203,207,211,215,224; libcore attrs.xml:8
  Touches: app/src/main/res/values* (colors-v2, dimens-v2, themes-v2), libcore/ui/.../attrs.xml + styles.xml
  Acceptance: each token confirmed zero-reference (incl. R.* usage in Java) then removed; build + resource merge green; no behavior change.
  Complexity: S

- [ ] P3 — Sibling list rows diverge from the V2 card treatment
  Why: ~15 list-row layouts (e.g. item_debloater.xml) still use the classic `Widget.AppTheme.CardView.ListItem.Outlined` (bg `?colorSurface`, elevation 0) while the main list uses `Widget.AppTheme.V2.Card.ListRow` (bg `?colorSurfaceContainerLow`, 1dp elevation, hairline stroke), so adjacent NG screens render visibly different card surfaces.
  Evidence: layout/item_debloater.xml:4 (+ ~14 sibling row layouts); themes-v2.xml V2.Card.ListRow
  Touches: app/src/main/res/layout/item_*.xml
  Progress 2026-06-12: migrated the shared sibling list rows, scanner cards,
  profile-review warnings, empty states, batch failure rows, and secondary
  toolbars onto V2 premium tokens; dark-mode emulator screenshots pass for the
  mode sheet, onboarding guides, and populated main list.
  Acceptance: NG list rows share the V2 card treatment; spot-checked across debloater / permission / one-click lists in light, dark and AMOLED. (Visual — needs on-device verification.)
  Complexity: S

- [ ] P3 — `BatchOpsResultsActivity` still single-foreground-notification-ID across workers (defense-in-depth)
  Why: AutoBackupWorker's manual + periodic runs share `FOREGROUND_NOTIFICATION_ID`/`RESULT_NOTIFICATION_ID`. The new process-wide run guard prevents concurrent runs (the real correctness issue), but deriving the foreground id from `getId()` would remove the last way two notifications can clobber each other if the guard is ever relaxed.
  Evidence: backup/schedule/AutoBackupWorker.java:46-47,177-188
  Touches: backup/schedule/AutoBackupWorker.java
  Acceptance: foreground notification id derived from the worker id; result notification unaffected.
  Complexity: S

## Improvement Sweep (2026-06-11)

Findings from a six-domain codebase sweep (settings/onboarding, file-manager/editor,
installer/scanner, running-apps/usage, rules/profiles/automation, CI/build/distribution).
Deduplicated against all sections above.

### P1

- [ ] P1 — Enable R8 in release builds + resolve ProGuard keep rules
  Why: `app/build.gradle:66` has `minifyEnabled = false` in the release buildType, so release APKs ship without dead-code elimination, class merging, or obfuscation. This inflates APK size, leaves all internal class names readable, and skips tree-shaking of vendored AAR dependencies. `proguard-rules.pro:30,33` has two FIXMEs citing missing keep strategies for XmlPullParser and server IPC classes — these are the likely reason R8 was disabled rather than fixed.
  Evidence: app/build.gradle:66; proguard-rules.pro:30,33 (FIXME)
  Touches: app/build.gradle (minifyEnabled, shrinkResources), proguard-rules.pro (resolve FIXMEs), possibly app/libs/ AAR consumer rules
  Acceptance: `./gradlew assembleRelease` with minify + resource shrinking produces a working APK that installs, runs its privileged paths, and passes a smoke test; per-ABI APK sizes logged. (Device-gated for smoke test.)
  Complexity: M

- [ ] P1 — Batch-install version downgrade bypasses confirmation
  Why: `PackageInstallerActivity:718` checks for downgrade in the single-install flow, but `triggerBatchInstall():751` skips it entirely, allowing silent downgrades in a queued install. A downgraded app loses data on devices that enforce it.
  Evidence: apk/installer/PackageInstallerActivity.java:718 vs 751-761
  Touches: apk/installer/PackageInstallerActivity.java (propagate check to batch path)
  Acceptance: batch-installing a set that includes a downgrade shows the same warning as single-install; user can skip individual items.
  Complexity: S

- [ ] P1 — Profile apply ignores privilege requirements (silent mass-failure)
  Why: Profiles that contain freeze, component-block, or permission-change operations execute without checking whether the current privilege level (no-root, ADB, Shizuku, root) can actually perform those operations. The result is a silent batch failure reported as success (now reported as failure after the 2026-06-11 fix, but still confusing — the user is told it failed without knowing *why*).
  Evidence: profiles/struct/AppsBaseProfile.java:110-298 (no privilege check before each op section)
  Touches: profiles/struct/AppsBaseProfile.java (pre-flight privilege check), profiles/ProfileApplierService.java (surface reason)
  Acceptance: applying a profile whose operations exceed the current privilege shows a pre-apply warning naming the impossible ops, with options to skip them or abort; the warning is JVM-testable.
  Complexity: M

### P2

- [ ] P2 — Installer error messages are bare status codes
  Why: `InstallTranscript.java:185-220` maps `STATUS_FAILURE_*` codes to their raw constant names ("STATUS_FAILURE_INCOMPATIBLE_ROM") with no localized user explanation or recovery guidance. Users see opaque strings in the finished dialog.
  Evidence: apk/installer/InstallTranscript.java:185-220
  Touches: apk/installer/InstallTranscript.java (user-friendly mapping), strings.xml (new resources)
  Acceptance: each `STATUS_FAILURE_*` code shows a one-line localized explanation + a recovery hint; raw code still available in diagnostic transcript.
  Complexity: S

- [ ] P2 — Tracker database has no freshness signal
  Why: `StaticDataset.java:110-115` loads tracker signatures from the bundled `trackers.xml` resource (1985 entries) with no version/date metadata and no check-for-updates mechanism. Users have no way to know whether their offline scan is running a stale signature set.
  Evidence: app/src/main/res/values/trackers.xml (no date metadata); StaticDataset.java:110-115
  Touches: trackers.xml (add date meta), scanner/ (surface "Database: YYYY-MM-DD" label in results card)
  Acceptance: scanner results card shows the bundled database date; the full flavor checks for a newer asset bundle on a schedule (opt-in, default on).
  Complexity: S

- [ ] P2 — Scanner results are ephemeral with no export
  Why: Scan reports are rendered on-screen but can't be exported or shared. Security-conscious users (the target audience) need to archive scan results or share them with teams.
  Evidence: scanner/ScannerViewModel.java + ScannerActivity.java (no export mechanism)
  Touches: scanner/ (export action → JSON/CSV snapshot with timestamp, device, app version, tracker matches)
  Acceptance: a "Share scan report" action in the scanner toolbar exports a structured snapshot; round-trips via import or plain-text reading.
  Complexity: S

- [ ] P2 — Implement stub profile rule export (6-year-old TODO)
  Why: `AppsBaseProfile.java:186-187` logs "Not implemented export rules" with a `TODO(18/11/20)`. The `exportRules` field is declared, serialized, configurable in the UI, but never applied — so a user who configures rule export in a profile gets silent no-op.
  Evidence: profiles/struct/AppsBaseProfile.java:186-187 (TODO from 2020-11-18)
  Touches: profiles/struct/AppsBaseProfile.java (wire rule export via RulesStorageManager)
  Acceptance: a profile with `exportRules` set exports component/app-op/permission rules on apply; the export appears in the rule store or a user-specified file. Alternatively: remove the dead field and UI toggle with a dated decision note.
  Complexity: M

- [ ] P2 — Expand profile trigger types beyond time-of-day
  Why: `ProfileTrigger.java:32-36` supports only 5 trigger types (time-of-day, charging, Wi-Fi, any-network, boot). Missing high-value triggers: on-app-install, on-app-update, on-app-uninstall — the use cases where automation matters most (e.g., "block trackers in any newly installed app"). Tasker integration exists (`AutomationIntents.java`) but NG's own triggers are narrow.
  Evidence: profiles/trigger/ProfileTrigger.java:32-36
  Touches: profiles/trigger/ (new trigger types + BroadcastReceiver for PACKAGE_ADDED/REPLACED/REMOVED), RoutineWorker/RoutineScheduler (type dispatch)
  Acceptance: a profile can trigger on app-install/update with a JVM test covering the trigger dispatch; on-app-uninstall trigger fires cleanup profiles.
  Complexity: M

- [ ] P2 — PR check workflow + lint gate
  Why: No CI runs on pull requests — lint, unit tests, and dependency review only run on pushes to main or scheduled scans. `app/build.gradle:102` sets `checkReleaseBuilds = false` and `abortOnError = false`, so lint findings never fail anything.
  Evidence: .github/workflows/ (no pr-checks.yml); app/build.gradle:102
  Touches: .github/workflows/ (new pr-checks.yml: lint + unit test + dependency-review), app/build.gradle (enable checkReleaseBuilds)
  Acceptance: every PR targeting main gets a pass/fail status from lint + tests before merge; lint warnings on release builds are visible in PR checks.
  Complexity: S

- [ ] P2 — Split APK ABI/density pre-install validation
  Why: `InstallDependencyChecker.java:84-96` checks minSdk and shared libraries but not whether selected APK splits are ABI- and density-compatible with the device. Installing arm64 splits on an x86 emulator (or the reverse) fails at runtime, not at install-time in NG.
  Evidence: apk/installer/InstallDependencyChecker.java:84-96
  Touches: apk/installer/InstallDependencyChecker.java (add ABI/density split validation)
  Acceptance: installing an incompatible split set shows a warning before the install attempt, naming the mismatched ABI/density.
  Complexity: S

- [ ] P2 — Onboarding should guide through POST_NOTIFICATIONS and other critical permissions
  Why: The onboarding wizard focuses on privilege *modes* (root/ADB/Shizuku) but doesn't guide through granting Android 13+ `POST_NOTIFICATIONS` or other mode-required permissions. Fresh installs on A13+ may hit silent notification failures with no context.
  Evidence: onboarding/OnboardingFragment.java (no permission-request step)
  Touches: onboarding/ (add permission-grant step before or after mode selection)
  Acceptance: fresh install on A13+ prompts for notification permission during onboarding; mode-specific permissions (usage access for usage stats, etc.) are requested with context.
  Complexity: S

- [ ] P2 — Capability detection re-runs heavyweight probes on every onResume
  Why: `OnboardingFragment.java:892-900` calls `refreshCapabilityStatuses()` unconditionally on every resume, re-probing root, Shizuku, ADB, and Dhizuku even if nothing changed. `RootManagerInfo.detect()` runs shell commands — expensive and battery-wasteful on every screen return.
  Evidence: onboarding/OnboardingFragment.java:892-900; runner/RootManagerInfo (shell probes)
  Touches: onboarding/OnboardingFragment.java (cache results, only re-probe on explicit "Re-check" tap)
  Acceptance: capability status is cached across the fragment lifecycle; "Re-check" button is the only path to a fresh probe; no visible lag on tab-return.
  Complexity: S

### P3

- [ ] P3 — Code editor: word-wrap preference not persistent + limited language map
  Why: `CodeEditorFragment:589` toggles word-wrap at runtime but doesn't persist the preference across sessions. `CodeEditorViewModel.EXT_TO_LANGUAGE_MAP:69-78` manually maps a narrow set of extensions; Kotlin, HTML, CSS, TOML, INI have no explicit entries.
  Evidence: editor/CodeEditorFragment.java:589; editor/CodeEditorViewModel.java:69-78
  Touches: editor/ (persist wrap pref, extend language map)
  Acceptance: word-wrap state survives session restart; at least Kotlin, HTML, CSS, TOML are syntax-highlighted.
  Complexity: S

- [ ] P3 — Code editor: undo history browser + diff view
  Why: The editor supports undo/redo but no UI to browse history depth or see what changed. No file-version diff view for comparing current edits against the on-disk version.
  Evidence: editor/CodeEditorFragment.java:652-656 (undo/redo wired, no history UI)
  Touches: editor/ (undo history panel, simple inline diff)
  Acceptance: a toolbar action shows the undo stack; a diff toggle highlights changes vs. the saved file.
  Complexity: M

- [ ] P3 — File manager: hardcoded 2s sleep in batch paste inflates apparent duration
  Why: `FmFragment.java:1526` inserts `SystemClock.sleep(2_000)` per file in the paste loop. A 50-file paste takes ≥100s of pure sleep regardless of actual copy speed — misleading progress and frustrating users.
  Evidence: fm/FmFragment.java:1526
  Touches: fm/FmFragment.java (remove the sleep or replace with a per-file progress update callback)
  Acceptance: pasting 50 small files completes proportionally to actual I/O, not a fixed 100s floor.
  Complexity: S

- [ ] P3 — File manager: "Open with" unimplemented in file properties
  Why: `FilePropertiesDialogFragment:147` has a TODO "Handle open with" — the action is wired in the dialog but does nothing on tap.
  Evidence: fm/FilePropertiesDialogFragment.java:147
  Touches: fm/FilePropertiesDialogFragment.java (wire ACTION_VIEW intent with MIME type)
  Acceptance: tapping "Open with" in file properties launches a chooser; unsupported MIME types show a toast.
  Complexity: S

- [ ] P3 — File manager: bookmarks panel lacks UI prominence
  Why: `FmFavoritesManager` exists and breadcrumb long-press adds to favorites, but there's no dedicated bookmarks panel or sidebar quick-access — only the breadcrumb context menu.
  Evidence: fm/FmFavoritesManager.java; fm/FmPathListAdapter.java:137-142
  Touches: fm/ (bookmarks drawer or bottom-sheet, accessible from toolbar)
  Acceptance: a toolbar icon opens a bookmarks panel; add/remove works from both the panel and the existing breadcrumb long-press.
  Complexity: S

- [ ] P3 — Scanner results not cached for app-details display
  Why: The main-list and app-details screens show a tracker count badge, but tapping it re-scans from scratch. Previous scan results (which trackers, which components) aren't cached per package+version, so users repeat expensive scans to review findings.
  Evidence: details/info/AppInfoFragment.java (opens ScannerActivity, no result cache)
  Touches: scanner/ (persist last scan result keyed by package+versionCode), details/info/ ("Last scanned: [date], [N] trackers" card with tap-through)
  Acceptance: re-opening app details after a scan shows the cached result without re-scanning; cache invalidates on app update.
  Complexity: M

- [ ] P3 — Running apps: fixed 10s polling with no configurability
  Why: `RunningAppsActivity:331-341` hardcodes a 10s `Timer` interval. No user preference, no adaptive throttling when backgrounded, no skip when the process list hasn't changed.
  Evidence: runningapps/RunningAppsActivity.java:331-341
  Touches: runningapps/ (configurable interval in settings, pause polling when backgrounded)
  Acceptance: user can choose refresh interval (5/10/30s/manual); polling pauses on onStop and resumes on onStart.
  Complexity: S

- [ ] P3 — Running apps: force-stop lacks critical-package guard
  Why: `RunningAppsViewModel:249-259` calls `forceStopPackage()` without checking if the target is system-critical. The debloat safety net (ROADMAP P1) guards batch ops but running-apps force-stop is unguarded.
  Evidence: runningapps/RunningAppsViewModel.java:249-259
  Touches: runningapps/RunningAppsViewModel.java (reuse the critical-package guard from PermissionRecovery)
  Acceptance: force-stopping a critical/system package shows a confirmation naming the risk; non-critical apps force-stop immediately.
  Complexity: S

- [ ] P3 — Usage stats: no comparative views or data export
  Why: `AppUsageViewModel:115-138` loads a single interval only. No week-over-week comparison, no trend visualization, no export to CSV/JSON for external analysis.
  Evidence: usage/AppUsageViewModel.java:115-138; usage/AppUsageAdapter.java (display only)
  Touches: usage/ (comparative interval selector, export action)
  Acceptance: usage screen offers a "Compare" toggle showing this-week vs. last-week deltas; an export action writes per-app usage to a shareable CSV.
  Complexity: M

- [ ] P3 — Offline scanner mode should be explicit
  Why: `ScannerViewModel.java:89-131` fires VirusTotal and Pithus network tasks unconditionally. When offline, they fail silently and users get partial results without knowing why online reports are missing.
  Evidence: scanner/ScannerViewModel.java:89-131
  Touches: scanner/ScannerViewModel.java (isOffline check), scanner/ScannerFragment.java (banner: "Offline — showing local scan only")
  Acceptance: offline scan shows a visible banner; online sections are grayed with "Requires network" labels rather than silently absent.
  Complexity: S

- [ ] P3 — Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Evidence: profiles/struct/BaseProfile.java (serializeToJson exists, no QR/deep-link codec)
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Complexity: M

- [ ] P3 — MyAndroidTools rule import format
  Why: `ExternalComponentsImporter.java:38-40` supports only Blocker (JSON) and Watt (IFW XML). MyAndroidTools has a legacy but active user base whose rule exports are incompatible.
  Evidence: rules/compontents/ExternalComponentsImporter.java:38-40
  Touches: rules/compontents/ExternalComponentsImporter.java (MyAndroidTools parser)
  Acceptance: importing a MyAndroidTools backup file produces the correct component-blocking rules in the rule store; conflicting rules are surfaced.
  Complexity: S

- [ ] P3 — Backup schedule summary doesn't show "Next run"
  Why: `BackupRestorePreferences.java:587-609` updates schedule summaries (time, network) only when the user manually opens the preference dialog. If auto-backup is active, there's no "Next run: ..." line updating reactively.
  Evidence: settings/BackupRestorePreferences.java:587-609
  Touches: settings/BackupRestorePreferences.java (reactive summary via observer)
  Acceptance: the backup-schedule preference shows "Next run: [date/time]" that updates automatically.
  Complexity: S

- [ ] P3 — Post-install abandoned session cleanup
  Why: `PackageInstallerService.java` manages install sessions but abandoned ones (force-quit mid-install) aren't explicitly cleaned. Stale sessions consume the system's package-installer quota.
  Evidence: apk/installer/PackageInstallerService.java (no cleanup logic)
  Touches: apk/installer/ (periodic cleanup of sessions older than N minutes on app start)
  Acceptance: stale install sessions are cleaned on app launch; no quota exhaustion from repeated failed installs.
  Complexity: S

- [ ] P3 — APK size tracking in release CI
  Why: The release workflow produces per-ABI APKs but doesn't capture or report their sizes. Without a baseline, bloat creeps unnoticed — especially important pre-IzzyOnDroid (30 MB cap).
  Evidence: .github/workflows/release.yml (no size logging step)
  Touches: .github/workflows/release.yml (add size-reporting step)
  Acceptance: release workflow logs per-ABI APK sizes as a workflow summary; optionally fails if any exceed a configurable threshold.
  Complexity: S
