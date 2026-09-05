<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research: AppManagerNG

Date: 2026-09-05. Replaces all prior research.

Confidence labels: **[Verified]** observed in the checked tree or in an authoritative primary source. **[Likely]** code and external evidence support it, not reproduced on a device. **[Assumption]** a design choice that still needs validation. **[Needs live validation]** requires a device, signing environment, or provider account.

## Executive Summary

[Verified] AppManagerNG v0.6.22 (2026-08-30, versionCode 30) is an offline-first Android package administration suite: 971 Java files, ~225k lines, 464 host test classes, minSdk 21 / targetSdk 36 / compileSdk 37, FLOSS and full flavors, and a fail-closed local release gate that produces byte-identical builds plus an SBOM, a CVE receipt, and a signing receipt. Its release evidence is stronger than upstream's — upstream App Manager's own v4.1.1 reproducible build is currently failing IzzyOnDroid verification (upstream #2035), which is precisely the failure class this fork's two-checkout server-JAR rehash already guards.

The tree has not moved since v0.6.22 shipped, and four user reports have arrived since: fork issues #12, #13, #14 (all 2026-08-30) and #15 (2026-08-31). Three are bugs. One of them is a regression this fork introduced. That is where the next release has to go, and the previous research pass (2026-08-30) predates all four.

Top opportunities, in priority order:

1. **[Verified] `InstallerConfirmIntentGuard` rejects the platform's own confirmation intent.** AOSP builds the uninstall confirmation as `new Intent(Intent.ACTION_UNINSTALL_PACKAGE)` with a `package:` data URI and **no component and no package** ([AOSP `PackageInstallerService.java:861` on android-10.0.0_r47, `:1182` on android-13.0.0_r83](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r83/services/core/java/com/android/server/pm/PackageInstallerService.java)). `InstallerConfirmIntentGuard.sanitize()` returns `null` for any payload with no explicit target, so **every no-root uninstall has been dead since the guard landed** (`7df18f1da`, 2026-07-29, v0.6.7). Install breaks the same way on ROMs that leave the confirm intent implicit. Fork #14 reports exactly this on MIUI 12 / API 29, and notes upstream App Manager works on the same device.
2. **[Verified] The install-confirmation notification is never cancelled on a non-broadcast terminal outcome.** `NotificationUtils.cancelInstallConfirmNotification` is called only from the `STATUS_SUCCESS` and default branches of `PackageInstallerBroadcastReceiver.onReceive`. The null-confirm-intent path fails inside `PackageInstallerActivity.onNewIntent` and the 5-minute `USER_INTERACTION_TIMEOUT_MINUTES` expiry never reaches the receiver at all, so the notification survives both. That is the "100%, stuck, still scrolling after 5 minutes" in fork #14.
3. **[Verified] The support bundle destroys the stack traces it exists to collect.** `misc/SupportInfoBundle.java:241` replaces every dotted identifier with `<package>`. In fork #12 that erased the exception classes, every framework frame, and every app frame; the only surviving frames were the ones the regex could not match (`nf0.<init>`, `j$.…`). The same function runs in the uncaught-exception handler (`misc/AMExceptionHandler.java:82`), so the crash report `misc/LocalCrashSink.java` writes to disk is stripped as well, and in `apk/installer/InstallTranscript.java:129,134,139`, which mangles installer failure explanations — the exact text a fork #14 reporter would otherwise have pasted. Release builds are minified (`app/build.gradle:71`) and **no `mapping.txt` is published** with any release. Every crash report from a released build is currently unresolvable twice over.
4. **[Verified] Code Editor crashes on inflation on API 29.** Fork #12: `Binary XML file line #51 in layout/fragment_code_editor: Error inflating class` → NPE. Line 41–51 of `app/src/main/res/layout/fragment_code_editor.xml` is the `CodeEditorWidget`, whose constructor only calls `super(context, attrs)` into sora-editor. The exact frame cannot be resolved until (3) is fixed, which is the strongest argument for fixing (3) first.
5. **[Verified] Two App Info subtitles clip without an ellipsis.** `pager_app_info.xml:160` (`tracker_cta_subtitle`) and `:236` (`perms_cta_subtitle`) set `android:maxLines="1"` with no `android:ellipsize`; every other capped text view in that file has one. The reporter of fork #13 runs a Russian locale, where those strings are materially longer than the English ones the 2026-08-22 visual pass was checked against.
6. **[Verified] Nothing tells the user the Android version is untested.** There is no maximum-SDK guard or unverified-platform banner anywhere in `app/src/main/java/`, and no logging when a hidden-API accessor silently returns nothing. This fork already lost the whole app list once to an Android 17 return-type change (`74fc7ae95`). Upstream #2033, filed 2026-09-04, asks for precisely this and describes stepping line by line through a debugger to find it.
7. **[Verified] Upstream v4.1.1 (2026-09-04) added per-app internet revocation via eBPF rules in root and ADB mode.** This fork has only `NetworkPolicyManagerCompat` and `NetPolicyRule`, which restrict metered background data. eBPF/netd rules sit inside the package-management boundary in a way an always-on VPN does not, and this fork's `BootReceiver` plus routine ops can address the reboot-persistence limitation upstream documents as a known gap.

## Product Map

- **Core workflows:** App List and Finder inventory; App Details with permission, AppOps, component, signer, and native-library evidence; batch, profile, freeze, archive, and debloat operations; APK inspection and installer preflight; backup, restore, and format conversion; file manager, code editor, logcat viewer, running processes, terminal, wireless ADB. Evidence: `README.md`, `app/src/main/java/io/github/muntashirakon/AppManager/`.
- **User personas:** privacy-focused power users, Android troubleshooters, debloat and device-maintenance users, developers inspecting manifests and logs, and operators working through root, Shizuku, ADB, or no-root fallbacks. Evidence: `settings/Ops.java:85-90`, `docs/raw/en/`.
- **Platforms and distribution:** Java plus Android Views, minSdk 21, targetSdk 36, compileSdk 37, `floss` (default) and `full` flavors, GitHub Releases, Obtainium, F-Droid-family metadata. Evidence: `app/build.gradle`, `versions.gradle`, `docs/distribution/`.
- **Key integrations and data flows:** PackageManager and AppOps binders, root and local privileged servers, Shizuku, SAF backup destinations, APK signature verification, VirusTotal in the opt-in full flavor, Exodus tracker data, Tasker-compatible automation, backup importers for OAndBackup, Neo Backup, Swift Backup, and Titanium. Evidence: `app/src/main/AndroidManifest.xml`, `settings/NetworkTransparencyLedger.java`, `backup/convert/`.
- **Privilege reality:** `Ops.java` defines five modes — `auto`, `root`, `shizuku`, `adb_tcp`, `adb_wifi`, `no-root`. There is **no** `MODE_DHIZUKU`; `dhizuku/DhizukuBridge.java` is a 206-line detection probe and `InstallerPrivilegeCascade.java:182` adds Dhizuku only as `Step.info(...)` with a null mode.

## Competitive Landscape

- **Upstream App Manager (v4.1.0 2026-06-29, v4.1.1 2026-09-04).** Two releases of divergence since this fork's `3d11bcb` baseline. Learn from: eBPF INTERNET revocation, the MIUI/HyperOS installer race fix (`f3db2698a`), and the installation-timeout reduction (`4c4e512bb`). Avoid: upstream's v4.1.1 reproducible build currently does not match IzzySoft's (#2035, `assets/am.jar` differs by 637 bytes) — this fork's two-checkout server-JAR rehash already covers that and should not be traded away for build speed. Also confirmed already ported or already present: Android 17 app-list enumeration (#2032, `74fc7ae95`) and ADB data backup (upstream v4.1.0; this fork already has `BackupFlags.BACKUP_ADB_DATA` and `backup/adb/`).
- **Inure (build107.2.2, 2026-09-03).** Highest release cadence in the category and the densest package/signer/library presentation. Learn from its signer and state treatment. Avoid duplicating its breadth at the cost of this fork's offline-first trust model.
- **InstallerX Revived (26.08, 2026-08-31).** Granular installer profiles make advanced session controls legible. Learn from the profile model for the pending signer-policy item. Avoid network APK streaming and hidden defaults.
- **Hail (v1.11.0, 2026-08-26).** Makes freeze state and recovery immediately recognizable. This fork already has stronger history, snapshots, tags, and automation, so the transferable lesson is plain state text rather than another freeze surface.
- **PermissionManagerX.** Its Pro tier sells exactly the Permission Watcher and Scheduled Check that fork #15 requests, which is the clearest demand signal available for that feature. Learn from the desired-state-plus-drift-report model. Avoid promising continuous watching: WorkManager's 15-minute floor and the absence of any always-on process (`profiles/trigger/RoutineScheduler.java:351`) put a hard ceiling on it.
- **Canta (v3.2.2) and UAD-NG.** Approachable debloat decisions, but Canta discussion #279 shows the cost of a wrong recommendation. This fork's `SystemAppRescueArtifacts` pre-operation snapshots are the stronger model; keep them and keep avoiding one-tap recommendation claims.
- **Neo Backup (8.3.18) and Android DataBackup (2.0.12).** Both stalled in 2026 and both have open provider-semantics failures. Their evidence still justifies the pending destination conformance probe, not a cloud account layer.
- **LibChecker (2.5.4) and AppVerifier (v13).** Both make library and signer evidence legible. Support user-owned signer pins; do not claim a library CVE from class-name matching.
- **SD Maid SE (v2.0.4-rc0, 2026-08-25).** Ships release candidates continuously and is explicit about privilege loss and recoverable cleanup. Learn from its capability and result treatment; do not expand into a general storage cleaner.
- **Obtainium (v1.6.14, 2026-08-29).** Ships roughly weekly and is this fork's primary update channel for direct-download users. Keep the release-feed contract in `docs/distribution/obtainium-config.json` stable; do not build a competing release scraper.
- **Dhizuku (v2.12.0, 2026-06-24) and Dhizuku-API (2.6.0).** The API AAR is MIT and declares `MIN_SDK = 26` ([`build.gradle`](https://raw.githubusercontent.com/iamr0s/Dhizuku-API/main/build.gradle)). Dhizuku itself already requires Android 8.0, so the minSdk gap is a manifest-merger question, not the hard "API-21 floor conflict" the app's own string claims.

## Reported Issues

This repository's tracker (SysAdminDoc/AppManagerNG), plus upstream MuntashirAkon/AppManager where the fork shares the code path.

Open bugs worth fixing:

- **[Verified] Fork #14 — installer posts a stuck notification and never shows the system prompt; uninstall is also broken.** Two independent root causes, both traced above: `apk/installer/InstallerConfirmIntentGuard.java:38-58` rejecting implicit platform payloads, and the notification cancellation gap in `apk/installer/PackageInstallerBroadcastReceiver.java:88-113` versus the failure path at `apk/installer/PackageInstallerActivity.java:580-595`. The reporter's own control — upstream App Manager installs fine on the same device — isolates it to this fork's guard. The uninstall half is Verified against AOSP for all versions checked; the install half is Likely and ROM-specific (MIUI 12.0.16, below the `isActualMiuiVersionAtLeast("12.5", "20.2.0")` retry threshold at `PackageInstallerCompat.java:1281`).
- **[Verified] Fork #12 — Code Editor crashes on inflation.** `fragment_code_editor.xml:41-51`, `editor/CodeEditorWidget.java:28-30`, sora-editor pinned in `versions.gradle`. Android 10 / API 29, armeabi-v7a-only device. The precise frame is **[Needs live validation]** until the diagnostics items below land.
- **[Verified] Fork #13 — text clipped in App Info.** `app/src/main/res/layout/pager_app_info.xml:160` and `:236`. Russian locale, Android 10.
- **[Verified] Upstream #2033 — no visibility into hidden-API incompatibility.** No maximum-SDK guard or silent-fallback logging exists in this fork either; `misc/ProfilingTriggerHelper.java:23` shows the reflective-resolution pattern the warning should key off.
- **[Verified] Carried forward and still open from the 2026-08-30 pass:** upstream #2023 stale cache size after clear, #2022 empty regex replacement, #2006 state legibility by color alone, and #2011's real lesson — thumbnail decoders run in the permission-bearing process. All four already have ROADMAP.md rows; none are re-proposed here.

Feature requests with real demand:

- **[Verified] Fork #15 — Permission Watcher and Schedule Checker.** Backed by PermissionManagerX Pro shipping both as paid features. Survey of the current tree: `permission/monitor/` already has 21 files, an atomic `PermissionSnapshotStore` (schema 2), a `PermissionChangeMonitor` that diffs on `ACTION_PACKAGE_REPLACED`, an `AppChangeFeedStore` with export/import, and four WorkManager schedulers to copy. `profiles/struct/AppsBaseProfile.java:224-243` can already revoke permissions on a `TYPE_TIME_OF_DAY` trigger. The genuine gaps are narrow: snapshots record manifest-declared permissions only and never call `checkPermission`, so no grant state is ever stored; there is no periodic rescan; and there is no drift report.
- **[Verified] Fork discussion #5 — full Dhizuku support**, requested because ColorOS 16 restricted ADB permissions. Reference implementation named by the requester: `trinadhthatakula/Thor`. Executor parity is already parked in `Roadmap_Blocked.md` as device-gated, correctly. What is not parked, and is host-fixable, is the copy: `onboarding_confidence_mode_shizuku_ready` says "Shizuku/Dhizuku is ready" and `onboarding_mode_dhizuku_status_ready` says "Dhizuku is active", while `privilege_health_dhizuku_dialog_message` correctly states that DPM operations stay disabled. Two surfaces contradict each other.
- **[Verified] Upstream v4.1.1 eBPF INTERNET revocation.** Not present here; `NetPolicyRule` covers metered background only.

Reports judged stale, already handled, or not worth acting on:

- **Fork #6, #8, #9, #10, #11 — closed and fixed in the v0.6.22 tree.** Fork PR #3 is functionally superseded by `74fc7ae95` and the settings key-parity test `13f4d3a32`.
- **Upstream #2034, one-tap backup and restore — closed upstream, and this fork already ships `oneclickops/` plus profiles.** No row.
- **Upstream #2031, launch the assistant without ADB — closed upstream.** Already parked here as "Assistant-launched privileged services and broadcasts without root" in `Roadmap_Blocked.md`.
- **Upstream #2032, empty app list on Android 17 — closed; already ported** (`74fc7ae95`).
- **Upstream #2035, reproducible build mismatch — upstream's problem, not this fork's.** The `server-jars.txt` release asset and the two-checkout rehash described in `README.md` already cover the `assets/am.jar` divergence class. Worth citing when the parked IzzyOnDroid submission is picked up.
- **Upstream #2013, grant all runtime permissions at install; #2012, Secure Folder with Shizuku; #2018, #2004, #2000, #1994, #1986** — unchanged from the 2026-08-30 assessment.

## Security, Privacy, and Reliability

- **Installer confirmation boundary.** `InstallerConfirmIntentGuard`'s policy is right — the payload arrives through a mutable `PendingIntent` and forwarding it verbatim is an intent-redirection primitive. The defect is that "implicit means reject" also rejects the legitimate platform payload. The correct shape is to resolve an implicit payload against the package manager, require that it resolves to a system installer component, and bind it explicitly to that component — so the target is chosen by this app from a system resolution rather than by the caller. `InstallerConfirmIntentGuardTest.java` has nine cases and **no positive control built from a real platform payload**; every "forwarded" case hand-builds `setPackage(INSTALLER)`. That is why the regression shipped.
- **Diagnostics as an attack on maintainability.** `SupportInfoBundle.scrubForPublicIssue` (`misc/SupportInfoBundle.java:234-244`) applies eight regexes in sequence. Seven are proportionate. The eighth, at `:241`, is not: class names in a stack trace are the app's and the platform's own code, not user data. The adjacent `\b\d{5,7}\b → <id>` rule at `:242` additionally corrupts five-to-seven-digit line numbers. It is not one screen's problem — three callers depend on it: the share-support-bundle action from `main/SplashActivity.java:399-417`, the uncaught-exception handler at `misc/AMExceptionHandler.java:82`, and the installer transcript at `apk/installer/InstallTranscript.java:129-139`. Fixing the function fixes all three.
- **Release symbolication.** `minifyEnabled = true` at `app/build.gradle:71`; the v0.6.22 release assets are two APKs, two `.sha256` files, a CycloneDX SBOM, the dependency-check HTML and SARIF, a CVE receipt, and `server-jars.txt`. No mapping file. Publishing `mapping.txt` alongside the receipt costs nothing in reproducibility and is what makes every future crash report actionable.
- **Build expiry is an offline kill switch on a wall-clock read.** `self/life/BuildExpiryChecker.java:88-105` compares `System.currentTimeMillis()` against `BuildConfig.BUILD_TIME_MILLIS`; the source comment concedes it should use SNTP. `getBuildExpiredDialog` at `:64-86` is `setCancelable(false)` and only adds a continue button when `getBuildType() == BUILD_TYPE_STABLE`, so an expired alpha, beta, or rc build offers only "Update" (which opens a browser then calls `finishAndRemoveTask()`) and "Uninstall". It is enforced at three entry points — `BaseActivity.java:86`, `SplashActivity.java:117`, `KeyStoreActivity.java:33` — so a user with a skewed clock cannot reach their own backups. `getUpdateUri()` also points DEBUG builds at `/actions`, and this repository has no `.github/workflows` by policy.
- **Dependency advisories, checked for the window since 2026-06-01.** No advisory was published in that window for sora-editor, jadx, BouncyCastle, apksig, libsu, Shizuku, ARSCLib, commons-compress, zip4j, XZ, zstd-jni, Room, or androidx.sqlite. The real SQLite FTS5 findings CVE-2026-11822 and CVE-2026-11824 are fixed in SQLite 3.53.2 and are an OS-patch matter for platform SQLite, not a dependency bump. Six further August 2026 SQLite CVEs (CVE-2026-51296, -51297, -51300, -51302, -51303, -51304) are documented by SQLite upstream and JFrog as fabricated; the CVE gate's suppression review should record that disposition rather than re-litigating it each release. The one pin still inside an affected range is `jadx-core` 1.4.7 against GHSA-hvp5-5x4f-33fq (path traversal on resource decoding, fixed 1.5.0, CVSS 3.3) — already covered by the existing ROADMAP row, and `DexUtils.java` setting `skipResources(true)` is what keeps it unreachable, which is exactly why that row's contract test matters.
- **Carried forward unchanged:** isolated thumbnail decoding, authoritative readback after cache and overlay mutations, user-owned signer policy, dependency-CVE reachability sectioning, the stale Pithus clause in `PRIVACY_POLICY.rst`, and the backup-destination conformance probe. All have ROADMAP.md rows.

## Architecture Assessment

- `apk/installer/` splits confirmation handling across three files — `PackageInstallerBroadcastReceiver`, `PackageInstallerActivity`, and `InstallerConfirmIntentGuard` — and sanitizes the same intent twice, in the receiver and again in `onNewIntent`. Terminal-state handling is spread across the same three, which is how a notification ends up with no owner. A single `InstallSessionOutcome` sink that every terminal path reports through, receiver and activity and timeout alike, is a small change that closes the whole class.
- `misc/SupportInfoBundle.java` is the right seam for the scrubbing fix. Keep it a pure function so its behavior stays host-testable; the fix is an allowlist of frame-shaped identifiers (`android.*`, `androidx.*`, `java.*`, `javax.*`, `dalvik.*`, `libcore.*`, `kotlin.*`, `j$.*`, `com.google.android.material.*`, and this application id) applied before the general rule, not a weaker general rule.
- `details/info/AppInfoFragment.java` is 4,372 lines and `details/AppDetailsViewModel.java` is 2,639. Unchanged assessment: extract an operation-and-result coordinator only where mutations need serialized execution plus authoritative reload. Do not rewrite.
- `permission/monitor/` is already the subsystem fork #15 needs. `PermissionSnapshotStore` is at `SCHEMA_VERSION = 2` and discards version-mismatched snapshots on load, so a schema-3 grant-state extension migrates cleanly with no Room work while the AppsDb migration ladder stays device-gated.
- `dhizuku/DhizukuBridge.java` is detection only and `MAX_DECLARED_SUPPORTED_SDK = 36` is a hardcoded claim. With Dhizuku-API's own `MIN_SDK = 26` now known, the executor work is a bounded `@RequiresApi(26)` module plus manifest-merger handling, not a floor conflict.
- **Testing.** 464 host test classes against 6 instrumentation classes. The host suite is the fork's strongest asset and it did not catch fork #14 because the guard test asserts a policy rather than the platform's actual payloads. It did not catch fork #13 because no test measures a text view against its longest translated string. Both gaps are cheap to close and both are in the roadmap below.
- **i18n.** Per-app locale handling and API 21 language fallback shipped; the hosted translation intake stays service-gated in `Roadmap_Blocked.md`. What is new is that the 2026-08-22 visual density pass was verified in English on one API 35 emulator, and the first two long-locale reports arrived eight days later. The clipping gate below is the i18n row.
- **Consciously not addressed here.** Multi-user, work-profile, and private-space matrices; the AppsDb migration ladder; screenshot regression testing; the fork-owned translation pipeline; IzzyOnDroid submission — all already sit in `Roadmap_Blocked.md` with real blockers and are not duplicated. A third-party plugin runtime remains rejected.

## Rejected Ideas

- **Port upstream v4.1.0's ADB data backup.** Already present: `BackupFlags.BACKUP_ADB_DATA` and `backup/adb/{AndroidBackupCreator,AndroidBackupExtractor,AndroidBackupHeader}.java`. Source: upstream v4.1.0 release notes.
- **Add a one-tap "back up and restore everything" button.** Upstream #2034; `oneclickops/` and profiles already cover it, and a single undifferentiated button over a data-loss path is the wrong direction for this product.
- **Build an always-on VPN firewall or tracker timeline.** Unchanged. Note this does *not* extend to eBPF/netd rules, which are privileged package state rather than traffic interception.
- **Maintain a central signer database, add cloud accounts or proprietary backup SDKs, add an Obtainium-style release scraper, report vulnerable libraries from class-name matches, add unreviewed one-tap debloat recommendations, migrate to Compose, raise minSdk, add a plugin runtime, migrate profile IDs to UUIDs, add a privileged sensitive-access timeline.** All unchanged from 2026-08-30 with the same reasoning; see that pass's sources.
- **Chase the six August 2026 SQLite CVEs.** SQLite upstream and JFrog both document them as fabricated. Record the disposition once in `config/owasp-suppressions.xml`; do not investigate per release.
- **Drive permission toggles through the accessibility service** as fork #15 suggests as a fallback. `accessibility/AccessibilityMultiplexer.java:82-106` automates only fixed Settings and installer screens; per-permission toggles live behind OEM-variable nested screens and would be unreliable in exactly the cases users need them.
- **Set a hard maximum supported SDK** as upstream #2033's first suggestion proposes. Refusing to run on a new Android version is worse than this fork's demonstrated ability to adapt (`74fc7ae95`); warn and log instead.

## Sources

### Project and trackers

- https://github.com/SysAdminDoc/AppManagerNG/issues/12
- https://github.com/SysAdminDoc/AppManagerNG/issues/13
- https://github.com/SysAdminDoc/AppManagerNG/issues/14
- https://github.com/SysAdminDoc/AppManagerNG/issues/15
- https://github.com/SysAdminDoc/AppManagerNG/discussions/5
- https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.22
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.1
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.0
- https://github.com/MuntashirAkon/AppManager/issues/2033
- https://github.com/MuntashirAkon/AppManager/issues/2035
- https://github.com/MuntashirAkon/AppManager/issues/2031

### Platform primary sources

- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r47/services/core/java/com/android/server/pm/PackageInstallerService.java
- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r83/services/core/java/com/android/server/pm/PackageInstallerService.java
- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r67/services/core/java/com/android/server/pm/PackageInstallerSession.java
- https://developer.android.com/reference/android/content/pm/PackageInstaller
- https://developer.android.com/topic/performance/vitals/crash
- https://developer.android.com/guide/topics/resources/providing-resources
- https://developers.google.com/android/play-protect/developer-verification

### Competitors and adjacent products

- https://github.com/Hamza417/Inure/releases
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases
- https://github.com/aistra0528/Hail/releases
- https://github.com/d4rken-org/sdmaid-se/releases
- https://github.com/ImranR98/Obtainium/releases
- https://github.com/LibChecker/LibChecker/releases
- https://github.com/NeoApplications/Neo-Backup/releases
- https://github.com/XayahSuSuSu/Android-DataBackup/releases
- https://github.com/samolego/Canta/releases
- https://mirfatif.github.io/PermissionManagerX/help/permission-watcher/
- https://mirfatif.github.io/PermissionManagerX/help/scheduled-check/
- https://github.com/trinadhthatakula/Thor
- https://github.com/iamr0s/Dhizuku/releases
- https://raw.githubusercontent.com/iamr0s/Dhizuku-API/main/build.gradle
- https://central.sonatype.com/artifact/io.github.iamr0s/Dhizuku-API

### Advisories

- https://sqlite.org/cves.html
- https://research.jfrog.com/post/sqlite-critical-cves-or-llm-slops/
- https://github.com/advisories/GHSA-hvp5-5x4f-33fq
- https://github.com/advisories/GHSA-8cx9-6hv6-67qj
- https://source.android.com/docs/security/bulletin/2026/2026-06-01
- https://source.android.com/docs/security/bulletin/2026/2026-07-01

## Open Questions

- **Which Android and OEM builds return an implicit install confirmation intent?** AOSP 10 and 14 both call `intent.setPackage(mPm.getPackageInstallerPackageName())`, so stock installs pass the guard; the MIUI 12 report in fork #14 shows at least one ROM where they do not. The uninstall half needs no answer — AOSP is implicit on every version checked — so the fix can proceed either way, but the install-side telemetry in the roadmap item exists to settle this rather than guess.
- **Does upstream's eBPF INTERNET revocation survive on non-GKI and pre-Android 12 kernels?** Upstream's v4.1.1 notes state the reboot limitation but not a kernel floor. This decides whether the ported feature is a general capability or a detected-and-degraded one, and it cannot be answered from source alone.
