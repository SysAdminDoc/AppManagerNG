# Research: AppManagerNG

Date: 2026-09-30. Replaces all prior research.
Reviewed source: v0.6.24 tree at 4a280e767 plus the uncommitted 2026-09-26 drain work. Latest published release: v0.6.23 (2026-09-05).

## Executive Summary

AppManagerNG is an offline-first Android package manager that continues upstream App Manager with root, ADB, Shizuku, Dhizuku and no-root execution, evidence-heavy inspection, backups, debloat guidance and an auditable change history. The 2026-09-25 pass's four P0 trust fixes all landed on 2026-09-26 (Bouncy Castle 1.86 with bounded keystore imports, a side-effect-free no-root start, Usage Access keyed to the app's own grant, and missing telephony binders treated as no data). The weakest area is now the privileged transport itself. Both ADB modes have been broken since 2026-06-12 by a fork-only hardening change, upstream spent 2026-09-12 rewriting the same state machine, and Android 17 changed wireless debugging underneath it. Fix the transport first, then make crash reports reliably reach the maintainer, then return to the rollback and inventory items already queued.

Top opportunities, in priority order:

1. **Verified, P0 (fork #20):** ADB over TCP and Wireless Debugging cannot start the server. `4be517ffd` and `9b90177d1` stage `run_server.sh` and `main.jar` in the app's device-protected cache, which SELinux forbids the `shell` domain from entering.
2. **Verified, P1:** keep the fork's privileged server apart from upstream's. Both use port `60001+userId` and process name `am_local_server`, and the fork is designed to install beside upstream.
3. **Verified, P1:** port upstream's 2026-09-12 mode-of-operation and server hardening, and the pre-v4.1.1 wireless-debugging fixes the fork never took.
4. **Verified, P1:** the crash handler can lose its own notification on API 34 and later and can skip the default handler if report building throws (upstream `edfae0b04`).
5. **Verified, P1:** setting an AppOp decides whether to grant or revoke its linked permission from the current mode instead of the requested one (upstream `6495496ce`).
6. **Verified, P2:** classify ADB-mode failures with a stable code and one next step, covering Android 17's untrusted-network shutoff and the two open libadb-android defects.
7. **Verified, P2:** preflight the base APK's native ABIs, not only selected ABI splits.
8. **Verified, P2:** stop `FmProvider` proxying its own authority (upstream `9345675c1`) and label the 12 unlabeled image controls.
9. **Carried forward, P1:** mutation before and after images, API-correct binary XML decoding, archived-package inventory, SAF grant reconciliation, and the DocumentsProvider child contract. All five were re-checked on 2026-09-30 and remain open.

## Product Map

### Core workflows

- Inventory and search apps, then inspect identity, signing, permissions, AppOps, components, trackers, native libraries (including 16 KB alignment), storage, usage and exit reasons.
- Apply package, permission, AppOps, component, freeze, suspend, archive and debloat operations through root, ADB, Shizuku, Dhizuku or no-root.
- Inspect and install APKs and split bundles with signer, ABI and dependency preflight.
- Back up, restore and convert app data to local or SAF destinations, on demand or on a schedule.
- Diagnose with logcat, running processes, terminal, file manager, code editor, App Change Feed, crash sink and support bundle.

### User personas

- Privacy-minded users who want evidence before changing permissions or AppOps.
- Power users managing many packages, users and profiles.
- Root, Shizuku and wireless-debugging users without an account or cloud dependency.
- Troubleshooters comparing APKs, signers, manifests and package state.

### Platforms and distribution

- Android API 21 through 37 (compile 37, target 36), Java and Android Views, Material 1.13.
- `floss` and `full` flavors, published as signed universal APKs on GitHub Releases with reproducibility and dependency evidence. Store listings are out of scope by owner policy.

### Key integrations and data flows

- Privileged calls: mode selection in `settings/Ops.java`, then `servermanager/LocalServer`, the `run_server.sh` launcher, `am.jar` and `main.jar`, a loopback token handshake, and binder services.
- On-device ADB uses libadb-android 3.1.1 against `127.0.0.1`. Shizuku uses Shizuku-API 13.1.5. Root uses libsu 6.0.0.
- Untrusted inputs: APKs, manifests, DEX, archives, keystores, fonts, images, backups, settings snapshots, DocumentsProvider URIs and intents from other apps.

## Competitive Landscape

| Product | Does well | Learn | Avoid |
|---|---|---|---|
| [Upstream App Manager](https://github.com/MuntashirAkon/AppManager) | Fast fixes to the privileged transport (2026-09-12 wave), new viewers | Port transport and correctness fixes with host tests | Wholesale merges; the fork's privilege model and release model differ |
| [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) | v2.2.0-rc0 (2026-09-29) adds a second ADB transport beside Shizuku, cancelable scans | Cancelable, progress-visible long scans | A second transport stack; this fork already has four |
| [Thor](https://github.com/trinadhthatakula/Thor) | Near-daily releases, App Ops inside permission management, HyperOS Shizuku freeze hotfix (v1.96.3) | OEM-specific failure handling as a first-class concern | Release churn without evidence |
| [PermissionManagerX](https://github.com/mirfatif/PermissionManagerX) | Permission watcher, ADB pairing helper (v1.34-pro, 2026-09-29) | Report-only drift on existing snapshots | Paywalled core features (now ships only a pro build) |
| [ShizuWall](https://github.com/AhmetCanArslan/ShizuWall) and [De1984](https://github.com/dorumrr/de1984) | Per-app network control via system services, shared-UID verdicts, work-profile enumeration | Shared-UID aware network verdicts for the planned INTERNET revocation | A VPN fallback |
| [Inure](https://github.com/Hamza417/Inure) | Dense package evidence; fixed an image-viewer file-access advisory (build107.2.3) | Treat every viewer as untrusted-input code | Breadth that doesn't improve trust |
| [Canta](https://github.com/samolego/Canta) and [UAD-NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | Accessible debloat lists | Device-qualified provenance; both trackers ask for automatic settings backup | Universal safe-to-remove labels (UAD-NG #1477, #1479, #1481 are all OEM breakage) |
| [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) | Granular installer options | #838: an x86_64-only APK soft-rebooted an arm64 phone, so preflight ABIs | Network APK streaming |
| [Neo Backup](https://github.com/NeoApplications/Neo-Backup) and [DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup) | Focused backup flows | Footprint preflight | Neo Backup has had no commit since 2026-05-03 with 245 open issues |
| [Blocker](https://github.com/lihenggui/blocker) | Component blocking UX | Bulk "unblock all" recovery (#1578) | IFW-only claims for providers |
| [LibChecker](https://github.com/LibChecker/LibChecker) | Library evidence | Reproducibility is a trust claim that can regress (#1760) | CVE claims from class names |
| [restoid](https://github.com/hddq/restoid) | restic-backed incremental root backups | Deduplication as a later storage idea | Breaking the upstream-compatible archive format |

Category signal: the loudest upstream request is Shizuku support (#2050, #2057), which this fork already ships. Competitors are converging on OEM-specific privileged-op failures, multi-user targeting and backup of the app's own settings. AppManagerNG already has the domains; it needs its transports to be dependable and its failures to be legible.

## Reported Issues

### Fork tracker (8 open on 2026-09-30)

| Issue | Finding | Disposition |
|---|---|---|
| [#20](https://github.com/SysAdminDoc/AppManagerNG/issues/20) (2026-09-28) | ADB over TCP and Wireless Debugging fail, Shizuku works, upstream works for the same user | **Verified root cause, new P0.** `ServerConfig.java:50-69` stages the launcher under `getDeContext().getCacheDir()`; `RootServiceManager` copies `main.jar` from the same place in ADB mode. AOSP `private/domain.te` neverallows `shell` on `app_data_file` directories. `RootServiceManagerTest.mainJarStagingPathUsesInternalDeviceProtectedCache` asserts the broken location. Device reproduction still needed. |
| [#19](https://github.com/SysAdminDoc/AppManagerNG/issues/19) | UID missing from rows, automation request | Already queued (UID rows P2, authenticated external operation contract P2). |
| [#18](https://github.com/SysAdminDoc/AppManagerNG/issues/18) | Crash when `isub` is missing | Fixed in the tree by `8517dd357` and `0ee71bf64`; needs a release and a reporter reply. |
| [#17](https://github.com/SysAdminDoc/AppManagerNG/issues/17) | Profile creation crash on API 37 | Still needs a symbolicated trace (existing P1 reproduce item). |
| [#16](https://github.com/SysAdminDoc/AppManagerNG/issues/16) | Usage Access re-asked under Shizuku | Fixed in the tree by `f130ef9e3` and `cd39ab4d1`. The roadmap entry written after those commits was stale and is removed. |
| [#15](https://github.com/SysAdminDoc/AppManagerNG/issues/15) | Permission watcher | Already queued (grant-state drift P2). |
| [#13](https://github.com/SysAdminDoc/AppManagerNG/issues/13) | Russian text clipping | Already queued (long-locale gate P1). |
| [#12](https://github.com/SysAdminDoc/AppManagerNG/issues/12) | Code Editor crash on API 29 armeabi-v7a | Reporter-evidence gated in `Roadmap_Blocked.md`. |

### Upstream tracker and commits since 2026-09-20

- **Ported as new items:** `edfae0b04` crash notification, `6495496ce` AppOps linked permission, `9345675c1` file provider self-proxy, and the 2026-09-12 ModeOfOps/Server wave (`8ba225324`, `4d03b737e`, `8ebd38362`, `43e74db72`, `32d93652c`, `b07e75ec3`, `bc52be1e8`, `d1f9c6b34`, `0152f468f`, `9638823e9`, `d0156c440`, `32ad2377d`).
- **Already handled here:** `2d9223a7a` APKs shared to App Info (fork `ae7ea0ee2`), folder-only sort (shipped in v0.6.16), the Android 17 `IPackageManager` return change carried by the Saloframes fork (fork issue #6).
- **Still open upstream, already queued here:** #2054 Recents, #2043 archived packages, #2040 binary XML.
- **Corroborated:** #2048 got a second reporter on 2026-09-24. This fork fixed startup probing on 2026-09-26, but `RunnerUtils.isRootAvailable()` is still called from App Info menu preparation, `DeviceInfo2.java:158` and `FileUtils.java:241` (existing P2 item).
- **Not acted on:** #2058 per-app MTE mode (one request, no fork demand), #2049 signer search (duplicate), #2056 default-installer toggle (hidden API, no OEM matrix), and the seven stale upstream PRs.

## Security, Privacy, and Reliability

### Verified defects

- `servermanager/ServerConfig.java:50-69` and `ipc/RootServiceManager.java` (ADB branch) put shell-executed files in app-private storage. The launch waits 60 seconds for a `Success!` or `Error!` line (`LocalServerManager.java:209-262`) because a denied `sh` never prints `Error!`.
- `AppPref.java:579-580`, `LocalServer.java:112-120`, `DataTransmission.java:208-211` and `LocalServerManager.java:337`: the fork shares port and process name with upstream, checks liveness by bind only, never waits for a handshake acknowledgement, and `killall am_local_server` kills upstream's server too.
- `misc/AMExceptionHandler.java:36-77` builds the report, writes the crash sink and posts the notification with no try/catch and no pause before the default handler ends the process.
- `details/struct/AppDetailsAppOpItem.java:217-222` uses `getMode()` where the requested `mode` belongs.
- `fm/FmProvider.java:67-68` wraps a URI of its own authority in another proxy layer.
- `apk/installer/InstallDependencyChecker.java:227` checks only selected ABI splits; a single APK whose `lib/` holds no supported ABI reaches session commit.

### Platform and dependency risks

- Android 17 [ADB Wi-Fi 2.0](https://android-developers.googleblog.com/2026/09/wireless-debugging-adb-wifi-2.html) turns wireless debugging off on networks the user hasn't allowed, and replaces the mDNS stack. Needs live validation against libadb-android's discovery.
- libadb-android 3.1.1 has two open defects: [#34](https://github.com/MuntashirAkon/libadb-android/issues/34) streams close 50 to 400 ms after a successful wireless connect, and [#32](https://github.com/MuntashirAkon/libadb-android/issues/32) a static `SSLContext` keeps the old key after re-pairing.
- A Google [feature request](https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/) (IssueTracker 526109803, July and August 2026) proposes binding adbd to `wlan0` only. If it ships, every loopback ADB client, this one included, breaks. Assumption until Google acts.
- One UI 8 kills ADB-spawned servers on wake from doze ([Shizuku #2475](https://github.com/RikkaApps/Shizuku/issues/2475)). Likely applies to this fork's ADB-launched server; it belongs to the blocked wireless-ADB resilience item.
- Vendored Commons Compress is a tar plus zip-encoding subset. `TarUtils.parsePaxHeaders` bounds reads by `headerSize` (`TarUtils.java:699-708`), so the DUMP, Pack200, 7z and ZIP-reader advisories do not apply. Verified, no action.
- Dependency currency: Gradle 9.8.0 (2026-09-24), baksmali 3.0.10 and jadx 1.5.6 exist. None carries a security fix for this app's reachable surface. Leave them to the normal refresh.

### Missing guardrails

- A launch command that fails should fail fast: append an explicit `|| echo "Error! ..."` so the reader stops waiting.
- Every privileged-mode failure should carry a stable code into the UI and the support bundle, as the Shizuku diagnostics item already requires for Shizuku.
- Crash reporting is the maintainer's only channel for #12, #17 and #18; it must never be the thing that fails.

### Recovery and rollback

- Unchanged from 2026-09-25: exact before and after images with conflict-aware rollback, grant handoff that keeps one working SAF destination, bounded untrusted-input failures that keep the last confirmed state.

## Architecture Assessment

- **Hot spots.** In the last 200 commits, `AppInfoFragment.java` (over 4,000 lines), `backup/RestoreOp.java`, `settings/Ops.java` and `main/MainViewModel.java` took 4 or 5 fix commits each. `Ops.java` is the privileged-mode state machine that upstream just rewrote. Port upstream's guard and health-check shape there before adding more fork-only branches.
- **Tests that pin bugs.** `RootServiceManagerTest.mainJarStagingPathUsesInternalDeviceProtectedCache` encodes an assumption that was never checked against SELinux. Contract tests for the launcher must assert what the `shell` domain can reach, not only file modes.
- **Coverage.** 933 first-party Java files (215k lines), 489 host test classes, 6 instrumentation classes. Privileged transports have no device test; the S22 and S25 are available for that.
- **i18n.** 37 locales. `scripts/translation_quality.py` counts 3,682 translatable strings; ru misses 2,394, zh 2,363 and de 2,424, mostly NG-added strings that were never translated. The gate already blocks new regressions, and the existing blocked translation-pipeline item covers the backlog.
- **Accessibility.** 12 image controls lack a label or a decorative flag (`dialog_backup_tasks.xml` 5, `dialog_restore_tasks.xml` 3, one each in `dialog_backup_restore.xml`, `item_icon_title_subtitle.xml`, `item_main.xml`, `item_main_v2.xml`). `app/lint.xml` does not escalate `ContentDescription`.
- **Debt worth knowing.** `servermanager/LocalServerManager.java:417` (no per-session TLS, covered by the blocked secure-session item), `permission/PermUtils.java:337-380` (policy-fixed permission handling left commented out), `details/info/AppInfoFragment.java:2940` (no uninstall watch, covered by the authoritative readback item).
- **Category coverage.** Security, reliability, observability, accessibility and testing have new items. Offline, multi-user, migration and upgrade strategy are covered by existing items (SAF grants, archived packages, the blocked capability matrix and AppsDb ladder, the API-21 dependency ledger). Distribution is GitHub Releases only by owner policy. Plugin ecosystems stay rejected. Mobile form factors beyond phones and tablets are not targeted.

## Rejected Ideas

- 16 KB page-size checks: already shipped in `scanner/NativeLibReadiness.java`.
- App lock or PIN gate (ShizuWall v4.6.4): already shipped (`misc/ScreenLockChecker.java`).
- Exit-reason display: already shipped through `ActivityManagerCompat` and App Info.
- IFW provider blocking gap (upstream #1218): `rules/compontents/ComponentsBlocker.java` already blocks providers through the package manager.
- A second ADB transport like SD Maid SE's Porter: four transports already exist; make them dependable instead.
- VirusTotal scanning (universal-installer): uploads user package data to a third party, against the offline-first model.
- restic-style deduplicated backups (restoid): breaks the upstream-compatible archive format for a storage saving nobody has asked for here.
- Build commit baked into the APK (De1984 v2.7.4): tags, published mappings and reproducibility receipts already bind a release to its source.
- Obfuscation-resilient library detection (SAD, [arXiv 2504.13547](https://arxiv.org/abs/2504.13547)): promising but XL; revisit if tracker false negatives are reported.
- Background-execution control like Thanox: needs Xposed hooks this app doesn't use.
- IzzyOnDroid submission and any store or listing work: owner policy excludes store distribution. The blocked item was removed on 2026-09-30.
- Carried from 2026-09-25: cloud backup accounts, a VPN firewall, a plugin runtime, universal debloat labels, class-name CVE verdicts, a Compose migration, and a default-installer toggle without an OEM matrix.

## Sources

### Project and trackers

- https://github.com/SysAdminDoc/AppManagerNG/issues/20
- https://github.com/SysAdminDoc/AppManagerNG/issues/19
- https://github.com/SysAdminDoc/AppManagerNG/issues/18
- https://github.com/SysAdminDoc/AppManagerNG/issues/16
- https://github.com/SysAdminDoc/AppManagerNG/discussions/5
- https://github.com/MuntashirAkon/AppManager/commits/master
- https://github.com/MuntashirAkon/AppManager/commit/edfae0b04
- https://github.com/MuntashirAkon/AppManager/commit/6495496ce
- https://github.com/MuntashirAkon/AppManager/commit/9345675c1
- https://github.com/MuntashirAkon/AppManager/commit/d0156c440
- https://github.com/MuntashirAkon/AppManager/issues/2048
- https://github.com/MuntashirAkon/AppManager/issues/2054
- https://github.com/MuntashirAkon/AppManager/issues/2057
- https://github.com/MuntashirAkon/AppManager/issues/2058
- https://github.com/MuntashirAkon/AppManager/milestones
- https://github.com/Saloframes/AppManager/commit/fe6bcf7d

### Platform, libraries, security

- https://android-developers.googleblog.com/2026/09/wireless-debugging-adb-wifi-2.html
- https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/
- https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/domain.te
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/privacy-and-security/local-network-permission
- https://source.android.com/docs/security/bulletin/2026/2026-09-01
- https://source.android.com/docs/security/features/apksigning/v3-2
- https://github.com/MuntashirAkon/libadb-android/issues/34
- https://github.com/MuntashirAkon/libadb-android/issues/32
- https://github.com/RikkaApps/Shizuku/issues/2475
- https://github.com/iamr0s/Dhizuku-API
- https://github.com/topjohnwu/libsu/releases
- https://github.com/apache/commons-compress/releases
- https://github.com/gradle/gradle/releases/tag/v9.8.0
- https://github.com/google/smali/tags
- https://github.com/skylot/jadx/releases/tag/v1.5.6
- https://www.w3.org/TR/WCAG22/

### Competitors and community

- https://github.com/d4rken-org/sdmaid-se/releases/tag/v2.2.0-rc0
- https://github.com/trinadhthatakula/Thor/releases/tag/v1.96.3
- https://github.com/mirfatif/PermissionManagerX/releases/tag/v1.34-pro
- https://github.com/AhmetCanArslan/ShizuWall/releases/tag/v4.6.4
- https://github.com/dorumrr/de1984/releases/tag/v2.7.8
- https://github.com/Hamza417/Inure/releases/tag/build107.2.3
- https://github.com/samolego/Canta/issues/377
- https://github.com/d4rken-org/sdmaid-se/issues/2807
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1481
- https://github.com/wxxsfxyzm/InstallerX-Revived/issues/838
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/lihenggui/blocker/issues/1578
- https://github.com/LibChecker/LibChecker/issues/1760
- https://github.com/hddq/restoid
- https://github.com/Tornaco/Thanox
- https://github.com/pass-with-high-score/universal-installer
- https://github.com/awesome-android-root/awesome-android-root
- https://github.com/timschneeb/awesome-shizuku
- https://arxiv.org/abs/2504.13547
- https://arxiv.org/abs/2508.02008
- https://arxiv.org/abs/2408.01810

## Open Questions

- Does the #20 reporter also have upstream App Manager installed and running its server on port 60001? That decides whether the shared-port defect contributed.
- Does libadb-android's mDNS discovery still find the pairing and connect services under Android 17's ADB Wi-Fi 2.0 stack?
- Does One UI 8 kill this fork's ADB-launched server on wake the way it kills `shizuku_server`?
- Will Google act on IssueTracker 526109803 (adbd bound to `wlan0` only)? If so, the on-device ADB modes need a LAN-address path and `ACCESS_LOCAL_NETWORK` once the target reaches API 37.
