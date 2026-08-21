# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

### P2

- [ ] P2 — Toolchain security pass: Kotlin 2.4.20, dependency-check 13.0.0, AGP 9.3.0
  Why: the Kotlin toolchain sits on a CVE'd line (CVE-2026-53914, unsafe deserialization of build-cache metadata), and AGP 9.3's keepRules source sets structurally mitigate the v0.6.12 class of R8 bug. Bundling the three bumps pays the dependency-locking and verification-metadata churn once.
  Evidence: GHSA-r937-wjx7-w2jp; dependency-check 13.0.0 (2026-08-03); AGP 9.3.0 release notes; docs/distribution/dependency-verification.md.
  Urgency, measured 2026-08-11: low. `gradle.properties` enables only the local build cache and no remote or shared cache is configured anywhere, so exploiting CVE-2026-53914 would require local write access to the developer's own cache — matching JetBrains' own 6.7 vector rather than NVD's 9.8. Sequence it deliberately; do not rush it at the end of a session.
  Touches: versions.gradle, buildscript-gradle.lockfile and module lockfiles, gradle/verification-metadata.xml, config/owasp-suppressions.xml (drop the CVE-2026-53914 rule after the upgrade — the gate fails on unused rules), optionally move the BouncyCastle keeps into src/*/keepRules/; check for LSPosed HiddenApiBypass 6.2+ while touching pins (AppManager.java:121).
  Acceptance: build and full host suite green; locking and verification refreshed per the documented procedure using publisher-published checksums, never --write-verification-metadata; release gate including the CVE stage passes; the Kotlin suppression is removed and the gate stays green.
  Complexity: M
  Corrections, 2026-08-11 research pass: "Kotlin 2.4.20" is a **Beta** (`2.4.20-Beta2`); the latest stable is **2.4.10** (2026-07-14). Kotlin is not a declared dependency — it reaches the build only on the buildscript classpath via AGP (`buildscript-gradle.lockfile:127-132`, all `2.2.10`), so AGP is the lever and there is no Kotlin pin to bump. Current stable floors are **AGP 9.3.1** (2026-07-23) and **Gradle 9.7.0** (2026-08-06), not 9.3.0/9.6.1. Sequence this **after** the BouncyCastle 1.85.2 row below, which pays the same locking/verification churn on a higher-severity finding.

### P3

- [ ] P3 — App-update change report
  Why: nobody in the fork's field answers "what changed when this app updated"; LibChecker's snapshots and AppDash's watchlists show demand, and NG already has the ingredients (tracker scanner, permission monitor, op history).
  Evidence: LibChecker 2.5.4 snapshot/diff feature; appdash.app watchlist; PermissionChangeReceiver (off-by-default) and op_history infrastructure.
  Touches: Room (new table with explicit migration — no destructive-fallback reliance while the P0 migration-ladder item is blocked), package-update broadcast handling, App Details or history surface, opt-in notification.
  Acceptance: after an app updates, a report lists added/removed permissions, trackers, and components vs the pre-update record; feature is opt-in; diff logic fully unit-tested; Room migration test included.
  Complexity: L

- [ ] P3 — Per-app notes
  Why: tags shipped (NF-08) but free-text notes are the standard companion in every app organizer (Inure build107.1.0 notes editor, AppDash, Hail) and pair naturally with Finder.
  Evidence: Inure build107.1.0 (2026-07-12); AppsDb tag infrastructure already in place.
  Touches: Room schema (explicit migration, see above), App Details, optional Finder "has note / note contains" predicate.
  Acceptance: a note is editable from App Details, persists across restarts, exports/imports with snapshot bundles, and is queryable from Finder; migration test included.
  Complexity: M

- [ ] P3 — Batch component-blocking performance baseline and coalescing
  Why: bulk IFW writes are the field's known slow path (Blocker #1565: "extremely slow processing" on block-all-matched), and v0.6.10's per-package serialized rules transactions add per-package lock/commit overhead to exactly that path — measure before it becomes a complaint.
  Evidence: lihenggui/blocker#1565; v0.6.10 rules transaction serialization (CHANGELOG).
  Touches: rules commit path (ComponentsBlocker/RulesStorageManager), batchops block/unblock flow, benchmark/ module.
  Acceptance: a benchmark records blocking N components across M packages; the batch path commits once per package (not per component); benchmark result recorded in the benchmark module and no correctness test regresses.
  Complexity: M

- [ ] P3 — Surface API 36/37 diagnostics in App Details (pending-job reasons, MemoryLimiter exits)
  Why: two cheap extensions of panels that already exist: JobScheduler pending-job reasons (API 36) explain stuck background work, and Android 17's memory-limiter kills appear in ApplicationExitInfo descriptions the app already renders.
  Evidence: developer.android.com Android 16 features (getPendingJobReasons/History); behavior-changes-17 (MemoryLimiter:AnonSwap in ApplicationExitInfo.getDescription()); existing recent-exits rendering at details/info/AppInfoFragment.java:3768.
  Touches: details/info, compat wrapper for the JobScheduler API.
  Acceptance: on API 36+ pending-job reasons render per app; MemoryLimiter exit descriptions render distinctly in recent exits; both guarded on older APIs; Robolectric coverage.
  Complexity: S

- [ ] P3 — Detect Advanced Protection Mode and explain blocked installs
  Why: API 37's AdvancedProtectionManager lets the installer name why sideloading is refused instead of failing opaquely — the same explain-the-gate pattern as the shipped restricted-settings detector.
  Evidence: developer.android.com Android 17 features (AAPM); v0.6.7 restricted-settings detector precedent.
  Touches: installer preflight, details/info diagnostics, strings.
  Acceptance: with APM active (simulated), install preflight and sideload diagnostics name Advanced Protection with guidance; behavior unchanged below API 37; unit-tested via compat seam.
  Complexity: S

- [ ] P3 — Validate Swift Backup imports via the zip-comment manifest
  Why: SBConverter ignores the format's own embedded JSON manifest, so corrupt or mismatched imports fail late instead of at selection; fixing it also revives the disabled converter test.
  Evidence: backup/convert/SBConverter.java:243 (TODO: parse zip comment); app/src/test/.../SBConverterTest.java:158 (test commented out).
  Touches: SBConverter, converter tests with parser-level fixtures.
  Acceptance: malformed or package-mismatched zip-comment JSON is rejected with a clear error before restore begins; valid imports unchanged; the disabled test is re-enabled or replaced at the parser level.
  Complexity: S

- [ ] P3 — Drop Pithus from the bundled user manual
  Why: the scanner integration was removed in code, but the manual shipped as a raw resource still describes fetching Pithus reports, in English and eleven translations.
  Evidence: docs/raw/en/pages/scanner-page.tex, settings-page.tex, app-details-page.tex, docs/raw/en/strings.xml, and the generated docs/raw/<lang>/index.html for each locale.
  Touches: docs/raw/**, docs/src/main/res/raw*/index.html (generated output must be regenerated, not hand-edited).
  Acceptance: no locale of the shipped manual describes Pithus as a current feature; the historical changelog appendix entry stays as-is because it records what a past release did; the docs module builds.
  Complexity: M

