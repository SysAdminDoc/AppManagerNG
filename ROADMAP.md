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


### P3

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


- [ ] P3 — Tracker report rollup: company → category → jurisdiction
  Why: TrackerControl's grouped presentation (parent company, ads/analytics/social category, HQ country) with plain-language blurbs is meaningfully more legible than flat library lists and is pure offline metadata; NG's TrackerInfoDialog already resolves tracker identity (verified) — the rollup is presentation work.
  Evidence: https://trackercontrol.org/ ; scanner/TrackerInfoDialog.java + rules/compontents/TrackerCategory.java (verified)
  Touches: scanner/ (report screen grouping), tracker metadata (extend the bundled dataset with company/category/country columns), strings (blurbs)
  Acceptance: the per-app tracker report groups findings by parent company with category chips and a one-line "what this category means"; flat list remains available as a toggle; works fully offline.
  Complexity: M

- [ ] P3 — Theme/a11y coherence pass (deferred-audit visual debt)
  Why: The 2026-06-09 audit verified divergent dark palettes across NG-added screens, dead premium design tokens, and tracker/perm badges under the 48dp touch-target minimum — small fixes that compound into perceived quality.
  Evidence: 2026-06-09 audit session record (deferred list); res/ themes and the named drawables (spot-verified)
  Touches: app/src/main/res/ (themes, drawables, dimens), details/ badge layouts
  Acceptance: NG-added screens share one dark palette token set; the misused drawables are replaced with purpose-named assets; all interactive badges hit ≥48dp touch targets (a11y scanner clean on those screens).
  Complexity: M

## Deep Audit Follow-ups (2026-06-11)

Deferred from the 2026-06-11 deep engineering/QA/UX audit pass. The fixed half
of that pass is in the commit history / CHANGELOG `Unreleased`. Items below were
verified real but are device-gated, design-verification-gated, or carry enough
regression risk to need their own change.

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

### P3

- [ ] P3 — Code editor: undo history browser + diff view
  Why: The editor supports undo/redo but no UI to browse history depth or see what changed. No file-version diff view for comparing current edits against the on-disk version.
  Evidence: editor/CodeEditorFragment.java:652-656 (undo/redo wired, no history UI)
  Touches: editor/ (undo history panel, simple inline diff)
  Acceptance: a toolbar action shows the undo stack; a diff toggle highlights changes vs. the saved file.
  Complexity: M

- [ ] P3 — Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Evidence: profiles/struct/BaseProfile.java (serializeToJson exists, no QR/deep-link codec)
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Complexity: M

## Research-Driven Additions

### P2

- [ ] P2 — Form-factor-aware permission prompt gate
  Why: WearOS/TV sideload users hit repeated or unreachable permission prompts for settings panels that do not exist on their device class.
  Evidence: upstream AppManager #1823; PermissionManagerX #61; app/src/main/AndroidManifest.xml leanback feature declaration; app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java permission/settings launch paths.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/{details,main,onboarding,settings,self}/, app/src/main/res/values/strings.xml
  Acceptance: unavailable permission/settings requests are suppressed or downgraded to a single dismissible explanation on TV/Wear; prompts remain reachable by D-pad/round screens; the gate is unit-tested with phone, TV, and Wear capability fixtures.
  Complexity: M

### P3

