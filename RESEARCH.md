# Research: AppManagerNG

Date: 2026-09-25. Replaces all prior research.  
Reviewed source: v0.6.24 at fde584ce0. Latest published release: v0.6.23, published 2026-09-05.

## Executive Summary

AppManagerNG is an offline-first Android package manager with an unusually broad evidence and control surface. Its strongest direction is not another utility module. It is a dependable loop that shows the exact state, performs a privileged change, verifies the result, and can reverse that change without overwriting newer work. The first release after this research should close four verified trust failures: hostile keystore cost, root probing in no-root mode, Usage Access identity confusion, and optional telephony service crashes. The next layer should make mutations conflict-safe, finish Android 15 through 17 compatibility, and turn existing storage, backup, and debloat data into clearer evidence.

Top opportunities, in priority order:

1. **Verified, P0:** align Bouncy Castle at 1.86 and bound BKS and PKCS12 import cost. The reachable paths are `settings/crypto/ImportExportKeyStoreDialogFragment.java` and `crypto/ks/KeyStoreUtils.java`.
2. **Verified, P0:** make explicit no-root startup bypass root and remote-server construction. The current path crosses `settings/Ops.java`, `runner/Runner.java`, and `runner/NormalShell.java`.
3. **Verified, P0:** separate AppManagerNG's own Usage Access grant from Shizuku or root query capability. [Fork issue #16](https://github.com/SysAdminDoc/AppManagerNG/issues/16) maps to `SelfPermissions.checkUsageStatsPermission()` using `Users.getSelfOrRemoteUid()`.
4. **Verified, P0:** treat a missing `isub` binder as unavailable subscriber metadata. [Fork issue #18](https://github.com/SysAdminDoc/AppManagerNG/issues/18) maps to `compat/SubscriptionManagerCompat.java`.
5. **Verified, P1:** add an append-only mutation journal with exact before-state, confirmed after-state, execution identity, and conflict-aware rollback.
6. **Verified, P1:** stop decoding every binary manifest against the newest Android framework table. [Upstream #2040](https://github.com/MuntashirAkon/AppManager/issues/2040) reproduces the wrong-name risk in `apk/parser/AndroidBinXmlDecoder.java`.
7. **Verified, P1:** finish Android 15 package and work compatibility: archived-package enumeration, scheduled-network work, persisted SAF grant cleanup, and ACTION_SEND APK handling.
8. **Verified, P1:** complete the read-only DocumentsProvider containment contract and test symlink and prefix escapes.
9. **Needs live validation, P2:** add storage composition and deltas, backup footprint preflight, and evidence-qualified debloat guidance to existing surfaces.
10. **Verified, P2:** improve diagnostics for Android 17 pre-ANR warnings, Shizuku OEM failures, and Samsung UI Tracker self-attribution.

## Product Map

### Core workflows

- Inventory and search apps, then inspect identity, signing, permissions, AppOps, components, native libraries, storage, usage, and install metadata.
- Apply package, permission, component, network, profile, freeze, archive, and debloat operations through root, ADB, Shizuku, or no-root capabilities.
- Inspect and install APKs with signer and session controls.
- Back up, restore, convert, and manage app data through local or Storage Access Framework destinations.
- Diagnose devices through logcat, running processes, the terminal, file manager, code editor, App Change Feed, and support bundles.

### User personas

- Privacy-conscious Android users who want local evidence before changing permissions or AppOps.
- Power users managing many packages, profiles, users, or work profiles.
- Root and Shizuku users who need privileged controls without a cloud account.
- Troubleshooters comparing APKs, signers, libraries, manifests, logs, and package state.

### Platforms and distribution

- Android API 21 and later, compiled against API 37 and targeting API 36 in the reviewed source.
- Java-first Android Views and Material components in a multi-module Gradle build.
- FLOSS and full variants, with local release evidence and reproducibility checks.
- Source v0.6.24 is ahead of the [published v0.6.23 release](https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.23). No roadmap item is warranted from that gap alone.

### Key integrations and data flows

- Privileged calls flow through operation selection, local or remote binder services, compatibility wrappers, then authoritative Android services.
- Backup data flows through part planning, archive creation, SAF or local destinations, publish, retention, and restore validation.
- Package and permission observations feed snapshots, operation history, rules, profiles, and the App Change Feed.
- External trust inputs include user-selected APKs, manifests, DEX, archives, fonts, images, BKS, PKCS12, and DocumentsProvider URIs.

## Competitive Landscape

| Product | What it does well | Learn from it | Avoid |
|---|---|---|---|
| [Upstream App Manager](https://github.com/MuntashirAkon/AppManager) | Broad package control and fast Android compatibility work | Port narrow fixes with local contract tests | Wholesale cherry-picks across this fork's changed privilege and release model |
| [Inure](https://github.com/Hamza417/Inure) | Dense package, signer, library, and state presentation | Compact evidence grouping and stronger visual hierarchy | Breadth that does not improve trust or rollback |
| [Hail](https://github.com/aistra0528/Hail) and [FreezeYou](https://github.com/FreezeYou/FreezeYou) | Focused freeze workflows | Fast, legible routine execution | A separate freeze-only workspace |
| [PermissionManagerX](https://github.com/mirfatif/PermissionManagerX) | Permission watcher and scheduled drift checks | Report-only drift built on current snapshots | Always-on enforcement claims without a durable privileged channel |
| [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) | Granular installer choices | Named local presets and visible option diffs | Network APK streaming and hidden defaults |
| [Canta](https://github.com/samolego/Canta) and [UAD-NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | Accessible debloat guidance | Device-qualified provenance and recovery notes | Universal safe-to-remove labels |
| [Neo Backup](https://github.com/NeoApplications/Neo-Backup) and [DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup) | Focused backup planning and restore flows | Footprint preflight and destination evidence | Full-device backup promises Android cannot keep |
| [LibChecker](https://github.com/LibChecker/LibChecker) | Clear library and change evidence | Storage deltas in the existing change feed | CVE claims based only on class names |
| [AppVerifier](https://github.com/soupslurpr/AppVerifier) | User-readable signer comparison | Local signer pins and lineage-aware continuity | A central package-trust authority |
| [Thor](https://github.com/trinadhthatakula/Thor) | Per-setting history and Changed elsewhere language | Exact before and after mutation records | Rollback that overwrites a newer external change |
| [AppDash](https://appdash.app/) | Focused package workflows | Clear task grouping | Cloud or account coupling |
| [Swift Backup](https://www.swiftbackup.app/) | Clear backup-part expectations | Per-part estimate and restore planning | Proprietary storage dependencies |

The category signal is consistent: users want inspection, debloat, freeze, backup, and permission control. AppManagerNG already has those domains. It should connect them through shared evidence rather than add more top-level tools.

## Reported Issues

### Fork tracker

| Issue | Finding | Disposition on 2026-09-25 |
|---|---|---|
| [#12](https://github.com/SysAdminDoc/AppManagerNG/issues/12) | Code Editor crash on API 29 armeabi-v7a | **Needs live validation.** v0.6.24 preserves first-party frames and publishes the R8 mapping. Require a fresh trace before changing code. The item remains in `Roadmap_Blocked.md`. |
| [#13](https://github.com/SysAdminDoc/AppManagerNG/issues/13) | Russian App Info text clips | **Verified, already represented.** The active roadmap has a long-locale and font-scale gate. |
| [#15](https://github.com/SysAdminDoc/AppManagerNG/issues/15) | Permission watcher request | **Verified, already represented.** The active roadmap covers grant-state snapshots and report-only drift. |
| [#16](https://github.com/SysAdminDoc/AppManagerNG/issues/16) | Repeated Usage Access prompt under Shizuku | **Verified, new P0.** UI grant identity and remote query identity are mixed. |
| [#17](https://github.com/SysAdminDoc/AppManagerNG/issues/17) | Profile creation crash on API 37 | **Needs live validation.** The v0.6.23 trace lacks a usable first-party frame. |
| [#18](https://github.com/SysAdminDoc/AppManagerNG/issues/18) | Usage data crashes when `isub` is missing | **Verified, new P0.** Service absence escapes a path that catches only `RemoteException`. A separate Material theme comment needs its own fresh trace. |
| [#19](https://github.com/SysAdminDoc/AppManagerNG/issues/19) | UID missing and automation requested | **Verified, split into two items.** Restore UID metadata. Add only a narrow authenticated operation contract. |

### Upstream tracker

- **Verified, new roadmap items:** [#2039](https://github.com/MuntashirAkon/AppManager/issues/2039) UI Tracker self-attribution, [#2040](https://github.com/MuntashirAkon/AppManager/issues/2040) framework attribute misdecode, [#2043](https://github.com/MuntashirAkon/AppManager/issues/2043) archived packages absent, [#2047](https://github.com/MuntashirAkon/AppManager/issues/2047) ACTION_SEND APK ignored, [#2048](https://github.com/MuntashirAkon/AppManager/issues/2048) no-root probing root, and [#2054](https://github.com/MuntashirAkon/AppManager/issues/2054) shortcut targets absent from Recents.
- **Verified, existing roadmap items strengthened:** [#2044](https://github.com/MuntashirAkon/AppManager/issues/2044) adds post-install reapplication evidence to privileged internet rules. [#2055](https://github.com/MuntashirAkon/AppManager/issues/2055) adds demand evidence to user-owned signer policy.
- **Rejected as duplicate:** [#2049](https://github.com/MuntashirAkon/AppManager/issues/2049) asks for signer evidence already present in the relevant inspection path.
- **Needs live validation:** [#2056](https://github.com/MuntashirAkon/AppManager/issues/2056) depends on hidden APIs and OEM behavior for default-installer selection. It is not ready for a roadmap commitment.
- **Stale or insufficient:** vague root-mode reports without a current version, mode, and support bundle do not justify a second item beside the verified no-root root probe.

## Security, Privacy, and Reliability

### Verified risks

- BKS and PKCS12 imports reach a Bouncy Castle family split across 1.85, 1.85.1, and 1.85.2. [Bouncy Castle 1.86](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/) addresses [CVE-2026-17508](https://github.com/bcgit/bc-java/discussions/2449). Align all three artifacts and enforce app-owned file, KDF, memory, and elapsed-time ceilings.
- Explicit no-root selection does not prevent root-capable shell construction soon enough. A mode choice must be side-effect free before any probe or connection attempt.
- Optional Android services are treated as universal in the subscription path. Absence must become classified unavailable data, not process failure.
- `settings/BackupRestorePreferences.java` takes persistable SAF grants for replacement destinations without releasing abandoned grants. Android maintains a finite per-app grant pool.
- `fm/AppManagerDocumentsProvider.java` lacks `isChildDocument()` and `FLAG_SUPPORTS_IS_CHILD`. Canonical containment needs sibling-prefix, encoded-separator, and symlink escape tests.
- Current inverse rollback in `history/ops/PerAppRollbackManager.java` does not prove that current state still equals the state AppManagerNG wrote.

### Missing guardrails

- Use one shared untrusted-input budget model for byte count, dimensions or entries, result size, elapsed time, cancellation, and classified failure.
- Make execution identity explicit at every compatibility boundary: app UID, remote UID, target user, calling package, and capability source.
- Preserve raw Android resource IDs when a binary XML mapping is uncertain. A confident wrong name is worse than an honest numeric ID.
- Record Shizuku binder state, provider version, SDK, OEM, remote UID, and stable failure code without logging commands or user data.
- Do not vendor [Shizuku API pull request #299](https://github.com/RikkaApps/Shizuku-API/pull/299) before affected device evidence identifies the exact failure.

### Recovery and rollback

- Store exact before value, requested value, confirmed after value, execution identity, result, and time for reversible mutations.
- Read current state before rollback. If it differs from the recorded after value, mark the entry Changed elsewhere and perform no write.
- Acquire and verify a replacement SAF grant before releasing the old one. Process death must leave at least one working destination.
- A failed thumbnail, manifest, DEX, archive, keystore, or provider operation must preserve the last confirmed UI state and return a bounded error.

## Architecture Assessment

### Boundary changes

- Split UI grant state from privileged execution capability in `self/SelfPermissions.java` and `compat/UsageStatsManagerCompat.java`.
- Replace int-narrowed package match flags in `compat/PackageManagerCompat.java` with long-backed framework flag objects on supported APIs.
- Put optional-service lookup behind nullable or result-bearing adapters instead of letting binder absence escape from feature code.
- Add a durable mutation record beside operation history rather than encoding rollback only as a derived inverse command.
- Keep storage deltas, backup estimates, debloat provenance, and ANR warnings in their existing domain stores. No new top-level database is needed.

### Refactor candidates

- `apk/parser/AndroidBinXmlDecoder.java`: framework-table selection and raw-ID fallback.
- `runner/Runner.java`, `runner/NormalShell.java`, and `settings/Ops.java`: mode-first shell construction.
- `details/AppDetailsActivity.java`: one input-URI resolver for data, ClipData, and ACTION_SEND.
- `fm/AppManagerDocumentsProvider.java`: canonical document identity and containment.
- `settings/BackupRestorePreferences.java`: reference-counted persisted-grant handoff.
- `accessibility/activity/TrackerWindow.java`: last-external-activity state instead of one-off self filters.

### Test and documentation gaps

- Add contract tests for no-root side effects, long package flags, optional binder absence, remote versus local Usage Access, and ACTION_SEND URI precedence.
- Add hostile fixtures for keystores, binary XML, DocumentsProvider paths, and backup providers. Each test needs a bounded failure assertion.
- Keep the existing i18n clipping, permission drift, JADX reachability, signer policy, dependency reachability, and backup destination items. This pass found new evidence, not replacements.
- Document the WorkManager Android 15 fix choice and the API 21 compatibility effect before changing the dependency.
- Distribution and packaging already have strong local evidence checks. No hosted build system or new packaging item is justified.
- Multi-user behavior is part of acceptance for Usage Access and archived-package enumeration. Schema migrations are part of acceptance for mutation, storage, debloat, and installer preset stores.
- Accessibility is covered by the existing state-text and locale gates plus the new Android 16 custom-chart item. Offline resilience is covered by SAF and scheduled-work items.
- Android 15 force-stop copy belongs with the existing blocked `ApplicationStartInfo` panel, which can establish why the process restarted. A separate in-process detection item would be naive because the app cannot run while it is stopped.

## Rejected Ideas

- Cloud backup protocols, cloud accounts, and proprietary storage SDKs: SAF is the product boundary, and provider behavior can be measured locally.
- An Obtainium-style release scraper or alternative app store: weak fit and a large new network trust surface.
- A VPN firewall: it duplicates the existing privileged eBPF or netd roadmap direction.
- Cleaner, duplicate-media, media-viewer, scrcpy, fastboot, and remote ADB workspaces: separate products with weak reuse of AppManagerNG's evidence model.
- A plugin marketplace or in-process third-party code: disproportionate execution and update risk. [Awesome Shizuku](https://github.com/timschneeb/awesome-shizuku) is discovery evidence, not a reason to host plugins.
- Universal one-tap debloat labels: [UAD-NG #1164](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1164), [#1400](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1400), and [#1311](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1311) show device, ROM, and region dependence.
- CVE claims from class-name matches: confirm packaged artifact, version, and reachable call path.
- Full-device backup and continuous privileged-monitoring promises: Android lifecycle and OEM policy make them unreliable.
- A Compose migration or higher minSdk for fashion: neither solves a verified user problem. WorkManager must be handled without abandoning API 21.
- A default-installer toggle before an Android and OEM matrix exists: the current proposal relies on hidden APIs and variable system handling.

## Sources

### Project and trackers

- https://github.com/SysAdminDoc/AppManagerNG
- https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.23
- https://github.com/SysAdminDoc/AppManagerNG/issues/12
- https://github.com/SysAdminDoc/AppManagerNG/issues/13
- https://github.com/SysAdminDoc/AppManagerNG/issues/15
- https://github.com/SysAdminDoc/AppManagerNG/issues/16
- https://github.com/SysAdminDoc/AppManagerNG/issues/17
- https://github.com/SysAdminDoc/AppManagerNG/issues/18
- https://github.com/SysAdminDoc/AppManagerNG/issues/19
- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.1
- https://github.com/MuntashirAkon/AppManager/issues/1959
- https://github.com/MuntashirAkon/AppManager/issues/2039
- https://github.com/MuntashirAkon/AppManager/issues/2040
- https://github.com/MuntashirAkon/AppManager/issues/2043
- https://github.com/MuntashirAkon/AppManager/issues/2044
- https://github.com/MuntashirAkon/AppManager/issues/2047
- https://github.com/MuntashirAkon/AppManager/issues/2048
- https://github.com/MuntashirAkon/AppManager/issues/2054
- https://github.com/MuntashirAkon/AppManager/issues/2055
- https://github.com/MuntashirAkon/AppManager/issues/2056

### Platform, standards, and dependencies

- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/app/AppOpsManager
- https://github.com/topjohnwu/libsu/blob/master/core/src/main/java/com/topjohnwu/superuser/Shell.java
- https://developer.android.com/reference/android/content/pm/PackageManager#MATCH_ARCHIVED_PACKAGES
- https://developer.android.com/reference/android/content/Intent
- https://developer.android.com/guide/components/activities/recents
- https://developer.android.com/guide/topics/manifest/activity-element
- https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts
- https://developer.android.com/reference/android/content/pm/PackageInstaller
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/reference/android/app/ActivityManager
- https://developer.android.com/reference/android/app/AnrWarningResult
- https://developer.android.com/reference/android/app/ApplicationExitInfo.AnrInfo
- https://developer.android.com/about/versions/16/features
- https://developer.android.com/about/versions/15/behavior-changes-all
- https://developer.android.com/reference/android/app/usage/StorageStats
- https://developer.android.com/reference/android/provider/DocumentsProvider
- https://developer.android.com/reference/android/provider/DocumentsContract.Root
- https://developer.android.com/training/data-storage/shared/documents-files
- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/uri/UriGrantsManagerService.java
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/versions
- https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/
- https://github.com/bcgit/bc-java/discussions/2449
- https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk15to18/1.86/bcpkix-jdk15to18-1.86.pom
- https://repo1.maven.org/maven2/org/bouncycastle/bcutil-jdk15to18/1.86/bcutil-jdk15to18-1.86.pom
- https://github.com/RikkaApps/Shizuku-API
- https://github.com/RikkaApps/Shizuku-API/pull/299
- https://github.com/topjohnwu/libsu/releases
- https://github.com/topjohnwu/Magisk/releases
- https://github.com/skylot/jadx/security/advisories/GHSA-hvp5-5x4f-33fq
- https://www.w3.org/TR/WCAG22/
- https://mas.owasp.org/MASVS/
- https://csrc.nist.gov/pubs/sp/800/218/final
- https://arxiv.org/abs/1904.05572
- https://www.usenix.org/conference/usenixsecurity21/presentation/lee-yu-tsung

### Competitors and community

- https://github.com/Hamza417/Inure
- https://github.com/aistra0528/Hail
- https://github.com/FreezeYou/FreezeYou
- https://github.com/mirfatif/PermissionManagerX
- https://mirfatif.github.io/PermissionManagerX/help/permission-watcher/
- https://mirfatif.github.io/PermissionManagerX/help/scheduled-check/
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://github.com/wxxsfxyzm/InstallerX-Revived/issues/821
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1164
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1400
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1311
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/NeoApplications/Neo-Backup/issues/906
- https://github.com/XayahSuSuSu/Android-DataBackup
- https://github.com/LibChecker/LibChecker/issues/1945
- https://github.com/soupslurpr/AppVerifier
- https://github.com/trinadhthatakula/Thor
- https://appdash.app/
- https://www.swiftbackup.app/
- https://adbappcontrol.com/en/
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root
- https://f-droid.org/en/categories/app-manager/
- https://shizukuapps.com/
- https://www.reddit.com/r/androidapps/comments/1oiewkh/permissions_management_app/
- https://www.reddit.com/r/androidapps/comments/1r0s7fl/your_top_shizuku_applications_and_why/
- https://news.ycombinator.com/item?id=39019252

## Open Questions

- Does fork issue #17 reproduce on v0.6.24, and what is the first AppManagerNG frame in a symbolicated API 37 trace?
- Which Android 15 through 17 builds reproduce WorkManager 2.10.5 connectivity or periodic-work failures in the scheduled backup path?
- Which Samsung firmware versions produce UI Tracker self-attribution, and does filtering all tracker-overlay self events hide any real target transition?
- Do API 35 and 36 expose archived packages consistently through ADB and Shizuku for secondary users?
- Which MediaTek and HyperOS builds require the open Shizuku compatibility change, and what binder failure appears before it?
- What capacity and failure behavior do common network-backed DocumentsProviders expose during backup footprint preflight?
