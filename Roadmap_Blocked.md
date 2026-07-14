<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Blocked Roadmap Items

Items moved here from ROADMAP.md because they cannot be completed without
device access, external services, or explicit design decisions. Move back to
ROADMAP.md once the blocker is resolved.

## Device/Privileged-Mode-Gated

### P0

- [ ] P0 — Replace destructive AppsDb fallback with a complete, preservation-tested migration ladder
  Why: schema 10 has no 1→2 migration and enables destructive forward and downgrade fallbacks even though the database contains filters, history, favorites, freeze choices, and backup state that cannot all be rebuilt.
  Evidence: `db/AppsDb.java`; `app/schemas/io.github.muntashirakon.AppManager.db.AppsDb/1.json` through `10.json`; Android Room migration documentation; current `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java` coverage.
  Touches: `db/AppsDb.java` (add 1→2 and remove unrestricted destructive fallbacks), `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java` (every start version through current plus downgrade/failure cases), exported Room schemas, database-open recovery UI/logging.
  Acceptance: fixtures from every schema version 1–10 open at the current schema and preserve representative rows from every durable table; an unknown/missing path or downgrade fails closed with the original DB copied intact to a recoverable location and never recreates tables silently.
  Blocker: Room migration verification runs as an instrumented `androidTest` (`MigrationTestHelper` needs a device/emulator), and removing `fallbackToDestructiveMigration()` without on-device upgrade testing risks a crash-loop on real devices. The fail-closed DB-open recovery path also needs runtime verification. Not host-verifiable offline.
  Complexity: M

### P1

- [ ] P1 — Validate Android 17 app-list enumeration on-device
  Why: the structural fix has landed (PackageManagerCompat now routes API 37+
  through the paginated `PackageInfoList`/`IPackageManagerV37` path, ported from
  upstream `836c7248ea`, host-verified by compile + contract test). Only runtime
  validation of the real paginated binder call remains, plus a recheck of
  Shizuku/Dhizuku binding on API 37.
  Evidence: main/MainViewModel.java; compat/PackageManagerCompat.java
  (`getInstalledPackagesInternal` API-37 branch); servermanager/ privileged-mode
  binding.
  Acceptance: on an API-37 emulator or device, the main list enumerates the
  same package set as API 36; root/Shizuku/Dhizuku bind successfully or fail
  with surfaced, actionable reasons; local smoke coverage asserts a non-empty
  package list.
  Blocker: requires Android 17/API-37 emulator or device runtime access.
  Complexity: S

- [ ] P1 — Root-detection retune for 2026 root managers
  Why: upstream #1967 reports root not detected on Android 16, while Magisk
  30.7 now preserves capabilities by default and KernelSU-Next moved probe
  paths again.
  Evidence: upstream MuntashirAkon/AppManager#1967; Magisk 30.7 release notes;
  runner/RootManagerInfo.java.
  Acceptance: root is detected on Android 16 with current Magisk and
  KernelSU-Next; drop-cap diagnostics give correct guidance for Magisk 30.7+;
  probe matrix is documented.
  Blocker: requires rooted Android 16 emulator or device with current Magisk
  and KernelSU-Next.
  Complexity: M

- [ ] P1 — Backup/restore round-trip integration tests
  Why: backup/restore has the highest reliability risk and still lacks
  end-to-end runtime coverage for no-crypto and AES paths.
  Evidence: backup/adb/AndroidBackupHeader.java; backup/RestoreOp.java; test
  coverage gaps in backup round-trip behavior.
  Acceptance: a local emulator suite installs a fixture app, backs it up
  with no-crypto and AES modes, uninstalls it, restores it, and asserts data
  equality.
  Blocker: requires a local emulator/device with enough storage for runtime
  backup round trips.
  Complexity: M

- [ ] P1 — Port upstream restore fixes from v4.1.0 milestone
  Why: upstream v4.1.0 planning includes restore fixes such as non-root
  restore SecurityException handling on Samsung/API 34; NG's restore path
  predates those fixes.
  Evidence: upstream MuntashirAkon/AppManager#1286; backup/RestoreOp.java;
  apk/installer/; compat/PackageManagerCompat.java.
  Acceptance: the #1286 reproduction succeeds; ported fixes are listed in
  CHANGELOG.md with upstream attribution.
  Blocker: upstream v4.1.0 shipped 2026-06-29, so the restore commits are now
  available to port, but the #1286 reproduction and final restore behavior
  need Samsung/API 34 device verification — restore is the highest data-loss
  risk path and must not ship unexercised on an offline host.
  Complexity: M

