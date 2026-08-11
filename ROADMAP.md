# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

- [ ] P1 — Port the upstream v4.1.0 bugfix run (audited cherry-pick)
  Why: upstream shipped ~15 small correctness fixes between 2026-05-25 and 2026-06-29 that post-date the fork point and touch code the fork retains; none are recorded as ported.
  Evidence: MuntashirAkon/AppManager commits 329b8dc1 (debloater: uninstalled system apps listing), 916eeb85 (inactive-app check for non-default users), daa54ac0 (filter-profile custom expression), 4a25c3f0 (intent resolution via `am start -d`), 706c36fb (APKS compile), ab2b17fe (main IME logic), 3bf97856 + 184df334 (NPEs in path parsing / AppDb#findUsage), 0d1be565 (editor symbols cropped at large font), 4d3da96b (Finder/Debloater nav buttons), ca038d66 (single/multi-choice dialogs), f8d31264 (force-create external cache dir in root mode), 936cb302 (log viewer scroll/filter — reconcile with the fork's own 2026-07-02 log-viewer fixes first). Skip the M3 preference restyle and locale commits (RESEARCH.md, Rejected Ideas).
  Touches: per-commit — debloat/, main/, profiles/, apk/, editor/, logcat/, fm/, io path utils.
  Acceptance: every listed commit is either ported with a regression test or recorded not-applicable with a one-line reason in the commit message; full host suite passes.
  Complexity: M

- [ ] P1 — Audit and port the upstream Android 16 hidden-API refresh
  Why: hidden-API mirrors drift against real OS internals; upstream refreshed them from Android 16 sources after the fork point and the fork's hiddenapi module has not taken the refresh — a latent-correctness risk on the OS most users now run.
  Evidence: upstream commits eff7f587 + 04ed88d0 (2026-05-25/27, "Update hidden API from Android 16"); no corresponding fork changes in hiddenapi/ (2026-08-10 scan).
  Touches: hiddenapi/, app compat layer where signatures changed.
  Acceptance: upstream diff reviewed; applicable signature/field changes merged; all modules compile; existing compat contract tests pass; divergences deliberately kept are listed in the commit message.
  Complexity: M

### P2

- [ ] P2 — Remove the defunct Pithus integration
  Why: upstream deleted Pithus and its pinned certificates as a defunct service on 2026-05-26; the full flavor still offers uploads to the dead endpoint — a dead network trust surface.
  Evidence: upstream commits 0e187e83 + 2c00f69f; app/src/main/java/.../scanner/Pithus.java, ScannerViewModel.java, ScannerFragment.java, settings/NetworkTransparencyLedger.java, NetworkRequestLedger.java, fragment_scanner.xml, locale strings (grep 2026-08-10).
  Touches: scanner/, settings ledger classes, strings across locales, fragment_scanner.xml, docs mentioning Pithus (README flavor table).
  Acceptance: no Pithus code, strings, or ledger rows remain; floss scanner UI unchanged; full-flavor scanner shows no Pithus action; probe result for beta.pithus.org recorded in the commit message; translation ratchet stays green after string removal.
  Complexity: M

- [ ] P2 — Disclose the KeyStore backup capability hole
  Why: backups silently skip Android KeyStore v2 entries, so users believe backups are complete when a class of credentials is absent — a truth gap in the app's core trust feature.
  Evidence: backup/BackupOp.java:531 and backup/RestoreOp.java:538 (TODO: "KeyStore v2 unsupported"); no disclosure in backup dialogs or metadata.
  Touches: backup/dialog/, BackupOp/RestoreOp, backup metadata/report, strings.
  Acceptance: when a package has KeyStore entries the backup cannot carry, the backup dialog and the stored metadata/report both say so; restore of such a backup repeats the note; unit tests cover flagged and unflagged paths.
  Complexity: S

- [ ] P2 — Finder AppOps mode predicate
  Why: the data layer already exposes per-op modes and AppOpsOption declares mode-flag constants, but Finder ships v1 without allowed/ignored/foreground filtering — a finished data layer with no UI, the exact gap class the 2026-08-02 pass closed for presets.
  Evidence: filters/options/AppOpsOption.java:26-29 (declared, unused MODE_FLAG_* constants); IFilterableAppInfo#getAppOps() exposes getMode().
  Touches: AppOpsOption, Finder option UI, filter serialization, tests.
  Acceptance: a query "apps with op X in mode Y" returns correct results under unit test; the option round-trips through saved presets; UI exposes mode selection only when an op is chosen.
  Complexity: S

- [ ] P2 — Readback verification for privileged batch operations
  Why: `pm`/`appops` can return success without doing the work on OEM builds; batch results currently trust exit codes, so a silent no-op reads as success — the field's live failure mode, demonstrated and fixed by Thor's readback pattern.
  Evidence: Thor v1.94.0 release notes (readback verification, 2026-08-05); NG batchops result layer reports command success only.
  Touches: batchops executors (freeze, disable/enable, uninstall, force-stop, app-op set, permission grant/revoke), batch results model, BatchOpsResultsActivity strings.
  Acceptance: after each mutating batch op the executor re-reads the relevant state (component/enabled/appop/permission) and results distinguish verified / unverified (state unreadable) / failed (state contradicts); a unit test simulating a silent no-op sees it reported as failed, not success; readback degrades to "unverified", never blocks.
  Complexity: M

- [ ] P2 — Show update ownership in App Details and offer claiming it on install
  Why: API 34+ update-ownership records which installer owns an app's updates and blocks silent takeover; NG neither surfaces nor claims it, though it is directly relevant as Developer Verification reshapes install trust (NG's ADB/Shizuku path stays exempt).
  Evidence: zero getUpdateOwnerPackageName / requestUserPreapproval references in app/src/main (grep 2026-08-10); InstallSourceInfo/PackageInstaller update-ownership APIs (Android 14+); RESEARCH.md platform section.
  Touches: details/info AppInfoFragment + ViewModel (owner row), installer options + PackageInstallerCompat (opt-in claim flag on session), strings.
  Acceptance: App Details shows the update owner when set (API 34+, hidden below); install options expose an off-by-default "claim update ownership" toggle wired to the session param; Robolectric tests cover display and flag plumbing.
  Complexity: M