- [ ] P3 — Map AID numbers to names in file properties
  Why: the file manager shows raw numeric AIDs where android_filesystem_config.h names exist — small readability/trust win for the audience this app serves.
  Evidence: fm/dialogs/FilePropertiesDialogFragment.java:584 (TODO).
  Touches: fm/dialogs, a static AID table.
  Acceptance: known AIDs render as "name (uid)", unknown values fall back to the number; unit test covers both.
  Complexity: S

- [ ] P3 — Per-folder file-manager view preferences
  Why: FmListOptions declares OPTIONS_ONLY_FOR_THIS_FOLDER but the flag does nothing, so per-folder sort/hidden-files choices are silently global.
  Evidence: fm/FmListOptions.java:35 (declared, unimplemented).
  Touches: fm/ list options, per-path preference storage.
  Acceptance: enabling "only for this folder" persists sort/filter for that path and restores it on return; global default unaffected; unit test.
  Complexity: S

- [ ] P3 — Ship code-editor language definitions as assets
  Why: language definitions are built at runtime with a serializer the code itself calls stale — startup cost and drift risk flagged in-code.
  Evidence: editor/CodeEditorViewModel.java:68 and :219 (TODOs).
  Touches: editor/, packaged assets, build script step.
  Acceptance: language defs load from packaged assets; the stale serializer path is gone; highlighting output unchanged against a fixture file.
  Complexity: M

## Research-Driven Additions (2026-08-11)

### P0

