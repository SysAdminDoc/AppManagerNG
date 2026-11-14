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
  Checked 2026-11-14 on the S25 (API 37, One UI CP2A.260605.016): Shizuku 13.6.0's server starts over ADB, Mode doctor sees it, the consent dialog works and NG's shizuku_am and shizuku_fs services connect, so Shizuku binds on Android 17. The first switch then died: the READ_LOGS self-grant changes NG's gids, Android killed NG with Settings resumed, and the mode was never saved. READ_LOGS now waits until every NG process is cached (DeferredLogAccess). Still owed: the same switch on the S25 with this build (expect "Active now: Shizuku", then READ_LOGS granted a few seconds after Home), and root plus Dhizuku on API 37. Those need an emulator with about 10 GB of host RAM free (rules/android.md). `AppManagerNG_Magisk_API37` is set up on a copy of the API 37 google_apis image and is not patched yet.
  Complexity: S

- [ ] P1: Root-detection retune for 2026 root managers
  Why: upstream #1967 reports root not detected on Android 16, while Magisk 30.7 now preserves capabilities by default and KernelSU-Next moved probe paths again.
  Evidence: upstream MuntashirAkon/AppManager#1967; Magisk 30.7 release notes; runner/RootManagerInfo.java.
  Acceptance: root is detected on Android 16 with current Magisk and KernelSU-Next; drop-cap diagnostics give correct guidance for Magisk 30.7+; probe matrix is documented.
  Today: #1967 closed with upstream v4.1.1, and NG already carries the KernelSU re-grant recovery from it (CHANGELOG, 2026-06-04). Magisk is now at v31.1 (2026-10-25), so the probe matrix should cover 31.1 too.
  Unblocked 2026-11-07: the rooted API 36 Magisk AVD on disk gives a real Android 16 root runtime for the Magisk half today. The KernelSU-Next half needs a kernel it can load into, which is setup work, not a missing resource.
  Research 2026-11-14: Magisk 31.1 removed the root prompt. Apps have to be turned on ahead of time in Magisk > Superuser > + (SuperuserGrantScreen), and hiding Magisk now means uninstalling its app. On 31.1 a first switch to Root gets refused with no prompt, and NG only shows the generic rollback toast (ModeOfOpsPreference `STATUS_FAILURE`). It should say where to grant it when com.topjohnwu.magisk is installed at versionCode 31100 or later, and the onboarding line about managers that "hide su until first request" needs the same note. SukiSU Ultra (`com.sukisu.ultra`, v4.3.0 2026-11-05, a KernelSU fork using /data/adb/ksu) is missing from the three package lists (RootManagerInfo, ModeDoctorPreferences, PrivilegeHealthPreferences' KernelSU launcher). Current releases: KernelSU-Next v3.4.2, APatch 11282. Magisk's versionCode is major * 1000 + minor * 100 (30700 for v30.7).
  Complexity: M

- [ ] P1: Backup/restore round-trip integration tests
  Why: backup/restore has the highest reliability risk and still lacks end-to-end runtime coverage for no-crypto and AES paths.
  Evidence: backup/adb/AndroidBackupHeader.java; backup/RestoreOp.java; test coverage gaps in backup round-trip behavior.
  Acceptance: a local emulator suite installs a fixture app, backs it up with no-crypto and AES modes, uninstalls it, restores it, and asserts data equality.
  Today: `app/src/androidTest` has seven classes and none of them runs a backup round trip. This also covers INIT-D5 (backup round-trip emulator coverage).
  Unblocked 2026-11-07: every emulator image on disk has room for a fixture-app backup and restore, so this is test work with nothing missing.
  Complexity: M

- [ ] P1: Port upstream's native run_server for the local privileged channel
  Why: the HMAC half is done. Protocol 1.4.0 (fcbb19030) proves the token both ways without sending it, and `DataTransmissionHandshakeTest` covers the negative cases. Upstream's native run_server executable is what's left.
  Evidence: upstream commits 07c7199 (run_server as a native executable, 2026-05-28) and b42efbb (main.jar copied through run_server). NG still launches through `app/src/main/assets/run_server.sh` and `servermanager/AdbLaunchFiles.java`, and its server has run apart from upstream's since 6e8f45d31 (port 62001 plus user ID, process `amng_server`).
  Touches: libserver/, server/, adb/, servermanager/.
  Acceptance: root mode still starts the server on a rooted Android 16 runtime through the native launcher, ADB mode still starts it, and the ported commits are attributed.
  Merged: INIT-D3 (HMAC mutual auth and native run_server port).
  Unblocked 2026-11-07: the upstream commits are public, and the rooted API 36 Magisk AVD on disk is the Android 16 root runtime the acceptance asks for.
  Complexity: M

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

- [ ] P2: Log permission watcher drift to the app change feed
  Why: the watcher (`permission/watcher/`) reports a rule mismatch it couldn't fix only as a notification, so once that's dismissed there's no record of which app drifted from the user's rule or when.
  Evidence: `permission/watcher/PermissionWatchWorker.java:49-52` passes the report to `PermissionWatchNotifier.notify` and nothing else; `permission/monitor/AppChangeFeedStore.java` already keeps the install and update history.
  Touches: `permission/watcher/PermissionWatchWorker.java`, `permission/monitor/AppChangeFeedStore.java`, the feed's type labels, a worker test.
  Acceptance: every drift in a report, fixed or not, appends one feed entry naming the app, the permission or op, the rule's state and the state found; an entry for a drift that was fixed says so; a test with a faked rule DAO and grant lookup asserts the entries and fails with the append removed.
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

- [ ] P1: Store exact mutation before and after images
  Why: inverse-operation rollback can overwrite a newer change because it does not prove that current state still matches the state AppManagerNG wrote.
  Evidence: upstream App Manager #1959; Thor's Changed elsewhere model; `history/ops/PerAppRollbackManager.java`; current operation history and App Change Feed stores. The existing App Details readback item covers two screen caches, not durable cross-operation rollback.
  Touches: operation-history schema, mutation executors, authoritative readback adapters, `PerAppRollbackManager.java`, rollback review UI, retention and export, tests.
  Acceptance: every supported mutation records target, operation, exact before value, requested value, confirmed post value, execution identity, result, and timestamp in an append-only entry; rollback reads current state first and proceeds only when it matches the recorded post value; a mismatch displays Changed elsewhere with both values and performs no write; partial batch results remain independently reversible; schema migration, pruning, restart, export, and tamper tests pass.
  Research 2026-11-13: upstream's unreleased Actions engine (434037c40, 2026-10-12) tracks `RevertMode` and `ActionConflict` and refuses to revert over an outside change, the same check this item asks for. Borrow its conflict cases, not the engine (RESEARCH.md, Rejected Ideas).
  Complexity: L

- [ ] P1: Decode binary XML with an API-correct framework table
  Why: the decoder always loads the newest Android framework resources, so an old numeric resource ID can be labelled as a different modern attribute.
  Evidence: upstream App Manager #2040; `apk/parser/AndroidBinXmlDecoder.java`; Android resource-table evolution across API levels.
  Touches: `AndroidBinXmlDecoder.java`, compiled-against API metadata discovery, framework table selection and cache, raw-ID rendering, old-manifest fixtures, tests.
  Acceptance: the decoder uses the APK's compiled-against API metadata when present; when it is absent, a name is emitted only if every plausible framework table agrees, otherwise the hexadecimal resource ID is preserved; fixtures compiled against at least API 21, 28, 35, and 37 assert stable attribute names and values; malformed tables still produce a bounded parse error.
  Research 2026-11-13: API 37 adds `PackageManager.parseAndroidManifest(ParcelFileDescriptor)`, a platform answer to check the decoder against on API 37. Upstream #2060 (2026-10-03) failed on a chunk of type 0, but its sample APK returns 403, so it can't be a fixture.
  Complexity: M

- [ ] P1: Keep shortcut-launched targets in Recents
  Why: the shortcut trampoline is excluded from Recents and no-history, then calls `finishActivity(0)`, which can leave the launched target without the normal task lifecycle users expect.
  Evidence: upstream App Manager #2054; Android Tasks and Recents documentation; shortcut-management guidance; `details/ActivityLauncherShortcutActivity.java`; its manifest entry.
  Touches: shortcut trampoline lifecycle, target launch-intent construction, manifest task flags, shortcut regression tests, API 21 through 37 device checks.
  Acceptance: the trampoline calls `finish()` or `finishAndRemoveTask()` on itself after a successful handoff and never relies on request code 0; a target launched from an AppManagerNG shortcut appears in Recents with the target app's normal label and task behavior unless that target itself opts out; repeated launches do not create duplicate orphan tasks; cancellation and missing-target paths close cleanly.
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

## Research-Driven Additions (2026-11-13)

Sources and reasoning: RESEARCH.md (2026-11-13). The four crash fixes come first, then the Android 17 items, then the rest.

### P2

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