- [ ] P2 — Fix FEAT_INTERNET missing from the feature-toggle map
  Why: bit 6 gates VirusTotal reachability but is absent from sFeatureFlagsMap, so the master Internet feature can never be listed or toggled from the features UI even in the full flavor — a gate enumerated by hand that drifted from its flag set.
  Evidence: settings/FeatureController.java (sFeatureFlagsMap lacks FEAT_INTERNET; isInternetEnabled() consumes it); repo gotcha "gate that enumerates what it guards".
  Touches: FeatureController, feature-toggle UI, tests.
  Acceptance: full flavor lists the Internet toggle; floss behavior unchanged (compiled out); a unit test asserts the map covers every declared FEAT_* flag so the next added flag cannot silently vanish.
  Complexity: S

- [ ] P2 — Propagate privileged-server status changes to the UI
  Why: ServerStatusChangeReceiver drops status updates (in-code TODO), so the privileged-mode indicator can show a dead server as alive — trust-surface staleness in the mode users depend on before destructive ops.
  Evidence: servermanager/ServerStatusChangeReceiver.java:72.
  Touches: servermanager/, the mode/status surface (Ops/Mode Doctor), a LiveData/observable path.
  Acceptance: a server start/stop/crash updates an observable the status UI consumes; unit test drives the receiver and asserts propagation; no polling added.
  Complexity: S

- [ ] P2 — Toolchain security pass: Kotlin 2.4.20, dependency-check 13.0.0, AGP 9.3.0
  Why: the Kotlin toolchain sits on a CVE'd line (CVE-2026-53914, build-cache deserialization — build-only today, supply-chain-relevant if a shared cache ever appears); bundling the three bumps pays the dependency-locking/verification churn once, and AGP 9.3's keepRules source sets structurally mitigate the v0.6.12 BC-keeps class of R8 bug.
  Evidence: GHSA-r937-wjx7-w2jp; dependency-check 13.0.0 (2026-08-03) release notes; AGP 9.3.0 release notes; docs/distribution/dependency-verification.md (22-run churn cost precedent).
  Touches: versions.gradle, buildscript-gradle.lockfile + module lockfiles, gradle/verification-metadata.xml, config/owasp-suppressions.xml (drop the CVE-2026-53914 rule post-upgrade — the gate flags unused rules), optionally migrate BC keeps into src/*/keepRules/; check for LSPosed HiddenApiBypass 6.2+ while touching pins (AppManager.java:121).
  Acceptance: build + full host suite green; locking/verification refreshed per the documented procedure (publisher checksums, not local cache); release gate incl. CVE stage passes; the Kotlin suppression is removed and the gate stays green.
  Complexity: M

- [ ] P2 — Pre-targetSdk-37 compliance audit: reflective static-final writes and IntentSender BAL
  Why: Android 17 makes reflective writes to static-final fields throw and extends background-activity-launch hardening to IntentSenders; auditing now de-risks the parked target-SDK bump with purely host-verifiable work.
  Evidence: developer.android.com/about/versions/17/behavior-changes-17; hiddenapi/Refine reflection helpers; installer confirmation IntentSender flows; Roadmap_Blocked.md "Android 17 target-SDK gate" (parked).
  Touches: static scan across hiddenapi/ and compat/ for Field.set on static finals; installer/batch IntentSender launch sites; docs/audits/ (dated audit doc, same pattern as 2026-05-02 audits).
  Acceptance: audit doc lists every reflective static-final write (fixed or justified) and every IntentSender launch site with its BAL opt-in status; any fixes carry tests; doc committed under docs/audits/2026-XX-XX-android17-target-prep.md.
  Complexity: S

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
