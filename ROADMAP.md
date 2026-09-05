# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-23)

### P1

- [ ] P1: Decode untrusted file thumbnails in an isolated process
  Why: image, PDF, font, eBook, and archive thumbnails pass attacker-controlled files to decoders inside the permission-bearing app process.
  Evidence: upstream App Manager #2011 maintainer security analysis; Android `<service android:isolatedProcess="true">` documentation; `fm/icons/FmIconFetcher.java`; `fm/icons/FmIcons.java:322-410`; `app/src/main/AndroidManifest.xml`.
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
  Evidence: InstallerX 26.05 signer-policy release; Obtainium #2922; AppVerifier; Inure feature matrix; `permission/monitor/SigningCertSnapshotStore.java`; `apk/signing/SignerInfo.java`; `apk/installer/PackageInstallerActivity.java`.
  Touches: schema extension for `SigningCertSnapshotStore`, signer-policy model, installer preflight, App Details certificate actions, snapshot export/import, audit history, tests.
  Acceptance: a user can pin the installed or reviewed SHA-256 signer set for a package; strict policy blocks a fresh install or update before session commit when the candidate signer and valid rotation lineage do not satisfy the pin; the blocked result names both fingerprints and never consults a remote database; changing a pin is a separate reviewed action and is recorded; policies survive restart and snapshot round-trip.
  Complexity: M

- [ ] P1: Classify dependency CVEs by shipped reachability
  Why: the aggregate CVE report mixes packaged runtime, transformed wrappers, host tools, and test dependencies, leaving 962 result rows across 79 artifacts without a reliable shipped-risk boundary.
  Evidence: `build/reports/dependency-check/dependency-check-report.sarif` from 2026-08-22; `scripts/run_dependency_cve_gate.py`; root `build.gradle`; `config/owasp-suppressions.xml`; CVE-2026-11822 and CVE-2026-11824 mappings to AndroidX wrappers and host `sqlite-jdbc`.
  Touches: `scripts/run_dependency_cve_gate.py`, Gradle dependency-report inputs, reproducible-release evidence, SBOM/APK reachability join, SARIF/HTML outputs, suppression validation tests.
  Acceptance: the gate produces separate packaged-release and host/build/test sections for FLOSS and full; every blocking finding records module, configuration, resolved artifact, and APK or SBOM reachability evidence; host-only and CPE-name-collision findings cannot be presented as shipped APK code; CVSS policy still fails closed for reachable findings; stale or blanket suppressions fail validation. The six August 2026 SQLite CVEs (CVE-2026-51296, -51297, -51300, -51302, -51303, -51304) are documented upstream as fabricated and must carry that disposition rather than being re-investigated each release.
  Complexity: M

- [ ] P1: Bind the privacy policy to the compiled network ledger
  Why: the policy still promises optional Pithus traffic after the integration and its network-ledger entry were removed in v0.6.13.
  Evidence: `PRIVACY_POLICY.rst:21,53,70,121`; `CHANGELOG.md` v0.6.13; `settings/NetworkTransparencyLedger.java`; `settings/NetworkTransparencyLedgerTest.java`. This is the narrow current-network contract, not the broader documentation-truth item parked in `Roadmap_Blocked.md`.
  Touches: `PRIVACY_POLICY.rst`, network-policy contract test, policy build or link check.
  Acceptance: Pithus is absent from current-service definitions, behavior, vendor lists, and references while historical changelog entries remain; a test compares the policy's current optional endpoints with `NetworkTransparencyLedger` and fails when either side adds or removes a service without the other; the policy renders without broken references.
  Complexity: S

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

