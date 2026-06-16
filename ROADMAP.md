<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Product Quality Roadmap (2026-06-12)

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

(All headless-verifiable items from this initiative are completed or moved
to `Roadmap_Blocks.md` for device/maintainer-gated work.)

## Research-Driven Additions

### P2

- [ ] P2 — Permission/app-op reference states (desired-vs-actual drift)
  Why: PermissionManagerX's reference-state model (pin desired value per permission/app-op, surface drift, restore references) is the only audit-grade permission pattern in the ecosystem and slots into NG's existing rule store + Permission Inspector.
  Evidence: https://github.com/mirfatif/PermissionManagerX (README, verified)
  Touches: rules/RulesStorageManager.java, rules/struct/, permissions/ (Inspector drift badges), details/AppDetailsPermissionsFragment
  Acceptance: user pins a reference for a permission/app-op; subsequent drift shows a visible indicator in Permission Inspector with one-tap restore-to-reference; references survive app reinstall via the rule store.
  Complexity: L


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

### P2


- [ ] P2 — Narrow remaining unjustified catch(Throwable) to catch(Exception)
  Why: catch blocks catch Throwable around standard library, JSON, file I/O, and UI code.
  This swallows OOM/StackOverflowError/VirtualMachineError silently. ~55 instances around
  IPC/hidden API calls are justified and intentionally kept broad.
  Where: app/src/main/java/ (~40 remaining files with narrowable catches)
  Progress 2026-06-16: narrowed ~170 catches across 92 files in a comprehensive pass
  covering backup ops (BackupOp, RestoreOp, VerifyOp, converters), all ViewModels
  (AppDetailsViewModel, AppInfoViewModel, RunningAppsViewModel, MainViewModel, ScannerViewModel,
  OneClickOpsViewModel, etc.), UI fragments (OnboardingFragment, AppDetailsPermissionsFragment,
  OpHistoryActivity, etc.), workers/monitors (AutoBackupWorker, PermissionChangeMonitor,
  RoutineWorker, etc.), activities (AssistActionActivity, TermActivity, AutomationReceiver,
  etc.), and database/utility files. Remaining ~115 catches are in IPC bridges (ShizukuBridge,
  DhizukuBridge), hidden API compat layers (PackageInstallerCompat, AppOpsManagerCompat,
  PermissionToggleHelper), privileged services (ComponentsBlocker, FreezeUnfreezeService),
  framework-boundary code (Ops, ExUtils), and a handful of mixed files (BatchOpsManager,
  BatchOpsService, PackageInstallerActivity) where individual-site triage is needed.
  Complexity: S (remaining)

### P3



