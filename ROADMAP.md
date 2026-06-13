<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Product Quality Roadmap (2026-06-12)

- [ ] P1 — Unified destructive-action safety model
  Why: destructive flows should consistently explain impact, name the affected apps/users,
  distinguish reversible vs irreversible work, and require the same confirmation quality
  across batch ops, app details, running apps, backup, and profiles.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/{batchops,details,runningapps,backup,profiles}/

- [ ] P1 — Recovery-first operation trail
  Why: powerful operations should leave a clear recovery path: what changed, what failed,
  what can be retried, and where logs/history/backups can be used to recover.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/{batchops,history,backup,details}/

- [ ] P1 — Privilege health and capability status surface
  Why: root/ADB/no-root capability differences shape nearly every workflow; users need a
  plain status surface that explains available modes, degraded behavior, and remediation.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/{settings,servermanager,adb,runner}/

- [ ] P2 — First-run confidence pass
  Why: the app exposes advanced controls before users understand privilege mode, backup
  safety, tracker rules, and restore risk; first-run guidance should establish trust without
  becoming a marketing screen.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/{main,settings,backup,rules}/

- [ ] P2 — Cross-surface workflow cohesion
  Why: app list, app details, batch operations, profiles, backup, and running-apps screens
  should use the same terms, primary actions, result states, and handoff points.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/

- [ ] P2 — Accessibility, keyboard, and touch-target hardening
  Why: dense expert tools still need predictable focus, visible labels, 48dp controls, and
  non-color-only status meaning across dialogs, lists, chips, menus, and result screens.
  Where: app/src/main/res/layout/, app/src/main/java/io/github/muntashirakon/AppManager/

- [ ] P2 — Degraded, empty, error, loading, and success state system
  Why: secondary screens should never fail silently or show blank states; every unavailable,
  partial, or failed workflow needs calm actionable copy and an obvious next step.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/, app/src/main/res/layout/

- [ ] P2 — Settings information architecture cleanup
  Why: settings should group risk, privileges, appearance, backup, rules, notifications, and
  advanced/debug controls so users can find decisions without memorizing implementation
  boundaries.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/settings/

- [ ] P2 — Critical-flow smoke and contract tests
  Why: destructive confirmations, parser hardening, lifecycle cleanup, restore recovery, and
  privilege fallbacks need tests that fail when safety regressions return.
  Where: app/src/test/, app/src/androidTest/, .github/workflows/

- [ ] P3 — Visual token and component polish pass
  Why: cards, banners, list rows, dialogs, badges, chips, toasts, and nested surfaces should
  feel like one product in light, dark, and AMOLED modes without one-off colors or spacing.
  Where: app/src/main/res/{layout,values,drawable}/

- [ ] P3 — Tooltips and microcopy consistency pass
  Why: expert controls need concise labels, explainers, and warnings that are useful without
  being robotic, vague, or inconsistent between screens.
  Where: app/src/main/res/values/strings.xml, app/src/main/java/io/github/muntashirakon/AppManager/

## Active Build Initiative — Quality & Capability Push (2026-06-11)

Sequenced from the post-audit improvement review. Items already specced in detail
elsewhere in this file are cross-referenced rather than duplicated. "Building now"
items are verifiable headless (compile / resource merge / JVM tests) and are being
implemented this initiative; "device-gated" items carry full specs but are NOT
patched blind — they touch the privileged bootstrap, need an emulator/rooted
device, or need on-device visual verification.

### Building now (verifiable headless)

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
- [ ] INIT-D3 — HMAC mutual auth + native run_server port. See "Port HMAC mutual auth …".
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

