# Research: AppManagerNG

Date: 2026-11-14. Replaces all prior research.
Reviewed source: `main` at 7ff61d2c3. Latest published release: v0.7.0 (2026-11-08). Upstream App Manager's latest release is still v4.1.1 (2026-09-04), and its master was read through dc4154f4c (2026-11-03).

## Executive Summary

AppManagerNG is an offline-first Android package manager that continues upstream App Manager. It works through root, ADB, Shizuku, Dhizuku or no-root, and pairs deep package inspection with guarded changes, backups and an audit trail. The 2026-11-13 pass put Android 17 at the center, and most of that work has since landed or been checked: Shizuku binds on the S25 at API 37, and the foreground-start and audio player crashes are fixed (75e985848, 618c7296d). This pass found the next weak spot somewhere less visible, in the build toolchain behind the reproducible release promise. The Gradle wrapper sits inside six published Gradle advisories, and the pinned AGP lacks an R8 fix for non-deterministic dex output, the very property the byte-identical release gate rests on. Beyond that, Android 17's Advanced Protection quietly takes NG's accessibility features away, one Local sync user can't update about eight apps through NG's installer, and NG's translations stopped at the fork while upstream's kept coming.

Top opportunities, in priority order:

1. **Verified, P1:** move the Gradle wrapper from 9.7.0 to 9.9.1. Six advisories cover 9.7.0, and five of them reach this build (daemon deserialization, registry trust, port squatting, a repository that fails SSL staying enabled).
2. **Verified, P1:** move AGP from 9.3.1 to 9.3.3 for its R8 fixes: non-deterministic dex and mapping output (#554586985), dex that fails verification and crashes on launch (#485558707), and an outliner VerifyError (#551348855).
3. **Likely, P2:** say when Android 17's Advanced Protection has taken NG's accessibility service away. Toast history, the top-app bubble, UI tracker and no-root cache clearing all depend on it, and NG's Advanced Protection row only talks about sideloading.
4. **Verified report, P2:** find out why WhatsApp and about seven other apps fail to update through NG's installer but install through Android's (fork #22, 2026-11-09).
5. **Verified, P2:** bring in the translations upstream's Weblate added after the fork. About 2,100 strings whose English NG never changed are translated upstream and missing here, 1,085 of them Swedish (NG has 44 Swedish strings), and 366 more have been revised upstream.
6. **Verified, P3:** say in words, and to TalkBack, when a component is disabled outside NG. Today the switch reads "Component allowed" and only a stroke color tells the difference (upstream #2071, #2072).
7. **Verified, P3:** sort the app list by cache size (upstream #1524). The getter already exists.
8. **Verified, P3:** offer to put an app's saved blocking rules back when it's reinstalled (AppOpsNext 1.6.7 restores settings after a reinstall).

## Product Map

### Core workflows

- Inventory and search apps, then inspect identity, signing, permissions, AppOps, special app access, components, trackers, native libraries, storage, usage and exit reasons.
- Change package, permission, AppOps, component, freeze, suspend, archive and debloat state through any access mode, one app or a batch at a time, with profiles, routines and previews.
- Install APKs and split bundles through a queue with signer, ABI and permission-change preflight, and move apps between phones with Local sync (`sync/lan/`).
- Back up and restore app data on demand or on a schedule with charging and network conditions (`backup/schedule/AutoBackupScheduler.java:184-185`).
- Watch the device: permission watcher with instant revoke, App Change Feed, notification filter and history, toast history, top-app and overlay history, logcat, terminal and file manager.

### User personas

- Privacy-minded users who want evidence before changing permissions or AppOps.
- Power users managing many packages, users and profiles, often without root.
- Users chasing adware or noisy apps (fork #26, #27, #32).
- People keeping two or more phones in step (fork #22).

### Platforms and distribution

- Android API 21 through 37 (compile 37, target 36), Java and Android Views, Material 1.13. AGP 9.3.1 and Gradle 9.7.0 (`versions.gradle:14`, `gradle/wrapper/gradle-wrapper.properties`).
- `floss` and `full` flavors as signed universal APKs on GitHub Releases. Each release is built twice and published only when the APKs and R8 mappings are byte-identical (README, `scripts/release_gate.py`).

### Key integrations and data flows

- Privileged calls go from `settings/Ops.java` to the local server and binder services. ADB uses libadb-android 3.1.1 against 127.0.0.1, Shizuku uses Shizuku-API 13.1.5, root uses libsu 6.0.0, and Dhizuku is used for installs.
- The server process sends its status and relay broadcasts through an NG package context (`server/.../BroadcastSender.java`, `ipc/RootServiceServer.java:143-153`).
- One AccessibilityService (`accessibility/NoRootAccessibilityService.java`) feeds toast history, the top-app bubble, UI tracker and no-root cache clearing.

## Competitive Landscape

| Product | Does well | Learn | Avoid |
|---|---|---|---|
| [Upstream App Manager](https://github.com/MuntashirAkon/AppManager) | Shared code base. Master adds an Actions engine (through dc4154f4c, 2026-11-03), unreleased | Its tracker still surfaces real confusion (#2071, #2072, #1524) | Porting an unreleased engine that would fork the profile model |
| [AppOpsNext](https://github.com/1zumiii/AppOpsNext) | 1.6.7 (2026-11-14) marks every permission it changed, notices outside changes and offers "restore previous" or "keep current", and can restore an app's settings after a reinstall (kept 30 days, off by default) | Ownership marks for NG's own changes, and reinstall restore | MIUI and HyperOS private ops NG can't test |
| [LibChecker](https://github.com/LibChecker/LibChecker) | 2.5.5 (2026-11-05) identifies APK Signature Scheme v3.2, R8 build metadata (version, Full or Compatibility mode) and AppFunctions, and snapshots now record DEX and resource sizes. It's the last release for Android 7 | Cheap static signals (already queued) | Snapshot size diffs, a different product |
| [PermissionManagerX](https://github.com/mirfatif/PermissionManagerX) | Permission and AppOps manager with its own ADB daemon | Its #83 (2026-11-12) shows Android 17 refusing a uid 2000 call that names the app's own package | A daemon that names the app's package from shell |
| [Canta](https://github.com/samolego/Canta) and [Hail](https://github.com/aistra0528/Hail) | Canta 4.1.0 (2026-10-29) installs and cleans inside a work profile. Canta #148 "Allow disabling apps" has 17 reactions. Hail #266 asks for one tile to freeze a chosen set | One-tap freeze of a chosen set fits NG's queued freeze widget | Splitting debloat and freeze into two apps |
| [Thor](https://github.com/trinadhthatakula/Thor) | v1.97.1 stable (2026-10-06). The v1.97.4 dev build (2026-11-09) adds navigation customization and optional combined user and system lists | Little new for NG this cycle | Navigation cosmetics |
| [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) | 26.11 preview (2026-11-12) makes uninstall requests respect the target user | Per-user correctness in every uninstall path | Duplicating an installer UI skin |
| [Shizuku](https://github.com/RikkaApps/Shizuku), [Porter](https://github.com/d4rken-org/porter) and [Dhizuku](https://github.com/iamr0s/Dhizuku) | Shizuku is still v13.6.0 (2025-05-25) with Android 17 reports #2180 and #2681 open (the latter updated 2026-11-14). Porter v0.8.2-rc0 (2026-11-09). Dhizuku v2.13.0 (2026-10-30) | Judge a server by whether it answers (already queued) | Naming one fork as the answer |
| [Inure](https://github.com/Hamza417/Inure) and [Blocker](https://github.com/lihenggui/blocker) | Inure build107.3.1 (2026-11-02) adds filters to its Untagged panel. Blocker v2.0.6447 (2026-10-22) | Nothing NG lacks this cycle | |
| [Neo Backup](https://github.com/NeoApplications/Neo-Backup) | 8.3.18 (2026-05-03). #195 (15 reactions) asks for more schedule conditions | NG already has charging and network conditions | Root-only assumptions |
| [UAD-ng](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), [ShizuWall](https://github.com/AhmetCanArslan/ShizuWall), [Thanox](https://github.com/Tornaco/Thanox), [Obtainium](https://github.com/ImranR98/Obtainium), [LocalSend](https://github.com/localsend/localsend) | Latest: UAD-ng v1.3.1 (2026-11-02, a Windows package-list fix), ShizuWall v4.7.1 (2026-11-08), Thanox v9.6 (2026-09-17), Obtainium v1.7.4 (2026-11-01), LocalSend v1.18.2 (2026-08-21) | No new NG gaps since 2026-11-13 | |

## Reported Issues

### Fork tracker (SysAdminDoc/AppManagerNG)

- Open on 2026-11-14: #15, #19, #22, #26, #27, #30, #31, #32 and #33. Every feature request has a build and a reply. What's left is device proof, already on the ROADMAP under "Issue Drain Left Open".
- #22 (comment by antiframe, 2026-11-09), item 8: "several applications, including WhatsApp, failed to update through the normal AppManagerNG installation process", about eight of them, while "installing them through Android's built-in package installer worked successfully". The 2026-11-13 reply says the installer fallback is built but hasn't run between two phones. The root cause is unknown. Local sync sends the base and split APKs of the host's install (`sync/lan/AndroidPackageCatalog.java:172-173`), so host splits that don't fit the receiving phone are one candidate and update ownership is another. New P2.
- #30 is a distribution request. It's left to the owner and isn't part of this plan.
- #31 (community translations through language files, open, updated 2026-11-13): importable language packs ship (`i18n/LanguagePackManager`). The hosted project it would feed still doesn't exist: Weblate's API returned 404 for `appmanagerng` and 200 for upstream's `app-manager` on 2026-11-14. That stays in Roadmap_Blocked.md. Syncing upstream's translations doesn't need it (see Architecture Assessment).

### Upstream tracker (shared code)

- #2071 and #2072 (2026-11-02, closed): a component with `android:enabled="false"` shows its switch on. The maintainer answered that the switch is App Manager's own block state (FAQ). NG has the same design. `details/AppDetailsComponentsFragment.java:743-746` sets the switch from NG's rule and reads "Component allowed" to TalkBack (`strings.xml:2708`), and `:792-797` marks a disabled component with a stroke color only. `AppDetailsViewModel.isComponentDisabled()` (`:1989-2010`) already knows whether the app is frozen, the state was set explicitly, or the manifest default is off. New P3.
- #1524 (opened 2025-02-13, updated 2026-11-03): sort by cache size. `main/MainListOptions.java:77-95` has no cache sort, and `main/ApplicationItem.java:1064` already returns the cache size. New P3.
- #2070 (2026-11-01): root-mode crash on devices without telephony. Doesn't apply: NG's `compat/SubscriptionManagerCompat.java` treats a missing `isub` or `iphonesubinfo` service as no subscriber data.
- #2028 (Disabled and Frozen chips): NG has a Frozen chip (`res/layout/activity_main.xml:103-107`, `FILTER_FROZEN_APPS`). Covered.
- #2043 (archived apps on Android 17): NG lists and labels archived apps (`main/ApplicationItem.java:789-811`, `apk/installer/AppArchiveManager.java`). Covered.
- #2066 and #2064, both P1s in the 2026-11-13 pass, are fixed here (75e985848, 618c7296d).
- #2068 (Android/data automation) stays under consideration. #2065 is a help request.

## Security, Privacy, and Reliability

### Bugs and risks found

- **Gradle wrapper 9.7.0** (`gradle/wrapper/gradle-wrapper.properties`) is inside six Gradle advisories. Fixed in 9.8.1 (2026-10-07): GHSA-j5m7-59rp-24f5 (a repository that fails SSL isn't disabled), GHSA-xwqc-3h47-hg64 (client-daemon deserialization before authentication) and GHSA-mvvg-497x-hmj8 (worker-to-daemon deserialization). Fixed in 9.9.1 (2026-11-10): GHSA-gqrr-4j89-5267 (daemon registry trust), GHSA-pcp8-98xm-c6pr (stale registry port squatting) and GHSA-3cfg-jpcr-gfp3 (critical, version catalog values run as code). NG has no TOML catalog (`gradle/` holds only `verification-metadata.xml` and `wrapper/`), so the critical one doesn't reach this build. The other five do, on the machine that builds releases.
- **AGP 9.3.1** (`versions.gradle:14`) misses 9.3.3's R8 fixes (published 2026-09-17): #554586985 "identical input produces different dex/mapping", #485558707 "dex files that fail verification and crash app on launch", #551348855 (outliner VerifyError) and #522844908 (duplicate mapping), plus 9.3.2's lint crash on JDK 17 (#522845800). The release gate compares two builds byte for byte, so R8 non-determinism shows up as a gate failure that looks like a source problem. No R8 version is pinned separately (`build.gradle:18`).
- **org.json 20251224** is a test-only dependency (`app/build.gradle:360`, `versions.gradle:33`) inside GHSA-ghc9-756g-vrcv (CVE-2026-59171, JSONTokener numeric string DoS, fixed in 20260719). It never ships in the APK. Note on the existing dependency item.

### Android 17 checks this pass

- **Calling package from shell (PermissionManagerX #83):** on Android 17 build CP4A.261105.040.A1, a uid 2000 `IActivityManager.startService()` call that names the app's own package fails in `ActivityManagerService.enforceCallingPackage`. NG's `compat/ActivityManagerCompat.java:106-145` takes its calling package from `self/SelfPermissions.java:386-394`, which returns `com.android.shell` for root and shell uids, so those paths don't hit it. NG's server process does send broadcasts through an NG package context (`ipc/RootServiceServer.java:143-153`, `server/BroadcastSender.java`), and ADB mode hasn't run on API 37 yet. Note on the existing P1 "Validate privileged-mode binding on Android 17".
- **QPR2 self-broadcasts:** from Android 17 QPR2 (Beta 7.1, 2026-10-16), a broadcast whose receivers all live in the sending process is delivered by that process on its main thread. It doesn't raise importance, waits while the process is frozen, and is lost if the process dies. NG's self-sent signals (`utils/BroadcastUtils.java:37-43`, `batchops/BatchOpsService.java:285-300`, `apk/installer/PackageInstallerCompat.java:2075-2122`) are package-scoped, come from a foreground service or a visible screen, and land on main-thread receivers that hand the work to an executor (`types/PackageChangeReceiver.java:160-166`). The server's status broadcasts come from another process and are exempt. A frozen UI only refreshes later. No item needed.
- **Advanced Protection:** "In Android 17, enabling Advanced Protection automatically restricts AccessibilityService access exclusively to verified applications categorized as Accessibility Tools" (The Hacker News, 2026-10-02, quoting Google). NG's service config (`res/xml/accessibility_service_config.xml`) declares no `isAccessibilityTool`, and it shouldn't, because NG isn't an assistive tool. NG already reads the state (`compat/AdvancedProtectionCompat.java`), but Privilege Health only says sideloading is blocked (`strings.xml:2966`), and toast history and the bubble still tell the user to turn the service on (`strings.xml:2159`, `:3419`). New P2. Whether a sideloaded service gets switched off or only can't be turned on needs live validation.

### Missing guardrails

- Toolchain bumps need fresh checksums in `gradle/verification-metadata.xml` (1,059 components, metadata verification on, signatures off), so each bump regenerates it and the diff gets read before commit.

### Recovery and rollback

- A reinstalled app comes back with none of NG's component rules applied, though its `conf/<package>.tsv` is still there to read (`rules/compontents/ComponentsBlocker.java:198-212`; no code found that deletes it on uninstall). `self/PackageEventSync.java` already spots a reinstall by its new first-install time. New P3.

## Architecture Assessment

### Module and boundary improvements

- Build toolchain upgrades have no owner item. Library bumps have one ("Take the dependency updates that keep API 21"), Gradle and AGP don't, and that's how the wrapper drifted past two security releases. The two new P1s fill the gap.
- Translations stopped at the fork. Upstream merged 53 Weblate commits on 2026-05-29 (540c30cac3 is 78 commits past NG's base 3d11bcb), and NG took none of them. Its `values-sv/strings.xml` hasn't changed since the initial commit, and `values-de` only lost the Pithus strings (eea413a50). The comparison (upstream master dc4154f4c against NG, 2026-11-14) counts only keys whose English text is identical in both repos and reads NG's base locales, since 3d716430f folded `es-rES`, `uk-rUA` and `cs-rCZ` into `es`, `uk` and `cs`. Missing here: Swedish 1,085 (NG has 44), Latvian 307 (NG has 125), Indonesian 225, Spanish 159, Ukrainian 100, and 271 more across eight other locales. Another 366 existing translations differ from upstream's current text. CONTRIBUTING.md asks contributors to hold broad catch-up until a hosted project exists, but these strings were already reviewed on upstream's Weblate under the same GPL. New P2.
- Each accessibility-dependent feature words its own "turn on the service" prompt. One shared availability check (service on, blocked by Advanced Protection, held by restricted settings) would serve toast history, the bubble and UI tracker.

### Refactor candidates

- `details/AppDetailsComponentsFragment.java` repeats the indicator chain in four row builders (`:797`, `:912`, `:1076`, `:1148`). The disabled label belongs in one helper used by all four.

### Test and documentation gaps

- No test asserts the component row's content description for a disabled component without an NG rule.
- No test covers sorting by cache size, or rules coming back after a reinstall.
- README "Build from source" names Gradle 9.7 and AGP 9.3.1, so it changes with the toolchain items.
- Counts on 7ff61d2c3: 1,125 main Java files, 652 unit test files, 9 instrumented tests.

## Rejected Ideas

- A new item for PermissionManagerX #83's pattern: NG names `com.android.shell` from shell (`self/SelfPermissions.java:386-394`). It's a note on the Android 17 binding item instead.
- A QPR2 self-broadcast refactor: NG's self-broadcasts don't carry background work (see above).
- Declaring `isAccessibilityTool` to get past Advanced Protection: NG isn't an assistive tool, and claiming to be one misstates what the service does.
- Stopping at Gradle 9.8.1: it leaves the two November registry advisories open.
- LibChecker's DEX and resource size snapshot diffs (2.5.5): a snapshot feature NG doesn't have, for little gain.
- AppOpsNext's MIUI and HyperOS private ops: no device to test them on.
- Thor v1.97.4 navigation customization: cosmetic.
- Upstream #2028, #2043 and Neo Backup #195: already shipped here (see Reported Issues and Product Map).
- Robolectric 4.18 (2026-11-13) and BouncyCastle 1.87: wait until they're two weeks old, as the dependency item already says.
- Carried from 2026-11-13 and earlier (commit b806587f9): upstream's Actions engine and VirusTotal rewrite, PDF and image viewers, a Guava bump past API 21, commons-compress DUMP and Pack200 fixes, the dependency installer, `service.adb.tls.port`, Dhizuku as an ADB replacement, Porter's SDK, Shizuku stealth packages, Android 17 App Lock, sora-editor 0.24.7, cloud backup, a VPN firewall, a plugin runtime, universal debloat labels, a Compose migration, a default-installer toggle and distribution listings (owner policy).

### Under consideration (not on the ROADMAP yet)

- Systemless debloat through a root module (upstream PR #1017). It needs a bootloop recovery story first.
- A one-time LAN page to send an APK to a phone without NG (LocalSend). It needs a threat model.
- Android/data automation rules (upstream #2068), after the Android 17 Android/data check.

## Sources

### OSS competitors and trackers
- https://github.com/MuntashirAkon/AppManager/issues/2071
- https://github.com/MuntashirAkon/AppManager/issues/2072
- https://github.com/MuntashirAkon/AppManager/issues/1524
- https://github.com/MuntashirAkon/AppManager/issues/2028
- https://github.com/MuntashirAkon/AppManager/issues/2043
- https://github.com/MuntashirAkon/AppManager/issues/2070
- https://github.com/MuntashirAkon/AppManager/commit/dc4154f4c
- https://github.com/MuntashirAkon/AppManager/commit/540c30cac3
- https://github.com/MuntashirAkon/AppManager/compare/3d11bcbc399d3a4f995b544e26d86bd80487fd32...540c30cac3
- https://github.com/MuntashirAkon/AppManager/tree/master/app/src/main/res
- https://hosted.weblate.org/api/projects/appmanagerng/
- https://hosted.weblate.org/api/projects/app-manager/
- https://github.com/1zumiii/AppOpsNext/releases/tag/v1.6.7
- https://github.com/LibChecker/LibChecker/releases/tag/2.5.5
- https://github.com/mirfatif/PermissionManagerX/issues/83
- https://github.com/samolego/Canta/releases/tag/v4.1.0
- https://github.com/samolego/Canta/issues/148
- https://github.com/aistra0528/Hail/issues/266
- https://github.com/trinadhthatakula/Thor/releases/tag/v1.97.4-dev-162
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases/tag/26.11.caccb78
- https://github.com/RikkaApps/Shizuku/issues/2180
- https://github.com/RikkaApps/Shizuku/issues/2681
- https://github.com/d4rken-org/porter/releases
- https://github.com/iamr0s/Dhizuku/releases
- https://github.com/Hamza417/Inure/releases/tag/build107.3.1
- https://github.com/lihenggui/blocker/releases
- https://github.com/NeoApplications/Neo-Backup/issues/195
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases/tag/v1.3.1
- https://github.com/AhmetCanArslan/ShizuWall/releases
- https://github.com/Tornaco/Thanox/releases
- https://github.com/ImranR98/Obtainium/releases
- https://github.com/localsend/localsend/releases

### Fork tracker
- https://github.com/SysAdminDoc/AppManagerNG/issues/22
- https://github.com/SysAdminDoc/AppManagerNG/issues/31

### Platform
- https://developer.android.com/about/versions/17/qpr2/release-notes
- https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html

### Build toolchain
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- https://services.gradle.org/versions/all

### Security advisories
- https://github.com/gradle/gradle/security/advisories/GHSA-3cfg-jpcr-gfp3
- https://github.com/gradle/gradle/security/advisories/GHSA-gqrr-4j89-5267
- https://github.com/gradle/gradle/security/advisories/GHSA-pcp8-98xm-c6pr
- https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5
- https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64
- https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8
- https://osv.dev/vulnerability/GHSA-ghc9-756g-vrcv

### Dependency metadata
- https://repo1.maven.org/maven2/com/github/luben/zstd-jni/maven-metadata.xml
- https://repo1.maven.org/maven2/org/json/json/maven-metadata.xml

## Open Questions

- Under Advanced Protection on Android 17, is an already-enabled sideloaded accessibility service switched off, or only blocked from being turned on? The answer decides whether the message belongs on the feature screens, in Privilege Health, or both. It needs an API 37 image or phone with Advanced Protection on, which means a Google account on that device.
- Which Shizuku-compatible server should the Android 17 guidance name? That's still the owner's call (carried from 2026-11-13).