- [ ] P1 — Port HMAC mutual auth and native run_server for the local privileged channel
  Why: upstream hardened the app-to-ADB-server channel with HMAC
  challenge-response and a native run_server executable; NG should evaluate
  that path as part of privileged local-server secure-session hardening.
  Evidence: upstream commits 88eb453, 07c7199, b42efbb, f8d3126; libserver/;
  server/; adb/; servermanager/.
  Acceptance: unauthenticated server connections are rejected by negative
  tests; root mode still works on a rooted Android 16 runtime; ported commits
  are attributed.
  Blocker: requires rooted device/emulator privileged-mode verification and
  overlaps the broader secure-session trust-model decision below.
  Complexity: M

### P2

- [ ] P2 — Dhizuku freeze/suspend executor parity
  Blocker: requires Dhizuku device-owner delegation on a device.
  Complexity: M

- [ ] P2 — Wireless-ADB resilience: trusted-network auto-reconnect and pairing-state surface
  Blocker: requires on-device ADB connection lifecycle testing.
  Complexity: L

- [ ] P2 — SAF DocumentsProvider exposure of app-private directories
  Blocker: requires privileged mode plus third-party SAF file-manager testing
  on device.
  Complexity: M

- [ ] P2 — Backup overwrite option for custom-name collisions
  Why: move-aside and rollback semantics differ between file-backed and
  SAF-backed Path implementations.
  Blocker: requires SAF storage verification on device.
  Complexity: M

- [ ] P2 — ApplicationStartInfo "why did this app start" panel
  Blocker: requires API 35+ device/emulator runtime verification.
  Complexity: M

- [ ] P2 — Assistant-launched privileged services and broadcasts without root
  Blocker: requires WRITE_SECURE_SETTINGS mode on device.
  Complexity: M

- [ ] P2 — Form-factor-aware permission prompt gate
  Blocker: requires TV and Wear emulator verification.
  Complexity: M

### Historical INIT Carry-Forward

- [ ] INIT-D1 — Full main-list ListAdapter/DiffUtil migration
  Blocker: View-ID preservation needs on-device verification.

- [ ] INIT-D3 — HMAC mutual auth and native run_server port
  Blocker: same as P1 local privileged channel hardening above.

- [ ] INIT-D5 — Backup round-trip emulator coverage
  Blocker: same as P1 backup/restore round-trip integration tests above.

- [ ] INIT-2b — Backup overwrite-option UI and move-aside for custom-name collisions
  Blocker: same as P2 backup overwrite option above.

- [ ] INIT-4b — Analytics/discovery dashboard screen
  Blocker: same as visual/device-gated analytics dashboard below.

## Device/Design-Verification-Gated

### P2

- [ ] P2 — Accessibility, keyboard, and touch-target hardening
  Why: dense expert tools still need predictable focus, visible labels, 48dp controls, and
  non-color-only status meaning across dialogs, lists, chips, menus, and result screens.
  Where: app/src/main/res/layout/, app/src/main/java/io/github/muntashirakon/AppManager/
  Blocker: requires on-device a11y scanner, TalkBack, and keyboard navigation testing to identify specific violations before fixing.
  Complexity: M

- [ ] P2 — Degraded, empty, error, loading, and success state system
  Why: secondary screens should never fail silently or show blank states; every unavailable,
  partial, or failed workflow needs calm actionable copy and an obvious next step.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/, app/src/main/res/layout/
  Blocker: requires systematic on-device walkthrough of each screen's error/empty/loading paths to identify gaps.
  Complexity: L

- [ ] P2 — Settings information architecture cleanup
  Why: settings should group risk, privileges, appearance, backup, rules, notifications, and
  advanced/debug controls so users can find decisions without memorizing implementation
  boundaries.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/settings/
  Blocker: current 4-category structure is reasonable; needs UX review on device to identify specific reorganization targets.
  Complexity: M

### P3