- [ ] P0 — Break the release gate's circular dependency on the published-release receipt
  Why: the consistency stage asserts that `docs/distribution/release-receipt.json` — a record of the *last published* release, carrying the published artifact's size, sha256 and download URL — equals the working tree's versionName/versionCode, which cannot be true until after that artifact exists. So the gate fails closed for every new version and blocks its own release. `consistency` is stage 2 of 8, so nothing downstream runs either: tests, lint, the two-build reproducibility check, and the dependency CVE scan (which lives inside `verify_reproducible_release`, stage 7) are all unreachable on the gate path while it fails. v0.6.8, v0.6.9, v0.6.10, v0.6.11 and v0.6.13 have CHANGELOG entries with dates but no git tag and no GitHub release; only v0.6.12 was ever published. If the intended workflow really is publish-then-sync-the-receipt, the gate must say so rather than failing on the ordinary path.
  Evidence: live run of `scripts/verify-release-consistency.sh` at HEAD emits `versionName 0.6.13 != receipt 0.6.12`, `versionCode 21 != receipt 20`, `FAILED`; scripts/verify-release-consistency.sh:146-165; scripts/verify_release_metadata.py; scripts/release_gate.py:685 (`ALL_STAGES`); scripts/verify_reproducible_release.sh:117 (CVE gate invocation); `git tag` (v0.6.12, v0.6.7, v0.6.5, v0.6.1, v0.5.0, v0.4.2); `gh release list` (latest = v0.6.12); receipt git history (synced only on publish days 2026-07-29 and 2026-08-08).
  Touches: scripts/verify_release_metadata.py (separate "the receipt describes a published release" from "the tree is ready to release" — assert the receipt is internally consistent and that the tree version is >= the receipt version, and require equality only when HEAD carries a matching tag), scripts/verify-release-consistency.sh, scripts/release_gate.py (consistency stage), docs/distribution/reproducible-builds.md and the three listing packets that describe the receipt contract.
  Acceptance: the consistency script passes on an untagged working tree whose version is ahead of the receipt, and still fails when the tree version is behind the receipt, when a tag disagrees with versionName, or when a listing packet disagrees with the receipt; the release gate runs end to end on a version that has not yet been published; a regression test covers the ahead / equal / behind / tagged cases. Publishing the outstanding versions is a separate maintainer action, not part of this row.
  Complexity: M

### P1

### P2

- [ ] P2 — Support the current Neo Backup on-disk format in the importer
  Why: OABConverter parses `<packageName>.log` as JSON with `lastBackupMillis` — the legacy OAndBackupX layout. Current Neo Backup writes `backup.properties` inside a `YYYY-MM-DD-HH-MM-SS[-mmm]-user_N` directory per backup instance, so present-day Neo Backup archives do not import at all. Neo Backup has been unpushed since 2026-05-03 with 239 open issues and its users are actively looking for an exit.
  Evidence: backup/convert/OABConverter.java:197-215; Neo-Backup Constants.kt (BACKUP_INSTANCE_PROPERTIES_INDIR = "backup.$PROP_NAME", BACKUP_INSTANCE_REGEX_PATTERN, LOG_INSTANCE = "%s.log.txt"); upstream MuntashirAkon/AppManager#2020 (2026-08-06, "add the option to import backup from Neo Backup").
  Touches: backup/convert/OABConverter.java (detect and branch on layout rather than replacing the legacy path), backup/convert/ConvertUtils.java, backup/convert/ImportType.java and its strings if the picker should name the format, converter tests with fixtures for both layouts.
  Acceptance: a fixture directory in each layout imports to the same NG backup metadata; a directory in neither layout is rejected with a message naming what was expected, not a generic failure; legacy imports are byte-for-byte unchanged; parser-level unit tests cover both plus the malformed case.
  Complexity: M

- [ ] P2 — Get the no-root accessibility path off its own main thread
  Why: onAccessibilityEvent runs on the service main thread and sleeps in it — one second directly, and up to five seconds through waitUntilEnabled's ten 500 ms iterations. A blocked AccessibilityService stops delivering events and can be dropped by the system, and this is the no-root force-stop / clear-data automation path. Lint cannot see it: ThreadConstraint is suppressed 69 times in app/lint-baseline.xml.
  Evidence: accessibility/NoRootAccessibilityService.java:36-38 (handler), :73 SystemClock.sleep(1000), :147; accessibility/BaseAccessibilityService.java:73,81,89 and :242-248.
  Touches: accessibility/NoRootAccessibilityService.java, accessibility/BaseAccessibilityService.java (move the wait/retry sequence onto a background executor and drive the UI actions back through the service; keep a bounded overall timeout), the multiplexer that owns the operation state.
  Acceptance: no sleep remains on any path reachable from onAccessibilityEvent; the force-stop and clear-data sequences still complete on a device within the same overall timeout budget; a unit test over the extracted sequencing logic covers the enabled-immediately, enabled-late, and never-enabled cases.
  Complexity: M

