# Research: AppManagerNG

Date: 2026-11-13. Replaces all prior research.
Reviewed source: `main` at 6420e5c1c (after the v0.7.0 issue drain). Latest published release: v0.7.0 (2026-11-08). Upstream App Manager's latest release is still v4.1.1 (2026-09-04), and its master was read through 2026-11-03.

## Executive Summary

AppManagerNG is an offline-first Android package manager that continues upstream App Manager. It runs through root, ADB, Shizuku, Dhizuku or no-root, and pairs deep package inspection with guarded changes, backups and an audit trail. Since the last pass on 2026-09-30, the fork's issue tracker has driven a burst of new surfaces: notification filtering with quiet mode, toast history, an overlay detector, a permission watcher with instant revoke, Quick Settings tiles and Local sync between phones. Every open fork issue now has a build behind it. The weak spot has moved again. Android 17 changes the ground under every privileged mode at once. The official Shizuku build is broken there, adbd advertises wireless debugging differently, shell can no longer uninstall a system app per user, and one report says Android/data files stop being readable to shell. Meanwhile, upstream fixed four bugs in code the fork shares, and three of them crash the app. Fix those first, then make every privileged mode hold up on Android 17, then pick up the parity and polish items.

Top opportunities, in priority order:

1. **Verified, P1:** four bugs upstream found in shared code are still live here. `getAppOps()` returns null or wipes its own cache (Finder crash and wrong matches), the audio player crashes on a negative position (upstream #2064), an unguarded foreground-service start crashes batch work on Android 17 (upstream #2066), and an install sent while the app lock screen is up drops the first one and runs the second twice.
2. **Likely, P1:** follow Android 17's wireless debugging port when it moves. adbd now advertises through NsdManager under one stored GUID name and no longer sets `service.adb.tls.port`, and Android 14+ NSD answers a resolve from its cache (what broke Local sync rediscovery in the #22 drain). libadb's `AdbMdns` resolves once, so a restarted Wireless debugging session can come back on its old port.
3. **Verified, P2:** judge Shizuku on Android 17 by whether the server answers. NG warns every Android 17 Shizuku user today, because the official 13.6.0 shows an empty authorized-apps list there (RikkaApps/Shizuku #2180, #2681), so users on a working Shizuku-API server get the same warning.
4. **Verified, P2:** explain an unreachable work profile in ADB mode (`DISALLOW_DEBUGGING_FEATURES`, upstream #2059) instead of showing nothing.
5. **Verified, P2:** when Android refuses a system app uninstall, offer to freeze in the same flow (Android 17 shell check, UAD-ng's switch to disable by default).
6. **Verified, P2:** stop treating other tools' pauses as NG's own (Thor PR #599).
7. **Verified, P2:** prepare for target SDK 37 (local network permission, IntentSender background starts, `USE_LOOPBACK_INTERFACE`).
8. **Verified, P2:** mark permissions an update brought in already granted, which a 2026 study found in 17% of apps with several versions.

## Product Map

### Core workflows

- Inventory and search apps, then inspect identity, signing, permissions, AppOps, special app access, components, trackers, native libraries, storage, usage and exit reasons.
- Change package, permission, AppOps, component, freeze, suspend, archive and debloat state through root, ADB, Shizuku, Dhizuku or no-root, one app or a batch at a time, with profiles and previews.
- Inspect and install APKs and split bundles with signer, ABI and permission-change preflight, through a queue.
- Back up, restore and convert app data, on demand or on a schedule, and move apps and APKs between two phones with Local sync.
- Watch the device: permission watcher with instant revoke, App Change Feed, notification filter and history, toast history, top-app and overlay history, logcat, terminal and file manager.

### User personas

- Privacy-minded users who want evidence before changing permissions or AppOps.
- Power users managing many packages, users and profiles, often without root.
- Users chasing adware or noisy apps (fork #26, #27, #32).
- Troubleshooters comparing APKs, signers, manifests and package state.

### Platforms and distribution

- Android API 21 through 37 (compile 37, target 36), Java and Android Views, Material 1.13. The API 21 floor is policy (`docs/policy/minsdk-21-ceiling.md`).
- `floss` and `full` flavors, published as signed universal APKs on GitHub Releases with reproducibility evidence, and installable through Obtainium.

### Key integrations and data flows

- Privileged calls: `settings/Ops.java` picks the mode, then the local server and binder services. On-device ADB uses libadb-android 3.1.1 against 127.0.0.1, Shizuku uses Shizuku-API 13.1.5, root uses libsu 6.0.0, and Dhizuku is detected but only used for installs.
- Listeners: a NotificationListenerService (filter and history), an AccessibilityService (toasts, top app, overlays), package broadcasts and WorkManager jobs (watcher, scheduled backups).
- LAN: Local sync discovery and transfer over NsdManager and TCP (`sync/lan/`).
- Untrusted inputs: APKs, manifests, DEX, archives, keystores, backups, snapshots, DocumentsProvider URIs, LAN peers and intents from other apps.

## Competitive Landscape

| Product | Does well | Learn | Avoid |
|---|---|---|---|
| [Upstream App Manager](https://github.com/MuntashirAkon/AppManager) | Steady fixes in shared code. An "Actions" engine on master (2026-10-12 to 11-03) with 27 action kinds, revert modes and conflict detection | Port the shared-code crash fixes. Borrow the idea of checking for an outside change before reverting | A wholesale port of an unreleased engine that would fork the profile model |
| [Thor](https://github.com/trinadhthatakula/Thor) | Real Dhizuku execution (hide for freeze, device-owner grants, clear data, uninstall), keeps other tools' pauses (PR #599), settings editor with read-back and guarded undo (v1.97.1) | Dhizuku executor shape, suspension ownership, guarded settings writes | Near-daily release churn |
| [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) and [Porter](https://github.com/d4rken-org/porter) | 2.2.0 (2026-09-29) supports Porter, a Shizuku-API compatible server, as Android 17 broke the official Shizuku | Test NG against compatible servers instead of assuming one vendor | Porter's own SDK (Kotlin coroutines, minSdk 24) |
| [Shizuku](https://github.com/RikkaApps/Shizuku) and its forks | The API every rootless tool targets | The official 13.6.0 (2025-05-25) is broken on Android 17. awesome-shizuku lists 8 forks, and users are split between them | Pointing users at one stale build |
| [UAD-ng](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | v1.3.0 (2026-11-02) made disable the default after Android 17 refused shell uninstalls of system apps | Fall back to a reversible action when removal is refused | Universal "safe to remove" labels |
| [Canta](https://github.com/samolego/Canta) and [Hail](https://github.com/aistra0528/Hail) | Simple debloat and freeze. Canta's top asks are disable (+9) and freeze (+6) | People want debloat and freeze in one app, which NG already is (r/fossdroid, 2026-11-06) | Splitting the two |
| [LibChecker](https://github.com/LibChecker/LibChecker) | 2.5.5 (2026-11-05) detects AppFunctions, v3.2 signatures and R8 mode, and shows DEX and resource sizes | Static signals that cost nothing at runtime | Cosmetic navigation work |
| [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) and [Obtainium](https://github.com/ImranR98/Obtainium) | Install pre-approval and a 100-record history (InstallerX 26.09). Per-app install method (Obtainium 1.7.4) | Named install presets (already queued) | Duplicating an update tracker |
| [Swift Backup](https://www.swiftbackup.app/) (commercial) | Charges for schedules, multiple versions, labels and cloud. Free tier backs up Wi-Fi networks through Shizuku on Android 11+ | NG already gives away what Swift charges for, except cloud. Wi-Fi backup without root is the gap | Cloud accounts |
| [Neo Backup](https://github.com/NeoApplications/Neo-Backup) and [DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup) | Root backup with schedules | People want more than apps backed up. Neo Backup #86 (+7) asks for contacts and SMS, and upstream App Manager #568 (+3) asks for Wi-Fi networks too | Their root-only assumption |
| [BuzzKill](https://buzzkillapp.com/) (commercial) | Cooldown (mute repeats for N minutes) and batch delivery are its core actions | Two actions NG's filter lacks | Paid rule packs |
| [ShizuWall](https://github.com/AhmetCanArslan/ShizuWall) and [De1984](https://github.com/dorumrr/de1984) | Per-app network control through system services. ShizuWall 4.6.8 and 4.7 hide shared-UID system apps and block shared-UID apps together | Shared-UID awareness, which NG already has for AppOps (`AppOpsUidGuard`) | A VPN fallback |
| [LocalSend](https://github.com/localsend/localsend) | 1.18 (2026-08-10) adds a browser receive page and checksums by default | A one-time LAN page could send an APK to a phone without NG (under consideration) | Serving HTTP on the LAN without a threat model |

## Reported Issues

### Fork tracker (SysAdminDoc/AppManagerNG)

- Open: #15, #19, #22, #26, #27, #31, #32 and #33 are feature requests that all have a build behind them and a reply posted or queued. What's left is device proof, not code: the #22 Local sync rows that need two phones (signature differs, update all newer, resume, Received APKs), #15's instant revoke in ADB mode, and #20 with adbd's secure auth on. Those are already ROADMAP items under "Issue Drain Left Open".
- #30 is a distribution request. It's left to the owner and isn't part of this plan.
- Discussion #5 asks for full Dhizuku support because ColorOS 16 restricts ADB. Lane research found no blanket ADB block on ColorOS 16. It found trimmed shell permissions (realme lacks `CREATE_VIRTUAL_DISPLAY`, dsh-Go #1153), changed dumpsys output (NOIR #19) and a "Permission monitoring" toggle. Dhizuku covers suspend, hide and grants but not shell, so it's a partial fallback. Existing items "Dhizuku freeze/suspend executor parity" and "Make the Dhizuku capability copy match what Dhizuku can actually do" stand, with new notes.
- Discussion #29 is praise that mentions crashes without logs. Nothing to act on.
- Closed since the last pass: #14, #16, #17, #18, #20, #21, #25 and #28. The 200-commit history shows the fixes clustered in the installer, Local sync, ADB connection handling and tiles, which is where the next regressions are most likely.

### Upstream tracker (shared code)

- #2064 (2026-10-11): Slider crash at value -13 in the audio player. NG has the same code. `viewer/audio/AudioPlayerDialogFragment.java:184-186` checks only the upper bound, and `:259` can seek below zero. New P1.
- #2066 (2026-10-17): freezing through "Advanced pause" crashes on Android 17 (PixelOS) with `SecurityException` from a specialUse foreground-service start. NG's `types/ForegroundService.java:36-40` has no guard, and `apk/behavior/FreezeUnfreezeService.java:134` is the same path. New P1.
- #2059 (2026-10-03): work profile apps missing in wireless ADB mode on One UI 8.5. The likely cause is `UserController.handleIncomingUser` refusing shell when the profile has `DISALLOW_DEBUGGING_FEATURES`. New P2.
- #2068 (2026-10-25): wants files moved out of Android/data automatically in ADB mode. Under consideration until the Android 17 Android/data check (new P2) settles whether shell can still read there.
- #2060 (2026-10-03): manifest with chunk type 0 fails to parse. The sample APK returns 403 to everyone, so it can't be reproduced. Noted on the binary XML item.
- #2058 (2026-09-25, accepted upstream): asks for `android:memtagMode="async"`. New P3.
- #2027 (2026-08-16): per-app language switcher. NG sets one app at a time. New P3 for batches.
- #1959 (2026-04-26): revert every change made to one app. That's the existing "Store exact mutation before and after images" item.
- #2065 is a help request with neither root nor ADB. Nothing to act on.
- Upstream commits checked against NG: b11644c3b (no-root shell probing) is half done here. `runner/NormalShell.java:20` already avoids the probe, and the rest is the existing "Stop screens probing for root while no-root is selected" item. c187298ae doesn't apply, because `settings/ModeOfOpsPreference.java` was rewritten here. 84603ef98 and c4c81f492 do apply (new P1 items). afbe5b329 reverted regex quoting, and NG never quoted (`filters/options/FilterOption.java:140`).

## Security, Privacy, and Reliability

### Bugs and risks found

- `main/ApplicationItem.java:590-600` and `filters/FilterableAppInfo.java:249-257` reset `mAppOpEntries` to an empty list on every call after the first, because the `else` branch also runs when the cache is filled. They return null when the ops lookup fails, although the getter is `@NonNull`. `filters/options/AppOpsOption.java:92-95` then calls `ops.isEmpty()`. `getAllComponents` and `getUsedPermissions` in `ApplicationItem` also return null without package info.
- `types/ForegroundService.java:36-40` lets `SecurityException` and `ForegroundServiceStartNotAllowedException` escape from five services: top app overlay, freeze, progress, Wi-Fi wait and session monitoring.
- `apk/installer/PackageInstallerActivity.java:629-660` calls `setIntent` and `enqueue` before `onAuthenticated` has run (`BaseActivity.java:76-97`), and `onAuthenticated` enqueues `getIntent()` again (`:324-341`). The queue is a plain `LinkedList` (`:286`).
- `compat/PackageManagerCompat.java:693-711` suspends with a null `SuspendDialogInfo`. In ADB mode the caller is the shell package, so NG's unsuspend can clear a pause another shell tool set.
- `shizuku/ShizukuBridge.java:50` leaves `MIN_ANDROID_17_COMPATIBLE_VERSION` null, so `hasAndroid17CompatibilityRisk` (`:342-357`) is true for every Shizuku install on Android 17. The warning (`onboarding_mode_shizuku_android17_warning`) sends users to Wireless ADB, which is right for 13.6.0 and noise for a server that works.

### Missing guardrails

- No `USE_LOOPBACK_INTERFACE` declaration. It's a new normal permission in Android 17 that will guard 127.0.0.1 once `full_loopback_protections_enabled` is enforced (frameworks/base `permissions/flags.aconfig` at android-17.0.0_r1). Cross-profile loopback blocking exempts shell and root (`netd.c`), so the local server's channel is safe for now.
- No `memtagMode`. Upstream accepted async MTE in #2058.
- Guava is pinned at 32.1.3-android, inside GHSA-xxph-c9ww-hj94 (≤33.7.1). It can't be bumped away because Guava 33.5.0 raised its minimum to API 23 and 33.7.0 to API 24. Reachability is low, since NG doesn't Java-serialize Guava types, and that belongs in the existing CVE reachability item.
- The jadx fork at 1.4.7 is inside GHSA-hvp5-5x4f-33fq's affected range (file override). `skipResources(true)` keeps it unreachable, and the existing item "Bound embedded JADX class decompilation and pin its reachable surface" already holds that with a contract test.
- The vendored commons-compress subset (`thirdparty/apache/commons/compress`) holds only tar, bzip2, gzip and zip encoding, and its tar reader carries the 1.21+ Pax header bounds checks (`TarUtils.java:709-760`). GHSA-4265-ccf5-phj5 (DUMP) and GHSA-4g9r-vxhx-9pgx (Pack200) don't apply.
- The November 2026 Android Security Bulletin (published 2026-11-02, 216 CVEs) has no entry that clearly concerns adbd, backup, package installation or multiple users. A few Settings entries may touch permissions or app ops, but the bulletin doesn't say. CVE-2026-0073, the wireless ADB pairing bypass behind the kitsumed write-up, is patched in the platform and needs nothing from NG.

### Recovery and rollback

- A refused system app uninstall should turn into an offer to freeze, recorded in the ledger (new P2).
- Upstream's Actions engine records who owns a change (`RevertMode`, `ActionConflict`) and refuses to revert over an outside change. That fits the existing "Store exact mutation before and after images" item as a note.

## Architecture Assessment

### Module and boundary improvements

- Android 17 work is spread across the existing ROADMAP: binding, pre-ANR evidence, binary XML, wireless reconnect. This pass adds port following, the Shizuku warning, target 37 and Android/data. Order them so that discovery and Shizuku come before target 37, because target 37 changes the same network paths.
- Dhizuku is still detection plus install only. Thor shows what an executor looks like (hide for freeze, `setPermissionGrantState`, device-owner clear data), and Dhizuku 2.12.0 (2026-06-24) works as profile owner too.

### Refactor candidates

- `details/info/AppInfoFragment.java` is 4,374 lines (235 KB) and `details/AppDetailsViewModel.java` is 122 KB. Only one commit has touched `AppInfoFragment` since 2026-09-01, so split it when a feature lands there (batch language would be one), not as its own project.
- `ApplicationItem` and `FilterableAppInfo` duplicate the same lazy getters with the same bug. Fix both, then consider one shared helper.

### Test and documentation gaps

- No host test calls an `IFilterableAppInfo` getter twice or with missing package info.
- No test drives the installer's `onNewIntent` before authentication.
- No test makes `startForeground` throw.
- Dumpsys parsers (`compat/ActivityManagerCompat.java`, `compat/JobSchedulerCompat.java`, `details/info/AppMemoryInfoParser.java`) have AOSP fixtures only.
- Docs don't say which Shizuku server works on Android 17.
- The unit suite has 642 test files against 1,109 main sources. There are 7 instrumented tests.

## Rejected Ideas

- Port upstream's Actions engine (upstream 434037c40 through dc4154f4c): unreleased, 27 action kinds, and it would fork NG's profile model. Its revert-conflict idea goes into an existing item instead.
- Port upstream's October VirusTotal rewrite (cd9e9a373 through 0d676ae6d): the Full flavor already has optional VirusTotal reports, and the FLOSS default stays offline.
- PDF, image and font viewers (upstream, September to October 2026): the system's viewers cover them. Isolated decoding is already tracked in "Decode untrusted file thumbnails in an isolated process".
- Shared-UID AppOps warning (upstream PR #2073): already shipped here as `AppOpsUidGuard` (`compat/AppOpsManagerCompat.java:819-832`).
- Move interceptor history out of default preferences (upstream d075eb63f): snapshots only export an allowlist that excludes default prefs (`snapshot/SnapshotBundle.java:150-165`), so there's no leak to fix.
- Guava bump to clear GHSA-xxph: 33.5.0 needs API 23 (Guava release notes).
- Apply commons-compress GHSA-4265 and GHSA-4g9r: the vendored subset has no DUMP or Pack200 code.
- Support the PackageInstaller dependency installer: deprecated in Android 17 (`core/api/current.txt` at android-17.0.0_r1).
- Read `service.adb.tls.port` for discovery: Android 17 no longer sets it (`adb_wifi.cpp` at android-17.0.0_r1).
- App-side mitigation for IssueTracker 526109803 (adbd on wlan0 only): never shipped. adbd still binds every interface at android-17.0.0_r1 (kitsumed blog, edited 2026-08-04).
- Dhizuku as a full replacement for ADB mode: no shell capability (upstream #1981, #1481).
- Porter's SDK directly: Kotlin coroutines and minSdk 24. Its Shizuku compatibility mode covers NG.
- Shizuku stealth or renamed packages to hide from banking apps: fork territory (thedjchi's fork does it).
- Android 17 App Lock APIs: the flags are off in the cp2a release config.
- sora-editor 0.24.7 (prerelease, heading to minSdk 23) and androidx bumps that raise minSdk (`versions.gradle` notes).
- A leaked platform certificate list (arXiv 2606.21487): the paper doesn't publish fingerprints.
- Carried from 2026-09-30 and earlier: 16 KB page checks (shipped), app lock (shipped), exit reasons (shipped), IFW provider gap (handled), a second ADB transport, restic-style dedup backups, build commit in the APK, SAD obfuscation detection, Thanox-style background control, cloud backup, a VPN firewall, a plugin runtime, universal debloat labels, class-name CVE verdicts, a Compose migration, a default-installer toggle and distribution listings (owner policy).

### Under consideration (not on the ROADMAP yet)

- Systemless debloat through a root module (upstream PR #1017, open since 2023 and touched 2026-10-19). Scalpel, System App Nuker and De-Bloater on awesome-android-root work this way. It needs a bootloop recovery story first.
- A one-time LAN page to send an APK to a phone without NG (LocalSend 1.18). It needs a threat model for serving HTTP on the LAN.
- Install pre-approval (`requestUserPreapproval`, InstallerX 26.09). It only helps no-root installs on API 34+.
- SMS and call log backup (Swift Backup's free tier, Neo Backup #86). That's outside app management.
- Android/data automation rules (upstream #2068). Wait for the Android 17 Android/data check.
- App Details tab order and visibility (upstream master, September 2026).

## Sources

### OSS competitors
- https://github.com/MuntashirAkon/AppManager/releases
- https://github.com/MuntashirAkon/AppManager/commit/434037c40
- https://github.com/MuntashirAkon/AppManager/commit/c6728f56c
- https://github.com/MuntashirAkon/AppManager/commit/84603ef98
- https://github.com/MuntashirAkon/AppManager/commit/c4c81f492
- https://github.com/MuntashirAkon/AppManager/commit/b11644c3b
- https://github.com/MuntashirAkon/AppManager/commit/d075eb63f
- https://github.com/MuntashirAkon/AppManager/pull/2073
- https://github.com/MuntashirAkon/AppManager/pull/1017
- https://github.com/MuntashirAkon/AppManager/issues/2059
- https://github.com/MuntashirAkon/AppManager/issues/2060
- https://github.com/MuntashirAkon/AppManager/issues/2064
- https://github.com/MuntashirAkon/AppManager/issues/2066
- https://github.com/MuntashirAkon/AppManager/issues/2068
- https://github.com/MuntashirAkon/AppManager/issues/2058
- https://github.com/MuntashirAkon/AppManager/issues/2027
- https://github.com/MuntashirAkon/AppManager/issues/1959
- https://github.com/MuntashirAkon/AppManager/issues/568
- https://github.com/trinadhthatakula/Thor/releases
- https://github.com/trinadhthatakula/Thor/pull/599
- https://github.com/d4rken-org/sdmaid-se/releases
- https://github.com/d4rken-org/porter
- https://github.com/RikkaApps/Shizuku/issues/2180
- https://github.com/RikkaApps/Shizuku/issues/2681
- https://github.com/RikkaApps/Shizuku/issues/2596
- https://github.com/thedjchi/Shizuku/releases
- https://github.com/iamr0s/Dhizuku/releases
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1426
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1436
- https://github.com/samolego/Canta/releases
- https://github.com/LibChecker/LibChecker/releases
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases
- https://github.com/AhmetCanArslan/ShizuWall/releases
- https://github.com/NeoApplications/Neo-Backup/issues/86

### Commercial
- https://www.swiftbackup.app/
- https://buzzkillapp.com/

### Adjacent
- https://github.com/localsend/localsend/releases
- https://github.com/ImranR98/Obtainium/releases
- https://github.com/Verlintas/NOIR/issues/19
- https://github.com/ccch1mneyyy/dsh-Go/issues/1153

### Awesome lists
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root

### Community
- https://news.ycombinator.com/item?id=49193173
- https://www.reddit.com/r/ShizukuforAndroid/comments/1xmoo42/
- https://www.reddit.com/r/ShizukuforAndroid/comments/1xbl3af/
- https://www.reddit.com/r/ShizukuforAndroid/comments/1xbrw54/
- https://www.reddit.com/r/fossdroid/comments/1xpalsj/
- https://www.reddit.com/r/UPI/comments/1w7zsmt/

### Platform and specs
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/tools/adb
- https://9to5google.com/2026/09/10/google-details-adb-wi-fi-2-0-for-more-reliable-wireless-android-debugging/
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/tags/android-17.0.0_r1/daemon/mdns.cpp
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/tags/android-17.0.0_r1/daemon/adb_wifi.cpp
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-17.0.0_r1/core/api/current.txt
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-17.0.0_r1/services/core/java/com/android/server/pm/PackageManagerShellCommand.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r4/services/core/java/com/android/server/am/UserController.java
- https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/tags/android-17.0.0_r1/bpf/progs/netd.c

### Papers and engineering blogs
- https://arxiv.org/abs/2605.27667
- https://arxiv.org/abs/2606.21487
- https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/

### Dependency changelogs
- https://github.com/robolectric/robolectric/releases
- https://github.com/google/gson/releases
- https://github.com/google/guava/releases
- https://github.com/google/smali/releases
- https://github.com/luben/zstd-jni
- https://github.com/LSPosed/AndroidHiddenApiBypass/releases
- https://github.com/bcgit/bc-java/tags
- https://github.com/skylot/jadx/releases

### Security advisories
- https://github.com/advisories/GHSA-xxph-c9ww-hj94
- https://github.com/advisories/GHSA-hvp5-5x4f-33fq
- https://github.com/advisories/GHSA-4265-ccf5-phj5
- https://github.com/advisories/GHSA-4g9r-vxhx-9pgx
- https://www.rescana.com/post/android-security-bulletin-november-2026-cve-2026-58868-prepare-now
- https://www.malaysianwireless.com/2026/11/android-november-2026-security-update/

## Open Questions

- Which Shizuku-compatible server should the Android 17 guidance name? Recommending one third-party fork is the owner's call. The options with users are Porter's compatibility mode, Shizuku Next, Shevery and Shizuku+ (r/ShizukuforAndroid 1xbl3af, 2026-10-21).
- Does declaring `ACCESS_LOCAL_NETWORK` while targeting 36 cause any side effect? The local network permission docs say apps targeting 36 or lower shouldn't declare it. Nothing found shows harm, and an API 37 run settles it.
- Is the Android 17 Android/data group change real across devices, or one S25 Ultra build (r/ShizukuforAndroid 1xmoo42, 2026-11-03)? The new P2 item starts by checking this.