- [ ] P3 — Clickable main-list badges have <48dp touch targets
  Why: `tracker_indicator` and `perm_indicator` are clickable (`setClickable(true)` + click listener) but render at `premium_badge_min_height/width` = 24dp with no TouchDelegate. Two clickable badges share one `FlowLayout` parent, so a single `TouchDelegate` (one target rect per view) can't cover both.
  Evidence: MainRecyclerAdapter.java:420-422,457-459; item_main_v2.xml:115-116,136-137; dimens-v2.xml:76-77
  Touches: MainRecyclerAdapter.java (composite touch delegate on the badge row), item_main_v2.xml
  Acceptance: each clickable badge has a >=48dp effective hit rect via a composite/multi-target TouchDelegate posted on the parent FlowLayout (visual size stays 24dp); a11y scanner clean. (Needs on-device touch verification.)
  Progress 2026-06-12: fixed the nested coordinate translation bug in the
  composite badge TouchDelegate, added a Robolectric regression test, installed
  the Floss debug build on the emulator, and captured the main-list badge
  surface. Remaining: clean a11y scanner confirmation after the emulator
  UiAutomation service recovers.
  Blocker: emulator UiAutomation service recovery needed for a11y scanner confirmation.
  Complexity: S

- [ ] P3 — Sibling list rows diverge from the V2 card treatment
  Why: ~15 list-row layouts (e.g. item_debloater.xml) still use the classic `Widget.AppTheme.CardView.ListItem.Outlined` (bg `?colorSurface`, elevation 0) while the main list uses `Widget.AppTheme.V2.Card.ListRow` (bg `?colorSurfaceContainerLow`, 1dp elevation, hairline stroke), so adjacent NG screens render visibly different card surfaces.
  Evidence: layout/item_debloater.xml:4 (+ ~14 sibling row layouts); themes-v2.xml V2.Card.ListRow
  Touches: app/src/main/res/layout/item_*.xml
  Progress 2026-06-12: migrated the shared sibling list rows, scanner cards,
  profile-review warnings, empty states, batch failure rows, and secondary
  toolbars onto V2 premium tokens; dark-mode emulator screenshots pass for the
  mode sheet, onboarding guides, and populated main list.
  Acceptance: NG list rows share the V2 card treatment; spot-checked across debloater / permission / one-click lists in light, dark and AMOLED. (Visual -- needs on-device verification.)
  Blocker: on-device visual verification required across light/dark/AMOLED.
  Complexity: S

- [ ] P3 — D-pad/TV navigation pass + Android TV banner
  Why: Upstream #107 (keyboard/remote navigation, "Partly Fixed") plus SD Maid SE's Android TV launcher support show the box-tinkerer segment is real (FireOS/Firestick issues already appear upstream: #1835, #1854); NG's M3 dashboard was not audited for focus traversal.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/107 ; SD Maid SE releases (TV support); upstream #1835/#1854 (FireOS users)
  Touches: app/src/main/res/ (focus order, leanback banner, manifest LEANBACK feature flags), main/, details/
  Acceptance: main list -> app details -> batch ops are fully operable with a D-pad on an Android TV emulator; app appears in the TV launcher with a banner.
  Blocker: requires Android TV emulator or device for focus-traversal testing and banner validation.
  Complexity: M

- [ ] P3 — Transparent launch-through frozen apps
  Why: Hail's killer UX pattern: tap a frozen app's launcher icon → auto-unfreeze → launch → auto-refreeze when the app closes or screen locks. NG has freeze/unfreeze plumbing and auto-freeze-on-screen-lock, but no transparent launch-through.
  Evidence: Hail (6k stars) — transparent launch is its most-cited feature; NG freeze data layer + screen-lock receiver already landed
  Touches: main/ (launcher shortcut handling), freeze/ (unfreeze-then-launch flow), auto-freeze receiver (refreeze-on-close trigger)
  Blocker: freeze/unfreeze toggle requires root, ADB, or Shizuku privilege modes for device testing; refreeze-on-app-close detection needs UsageStatsManager or ActivityLifecycleCallbacks testing on device.
  Complexity: M

