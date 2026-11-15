# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Issue Drain Left Open (2026-11-08)

Every report is fixed or built and has a reply posted or queued. What's left needs hardware the drain couldn't get: the S22 was leased to another drain the whole time and the S25 wasn't connected.

- [ ] P2: Repeat the drain's device checks on the S22 or S25. The fixes and features for #12, #13, #15, #16, #17, #18, #19, #20, #21, #23, #25, #26, #27, #28 and PR #24 ran on API 29 and API 35 emulators only. On a phone, look hardest at what an emulator fakes: Wireless debugging pairing and ADB over TCP (#20), usage access staying granted after a reboot (#16), the #15 scheduled check in `dumpsys jobscheduler` after a restart, and the #26 overlay bubble over a real app's ad. For #20 on a phone with `adb tcpip 5555` and Wireless debugging both on (adbd's secure auth is on there, unlike the emulator): ADB over TCP connects to 5555, switching to Wireless debugging moves to the TLS port without asking to pair again, and a new Wireless debugging port mid-session gets used (`adb shell ss -tn`). For #22, Forget drops a connected device at once, and a share that comes back on a new port shows up on the other device without reopening.
- [ ] P2: Check #22 Local sync between two separate devices. A work profile can't stand in, because both users share one install of each app, so these two rows never show there. "Signature differs": install the same package signed with different keys on each device, connect, and confirm the row says so and offers no install. Update all newer: give the sharing device a higher versionCode of an app the other has (same key), tap Update all newer and confirm it updates. Two emulators work too: on the client type 10.0.2.2 and a host port that `adb -s <host> forward tcp:<port> tcp:<share port>` points at the sharing emulator.

