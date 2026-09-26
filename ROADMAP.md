# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

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

- [ ] P1: Stop App Info subtitles clipping, and gate long locales and large fonts
  Why: two App Info subtitles cap at one line with no ellipsize, so longer translations are cut mid-glyph with nothing signalling truncation, and nothing automated checks translated string length or font scale.
  Evidence: fork issue #13, Russian locale, Android 10, App Info tab, v0.6.22, with a screenshot; `app/src/main/res/layout/pager_app_info.xml:160` `tracker_cta_subtitle` and `:236` `perms_cta_subtitle` set `android:maxLines="1"` with no `android:ellipsize`, while `:59`, `:73`, `:88` and `:313` all pair the two; the 2026-08-22 visual density pass was verified in English on a single API 35 emulator.
  Touches: `app/src/main/res/layout/pager_app_info.xml`, any further layouts the new gate finds, `app/src/main/res/values/dimens-v2.xml`, a new layout-clipping host test.
  Acceptance: no layout under `app/src/main/res/layout/` caps a text view with `maxLines` or `singleLine` without an `ellipsize`, and a host test enumerating that directory fails when one does; both App Info subtitles render their longest shipped translation without a mid-glyph cut; a Robolectric measurement test renders the App Info header and both CTA cards using the longest translated value of each string at font scales 1.0 and 1.3 and fails when measured text is truncated or a row overflows its container; the fix is checked against `values-ru`, the reported locale.
  Complexity: M

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

- [ ] P1: Accept APKs shared through ACTION_SEND in App Details
  Why: App Details reads only `Intent.getData()`, so an APK sent through the standard `ACTION_SEND` `EXTRA_STREAM` contract is ignored.
  Evidence: upstream App Manager #2047 and fix commit `2d9223a7a`; Android `Intent` documentation; `details/AppDetailsActivity.java#getApkSource()`.
  Touches: a shared intent-data resolver, `AppDetailsActivity.java`, URI grant validation, intent fixtures and tests.
  Acceptance: App Details resolves one APK URI from explicit data, `ClipData`, or `EXTRA_STREAM` in deterministic precedence order; it accepts only readable content or file URIs already allowed by the app's policy; multiple streams, wrong MIME types, revoked grants, and missing payloads produce a concise error without a crash; existing deep links behave unchanged.
  Complexity: S

### P2

- [ ] P2: Restore UID and AppId in compact app rows
  Why: `ApplicationItem` still computes UID and AppId and the adapter still binds them, but the compact row hides their containing view, removing useful identity evidence.
  Evidence: fork issue #19; `main/ApplicationItem.java`; `main/MainRecyclerAdapter.java`; `res/layout/item_main_v2.xml`.
  Touches: compact row layout, metadata formatter, adapter visibility rules, content descriptions, long-locale and font-scale snapshots.
  Acceptance: installed rows show concise `UID 10123` or shared-user context in the existing metadata line, not a pill; archived and uninstalled rows never display a stale UID; the same value is available to accessibility services; API, locale, 1.3 font scale, and narrow-width snapshots retain package-name readability and current row density.
  Complexity: S

- [ ] P2: Add an authenticated external operation contract
  Why: automation users need targeted package actions, but a general exported broadcast would create an unauthenticated privileged-operation surface.
  Evidence: the automation half of fork issue #19; existing Tasker integration and signature-protected components; InstallerX's explicit option model; Android exported-component guidance.
  Touches: signed Tasker or signature-permission integration, explicit operation schema, validation, audit history, result callback, security tests and documentation.
  Acceptance: callers must hold the existing signature-level trust or complete the existing Tasker authorization path; each request names one supported operation, package, user, and immutable options; ambiguous, wildcard, stale, replayed, or unauthorized requests are rejected before privilege use; accepted operations enter the same confirmation-free executor and audit journal as UI actions; no general shell command or arbitrary intent field is exposed.
  Complexity: M

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

### P0

