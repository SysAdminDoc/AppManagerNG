<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research: AppManagerNG

Date: 2026-08-30. Replaces all prior research.

Confidence labels: **[Verified]** means observed in the checked tree or an authoritative source. **[Likely]** means the code and external evidence support the conclusion, but the behavior was not reproduced on a device. **[Assumption]** marks an implementation choice that still needs design validation. **[Needs live validation]** requires a device, signing environment, or provider account.

## Executive Summary

[Verified] AppManagerNG v0.6.23 is an offline-first Android package administration suite for privacy-conscious power users. Its strongest current shape is the combination of package inspection, privileged actions, installer preflight, backup conversion, local diagnostics, an API 21 floor, a FLOSS flavor without optional remote-network features, and unusually strict release evidence. The UID-wide AppOps guard, private broadcast boundary, and Log Viewer lifecycle work identified in the previous research are now implemented and covered by host plus isolated-emulator tests. Its next gains should continue closing narrow trust boundaries rather than adding another broad tool surface.

Top opportunities, in priority order:

1. **[Verified] Move file thumbnail decoding into an isolated process.** `FmIconFetcher` sends untrusted image, PDF, font, eBook, and archive content through platform or library decoders in the app process. Android isolated services have no permissions of their own, which directly addresses the risk called out by the upstream maintainer in issue #2011, especially on API 21 through 23.
2. **[Verified] Make App Details mutations re-read authoritative state.** The privileged clear-cache branch never refreshes `sizeInfo`, matching upstream #2023. Overlay toggles notify the adapter without replacing the cached `OverlayInfoHidden`, so a successful mutation can rebind the old state.
3. **[Verified] Add user-owned signer policies before installer commit.** The app already parses signing lineages and keeps an atomic per-package signing snapshot, but it has no strict local pin that can reject an unrecognized signer before installation. InstallerX, AppVerifier, Inure, and Obtainium issue #2922 show sustained demand for this boundary.
4. **[Verified] Make dependency-CVE results describe shipped reachability.** The 2026-08-22 SARIF contains 962 result rows across 79 artifacts, mixing release runtime, transformed wrappers, host JDBC, test, and build-tool dependencies. `scripts/run_dependency_cve_gate.py` aggregates all configurations, so reviewers cannot tell a packaged risk from a CPE-name collision without manual archaeology.
5. **[Verified] Repair the privacy policy's network contract.** `PRIVACY_POLICY.rst` still says full builds can contact Pithus even though v0.6.13 removed that integration and `NetworkTransparencyLedger` no longer lists it.
6. **[Verified] Probe backup destinations before trusting their semantics.** Neo Backup and Android DataBackup reports show that SAF, CIFS, and SMB providers can pass basic selection yet fail parallel writes, rename, retention, or restore. AppManagerNG has capacity checks and transactional publish, but no destination conformance test or stored sequential-mode fallback.
7. **[Verified] Finish several contained correctness gaps.** Main-list state still relies on color and terse symbols, empty regex replacement is rejected, Swift APK plus OBB conversion has a disabled regression test, and API 30 proxy attribution is left behind a TODO.

[Verified] The v0.6.23 release tree packages the accumulated fixes, the August 2026 icon set, and Finder in stable builds. Its local release gate binds the signed FLOSS and full artifacts to the exact tag before publication.

## Product Map

- **Core workflows:** App List and Finder inventory; App Details and permission/AppOps inspection; batch, profile, freeze, archive, and debloat actions; APK inspection and installation; backup, restore, conversion, file management, logcat, running-process, terminal, and wireless-ADB tools. Evidence: `README.md`, `app/src/main/java/io/github/muntashirakon/AppManager/`, and `app/src/main/res/layout/`.
- **User personas:** privacy-focused power users, Android troubleshooters, debloat and device-maintenance users, developers inspecting manifests or logs, and operators who need root, Shizuku, Dhizuku, ADB, or no-root fallbacks. Evidence: `README.md`, `docs/raw/en/`, and `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java`.
- **Platforms and distribution:** Java and Android Views, minSdk 21, targetSdk 36, compileSdk 37, FLOSS and full product flavors, GitHub Releases, F-Droid-family metadata, and Obtainium-compatible release feeds. Evidence: `app/build.gradle`, `versions.gradle`, `docs/distribution/`, and the GitHub releases page.
- **Key integrations and data flows:** Android PackageManager and AppOps binders, root and local privileged servers, Shizuku and Dhizuku, SAF backup destinations, APK signature verification, VirusTotal in the opt-in full flavor, Exodus tracker data, Tasker-compatible automation, and multiple backup import formats. Evidence: `settings.gradle`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `NetworkTransparencyLedger.java`, and `backup/convert/`.

