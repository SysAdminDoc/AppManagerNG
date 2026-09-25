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

### P3

- [ ] P3: Let the consistency gate find Python the way its sibling script does
  Why: `scripts/verify-release-consistency.sh` fails with "Python 3 is required to verify the release receipt" on a machine where Python is installed but not on `PATH`, so the whole gate reports FAILED for an environment reason while every version surface it checked passed.
  Evidence: `scripts/verify-release-consistency.sh:181-189` probes only `python3` and `python`; `scripts/verify_reproducible_release.sh:10-22` already honours `$PYTHON_CMD` and falls back to `py -3`. Hit on 2026-09-05 during a version bump; the run passed once `PATH` was patched by hand.
  Touches: `scripts/verify-release-consistency.sh`, `scripts/tests/test_verify_release_consistency.py`.
  Acceptance: the script resolves an interpreter through `$PYTHON_CMD`, then `python3`, then `python`, then `py -3`, matching `verify_reproducible_release.sh`; a test proves that an environment with none of them still fails closed with the existing message, and that `$PYTHON_CMD` is preferred when set.
  Complexity: S

## Research-Driven Additions (2026-09-25)

### P0

- [ ] P0: Align Bouncy Castle 1.86 and bound hostile keystore imports
  Why: user-selected BKS and PKCS12 files reach Bouncy Castle parsing, where hostile PBE parameters can force unbounded key derivation, and the current provider family is split across 1.85, 1.85.1, and 1.85.2.
  Evidence: CVE-2026-17508; Bouncy Castle Java 1.86 release and discussion #2449; the 1.86 `bcpkix-jdk15to18` and `bcutil-jdk15to18` POMs; `settings/crypto/ImportExportKeyStoreDialogFragment.java:131-134`; `crypto/ks/KeyStoreUtils.java:116-134`; current dependency locks. The existing CVE-reachability item fixes report classification, not this reachable dependency and import path.
  Touches: `versions.gradle`, Bouncy Castle declarations and locks, keystore import policy, `ImportExportKeyStoreDialogFragment.java`, `KeyStoreUtils.java`, hostile BKS and PKCS12 fixtures, SBOM and dependency checksums.
  Acceptance: `bcprov`, `bcpkix`, and `bcutil` resolve to 1.86 from one version declaration in every variant; import rejects files, KDF counts, salt sizes, and derived-memory requests above documented ceilings before expensive work begins; parsing runs with an elapsed-time budget and returns a classified error without blocking the main thread; hostile fixtures cannot exhaust the test process; FLOSS and full SBOMs show one aligned provider family.
  Complexity: M

- [ ] P0: Make no-root initialization side-effect free
  Why: choosing no-root can still enter root-capable shell construction and remote-server detection before the selected mode is honored, causing an unwanted superuser prompt and slow startup.
  Evidence: upstream App Manager #2048 and #2036; `settings/Ops.java`; `runner/Runner.java:95-112`; `runner/NormalShell.java`; libsu's default `Shell.getShell()` behavior.
  Touches: `settings/Ops.java`, `runner/Runner.java`, `runner/NormalShell.java`, remote-service initialization, privilege-mode tests, Privilege Health diagnostics.
  Acceptance: an explicit no-root start makes zero calls to root detection, `Shell.getShell()`, Shizuku or ADB server connection, or remote UID discovery; it creates only the non-root process shell after the mode decision; switching from a privileged mode tears down its connection before no-root becomes active; spy-based tests fail on any forbidden call; startup reports the selected mode without a root prompt or avoidable timeout.
  Complexity: M

- [ ] P0: Separate app Usage Access from remote query capability
  Why: the permission check uses the remote shell UID in Shizuku mode while Android's Usage Access settings grant belongs to AppManagerNG, so the app can repeatedly ask for a grant the user already supplied.
  Evidence: fork issue #16; Android `UsageStatsManager` and `AppOpsManager` contracts; `self/SelfPermissions.java:179-188`; `users/Users.java:151`; `compat/UsageStatsManagerCompat.java`; every UI caller of `checkUsageStatsPermission()`.
  Touches: `SelfPermissions.java`, `UsageStatsManagerCompat.java`, Usage Access settings routing, App Usage and widget callers, secondary-user capability model, tests.
  Acceptance: one method answers whether AppManagerNG's own UID has the user-facing grant, and a separate method answers whether a concrete execution identity can query one specified user; the settings prompt is based only on the app UID; Shizuku UID 2000 cannot turn a granted UI state into a false negative; local, Shizuku, root, and cross-user tests cover granted, denied, and unavailable results without broadening access.
  Complexity: M

- [ ] P0: Degrade gracefully when optional telephony binder services are absent
  Why: usage-data collection assumes the `isub` binder exists, but Wi-Fi-only hardware and some OEM builds omit it, allowing service-not-found to escape instead of returning an empty subscriber set.
  Evidence: fork issue #18; `compat/SubscriptionManagerCompat.java:28-79`; `ipc/ProxyBinder.java`; Android's optional telephony feature model.
  Touches: `SubscriptionManagerCompat.java`, binder lookup result handling, network-usage aggregation, support-bundle diagnostics, service-absence tests.
  Acceptance: a missing, null, dead, or security-rejected subscription service returns no subscriber IDs and does not discard the rest of the usage result; support diagnostics record one classified line without a stack-trace loop; devices with a working service retain current behavior; tests cover no service, binder death, `RemoteException`, and a valid multi-SIM response.
  Complexity: S

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

- [ ] P2: Keep UI Tracker focused on the last external activity
  Why: on affected Samsung builds, AppManagerNG's own tracker overlay can become the detected foreground activity and replace the app the user is trying to inspect.
  Evidence: upstream App Manager #2039; `accessibility/activity/TrackerWindow.java`; `LeadingActivityTrackerActivity.java`; current self-filtering limited to one EditText path.
  Touches: tracker event filter, last-external-activity state, overlay lifecycle, Samsung event fixtures, tests.
  Acceptance: events from AppManagerNG's tracker and overlay windows never replace a valid external target; closing or moving the overlay preserves the last external package and activity until a different external activity arrives or a bounded stale timeout expires; genuine navigation into AppManagerNG outside the overlay remains observable when tracking is not active; tests cover Samsung-style self events, split screen, rapid app switches, and timeout.
  Complexity: M

### P3

- [ ] P3: Add named local installer presets
  Why: installer options are global, so users who repeat different workflows must reconfigure individual controls and can miss a risky carried-over setting.
  Evidence: InstallerX Revived option model and issue #821; `apk/installer/InstallerOptions.java`; installer preferences and preflight.
  Touches: preset model and store, installer options UI, preflight diff, import and export, migration, tests.
  Acceptance: users can save, rename, duplicate, apply, and delete local presets; applying a preset shows the fields that differ from current options before commit; unsupported options are ignored with an explanation, not silently coerced; no preset changes the default until explicitly applied; presets round-trip through app settings export without secrets or device-specific paths.
  Complexity: M