- [ ] P0 — Restore the strict dependency-verification baseline
  Category: Build integrity
  Where: `build.gradle:6-33`; `gradle/verification-metadata.xml:4-5,3935-3938,5083-5086,8085-8193`; `scripts/release_gate.py:571-606`
  Problem: A clean strict build cannot resolve every release-gate dependency, so the trusted build path is not reproducible from the committed verification metadata.
  Evidence: Two fresh strict runs stopped on missing checksums for Guava parent metadata, Jackson parents, JUnit BOM module metadata, and OpenTelemetry BOM module metadata before lint and CVE checks could complete.
  Fix: Add publisher-verified hashes for every resolved artifact and parent descriptor, then make a cold-cache strict resolution part of the local release gate. Do not use lenient verification or trust-on-first-use.
  Acceptance: On an empty Gradle dependency cache, the documented JDK 21 release gate resolves FLOSS and Full release configurations with strict verification, produces no dependency-verification report, and leaves `verification-metadata.xml` unchanged on a second run.
  Confidence: Verified
  Effort: M

### P1

- [ ] P1 — Make Code Editor saves atomic and await Save and exit
  Category: Data integrity
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorViewModel.java:207-238`; `app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorFragment.java:266-291,520-532,944-962`; `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java:553-558`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:1240-1259`
  Problem: Saving truncates the destination before serialization and writing finish, while Save and exit navigates away before the asynchronous result is known.
  Evidence: Raw paths use replacement semantics and provider paths request `wt`; an encoding or write failure can leave an empty or partial file, and the destroyed view can miss the failure notice.
  Fix: Serialize into a staged file first, replace raw files atomically where supported, use a provider-safe staged replacement elsewhere, and navigate back only after a successful result.
  Acceptance: Injected encode, open, short-write, flush, and replace failures preserve the original bytes and keep the editor open with actionable feedback; successful Save and exit returns only after the committed bytes can be reopened and verified.
  Confidence: Verified
  Effort: M

- [ ] P1 — Bind restore verification to the bytes that are consumed
  Category: Security
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java:1162-1182`; `app/src/main/java/io/github/muntashirakon/AppManager/backup/RestoreOp.java:369-460,609-815`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:1263-1285`; `libcore/io/src/main/java/io/github/muntashirakon/io/SplitInputStream.java:214-231`
  Problem: Restore hashes one provider stream, then reopens the path for listing, decryption, staging, or extraction, so a mutable provider can change the consumed archive after verification.
  Evidence: Digesting and extraction independently call `openInputStream`; plaintext backup paths are returned unchanged, and destructive restore work can begin from bytes that were never hashed.
  Fix: Copy every selected archive into private staging once, enforce size limits while copying, verify that exact staged object, and perform all later reads from it.
  Acceptance: A provider that changes content between opens cannot alter the restored payload; checksum or authentication failure occurs before app data is cleared; plaintext, encrypted, split, APK, and data archives share the same stage-once contract and cleanup tests.
  Confidence: Verified
  Effort: L

- [ ] P1 — Parse and import one immutable settings snapshot
  Category: Security
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java:725-755,807-818`; `app/src/main/java/io/github/muntashirakon/AppManager/snapshot/SnapshotBundle.java:394-429,548-658`
  Problem: Snapshot preview and import open the selected URI separately, and duplicate ZIP names are interpreted with inconsistent first-wins, last-wins, and append behavior.
  Evidence: Preview returns the first `manifest.json`; import keeps the last manifest and scalar entry while appending duplicate file-list entries, with no duplicate-name rejection.
  Fix: Stage and hash the selected snapshot once, preview and import that staged object, reject every duplicate entry name, and reconcile manifest counts with parsed payloads before writes.
  Acceptance: Mutable-provider and duplicate-entry fixtures cannot make the applied snapshot differ from the reviewed preview; malformed counts and repeated names fail before any preference or file mutation; one digest identifies both review and import.
  Confidence: Verified
  Effort: M

- [ ] P1 — Install the exact APK bytes shown in review
  Category: Security
  Where: `app/src/main/AndroidManifest.xml:749-795`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/ApkQueueItem.java:36-61`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/UriApkSource.java:29-49`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerViewModel.java:86-107,211-234`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerService.java:248-270`
  Problem: A reviewed seekable `content://` APK can remain backed by a mutable provider file until the deferred installer rereads it.
  Evidence: Seekable inputs can be represented by a live `/proc/self/fd` path; final staging computes a digest but never compares it with a review-time digest.
  Fix: Ingest external APK and split inputs into immutable private staging before parsing, then bind size and SHA-256 to the queue item used for approval and installation.
  Acceptance: Provider mutation after review cannot change installed bytes; the service rejects any size or digest mismatch; split membership, signer details, requested permissions, and installed payload all derive from the same staged files.
  Confidence: Verified
  Effort: M