## Competitive Landscape

- **Upstream App Manager.** It remains the broadest direct reference and its tracker exposes current Android/OEM failures, including shared-UID AppOps and stale cache data. AppManagerNG should keep harvesting reproducible edge cases, but should not assume an upstream issue is present until the fork's current path is traced.
- **Inure.** It presents dense package, signer, permission, library, and installer evidence well. AppManagerNG should learn from its visible signer and state treatment, while avoiding feature duplication that would weaken the existing offline-first trust model.
- **InstallerX Revived and Install With Options.** Their granular installer profiles make advanced package-session controls understandable. AppManagerNG should add a local signer policy to its existing preflight, but should avoid network APK streaming and hidden defaults.
- **Hail.** It makes freeze state and recovery easy to recognize. AppManagerNG already has stronger history, snapshots, tags, and automation, so the useful lesson is explicit state text rather than another dedicated freeze surface.
- **Blocker and PermissionManagerX.** They keep component, permission, and AppOps state close to the action. AppManagerNG should add proxy provenance and UID blast-radius review, while preserving its own readback and rollback requirements.
- **Canta and UAD-NG.** They make debloat decisions approachable, but Canta discussion #279 shows how a recommended removal can strand the user outside the app. AppManagerNG already writes pre-operation snapshots and ADB rescue commands through `SystemAppRescueArtifacts`; it should keep that stronger recovery model and avoid one-tap recommendation claims.
- **Neo Backup and Android DataBackup.** Their network-provider failures show that selecting a SAF or SMB destination does not prove safe close, reopen, rename, or parallel-write behavior. AppManagerNG should add a non-destructive conformance probe, not a cloud account layer.
- **LibChecker and AppVerifier.** They make library and signer evidence legible. AppManagerNG should support user-owned signer pins, but should not claim a library CVE from class-name matching because version inference remains unreliable after shrinking and obfuscation.
- **SD Maid SE.** Its 2026 releases emphasize privilege loss, saved-result clarity, large-text behavior, and recoverable cleanup. AppManagerNG should borrow its explicit capability and result treatment, not expand into a general storage-cleaner product.
- **TrackerControl.** Its network timeline and work-profile fixes are useful adjacent evidence. An always-on VPN would conflict with AppManagerNG's package-management boundary and introduce routing, battery, and attribution obligations.
- **AppDash and Swift Backup.** Their paid value is monitoring, cloud destinations, and convenience around backup metadata. AppManagerNG should retain local and SAF portability rather than adding accounts or proprietary provider SDKs.

## Reported Issues

Actionable bugs:

- **[Fixed in v0.6.22] Upstream #2030, shared system UID AppOps collateral damage.** The central interlock now resolves the full UID package set at the binder boundary, requires a complete reviewed plan, and fails closed for unresolved, stale, shared, or system UIDs.
- **[Fixed in v0.6.22] Fork #10, crash after leaving live log view on API 29 in no-root mode.** View-owned sessions now stop at teardown, replacement readers are installed atomically, and host plus isolated-emulator tests cover restart, rotation, and final destruction.
- **[Verified] Upstream #2023, stale cache size after clear.** `details/info/AppInfoFragment.java:3078` refreshes after the accessibility fallback but not after a successful privileged `deleteApplicationCacheFilesAsUser` call. Swipe refresh keeps reading the same cached `sizeInfo` until the screen is recreated.
- **[Verified] Upstream #2022, incomplete regex replacement.** `editor/CodeEditorFragment.java:429` and the replace-all branch reject an empty replacement through `TextUtils.isEmpty`. Capture groups, multiline anchors, zero-width matches, and invalid-pattern behavior have no app-owned contract test.
- **[Verified] Upstream #2006, state recognition depends on color and symbols.** `main/ApplicationItem.java:323` encodes suspension as `°`; `main/MainRecyclerAdapter.java:564` announces installed or uninstalled only; `res/layout/item_main_v2.xml` does not show a plain Disabled, Frozen, or Suspended label.
- **[Verified] Upstream #2011, full PDF viewer request.** The requested viewer is not recommended. The maintainer's security analysis is relevant to the thumbnails already generated by `fm/icons/FmIcons.java`, which run platform decoders in the permission-bearing app process.