- [ ] P3 — Hostile APK/APKS archive fixture corpus
  Why: NG accepts untrusted APK, APKS, APKM, and XAPK-style archives; parser or extraction mistakes can become crashes, hangs, or path traversal.
  Evidence: app/src/main/java/io/github/muntashirakon/AppManager/apk/ApkFile.java:236 FIXME(#227); Android Zip Path Traversal guidance; APKMirror Installer/SAI split-archive support.
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/, app/src/test/
  Acceptance: fixture archives cover path traversal, special names, duplicate entries, unsupported compression, malformed manifests, and oversized member metadata; scanner/installer/manifest-viewer paths return structured per-file errors with no crash, hang, or path escape.
  Complexity: M

## Research-Driven Additions (Pass 3 — 2026-06-13)

### P3

- [ ] P3 — Dedicated freeze surface with home-screen widget
  Why: Freeze/unfreeze works via app details or batch ops, but there is no dedicated screen listing all frozen apps with one-tap toggle — the feature is buried. Hail's frozen-apps grid with one-tap toggle and home-screen widget is the competitive standard for daily freeze/unfreeze workflows. NG ships a QS freeze tile but no in-app freeze surface or widget.
  Evidence: https://github.com/aistra0528/Hail (freeze grid, widget, grayscale icons); main/MainActivity.java (frozen filter exists but no dedicated freeze fragment); QuickFreezeTileService (QS tile only, no widget)
  Touches: new FreezeManagerFragment under main/ (reuse existing freeze/unfreeze plumbing from batchops/), new AppWidgetProvider for home-screen toggle, main menu entry, app/src/main/res/layout/ (grid layout), app/src/main/res/xml/ (widget metadata)
  Acceptance: a main-menu entry opens a grid of all frozen/suspended apps; each row has a one-tap toggle that freezes or unfreezes immediately; a home-screen widget shows frozen-app count and opens the freeze surface on tap; works in root, ADB, and Shizuku modes.
  Complexity: M

- [ ] P3 — Scheduled cache/data clearing as routine operation type
  Why: SD Maid SE's scheduled cache-clearing is the #1 feature users associate with automated Android maintenance. NG's RoutineScheduler (v0.6.0 target) already has the executor pattern for scheduled operations but does not include cache or expendable-data clearing as an operation type.
  Evidence: https://github.com/d4rken-org/sdmaid-se (scheduled cache clearing); profiles/RoutineScheduler.java and profiles/RoutineWorker.java (verified: no CLEAR_CACHE operation type); compat/PackageManagerCompat.java (freeStorageAndNotify available for privileged modes)
  Touches: profiles/ (add CLEAR_CACHE and CLEAR_DATA operation types to RoutineScheduler), compat/PackageManagerCompat.java (cache-clearing wrapper), settings/ (per-profile operation type selector)
  Acceptance: a routine can include "clear cache" or "clear expendable data" as an operation, scoped to specific apps or app-set filters; scheduled execution clears cache for matched apps and logs byte counts; requires root or Shizuku privilege; operation type cleanly refused with explanation on no-root mode.

## Deep Audit Follow-ups (2026-06-13)

Deferred from the 2026-06-13 deep engineering audit pass. Fixed items are in
the commit history / CHANGELOG. Items below were verified real but need design
decisions, careful refactoring, or on-device testing.

### P1

- [ ] P1 — Restore clears app data before extraction (crash = total data loss)
  Why: RestoreOp.restoreData() calls clearApplicationUserData before extracting the backup. If extraction fails (corrupted tar, disk full, SAF error), the app's original data is gone with no rollback. Needs a copy-then-swap or rollback mechanism.
  Where: backup/RestoreOp.java:581-598
  Complexity: L

- [ ] P1 — Master-key verification permanently disabled on restore
  Why: RestoreOp.checkMasterKey() starts with `if (true) { return; }` (TODO from 2022). KeyStore integrity is never validated during restore. If the device's KeyStore master key changed (factory reset), restored KeyStore entries may be silently corrupted.
  Where: backup/RestoreOp.java:303-307
  Complexity: M

### P2

- [ ] P2 — Backup commit() has no crash atomicity (delete-then-move)
  Why: BackupItems.commit() deletes the old backup directory before moving temp to final. Process crash between delete and move loses both old and new backup.
  Where: backup/BackupItems.java:520-547
  Complexity: M

- [ ] P2 — DialogFragment LiveData observers use Fragment lifecycle instead of view lifecycle
  Why: BackupRestoreDialogFragment, IconPickerDialogFragment, and RSACryptoSelectionDialogFragment observe LiveData with `this` instead of `getViewLifecycleOwner()`. Observers survive view destruction and can fire UI updates against null views after config changes.
  Where: backup/dialog/BackupRestoreDialogFragment.java:184-186, details/IconPickerDialogFragment.java:74, settings/crypto/RSACryptoSelectionDialogFragment.java:72-83
  Complexity: S

- [ ] P2 — BackupRestoreDialogFragment BroadcastReceiver never unregistered on detach
  Why: mBatchOpsBroadCastReceiver references mActivity and is never unregistered in onDestroyView/onDetach. If the broadcast arrives after fragment detach, the stale activity reference can crash.
  Where: backup/dialog/BackupRestoreDialogFragment.java:129-138
  Complexity: S

- [ ] P2 — Narrow ~140 unjustified catch(Throwable) to catch(Exception)
  Why: ~140 catch blocks catch Throwable around standard library, JSON, file I/O, and UI code that can only throw Exception subclasses. This swallows OOM/StackOverflowError/VirtualMachineError silently. ~73 instances around IPC/hidden API calls are justified (can throw Error subclasses from reflection/binder). Priority files: AppInfoFragment (22), BatchOpsManager (22), BackupRetentionPolicy (6), MainRecyclerAdapter (5), AssistActionActivity (9).
  Where: app/src/main/java/ (89 files, 356 total instances)
  Complexity: L

### P3

- [ ] P3 — requireActivity()/requireContext() in nested dialog and async callbacks
  Why: ~8 MEDIUM-severity instances where requireActivity() is called inside nested dialog button callbacks or adapter bind methods that can fire after fragment detach. Not immediate crashers (requires specific timing) but violate lifecycle safety.
  Where: AdvancedPreferences.java:194-206, AppInfoFragment.java:793/866/894, RestoreSingleFragment.java:242, AppDetailsComponentsFragment.java:1179
  Complexity: S