- [ ] P1 — Bound exported XML reads before buffer growth
  Category: Availability security
  Where: `libcore/io/src/main/java/io/github/muntashirakon/io/IoUtils.java:27-33,46-80`; `app/src/main/AndroidManifest.xml:323-380`; `app/src/main/java/io/github/muntashirakon/AppManager/editor/CodeEditorViewModel.java:145-185`; `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java:566-580`
  Problem: The nominal 256 MiB read limit can allocate a 536,869,888-byte buffer before rejection, and exported XML editing can reach this whole-stream path.
  Evidence: The limit check runs before geometric growth; a 268,434,432-byte buffer grows by another 268,435,456 bytes, then the next loop rejects it.
  Fix: Cap growth at `limit + 1`, reject as soon as the next read would exceed the limit, and apply a much smaller documented editor-specific limit before binary XML parsing.
  Acceptance: Inputs at, below, and above each limit use bounded memory; an oversized exported URI fails without an allocation spike or process death; exact-boundary and short-read tests cover byte arrays and provider streams.
  Confidence: Verified
  Effort: S

- [ ] P1 — Treat Pure black as a night-only palette
  Category: Accessibility
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/settings/AppearancePreferences.java:44-70`; `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java:199-235`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java:189-221,365-370`; `app/src/main/res/values/themes-v2.xml:93-127,249`
  Problem: Enabling Pure black in Night mode, then applying Day, produces a persistent black surface with Day text and icon colors.
  Evidence: The headless API 35 reproduction persisted across a cold launch; sampled preference titles were about 1.22:1 and icons about 2.12:1 against black.
  Fix: Gate AMOLED themes and overlays by effective night state, keep the stored preference for future Night use, and bind preference text and icons to semantic on-surface colors.
  Acceptance: Night to Pure black to Day remains readable after recreation and cold launch; Day uses the normal light palette, Night uses true black, Follow system and Battery follow effective state, text reaches 4.5:1, and controls and icons reach 3:1 with and without dynamic color.
  Confidence: Verified
  Effort: M

- [ ] P1 — Cancel screen-lock timers when services stop
  Category: Lifecycle reliability
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/misc/ScreenLockChecker.java:29-97`; `app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/FreezeUnfreezeService.java:65-78,146-153`; `app/src/main/java/io/github/muntashirakon/AppManager/session/SessionMonitoringService.java:44-59,127-140`
  Problem: `ScreenLockChecker` owns a timer that callers cannot close, so queued work can invoke service callbacks after normal destruction.
  Evidence: Both services create the checker and stop their own work in `onDestroy`, but the checker exposes no cancellation, purge, or closed-state guard.
  Fix: Make the checker closeable, cancel and purge its scheduler during teardown, reject new work after close, and guard callbacks with lifecycle state.
  Acceptance: Repeated service start and stop cycles leave no timer threads; advancing the clock after `onDestroy` invokes no callback; concurrent close and screen events are idempotent and covered by deterministic scheduler tests.
  Confidence: Verified
  Effort: S

- [ ] P1 — Serialize Debloater loads and discard stale results
  Category: Concurrency
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterViewModel.java:40-85,210-308`; `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterActivity.java:157-162,289-294`; `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloaterListOptions.java:148-155`; `app/src/main/java/io/github/muntashirakon/AppManager/utils/MultithreadedExecutor.java:22-40`
  Problem: Filter, sort, and reload requests can run concurrently over mutable shared state, allowing an older computation to overwrite a newer selection.
  Evidence: UI option changes submit independent work to a fixed pool without a generation check, serialized snapshot, or immutable result dataset.
  Fix: Snapshot inputs, serialize or cancel superseded loads, compute immutable result sets, and publish only the newest generation.
  Acceptance: Rapid filter, sort, source, and search changes always end on the latest requested state; delayed older work cannot publish; stress tests use forced completion reordering and report no concurrent mutation or mixed-source rows.
  Confidence: Verified
  Effort: M