Feature requests and weak reports:

- **[Verified] Upstream #2013, grant all runtime permissions during install.** One request and a security-sensitive default do not justify a roadmap row. Android exposes `SessionParams#setPermissionState`, but the caller needs `INSTALL_GRANT_RUNTIME_PERMISSIONS`, and a broad grant conflicts with the installer's disclosure-first design.
- **[Verified] Upstream #2012, Samsung Secure Folder with Shizuku.** This strengthens the existing multi-user/work-profile/private-space device matrix in `Roadmap_Blocked.md`. It does not need a duplicate active item.
- **[Verified] Upstream #2028 frozen filters, #2027 per-app locale, #2017 Android 17 local-network recovery, #1958 AES-GCM handling, #1955 export naming, and #1948 Android 17 enumeration are already implemented or regression-tested in AppManagerNG.** Evidence: `CHANGELOG.md`, `main/MainListOptions.java`, `compat/AppLocaleManagerCompat.java`, and `app/src/test/java/io/github/muntashirakon/AppManager/compat/android17/Android17BehaviorContractTest.java`.
- **[Verified] Fork #6, #8, #9, #10, and #11 are fixed in the v0.6.22 tree.** Evidence: `CHANGELOG.md`, `settings/PrivacyPreferences.java`, `main/MainActivity.java`, `logcat/LogViewerViewModel.java`, and `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`. Fork PR #3 conflicts with and is functionally superseded by the current Android 17 and privacy fixes.
- **[Needs live validation] Upstream #2018, #2004, #2000, #1994, and #1986 lack enough fork-specific reproduction evidence.** Credible parts already map to blocked device, backup, or profile verification work.

## Security, Privacy, and Reliability

- **UID blast radius:** `AppOpsManagerCompat#setMode` now checks every UID-mode mutation against a complete package and operation plan. Unresolved, stale, shared, and system UIDs fail closed before the binder call.
- **Untrusted parser boundary:** `FmIconFetcher.java` and `FmIcons.java` decode attacker-controlled files in the main app process. A non-exported `android:isolatedProcess="true"` service can receive only bounded file descriptors and return a bitmap or classified failure. Decoder death must degrade to a generic icon.
- **Internal broadcast boundary:** system actions use exported registrations while app-private actions use a separate non-exported registration protected by a signature permission. Package arrays are bounded and validated before worker dispatch.
- **Installer provenance:** `SigningCertSnapshotStore.java` already provides an atomic JSON store and `SignerInfo.java` exposes current certificates plus lineage. Extend that boundary with user-owned trusted fingerprints and strict per-package policy before `PackageInstaller` session commit.
- **Dependency evidence:** `scripts/run_dependency_cve_gate.py` scans an aggregate graph at CVSS 9.0. The current SARIF maps SQLite CVEs to AndroidX wrappers, transformed copies, and host `sqlite-jdbc` artifacts. Release-runtime reachability and build/test exposure need separate reports and failure rules.
- **Privacy contract:** `PRIVACY_POLICY.rst:21`, `:53`, `:70`, and `:121` still name Pithus. `CHANGELOG.md` records its removal, while `NetworkTransparencyLedger.java` and current code no longer expose it. A test should keep policy endpoints aligned with the compiled network ledger.
- **Authoritative mutation state:** Cache clear and overlay toggle paths report success before the model reflects system state. Success must trigger a fresh read; failure must keep the last confirmed value. Controls should not accept duplicate work while a mutation is running.
- **Recovery:** Existing operation history, rollback, backup transactional publish, and `SystemAppRescueArtifacts` are strong. The missing recovery check is storage-provider capability before a long backup, especially rename, reopen, checksum, delete, and bounded parallel writes.

## Architecture Assessment