- [ ] P2: Check the #15 instant revoke on a phone in ADB and Shizuku mode
  Why: the privileged server now watches apps that have an Always deny permission and starts a check the moment one changes (c9eb21750, 2026-11-13), so a grant from Android's Settings is taken back right away. It hasn't run on a phone yet.
  Touches: nothing unless it fails (`permission/watcher/`, the server's permission listener).
  Acceptance: on the S22 or S25, in ADB mode and again in Shizuku mode, grant a permission watched as Always deny from Android's Settings. It's revoked within a few seconds and the notification says so. Reboot, start the server again and repeat once.
  Complexity: S

- [ ] P2: Check an archived app's row on the S22
  Why: 6be4b000e lists archived apps under the name and icon Android kept for them, with Archived at the front of the row's details line. The unit tests cover it, but the row hasn't been seen on a phone: the S22 was leased to other work both times it came up.
  Touches: nothing unless it fails (`main/MainListUidFormatter.java`, `main/MainViewModel.java`).
  Acceptance: on the S22, with the latest floss debug build installed, the archived `com.example.archiveprobe` (installed with `adb install --user 0 -i com.android.vending`, then `pm archive --user 0`) shows in the main list as its label and icon with "Archived ·" leading the details line, and TalkBack's text for the row starts with Archived. Afterwards clear the list's search, close the keyboard and `adb uninstall com.example.archiveprobe`, which is still on the phone from the first try.
  Complexity: S

- [ ] P2: Check a no-root restore on the S22 (upstream #1286)
  Why: upstream's #1286 crash was a no-root restore on a Samsung phone throwing a SecurityException from `createSession`, because the session was opened in the backup's installer's name. NG opens it in its own name, and the MIUI and HyperOS retry now runs inside the install call (upstream f3db2698a). Both are covered by Robolectric (`PackageInstallerSessionInstallerTest`, `PackageInstallerRetryTest`), but no restore has run on a phone since: the S22 was leased to other work.
  Touches: nothing unless it fails (`apk/installer/PackageInstallerCompat.java`, `backup/RestoreOp.java`).
  Acceptance: on the S22 in no-root mode, with the latest floss debug build, a probe app installed with `adb install --user 0 -i com.android.vending` is backed up (APK only), uninstalled, and restored from that backup. Android's install prompt is approved from a fresh dump, the restore reports success, the app is installed again, and logcat shows no SecurityException from `createSession`. Afterwards uninstall the probe, delete its backup and put the app's mode back to what it was.
  Complexity: S

- [ ] P2: Check the isolated thumbnail process on a phone and on API 21 and 22
  Why: file manager thumbnails now come from an isolated `:thumbnails` process. Robolectric can't start a real isolated process, render a PDF, or run API 21 and 22, so those parts have only run as unit tests on API 23 and 36.
  Touches: nothing unless it fails (`fm/icons/IsolatedThumbnails.java`, `ThumbnailDecoderService`, the isolated-process early return in `AppManager`).
  Acceptance: on the S22 or S25, open a folder holding a PNG, a JPEG, an SVG, a PDF, a TTF, an EPUB with a cover, an .apks or .apkm bundle, a J2ME .jar and an .odt. Each gets its thumbnail, and `adb shell ps -A -o USER,NAME | grep thumbnails` shows the process under a `u0_i` user. A truncated PNG, random bytes renamed to .pdf and a zip bomb renamed to .epub get the generic icon, scrolling never stalls, and the app doesn't crash. Thirty seconds after the last thumbnail the `:thumbnails` process is gone. Repeat the folder on an API 21 and an API 22 emulator, where fonts get the generic icon by design.
  Complexity: S

- [ ] P2: Let ADB mode start in a work profile
  Why: in a managed profile the app reaches adbd and launches its server, but never binds the privileged service, so the splash sits on "Checking the configured operation mode" until the startup check times out and only then offers recovery buttons.
  Evidence: API 35 emulator, `pm create-user --profileOf 0 --managed`, release build in user 10: `:priv:10` started on port 62011 as uid 1010209, then the app logged `RemoteException: Binder not running` from `ipc/LocalServices.bindAmService`.
  Touches: `server/.../RootServiceMain.java` (how it hands the binder to a non-zero user), `ipc/LocalServices.java`, `servermanager/LocalServerManager.java`.
  Acceptance: a profile instance in ADB mode either binds its service or drops to No root within a few seconds with the reason shown; a test covers the user the binder is delivered to.
  Complexity: M

- [ ] P3: Let the debug and release builds run their servers side by side
  Why: both bind local server port 62001 (plus the user id), so whichever starts second fails with EADDRINUSE and its mode check fails.
  Evidence: emulator, release server left running, debug build's TLS connect then failed on 62001.
  Touches: `servermanager/ServerConfig.java` (port choice), the server launch arguments.
  Acceptance: the port differs per package or is picked free at launch and passed to the server; both builds start their servers on one device.
  Complexity: S

## Unblocked from Roadmap_Blocked.md (2026-11-07)

- [ ] P1: Validate privileged-mode binding on Android 17
  Why: app-list enumeration on API 37 is verified and pinned by `PackageEnumerationInstrumentedTest`. What remains is whether Shizuku, Dhizuku, and root bind on API 37, or fail with a surfaced, actionable reason.
  Evidence: servermanager/ privileged-mode binding; settings/Ops.java.
  Acceptance: on API 37, each privileged mode either binds or reports why in the UI rather than failing silently.
  Today: Shizuku's newest release is still v13.6.0 (2025-05-25) and its Android 17 reports #1965 and #1988 are open, so a Shizuku failure on API 37 that shows its reason in the UI meets the bar.
  Unblocked 2026-11-07: the API 37.0 and 37.1 images are on disk and should root with Magisk 31.1 the way the API 36 image did (not tried yet), Shizuku 13.6.0 sideloads and starts over ADB, and Dhizuku 2.13.0 (2026-10-30) gets device owner through `dpm set-device-owner` on an emulator with no accounts.
  Research 2026-11-13: Shizuku 13.6.0 shows an empty authorized-apps list on Android 17 (RikkaApps/Shizuku #2180 with 25 upvotes, #2681 on 2026-11-11), so a 13.6.0 failure is expected. Also bind once through a Shizuku-API compatible server, such as Porter's compatibility mode (SD Maid SE 2.2.0, 2026-09-29). The warning logic is the new P2 "Judge Shizuku on Android 17 by whether the server answers".
  Checked 2026-11-14 on the S25 (API 37, One UI CP2A.260605.016): Shizuku 13.6.0's server starts over ADB, Mode doctor sees it, the consent dialog works and NG's shizuku_am and shizuku_fs services connect, so Shizuku binds on Android 17. The first switch then died: the READ_LOGS self-grant changes NG's gids, Android killed NG with Settings resumed, and the mode was never saved. READ_LOGS now waits until every NG process is cached (DeferredLogAccess). Retested on the S25 the same day with that build: the switch sticks ("Selected: Shizuku. Active now: Shizuku (shell)"), READ_LOGS lands 2 seconds after Home, the kill hits the cached process (adj 700), and reopening lands back on Mode of operation. Shizuku on API 37 is done. Still owed: root plus Dhizuku on API 37, which need an emulator with about 10 GB of host RAM free (rules/android.md). `AppManagerNG_Magisk_API37` is set up on a copy of the API 37 google_apis image and is not patched yet.
  Research 2026-11-14: run ADB mode on API 37 too, since none of the checks above covers it. PermissionManagerX #83 (2026-11-12) shows Android 17 build CP4A.261105.040.A1 refusing a uid 2000 `startService` that names the app's own package (`ActivityManagerService.enforceCallingPackage`). NG's `ActivityManagerCompat` calls name `com.android.shell` from shell (`self/SelfPermissions.java:386-394`), so they're clear. The server's own broadcasts go through an NG package context (`ipc/RootServiceServer.java:143-153`, `server/BroadcastSender.java`), so in ADB mode on API 37 confirm the server-started and relay broadcasts still arrive (Mode doctor shows ADB active, and a permission watcher revoke fires).
  Complexity: S

- [ ] P1: Check root detection on a live Android 16 root
  Why: the 2026 root-manager retune (probe matrix in RootManagerInfo, the Magisk 31.1 grant toast and setup note, SukiSU Ultra as KernelSU) is covered by Robolectric only.
  Evidence: runner/RootManagerInfo.java; RootManagerInfoTest; OpsRootRefusedTest; OnboardingFragment.pick().
  Acceptance: on an API 36 AVD with Magisk 31.1, picking Root before granting it shows the Magisk > Superuser toast and the setup sheet's Open Magisk button opens Magisk. After AppManagerNG is added in Magisk > Superuser > +, Root works and Privilege health names Magisk from the /data/adb marker. KernelSU-Next gets the same check where a kernel can load it.
  Today: 2026-11-14 there was no RAM to boot a Magisk AVD next to the other sessions' emulator, and both phones were leased. Check which Magisk the Hushfeed_Magisk_API36 AVD carries first, since the vault records 30.7 there.
  Complexity: S

- [ ] P2: Dhizuku freeze/suspend executor parity
  Why: freeze and suspend should run through Dhizuku's device-owner delegation on devices where ADB permissions are restricted.
  Evidence: `dhizuku/DhizukuBridge.java` is a detection probe only, and `settings/Ops.java` declares no Dhizuku mode.
  Order: do it after the ROADMAP item "Make the Dhizuku capability copy match what Dhizuku can actually do", which settles the API 26 module question.
  Unblocked 2026-11-07: Dhizuku 2.13.0 (2026-10-30) is out, and `dpm set-device-owner` hands it device owner on any emulator with no accounts.
  Research 2026-11-13: Thor 1.96.1 to 1.97.4 shows a working shape. It freezes with `setApplicationHidden`, grants with `setPermissionGrantState` and clears data through DevicePolicyManager, and has no Dhizuku path for force-stop or cache clearing. Dhizuku 2.12.0 (2026-06-24) also works as profile owner.
  Complexity: M

- [ ] P2: Wireless-ADB resilience: recover after the One UI 8 doze kill
  Why: One UI 8 kills ADB-spawned servers on wake from doze (Shizuku #2475, reported on an S22 with Android 16).
  Evidence: the trusted-network half landed. 658ef6e38 asks once per network when Wireless debugging must be allowed, 14e8c1277 reconnects after a reboot on Android 15+, and 4cc5254b6 says why ADB mode failed. Nothing in `servermanager/` or `adb/` handles the server dying on wake from doze, and Shizuku #2475 is still open.
  Acceptance: on the S22, after the phone sits in doze and wakes, NG notices the dead server and reconnects or says why it can't.
  Unblocked 2026-11-07: the S22 from the Shizuku report is here, and doze can be forced with `dumpsys deviceidle force-idle`. Pair it with the ROADMAP P1 reboot reconnect check.
  Research 2026-11-13: Shizuku #2596 (2026-10-14) reports the same disconnects on an S25 Ultra with One UI 8.5, so run the check on the S25 as well as the S22.
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
  Research 2026-11-13: the scanner run should also cover what the November issue drain added after the label commits: notification filter rules and history, toast history, the overlay detector bubble, Local sync, the permission watcher and the Quick Settings tiles.
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
  Research 2026-11-14: Hail #266 (open since 2024-09-06) asks for one widget tile that freezes a chosen set of apps. A "freeze my selection" action on the widget covers it.
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
  Research 2026-11-13: arXiv 2606.21487 (2026-06-19) found 26 leaked signing certificates tied to 278 real apps but published no certificate list, so a "leaked key" chip has no data to use yet. Keep this item to signature schemes.
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
  Research 2026-11-13: LibChecker 2.5.5 (2026-11-05) added AppFunctions, v3.2 signature and R8 mode detection, three more candidates for this item.
  Research 2026-11-14: LibChecker identifies v3.2 as a scheme without verifying it. Roadmap_Blocked's "Show APK Signature Scheme v3.2" waits on a verifier, but a row that says "v3.2 block present, not verified" doesn't need one, so that part can land here.
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

### P2

- [ ] P2: Move APK icons and embedded media art into the isolated thumbnail process
  Why: images, SVGs, PDFs, fonts, EPUB covers, APK bundle icons, J2ME icons and ODF previews now decode in the isolated `:thumbnails` process, but three thumbnail paths still read untrusted files in the app process. A plain APK's icon (and the base APK's icon in an .apks without icon.png) goes through `PackageManager.getPackageArchiveInfo`. That .apks fallback also runs when the isolated decoder crashed or ran out of time on the same file, so a bundle built to kill the decoder gets a second try in the app process. Cover art embedded in audio and video files is decoded with `BitmapFactory.decodeByteArray` with no size limit. Before that, `FmIcons.getUsableFile` copies an APK only root can read, or one inside an opened archive, into the global FileCache with no size cap, from both thumbnail threads, and that cache isn't thread-safe.
  Evidence: `fm/icons/FmIconFetcher.java` (`FmIcons.generateApkIcon`, `FmIcons.getApksBaseIcon`), `fm/icons/FmIcons.java` (`getUsableFile`), `compat/ThumbnailUtilsCompat.java:48-50` and `:84-86`.
  Touches: `fm/icons/ThumbnailDecoder.java` (new kinds), `FmIconFetcher.java`, an APK resource-table and icon reader that works from a file descriptor (the isolated process can't hand PackageManager a path), `MediaMetadataRetriever.setDataSource(FileDescriptor)` inside the decoder.
  Acceptance: no file manager thumbnail decodes file contents in the app process; APK icons and embedded art come back through `IsolatedThumbnails` within the same byte, pixel and time budgets; malformed APKs and media files give the generic icon, and so does an .apks whose isolated decode crashed or timed out; an APK that has to be copied first goes through a per-call cache with the same 64 MB cap; unit tests cover each new kind and its malformed input.
  Complexity: L

- [ ] P2: Give thumbnails a deadline for reading the file, not only for drawing it
  Why: the isolated decoder gets 5 seconds, but the app-side reads before it have no limit. On a document from another app's provider, `openSeekable` (`Path.openFileDescriptor`), the copy in `IsolatedThumbnails.copyAtMost(Path, ...)`, the 10 KB type sniff in `PathContentInfoImpl.fromPath` and `Path.length()` all block for as long as the provider does. A network share that stops answering (SMB or SFTP over SAF with the connection gone) ties up both `ThumbnailThreads` workers, and no folder gets thumbnails again until those calls return. A blocked read can be ended by closing its descriptor (libcore wakes the reader), but a provider that hangs inside `openDocument` can't be interrupted from the calling thread, so the open has to happen off the thumbnail threads.
  Evidence: `fm/icons/IsolatedThumbnails.java` (`decode(Path, ...)`, `openSeekable`, `copyAtMost(Path, ...)`), `fm/icons/FmIconFetcher.java:97-100`, `io/PathContentInfoImpl.java` (`fromPath`), `FmIconFetcher.ThumbnailThreads`.
  Touches: `IsolatedThumbnails.java` and `FmIconFetcher.java` (source reads on a small bounded pool with a per-file deadline, closing what they opened when it passes).
  Acceptance: a source whose open or read never returns gives the generic icon within the deadline, the thumbnail threads go on to the next row, at most a fixed number of reader threads can be stuck at once (past that, rows get the generic icon right away), and a reader that does return late closes what it opened. Unit tests use a stream that blocks forever and an open that blocks forever.
  Complexity: M

- [ ] P2: Apply App Details tab lists in arrival order
  Why: the Components, Other and Permissions tabs hand each list from the view model to a background thread before it reaches the adapter, so two lists posted close together (a reload right after a change, or a search while a reload runs) can land in the wrong order and leave the older one on screen. The Overlays tab now applies its list on the main thread as it arrives.
  Evidence: `details/AppDetailsComponentsFragment.java:554,1025`; `details/AppDetailsOtherFragment.java:199`; `details/AppDetailsPermissionsFragment.java:494,938,960` (`ThreadUtils.postOnBackgroundThread` inside `setDefaultList`); `details/AppDetailsOverlaysFragment.java` for the fixed shape.
  Touches: those three fragments' adapters, the permission checks they run before showing a list (move them to the view model or compute them once per load), tests.
  Acceptance: each tab applies lists in the order LiveData delivers them; no permission or binder call runs on the main thread; a test posts two lists back to back and sees the second on screen.
  Complexity: M

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

### P2

- [ ] P2: Log permission watcher drift to the app change feed
  Why: the watcher (`permission/watcher/`) reports a rule mismatch it couldn't fix only as a notification, so once that's dismissed there's no record of which app drifted from the user's rule or when.
  Evidence: `permission/watcher/PermissionWatchWorker.java:49-52` passes the report to `PermissionWatchNotifier.notify` and nothing else; `permission/monitor/AppChangeFeedStore.java` already keeps the install and update history.
  Touches: `permission/watcher/PermissionWatchWorker.java`, `permission/monitor/AppChangeFeedStore.java`, the feed's type labels, a worker test.
  Acceptance: every drift in a report, fixed or not, appends one feed entry naming the app, the permission or op, the rule's state and the state found; an entry for a drift that was fixed says so; a test with a faked rule DAO and grant lookup asserts the entries and fails with the append removed.
  Research 2026-11-14: AppOpsNext 1.6.7 (2026-11-14) lists every permission that no longer matches what it last set, with "restore previous" and "keep current" per entry. Those two actions on a feed entry would make the record useful, not just visible.
  Complexity: S

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
  Research 2026-11-13: Dhizuku 2.12.0 (2026-06-24) added profile owner support and 2.13.0 (2026-10-30) an off-by-default setting that lets clients transfer or clear ownership. Its default APK needs API 23, which the copy should say.
  Complexity: S

## Research-Driven Additions (2026-09-25)

### P1

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
  Research 2026-11-13: `ApplicationExitInfo.AnrInfo` and `ActivityManager.registerAnrWarningListener` are both in `core/api/current.txt` at android-17.0.0_r1, so the names here are final.
  Complexity: M

- [ ] P2: Add Shizuku OEM failure diagnostics
  Why: Android 16 MediaTek and HyperOS failures can look like generic privilege loss, making it impossible to tell whether the app, provider, binder, or OEM service path failed.
  Evidence: Shizuku API 13.1.5; Shizuku API pull request #299; current Shizuku bridge and Privilege Health diagnostics.
  Touches: Shizuku bridge diagnostics, Privilege Health, support bundle, binder-death classification, OEM fixture tests.
  Acceptance: a failed Shizuku operation records SDK, manufacturer, provider package and version, binder alive state, permission state, remote UID, and one stable failure code without device identifiers or command contents; Privilege Health distinguishes provider missing, permission denied, binder dead, incompatible provider, and operation failure; no open upstream patch is vendored by this item; tests redact user data and cover MediaTek or HyperOS signatures only as diagnostic inputs.
  Research 2026-11-13: realme's ColorOS 16 shell lacks `CREATE_VIRTUAL_DISPLAY` (ccch1mneyyy/dsh-Go #1153), and ColorOS 16 changed dumpsys output (Verlintas/NOIR #19). Both belong in the OEM codes, and fork discussion #5 is from a ColorOS 16 user.
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
  Research 2026-11-13: Obtainium 1.7.4 (2026-11-01) added a per-app install method, so a preset may need a per-app default as well as a global one.
  Complexity: M

## Audit Findings — 2026-09-25

### P1

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
  Research 2026-11-13: upstream b11644c3b makes the no-root shell skip the probe. NG's `runner/NormalShell.java` already does that, so only the three screens listed here remain.

- [ ] P2 — Import PKCS #8 keys off the main thread
  Category: Responsiveness
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/KeyPairImporterDialogFragment.java:135-147`; `app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/KeyStoreUtils.java` (`getKeyPair(Context, Uri, Uri)`)
  Problem: The PKCS #8 and PEM path reads both files in full, with no size limit, and parses the key and certificate on the UI thread, while the keystore paths beside it already run in the background.
  Evidence: Found on 2026-09-26 while routing keystore imports through `KeyStoreImportPolicy`; the OK handler calls `KeyStoreUtils.getKeyPair(mActivity, mPk8File, mKsOrPemFile)` directly, and that method uses `IoUtils.readFully(pk, -1, true)`.
  Fix: Run the import on a worker, bound both reads to a small documented ceiling, and post the result back to the dialog.
  Acceptance: A slow or oversized key or certificate file never blocks the UI thread; files above the ceiling fail with a classified error; the existing successful import still selects the key pair.
  Confidence: Verified
  Effort: S

## Research-Driven Additions (2026-09-30)

Sources and reasoning: RESEARCH.md (2026-09-30).

### P1

- [ ] P1: Check the wireless reconnect after a reboot on a phone
  Why: the upstream wireless fixes are ported, and on the S22 on 2026-09-30 AppManagerNG paired, connected over Wireless debugging and came back on its own after Wi-Fi went off and on. The start at boot, which Android 15 only allows as a special use service, has host tests but hasn't run on a phone yet.
  Evidence: `self/BootReceiver.java`, `servermanager/WifiWaitService.java`; upstream `22d439d61` (connect on boot).
  Touches: nothing unless it fails.
  Acceptance: with Wireless debugging as the mode, the network always allowed for Wireless debugging and the server stopped, `adb reboot` the S22 or S25 and unlock it; the server is running within two minutes of the unlock without the app being opened. Then join an access point Wireless debugging isn't allowed on yet: one system prompt and one AppManagerNG notification come up, not one per retry, and the server starts once the network is allowed. (Already seen on the S22 on 2026-11-15: the log shows the echo only as "(echo of the launch command, not logged)" and reaches "Success!", and the server came back over Wireless debugging from the app change job without the app being opened.)
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

## Research-Driven Additions (2026-11-13)

Sources and reasoning: RESEARCH.md (2026-11-13). The four crash fixes come first, then the Android 17 items, then the rest.

### P2

- [ ] P2: Lift a deny left on a UID when an internet block change fails
  Why: when a block or unblock fails partway (`InternetAccess.java` `failedStatus`, called from the SecurityException, RemoteException and platform catches around line 313-322), the saved rule is dropped and the status says a deny may still be on the UID. Nothing records that UID, so `applyAll()` never lifts the deny before a reboot, and `Status.canChange()` returns false, so the app's INTERNET switch stays locked with the app offline.
  Evidence: found by the 2026-11-15 review of the internet block UID tracking. Predates that change.
  Touches: `rules/internet/InternetAccess.java` (record the UID a failed change may have left denied, have `applyAll` clear it once a privileged mode is up, and let the switch unblock it), `InternetAccessTest.java`.
  Acceptance: WHEN a block change fails after the deny may have reached the UID, the system SHALL keep that UID on record, SHALL lift the deny at the next `applyAll` with a privileged mode, and SHALL leave the INTERNET switch usable to turn the block off. A Robolectric test drives a failed change, then `applyAll`, and reads the firewall seam.
  Complexity: M

- [ ] P2: Keep the install queue when the installer is recreated after unlocking
  Why: the queue is a plain list on `PackageInstallerActivity`, and `handleNewIntent` calls `setIntent` with each APK or confirmation it handles. When the activity is recreated after the lock screen was passed (a rotation, or the process stopped while a confirmation waits), `onAuthenticated` queues `getIntent()` again. That's the last intent handled, so every other APK still waiting is dropped, and on the confirmation path a confirmation that was already handled is launched again. The manifest gives the activity no `configChanges`, so a rotation does recreate it.
  Evidence: `apk/installer/PackageInstallerActivity.java` (`mApkQueue`, `setIntent` in `handleNewIntent`, `onAuthenticated`). Found by the 2026-11-13 review of the lock-screen fix, which only covers recreation before unlocking.
  Touches: `PackageInstallerActivity.java` (save the queued sources and senders in `onSaveInstanceState` or keep the queue in `PackageInstallerViewModel`, and don't queue `getIntent()` again on a restore), `PackageInstallerActivityTest.java`.
  Acceptance: with two APKs queued and the first one's dialog up, recreating the activity keeps both, each installs once, and a confirmation that was handled isn't launched again. A Robolectric test recreates the activity at each step.
  Complexity: M

- [ ] P2: Keep one audio player across a rotation
  Why: `AudioPlayerActivity.onCreate` shows a new `AudioPlayerDialogFragment` every time, including when Android recreates the activity after a rotation, so the restored dialog and a new one stack. The restored one builds a fresh `MediaPlayer` while the ViewModel's prepared LiveData still says true, so it calls `startPlayingMedia()` on a player that never had a data source.
  Evidence: `viewer/audio/AudioPlayerActivity.java:31-32`, `viewer/audio/AudioPlayerDialogFragment.java:110-118` (new player plus the prepared observer).
  Touches: `AudioPlayerActivity.java` (show only when `savedInstanceState == null` or no fragment has the tag), `AudioPlayerDialogFragment.java` (prepare the current track again on a new player), a Robolectric test.
  Acceptance: rotating while a track plays leaves one dialog that keeps playing the same track from about the same spot. A test recreates the activity and finds one fragment with that tag.
  Complexity: S

- [ ] P2: Judge Shizuku on Android 17 by whether the server answers
  Why: NG warns every Shizuku user on Android 17 and points them at Wireless ADB, because `MIN_ANDROID_17_COMPATIBLE_VERSION` is null. That's right for the official 13.6.0, which shows an empty authorized-apps list there. It's noise for anyone on a Shizuku-API server that works, such as Porter's compatibility mode (SD Maid SE 2.2.0, 2026-09-29) or one of the forks awesome-shizuku lists.
  Evidence: `shizuku/ShizukuBridge.java:50` and `:342-357`, `onboarding/OnboardingFragment.java:350`, `settings/PrivilegeHealthPreferences.java:345`. RikkaApps/Shizuku #2180 (25 upvotes) and #2681 (2026-11-11). r/ShizukuforAndroid 1xbl3af (2026-10-21) shows users split across forks.
  Touches: `ShizukuBridge.java`, the two warning call sites, `onboarding_mode_shizuku_android17_warning` and `privilege_health_shizuku_android17_warning`, `ShizukuBridgeTest.java`.
  Acceptance: on API 37 the warning shows when the server is missing, its binder is dead, or NG's permission request can't complete, and stays off when the binder answers and NG holds the permission. The copy names no third-party fork until the owner picks one (RESEARCH.md, Open Questions). `ShizukuBridgeTest` covers each case. Related: "Validate privileged-mode binding on Android 17".
  Complexity: S

- [ ] P2: Say why a work profile is out of reach in ADB mode
  Why: when a work profile sets `DISALLOW_DEBUGGING_FEATURES`, which managed profiles often do, Android refuses shell calls for that user. ADB mode then lists the main profile's apps and nothing from the work profile, with no reason. Upstream #2059 (2026-10-03, One UI 8.5, wireless ADB) is this report.
  Evidence: `UserController.handleIncomingUser` at android-16.0.0_r4 refuses shell for a user with that restriction. `users/Users.java`.
  Touches: `users/Users.java`, the main list's user filter, Privilege Health, a host test.
  Acceptance: in ADB mode, or Shizuku running as shell, a profile NG can't reach shows in the user picker and Privilege Health with a short reason instead of vanishing. The reason comes from the restriction where it's readable, else from the `SecurityException`. Root mode is unchanged. Related: "Let ADB mode start in a work profile" is about NG running inside the profile.
  Complexity: S

- [ ] P2: Freeze instead when Android refuses a system app uninstall
  Why: Android 17 refuses `pm uninstall --user` of a system app unless the caller is root, so rootless debloat fails there. UAD-ng made disable its default in v1.3.0 (2026-11-02) for this reason (issues #1426 and #1436), and HN 49193173 discussed the same change. NG's rootless debloat runs that exact command.
  Evidence: `PackageManagerShellCommand.java` at android-17.0.0_r1. `debloat/RootlessDebloat.java:33-63` builds `pm uninstall --user`. Callers: `batchops/BatchOpsManager.java:1211-1224` and `debloat/DebloaterActivity.java:480`. The freeze fallback at `DebloaterActivity.java:617` (`rootless_debloat_oem_disable_fallbacks`) covers a fixed OEM list only.
  Touches: `RootlessDebloat.java`, `BatchOpsManager.java`, `DebloaterActivity.java`, the removal ledger, host tests with a recorded refusal.
  Acceptance: on API 37 in ADB or Shizuku mode, a refused system app uninstall offers freeze for the same apps as an action on the result, records which action ran in the ledger, and doesn't count the refusal as a removal. API 36 and below behave as today. A host test feeds the API 37 failure text and checks the fallback.
  Complexity: M

- [ ] P2: Leave other tools' pauses alone
  Why: NG suspends with a null `SuspendDialogInfo`, and in ADB mode the suspending package is the shell. A pause set by another shell tool, or by hand over adb, looks the same, so NG's unfreeze can lift a pause it never set. Thor fixed this in PR #599 by recording its own suspensions and keeping outside ones.
  Evidence: `compat/PackageManagerCompat.java:693-711`, `utils/FreezeUtils.java:123` and `:130`, trinadhthatakula/Thor PR #599.
  Touches: `PackageManagerCompat.java`, `FreezeUtils.java`, the freeze state store, host tests.
  Acceptance: NG records which packages it suspended, per user. Unfreeze and unfreeze-all lift only those. A package someone else suspended shows as paused by another app, and lifting it is its own action. A host test covers an outside suspension surviving unfreeze-all.
  Complexity: M

- [ ] P2: Get ready for target SDK 37
  Why: at target 37, Android 17 enforces the local network permission (TCP to the LAN times out and UDP gets EPERM without it) and applies background activity launch rules to `IntentSender` starts. It also adds the normal permission `USE_LOOPBACK_INTERFACE`, which will guard 127.0.0.1 once `full_loopback_protections_enabled` is enforced. NG uses all three: Local sync and wireless ADB on the LAN, PendingIntents from notifications and the installer, and its local server on loopback.
  Evidence: developer.android.com Android 17 behavior changes for apps targeting 17, and the local network permission page. `core/api/current.txt` and `permissions/flags.aconfig` at android-17.0.0_r1. `AndroidManifest.xml:16` already declares `ACCESS_LOCAL_NETWORK`.
  Touches: `AndroidManifest.xml`, `sync/lan/`, `adb/`, `IntentSender` and `PendingIntent` call sites, a target 37 test run.
  Acceptance: `USE_LOOPBACK_INTERFACE` is declared now (a normal permission, so nothing to ask). A build targeting 37 passes the unit suite, and on an API 37 image Local sync, wireless pairing and the local server all connect, and every notification action and installer prompt still opens. Raising the shipped target stays a separate decision.
  Complexity: M

- [ ] P2: Check Android/data access in ADB mode on Android 17 before backing it up
  Why: one report says Android 17 on a Galaxy S25 Ultra changed Android/data so shell, and so Shizuku, can no longer read other apps' folders there (r/ShizukuforAndroid 1xmoo42, 2026-11-03). NG's ADB-mode backup reads those folders. If the report holds, a backup could finish as complete while missing external data.
  Evidence: r/ShizukuforAndroid 1xmoo42. `backup/BackupDataDirectoryInfo.java:112` and `:176` handle `Android/data/`. Upstream #2068 (2026-10-25) asks to move files out of Android/data in ADB mode.
  Touches: `BackupDataDirectoryInfo.java`, the backup's external data step, the file manager's error text, tests.
  Acceptance: on an API 37 image, check whether shell can list and read another app's Android/data folder, and note the result in RESEARCH.md's Open Questions. Where it can't, an ADB-mode backup that includes external data says which folders it skipped and why instead of finishing as complete, and a host test covers that result.
  Complexity: M

- [ ] P2: Add ColorOS 16 dumpsys output to the parser fixtures
  Why: ColorOS 16 changed dumpsys output enough to break another app's parser (Verlintas/NOIR #19, 2026-11-11). NG reads activity, job and memory state from dumpsys too, and its fixtures are AOSP only.
  Evidence: Verlintas/NOIR #19. `compat/ActivityManagerCompat.java`, `compat/JobSchedulerCompat.java`, `details/info/AppMemoryInfoParser.java`. Fork discussion #5 is from a ColorOS 16 user.
  Touches: those parsers and their tests, fixtures taken from the samples in NOIR #19 or a ColorOS 16 device.
  Acceptance: ColorOS 16 fixtures parse to the same fields as the AOSP ones, or the parser marks a field unknown instead of returning a wrong value. One test per parser.
  Complexity: S

- [ ] P2: Mark permissions an update brought in already granted
  Why: Android grants a new permission without asking when the app already holds another in the same group. A 2026 study of 2.24 million apps with several versions found 17% gained permissions this way (arXiv 2605.27667, DIMVA 2026). NG's watcher reports new dangerous permissions after an update but not which ones arrived granted, and that's the part the user never saw.
  Evidence: arXiv 2605.27667. `permission/monitor/PermissionChangeMonitor.java:95-132`. `permission/monitor/PermissionSnapshot.java` stores no grant state.
  Touches: `PermissionChangeMonitor.java`, `PermissionSnapshot.java`, `PermissionChangeDiff.java`, the notification text, host tests.
  Acceptance: after an update adds a permission to a group the app already held, the notification and its App Change Feed entry mark it granted without asking, with a revoke action. Snapshots saved before this change still load. A host test covers both.
  Complexity: S

- [ ] P2: Take the dependency updates that keep API 21
  Why: several pinned libraries have newer releases that still support minSdk 21, and upstream already moved to Robolectric 4.17 (9dd048f26, 2026-10-19).
  Evidence: Robolectric 4.17, or 4.18 (2026-11-13) once it's two weeks old. gson 2.15.0 (2026-10-24), smali 3.0.10 (2026-08-20), zstd-jni 1.5.7-26 (2026-11-08), AndroidHiddenApiBypass 6.2 (2026-10-09), BouncyCastle 1.87 (2026-11-12) once it's two weeks old. Guava stays at 32.1.3-android because 33.5.0 needs API 23.
  Touches: `versions.gradle`, the backup round-trip tests, the reproducible-build check.
  Acceptance: each bump lands on its own with the unit suite and the backup round-trip tests green and the APK's minSdk still 21. `versions.gradle` says why Guava is held.
  Research 2026-11-14: zstd-jni is now 1.5.7-28 (2026-11-13). The test-only org.json 20251224 (`versions.gradle:33`) is inside GHSA-ghc9-756g-vrcv, fixed in 20260719, and 20260814 is the newest. It never ships in the APK, but the bump is free.
  Complexity: S

- [ ] P2: Read the per-user disabled state the same way on a full load and a refresh
  Why: a full load (`PackageUtils.mergeApplicationItems`) records no enabled or disabled users for a row, but a one-package refresh (`MainViewModel.getNewApplicationItem`) does. An app disabled for one user and enabled for another gets the per-user tint on its UID after a refresh and loses it on the next full load.
  Evidence: `utils/PackageUtils.java` `mergeApplicationItems`, `main/MainViewModel.java` `getNewApplicationItem`, `MainListUidFormatter.isHighlighted`. Found while making the two paths agree on uninstalled users (2026-11-14).
  Touches: `mergeApplicationItems`, `getNewApplicationItem`, `PackageUtilsMergeTest`, `MainViewModelNewApplicationItemTest`.
  Acceptance: for the same database rows, both paths give the row the same `userIds`, `disabledUserIds` and `uninstalledUserIds`, and one test runs both over an app disabled for one of two users.
  Complexity: S

- [ ] P2: Stop passing log messages in as format strings
  Why: `Log.v/d/i/w/e(tag, format, args)` formats its first string, so a call like `Log.w(TAG, "Error: " + e.getMessage())` throws `MissingFormatArgumentException` or `UnknownFormatConversionException` when the message holds a `%`, such as a URL-encoded path. The SSAID read in `AppDb.updateVariableData` did this and stopped a package refresh on the test host. About 190 such calls remain in about 70 files.
  Evidence: `logs/Log.java` (`String.format(Locale.ROOT, format, args)`); `rg 'Log\.[vdiwe]\(TAG, "[^"]*" \+' app/src/main`.
  Touches: those call sites, or a `Log` overload that takes a plain message, and a test that logs a `%`.
  Acceptance: no production call builds the format string from runtime text, a message holding `%3A` logs as written, and the change keeps each call's level and throwable.
  Complexity: M

### P3

- [ ] P3: Name a snapshot section that failed to export
  Why: a section whose serializer throws (log filters, file-manager favorites, freeze methods, signer pins) is bundled empty and counted 0, and the manifest's `excluded` list doesn't mention it. A damaged signer pin list exports as "signer pins: 0", which reads as if there were none.
  Evidence: `snapshot/SnapshotBundle.java` `tryExport` and `writeTo`.
  Touches: `SnapshotBundle.writeTo`, the manifest's `excluded`, the export toast, the import preview, tests.
  Acceptance: a section that fails to serialize is listed in `excluded` by name, the export toast says it was left out, and the import preview shows it as missing instead of empty.
  Complexity: S

- [ ] P3: Tie a reinstall's signer pin check to the bytes it installs
  Why: a reinstall checks the pin, uninstalls, then the installer service reads the APK again. An APK opened from a file another app can write could be swapped in between. The install-time check still refuses a wrong signer, so the pin holds, but by then the old app and its data are gone. LAN sync Replace has a second gap of the same kind: its check signs the copies when signing is on in Settings, but the installer screen it hands off to lets the user turn signing on or off in its options after the uninstall already ran.
  Evidence: `PackageInstallerViewModel.uninstallUnlessSignerPinRefuses`, `PackageInstallerCompat.install(ApkFile, ...)` `checkSignerPins` and `sessionMatchesSignerPinCheck`; `LanSyncViewModel.signerPinRefusal` then `PackageInstallerActivity` `displayInstallerOptions`.
  Touches: the reinstall flow, `PackageInstallerCompat` (accept an expected SHA-256), the LAN hand-off (carry the checked signing choice and hash, or uninstall from inside the installer), tests.
  Acceptance: the hash from the pre-uninstall check travels with the reinstall, and the session is refused unless its bytes match it. A LAN Replace installs with the signing choice its check used. A test swaps the file between the check and the install and sees the refusal name the change.
  Complexity: S

- [ ] P3: Stop a reinstall's uninstall result from replaying after rotation
  Why: `mPackageUninstalledLiveData` is a plain `MutableLiveData`, so turning the phone after a reinstall's uninstall hands the new activity `true` again and `install()` runs a second time.
  Evidence: `PackageInstallerViewModel` `mPackageUninstalledLiveData`; `PackageInstallerActivity` `packageUninstalledLiveData().observe`.
  Touches: `PackageInstallerViewModel`, `PackageInstallerActivity`, a Robolectric recreate test.
  Acceptance: after an uninstall result is handled, recreating the activity doesn't start another install or show the failure dialog again.
  Complexity: S

- [ ] P3: Add cooldown and batch delivery to notification filter rules
  Why: BuzzKill builds its rules around a cooldown (let one through, then mute repeats from that app for a while) and batch delivery (hold matches and deliver them together). NG's filter has Allow, Silent, Vibrate and Block, and fork #32 asked for taming noisy apps.
  Evidence: `notificationfilter/NotificationFilterRule.java:30-38`, buzzkillapp.com, fork issue #32.
  Touches: `NotificationFilterRule.java`, the listener service, the rule editor, an alarm or WorkManager release for batches, tests.
  Acceptance: a rule can mute repeats for a set number of minutes after the first one, and a batch rule delivers held notifications as one summary at set times. Both survive a restart, and host tests cover the timing.
  Complexity: M

- [ ] P3: Set one language for many apps at once
  Why: upstream #2027 (2026-08-16) asks for a per-app language switcher. NG sets one app's language from App Info, and people who run several apps in a second language have to do them one by one.
  Evidence: upstream #2027. `compat/AppLocaleManagerCompat.java`, used only at `details/info/AppInfoFragment.java:3598-3649`.
  Touches: a new batch operation, `AppLocaleManagerCompat.java`, the batch options dialog, tests.
  Acceptance: select several apps in the main list, pick a language or the system default, and each app that declares that locale gets it. Apps that don't support per-app languages are listed as skipped. Works in every privileged mode on API 33+, and the change goes in the ledger.
  Complexity: M

- [ ] P3: Let an app skip Android 17's memory limiter
  Why: Android 17 adds per-app memory limits and an `am memory-limiter ignore <uid>` shell command to lift them. NG's exit history already spots kills by the memory limiter, so the switch can sit next to the evidence.
  Evidence: developer.android.com/tools/adb (`memory-limiter ignore uid|none|all`), Android 17 behavior changes (app memory limits), `details/info/ExitHistoryDiagnostics.java`.
  Touches: `ExitHistoryDiagnostics.java`, App Info's exit history, the privileged command runner, tests.
  Acceptance: on API 37 in root, ADB or Shizuku mode, App Info offers to lift the limit for an app after a memory limiter kill, runs the command for its uid, and can put the limit back. Hidden below API 37. Note that `none` clears every app's exemption, so undo has to restore the others.
  Complexity: S

- [ ] P3: Edit system, secure and global settings with a guarded undo
  Why: Thor added a settings editor in v1.97.1 (2026-10-06) that reads each value back after writing and keeps an undo, and upstream added one in c6728f56c. NG already writes `Settings.Global` through its server for Wireless debugging.
  Evidence: Thor releases, upstream c6728f56c, `servermanager/WirelessDebuggingSwitch.java`.
  Touches: a new settings screen, the server's settings calls, the ledger, tests.
  Acceptance: search keys in the three tables, see the old and new value side by side while editing, read the value back after the write and undo it from the ledger. A key Android refuses shows the reason. Works in root, ADB and Shizuku modes.
  Complexity: M

- [ ] P3: Offer to turn off Wireless debugging when NG's server stops
  Why: banking and UPI apps refuse to run while Wireless debugging is on (r/UPI 1w7zsmt, 2026-09-05, 48 points), and thedjchi's Shizuku fork added a switch for it. NG already writes that setting.
  Evidence: r/UPI 1w7zsmt, thedjchi/Shizuku releases, `servermanager/WirelessDebuggingSwitch.java`.
  Touches: `WirelessDebuggingSwitch.java`, a setting, the server stop path, tests.
  Acceptance: with the setting on, stopping NG's server turns Wireless debugging off, and starting ADB mode turns it back on where NG is allowed to. Off by default, and a host test covers both directions.
  Complexity: S

- [ ] P3: Back up saved Wi-Fi networks without root
  Why: Wi-Fi backup is root only in NG, while Swift Backup backs up saved networks with Shizuku on Android 11 and later. Upstream #568 (+3) asks for Wi-Fi networks in backups.
  Evidence: `backup/SystemDataBackup.java:66` (`SelfPermissions.isSystemOrRoot()`), swiftbackup.app, upstream #568.
  Touches: `SystemDataBackup.java`, the server's Wi-Fi calls (find which ones shell may make on API 30+), restore, tests.
  Acceptance: in ADB or Shizuku mode on API 30+, a backup includes saved networks, encrypted like the rest of the backup, and restore adds them back. Where shell lacks the permission, the backup says Wi-Fi was skipped. Host tests cover the format.
  Complexity: M

- [ ] P3: Turn on memory tagging
  Why: upstream accepted `android:memtagMode="async"` in #2058 (2026-09-25). On a device with MTE it catches memory bugs in the native code NG ships at little cost, and does nothing elsewhere.
  Evidence: upstream #2058. `AndroidManifest.xml` has no `memtagMode`. NG already shows other apps' MTE state (`details/info/MemoryTaggingInfo.java`).
  Touches: `AndroidManifest.xml`, one check on an MTE-capable device or image.
  Acceptance: the manifest sets `memtagMode="async"`, and a pass through the main screens plus a backup round trip on an MTE-enabled device shows no tag faults.
  Complexity: S

- [ ] P3: Show an app archived for another user under its own name and icon
  Why: the main list reads an archived app's name and icon through `PackageManager.getArchivedPackage`, which only answers for this user, so an app archived for a work profile or second user is marked Archived but still shows its package name and the generic icon.
  Evidence: `apk/installer/AppArchiveManager.java`; AOSP `PackageArchiver` and `IPackageManager.getArchivedPackage(String, int)` take a user ID behind a hidden API.
  Touches: `AppArchiveManager`, the privileged-server path for other users, `ApplicationItem.isKnownArchived`, `ShadowArchivedPackages`, a work-profile check on API 35 or later.
  Acceptance: with root, ADB or Shizuku, a row archived only for another user shows that user's archived name and icon; in no-root mode it keeps the package name as now; a test covers both modes.
  Complexity: M

- [ ] P3: A settings link to a key with no page leaves the two-pane right side empty
  Why: in dual pane, Settings fills an empty right pane with Language only when the path has no first key. A link whose first key doesn't exist or is a plain switch (`app-manager://settings/bogus`) skips that fill, opens nothing, and leaves the right pane blank. A bare `app-manager://settings/` sent after startup resets the saved path while the right pane keeps its page, so a later recreate shows Language instead.
  Evidence: `settings/SettingsActivity.java` (`defaultPref` in `onAuthenticated`, `onNewIntent`). Found by the 2026-11-14 review of the settings restore fix. HEAD before that fix behaved the same.
  Touches: `SettingsActivity`, `SettingsActivityRecreateTest`.
  Acceptance: in dual pane a link that opens no page falls back to the default fill, an empty link leaves the saved path alone, and both have a Robolectric case.
  Complexity: S

## Research-Driven Additions (2026-11-14)

Sources and reasoning: RESEARCH.md (2026-11-14).

### P2

- [ ] P2: Stop ServerLoopbackBindTest failing when the machine is busy
  Why: `noOtherAddressOfThisMachineReachesIt` failed once on "loopback still reaches it" while lint ran beside it (2026-11-15, Gradle 9.9.1 move), then passed alone in 6 seconds. The release gate runs the server tests, so a flake there stops a release for nothing.
  Evidence: `server/src/test/.../ServerLoopbackBindTest.java:49`. Each probe of another local address waits out its 2-second timeout (three of them on this machine), and the final 127.0.0.1 connect also has 2 seconds. A busy run can miss that window, or a probe can leave the listener in a state the loopback connect hits.
  Touches: `ServerLoopbackBindTest`.
  Acceptance: WHEN the server tests run beside a full lint pass, the loopback assertion SHALL pass on five back-to-back runs, and a server bound to 0.0.0.0 SHALL still fail the test.
  Complexity: S

- [ ] P2: Say when Advanced Protection has taken the accessibility service away
  Why: on Android 17, Advanced Protection limits AccessibilityService to "verified applications categorized as Accessibility Tools". NG's toast history, top-app bubble, UI tracker and no-root cache clearing all run on its one service, and the screens still tell the user to turn the service on.
  Evidence: The Hacker News, 2026-10-02, quoting Google. `compat/AdvancedProtectionCompat.java` already reads the state. Privilege Health's row only mentions sideloading (`strings.xml:2966`). Toast history and the bubble say "Turn on the accessibility service" (`strings.xml:2159`, `:3419`). `res/xml/accessibility_service_config.xml` has no `isAccessibilityTool`, and it shouldn't get one, because NG isn't an assistive tool.
  Touches: `compat/AdvancedProtectionCompat.java`, `accessibility/NoRootAccessibilityService.java` (or one shared availability helper), the toast history and top-app bubble prompts, UI tracker's prompt, `settings/PrivilegeHealthPreferences.java`, strings, tests.
  Acceptance: first, on an API 37 image or phone with Advanced Protection on, record in the repo `CLAUDE.md` whether an already-enabled NG service is switched off or can only no longer be turned on. Then, on API 37+ with Advanced Protection on, Privilege Health's row names the features it stops (toast history, the bubble, UI tracker, no-root cache clearing), and those screens say Advanced Protection is the reason instead of sending the user to accessibility settings. With Advanced Protection off nothing changes. A Robolectric test drives the helper through on, off and unavailable.
  Complexity: S

- [ ] P2: Find why some apps won't update through NG's installer but will through Android's
  Why: a #22 tester found WhatsApp and about seven other apps failed to update through NG while the same APKs installed through Android's own installer. The fallback and the blocked message have shipped, but nobody knows the cause, so it can't be fixed or explained.
  Evidence: fork #22, antiframe's comment of 2026-11-09, item 8. The 2026-11-13 reply says the fallback hasn't run between two phones. Local sync sends the host's base and split APKs (`sync/lan/AndroidPackageCatalog.java:172-173`), so splits that don't fit the receiving phone (density, ABI or language) are one candidate, and Android 14+ update ownership is another (`apk/installer/InstallerOptions.java`, `mRequestUpdateOwnership`).
  Touches: `apk/installer/PackageInstallerCompat.java` (status and message mapping), `sync/lan/` (which files get sent), the installer fallback, strings.
  Acceptance: on the S22 or S25, update a Play-installed WhatsApp from a Local sync copy (and from a plain same-signer split set), capture the `PackageInstaller` status code and message in the repo `CLAUDE.md`, and name the cause. If it's split fit, Local sync sends or the installer picks only the splits the receiving phone can use. If it's update ownership, the installer says so and offers the fallback. A unit test covers the status mapping for that cause. The #22 reply waits until it's seen working on a phone.
  Complexity: M

- [ ] P2: Bring in the translations upstream added after the fork
  Why: NG's locale files stopped at the 2026-04-30 fork while upstream's Weblate kept going. About 2,100 strings whose English NG never changed are translated upstream but show in English here, 1,085 of them Swedish, where NG has only 44. This doesn't wait on the hosted translation project in Roadmap_Blocked.md, because upstream's Weblate already reviewed these strings and they come under the same GPL.
  Evidence: upstream merged 53 Weblate commits on 2026-05-29 (540c30cac3 sits 78 commits past NG's base 3d11bcb). Upstream master dc4154f4c against NG main 7ff61d2c3, counting only keys whose English text is identical in both and reading NG's base locales first: Swedish 1,085 missing, Latvian 307, Indonesian 225, Spanish 159, Ukrainian 100, and 271 more across German, French, Italian, Polish, Portuguese, Brazilian Portuguese, Turkish and Chinese. Another 366 existing translations differ from upstream's current text. NG's `values-sv/strings.xml` hasn't changed since the initial commit.
  Touches: `app/src/main/res/values-*/strings.xml` (upstream's `es-rES`, `uk-rUA`, `it-rIT`, `zh-rCN`, `in-rID` and `tr-rTR` land in NG's `es`, `uk`, `it`, `zh`, `in` and `tr`, since 3d716430f folded regional dirs into base locales), `scripts/translation-coverage-baseline.json` (lower each `maxMissing` so the gain sticks), CONTRIBUTING.md "Translations" (existing upstream translations get synced, new NG strings still wait for the hosted project), CHANGELOG.
  Acceptance: only keys whose English matches upstream's are added or updated. NG-only keys, reworded keys and any translation NG changed itself after the fork stay as they are. Each imported string keeps the placeholders and xliff tags of NG's English. `py -3.13 scripts/release_gate.py --only translation --only lint` passes. With NG's own language setting on Svenska, the main list, its menus and App details read in Swedish wherever upstream has a translation.
  Complexity: S

### P3

- [ ] P3: Say in words when a component is disabled outside AppManagerNG
  Why: a component that Android has disabled but NG hasn't blocked shows its switch on and reads "Component allowed" to TalkBack. Only a stroke color says otherwise, which a color-blind or TalkBack user can't see.
  Evidence: upstream #2071 and #2072 (2026-11-02, closed as by design; the switch is App Manager's block state). `details/AppDetailsComponentsFragment.java:743-746` (switch and its description), `:792-797` (stroke only), `strings.xml:2708`. `details/AppDetailsViewModel.java:1989-2010` already tells a frozen app, an explicit disabled state and a manifest default apart.
  Touches: `AppDetailsComponentsFragment.java` (the four row builders at `:797`, `:912`, `:1076`, `:1148`, through one helper), `AppDetailsViewModel.isComponentDisabled` (return the reason, not just a boolean), strings, an adapter test.
  Acceptance: a disabled component with no NG rule shows a short text label for its reason (off in the app's manifest, turned off outside NG, or app frozen), and the switch's content description says it isn't blocked by NG but is disabled for that reason. The stroke color stays as reinforcement. A Robolectric test covers all three reasons for each of the four component types.
  Complexity: S

- [ ] P3: Sort the app list by cache size
  Why: upstream #1524 (opened 2025-02-13, updated 2026-11-03) asks for it, and it's the quickest way to find what Clear cache should target. NG sorts by total size but not by cache.
  Evidence: `main/MainListOptions.java:77-95` (sort constants end at `SORT_BY_DANGEROUS_PERMS = 18`), `main/ApplicationItem.java:1064` (cache size getter), `main/MainViewModel.java` (sort switch).
  Touches: `MainListOptions` (a new `SORT_BY_CACHE_SIZE`), `MainViewModel`'s comparator, the sort dialog's labels, strings, a sort test.
  Acceptance: "Cache size" appears in the sort options, sorting puts the largest cache first (and last when reversed), apps with no size info sort after the rest, and a host test asserts the order over fixture items.
  Complexity: S

- [ ] P3: Offer to put an app's blocking rules back after it's reinstalled
  Why: uninstalling and reinstalling an app clears every component NG blocked, while NG's saved rules for it stay on disk unused. AppOpsNext 1.6.7 now restores its settings when an app comes back.
  Evidence: AppOpsNext 1.6.7 release notes (2026-11-14): restore after reinstall, records kept 30 days, off by default. `rules/compontents/ComponentsBlocker.java:198-212` reads `conf/<package>.tsv`, and no code was found that deletes it on uninstall. `self/PackageEventSync.java` already spots a reinstall by its new first-install time (`diff`).
  Touches: `self/PackageEventSync.java`, `ComponentsBlocker.applyRules`, a notification with an Apply action, a Rules setting (off by default) to apply without asking, op history, strings, a Robolectric test.
  Acceptance: after a reinstall of an app with saved rules, NG posts one notification offering to apply them. With the setting on, root, ADB and Shizuku modes apply them without asking and write an op-history entry. In no-root mode the notification says a privileged mode is needed. A test drives a reinstall through `diff` and asserts the offer, or the apply when the setting is on.
  Complexity: M

## Found While Fixing Restore Staging (2026-11-15)

### P2

- [ ] P2: Check backup metadata against its checksum before parsing it
  Why: the restore parses `meta_v5.am.json` (`RestoreOp.java:185`) before `verifyMetadata()` hashes it (`:199`), and the two read the file separately. The parse acts on bytes that were never checked, and a provider can hand the hash a different file than the parser got.
  Evidence: `MetadataManager.readMetadata` opens the file, then `verifyMetadata` opens it again for the digest. The same two-read pattern was fixed for archives with `RestoreStaging` on 2026-11-15.
  Touches: `RestoreOp` setup, `MetadataManager.readMetadata`, `RestoreStaging` (stage the metadata file first), `RestoreStagingTest`.
  Acceptance: WHEN the metadata file changes between two reads, the system SHALL parse and hash the same staged copy, and a test that swaps the file after the hash SHALL see the restore read only the hashed bytes or stop.
  Complexity: S

### P3

- [ ] P3: Budget an OpenPGP backup's decrypted copy by what it really holds
  Why: `RestoreStaging` asks for room for the decrypted copy equal to the encrypted size plus the 64 MB reserve (`RestoreStaging.java:107`). AES and RSA output is about the size of its input, but OpenPGP compresses before it encrypts, so a well-compressed OpenPGP backup can decrypt to far more than its own size. The restore then runs out of space part way through decrypting instead of stopping first.
  Evidence: 2026-11-15 review of 4b9359bf1. `OpenPGPCrypto` decrypts through the provider's stream, which inflates compressed literal data.
  Touches: `RestoreStaging.stage`, `BackupItems.decryptWritesCopies`, `crypto/OpenPGPCrypto.java`, `RestoreStagingTest`.
  Acceptance: WHEN an OpenPGP backup's decrypted size exceeds the free space, the system SHALL stop before writing any app data with a message naming the space needed, either by reading the literal data's declared length or by decrypting with a running space check, and a test with a highly compressible payload SHALL cover it.
  Complexity: S

- [ ] P3: Count an encrypted backup's plain copy in the space check before it starts
  Why: an encrypted backup writes its plain tar files to the app's private staging folder (since 2026-11-15) before encrypting them onto the backup volume. `BackupStorageCheck` measures only the backup volume. When that volume is an SD card or a document provider, internal storage can run out part way through, and the backup fails late instead of warning first.
  Evidence: `BackupStorageCheck.getFreeBytesOnBackupVolume` reads the destination's usable space only. The old staging spot, the external cache, sat on the same internal partition and was never counted either, so this isn't a regression.
  Touches: `BackupStorageCheck.evaluate` and the aggregate path, `FileCache.getPrivateStagingDir`, `BackupStorageCheckTest`.
  Acceptance: WHEN encryption is on and the estimated backup size exceeds the free space of the private staging folder's partition, the system SHALL report the backup as short of space before it starts, and a test SHALL cover a destination with room and a staging partition without it.
  Complexity: S

## Found While Fixing Binary XML Decoding (2026-11-15)

### P2

- [ ] P2: Decode an APK's other XML files with that APK's compile SDK
  Why: only a manifest records the SDK its app was built against, so a layout or other XML opened from inside an APK decodes under the no-metadata rules and shows private framework references as raw IDs even when the APK's own manifest pins the table.
  Evidence: `editor/CodeEditorViewModel.java` calls `AndroidBinXmlDecoder.decode(bytes)` with no SDK; `AndroidBinXmlDecoder.decode(ByteBuffer, int)` already takes one.
  Touches: `CodeEditorViewModel.java`, the file manager's path into an APK, tests.
  Acceptance: WHEN a binary XML file opened from inside an APK is decoded, the system SHALL pass that APK's manifest compile SDK to the decoder, so its private framework references get names exactly when its manifest's would; a standalone file keeps the no-metadata rules; a test decodes a layout from a fixture APK built against API 35 and sees `@*android:dimen/status_bar_height` or `@android:dimen/status_bar_height`.
  Complexity: S

- [ ] P2: Print enum and flag values as numbers when the SDKs disagree on their names
  Why: a file with no compile metadata decodes with the newest table, which names an attribute's enum and flag values from that table's definition of the attribute. Some definitions changed between SDKs (in `android:permissionFlags`, 0x20 is `installerExemptIgnored` in one SDK and `allowedInPrivateComputeCore` in another), so a value can get a name it never had.
  Evidence: `AndroidBinXmlDecoder.attributeValue` falls back to the plain value only when `canNameAttribute(nameId)` is false; `disputed-ids.txt` lists IDs, not the values defined under an attribute ID.
  Touches: `scripts/framework-tables/GenerateFrameworkTables.java` (record attributes whose enum or flag values differ between SDKs), `disputed-ids.txt`, `FrameworkTables.java`, `AndroidBinXmlDecoder.java`, tests.
  Acceptance: WHEN a file is decoded without a table pinned by its compile SDK and an attribute's enum or flag values differ between the SDKs it could have been built against, the system SHALL print that attribute's value as a number; attributes whose values agree keep their names; a test decodes `android:permissionFlags` 0x20 with no metadata and sees the number.
  Complexity: M

### P3

- [ ] P3: Check the decoder against `PackageManager.parseAndroidManifest` on API 37
  Why: API 37 parses a manifest itself, which gives the decoder a platform answer to compare its names and values against.
  Touches: an instrumented test, an API 37 emulator or a phone on API 37.
  Acceptance: WHEN running on API 37 or later, an instrumented check SHALL compare the decoder's attribute names and values for the `framework_ref` fixtures and three installed apps against `parseAndroidManifest` and list every difference; each naming difference gets its own fix or ROADMAP item.
  Complexity: S

- [ ] P3: Use the device's own framework table for system apps built against it
  Why: OEM system apps are built against the device's `framework-res.apk`, whose private IDs differ from AOSP's, so the bundled AOSP tables can name them wrongly. Today such an app's private IDs show by name whenever its compile SDK has no disputed set covering them.
  Touches: `FrameworkTables.java`, `AndroidBinXmlDecoder.java`, the manifest viewer's load path, tests.
  Acceptance: WHEN decoding an installed system app whose compile SDK equals the device's SDK, the system SHALL take private framework names from the device's `/system/framework/framework-res.apk` instead of the bundled AOSP table; other apps keep the bundled tables; a device table that can't be read falls back to raw IDs, never to another table's name.
  Complexity: M

- [ ] P3: Decode elements whose attribute records are shorter than 20 bytes
  Why: Android reads attribute N at `attributeStart + attributeSize * N` and only checks that the run fits inside the element, so a file with a record size under 20 installs and parses there. `BinaryXmlBounds.checkElement` refuses it, so the manifest viewer shows an error for an APK Android accepts.
  Evidence: `apk/parser/BinaryXmlBounds.java` `checkElement` (`attributeSize < ATTRIBUTE_SIZE`); AOSP `ResXMLTree::validateNode` and `ResXMLParser::getAttribute*`.
  Touches: `BinaryXmlBounds.java` (rewrite such an element to 20-byte records in its copy, or confirm how ARSCLib strides), tests.
  Acceptance: WHEN a start element has attributes and a record size under 20 whose run still fits inside the element, the system SHALL decode each attribute from the same bytes Android reads for it, and SHALL keep refusing a run that doesn't fit.
  Complexity: M

## Found While Storing Mutation Images (2026-11-15)

### P2

- [ ] P2: Record mutation images for the remaining state writers
  Why: App Info, the app's tabs, Running apps and batch operations now store before, requested and read-back values, so per-app revert can refuse to overwrite an outside change. Other writers still change the same settings without an image, so revert falls back to the blind inverse for them or can't undo them at all.
  Evidence: freeze writers in `AutoFreezeOnLockWorker`, `FreezeUnfreezeActivity`, `FreezeUnfreezeService`, `AssistActionActivity` and `shortcut/AppActionShortcutActivity`; permission writers in `permissions/PermissionToggleHelper` (Permission Inspector) and `permissions/SpecialAccessCatalog`; `backup/RestoreOp`, `rules/compontents/ComponentsBlocker`, `ComponentUtils`, `profiles/ProfileApplierService` and `permission/watcher/SystemWatchBackend`; component and tracker batch ops record no image because `MutationState` has no component kind.
  Touches: those writers, `MutationState.java` (a component kind: enabled state plus blocking rule), `PerAppRollbackManager.java`, tests.
  Acceptance: WHEN any writer listed above changes a freeze state, permission, app op, network policy or component, the system SHALL store a mutation image with the same fields and chain as the hooked writers, and per-app revert SHALL put that target back only while its current value equals the recorded post value. A component image SHALL restore the previous enabled state and blocking rule. WHEN a freeze moves from one frozen state to another, revert SHALL either restore it through a sequence that ends in the recorded before value or keep it in manual review with that reason shown.
  Complexity: L

### P3

- [ ] P3: Check each revert step again when BatchOpsService runs it
  Why: per-app revert reads every image step's target once, in `PerAppRollbackManager.start`, and queues them all at that moment. BatchOpsService runs them one after another later, without reading again. A change made between the queue and the run gets overwritten, and two steps whose targets share one stored value (app ops under the same switch, such as fine and coarse location) each pass their check before either runs, so the second undoes the first.
  Evidence: `history/ops/PerAppRollbackManager.java` `start` and `stillHolds` (the only check); `batchops/BatchOpsService` applies the queued item with no expected value.
  Touches: `PerAppRollbackManager.java`, `BatchQueueItem` (an expected value per target), `BatchOpsService`/`BatchOpsManager`, tests.
  Acceptance: WHEN BatchOpsService is about to apply a revert step and the target's current value no longer equals the image's recorded post value, the system SHALL skip that step and report it as changed elsewhere. WHEN two selected steps write targets that share one stored mode, the system SHALL run only the first and list the other in manual review with that reason.
  Complexity: M

## Found While Fixing Shortcut Launches (2026-11-15)

### P1

- [ ] P1: Check shortcut-launched targets in Recents on a phone
  Why: the trampoline now builds the target's intent from its component alone and closes itself with `finishAndRemoveTask()` on every path. Host tests cover that, but Recents and task reuse are system behavior Robolectric doesn't model.
  Evidence: `details/ActivityLauncherShortcutActivity.java`, `ActivityLauncherShortcutActivityTest`; AOSP `ActivityRecord` adds `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` to the intent of an activity declared `excludeFromRecents`, which the trampoline used to copy.
  Touches: nothing unless it fails.
  Acceptance: WHEN a home-screen shortcut made from App Details' activity list starts another app's activity on the S22 or S25 and Home is pressed, the system SHALL show that app in Recents under its own label; tapping the shortcut again SHALL bring back that task instead of adding a second one; a shortcut whose app was uninstalled SHALL show the "target unavailable" message and leave nothing over the launcher. The same run on an API 23 emulator covers the oldest version Robolectric can't.
  Complexity: S

### P2

- [ ] P2: Apply the stored night mode before the first screen of a cold start is created
  Why: `AppearanceUtils.ActivityAppearanceCallback.onActivityPostCreated` calls `AppCompatDelegate.setDefaultNightMode` only after a screen exists. In a fresh process AppCompat's default is still unset when the first screen is created, so when Day or Night is picked in Appearance and the system is in the other mode, that call changes the mode and AppCompat recreates the screen. Its `onCreate` work runs twice on that cold start.
  Evidence: a Robolectric run of `ActivityLauncherShortcutActivityTest` on 2026-11-15 recreated the activity from `AppCompatDelegateImpl.updateAppConfiguration`, reached through `onActivityPostCreated` and then `onStart`, and started its target three times until the test set the night mode first. `setDefaultNightMode` is called nowhere else except `settings/AppearancePreferences.java:57`. Not yet seen on a phone.
  Touches: `AppearanceUtils.init` or `App.onCreate` (set the stored mode once at process start), a Robolectric test.
  Acceptance: WHEN the process starts with Day or Night picked in Appearance, the system SHALL create the first screen once, already in that mode, and a test SHALL build an activity with AppCompat's default night mode unset and assert `onCreate` ran once.
  Complexity: S

- [ ] P2: Give the user's assistant back as soon as the assist-launch dialog can't be answered
  Why: without INJECT_EVENTS, an assist launch sets the secure `assistant` setting to the target and then waits on a dialog for up to 10 minutes before putting the user's assistant back. The dialog is posted on the trampoline, which may already be finishing or gone (it closes itself on most paths, and Home or a config change ends it). Then `show()` throws or the window never appears, its dismiss listener never runs, and the user's assistant key opens the target for those 10 minutes.
  Evidence: `details/ActivityLauncherShortcutActivity.java` `launchActivityViaAssist` (CountDownLatch with `await(10, TimeUnit.MINUTES)`, `show()` on `this` from a background post) and `compat/ActivityManagerCompat.java:89-101` (restores the setting only in `finally` after the callback returns). Found by the verification pass over the shortcut fix on 2026-11-15; not yet seen on a phone.
  Touches: `launchActivityViaAssist` (count the latch down when the activity is finishing or destroyed, or when `show()` throws), `ActivityManagerCompat.startActivityViaAssist`, a Robolectric test with a fake callback runner.
  Acceptance: WHEN the trampoline is finishing or destroyed while an assist launch waits for the user, the system SHALL restore the previous `assistant` value within a second, and a test SHALL finish the activity mid-wait and assert the setting was restored.
  Complexity: S

## Found While Verifying Backup, History and Shortcut Fixes (2026-11-15)

### P3

- [ ] P3: Key the encrypted-backup record by volume as well as folder
  Why: `BackupCryptoRecord` keys a v4 backup as `<pkg>/<userId>[_<name>]` with no volume, so an encrypted v4 backup on one volume makes a plain backup of the same name on another volume look swapped, and the restore refuses it. The Room key `(backup_name, package_name)` also folds rows together, so `AppDb.deleteAllBackups` can carry over only one encrypted mode where several backups shared a name.
  Evidence: refute pass on eaf89bb94 (2026-11-15). `BackupCryptoRecord.relativeDirOf`, `db/entity/Backup` primary key.
  Touches: `backup/BackupCryptoRecord.java`, `db/utils/AppDb.java`, `EncryptedBackupModeTest`.
  Acceptance: WHEN two v4 backups with the same package, user and name sit on different volumes, one encrypted and one plain, the system SHALL restore the plain one and still refuse a plain info file in the encrypted one's folder, and a test SHALL cover both volumes.
  Complexity: S

- [ ] P3: Forget a backup's encryption record only when its folder is really gone
  Why: `BackupItems.BackupItem.delete()` calls `BackupCryptoRecord.forget` even when the folder doesn't exist. Someone with file access can move an encrypted backup out, delete its entry in the app, move it back and edit its info file to say it isn't encrypted, and the restore no longer refuses it.
  Evidence: refute pass on eaf89bb94 (2026-11-15). `BackupItems.java` `delete()`.
  Touches: `backup/BackupItems.java`, `EncryptedBackupModeTest`.
  Acceptance: WHEN delete() runs on a backup whose folder is missing or couldn't be removed, the system SHALL keep its record, and a test SHALL delete an absent folder, put an encrypted backup back with a plain info file and see the restore stop.
  Complexity: S

- [ ] P3: Act on a failed encryption-record write instead of only logging it
  Why: `BackupCryptoRecord.record`, `forget` and `keepEncryptedModes` log a failed `commit()` and carry on, and Android keeps the unsaved value in memory, so the check passes for the rest of the process and the record is gone after a restart. `keepEncryptedModes` also checks `contains` and then writes in two steps, so a backup committed in between can have its mode overwritten by the old table row.
  Evidence: refute pass on eaf89bb94 (2026-11-15). `BackupCryptoRecord.java` lines 48-90.
  Touches: `backup/BackupCryptoRecord.java`, `db/utils/AppDb.java`, `EncryptedBackupModeTest`.
  Acceptance: WHEN a record write fails, the system SHALL restore the in-memory value and fail the backup commit (or the list rebuild) with a logged error the user sees; WHEN a backup commits while the table's modes are being kept, the newer mode SHALL win; tests SHALL use a preferences double whose commit fails and one that interleaves a commit.
  Complexity: S

- [ ] P3: Stage backup converters' plain entries in private storage
  Why: the OAndBackup, Swift Backup and Titanium Backup converters write plain temporary entries through the global `FileCache`, which sits in the external cache. They delete them in `finally`, but other apps can read them during the conversion on older Android versions, which the 2026-11-15 staging fix closed for normal backups.
  Evidence: refute pass on 40cde5d78 (2026-11-15). `OABConverter.java`, `SBConverter.java`, `TBConverter.java` cleanup blocks.
  Touches: `backup/convert/*Converter.java`, `self/filecache/FileCache.java`, the converter tests.
  Acceptance: WHEN a converter writes a temporary plain entry, the system SHALL put it under `FileCache.getPrivateStagingDir()` and remove it whether the conversion works or not, and each converter test SHALL assert nothing lands in the external cache.
  Complexity: S

- [ ] P3: Tell a shortcut start that Android 11 and 12 canceled from one that went through
  Why: on Android 11 and 12 `ActivityLauncherShortcutActivity` counts `START_SWITCHES_CANCELED` as a start that will happen later, but a background-activity-launch abort returns the same code without queuing anything, so the shortcut shows no failure and nothing opens. Before Android 8 an aborted start returns `START_SUCCESS`, so the result code can't tell there either.
  Evidence: refute pass on 8ef00c65d (2026-11-15). `ActivityLauncherShortcutActivity.java` lines 185-200.
  Touches: `details/ActivityLauncherShortcutActivity.java`, `ActivityLauncherShortcutActivityTest`.
  Acceptance: WHEN a shortcut start returns a switches-canceled result and the target activity hasn't resumed within the app switch window, the system SHALL show the could-not-open toast, and a test SHALL cover a canceled start that never resumes and one that resumes late.
  Complexity: S

- [ ] P3: Test the shortcut trampoline's real unlock path
  Why: the test for a start that waits on the lock screen sets `mAuthenticationPending` by reflection after an authenticated setup, so it never runs `onCreate`, the authentication callback and `completeAuthentication` in order, or a recreation in between.
  Evidence: refute pass on 8ef00c65d (2026-11-15). `ActivityLauncherShortcutActivityTest`.
  Touches: `ActivityLauncherShortcutActivityTest`.
  Acceptance: WHEN the trampoline is launched while authentication is required, a Robolectric test SHALL drive `onCreate`, the authentication result and a recreation through the activity controller with no reflection, and SHALL see exactly one start and the trampoline finish.
  Complexity: S

## Found While Verifying App-Op, Standby and Notice Fixes (2026-11-15)

### P3

- [ ] P3: Route the remaining hidden-API fallbacks through HiddenApiFallbacks
  Why: a few accessors still swallow a linkage failure on their own, so a build whose hidden signature changed falls back silently or logs on every call instead of once per member.
  Evidence: refute pass on b07ad825f (2026-11-15). `AppOpsManagerCompat.java` near line 280, `PackageManagerCompat.java` near line 917, `BackupCompat.java` lines 139 and 157, `PackageInfoCompat2.java` line 52 (through `ExUtils.exceptionAsNull`).
  Touches: those four compat classes, `HiddenApiFallbacksTest`.
  Acceptance: WHEN any of those calls fails with a `LinkageError`, the system SHALL log it once through `HiddenApiFallbacks.log` under a `Class#member` key and keep its current fallback answer; ordinary failures SHALL keep their own logging. A test SHALL cover one of them.
  Complexity: S

- [ ] P3: Pass a member name, not the service name, to HiddenApiFallbacks in SubscriptionManagerCompat
  Why: `SubscriptionManagerCompat` line 218 logs its fallback under the service name, so the once-per-member key is wrong and two different members would share one line.
  Evidence: refute pass on b07ad825f (2026-11-15).
  Touches: `compat/SubscriptionManagerCompat.java`, `HiddenApiFallbacksTest`.
  Acceptance: WHEN that call falls back, the system SHALL log it under `SubscriptionManagerCompat#<method>`, and a test SHALL check the key.
  Complexity: XS

- [ ] P3: Cache stored op modes per package while filtering the app list
  Why: `PackageUtils.getFilteredAppOps` calls `checkOperationRaw` per op, and every op that reads as ignored now costs two more IPCs (`getUidOps`, then `getOpsForPackage`) to read what is stored. A list with many ignored ops on many apps makes a lot of extra calls.
  Evidence: refute pass on b07ad825f (2026-11-15).
  Touches: `utils/PackageUtils.java`, `compat/AppOpsManagerCompat.java`, a filter test.
  Acceptance: WHEN the filter reads several ignored ops for one package, the system SHALL read that package's op lists at most once, and a test with a counting fake service SHALL check the call count.
  Complexity: S

- [ ] P3: Make two AppOpRestrictedModeTest cases fail without the fix they guard
  Why: two cases in `AppOpRestrictedModeTest` pass on the code from before the restricted-mode fix too, so they don't guard against it coming back.
  Evidence: refute pass on b07ad825f (2026-11-15).
  Touches: `AppOpRestrictedModeTest`.
  Acceptance: WHEN the stored-mode read in `checkOperationRaw` is removed, each of those two cases SHALL fail; the fixture SHALL store a mode that differs from what the checks answer.
  Complexity: XS

- [ ] P3: Stop the preview build notice from treating a tested SDK's codename build as untested
  Why: `PlatformSupport` adds one to the SDK level when the codename isn't REL, so a preview build of an SDK that is already tested shows the untested-Android notice, and dismissing it stores the next level (38). The compile SDK test compares the constant to itself.
  Evidence: refute pass on b07ad825f (2026-11-15).
  Touches: `compat/PlatformSupport.java`, its test.
  Acceptance: WHEN the device reports a preview codename for an SDK level that is tested, the system SHALL NOT show the notice, a dismissal SHALL store the level the notice was shown for, and the compile SDK test SHALL compare against the Gradle value.
  Complexity: S

- [ ] P3: Test that an unencrypted backup writes its metadata into the backup folder
  Why: the private staging change covers encrypted backups. Nothing checks that a plain backup still writes `meta_v5.json` next to its files.
  Evidence: refute pass on b07ad825f (2026-11-15).
  Touches: a backup test.
  Acceptance: WHEN a backup without encryption commits, a test SHALL find `meta_v5.json` in the backup folder and none left in private staging.
  Complexity: XS

- [ ] P3: Hand backup converters the crypto they read with
  Why: the OAndBackup, Swift Backup and Titanium Backup converters never call `setCrypto`, so a converted backup's crypto mode depends on defaults rather than the source.
  Evidence: refute pass on b07ad825f (2026-11-15). Predates this batch.
  Touches: `backup/convert/*Converter.java`, the converter tests.
  Acceptance: WHEN a converter writes a backup, the system SHALL set the crypto mode the result is written with, and each converter test SHALL check the mode in the written metadata.
  Complexity: S

## Found While Adding Internet Blocking (2026-11-15)

### P1

- [ ] P1: Check that an internet block comes back after a restart
  Why: on the S22 (Android 16, Wireless debugging mode) on 2026-11-15 the block, the allow, the disabled states without a privileged mode and the move to a reinstalled app's new UID all worked against the real `FIREWALL_CHAIN_OEM_DENY_3`. The restart is the one part left, and it hasn't run anywhere yet.
  Evidence: `self/BootReceiver.java` re-applies saved rules; `rules/internet/InternetAccess.java` `applySoonWhereTheUidMoved`.
  Touches: nothing unless it fails. An API 36 Magisk emulator in root mode can run it without a phone.
  Acceptance: WHEN an app's INTERNET switch is off on an Android 14+ device in root or ADB mode and the device restarts, the app SHALL be cut off again once `BOOT_COMPLETED` runs and the privileged mode is back, with the row reading Blocked.
  Complexity: S

### P2

- [ ] P2: Stop background work from toasting "Working on ADB mode"
  Why: on the S22 on 2026-11-15 the app change job and the internet rule worker each brought up the privileged mode in the background, and `Ops.checkRootOrIncompleteUsbDebuggingInAdb` showed "Working on ADB mode" twice over whatever app was on screen (logcat 11:48:56 and 11:51:01). A toast nobody asked for, over another app, reads as noise.
  Evidence: `settings/Ops.java` `checkRootOrIncompleteUsbDebuggingInAdb` posts `displayShortToast(R.string.working_on_adb_mode)` (and the long root and system mode warnings) on the main thread whatever started the init; `rules/internet/InternetRuleWorker`, `self/PackageEventSync` and `self/BootReceiver` start it with no screen of their own.
  Touches: `settings/Ops.java`, the background callers, an Ops test.
  Acceptance: WHEN the privileged mode starts from a worker, receiver or service with no AppManagerNG screen in front, the system SHALL show none of these toasts; WHEN it starts from a screen the user opened, the toasts SHALL show as before; a test SHALL cover both.
  Complexity: S

- [ ] P2: Block internet access from batch operations and profiles
  Why: an INTERNET rule can only be set one app at a time from its Permissions tab. Batch operations and profiles still offer only the metered background network policy, which doesn't cut an app off.
  Evidence: `batchops/struct/BatchNetPolicyOptions.java` and `profiles/` have no INTERNET rule; `rules/internet/InternetAccess.java` has `setBlocked(packageName, userId, blocked)`.
  Touches: `batchops/BatchOpsManager.java`, `batchops/struct/`, `profiles/`, the batch op picker, a batch op test.
  Acceptance: WHEN a batch operation or profile blocks or allows internet access for a set of apps, the system SHALL call `InternetAccess.setBlocked` for each one and report every app whose rule is saved but not in force, with the reason; a test against the fake firewall SHALL check both outcomes.
  Complexity: M

- [ ] P2: Know a block is gone after a restart
  Why: a restart clears the firewall, but a saved block still names the UID it had before. Until root, ADB or Shizuku comes up again, the INTERNET row says AppManagerNG can't tell whether the block is in force and the switch stays locked, though the app is online and nothing is left to lift. An allowed rule kept from before the restart can't be dropped either.
  Evidence: `rules/internet/InternetAccess.java` `Status.canChange` and `describe` lock on `holdsUid` under `NO_PRIVILEGED_MODE` and `NO_PERMISSION`; `rules/struct/InternetRule.java` saves the UID with no record of the boot it was denied in; `Settings.Global.BOOT_COUNT` (Android 7 and later) counts restarts.
  Touches: `rules/struct/InternetRule.java` and its text form, `rules/internet/InternetAccess.java`, `rules/internet/InternetAccessTest.java`.
  Acceptance: WHEN a saved rule's UID was denied in an earlier boot than the current one, the system SHALL treat it as holding no deny: the row SHALL say the block isn't in force, the switch SHALL be usable without a privileged mode, and allowing the app SHALL drop the rule; a rule saved before this change SHALL keep today's behavior; tests SHALL cover a same-boot and an earlier-boot rule.
  Complexity: M

### P3

- [ ] P3: Say when another app's block cuts this app off
  Why: apps that share a UID share one firewall rule. Allowing one of them while another is still blocked shows the reason on the app you allowed, but the other apps in that UID show no rule at all while they're cut off.
  Evidence: `rules/internet/InternetAccess.java` `findOtherBlockedRule` and the `SHARED_UID` reason; `details/AppDetailsViewModel.java` reads only the app's own rule.
  Touches: `rules/internet/InternetAccess.java`, `details/AppDetailsPermissionsFragment.java`, `rules/internet/InternetAccessTest.java`.
  Acceptance: WHEN an app without its own rule shares a UID with an app whose block is in force, its INTERNET row SHALL say which app's block cuts it off; a test SHALL cover it.
  Complexity: S

- [ ] P3: Notice app changes made only in another user
  Why: the app change sync compares the installed apps of the user AppManagerNG runs in. An app that lives only in another user (a work profile, a second user) and is removed and installed again there gets a new app ID, and its internet block stays on the old UID until something else puts it back.
  Evidence: `self/PackageEventSync.java` lists packages for `UserHandleHidden.myUserId()` only; `rules/internet/InternetAccess.java` `applySoonInEveryUser` covers every user only once a change is seen.
  Touches: `self/PackageEventSync.java`, `self/PackageEventSyncTest.java`.
  Acceptance: WHEN an app with an internet rule exists only in another user and gets a new UID there, the system SHALL put the block on the new UID within one sync; a test SHALL cover a package seen only in user 10.
  Complexity: M

- [ ] P3: Test a restore's internet rule warning end to end
  Why: a restore that saves an internet block it can't put into force adds a warning, but only the importer's list behind it is tested. No test runs a backup with an INTERNET row through `RestoreOp`.
  Evidence: `backup/RestoreOp.java` `restoreRules` adds `restore_internet_rule_warning`; the test backups under `app/src/test/resources/backups/` hold no rules file, and `InternetAccess.setFirewallForTesting` is package private.
  Touches: `backup/RestoreOp.java`, `backup/BackupManagerTest.java`, a test backup with a rules file.
  Acceptance: WHEN a backup holding a blocked INTERNET rule is restored with no privileged mode running, the restore SHALL finish with one warning naming the reason; a test SHALL check it.
  Complexity: S

- [ ] P3: Test the paths that put internet rules back
  Why: the boot receiver, the privileged mode coming up and the rule worker are what put blocks back after a restart, and none of them has a test. A broken hook there leaves every app online after a restart with nothing failing.
  Evidence: `rules/internet/InternetRuleWorker.java` (now `Ops.startUnattended`), the `applySoon(context, null, 0)` call in `self/BootReceiver.java` and the `applyAll` call in `ipc/LocalServices.java` when the privileged mode binds have no test calling them.
  Touches: `rules/internet/InternetRuleWorker.java`, `self/BootReceiver.java`, `ipc/LocalServices.java`, new tests.
  Acceptance: WHEN the phone finishes booting, the system SHALL enqueue the all-rules work; WHEN the privileged mode binds, the system SHALL put every saved rule back; WHEN the worker runs with no rules saved, or on Android 13 or older, it SHALL not start a privileged mode; tests SHALL cover all three.
  Complexity: S

- [ ] P3: Test the UID lookup for a hidden app
  Why: the package manager lookup behind internet rules passes `MATCH_UNINSTALLED_PACKAGES` so a hidden or data-kept app still resolves to its UID, but every internet test swaps the resolver for a map. Dropping the flag would leave a hidden app's block on a stale UID with nothing failing.
  Evidence: `rules/internet/InternetAccess.java` `PACKAGE_MANAGER_UIDS`; `InternetAccessTest` sets `setUidResolverForTesting` in `setUp`.
  Touches: `rules/internet/InternetAccess.java`, a new test against a shadow package manager.
  Acceptance: WHEN an app is installed but hidden in a user, the real resolver SHALL return its UID; a test SHALL cover it.
  Complexity: S

- [ ] P3: Test the INTERNET switch's toggle lock
  Why: two quick taps on the switch run one after the other under `mInternetToggleLock`, and a queued tap re-checks `canChange()`, but no test drives `toggleInternetAccessAsync` twice.
  Evidence: `details/AppDetailsViewModel.java` `toggleInternetAccessAsync`.
  Touches: `details/AppDetailsViewModel.java`, a new view model test with a direct executor.
  Acceptance: WHEN the switch is tapped twice before the first change finishes, the system SHALL apply both flips in order and SHALL skip a queued flip once the switch can't be used; a test SHALL cover both.
  Complexity: S

- [ ] P3: Lock the rules file across users
  Why: every user's rules for a package live in one `conf/<package>.tsv`, but the lock that guards an edit is keyed by package and user. Two users editing the same package's rules at once can each write the file over the other's change.
  Evidence: `rules/RulesStorageManager.java` builds its lock key from the package name plus the user id, and `getDesiredFile` names the file by package only. Predates the INTERNET rule, which made it reachable from boot and install work as well.
  Touches: `rules/RulesStorageManager.java`, a concurrency test.
  Acceptance: WHEN two users' rules for one package are edited at the same time, the system SHALL keep both changes; a test SHALL run two edits on separate threads and find both rules in the file.
  Complexity: S

- [ ] P3: Import internet rules under the internet lock
  Why: a rules import reads the UID a saved internet rule holds and writes the imported rule without taking the lock `InternetAccess` holds while it changes the firewall. An import that lands while a block is being put into force can save a stale UID, and the block's own clean-up then skips its revert because the rule changed under it, which can leave a deny in the kernel with no rule naming it.
  Evidence: `rules/RulesImporter.java` `applyRules` copies `saved.getUid()` under the components lock only; `rules/internet/InternetAccess.java` `apply` reverts through `replaceIfUnchanged`.
  Touches: `rules/RulesImporter.java`, `rules/internet/InternetAccess.java` (a save that takes `LOCK`), `rules/internet/InternetAccessTest.java`.
  Acceptance: WHEN an import saves an internet rule, the system SHALL read the held UID and write the rule under the same lock that putting a rule into force takes; a test SHALL run an import while a block is being put into force and SHALL find the deny either lifted or named by the saved rule.
  Complexity: S

## Found While Stabilizing Scheduled Backups (2026-11-15)

### P2

- [ ] P2: Keep periodic workers scheduled after an uncaught error
  Why: on WorkManager 2.10.5 a periodic worker that throws ends FAILED and never runs again (b/443879071, fixed in 2.11.2, which needs minSdk 23). AutoBackupWorker, OpHistoryPruneWorker, PermissionWatchWorker and RoutineWorker catch Exception at most and PackageEventSyncWorker catches nothing, so an Error such as an OutOfMemoryError, or a LinkageError on a vendor build, ends that schedule. The scheduled backup only comes back when its settings change.
  Evidence: WorkManager 2.11.2 release notes; `backup/schedule/AutoBackupScheduler.java` is scheduled only from `BackupRestorePreferences`; the five workers' `doWork`.
  Touches: the five periodic workers, a shared base or helper, a test on the WorkManager harness in `ScheduledBackupNetworkTest`.
  Acceptance: a test first shows a periodic worker that throws ends FAILED on 2.10.5; WHEN any of the five periodic workers throws, the system SHALL log it and keep the work ENQUEUED with its period; a test SHALL cover each worker.
  Complexity: S

## Found While Reconciling Backup Folder Grants (2026-11-15)

### P3

- [ ] P3: Keep the file manager from dropping the backup folder's access
  Why: the file manager drawer's release action calls `releasePersistableUriPermission` on any folder it lists, including the one backups go to or one a backup tag policy points at. After that, backups to that folder fail until it's picked again.
  Evidence: `fm/FmActivity.java` `FmDrawerViewModel.releaseUri`; `backup/BackupDestinationGrants.java` treats the backup folder and tag policy destinations as in use.
  Touches: `FmActivity.java`, `BackupDestinationGrants.java` (a public in-use check), strings.
  Acceptance: WHEN the user releases a folder in the file manager that is the backup folder or a tag policy destination, the system SHALL keep the access and say why; other folders SHALL be released as before; a Robolectric test SHALL cover both.
  Complexity: S

- [ ] P3: Let a failed backup import be retried after the app restarts
  Why: a failed import keeps the folder's access for a retry, but the app's start lets go of import folders nothing references, so a retry from batch op history after a restart can't open the folder and the user has to pick it again.
  Evidence: `batchops/BatchOpsManager.java` `opImportBackups` releases only after a clean import; `BackupDestinationGrants.pruneStale` releases unreferenced PURPOSE_IMPORT grants.
  Touches: `BackupDestinationGrants.java` (keep import grants a pending retry references, with an age cap), batch op history.
  Acceptance: WHEN an import failed and its retry is still offered, the system SHALL keep the folder's access across a restart for up to 7 days; WHEN the retry succeeds or the window passes, the system SHALL let it go; tests SHALL cover both.
  Complexity: S

- [ ] P3: Report a backup import that throws before listing as failed
  Why: `opImportBackups` logs an exception thrown while opening or listing the folder and then returns an empty failure list, which reads as a successful import with nothing imported.
  Evidence: `batchops/BatchOpsManager.java` `opImportBackups` catch block.
  Touches: `BatchOpsManager.java`, a batch ops test.
  Acceptance: WHEN listing the import folder throws, the system SHALL return an unsuccessful result and keep the folder's access; a test SHALL cover it.
  Complexity: S

## Found While Verifying Pure Black and Scheduled Backup Fixes (2026-11-15)

### P3

- [ ] P3: Re-theme the code editor when the night setting changes
  Why: the code editor declares `uiMode` in its `configChanges` and has no `onConfigurationChanged`, so with Follow system, turning the system's dark mode on or off while the editor is open leaves it in the old colors until it's reopened. Pure black now depends on whether the app is dark, so the editor can also keep a black surface in the day.
  Evidence: `AndroidManifest.xml` `.editor.CodeEditorActivity` and its `.editor.EditorActivity` alias both list `uiMode`; `editor/CodeEditorActivity.java` handles no configuration change.
  Touches: the manifest entries, `CodeEditorActivity.java` (recreate on a night change while keeping the unsaved text, or keep `uiMode` handled and re-apply the theme).
  Acceptance: WHEN the night mode in effect changes while the editor is open, the system SHALL show the editor in the new palette without losing unsaved text; a Robolectric test SHALL cover it.
  Complexity: S

- [ ] P3: Keep the floating windows black when dynamic colors and Pure black are both on
  Why: `AppearanceUtils.getThemedContext` wraps the black transparent theme in dynamic colors and never applies `ThemeOverlay.AppTheme.V2.AmoledSurfaces` after it, so the top app bubble and the tracker window get the wallpaper's dark surfaces instead of black. Activities apply the overlay after dynamic colors, so these two windows don't match the rest of the app.
  Evidence: `utils/appearance/AppearanceUtils.java` `getThemedContext`; callers `accessibility/topapp/TopAppBubble.java` and `accessibility/activity/TrackerWindow.java`.
  Touches: `AppearanceUtils.java`, `PureBlackThemeTest`.
  Acceptance: WHEN Pure black is in effect with dynamic colors on, the system SHALL resolve colorSurface and colorBackground to black in the themed context those windows use; a test SHALL cover it.
  Complexity: S

- [ ] P3: Clear the scheduled backup's "waiting for a network" status in any language
  Why: turning scheduled backups off clears the status only when the saved text equals today's "waiting for a network" string, so after a language change the old text stays. The worker also checks that no backup is running and then writes the waiting status, so a run that starts in between can have its status overwritten.
  Evidence: `backup/schedule/AutoBackupScheduler.java` compares `auto_backup_result_waiting_for_network` with the saved result; `backup/schedule/AutoBackupWorker.java` checks `isRunning()` before `recordRunResult`.
  Touches: `AutoBackupScheduler.java`, `AutoBackupWorker.java` (save a status code beside the text, and write the waiting status under the run claim), `ScheduledBackupNetworkTest`.
  Acceptance: WHEN scheduled backups are turned off after the app language changed while a run waited for a network, the system SHALL clear the waiting status; WHEN a run starts while another waits, the system SHALL keep the running run's status; tests SHALL cover both.
  Complexity: S