- [ ] P1 — Port HMAC mutual auth + native run_server for the local privileged channel
  Why: Upstream hardened the app↔ADB-server channel with HMAC challenge-response and converted run_server to a native executable (fixing root mode broken since 3.0.0, #948, and reducing detectable service footprint); NG's channel lacks both.
  Evidence: upstream commits 88eb453, 07c7199, b42efbb, f8d3126 (RESEARCH.md §Competitive); grep: no HMAC in adb/ or libserver/ (verified)
  Touches: libserver/, server/, adb/, servermanager/
  Acceptance: server rejects unauthenticated connections (negative test); root mode works on a rooted A16 emulator; ported commits attributed.
  Complexity: M

### P2

- [ ] P2 — Restore the missing minSdk-21 ceiling ledger (decision itself is already made)
  Why: The minSdk-23 decision EXISTS on disk (docs/policy/2026-05-26-minsdk-23-decision.md: hold 21 through v0.6.x, four forced-decision triggers) — but the dependency ledger it depends on, docs/policy/minsdk-21-ceiling.md, is absent while being linked from versions.gradle:39, the decision memo, and docs/architecture/README.md; without it the trigger watch has no bookkeeping.
  Evidence: docs/policy/2026-05-26-minsdk-23-decision.md (verified on disk, 2026-06-10); https://github.com/material-components/material-components-android/releases/tag/1.14.0 (minSdk 23 confirmed); versions.gradle:39
  Touches: docs/policy/minsdk-21-ceiling.md (recreate the ledger: material/activity/biometric/room/webkit/sora-editor pinned-cluster table + trigger status), versions.gradle (ledger comments)
  Acceptance: the ledger file exists with the current pinned-cluster table and a dated trigger-status section; all three inbound references resolve.
  Complexity: S

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

### P2

- [ ] P2 — RootService main.jar staged via external storage before privileged copy (TOCTOU)
  Why: For the ADB/non-root RootService channel, `main.jar` is written to `getExternalCachePath()` then a later privileged shell `cp` copies it into `/data/local/tmp` and runs it via `app_process`. Between the app write and the privileged `cp`, another app with external-storage write access can swap the jar, so the privileged backend loads attacker bytes.
  Evidence: ipc/RootServiceManager.java:170-201,228-268
  Touches: ipc/RootServiceManager.java
  Acceptance: `main.jar` is dumped to app-internal (DE cache) storage, never external, before the privileged `cp`; or a digest is verified inside the privileged script before `app_process` loads it. (Device-gated.)
  Complexity: S

### P3

- [ ] P3 — Clickable main-list badges have <48dp touch targets
  Why: `tracker_indicator` and `perm_indicator` are clickable (`setClickable(true)` + click listener) but render at `premium_badge_min_height/width` = 24dp with no TouchDelegate. Two clickable badges share one `FlowLayout` parent, so a single `TouchDelegate` (one target rect per view) can't cover both.
  Evidence: MainRecyclerAdapter.java:420-422,457-459; item_main_v2.xml:115-116,136-137; dimens-v2.xml:76-77
  Touches: MainRecyclerAdapter.java (composite touch delegate on the badge row), item_main_v2.xml
  Acceptance: each clickable badge has a ≥48dp effective hit rect via a composite/multi-target TouchDelegate posted on the parent FlowLayout (visual size stays 24dp); a11y scanner clean. (Needs on-device touch verification.)
  Progress 2026-06-12: fixed the nested coordinate translation bug in the
  composite badge TouchDelegate, added a Robolectric regression test, installed
  the Floss debug build on the emulator, and captured the main-list badge
  surface. Remaining: clean a11y scanner confirmation after the emulator
  UiAutomation service recovers.
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

## Improvement Sweep (2026-06-11)

Findings from a six-domain codebase sweep (settings/onboarding, file-manager/editor,
installer/scanner, running-apps/usage, rules/profiles/automation, CI/build/distribution).
Deduplicated against all sections above.

### P1

### P2

### P3

- [ ] P3 — Code editor: undo history browser + diff view
  Why: The editor supports undo/redo but no UI to browse history depth or see what changed. No file-version diff view for comparing current edits against the on-disk version.
  Evidence: editor/CodeEditorFragment.java:652-656 (undo/redo wired, no history UI)
  Touches: editor/ (undo history panel, simple inline diff)
  Acceptance: a toolbar action shows the undo stack; a diff toggle highlights changes vs. the saved file.
  Complexity: M

- [ ] P3 — Scanner results not cached for app-details display
  Why: The main-list and app-details screens show a tracker count badge, but tapping it re-scans from scratch. Previous scan results (which trackers, which components) aren't cached per package+version, so users repeat expensive scans to review findings.
  Evidence: details/info/AppInfoFragment.java (opens ScannerActivity, no result cache)
  Touches: scanner/ (persist last scan result keyed by package+versionCode), details/info/ ("Last scanned: [date], [N] trackers" card with tap-through)
  Acceptance: re-opening app details after a scan shows the cached result without re-scanning; cache invalidates on app update.
  Complexity: M

- [ ] P3 — Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Evidence: profiles/struct/BaseProfile.java (serializeToJson exists, no QR/deep-link codec)
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Complexity: M

## Research-Driven Additions

### P1

- [ ] P1 — Distribution documentation link-rot repair
  Why: README and policy docs point reviewers to missing build-flavor, package-visibility, reproducible-build, sideload, and project-context markdown, weakening F-Droid/Izzy/Obtainium trust despite strong release automation.
  Evidence: README.md:116,165; docs/policy/permissions.md; .github/workflows/docs-link-check.yml:78; CLAUDE.md:6,20,167; IzzyOnDroid/F-Droid reproducible-build requirements.
  Touches: README.md, CLAUDE.md, .github/workflows/docs-link-check.yml, existing docs/distribution/ and docs/policy/ references
  Acceptance: every README/docs/workflow markdown link resolves in a clean checkout by repointing or removing stale references; reviewer-facing flavor, package-visibility, reproducibility, and sideload-verification claims have one canonical destination; docs-link-check fails on future drift.
  Complexity: S

- [ ] P1 — Main-list load failure watchdog and recovery surface
  Why: accepted upstream reports show the app list can appear to load forever or show no apps when package enumeration throws; NG currently logs loader failures but does not expose a failed-load state with retry/support details.
  Evidence: upstream AppManager #1982/#1825/#1948; app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java:593-619; app/src/main/res/layout/activity_main.xml:172-221
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/main/, app/src/main/res/layout/activity_main.xml, app/src/main/res/values/strings.xml, app/src/test/
  Acceptance: injected package-enumeration failure exits loading within a bounded timeout, shows a retry/support-info action instead of stale indefinite loading, labels any last-good list as stale, and has a JVM or Robolectric regression test.
  Complexity: M

### P2

- [ ] P2 — Form-factor-aware permission prompt gate
  Why: WearOS/TV sideload users hit repeated or unreachable permission prompts for settings panels that do not exist on their device class.
  Evidence: upstream AppManager #1823; PermissionManagerX #61; app/src/main/AndroidManifest.xml leanback feature declaration; app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java permission/settings launch paths.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/{details,main,onboarding,settings,self}/, app/src/main/res/values/strings.xml
  Acceptance: unavailable permission/settings requests are suppressed or downgraded to a single dismissible explanation on TV/Wear; prompts remain reachable by D-pad/round screens; the gate is unit-tested with phone, TV, and Wear capability fixtures.
  Complexity: M

- [ ] P2 — Trigger-bound profile filters
  Why: package install/update/uninstall triggers now exist, but they cannot yet apply a profile only to apps matching a saved filter such as newly installed apps with trackers or a vendor package prefix.
  Evidence: docs/architecture/05-routine-scheduler.md open decision; app/src/main/java/io/github/muntashirakon/AppManager/profiles/trigger/ProfileTrigger.java TYPE_ON_APP_*; app/src/main/java/io/github/muntashirakon/AppManager/filters/FilterItem.java; AppDash tag/filter workflows.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/{profiles/trigger,profiles,filters/preset,automation}/, app/src/main/res/values/strings.xml
  Acceptance: a routine trigger can reference an optional filter/preset; package-event triggers pass the changed package through the matcher; non-matching events do not run the profile; JSON round-trip and scheduler tests cover the filter field.
  Complexity: M

### P3

- [ ] P3 — Hostile APK/APKS archive fixture corpus
  Why: NG accepts untrusted APK, APKS, APKM, and XAPK-style archives; parser or extraction mistakes can become crashes, hangs, or path traversal.
  Evidence: app/src/main/java/io/github/muntashirakon/AppManager/apk/ApkFile.java:236 FIXME(#227); Android Zip Path Traversal guidance; APKMirror Installer/SAI split-archive support.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/, app/src/test/
  Acceptance: fixture archives cover path traversal, special names, duplicate entries, unsupported compression, malformed manifests, and oversized member metadata; scanner/installer/manifest-viewer paths return structured per-file errors with no crash, hang, or path escape.
  Complexity: M

- [ ] P3 — Installer caller outcome result support
  Why: external callers and automation tools need a reliable result contract instead of scraping UI or notifications after NG handles an install.
  Evidence: InstallerX-Revived #672; app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/; Android PackageInstaller status result conventions.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/, app/src/main/res/values/strings.xml
  Acceptance: when launched with the standard return-result extra, NG finishes with result code plus package/status/message extras after single or queued install completion; failures still show the existing transcript in-app.
  Complexity: S

## Research-Driven Additions (Pass 3 — 2026-06-13)

### P2

- [ ] P2 — IFW+PM dual-mode component blocking
  Why: Component blocking currently uses either IFW (Intent Firewall rules in /data/system/ifw/) or PM (setComponentEnabledSetting) but never both. Blocker's IFW_PLUS_PM mode proves combined blocking is more reliable: IFW rules survive factory reset while PM disables don't, and PM disables take effect immediately while IFW rules may need a reboot on some ROMs. Using both gives belt-and-suspenders reliability.
  Evidence: https://github.com/lihenggui/blocker (IFW+PM dual-mode); rules/compontents/ and details/AppDetailsComponentsFragment (verified: single-mode per operation)
  Touches: rules/compontents/ComponentRule.java (add COMBINED mode enum), rules/RulesStorageManager.java (apply both in sequence), details/AppDetailsComponentsFragment.java (mode selector), batchops/ (batch component ops), settings/ (default blocking mode preference)
  Acceptance: a "Combined (IFW+PM)" blocking mode is available in component-blocking flows; blocking applies both IFW and PM rules in sequence; verify both states survive reboot and factory-reset scenarios separately; existing single-mode behavior unchanged for users who prefer it.
  Complexity: M

- [ ] P2 — Human-readable split APK labels in installer chooser
  Why: SplitApkChooser presents raw split entry names (config.arm64_v8a, config.en, config.xxhdpi) in a SearchableMultiChoiceDialogBuilder. Users without Android packaging knowledge cannot distinguish ABI splits from locale or density splits. InstallerX-Revived solves this with a human-readable label layer that maps split names to descriptions.
  Evidence: https://github.com/wxxsfxyzm/InstallerX-Revived (split APK UX); apk/splitapk/SplitApkChooser.java (verified: raw ApkFile.Entry names passed to dialog)
  Touches: apk/splitapk/SplitApkChooser.java (label resolution), apk/splitapk/ (new SplitLabelResolver utility: ABI → "ARM 64-bit", density → "High-DPI resources", locale → "French language pack", feature → feature module name), app/src/main/res/values/strings.xml (label templates)
  Acceptance: the split chooser shows human-readable descriptions alongside or instead of raw split names; auto-selection still picks the best ABI+density+locale for the device; users can override; unknown split types fall back to the raw name.
  Complexity: S

- [ ] P2 — Debloat preset export/import for OTA re-application
  Why: Users who debloat before an OTA update must re-select every package manually afterward because debloat selections have no export/import path. Canta and UAD-NG both support debloat-list export for re-application. NG has profiles for per-app configuration but no export/import specifically for the set of packages marked for removal/disabling in the debloater.
  Evidence: https://github.com/samolego/Canta (debloat list export); https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation (package database export); debloat/ package (verified: no preset export/import flow)
  Touches: debloat/ (export current selections to JSON, import from JSON), debloat/DebloatActivity or DebloaterFragment (export/import menu actions), profiles/ (optional: convert a debloat preset into a profile for reuse)
  Acceptance: a "Export debloat preset" action saves current debloat selections (package names + chosen action per package) to a shareable JSON file; "Import preset" loads selections and highlights any packages not found on the current device; round-trip tested with at least one OEM-specific set.
  Complexity: S

### P3

- [ ] P3 — Dedicated freeze surface with home-screen widget
  Why: Freeze/unfreeze works via app details or batch ops, but there is no dedicated screen listing all frozen apps with one-tap toggle — the feature is buried. Hail's frozen-apps grid with one-tap toggle and home-screen widget is the competitive standard for daily freeze/unfreeze workflows. NG ships a QS freeze tile but no in-app freeze surface or widget.
  Evidence: https://github.com/aistra0528/Hail (freeze grid, widget, grayscale icons); main/MainActivity.java (frozen filter exists but no dedicated freeze fragment); QuickFreezeTileService (QS tile only, no widget)
  Touches: new FreezeManagerFragment under main/ (reuse existing freeze/unfreeze plumbing from batchops/), new AppWidgetProvider for home-screen toggle, main menu entry, app/src/main/res/layout/ (grid layout), app/src/main/res/xml/ (widget metadata)
  Acceptance: a main-menu entry opens a grid of all frozen/suspended apps; each row has a one-tap toggle that freezes or unfreezes immediately; a home-screen widget shows frozen-app count and opens the freeze surface on tap; works in root, ADB, and Shizuku modes.
  Complexity: M

- [ ] P3 — Biometric gate option for privileged/destructive operations
  Why: Upstream #1738 requests biometric authentication before terminal access and destructive batch operations. NG's destructive flows have confirmation dialogs but no optional biometric gate, leaving the device vulnerable if unlocked and unattended. The biometric library (1.4.0-alpha04) is already a dependency.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1738 ; versions.gradle:biometric_version = '1.4.0-alpha04' (verified dependency); batchops/, terminal/, backup/ (no BiometricPrompt usage, verified)
  Touches: settings/ (opt-in toggle, default off), a shared BiometricGateHelper utility, batchops/ (batch uninstall, clear data, disable), terminal/TermActivity.java, backup/ (restore, delete backup)
  Acceptance: with the toggle on, BiometricPrompt challenges before batch uninstall, batch clear-data, terminal launch, and backup deletion; authentication failure blocks the operation; toggle off = current behavior unchanged; works with fingerprint, face, and device credential fallback.
  Complexity: S

- [ ] P3 — Backup restore API-level compatibility warnings
  Why: Community complaints about Neo-Backup and Titanium Backup restoration unreliability across Android version jumps (e.g., Android 12 backup restored on Android 14) apply equally to NG. Backup metadata already records the source device's targetSdk and Android version, but the restore flow does not warn when these differ significantly from the current device.
  Evidence: https://github.com/NeoApplications/Neo-Backup (restore reliability complaints); backup/ metadata (MetadataManager records source SDK/OS version); backup/RestoreOp.java (no API-level boundary check, verified)
  Touches: backup/RestoreOp.java (pre-restore compatibility check), backup/dialog/ (warning dialog with "proceed anyway" option), app/src/main/res/values/strings.xml (warning copy)
  Acceptance: when restoring a backup whose source Android API level differs by 2+ from the device, a warning explains the risk and offers "proceed anyway" or "cancel"; warning includes specific risk factors (permission model changes, scoped storage, package visibility); no warning for same-API or adjacent-API restores.
  Complexity: S

- [ ] P3 — Scheduled cache/data clearing as routine operation type
  Why: SD Maid SE's scheduled cache-clearing is the #1 feature users associate with automated Android maintenance. NG's RoutineScheduler (v0.6.0 target) already has the executor pattern for scheduled operations but does not include cache or expendable-data clearing as an operation type.
  Evidence: https://github.com/d4rken-org/sdmaid-se (scheduled cache clearing); profiles/RoutineScheduler.java and profiles/RoutineWorker.java (verified: no CLEAR_CACHE operation type); compat/PackageManagerCompat.java (freeStorageAndNotify available for privileged modes)
  Touches: profiles/ (add CLEAR_CACHE and CLEAR_DATA operation types to RoutineScheduler), compat/PackageManagerCompat.java (cache-clearing wrapper), settings/ (per-profile operation type selector)
  Acceptance: a routine can include "clear cache" or "clear expendable data" as an operation, scoped to specific apps or app-set filters; scheduled execution clears cache for matched apps and logs byte counts; requires root or Shizuku privilege; operation type cleanly refused with explanation on no-root mode.