- [ ] P3 — Samsung "Clear Compiler Artifacts" batch operation
  Why: Upstream #1989 requests batch dexopt/ART profile clearing on Samsung devices. Samsung One UI shows this option per-app but no package manager exposes it as a batch operation.
  Evidence: upstream MuntashirAkon/AppManager#1989; Samsung One UI storage management
  Touches: batchops/BatchOpsManager.java, compat/ (Samsung dexopt clearing API)
  Blocker: requires Samsung device to test `cmd package compile` and `pm bg-dexopt-job` behavior and verify the correct shell commands for clearing compiler artifacts.
  Complexity: S

- [ ] P2 — Multi-user/work-profile/private-space capability matrix
  Why: AppManagerNG has broad userId plumbing and hidden-profile permission support, but users need one visible source of truth for which operations work in main, work, hidden, and private profiles under each privilege mode.
  Evidence: app/src/main/AndroidManifest.xml; docs/policy/permissions.md; Canta work-profile issue; Neo Backup multi-profile issue.
  Blocker: requires multi-user/work-profile device or emulator to test capability detection and action guards across profile boundaries.
  Complexity: L

- [ ] P2 — File-manager operations service with cancellation and recovery
  Why: Copy, move, delete, and archive work is still tied to fragment/background-thread flows even though the code calls out a bound service and cancellable thread ownership as needed.
  Evidence: fm/FmFragment.java; fm/FmAdapter.java.
  Blocker: foreground service behavior, rotation survival, and partial-move recovery need device testing; large architectural change touching the FM core.
  Complexity: L

## Security-Design-Gated

### P1