- `details/info/AppInfoFragment.java` is 4,372 lines and `details/AppDetailsViewModel.java` is 2,567 lines. Do not perform a wholesale rewrite. Extract an operation/result coordinator only where cache, overlay, and future mutations need serialized execution plus authoritative reload.
- `PackageChangeReceiver` now separates trust domains, validates payloads at the boundary, and feeds one bounded lifecycle-owned executor. A separately packaged instrumentation fixture verifies external rejection.
- `FmIconFetcher` is an orchestration point for many decoder types but has no process boundary. Keep format policy, budgets, and fallback behavior outside the decoder service so the isolated implementation stays small.
- `SigningCertSnapshotStore` is the right persistence seam for installer pins. A schema extension avoids introducing a new Room migration while the complete AppsDb migration ladder remains device-gated in `Roadmap_Blocked.md`.
- `scripts/run_dependency_cve_gate.py` and the aggregate Gradle task need a machine-readable join to release dependency graphs, SBOM/APK contents, and host-tool configurations. Handwritten suppression notes should record evidence generated by that join, not substitute for it.
- `DexUtils.java:213` pins the Android JADX fork at 1.4.7, sets `skipResources(true)`, and decompiles generated DEX rather than exporting an APK. Known ZIP/resource/export advisories must not be described as presently reachable. The remaining action is bounded hostile-DEX and resource-exhaustion coverage plus a test that the narrow call surface stays narrow.
- `AppOpsManagerCompat.java:666-700` and `hiddenapi/src/main/java/android/app/AppOpsManagerHidden.java:538-568` retain the API 30 proxy-info TODO. Extending the existing AppOps model with `OpEventProxyInfo` is a contained provenance improvement and does not require a new collection subsystem.
- `benchmark/build.gradle:48,51-52` and `app/build.gradle:318-319` hardcode versions already suited to the central ledger in `versions.gradle`. Move them there and extend `scripts/verify_dependency_floor.py` so module and test pins cannot drift around the API 21 policy.
- Test gaps with direct user impact remain in overlay readback, empty regex replacement, and Swift APK plus OBB conversion. Log Viewer teardown, UID-wide AppOps preflight, and external-broadcast rejection now have host or isolated-emulator coverage.
- API 21 is an intentional product constraint. Activity 1.12, Room 2.8, Material 1.14, WebKit 1.15, and WorkManager 2.11 raise the floor. Dependency modernization must follow `docs/policy/minsdk-21-ceiling.md`, not generic version churn.
- No new i18n row is justified. Per-app locale handling and API 21 language fallback have shipped, while the hosted translation intake remains explicitly service-gated in `Roadmap_Blocked.md`. Accessibility is addressed by visible app-state text; observability by the log lifecycle and AppOps provenance items; testing and docs by their dedicated additions.
- Multi-user, migration, distribution, and upgrade risks are already accounted for: device matrices and the AppsDb migration ladder remain in `Roadmap_Blocked.md`, the v0.6.23 release gate preserves the established signer, and the API 21 ceiling governs dependency upgrades. Offline resilience is strengthened through the backup-provider probe without adding cloud state.

## Rejected Ideas

- **Grant every requested runtime permission during install.** Upstream #2013 has weak demand, requires privileged permission, and works against the installer's disclosure-first safety model.
- **Build a full PDF viewer.** Upstream #2011 would enlarge the untrusted-content surface. Isolate existing thumbnail decoders first and keep viewing in a dedicated app.
- **Add cloud accounts or proprietary backup SDKs.** Swift Backup and AppDash show commercial demand, but accounts conflict with the local, portable FLOSS boundary.
- **Add an always-on VPN firewall or tracker timeline.** TrackerControl and OpTrace are specialized products. Battery, UID attribution, retention, OEM parsing, and work-profile routing would become permanent obligations.
- **Add an Obtainium-style release-source scraper.** Source adapters are brittle and expand the network trust surface without improving package administration.
- **Maintain a central signer database.** Obtainium #2922 explicitly asks for user-controlled hashes. Local pins and verified rotation lineage are auditable; a central service creates freshness and governance problems.
- **Report vulnerable libraries from class-name matches.** The Android third-party-library studies in Sources document obfuscation, optimization, adjacent-version confusion, and version-level false positives. AppManagerNG should show evidence, not a vulnerability verdict it cannot prove.
- **Add one-tap tracker or debloat recommendations without review.** Warden labels its nuke experimental, and Canta discussion #279 demonstrates the recovery cost of an incorrect recommendation.
- **Add a debloat emergency receipt as new work.** The idea is valid but already shipped in `SystemAppRescueArtifacts.java`, including pre-operation snapshots and exact `install-existing` commands.
- **Migrate to Compose or raise minSdk for visual parity.** No accepted opportunity requires either change, and raising the floor would abandon the API 21 and 22 users the project explicitly supports.
- **Add a third-party plugin runtime.** No reviewed competitor demonstrates a safe plugin boundary for UID-wide privileged operations. Profiles, Tasker-compatible automation, and explicit import formats provide extension points without loading third-party code into the app.
- **Migrate profile IDs to UUIDs.** `BaseProfile.java` contains the TODO, but no user-visible collision was found. The migration would touch filenames, routines, shortcuts, imports, logs, and automation payloads without enough evidence to justify the compatibility risk.
- **Add a privileged sensitive-access timeline.** OpTrace has low adoption and unfinished Shizuku support. Proceed only if a parser corpus proves stable attribution across AOSP, Samsung, Xiaomi, and Pixel outputs.

