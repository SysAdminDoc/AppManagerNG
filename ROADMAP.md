# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Issue Drain Left Open (2026-11-06)

The drain stopped here on the owner's word. These reports are still waiting.

- [ ] P2: Check on a real phone: wireless debugging and ADB over TCP (issue #20), and that usage access isn't asked for again once granted (issue #16). The fixes for #12, #13, #17, #18, #23 and #28 have only run on an API 29 emulator. The features for #19, #25 and #26 and the profile search and sort from PR #24 have only run in tests. For #21, install an APK through Open with in no-root mode and confirm the system prompt shows instead of the spinner, then uninstall an app the same way. For #27, turn on Record toasts with the accessibility service and check that another app's toast lands with its text, app and time. For #15, deny CAMERA on a test app, grant it in system Settings, tap Check now and confirm root or ADB mode takes it back; also the Always allow and Always deny buttons, the App Details long press, the check after a restart and the scheduled job in `dumpsys jobscheduler`. For #22, tap Share this device on one phone and connect from the other (type the address and port when one side is an emulator, since discovery doesn't cross its NAT), check that both show the same six digits and Allow, then check the buckets, a Get install, a "Signature differs" row, Update all newer, Remember and Forget, and that leaving the screen or rotating stops sharing. For the installer prompt fix, send an APK with the batch extra from `adb shell am start` in ADB mode and confirm the prompt still shows.
- [ ] P2: Reply on #12, #13, #15, #16, #17, #18, #19, #20, #21, #22, #23, #24, #25, #26, #27 and #28 once their device checks pass.

## Unblocked from Roadmap_Blocked.md (2026-11-07)

- [ ] P0: Replace destructive AppsDb fallback with a complete, preservation-tested migration ladder
  Why: schema 12 still has no 1→2 migration and still enables destructive forward and downgrade fallbacks, though the database holds filters, history, favorites, freeze choices and backup state that can't all be rebuilt.
  Evidence: `db/AppsDb.java` (version 12, `.addMigrations(M_2_3 ... M_11_12)` at line 144, both destructive fallbacks at lines 151 and 152), exported schemas `1.json` through `12.json`, `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java`.
  Touches: `db/AppsDb.java` (add 1→2 and remove unrestricted destructive fallbacks), `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java` (every start version through current plus downgrade/failure cases), exported Room schemas, database-open recovery UI/logging.
  Acceptance: fixtures from every schema version 1 through 12 open at the current schema and keep representative rows from every durable table. An unknown or missing path, or a downgrade, fails closed with the original DB copied intact to a recoverable location and never recreates tables silently.
  Unblocked 2026-11-07: `MigrationTestHelper` runs on the API 23 to 37.1 emulator images on disk and on the S22 and S25, so the upgrade and fail-closed checks can run now.
  Complexity: M

- [ ] P1: Validate privileged-mode binding on Android 17
  Why: app-list enumeration on API 37 is verified and pinned by `PackageEnumerationInstrumentedTest`. What remains is whether Shizuku, Dhizuku, and root bind on API 37, or fail with a surfaced, actionable reason.
  Evidence: servermanager/ privileged-mode binding; settings/Ops.java.
  Acceptance: on API 37, each privileged mode either binds or reports why in the UI rather than failing silently.
  Today: Shizuku's newest release is still v13.6.0 (2025-05-25) and its Android 17 reports #1965 and #1988 are open, so a Shizuku failure on API 37 that shows its reason in the UI meets the bar.
  Unblocked 2026-11-07: the API 37.0 and 37.1 images are on disk and should root with Magisk 31.1 the way the API 36 image did (not tried yet), Shizuku 13.6.0 sideloads and starts over ADB, and Dhizuku 2.13.0 (2026-10-30) gets device owner through `dpm set-device-owner` on an emulator with no accounts.
  Complexity: S

- [ ] P1: Root-detection retune for 2026 root managers
  Why: upstream #1967 reports root not detected on Android 16, while Magisk 30.7 now preserves capabilities by default and KernelSU-Next moved probe paths again.
  Evidence: upstream MuntashirAkon/AppManager#1967; Magisk 30.7 release notes; runner/RootManagerInfo.java.
  Acceptance: root is detected on Android 16 with current Magisk and KernelSU-Next; drop-cap diagnostics give correct guidance for Magisk 30.7+; probe matrix is documented.
  Today: #1967 closed with upstream v4.1.1, and NG already carries the KernelSU re-grant recovery from it (CHANGELOG, 2026-06-04). Magisk is now at v31.1 (2026-10-25), so the probe matrix should cover 31.1 too.
  Unblocked 2026-11-07: the rooted API 36 Magisk AVD on disk gives a real Android 16 root runtime for the Magisk half today. The KernelSU-Next half needs a kernel it can load into, which is setup work, not a missing resource.
  Complexity: M

- [ ] P1: Backup/restore round-trip integration tests
  Why: backup/restore has the highest reliability risk and still lacks end-to-end runtime coverage for no-crypto and AES paths.
  Evidence: backup/adb/AndroidBackupHeader.java; backup/RestoreOp.java; test coverage gaps in backup round-trip behavior.
  Acceptance: a local emulator suite installs a fixture app, backs it up with no-crypto and AES modes, uninstalls it, restores it, and asserts data equality.
  Today: `app/src/androidTest` has seven classes and none of them runs a backup round trip. This also covers INIT-D5 (backup round-trip emulator coverage).
  Unblocked 2026-11-07: every emulator image on disk has room for a fixture-app backup and restore, so this is test work with nothing missing.
  Complexity: M

- [ ] P1: Port upstream restore fixes from v4.1.0 milestone
  Why: upstream v4.1.0 planning includes restore fixes such as non-root restore SecurityException handling on Samsung/API 34; NG's restore path predates those fixes.
  Evidence: upstream MuntashirAkon/AppManager#1286; backup/RestoreOp.java; apk/installer/; compat/PackageManagerCompat.java.
  Acceptance: the #1286 reproduction succeeds; ported fixes are listed in CHANGELOG.md with upstream attribution.
  Today: upstream v4.1.0 (2026-06-29) and v4.1.1 (2026-09-04) are both out, and the `backup/RestoreOp.java` history shows no upstream restore port yet.
  Unblocked 2026-11-07: the fixes are public to port, the S22 and S25 are Samsung phones on current One UI, and the API 34 emulator image covers the API 34 half of the reproduction.
  Complexity: M

- [ ] P1: Port upstream's native run_server for the local privileged channel
  Why: the HMAC half is done. Protocol 1.4.0 (fcbb19030) proves the token both ways without sending it, and `DataTransmissionHandshakeTest` covers the negative cases. Upstream's native run_server executable is what's left.
  Evidence: upstream commits 07c7199 (run_server as a native executable, 2026-05-28) and b42efbb (main.jar copied through run_server). NG still launches through `app/src/main/assets/run_server.sh` and `servermanager/AdbLaunchFiles.java`, and its server has run apart from upstream's since 6e8f45d31 (port 62001 plus user ID, process `amng_server`).
  Touches: libserver/, server/, adb/, servermanager/.
  Acceptance: root mode still starts the server on a rooted Android 16 runtime through the native launcher, ADB mode still starts it, and the ported commits are attributed.
  Merged: INIT-D3 (HMAC mutual auth and native run_server port).
  Unblocked 2026-11-07: the upstream commits are public, and the rooted API 36 Magisk AVD on disk is the Android 16 root runtime the acceptance asks for.
  Complexity: M

- [ ] P1: Privileged local-server secure-session hardening
  Why: the server channel is still a cleartext loopback socket. Protocol 1.4.0 added mutual HMAC proof of the session token, but no doc states the trust model and no test pins the loopback bind.
  Evidence: `server/src/main/java/io/github/muntashirakon/AppManager/server/Server.java:262` binds `127.0.0.1`. `DataTransmissionHandshakeTest` already rejects a wrong token, a squatting server, a replayed nonce and old handshakes. No trust-model doc exists under `docs/`.
  Touches: security docs, server connection tests.
  Acceptance: a security doc states the trust model (loopback only, mutual HMAC, no TLS, and why), and a host test fails if the server binds anything other than loopback.
  Unblocked 2026-11-07: the design call is already made in the code (loopback bind plus mutual HMAC), so what's left is writing it down and pinning the bind with a test.
  Complexity: S

- [ ] P2: Dhizuku freeze/suspend executor parity
  Why: freeze and suspend should run through Dhizuku's device-owner delegation on devices where ADB permissions are restricted.
  Evidence: `dhizuku/DhizukuBridge.java` is a detection probe only, and `settings/Ops.java` declares no Dhizuku mode.
  Order: do it after the ROADMAP item "Make the Dhizuku capability copy match what Dhizuku can actually do", which settles the API 26 module question.
  Unblocked 2026-11-07: Dhizuku 2.13.0 (2026-10-30) is out, and `dpm set-device-owner` hands it device owner on any emulator with no accounts.
  Complexity: M

- [ ] P2: Wireless-ADB resilience: recover after the One UI 8 doze kill
  Why: One UI 8 kills ADB-spawned servers on wake from doze (Shizuku #2475, reported on an S22 with Android 16).
  Evidence: the trusted-network half landed. 658ef6e38 asks once per network when Wireless debugging must be allowed, 14e8c1277 reconnects after a reboot on Android 15+, and 4cc5254b6 says why ADB mode failed. Nothing in `servermanager/` or `adb/` handles the server dying on wake from doze, and Shizuku #2475 is still open.
  Acceptance: on the S22, after the phone sits in doze and wakes, NG notices the dead server and reconnects or says why it can't.
  Unblocked 2026-11-07: the S22 from the Shizuku report is here, and doze can be forced with `dumpsys deviceidle force-idle`. Pair it with the ROADMAP P1 reboot reconnect check.
  Complexity: M

- [ ] P2: SAF DocumentsProvider exposure of app-private directories
  Why: let a SAF file manager browse an app's private data through NG's privileged mode.
  Evidence: `fm/AppManagerDocumentsProvider.java` exposes only the read-only Backups and Profiles roots.
  Order: land the ROADMAP P1 "Complete the read-only DocumentsProvider child contract" first.
  Acceptance: with root or ADB mode on, a sideloaded SAF file manager opens an app's data directory through the system picker, and with no privileged mode the root isn't offered.
  Unblocked 2026-11-07: root, ADB and Shizuku all run on the emulators and phones, and any SAF file manager can be sideloaded for the check.
  Complexity: M

- [ ] P2: Backup overwrite option for custom-name collisions
  Why: move-aside and rollback semantics differ between file-backed and SAF-backed Path implementations.
  Evidence: `backup/dialog/BackupFragment.java:246` still has `// TODO(18/9/20): Add overwrite option`.
  Touches: the overwrite-option UI and move-aside for custom-name collisions (merged from INIT-2b).
  Unblocked 2026-11-07: both Path kinds run on any emulator image, with a SAF tree picked through the system picker for the SAF half.
  Complexity: M

- [ ] P2: ApplicationStartInfo "why did this app start" panel
  Evidence: nothing calls `getHistoricalProcessStartReasons` yet. Exit info already lives in `compat/ActivityManagerCompat.java`, which is the natural home for it.
  Unblocked 2026-11-07: the API arrived in Android 15, and the API 35, 36 and 37 images are on disk.
  Complexity: M

- [ ] P2: Form-factor-aware permission prompt gate (TV)
  Why: TV sideload users hit permission prompts they can't reach or that keep coming back (upstream #1823, PermissionManagerX #61). Wear OS isn't a target, so this covers TV only.
  Evidence: `utils/Utils.java:619` `isTv()` checks FEATURE_LEANBACK, but no permission prompt consults it.
  Unblocked 2026-11-07: the SDK offers Android TV images for API 34 and 36 and a Google TV image for API 34.
  Complexity: M

- [ ] P2: Accessibility, keyboard, and touch-target hardening
  Why: dense expert tools still need predictable focus, visible labels, 48dp controls, and non-color-only status meaning across dialogs, lists, chips, menus, and result screens.
  Where: app/src/main/res/layout/, app/src/main/java/io/github/muntashirakon/AppManager/
  Today: the main-list badge composite TouchDelegate and `MainRecyclerAdapterBadgeStyleTest` landed, and so did the 2026-09-30 label commits (ebc4008ea, 19b27d3a4). Include the badge row in the scanner run.
  Unblocked 2026-11-07: Accessibility Scanner, TalkBack and keyboard input all work on the emulators and phones, so finding the violations is the first step of the work.
  Complexity: M

- [ ] P2: Degraded, empty, error, loading, and success state system
  Why: secondary screens should never fail silently or show blank states; every unavailable, partial, or failed workflow needs calm actionable copy and an obvious next step.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/, app/src/main/res/layout/
  Unblocked 2026-11-07: walking each screen's empty, error and loading paths is a device check, and the emulators and phones are available.
  Complexity: L

- [ ] P2: Settings information architecture cleanup
  Why: settings should group risk, privileges, appearance, backup, rules, notifications, and advanced/debug controls so users can find decisions without memorizing implementation boundaries.
  Where: app/src/main/java/io/github/muntashirakon/AppManager/settings/
  Unblocked 2026-11-07: the regrouping is a design call an experienced Android engineer can make from the settings code, then check on an emulator.
  Complexity: M

- [ ] P2: Multi-user/work-profile/private-space capability matrix
  Why: AppManagerNG has broad userId plumbing and hidden-profile permission support, but users need one visible source of truth for which operations work in main, work, hidden, and private profiles under each privilege mode.
  Evidence: app/src/main/AndroidManifest.xml; docs/policy/permissions.md; Canta work-profile issue; Neo Backup multi-profile issue.
  Unblocked 2026-11-07: `pm create-user` makes secondary users and work profiles on any emulator, and the API 35+ images support private space.
  Complexity: L

- [ ] P2: File-manager operations service with cancellation and recovery
  Why: Copy, move, delete, and archive work is still tied to fragment/background-thread flows even though the code calls out a bound service and cancellable thread ownership as needed.
  Evidence: fm/FmFragment.java; fm/FmAdapter.java.
  Unblocked 2026-11-07: rotation, service survival and an interrupted move can all be tested on an emulator, and a large change in the FM core is ordinary work.
  Complexity: L

- [ ] P2: Screenshot regression testing (Paparazzi)
  Why: 143 layouts, 3 themes (AMOLED/dark/light), ongoing V2 design token work create continuous visual regression risk with no automated catching.
  Touches: app/build.gradle, app/src/test/ (screenshot test classes)
  Today: Paparazzi 2.0.0-alpha05.1 (2026-09-28) is the newest on Maven Central and its notes list Android Gradle Plugin 9.0.0 support (#2198). PR #2318 closed without merging.
  Unblocked 2026-11-07: a Paparazzi release with AGP 9 support now exists. Confirm it builds against the repo's AGP 9.3.1 and compileSdk 37 before writing tests.
  Complexity: M

- [ ] P2: Publish a concrete coordinated-disclosure policy
  Why: the public repository has no `SECURITY.md`, and `CONTRIBUTING.md:61` still sends reporters to "the maintainer contact listed" for a root-capable app that handles credentials and backups.
  Evidence: `.github/` holds only `ISSUE_TEMPLATE`. GitHub private vulnerability reporting is now enabled on the repo.
  Touches: `.github/SECURITY.md`, `CONTRIBUTING.md`, `.github/ISSUE_TEMPLATE/bug_report.yml`.
  Acceptance: the Security tab exposes supported versions, an explicit non-public reporting route, requested report fields, acknowledgment/disclosure expectations, and a warning against public exploit details; CONTRIBUTING and the bug template link to the same policy without conflicting instructions.
  Unblocked 2026-11-07: the old blocker was one past pass's rule about which Markdown it could stage. Nothing outside the repo is missing, and private reporting is already on for the policy to point at.
  Complexity: S

- [ ] P2: Fix tracked documentation truth and links
  Why: canonical docs refer to removed files/workflows and describe shipped features/tests as future or disabled, so a clean clone gives maintainers contradictory architecture and release guidance.
  Evidence: `docs/architecture/README.md`, `launcher-icon-aliases.md`, `04-filter-finder.md`, `docs/roadmap/COMPLETED.md` (line 18 still names `PROJECT_CONTEXT.md`), `docs/policy/2026-05-26-minsdk-23-decision.md`, `design/README.md`.
  Acceptance: no tracked doc names the absent `PROJECT_CONTEXT.md` or deleted workflows, the architecture indexes list the tracked topics, shipped launcher, tag and test status matches the source, and every relative link resolves in a clean clone.
  Unblocked 2026-11-07: the old blocker was a past pass's staging rule. The doc contract test half is dropped, because the project tests the app and not its docs.
  Complexity: M

- [ ] P2: Conditional auto-freeze predicates (skip charging / foreground / active notifications)
  Why: Hail's loudest 2026 complaint is that unconditional auto-freeze kills apps users still want alive; conditional guards (skip while charging, skip the foreground app, skip apps with active notifications) are the requested fix and fit NG's routine-ops model.
  Evidence: Hail issues #186/#222 (auto-freeze reliability + skip guards); NG routine/scheduler executor.
  Touches: routines/ (new predicate types), freeze/ (guard evaluation), settings/ (defaults).
  Acceptance: a routine can freeze a package set except while charging, except when foreground, or except when it holds an active notification; each guard verified against the real system state.
  Unblocked 2026-11-07: charging, foreground and notification state can all be set on an emulator (`dumpsys battery set ac 1`, an app in front, a posted notification), and freeze runs there in root, ADB or Shizuku mode.
  Complexity: M

- [ ] P2: Event-driven leftover sweep after any uninstall (CorpseFinder pattern)
  Why: NG's LeftoverScanner is manual; SD Maid SE's CorpseFinder offers to sweep orphaned data dirs immediately after any uninstall (including uninstalls by other apps), which is when the mistake cost is highest.
  Touches: a new PACKAGE_FULLY_REMOVED receiver, `oneclickops/LeftoverScanner.java` reuse, a notification and review flow.
  Acceptance: uninstalling an app (or observing another app's uninstall) surfaces an actionable "found N orphaned dirs, review" prompt that routes into the existing leftover UI.
  Unblocked 2026-11-07: PACKAGE_FULLY_REMOVED is exempt from the implicit-broadcast limits, so a manifest receiver gets it, and root and ADB modes on an emulator cover the directory scan.
  Complexity: M

- [ ] P3: Full main-list ListAdapter/DiffUtil migration (INIT-D1)
  Evidence: `main/MainRecyclerAdapter.java:81` extends `MultiSelectionView.Adapter`, and `setDefaultList` calls `AdapterUtils.notifyDataSetChanged` with no ListAdapter or DiffUtil.
  Acceptance: list updates go through DiffUtil, and selection, scroll position and row View IDs survive a refresh.
  Unblocked 2026-11-07: View-ID preservation can be checked on any emulator. The item had no priority, so it's set to P3 here.
  Complexity: M

- [ ] P3: Complete the unaudited device, native, locale, and no-test-module pass
  Why: the 2026-08-02 audit was static and host-only, so the remaining runtime and coverage-sensitive areas must not be assumed clean.
  Scope: device/emulator UI rendering and motion/focus/touch/contrast checks; native C/C++ under `app/src/main/cpp/` and the JNI boundary; non-English locale pseudolocale overflow and truncation; Room schema and migration behavior; the `benchmark/` module; packages `viewer/`, `types/`, `magisk/`, and `progress/`; and privileged root/ADB runtime behavior.
  Acceptance: each scoped area has either findings logged with fixes or an explicit review result showing it clean, with runtime evidence preserved in the appropriate tracked report.
  Unblocked 2026-11-07: the emulators at every API level, the rooted API 36 image and the two phones cover the runtime parts, and the native and JNI review is code reading.
  Complexity: M

- [ ] P3: D-pad/TV navigation pass
  Why: Upstream #107 (keyboard/remote navigation, "Partly Fixed") plus SD Maid SE's Android TV launcher support show the box-tinkerer segment is real (FireOS/Firestick issues already appear upstream: #1835, #1854); NG's M3 dashboard was not audited for focus traversal.
  Today: the banner, the leanback launcher entry and the optional leanback and touchscreen features are already in the manifest (bc36d78de). Focus traversal is what's left, and upstream #107 is still open.
  Acceptance: main list -> app details -> batch ops are fully operable with a D-pad on an Android TV emulator.
  Unblocked 2026-11-07: the SDK offers Android TV images for API 34 and 36.
  Complexity: M

- [ ] P3: Transparent launch-through frozen apps
  Why: Hail's killer UX pattern: tap a frozen app's launcher icon → auto-unfreeze → launch → auto-refreeze when the app closes or screen locks. NG has freeze/unfreeze plumbing and auto-freeze-on-screen-lock, but no transparent launch-through.
  Today: `apk/behavior/FreezeUnfreeze.java` has FLAG_ON_UNFREEZE_OPEN_APP, FLAG_ON_OPEN_APP_NO_TASK and FLAG_FREEZE_ON_PHONE_LOCKED. `ActivityLauncherShortcutActivity.unfreezeAndLaunchActivity` still shows a confirm dialog, and `FreezeUnfreezeService` only refreezes on SCREEN_OFF.
  Acceptance: a launch-through shortcut opens a frozen app without a dialog and refreezes it after it leaves the foreground.
  Unblocked 2026-11-07: root, ADB and Shizuku modes all run on the emulators, and UsageStatsManager foreground events can be watched there.
  Complexity: M

- [ ] P3: Samsung "Clear Compiler Artifacts" batch operation
  Why: Upstream #1989 requests batch dexopt/ART profile clearing on Samsung devices. Samsung One UI shows this option per-app but no package manager exposes it as a batch operation.
  Today: batch ops already has `OP_DEXOPT` (21), and `DexOptimizer` has `clearApplicationProfileData` and `forceDexOpt`. Upstream #1989 is still open.
  Touches: batchops/BatchOpsManager.java, compat/ (Samsung dexopt clearing API)
  Unblocked 2026-11-07: the S22 and S25 are Samsung phones, so the One UI commands can be checked over ADB.
  Complexity: S

- [ ] P3: Visual token and component polish pass
  Why: cards, banners, list rows, dialogs, badges, chips, toasts, and nested surfaces should feel like one product in light, dark, and AMOLED modes without one-off colors or spacing.
  Where: app/src/main/res/{layout,values,drawable}/
  Merged: the theme and a11y coherence pass. Give NG-added screens one dark palette token set and replace misused drawables with purpose-named assets. Its badge touch-target part is done.
  Unblocked 2026-11-07: comparing the three themes is a screenshot job on an emulator, and the one-off colors can be found by searching `res/` for literal values.
  Complexity: M

- [ ] P3: Tooltips and microcopy consistency pass
  Why: expert controls need concise labels, explainers, and warnings that are useful without being robotic, vague, or inconsistent between screens.
  Where: app/src/main/res/values/strings.xml, app/src/main/java/io/github/muntashirakon/AppManager/
  Unblocked 2026-11-07: the walkthrough runs on an emulator, and most of the copy can be reviewed straight from `strings.xml`.
  Complexity: M

- [ ] P3: Device-wide analytics dashboard (install-source / SDK / signing distributions)
  Why: Inure's analytics panel and AppDash's insight cards ("unused apps", "storage-heavy") are the category's stickiest discovery surfaces; NG already computes every datapoint (installer source, target SDK, signing info, usage) but offers no aggregate view with tap-through to a filtered list.
  Today: `analytics/DeviceAnalyticsAggregator.java` (INIT-4a) already computes the data, and `usage/BarChartView.java` is an in-house chart view to reuse, so no chart library is needed. This also covers INIT-4b.
  Acceptance: a dashboard screen shows at least installer-source, targetSdk, and signing distributions plus an "unused 30/60/90 days" card; tapping any segment opens the main list pre-filtered to it.
  Unblocked 2026-11-07: the chart and tap-through can be checked on an emulator.
  Complexity: M

- [ ] P3: Dedicated freeze surface with home-screen widget
  Why: Freeze/unfreeze works via app details or batch ops, but there is no dedicated screen listing all frozen apps with one-tap toggle, so the feature is buried. Hail's frozen-apps grid with one-tap toggle and home-screen widget is the competitive standard for daily freeze/unfreeze workflows. NG ships a QS freeze tile but no in-app freeze surface or widget.
  Today: the only widgets are ScreenTime, DataUsage, ClearCache and Recording.
  Acceptance: a main-menu entry opens a grid of all frozen/suspended apps; each row has a one-tap toggle that freezes or unfreezes immediately; a home-screen widget shows frozen-app count and opens the freeze surface on tap; works in root, ADB, and Shizuku modes.
  Unblocked 2026-11-07: freeze runs in root, ADB and Shizuku modes on the emulators, and every emulator launcher can place a widget.
  Complexity: M

- [ ] P3: Profile sharing via QR code or deep link
  Why: Profiles serialize to JSON but there's no QR code or `am://profile/import/<encoded>` deep link for mobile-to-mobile sharing. Users must export to file, transfer, and import manually.
  Touches: profiles/ (QR encoder/decoder, deep-link handler in manifest)
  Acceptance: a "Share" action in the profile editor generates a QR code or copyable deep link; scanning/tapping it on another device opens the import flow.
  Unblocked 2026-11-07: ZXing core is Apache-2.0 and only needs adding, and the deep link can be fired on an emulator with `am start -d`.
  Complexity: M

- [ ] P3: Boot-component manager view
  Why: A dedicated "what starts at boot" surface (BOOT_COMPLETED receivers across all apps, batch-blockable) is a classic MyAndroidTools/Inure feature NG can build almost entirely from existing component-blocking plumbing.
  Touches: new BootManagerActivity (reuse component list UI), rules/compontents/ (existing IFW/disable paths), filters/, main menu entry
  Acceptance: a screen lists every app with BOOT_COMPLETED/LOCKED_BOOT_COMPLETED receivers and their enable state; per-row and batch block/unblock work through the existing rule store with undo.
  Unblocked 2026-11-07: IFW rules and component disabling run in root and ADB modes on the emulators, and a multi-file feature is ordinary work.
  Complexity: M

- [ ] P3: File-manager trash bin (staged deletion)
  Why: NG's FM hard-deletes; Files-by-Google's staged trash with 30-day retention is the established data-safety pattern and FM batch ops magnify mistake cost.
  Touches: fm/ (delete paths, trash root, restore UI), settings/ (retention pref)
  Acceptance: FM delete moves to a trash location with restore; trash auto-empties after the configured retention; "delete permanently" remains available.
  Unblocked 2026-11-07: delete, restore and auto-empty can be tested on any emulator, with the clock moved forward for the retention check.
  Complexity: M

- [ ] P3: Installer preflight: show the initiating package and select-all splits
  Why: InstallerX-Revived surfaces which app requested an install and offers select-all in the split chooser; both are transparency/UX wins NG's SplitApkChooser lacks.
  Today: `apk/splitapk/SplitApkChooser.java:78` sets `.showSelectAll(false)`, and there's no initiator lookup.
  Acceptance: the install preflight shows the originating/installer package where available and offers a select-all toggle for split sets.
  Unblocked 2026-11-07: `InstallSourceInfo` gives the initiating package from API 30, and the API 23 to 37.1 images cover the older and newer paths.
  Complexity: S

- [ ] P3: Weak-signature Finder filter and App Details chip
  Why: the host-verifiable classifier (`PackageUtils.isV1SchemeOnlySigning`) and the App Details signature-schemes warning text have shipped; a dedicated Finder predicate ("weak / v1-only signing") and a compact at-a-glance chip on the app row remain, so users can filter the whole device for weakly-signed apps.
  Today: `filters/options/` has no scheme predicate, so `IFilterableAppInfo` needs a scheme flag first.
  Acceptance: a Finder filter lists only v1-only-signed apps; the app row shows a weak-signature badge.
  Unblocked 2026-11-07: adding a scheme flag is an internal API choice, and the chip can be checked on an emulator in each theme.
  Complexity: M

- [ ] P3: Health-permission granular mapping (Android 16 android.permissions.health)
  Why: Android 16 splits several BODY_SENSORS capabilities into the granular `android.permissions.health` group; the permission views should map and label these so health-sensor access is legible instead of showing raw permission names.
  Today: `permissions/PermissionGroupCatalog.java` exists now, but its body_sensors group holds only BODY_SENSORS and BODY_SENSORS_BACKGROUND. `details/info/HealthConnectInfo.java` already knows the `android.permission.health.` prefix.
  Acceptance: apps requesting the split health permissions show them grouped/labelled under a health category; a host test covers the mapping table.
  Unblocked 2026-11-07: the catalog the old blocker asked for exists, and the API 36 image has the runtime permission data.
  Complexity: M

- [ ] P3: LibChecker-class static inspection signals (modern Xposed API, live-update capability, themed-icon/alias)
  Why: LibChecker surfaces cheap static signals AppManagerNG already has the data for (modern vs legacy Xposed API usage, Android 16 live-update-notification capability, themed-icon and launcher alias detection) as App Details and Finder rows.
  Today: `misc/XposedModuleInfo.java` already tells modern modules from legacy ones (`META-INF/xposed/module.prop`). There's no live-update or themed-icon signal, and no Finder filter for any of them.
  Acceptance: at least one new signal appears in App Details and is filterable in the Finder, over fixture package data.
  Unblocked 2026-11-07: the Xposed signal only needs a row and a Finder filter, and the rows can be checked on an emulator.
  Complexity: M

- [ ] P3: Persistent removal/debloat ledger surface
  Why: Canta keeps a durable record of what the user removed that survives across reinstalls of the manager; NG has op_history but no dedicated "what I removed and when" audit view users can revisit or restore from.
  Touches: new removal-ledger fragment (reuse op_history), db/ (query), main menu entry.
  Acceptance: a screen lists removed/debloated packages with timestamp and mode, and offers reinstall/restore where data allows.
  Unblocked 2026-11-07: op_history already stores the data, and reinstall or restore runs in root or ADB mode on an emulator.
  Complexity: M

- [ ] P3: Version-watch panel (full flavor): installed vs latest from static indexes
  Why: APKUpdater (3.8k stars, active) proves demand for multi-source update awareness without being a store; AppDash paywalls it; checking F-Droid/IzzyOnDroid index-v2 + GitHub releases against installed versions fits the full flavor's opt-in network doctrine and NG stays a manager (notify, don't install).
  Today: there's no flavor source set yet (`app/src` has only androidTest, debug, main and test), so the full-only code needs one.
  Acceptance: with the toggle on, a scheduled check lists apps whose installed version trails the chosen indexes, with a signing-cert mismatch warning where the index cert differs; floss flavor compiles the feature out entirely.
  Unblocked 2026-11-07: the F-Droid and IzzyOnDroid index hosts and the GitHub releases API all answered with HTTP 200 today, so the network access the old blocker named is there.
  Complexity: L

## Research-Driven Additions (2026-08-23)

### P1

- [ ] P1: Decode untrusted file thumbnails in an isolated process
  Why: image, PDF, font, eBook, and archive thumbnails pass attacker-controlled files to decoders inside the permission-bearing app process.
  Evidence: upstream App Manager #2011 maintainer security analysis; upstream commit `8fbd0ef76` isolates PDF rendering but leaves the broader decoder set; Android `<service android:isolatedProcess="true">` documentation; `fm/icons/FmIconFetcher.java`; `fm/icons/FmIcons.java:322-410`; `app/src/main/AndroidManifest.xml`.
  Touches: a non-exported isolated decoder service and binder contract, `fm/icons/FmIconFetcher.java`, `fm/icons/FmIcons.java`, manifest, malformed-file fixtures, process-death tests.
  Acceptance: thumbnail work receives only duplicated read-only file descriptors plus explicit pixel, byte, and time budgets; the service has no app permissions and cannot open arbitrary paths; malformed input, timeout, OOM, or decoder-process death returns the generic icon without crashing or blocking the file list; API 21 through 23 and a current API are covered.
  Complexity: L

- [ ] P1: Re-read authoritative App Details state after mutations
  Why: successful privileged cache clears leave stale storage totals, and successful overlay toggles can rebind the cached pre-operation state.
  Evidence: upstream App Manager #2023; `details/info/AppInfoFragment.java:3073-3098`; `details/AppDetailsOverlaysFragment.java:227-232`; `details/struct/AppDetailsOverlayItem.java:73-75`.
  Touches: `details/AppDetailsViewModel.java`, `details/info/AppInfoFragment.java`, `details/AppDetailsOverlaysFragment.java`, overlay and storage state models, tests.
  Acceptance: cache totals reload only after a confirmed successful clear; failure preserves the last confirmed totals; overlay controls are disabled while work is in flight, duplicate taps serialize, and success or failure re-queries `IOverlayManager`; detached completions do not touch views; tests cover both mutation types and failure rollback.
  Complexity: M

- [ ] P1: Enforce user-owned signer policies before installer commit
  Why: AppManagerNG can inspect certificates and detect changes after installation, but it cannot reject an unrecognized signer against a user-approved package policy before a session is committed.
  Evidence: InstallerX 26.05 signer-policy release; Obtainium #2922; AppVerifier; Inure feature matrix; upstream App Manager #2055; `permission/monitor/SigningCertSnapshotStore.java`; `apk/signing/SignerInfo.java`; `apk/installer/PackageInstallerActivity.java`.
  Touches: schema extension for `SigningCertSnapshotStore`, signer-policy model, installer preflight, App Details certificate actions, snapshot export/import, audit history, tests.
  Acceptance: a user can pin the installed or reviewed SHA-256 signer set for a package; strict policy blocks a fresh install or update before session commit when the candidate signer and valid rotation lineage do not satisfy the pin; the blocked result names both fingerprints and never consults a remote database; changing a pin is a separate reviewed action and is recorded; policies survive restart and snapshot round-trip.
  Complexity: M

- [ ] P1: Classify dependency CVEs by shipped reachability
  Why: the aggregate CVE report mixes packaged runtime, transformed wrappers, host tools, and test dependencies, leaving 962 result rows across 79 artifacts without a reliable shipped-risk boundary.
  Evidence: `build/reports/dependency-check/dependency-check-report.sarif` from 2026-08-22; `scripts/run_dependency_cve_gate.py`; root `build.gradle`; `config/owasp-suppressions.xml`; CVE-2026-11822 and CVE-2026-11824 mappings to AndroidX wrappers and host `sqlite-jdbc`.
  Touches: `scripts/run_dependency_cve_gate.py`, Gradle dependency-report inputs, reproducible-release evidence, SBOM/APK reachability join, SARIF/HTML outputs, suppression validation tests.
  Acceptance: the gate produces separate packaged-release and host/build/test sections for FLOSS and full; every blocking finding records module, configuration, resolved artifact, and APK or SBOM reachability evidence; host-only and CPE-name-collision findings cannot be presented as shipped APK code; CVSS policy still fails closed for reachable findings; stale or blanket suppressions fail validation. The six August 2026 SQLite CVEs (CVE-2026-51296, -51297, -51300, -51302, -51303, -51304) are documented upstream as fabricated and must carry that disposition rather than being re-investigated each release.
  Complexity: M

### P2

- [ ] P2: Show Disabled, Frozen, and Suspended as text in app rows
  Why: upstream #2006 reports difficulty distinguishing state colors, while the compact V2 row still encodes suspension as `°` and announces only installed or uninstalled state.
  Evidence: `https://github.com/MuntashirAkon/AppManager/issues/2006`; WCAG 2.2 Use of Color; Android accessibility guidance; `main/ApplicationItem.java:323`; `main/MainRecyclerAdapter.java:564`; `res/layout/item_main_v2.xml`.
  Touches: a shared app-state formatter, `main/ApplicationItem.java`, `main/MainRecyclerAdapter.java`, `item_main_v2.xml`, localized strings, accessibility tests.
  Acceptance: every non-normal state renders concise inline text in the metadata row, not a new status pill; the row content description uses the same state; color remains reinforcement only; combined states have deterministic precedence; monochrome, large-text, and TalkBack tests retain package-name readability and row density.
  Complexity: S

- [ ] P2: Support complete regex replacement semantics in Code Editor
  Why: upstream #2022 maps to a local guard that prevents deletion by replacement and leaves other regex behavior without a stable app-owned contract.
  Evidence: `https://github.com/MuntashirAkon/AppManager/issues/2022`; `editor/CodeEditorFragment.java:429-447`; pinned Sora editor 0.24.6 in `versions.gradle`.
  Touches: a small replacement-policy helper, `editor/CodeEditorFragment.java`, editor tests and fixtures.
  Acceptance: replace-current and replace-all accept an empty replacement; numbered capture groups, multiline `^`, zero-width matches, escaped replacement text, and invalid regex errors behave deterministically; invalid patterns do not alter the document; tests pin behavior to the API 21-compatible Sora version.
  Complexity: M

- [ ] P2: Add a backup-destination conformance probe
  Why: network-backed SAF providers can accept selection yet corrupt parallel writes or fail close, reopen, rename, retention, and restore semantics.
  Evidence: Neo Backup #1029 and #1022; Neo Backup FAQ; Android DataBackup 2.0.12 release notes and #485; current capacity, publish, rollback, and pruning paths under `backup/`.
  Touches: backup destination settings, a bounded provider-probe service, per-authority capability store, backup concurrency policy, fake-provider tests.
  Acceptance: a user-initiated health check creates, writes, closes, reopens, checksums, renames, and deletes small probe files, then tests two bounded parallel writes; no user data is read or removed; results persist per URI authority; failed parallel semantics force sequential backup for that destination; unknown capacity is reported as unknown and a failed probe never prunes a known-good backup.
  Complexity: M

- [ ] P2: Bound embedded JADX class decompilation and pin its reachable surface
  Why: the Android JADX fork is fixed at 1.4.7 while upstream has moved, and untrusted DEX or smali can still consume excessive CPU or memory even though resource and export advisories are not reachable through the checked call surface.
  Evidence: `versions.gradle:38`; `dex/DexUtils.java:161-223`; JADX Android fork releases; JADX 1.5.6 release and GHSA-hvp5-5x4f-33fq, GHSA-w6f5-h4x4-rfpj, GHSA-jwv3-q635-w9m4. The 1.4.7 pin is inside GHSA-hvp5-5x4f-33fq's affected range and `skipResources(true)` is the only thing keeping it unreachable, which is what the contract test below has to hold.
  Touches: `dex/DexUtils.java`, a decompile-policy wrapper, worker cancellation, size/time/result ceilings, hostile DEX and smali fixtures, dependency disposition notes.
  Acceptance: class decompilation has explicit input, output, time, and memory ceilings and is cancellable when its host screen closes; malformed, recursive, and oversized fixtures return classified errors without process death; a contract test proves AppManagerNG continues to set `skipResources(true)` and never invokes JADX APK export or GUI paths; reachable upstream fixes are backported or documented against the narrow fork.
  Complexity: M

- [ ] P2: Restore Swift Backup APK plus OBB conversion coverage
  Why: the only APK plus OBB regression test has been commented out since a legacy Robolectric limitation, while the project pins Robolectric 4.16.1 and v0.6.18 changed Swift manifest validation.
  Evidence: `backup/convert/SBConverterTest.java:158-176`; `versions.gradle:64`; `backup/convert/SBConverter.java`; v0.6.18 Swift ZIP-comment validation changelog entry.
  Touches: `SBConverterTest.java`, deterministic Swift fixtures, converter temporary-file cleanup.
  Acceptance: the test runs in the normal unit suite and proves that a valid fixture emits `base.apk` and the expected OBB payload; malformed comment metadata, package mismatch, missing APK, interrupted conversion, and cleanup of temporary output are covered; no live Swift account is required.
  Complexity: S

### P3

- [ ] P3: Show AppOps proxy package and attribution provenance
  Why: App Details displays time, duration, and mode but omits the proxy package and attribution tag available on API 30 and later.
  Evidence: `compat/AppOpsManagerCompat.java:666-700`; `hiddenapi/src/main/java/android/app/AppOpsManagerHidden.java:538-568`; Android `AppOpsManager.OpEventProxyInfo` API.
  Touches: hidden API stubs, `AppOpsManagerCompat`, App Details AppOps model and row, tests.
  Acceptance: API 30 and later show proxy package, UID, and attribution tag when supplied; missing or redacted values render no false provenance; older APIs retain current behavior; parcel and model tests cover direct, proxied, and unavailable events.
  Complexity: M

- [ ] P3: Move remaining module dependency pins into the central ledger
  Why: benchmark and app test configurations duplicate AndroidX Collection and test-runner versions outside the API 21 ceiling policy.
  Evidence: `benchmark/build.gradle:48,51-52`; `app/build.gradle:318-319`; `versions.gradle`; `scripts/verify_dependency_floor.py`.
  Touches: `versions.gradle`, `benchmark/build.gradle`, `app/build.gradle`, `docs/policy/minsdk-21-ceiling.json`, dependency-floor tests and lockfiles.
  Acceptance: each duplicated version is declared once in `versions.gradle`; all modules resolve the same pin; the API 21 ceiling gate covers runtime and test pins that can affect the device floor; dependency locks regenerate without unrelated drift.
  Complexity: S

## Research-Driven Additions (2026-09-05)

### P1

- [ ] P1: Warn on an untested Android version and log hidden-API fallbacks
  Why: nothing tells a user that the running Android version is above what this build was tested against, and a hidden-API accessor that silently returns nothing writes no log line, which is how the Android 17 enumeration break reached users undiagnosed.
  Evidence: upstream issue #2033 (2026-09-04) describes stepping line by line through a debugger because neither logcat nor a debug build gave any hint; this fork's own Android 17 fix `74fc7ae95` and fork issue #6; no maximum-SDK guard exists anywhere under `app/src/main/java/`; `misc/ProfilingTriggerHelper.java:23` documents the reflective-resolution pattern the warning should key off; `compat/PackageManagerCompat.java`; `settings/PrivilegeHealthPreferences.java`.
  Touches: a shared platform-support constant derived from `compileSdk`, the `compat/` accessors that swallow reflection failures, `settings/PrivilegeHealthPreferences.java`, `misc/SupportInfoBundle.java`, strings, tests.
  Acceptance: running on an SDK above the tested ceiling shows a dismissible non-blocking notice in Privilege Health and adds a line to the support bundle, and the app still starts and runs. It never refuses a new Android version; every `compat/` accessor that falls back after a reflection or linkage failure logs the class, member, and SDK at warning level once per process instead of silently returning a default; a host test asserts the ceiling constant tracks `compileSdk` so it cannot go stale, and asserts a simulated linkage failure produces exactly one log record.
  Complexity: M

- [ ] P1: Revoke internet access per app through privileged network rules
  Why: upstream shipped per-app INTERNET revocation in v4.1.1 and this fork has only metered-background net policy, which does not block connectivity.
  Evidence: upstream v4.1.1 release notes (2026-09-04) describe eBPF rules applied in root and ADB mode from the Uses-permissions tab, with loss on reboot named as a known limitation; upstream #2044 reports access returning immediately after an install; `compat/NetworkPolicyManagerCompat.java` and `rules/struct/NetPolicyRule.java` cover metered background only; `self/BootReceiver.java:29-31` already re-applies routines on boot; `batchops/struct/BatchNetPolicyOptions.java`.
  Touches: a new privileged network-rule executor, `rules/struct/`, `rules/RulesStorageManager.java`, the App Details uses-permissions tab, `batchops/BatchOpsManager.java`, `self/BootReceiver.java`, `profiles/`, host tests using a faked privileged executor.
  Acceptance: a persisted rule type records package, UID, and rule state and round-trips through the existing rules import and export formats; the uses-permissions row for `android.permission.INTERNET` reflects stored state and is disabled with a stated reason when no privileged mode is active; rules are re-applied after a successful install or update and on `BOOT_COMPLETED`, and the UI reports any window before enforcement resumes rather than claiming enforcement it does not have; host tests cover apply, revert, post-install re-apply, boot re-apply, and privilege loss against a faked executor; an unsupported kernel or ROM produces an explained unavailable state rather than a silent no-op. If on-device verification needs a kernel floor that cannot be checked on the host, move the enforcement half to `Roadmap_Blocked.md` and keep the rule model here.
  Complexity: L

### P2

- [ ] P2: Record permission grant state and report drift against a user policy
  Why: the monitoring subsystem stores only manifest-declared permissions, so a permission the user revoked and something later re-granted is invisible, and there is no view of which apps deviate from what the user chose.
  Evidence: fork issue #15 cites PermissionManagerX Pro's Permission Watcher and Scheduled Check as paid features solving exactly this; `permission/monitor/PermissionSnapshot.java:24-33` records declared, requested, and dangerous permissions and never a grant result; `permission/monitor/PermissionChangeMonitor.java:210` never calls `checkPermission`; `permission/monitor/PermissionSnapshotStore.java:60` is at schema version 2 and discards mismatched snapshots on load; `permission/monitor/PermissionChangeReceiver.java:31` fires only on package replacement; `history/ops/OpHistoryPruneScheduler.java:70` is the periodic-job pattern to copy.
  Touches: `permission/monitor/PermissionSnapshot.java`, `PermissionSnapshotStore.java`, `PermissionChangeMonitor.java`, a policy store modelled on `profiles/trigger/ProfileTriggerStore.java`, a new periodic worker, `permission/monitor/AppChangeFeedStore.java`, `settings/PrivacyPreferences.java`, `self/BootReceiver.java`, tests.
  Acceptance: snapshots carry grant state at schema 3 and older snapshots are re-primed rather than misread; an opt-in daily worker rescans and appends a drift entry to the existing app-change feed for every permission whose grant state differs from the stored policy, naming package, permission, expected state, and observed state; drift reporting works in every mode including no-root because it only reads; this item stays report-only and says so plainly in its user-facing copy, because automatic revocation needs a live privileged binder; a host test primes a policy, mutates a grant result behind a fake, and asserts exactly one drift entry, and reverting the schema change turns it red. Automatic remediation is deliberately left to the existing profile routines, which can already revoke permissions on a time-of-day trigger.
  Complexity: M

- [ ] P2: Bound the build-expiry lockout so it cannot strand a user
  Why: an expired prerelease build shows a non-cancelable dialog with no way through, expiry is decided from an unvalidated device clock, and the debug update link points at a page this project does not use.
  Evidence: `self/life/BuildExpiryChecker.java:88-105` compares `System.currentTimeMillis()` against `BuildConfig.BUILD_TIME_MILLIS`, with a source comment conceding it should use an SNTP server; `:64-86` adds a continue button only for the stable build type, leaving alpha, beta, and rc with only Update and Uninstall; it is enforced at `BaseActivity.java:86`, `main/SplashActivity.java:117`, and `crypto/ks/KeyStoreActivity.java:33`, so a skewed clock locks the user out of their own backups; `getUpdateUri()` returns the repository's Actions page for debug builds while this repository has no `.github/workflows` by policy.
  Touches: `self/life/BuildExpiryChecker.java`, `BaseActivity.java`, `main/SplashActivity.java`, `crypto/ks/KeyStoreActivity.java`, `app/src/test/java/io/github/muntashirakon/AppManager/self/life/BuildExpiryCheckerTest.java`, strings.
  Acceptance: every build type offers a continue path, so no expiry state can stop a user reaching their backups, rules, and snapshots; a build time in the future, or a device clock earlier than the build time, is treated as unknown rather than expired; the debug update link points at the releases page; the existing warning-period behaviour is unchanged; tests cover a clock set backwards, a clock set far forwards, and each build type, asserting a continue action exists in every case.
  Complexity: S

- [ ] P2: Make the Dhizuku capability copy match what Dhizuku can actually do
  Why: onboarding tells the user Dhizuku is ready and active while Privilege Health correctly states its operations are disabled, and the reason Privilege Health gives for that is now answerable rather than open.
  Evidence: fork discussion #5, where a user asks for full Dhizuku support because ColorOS 16 restricted ADB permissions and points at `trinadhthatakula/Thor`; `onboarding_confidence_mode_shizuku_ready` and `onboarding_mode_dhizuku_status_ready` in `app/src/main/res/values/strings.xml` against `privilege_health_dhizuku_dialog_message`; `settings/Ops.java:85-90` declares no Dhizuku mode; `apk/installer/InstallerPrivilegeCascade.java:182` adds Dhizuku only as an informational step with a null mode; `dhizuku/DhizukuBridge.java` is a detection probe; Dhizuku-API 2.6.0 declares a minimum SDK of 26 (https://raw.githubusercontent.com/iamr0s/Dhizuku-API/main/build.gradle) against a Dhizuku manager that already requires Android 8.0, so the AAR is a manifest-merger question rather than the API 21 floor conflict the string claims.
  Touches: `app/src/main/res/values/strings.xml`, `onboarding/OnboardingFragment.java`, `settings/PrivilegeHealthPreferences.java`, `docs/policy/minsdk-21-ceiling.md`, `Roadmap_Blocked.md`.
  Acceptance: no user-facing string implies Dhizuku can perform operations while it cannot, and none conflates Shizuku readiness with Dhizuku readiness; the Dhizuku status text states what detection gives the user today and what it does not; `docs/policy/minsdk-21-ceiling.md` records the measured Dhizuku-API minimum SDK and the decision on whether an API 26 guarded module with `tools:overrideLibrary` is acceptable under the API 21 policy, so the parked Dhizuku executor-parity row in `Roadmap_Blocked.md` carries a decided approach instead of an open question; a settings-string test fails if a readiness string is reintroduced without a matching capability.
  Complexity: S

## Research-Driven Additions (2026-09-25)

### P1

- [ ] P1: Store exact mutation before and after images
  Why: inverse-operation rollback can overwrite a newer change because it does not prove that current state still matches the state AppManagerNG wrote.
  Evidence: upstream App Manager #1959; Thor's Changed elsewhere model; `history/ops/PerAppRollbackManager.java`; current operation history and App Change Feed stores. The existing App Details readback item covers two screen caches, not durable cross-operation rollback.
  Touches: operation-history schema, mutation executors, authoritative readback adapters, `PerAppRollbackManager.java`, rollback review UI, retention and export, tests.
  Acceptance: every supported mutation records target, operation, exact before value, requested value, confirmed post value, execution identity, result, and timestamp in an append-only entry; rollback reads current state first and proceeds only when it matches the recorded post value; a mismatch displays Changed elsewhere with both values and performs no write; partial batch results remain independently reversible; schema migration, pruning, restart, export, and tamper tests pass.
  Complexity: L

- [ ] P1: Decode binary XML with an API-correct framework table
  Why: the decoder always loads the newest Android framework resources, so an old numeric resource ID can be labelled as a different modern attribute.
  Evidence: upstream App Manager #2040; `apk/parser/AndroidBinXmlDecoder.java`; Android resource-table evolution across API levels.
  Touches: `AndroidBinXmlDecoder.java`, compiled-against API metadata discovery, framework table selection and cache, raw-ID rendering, old-manifest fixtures, tests.
  Acceptance: the decoder uses the APK's compiled-against API metadata when present; when it is absent, a name is emitted only if every plausible framework table agrees, otherwise the hexadecimal resource ID is preserved; fixtures compiled against at least API 21, 28, 35, and 37 assert stable attribute names and values; malformed tables still produce a bounded parse error.
  Complexity: M

- [ ] P1: Keep shortcut-launched targets in Recents
  Why: the shortcut trampoline is excluded from Recents and no-history, then calls `finishActivity(0)`, which can leave the launched target without the normal task lifecycle users expect.
  Evidence: upstream App Manager #2054; Android Tasks and Recents documentation; shortcut-management guidance; `details/ActivityLauncherShortcutActivity.java`; its manifest entry.
  Touches: shortcut trampoline lifecycle, target launch-intent construction, manifest task flags, shortcut regression tests, API 21 through 37 device checks.
  Acceptance: the trampoline calls `finish()` or `finishAndRemoveTask()` on itself after a successful handoff and never relies on request code 0; a target launched from an AppManagerNG shortcut appears in Recents with the target app's normal label and task behavior unless that target itself opts out; repeated launches do not create duplicate orphan tasks; cancellation and missing-target paths close cleanly.
  Complexity: M

- [ ] P1: Include archived packages in API 35 and later inventory
  Why: archive and unarchive actions exist, but package enumeration narrows match flags to `int`, so `MATCH_ARCHIVED_PACKAGES` cannot reach the framework query and archived apps disappear from inventory.
  Evidence: upstream App Manager #2043; Android `PackageManager.MATCH_ARCHIVED_PACKAGES`; `compat/PackageManagerCompat.java`; archive actions and filters in the app list.
  Touches: `PackageManagerCompat.java`, long-backed `PackageInfoFlags` adapters, inventory queries, archived-state model, filters, secondary-user tests.
  Acceptance: API 35 and later query with a lossless long flag path and include archived packages when the user enables the archived filter; older APIs retain current queries; archived rows cannot expose actions that require an installed APK; primary and secondary-user tests cover installed, archived, restored, and unavailable states; a contract test fails if flags are narrowed to `int`.
  Complexity: M

- [ ] P1: Stabilize scheduled backup work on Android 15 and later
  Why: WorkManager 2.10.5 retains connectivity and periodic-work defects fixed in 2.11.1 and 2.11.2, while taking 2.11 directly would raise its minimum SDK from 21 to 23.
  Evidence: AndroidX WorkManager release notes; AndroidX version ledger; the pinned version in `versions.gradle`; scheduled backup workers and network-backed SAF support.
  Touches: backup scheduling, worker constraints, connectivity observation, WorkManager patch or app-owned gate, API-floor policy, API 21 and API 35 through 37 tests.
  Acceptance: a reproduction test first proves the affected behavior on WorkManager 2.10.5; the chosen fix retains minSdk 21 and is limited to the confirmed Android 15 or later path; a scheduled network-backed backup waits for usable connectivity, runs once after connectivity returns, and retains its next period after process death or reboot; API 21 scheduling remains unchanged; the dependency decision and upstream fix reference are recorded.
  Complexity: L

- [ ] P1: Reconcile persisted SAF grants when backup destinations change
  Why: selecting a new backup or restore destination takes another persistable URI grant but never releases an abandoned one, eventually consuming Android's finite per-app grant pool.
  Evidence: Android Storage Access Framework documentation; AOSP `UriGrantsManagerService`; `settings/BackupRestorePreferences.java:111,131,151`; current destination preference storage.
  Touches: `BackupRestorePreferences.java`, a reference-counted active-destination resolver, startup repair, destination migration, fake DocumentsProvider tests.
  Acceptance: the app acquires and verifies a replacement grant before switching preferences; it releases the old grant only when no active backup, restore, schedule, or profile references it; startup removes only provably stale grants owned by AppManagerNG; a failed replacement preserves the last working destination; tests cover shared URIs, revoked providers, process death during handoff, and repeated destination changes without grant growth.
  Complexity: M

- [ ] P1: Complete the read-only DocumentsProvider child contract
  Why: the provider advertises a local document tree but does not implement `isChildDocument()` or advertise child checks, leaving containment behavior incomplete for clients and security-sensitive path handling.
  Evidence: Android `DocumentsProvider` and `DocumentsContract.Root` documentation; `fm/AppManagerDocumentsProvider.java`; provider manifest and root flags.
  Touches: `AppManagerDocumentsProvider.java`, canonical document-ID mapping, root flags, symlink and prefix-confusion fixtures, provider instrumentation tests.
  Acceptance: the root advertises `FLAG_SUPPORTS_IS_CHILD`; `isChildDocument()` resolves canonical paths and returns true only inside the declared root; sibling-prefix, `..`, encoded-separator, symlink, deleted-node, and cross-root cases fail closed; the provider remains read-only and publishes no mutation flags; API 21 fallback behavior is documented and tested.
  Complexity: M

### P2

- [ ] P2: Show storage composition and deltas in the App Change Feed
  Why: a single current total cannot explain growth after an update, cache clear, restore, or package event, even though Android 15 exposes detailed app data types.
  Evidence: Android `StorageStats.getAppBytesByDataType()`; LibChecker #1945; App Info storage code; `permission/monitor/AppChangeFeedStore.java`.
  Touches: storage snapshot model, API 35 adapter, App Info storage presentation, App Change Feed event schema, retention, tests.
  Acceptance: API 35 and later show available categories with their capture time and record deltas after a user-requested refresh, install, update, restore, or clear; older APIs keep aggregate totals; shared UID, hard-link overlap, unsupported category, and unknown values are labelled and never forced to sum exactly; feed coalescing avoids duplicate entries for one mutation; tests cover positive, negative, unknown, and shared-UID deltas.
  Complexity: M

- [ ] P2: Preview backup and restore footprint before work begins
  Why: users cannot see which backup parts dominate the job or whether a selected destination is likely to fit before a long operation starts.
  Evidence: Neo Backup #906; Neo Backup and Swift Backup documentation; Android DataBackup; AppManagerNG's backup part, capacity, publish, and rollback paths.
  Touches: backup planning model, part estimators, destination-capacity adapter, backup and restore review screens, provider conformance result, tests.
  Acceptance: preflight lists APK, private data, external data, OBB, rules, and extras separately with an estimated, exact, or unknown label; known destination free space is compared with a documented safety margin; unknown remote capacity does not appear as zero or sufficient; restore previews compressed input and estimated expanded output; the final job records estimate versus actual without scanning unrelated user data.
  Complexity: M

- [ ] P2: Qualify debloat recommendations with device evidence
  Why: package safety changes by OEM, ROM, region, Android build, and dependency set, while current definitions cannot express when or where an observation was valid.
  Evidence: UAD-NG issues #1164, #1400, and #1311; Canta's UAD-backed workflow; `debloat/DebloatObject.java`; current OEM, dependency, web-reference, and warning fields.
  Touches: debloat schema and bundled definitions, source and observation metadata, device applicability matcher, review UI, import validation, tests.
  Acceptance: each recommendation can record source URL, observation date, device or ROM, region, build range, package role, dependencies, recovery path, and confidence; missing or mismatched applicability displays Unverified for this device and never Safe; imports preserve provenance and reject invalid dates or confidence values; filtering explains why a definition matched; existing records migrate with an explicit legacy-unknown state.
  Complexity: M

- [ ] P2: Capture bounded Android 17 pre-ANR evidence
  Why: Android 17 can warn before an ANR and provide an identifier that can be correlated with later evidence, but the current support bundle sees only post-event process information.
  Evidence: Android 17 feature and behavior-change documentation; `ActivityManager.registerAnrWarningListener`; `AnrWarningResult`; `ApplicationExitInfo.AnrInfo`; current profiling and support-bundle helpers.
  Touches: process lifecycle registration, a bounded ANR-warning store, support bundle, process-exit correlation, API 37 tests.
  Acceptance: API 37 registers one listener while the app process is active; each warning stores only time, process identity, warning type, and ANR identifier under a small count and age cap; later exit evidence links by identifier when available; callback work cannot block the main thread or read app content; older APIs and unsupported devices remain silent; tests cover correlation, pruning, duplicate callbacks, and listener teardown.
  Complexity: M

- [ ] P2: Add Shizuku OEM failure diagnostics
  Why: Android 16 MediaTek and HyperOS failures can look like generic privilege loss, making it impossible to tell whether the app, provider, binder, or OEM service path failed.
  Evidence: Shizuku API 13.1.5; Shizuku API pull request #299; current Shizuku bridge and Privilege Health diagnostics.
  Touches: Shizuku bridge diagnostics, Privilege Health, support bundle, binder-death classification, OEM fixture tests.
  Acceptance: a failed Shizuku operation records SDK, manufacturer, provider package and version, binder alive state, permission state, remote UID, and one stable failure code without device identifiers or command contents; Privilege Health distinguishes provider missing, permission denied, binder dead, incompatible provider, and operation failure; no open upstream patch is vendored by this item; tests redact user data and cover MediaTek or HyperOS signatures only as diagnostic inputs.
  Complexity: S

- [ ] P2: Honor Android 16 outlined text in custom usage charts
  Why: `BarChartView` draws labels directly on Canvas, bypassing the platform's automatic outlined-text treatment for users who enable it.
  Evidence: Android 16 accessibility features; WCAG 2.2 contrast guidance; `usage/BarChartView.java`.
  Touches: `BarChartView.java`, accessibility-setting adapter, chart paint calculation, light and dark screenshot tests.
  Acceptance: on Android 16 and later, when outlined text is requested, chart labels draw a contrast-calculated stroke followed by the existing fill without changing metrics; the outline is absent when the setting is off and on older APIs; labels remain legible in light, dark, high-contrast, and large-text snapshots; TalkBack content is unchanged.
  Complexity: S

- [ ] P2: Keep UI Tracker focused and bound its event work
  Why: on affected Samsung builds, AppManagerNG's own overlay can replace the external target, while every accessibility event can also start a new UsageStats scan and later publish stale data.
  Evidence: upstream App Manager #2039 and #1848; `accessibility/NoRootAccessibilityService.java:42-50`; `accessibility/activity/TrackerWindow.java:194-240,377-408,429-461`; the current fixed-pool queries do not debounce events, honor interruption during the scan, or reject an obsolete callback.
  Touches: tracker event filter, last-external-activity state, a dedicated serial query executor, generation and dismissal guards, overlay lifecycle, Samsung event fixtures, tests.
  Acceptance: tracker and overlay events never replace a valid external target; a 100-event burst causes at most one immediate and one trailing UsageStats query; an unchanged target is not rescanned; an older generation and a dismissed window can never publish; closing the overlay cancels pending work; genuine navigation into AppManagerNG remains observable when tracking is inactive; tests cover Samsung self events, split screen, rapid switches, interruption, timeout, and teardown.
  Complexity: M

### P3

- [ ] P3: Add named local installer presets
  Why: installer options are global, so users who repeat different workflows must reconfigure individual controls and can miss a risky carried-over setting.
  Evidence: InstallerX Revived option model and issue #821; `apk/installer/InstallerOptions.java`; installer preferences and preflight.
  Touches: preset model and store, installer options UI, preflight diff, import and export, migration, tests.
  Acceptance: users can save, rename, duplicate, apply, and delete local presets; applying a preset shows the fields that differ from current options before commit; unsupported options are ignored with an explanation, not silently coerced; no preset changes the default until explicitly applied; presets round-trip through app settings export without secrets or device-specific paths.
  Complexity: M

## Audit Findings — 2026-09-25

### P1

- [ ] P1 — Bind restore verification to the bytes that are consumed
  Category: Security
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java:1162-1182`; `app/src/main/java/io/github/muntashirakon/AppManager/backup/RestoreOp.java:369-460,609-815`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:1263-1285`; `libcore/io/src/main/java/io/github/muntashirakon/io/SplitInputStream.java:214-231`
  Problem: Restore hashes one provider stream, then reopens the path for listing, decryption, staging, or extraction, so a mutable provider can change the consumed archive after verification.
  Evidence: Digesting and extraction independently call `openInputStream`; plaintext backup paths are returned unchanged, and destructive restore work can begin from bytes that were never hashed.
  Fix: Copy every selected archive into private staging once, enforce size limits while copying, verify that exact staged object, and perform all later reads from it.
  Acceptance: A provider that changes content between opens cannot alter the restored payload; checksum or authentication failure occurs before app data is cleared; plaintext, encrypted, split, APK, and data archives share the same stage-once contract and cleanup tests.
  Confidence: Verified
  Effort: L

- [ ] P1 — Install the exact APK bytes shown in review
  Category: Security
  Where: `app/src/main/AndroidManifest.xml:749-795`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/ApkQueueItem.java:36-61`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/UriApkSource.java:29-49`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerViewModel.java:86-107,211-234`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerService.java:248-270`
  Problem: A reviewed seekable `content://` APK can remain backed by a mutable provider file until the deferred installer rereads it.
  Evidence: Seekable inputs can be represented by a live `/proc/self/fd` path; final staging computes a digest but never compares it with a review-time digest.
  Fix: Ingest external APK and split inputs into immutable private staging before parsing, then bind size and SHA-256 to the queue item used for approval and installation.
  Acceptance: Provider mutation after review cannot change installed bytes; the service rejects any size or digest mismatch; split membership, signer details, requested permissions, and installed payload all derive from the same staged files.
  Confidence: Verified
  Effort: M

- [ ] P1 — Treat Pure black as a night-only palette
  Category: Accessibility
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/AppearancePreferences.java:44-70`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java:199-235`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java:189-221,365-370`; `app/src/main/res/values/themes-v2.xml:93-127,249`
  Problem: Enabling Pure black in Night mode, then applying Day, produces a persistent black surface with Day text and icon colors.
  Evidence: The headless API 35 reproduction persisted across a cold launch; sampled preference titles were about 1.22:1 and icons about 2.12:1 against black.
  Fix: Gate AMOLED themes and overlays by effective night state, keep the stored preference for future Night use, and bind preference text and icons to semantic on-surface colors.
  Acceptance: Night to Pure black to Day remains readable after recreation and cold launch; Day uses the normal light palette, Night uses true black, Follow system and Battery follow effective state, text reaches 4.5:1, and controls and icons reach 3:1 with and without dynamic color.
  Confidence: Verified
  Effort: M

- [ ] P1 — Serialize Debloater loads and discard stale results
  Category: Concurrency
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterViewModel.java:40-85,210-308`; `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterActivity.java:157-162,289-294`; `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterListOptions.java:148-155`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/MultithreadedExecutor.java:22-40`
  Problem: Filter, sort, and reload requests can run concurrently over mutable shared state, allowing an older computation to overwrite a newer selection.
  Evidence: UI option changes submit independent work to a fixed pool without a generation check, serialized snapshot, or immutable result dataset.
  Fix: Snapshot inputs, serialize or cancel superseded loads, compute immutable result sets, and publish only the newest generation.
  Acceptance: Rapid filter, sort, source, and search changes always end on the latest requested state; delayed older work cannot publish; stress tests use forced completion reordering and report no concurrent mutation or mixed-source rows.
  Confidence: Verified
  Effort: M


- [ ] P2 — Represent complete routes in Settings search
  Category: Navigation
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndex.java:50-105`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/SettingsActivity.java:184-193,215-243,332-343`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/PreferenceFragment.java:88-97`; `app/src/main/res/xml/preferences_main.xml:16-21,37-55,132-137`
  Problem: Search entries store only one parent and one target, so nested results cannot be opened and root-level settings are absent from the index.
  Evidence: The current-run Shizuku glossary result returned to the Settings root; its real route is root to About to Glossary to topic, while root rows are never harvested.
  Fix: Store a full key path for each result, index the root resource, validate every segment, and integrate navigation with the existing fragment stack.
  Acceptance: Every indexed result opens and focuses the intended row from a cold Settings launch; root, two-level, and deeper routes have tests; removed or conditional keys fail safely with a clear unavailable state.
  Confidence: Verified
  Effort: M

- [ ] P2 — Handle every supported ABX token in Code Editor
  Category: Correctness
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorViewModel.java:158-180,353-414`; `libcore/compat/src/main/java/io/github/muntashirakon/compat/xml/BinaryXmlSerializer.java:313-338`
  Problem: Valid binary XML tokens outside start, end, text, ignorable whitespace, and end-document can throw `UnsupportedOperationException` and leave the editor blank.
  Evidence: The serializer supports CDATA sections, entity references, processing instructions, comments, and document declarations, while `copyXml()` has no handling for them and the load path catches only `IOException`.
  Fix: Map every supported parser token or preserve it losslessly, convert parse failures into a structured editor error, and never publish a blank successful document.
  Acceptance: Fixtures for every ABX token open, display, and round-trip without loss; unknown and malformed tokens show a stable error with no crash; unit tests cover both binary-to-text and save-back paths.
  Confidence: Verified
  Effort: M

- [ ] P2 — Stop log recording through a non-exported receiver
  Category: Component security
  Where: `app/src/main/AndroidManifest.xml:1822-1838`; `app/src/main/java/io/github/muntashirakon/AppManager/logcat/RecordingWidgetProvider.java:42-63`; `app/src/main/java/io/github/muntashirakon/AppManager/logcat/helper/ServiceHelper.java:35-41`; `app/src/main/java/io/github/muntashirakon/AppManager/logcat/helper/WidgetHelper.java:70-79`
  Problem: Any installed app can send the custom explicit widget action to the exported provider and stop active log recording.
  Evidence: The provider must receive system widget broadcasts, but the same exported component also handles the app-only stop action without caller authentication.
  Fix: Route the PendingIntent to a non-exported receiver or service and leave only required system widget actions on the exported provider.
  Acceptance: External broadcasts cannot stop recording; the widget stop control still works from every supported launcher; system update, delete, enable, and disable broadcasts remain functional in instrumentation tests.
  Confidence: Verified
  Effort: S

- [ ] P2 — Shut down Debloater worker pools with the ViewModel
  Category: Resource lifecycle
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterViewModel.java:40-85,210-310`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/MultithreadedExecutor.java:22-40`
  Problem: Each Debloater ViewModel creates a fixed thread pool and never cancels queued work or shuts the pool down in `onCleared`.
  Evidence: The executor uses persistent core threads with no timeout, and the ViewModel has no lifecycle teardown through the end of the class.
  Fix: Track submitted work, cancel it in `onCleared`, call `shutdownNow`, and prevent late LiveData publication.
  Acceptance: Fifty create and destroy cycles return executor thread count to baseline; queued and running work cannot publish after clear; shutdown remains idempotent under simultaneous reload and navigation.
  Confidence: Verified
  Effort: S

- [ ] P2 — Release the Sora editor when its view is destroyed
  Category: Resource lifecycle
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorFragment.java:204-218,312-345,546-568`; `versions.gradle:48`; `app/build.gradle:300-301`
  Problem: The fragment detaches observers but never calls Sora's `release()`, so editor threads, language resources, and view references can survive back-stack churn.
  Evidence: The project uses Sora 0.24.6, whose editor lifecycle includes explicit release; current `onDestroyView` cleanup does not perform it.
  Fix: Stop callbacks, detach language and diagnostics resources, release the editor exactly once, and clear view-bound fields after preserving required state.
  Acceptance: Repeated open, rotate, back, and reopen cycles retain content and produce no growing editor threads or retained Fragment views; release is once-only and late callbacks are ignored.
  Confidence: Verified
  Effort: S

- [ ] P2 — Invalidate Settings search after locale changes
  Category: Localization
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndex.java:110-144,187-198`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/ChangeLanguageFragment.java:133-142`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java:179-188`
  Problem: The process-wide search index caches localized titles and summaries without keying them by locale or invalidating them when the app language changes.
  Evidence: Language application updates configuration, while the production index cache has no locale transition hook; invalidation appears only in tests.
  Fix: Key cached entries by effective locale and configuration, or invalidate and rebuild after language application before new search results are shown.
  Acceptance: Switching languages in one process updates every search title, summary, and normalized query without restart; switching back is also correct; tests cover script and region variants plus rapid consecutive changes.
  Confidence: Verified
  Effort: S

- [ ] P2 — Announce onboarding access checks only after they finish
  Category: Accessibility
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java:161-168,267-276,361-414,445-471,659-692`
  Problem: Re-check announces completion before asynchronous root and ADB probes finish, and stored accessibility descriptions can continue to describe the previous result.
  Evidence: The completion snackbar is posted immediately after starting background checks, while content descriptions are assembled from snapshots that are not recomputed for every result.
  Fix: Aggregate a generation-scoped pending check set, announce only the final combined state, discard stale generations, and rebuild descriptions from the published result.
  Acceptance: TalkBack hears one accurate completion announcement per re-check; slow older probes cannot overwrite newer state; every row's spoken status matches its visible state under success, denial, timeout, and teardown.
  Confidence: Verified
  Effort: M

- [ ] P2 — Keep text-input dialogs open when validation fails
  Category: Form usability
  Where: `libcore/ui/src/main/java/io/github/muntashirakon/dialog/TextInputDialogBuilder.java:128-139,191-208`; `app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java:972-989`; `app/src/main/java/io/github/muntashirakon/AppManager/filters/EditFilterOptionFragment.java:101-146,208-229`
  Problem: Positive actions use the platform auto-dismiss listener, so validation callbacks can report an error and return but cannot keep the user's input dialog open.
  Evidence: Invalid tag input shows feedback and exits the callback, yet the dialog closes because the builder never replaces the positive listener after `show()`.
  Fix: Add a validated positive-action API that runs after show, dismisses only on success, focuses the invalid field, and exposes inline error text.
  Acceptance: Invalid values in tag, filter, and log-viewer dialogs retain input and focus; valid values dismiss once; IME action and button paths share validation and have UI tests.
  Confidence: Verified
  Effort: S

- [ ] P2 — Use density units for screen-time widget geometry
  Category: Responsive layout
  Where: `app/src/main/res/layout/app_widget_screen_time.xml:63-153`; `app/src/main/res/xml/app_widget_info_screen_time.xml:6-16`; `app/src/main/java/io/github/muntashirakon/AppManager/usage/ScreenTimeAppWidget.java:64-72`
  Problem: Margins and positional geometry use `sp`, so font scaling moves non-text layout independently of widget bounds and can cause overlap or clipping.
  Evidence: Bubble sizes use `dp`, but several layout margins and top offsets use `sp` in the same fixed widget composition.
  Fix: Use `dp` or constraints for geometry, reserve `sp` for text size, and provide size-specific resources where launcher bounds require different composition.
  Acceptance: Widget snapshots at 100, 130, and 200 percent font scale and minimum and expanded launcher sizes show no overlap, clipping, or displaced controls in light and dark themes.
  Confidence: Likely
  Effort: S

- [ ] P2 — Stop screens probing for root while no-root is selected
  Category: Privilege boundaries
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java:468-471`; `app/src/main/java/io/github/muntashirakon/AppManager/misc/DeviceInfo2.java:158`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/FileUtils.java:241`; `app/src/main/java/io/github/muntashirakon/AppManager/runner/RunnerUtils.java:104-122`
  Problem: With No root chosen, opening App Info (to size the Open in Termux menu item), the device info dialog, and a failed shared-storage lookup still call `RunnerUtils.isRootAvailable()` or `isRootGiven()`, which builds libsu's root shell and raises a superuser prompt on a rooted phone.
  Evidence: Found on 2026-09-26 while fixing the side-effect-free no-root start; `isAppGrantedRoot()` constructs `Runner.getRootInstance()`, and `NormalShell(true)` asks libsu for the root main shell. Upstream App Manager #2048 describes the resulting repeated "denied superuser rights" toasts during ordinary use.
  Fix: Answer these UI questions from the selected mode or from a root-manager presence check that spawns no `su`, and keep the real grant probe for the mode chooser, onboarding, and Privilege Health, where the user asked for it.
  Acceptance: In no-root mode, opening App Info, the device info dialog, and the storage fallback make no `su` attempt, pinned by a spy test like `OpsNoRootInitTest`; the mode chooser and Privilege Health still report a granted root when the user checks.
  Confidence: Verified
  Effort: S

- [ ] P2 — Import PKCS #8 keys off the main thread
  Category: Responsiveness
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/KeyPairImporterDialogFragment.java:135-147`; `app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/KeyStoreUtils.java` (`getKeyPair(Context, Uri, Uri)`)
  Problem: The PKCS #8 and PEM path reads both files in full, with no size limit, and parses the key and certificate on the UI thread, while the keystore paths beside it already run in the background.
  Evidence: Found on 2026-09-26 while routing keystore imports through `KeyStoreImportPolicy`; the OK handler calls `KeyStoreUtils.getKeyPair(mActivity, mPk8File, mKsOrPemFile)` directly, and that method uses `IoUtils.readFully(pk, -1, true)`.
  Fix: Run the import on a worker, bound both reads to a small documented ceiling, and post the result back to the dialog.
  Acceptance: A slow or oversized key or certificate file never blocks the UI thread; files above the ceiling fail with a classified error; the existing successful import still selects the key pair.
  Confidence: Verified
  Effort: S

### P3

- [ ] P3 — Add the current version to What’s New
  Category: Release communication
  Where: `app/build.gradle:22-23`; `app/src/main/res/raw/changelog.xml:8-16`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/AboutPreferences.java:127-142`
  Problem: A 0.6.24 build displays 0.6.23 as the newest bundled What’s New entry.
  Evidence: HEAD declares version code 32 and version name 0.6.24, while the first changelog record is version code 31 and 0.6.23; the current-run screen showed that older entry.
  Fix: Add the 0.6.24 entry and make release consistency parse the first bundled changelog record against the application version.
  Acceptance: About and first-run What’s New show 0.6.24 first; a version bump without a matching first record fails the local release gate; historic entries keep their order and content.
  Confidence: Verified
  Effort: S

## Research-Driven Additions (2026-09-30)

Sources and reasoning: RESEARCH.md (2026-09-30).

### P1

- [ ] P1: Check the wireless reconnect after a reboot on a phone
  Why: the upstream wireless fixes are ported, and on the S22 on 2026-09-30 AppManagerNG paired, connected over Wireless debugging and came back on its own after Wi-Fi went off and on. The start at boot, which Android 15 only allows as a special use service, has host tests but hasn't run on a phone yet.
  Evidence: `self/BootReceiver.java`, `servermanager/WifiWaitService.java`; upstream `22d439d61` (connect on boot).
  Touches: nothing unless it fails.
  Acceptance: with Wireless debugging as the mode, the network always allowed for Wireless debugging and the server stopped, `adb reboot` the S22 or S25 and unlock it; the server is running within two minutes of the unlock without the app being opened. On the same run, a debug build's `LocalServerManager` log shows the launch shell's echo only as "(echo of the launch command, not logged)" and still reaches "Success!", which checks on a phone that leaving the echo out didn't hide the launcher's answer. Then join an access point Wireless debugging isn't allowed on yet: one system prompt and one AppManagerNG notification come up, not one per retry, and the server starts once the network is allowed.
  Complexity: S

### P2

### P3

- [ ] P3: Schedule settings snapshot export to a chosen folder
  Why: settings, rules and profiles are lost with the device unless the user remembers to export them, and two competitor trackers ask for exactly this.
  Evidence: Canta #377 and SD Maid SE #2807 (automatic settings backup requests); `snapshot/SnapshotBundle.java` already exports and imports; scheduled backups already use WorkManager and SAF destinations.
  Touches: `SnapshotBundle.java`, a WorkManager worker beside `backup/schedule/`, Privacy or Backup settings, retention, tests.
  Acceptance: a user can pick a SAF folder, an interval and a keep count; each run writes an encrypted snapshot when encryption is configured and never a plaintext one when it is; the oldest files beyond the keep count are removed only after a new snapshot is written and read back; server secrets stay excluded; host tests cover retention, a revoked folder, and encryption.
  Complexity: M

- [ ] P3: Let a hung wireless pairing attempt be closed
  Why: libadb-android 3.1.1 opens the pairing socket with no connect or read timeout and keeps it private, so when an attempt hangs, AppManagerNG's 30-second limit can only walk away from it. The thread stays blocked, and the replaced connection manager keeps whatever ADB connection it had, until TCP gives up.
  Evidence: `adb/AdbConnectionManager.java` (`pairLiveData`, `discardInstance`); libadb-android 3.1.1 `PairingConnectionCtx.setupTlsConnection` (`new Socket(host, port)`, no `setSoTimeout`); `AbsAdbConnectionManager.pair` holds the manager's lock for the whole attempt, so closing the old manager would wait on the stuck attempt.
  Touches: libadb-android (a socket timeout, or a pairing context the caller can close), `AdbConnectionManager`, tests.
  Acceptance: a pairing attempt against a port that accepts TCP and never answers ends within its limit with no thread left blocked in the pairing code, shown by a host test with a silent local server socket; the replaced manager's connection is closed without waiting on the stuck attempt.
  Complexity: M