- [ ] P1 — Remove the exported path that queues a manual backup
  Category: Security
  Where: `app/src/main/AndroidManifest.xml:245-258`; `app/src/main/java/io/github/muntashirakon/AppManager/shortcut/ShortcutDispatchActivity.java:41-80`; `app/src/main/java/io/github/muntashirakon/AppManager/shortcut/AutoBackupShortcutActivity.java:21-63`; `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupScheduler.java:98-105`; `app/src/main/java/io/github/muntashirakon/AppManager/backup/schedule/AutoBackupWorker.java:63-84,107-176`
  Problem: Any installed app can explicitly launch the exported, permissionless shortcut trampoline and queue a manual backup when app authentication is disabled or already satisfied.
  Evidence: The whitelisted action forwards to an unexported target that immediately enqueues `REPLACE` work, and manual input bypasses the scheduled-backup-enabled guard.
  Fix: Make the static exported shortcut open a review and status screen only; move enqueueing behind a user-tapped action in an unexported component and reject untrusted direct intents.
  Acceptance: An external instrumentation app cannot enqueue, replace, or cancel backup work through exported components; a launcher shortcut still opens the review screen; one user tap starts the job and records its origin without a confirmation dialog.
  Confidence: Verified
  Effort: M

- [ ] P1 — Make Full Android-test dependency locks round-trip
  Category: Build integrity
  Where: `build.gradle:30-32`; `app/build.gradle:317-321`; `app/gradle.lockfile:355,366`; `gradle/verification-metadata.xml:6646-6649`
  Problem: The Full debug Android-test runtime resolves `kotlin-stdlib-common:2.3.10`, but the lock assigns it only to the FLOSS runtime, and the documented lock refresh removes rather than repairs the entry.
  Evidence: `generateFullDebugAndroidTestLintModel` fails under strict locking; the failure reproduces with verification enabled, while FLOSS lint, Full release lint, and Full release assembly pass. Commit `c4f3dacd1` narrowed the lock assignment.
  Fix: Make the metadata-only Kotlin module deterministic for both Android-test runtimes through an explicit constraint or safe exclusion, or use a Gradle and AGP pair whose lock writer preserves it.
  Acceptance: Both Android-test lint models, `lintFullDebug`, and `assembleFullDebugAndroidTest` pass under strict verification and locking; refreshing locks then rerunning changes no lockfile and needs no manual repair or bypass.
  Confidence: Verified
  Effort: M

- [ ] P1 — Reproduce and symbolicate profile creation crash on API 37
  Category: Crash triage
  Where: `app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfilesActivity.java:391-393`; `app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfileManager.java:41-53`; `app/src/main/java/io/github/muntashirakon/AppManager/profiles/AppsBaseProfileActivity.java:111-126`; `app/src/main/java/io/github/muntashirakon/AppManager/profiles/AppsProfileViewModel.java:285-310`; `RESEARCH.md:83,263-266`
  Problem: Issue #17 reports a crash on every new-profile attempt, but its v0.6.23 Android 17 trace redacts the exception and every frame, so the current defect location is unknown.
  Evidence: The report is repeatable on a Pixel 6a with Shizuku, but there is no v0.6.24 reproduction, symbolicated AppManagerNG frame, attachment, or follow-up comment.
  Fix: Supply a current debug or mapping-backed build, reproduce the exact create flow on API 37, capture the first app frame and inputs, then add the smallest failing test before choosing a code fix.
  Acceptance: The issue contains a current-build result and symbolicated first-party frame; a deterministic test reproduces the failure if present, or three clean creation runs across empty, app, and component profiles document closure evidence.
  Confidence: Needs-repro
  Effort: M
  Reported: #17

### P2

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