- [ ] P2 — Make BUILD_TIME_MILLIS deterministic or fail loudly
  Why: `buildTime()` shells out to `git show --no-patch --format=%ct000` and, when that yields anything non-numeric, falls back to System.currentTimeMillis() with only a println. A build from a source tarball or a shallow/exported tree therefore bakes wall-clock time into BuildConfig and can never be byte-reproduced, while the two-build gate — which runs twice in the same git tree — sees nothing. Upstream's reproducibility was publicly written off by IzzySoft over a related non-determinism (#1997), so this is an axis NG can win rather than merely match.
  Evidence: app/build.gradle:336-343; docs/distribution/reproducible-builds.md; upstream MuntashirAkon/AppManager#1997.
  Touches: app/build.gradle (honour SOURCE_DATE_EPOCH first, then git commit time, and fail the build for release variants when neither is available unless an explicit opt-out property is set), docs/distribution/reproducible-builds.md, scripts/verify_reproducible_release.sh and .ps1.
  Acceptance: a release build outside a git tree and without SOURCE_DATE_EPOCH fails with a message naming the reason instead of embedding wall-clock time; with either source present the emitted BUILD_TIME_MILLIS is identical across two builds; debug builds are unaffected.
  Complexity: S

- [ ] P2 — Prove am.jar and main.jar are reproducible across environments, not just across runs
  Why: the release gate builds twice in the same tree, same paths, same locale and timezone, so it cannot detect the class of non-determinism that actually breaks third-party rebuilds — and that is precisely where upstream failed: its assets/am.jar and assets/main.jar differ in size and hash between maintainer and rebuilder (19730 vs 19114 bytes), unresolved as of 2026-07-02. NG builds the same two jars through its own d8 step, so it carries the same risk without evidence either way.
  Evidence: upstream MuntashirAkon/AppManager#1997; server/build.gradle d8 invocation with a filename-prefix class split (ServerUtils, RootServiceMain, IRootServiceManager); docs/distribution/reproducible-builds.md.
  Touches: server/build.gradle (sort d8 inputs deterministically, normalise entry timestamps and ordering in the produced jars), scripts/verify_reproducible_release.sh and .ps1 (second build from a different absolute path with a different TZ, LC_ALL and user, comparing the two jars explicitly and by name in the report).
  Acceptance: two builds from different absolute paths, timezones and locales produce byte-identical am.jar and main.jar; the verifier reports those two hashes separately so a future drift names the jar; a deliberately reordered input set still produces the same jar.
  Complexity: M

- [ ] P2 — Developer-verification-aware install diagnostics
  Why: enforcement begins 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, and the installer-facing APIs already exist at API 36.1 — PackageInstaller.getDeveloperVerificationServiceProvider(), EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, EXTRA_DEVELOPER_VERIFICATION_LITE_PERFORMED, DEVELOPER_VERIFICATION_FAILED_REASON_{UNKNOWN,NETWORK_UNAVAILABLE,DEVELOPER_BLOCKED}, SessionParams.setExtensionParams. Without this an install the platform refuses for verification reasons fails the same way as any other failure, and NG's whole installer proposition is disclosure. No competitor has shipped this. Distinct from the existing P3 Advanced Protection row, which covers a different gate.
  Evidence: developer.android.com PackageInstaller reference (36.1 additions); Android 16 QPR2 release notes; developer.android.com/developer-verification/guides (2026-09-30 enforcement, ADB installs exempt); the shipped restricted-settings detector as precedent.
  Touches: apk/installer/ preflight and status handling, a compat seam for the 36.1 extras, details/info/ sideload diagnostics, strings.
  Acceptance: a session failure carrying a developer-verification reason renders that reason and what the user can do about it, distinct from a generic install failure; below API 36.1 behaviour is unchanged; each failure-reason constant is covered by a unit test through the compat seam. Whether to raise targetSdk past 36 is explicitly not part of this row.
  Complexity: M

### P3

- [ ] P3 — Give SimpleArrayMapDiffCallback a real content comparison
  Why: areContentsTheSame returns false unconditionally, so every DiffUtil pass reports every surviving row as changed and dispatches a payload rebind for the entire visible set. 82 call sites go through AdapterUtils. Related to but distinct from the blocked INIT-D1 main-list ListAdapter migration, which covers the main list only and is device-gated; this is the shared utility and is host-testable.
  Evidence: libcore/ui/src/main/java/io/github/muntashirakon/util/AdapterUtils.java:107-116.
  Touches: libcore/ui/.../util/AdapterUtils.java (compare values via Objects.equals, keeping the payload path for callers that rely on partial rebind), the :app callers that assume every notify is a change. :libcore:ui has no test source set and adding one breaks dependency locking — cover it from app/src/test.
  Acceptance: a diff between two identical maps dispatches no updates; a diff with one changed value dispatches exactly one change; existing list screens still repaint correctly after a refresh with no visible regression.
  Complexity: M