## Sources

### Project and trackers

- https://github.com/SysAdminDoc/AppManagerNG
- https://github.com/SysAdminDoc/AppManagerNG/releases
- https://github.com/SysAdminDoc/AppManagerNG/issues/10
- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/releases
- https://github.com/MuntashirAkon/AppManager/issues/2030
- https://github.com/MuntashirAkon/AppManager/issues/2023
- https://github.com/MuntashirAkon/AppManager/issues/2022
- https://github.com/MuntashirAkon/AppManager/issues/2013
- https://github.com/MuntashirAkon/AppManager/issues/2012
- https://github.com/MuntashirAkon/AppManager/issues/2011
- https://github.com/MuntashirAkon/AppManager/issues/2006

### Direct competitors

- https://github.com/Hamza417/Inure/blob/master/FEATURES.md
- https://github.com/wxxsfxyzm/InstallerX-Revived/releases
- https://github.com/zacharee/InstallWithOptions/blob/main/CHANGELOG.md
- https://github.com/aistra0528/Hail/releases
- https://github.com/aistra0528/Hail/discussions/394
- https://github.com/lihenggui/blocker
- https://github.com/mirfatif/PermissionManagerX
- https://github.com/samolego/Canta/discussions/279
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/NeoApplications/Neo-Backup/issues/1029
- https://github.com/NeoApplications/Neo-Backup/issues/1022
- https://github.com/NeoApplications/neo-backup/blob/main/FAQ.md
- https://github.com/XayahSuSuSu/Android-DataBackup/releases
- https://github.com/XayahSuSuSu/Android-DataBackup/issues/485
- https://github.com/LibChecker/LibChecker/releases
- https://github.com/d4rken-org/sdmaid-se/releases
- https://github.com/TrackerControl/tracker-control-android/releases

### Commercial and adjacent products

- https://appdash.app/faq/
- https://www.swiftbackup.app/faq
- https://github.com/soupslurpr/AppVerifier
- https://github.com/ImranR98/Obtainium/issues/2922
- https://github.com/jksalcedo/optrace
- https://github.com/whyorean/Warden

### Platform and accessibility

- https://developer.android.com/reference/android/app/AppOpsManager.html
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- https://developer.android.com/guide/topics/manifest/service-element
- https://www.w3.org/WAI/WCAG22/Understanding/use-of-color
- https://developer.android.com/design/ui/mobile/guides/foundations/accessibility
- https://developer.android.com/guide/topics/ui/accessibility/views/principles-views

### Dependencies, advisories, and research

- https://github.com/MuntashirAkon/jadx-android/releases
- https://github.com/skylot/jadx/releases
- https://github.com/skylot/jadx/security/advisories/GHSA-hvp5-5x4f-33fq
- https://github.com/skylot/jadx/security/advisories/GHSA-w6f5-h4x4-rfpj
- https://github.com/skylot/jadx/security/advisories/GHSA-jwv3-q635-w9m4
- https://developer.android.com/jetpack/androidx/releases/sqlite
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/activity
- https://github.com/material-components/material-components-android/releases
- https://nvd.nist.gov/vuln/detail/CVE-2026-11822
- https://nvd.nist.gov/vuln/detail/CVE-2026-11824
- https://arxiv.org/abs/2108.01964
- https://www.sciencedirect.com/science/article/abs/pii/S1566253524006869
- https://arxiv.org/abs/2504.13547
- https://www.sciencedirect.com/science/article/pii/S016740482500361X

## Open Questions

None. Public evidence and the checked tree are sufficient to prioritize and implement the active additions. Device-only validation already recorded in `Roadmap_Blocked.md` remains separately gated.