- [ ] P1 — Privileged local-server secure-session hardening
  Why: The privileged server uses a cleartext socket authenticated by a per-session token, while the code itself notes SSL as a future hardening path and Android 17/local ADB changes raise the cost of ambiguous local transport boundaries.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java`; `docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`; Android 17 local-network behavior docs; Shizuku limitation docs.
  Touches: local server client/server handshake; `ServerConfig`; server connection tests; security docs.
  Acceptance: the trust model is explicit; loopback-only sessions are enforced where possible or non-loopback sessions use per-session authenticated encryption/TLS; wrong host, wrong token, and replayed session attempts are rejected by tests.
  Blocker: requires explicit trust-model design decisions and touches native server bootstrap; device-gated for privileged-mode testing.
  Complexity: L

## Visual/Device-Verification-Gated (2026-06-17)

### P2

- [ ] P2 — Replace legacy storefront screenshots with deterministic current NG captures
  Why: all nine Fastlane phone images predate the v0.6.x UI and show upstream-era identity/data, weakening F-Droid listing accuracy and release trust even though store graphics are an explicit F-Droid discovery input.
  Evidence: `fastlane/metadata/android/en-US/images/phoneScreenshots/{1..9}.png`; current V2 layouts/themes; F-Droid graphics and screenshots documentation.
  Touches: Fastlane phone screenshots, a privacy-safe debug/demo data fixture or capture setup, Fastlane metadata, release-consistency image checks.
  Acceptance: nine 1080×2160 captures show current NG onboarding, app list, app details, permissions/AppOps, scanner, backup/restore, installer preflight, file manager, and settings/search across both themes; no upstream package identity, real user/device data, stale version copy, clipping, or unreadable contrast remains; dimensions and count are mechanically validated.
  Blocker: capturing the nine screens requires running the app on a device/emulator across light and dark themes; not producible on an offline host. (The mechanical dimension/count validation half can ship with the release-consistency gate.)
  Complexity: M

### P3

- [ ] P3 — Visual token and component polish pass
  Why: cards, banners, list rows, dialogs, badges, chips, toasts, and nested surfaces should
  feel like one product in light, dark, and AMOLED modes without one-off colors or spacing.
  Where: app/src/main/res/{layout,values,drawable}/
  Blocker: requires on-device visual comparison across light, dark, and AMOLED themes to identify specific one-off colors and spacing inconsistencies before fixing.
  Complexity: M

- [ ] P3 — Tooltips and microcopy consistency pass
  Why: expert controls need concise labels, explainers, and warnings that are useful without
  being robotic, vague, or inconsistent between screens.
  Where: app/src/main/res/values/strings.xml, app/src/main/java/io/github/muntashirakon/AppManager/
  Blocker: requires on-device walkthrough of all expert controls to identify specific vague/inconsistent labels and warnings before rewriting.
  Complexity: M

- [ ] P3 — Theme/a11y coherence pass (deferred-audit visual debt)
  Why: The 2026-06-09 audit verified divergent dark palettes across NG-added screens, dead premium design tokens, and tracker/perm badges under the 48dp touch-target minimum — small fixes that compound into perceived quality.
  Evidence: 2026-06-09 audit session record (deferred list); res/ themes and the named drawables (spot-verified)
  Touches: app/src/main/res/ (themes, drawables, dimens), details/ badge layouts
  Acceptance: NG-added screens share one dark palette token set; the misused drawables are replaced with purpose-named assets; all interactive badges hit ≥48dp touch targets (a11y scanner clean on those screens).
  Blocker: requires a11y scanner and on-device dark-palette comparison to identify specific divergent tokens before fixing.
  Complexity: M

- [ ] P3 — Device-wide analytics dashboard (install-source / SDK / signing distributions)
  Why: Inure's analytics panel and AppDash's insight cards ("unused apps", "storage-heavy") are the category's stickiest discovery surfaces; NG already computes every datapoint (installer source, target SDK, signing info, usage) but offers no aggregate view with tap-through to a filtered list.
  Evidence: https://github.com/Hamza417/Inure (FEATURES.md analytics panel); https://appdash.app/ (insight cards); NG filters already support these predicates (filters/options/)
  Touches: main/ or a new dashboard fragment, filters/ (reuse predicates as tap-through), existing chart utilities
  Acceptance: a dashboard screen shows at least installer-source, targetSdk, and signing distributions plus an "unused 30/60/90 days" card; tapping any segment opens the main list pre-filtered to it.
  Blocker: chart rendering and tap-through UX require on-device visual verification; no chart library is currently in the project.
  Complexity: M

- [ ] P3 — Dedicated freeze surface with home-screen widget
  Why: Freeze/unfreeze works via app details or batch ops, but there is no dedicated screen listing all frozen apps with one-tap toggle — the feature is buried. Hail's frozen-apps grid with one-tap toggle and home-screen widget is the competitive standard for daily freeze/unfreeze workflows. NG ships a QS freeze tile but no in-app freeze surface or widget.
  Evidence: https://github.com/aistra0528/Hail (freeze grid, widget, grayscale icons); main/MainActivity.java (frozen filter exists but no dedicated freeze fragment); QuickFreezeTileService (QS tile only, no widget)
  Touches: new FreezeManagerFragment under main/ (reuse existing freeze/unfreeze plumbing from batchops/), new AppWidgetProvider for home-screen toggle, main menu entry, app/src/main/res/layout/ (grid layout), app/src/main/res/xml/ (widget metadata)
  Acceptance: a main-menu entry opens a grid of all frozen/suspended apps; each row has a one-tap toggle that freezes or unfreezes immediately; a home-screen widget shows frozen-app count and opens the freeze surface on tap; works in root, ADB, and Shizuku modes.
  Blocker: freeze/unfreeze toggle and widget require root, ADB, or Shizuku privilege modes for device testing; AppWidgetProvider requires launcher interaction.
  Complexity: M

- [ ] P3 — Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Evidence: profiles/struct/BaseProfile.java (serializeToJson exists, no QR/deep-link codec)
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Blocker: no QR generation library in project; adding ZXing/MLKit dependency needed; deep-link handling requires on-device intent-filter testing.
  Complexity: M

- [ ] P3 — Boot-component manager view
  Why: A dedicated "what starts at boot" surface (BOOT_COMPLETED receivers across all apps, batch-blockable) is a classic MyAndroidTools/Inure feature NG can build almost entirely from existing component-blocking plumbing.
  Evidence: Inure boot manager; Blocker component search; existing rules/compontents/ IFW/disable paths
  Touches: new BootManagerActivity (reuse component list UI), rules/compontents/ (existing IFW/disable paths), filters/, main menu entry
  Acceptance: a screen lists every app with BOOT_COMPLETED/LOCKED_BOOT_COMPLETED receivers and their enable state; per-row and batch block/unblock work through the existing rule store with undo.
  Blocker: per-row and batch IFW component blocking requires root/ADB/Shizuku privileged-mode device testing; full Activity + ViewModel + Adapter + layout creation is a multi-file feature.
  Complexity: M

- [ ] P3 — File-manager trash bin (staged deletion)
  Why: NG's FM hard-deletes; Files-by-Google's staged trash with 30-day retention is the established data-safety pattern and FM batch ops magnify mistake cost.
  Evidence: Files by Google clean-flow walkthrough (RESEARCH.md Sources); fm/ has no trash concept (verified)
  Touches: fm/ (delete paths, trash root, restore UI), settings/ (retention pref)
  Acceptance: FM delete moves to a trash location with restore; trash auto-empties after the configured retention; "delete permanently" remains available.
  Blocker: restore UI flow, trash browsing, and auto-empty behavior need on-device testing; touches critical delete paths.
  Complexity: M

## Compose-Dependency-Gated

### P2

- [ ] P2 — Screenshot regression testing (Paparazzi)
  Why: 143 layouts, 3 themes (AMOLED/dark/light), ongoing V2 design token work create continuous visual regression risk with no automated catching.
  Evidence: Roborazzi blocked by Compose transitive deps. Paparazzi (cashapp/paparazzi) is the best Views-compatible alternative — JVM-only, Material Components rendering works since v1.2+, no Compose dependency needed.
  Touches: app/build.gradle, app/src/test/ (screenshot test classes)
  Blocker: Paparazzi 2.0.0-alpha05.2 (AGP 9.x support, PR #2318) is not
  published as of 2026-06-27. No released Paparazzi version works with AGP
  9.2.1. Retry after the milestone ships.
  Complexity: M

## Design-Decision-Gated

### P3

- [ ] P3 — DDG Tracker Radar as supplementary tracker source
  Why: TrackerControl layers DuckDuckGo's mobile-specific tracker database on top of Exodus for broader coverage. DDG Tracker Radar is MIT-licensed and maintained by DuckDuckGo.
  Evidence: TrackerControl multi-source approach; DDG Tracker Radar mobile TDS at staticcdn.duckduckgo.com
  Touches: scanner/ (new domain matching pass), app/src/main/assets/ (DDG data), StaticDataset.java, ScannerViewModel.java
  Blocker: DDG Tracker Radar is domain-based (tracking which network domains apps contact), while NG's scanner is class-name-based (Aho-Corasick matching against Exodus code signatures). Integration requires a design decision: (a) static domain extraction from DEX string pool + manifest (imprecise, high false-positive risk), (b) runtime network monitoring via VPN/firewall (scope creep for a package manager), or (c) build-time cross-reference of DDG entities with existing libs.json entries to enrich metadata without new detection (low value). The libs.json `network` signature field already exists but is unused at runtime.
  Complexity: M

## Dependency-Gated

### P3

- [ ] P3 — APK Signature Scheme v3.2 display
  Why: Android 17 ships hybrid PQC signing (ML-DSA + classical). The signing cert chip should show "v3.2 (PQC)" for hybrid-signed APKs when the detection API is available.
  Evidence: PackageUtils.java:882 TODO comment; Android 17 PQC upgrade docs
  Touches: utils/PackageUtils.java (getSignerInfo), details/info/ (signing cert display), apksig dependency
  Blocker: apksig-android (MuntashirAkon/apksig-android) does not yet expose `isVerifiedUsingV32Scheme()`. The one-line change is ready — gate behind a version check when upstream ships it.
  Complexity: S

## External-Service-Gated

### P2

- [ ] P2 — Fork-owned translation pipeline and NG-string catch-up
  Why: translation intake needs a hosted Weblate or Crowdin project before
  repo-side configuration has a real destination.
  Evidence: README.md translation note; current hosted project is not live.
  Blocker: requires maintainer/account action to create and operate the
  hosted translation service.
  Complexity: M

### P3

- [ ] P3 — IzzyOnDroid repository submission
  Why: Fastlane metadata is already complete (title, descriptions, icon, 9 screenshots, changelogs); IzzyOnDroid is the fastest path to F-Droid ecosystem visibility in Neo Store and Droid-ify clients.
  Evidence: fastlane/metadata/android/en-US/ (complete); IzzyOnDroid inclusion policy; upstream App Manager is already listed.
  Touches: fastlane/metadata/android/en-US/ (verify currency), docs/distribution/ (submission checklist), README.md (add badge after listing)
  Blocker: requires filing a submission request at codeberg.org/IzzyOnDroid/repo — operator action, not code.
  Complexity: S



## Device/Privileged-Mode-Gated (2026-07-14 research)

### P2

- [ ] P2 — Conditional auto-freeze predicates (skip charging / foreground / active notifications)
  Why: Hail's loudest 2026 complaint is that unconditional auto-freeze kills apps users still want alive; conditional guards (skip while charging, skip the foreground app, skip apps with active notifications) are the requested fix and fit NG's routine-ops model.
  Evidence: Hail issues #186/#222 (auto-freeze reliability + skip guards); NG routine/scheduler executor.
  Touches: routines/ (new predicate types), freeze/ (guard evaluation), settings/ (defaults).
  Acceptance: a routine can freeze a package set except while charging, except when foreground, or except when it holds an active notification; each guard verified against the real system state.
  Blocker: freeze execution and foreground/notification/charging state require root/ADB/Shizuku and on-device runtime verification.
  Complexity: M

- [ ] P2 — Event-driven leftover sweep after any uninstall (CorpseFinder pattern)
  Why: NG's LeftoverScanner is manual; SD Maid SE's CorpseFinder offers to sweep orphaned data dirs immediately after any uninstall (including uninstalls by other apps), which is when the mistake cost is highest.
  Evidence: SD Maid SE changelog (CorpseFinder); NG LeftoverScanner (manual/one-click only).
  Touches: new PACKAGE_FULLY_REMOVED receiver, backup/scanner/LeftoverScanner reuse, notification + confirm flow.
  Acceptance: uninstalling an app (or observing another app's uninstall) surfaces an actionable "found N orphaned dirs — review" prompt that routes into the existing leftover UI.
  Blocker: orphaned-dir enumeration and deletion require root/privileged mode and on-device broadcast-timing verification.
  Complexity: M

### P3

- [ ] P3 — Installer preflight: show the initiating package and select-all splits
  Why: InstallerX-Revived surfaces which app requested an install and offers select-all in the split chooser; both are transparency/UX wins NG's SplitApkChooser lacks.
  Evidence: InstallerX-Revived 2026 repo/docs; NG apk/installer/ + SplitApkChooser.
  Touches: apk/installer/ (SessionInfo initiator lookup), SplitApkChooser (select-all).
  Acceptance: the install preflight shows the originating/installer package where available and offers a select-all toggle for split sets.
  Blocker: PackageInstaller SessionInfo initiator data and install flow require on-device verification across API levels.
  Complexity: S

- [ ] P3 — Persistent removal/debloat ledger surface
  Why: Canta keeps a durable record of what the user removed that survives across reinstalls of the manager; NG has op_history but no dedicated "what I removed and when" audit view users can revisit or restore from.
  Evidence: Canta 2026 (uninstall history); NG op_history/db.
  Touches: new removal-ledger fragment (reuse op_history), db/ (query), main menu entry.
  Acceptance: a screen lists removed/debloated packages with timestamp and mode, and offers reinstall/restore where data allows.
  Blocker: reinstall/restore actions require privileged mode; ledger persistence across reinstall needs on-device verification.
  Complexity: M

- [ ] P3 — Version-watch panel (full flavor): installed vs latest from static indexes
  Why: APKUpdater (3.8k stars, active) proves demand for multi-source update awareness without being a store; AppDash paywalls it; checking F-Droid/IzzyOnDroid index-v2 + GitHub releases against installed versions fits the full flavor's opt-in network doctrine and NG stays a manager (notify, don't install).
  Evidence: https://github.com/rumboalla/apkupdater ; https://appdash.app/ ; f-droid index-v2 format (RESEARCH.md Sources)
  Touches: full-flavor source set (new updates/ package), settings/PrivacyPreferences (opt-in + source toggles), WorkManager scheduled check
  Acceptance: with the toggle on, a scheduled check lists apps whose installed version trails the chosen indexes, with a signing-cert mismatch warning where the index cert differs; floss flavor compiles the feature out entirely.
  Blocker: requires network access to F-Droid/IzzyOnDroid index-v2 and GitHub release APIs for testing.
  Complexity: L