- [ ] P3 — Make backup-scoped Finder predicates answer from backup metadata instead of hard-coded false
  Why: BackupFilterableAppInfo returns false from isInstalled(), isFrozen() and backupAllowed(), 0 from getFreezeFlags(), and null from fetchSignerInfo(), so any Finder query run over backups that touches those axes silently matches nothing — indistinguishable from a genuinely empty result.
  Evidence: filters/BackupFilterableAppInfo.java:78-126; constructed at filters/FilteringUtils.java:145.
  Touches: filters/BackupFilterableAppInfo.java (answer isInstalled/isFrozen from the current package state for the backup's package where that is knowable, and derive the rest from Backup metadata), filters/FilteringUtils.java, or — where an axis genuinely cannot be answered for a backup — exclude that predicate from the backup filter surface so it cannot be selected.
  Acceptance: every predicate offered in the backup filter context is either answered from real data or not offered; a fixture backup whose package is installed matches an "installed" query; unit tests cover each of the five predicates.
  Complexity: M

- [ ] P3 — Surface the static binary signals LibChecker reports and NG does not
  Why: LibChecker 2.5.4 reports the exact ZIP alignment value for libraries that are 16 KB page-aligned but not 16 KB ZIP-aligned, and detects a stripped symbol table. Both are computable from the APK with the ELF and ZIP parsing NG already has, and both are exactly the "state what this is evidence of" signal the fork's scanner philosophy asks for. Distinct from the blocked LibChecker-parity row, which covers Modern Xposed API, live-update capability and themed-icon/alias.
  Evidence: LibChecker 2.5.4 release notes; scanner/NativeLibraries.java (PT_LOAD.p_align parsing already present); scripts/verify-native-page-alignment.py (local-file-header offset check already present).
  Touches: scanner/NativeLibraries.java, App Details libraries tab, strings; optionally extend scripts/verify-native-page-alignment.py to assert GNU_RELRO is present, which the official 16 KB requirement lists and the script does not currently check.
  Acceptance: each .so row shows its ZIP alignment when it differs from the page alignment, and whether its symbol table is stripped; both are computed without extracting the APK to disk; unit tests use fixture ELF headers for stripped, unstripped, aligned and misaligned cases.
  Complexity: M

- [ ] P3 — Replace the swallowed failures on destructive and trust paths
  Why: 179 empty catch blocks remain in main sources and the ones that matter sit where a silent failure is indistinguishable from success. self/SelfPermissions.java:52,61 is the worst shape — a swallowed exception there makes every permission self-check answer "no", which reads as a capability the app does not have. The 2026-06 catch(Throwable) narrowing pass did not reach these.
  Evidence: apk/installer/PackageInstallerCompat.java:1516; batchops/BatchOpsService.java:240,250; backup/RestoreOp.java:176,422; backup/BackupOp.java:621; crypto/ks/KeyStoreManager.java:280; rules/compontents/ComponentsBlocker.java:613; settings/Ops.java:790,1057; self/SelfPermissions.java:52,61.
  Touches: the ten sites above only — this is a scoped pass, not a repo-wide sweep. Each becomes either a logged failure carrying the operation identity, or a documented deliberate ignore with the reason in a comment (the pattern logs/FLog.java:226 and NetworkRequestLedger.java:53 already use correctly).
  Acceptance: none of the ten sites discards a throwable without either logging it with enough context to identify the operation, or carrying a comment stating why discarding is correct; no behaviour change on the success paths; the destructive paths surface the failure through the mechanism their caller already has.
  Complexity: M

- [ ] P3 — Move the remaining hardcoded English UI strings into resources
  Why: thirteen user-visible English strings are built in Java, so they never translate — seven "Error: " toasts plus two permission messages in App Details, and two file-manager default names. That is small enough to close completely, and the translation ratchet cannot see strings that never reach strings.xml.
  Evidence: details/info/AppInfoFragment.java:536,610,658,1048,1215,2870,2939,3111,3363; fm/dialogs/NewFolderDialogFragment.java:51; fm/dialogs/NewSymbolicLinkDialogFragment.java:83.
  Touches: those three files, app/src/main/res/values/strings.xml. The two format-only strings in intercept/ActivityInterceptor.java:331,1119 are punctuation templates and should stay.
  Acceptance: no user-visible literal string is passed to a toast, dialog or setText in those three files; the new resources appear in the translation baseline; `:app:lint` reports no new HardcodedText.
  Complexity: S