- [ ] P1: Publish the R8 mapping file with every release
  Why: release builds are minified and no mapping is published, so even a correctly scrubbed crash report resolves only to obfuscated names.
  Evidence: `app/build.gradle:71` sets `minifyEnabled = true`; the v0.6.22 release assets are two APKs, two `.sha256` files, the CycloneDX SBOM, the dependency-check HTML and SARIF, the CVE receipt, and `server-jars.txt`, with no mapping file; fork issue #12's trace contains `nf0.<init>` and an `r8-map-id` marker.
  Touches: `scripts/release_gate.py`, `scripts/verify_release_metadata.py`, `docs/distribution/reproducible-builds.md`, `README.md`.
  Acceptance: the gate emits a mapping file plus its SHA-256 for each minified flavor, records both hashes in the release receipt, and refuses to publish when a minified variant produced no mapping; the two-build reproducibility check treats a mapping difference between the two clean builds as a failure, because differing mappings mean differing DEX; `README.md` documents how to retrace a pasted trace against the published mapping.
  Complexity: S

- [ ] P1: Fix the Code Editor inflation crash and stop it taking the process down
  Why: opening the Code Editor from Labs crashes and restarts the app on Android 10, and one third-party view failing to inflate should not end the process.
  Evidence: fork issue #12, `Binary XML file line #51 in layout/fragment_code_editor: Error inflating class` followed by a null-pointer dereference, on a Redmi 9C running API 29, armeabi-v7a only, MIUI 12.0.16, AppManagerNG v0.6.22; `app/src/main/res/layout/fragment_code_editor.xml:41-51` is the `CodeEditorWidget` element; `editor/CodeEditorWidget.java:28-30` only delegates to the pinned sora-editor; `editor/Languages.java` and `LanguagesAssetTest` cover asset presence but never widget construction.
  Touches: `editor/CodeEditorFragment.java`, `editor/CodeEditorWidget.java`, `app/src/main/res/layout/fragment_code_editor.xml`, `editor/EditorThemes.java`, editor host tests.
  Acceptance: the reported trace is retraced against the published mapping and the failing constructor path is named in the fix; the editor screen catches an inflation or initialisation failure, shows a dismissible error naming what failed, and returns to Labs rather than terminating; a Robolectric test inflates `fragment_code_editor` at API 21, API 29, and the current target and fails when inflation throws; the underlying cause is fixed rather than only caught, and a regression test pins it.
  Complexity: M

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
  Acceptance: running on an SDK above the tested ceiling shows a dismissible non-blocking notice in Privilege Health and adds a line to the support bundle, and the app still starts and runs — it never refuses a new Android version; every `compat/` accessor that falls back after a reflection or linkage failure logs the class, member, and SDK at warning level once per process instead of silently returning a default; a host test asserts the ceiling constant tracks `compileSdk` so it cannot go stale, and asserts a simulated linkage failure produces exactly one log record.
  Complexity: M

- [ ] P1: Revoke internet access per app through privileged network rules
  Why: upstream shipped per-app INTERNET revocation in v4.1.1 and this fork has only metered-background net policy, which does not block connectivity.
  Evidence: upstream v4.1.1 release notes (2026-09-04) describe eBPF rules applied in root and ADB mode from the Uses-permissions tab, with loss on reboot named as a known limitation; `compat/NetworkPolicyManagerCompat.java` and `rules/struct/NetPolicyRule.java` cover metered background only; `self/BootReceiver.java:29-31` already re-applies routines on boot; `batchops/struct/BatchNetPolicyOptions.java`.
  Touches: a new privileged network-rule executor, `rules/struct/`, `rules/RulesStorageManager.java`, the App Details uses-permissions tab, `batchops/BatchOpsManager.java`, `self/BootReceiver.java`, `profiles/`, host tests using a faked privileged executor.
  Acceptance: a persisted rule type records package, UID, and rule state and round-trips through the existing rules import and export formats; the uses-permissions row for `android.permission.INTERNET` reflects stored state and is disabled with a stated reason when no privileged mode is active; rules are re-applied on `BOOT_COMPLETED` and the UI reports the window before enforcement resumes rather than claiming enforcement it does not have; host tests cover apply, revert, boot re-apply, and privilege loss against a faked executor; an unsupported kernel or ROM produces an explained unavailable state rather than a silent no-op. If on-device verification turns out to need a kernel floor that cannot be checked on the host, move the enforcement half to `Roadmap_Blocked.md` and keep the rule model here.
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
