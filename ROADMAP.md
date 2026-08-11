# ROADMAP

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

### P2

- [ ] P2 — Readback verification for privileged batch operations
  Why: `pm`/`appops` can return success without doing the work on OEM builds; batch results currently trust exit codes, so a silent no-op reads as success — the field's live failure mode, demonstrated and fixed by Thor's readback pattern.
  Evidence: Thor v1.94.0 release notes (readback verification, 2026-08-05); NG batchops result layer reports command success only.
  Touches: batchops executors (freeze, disable/enable, uninstall, force-stop, app-op set, permission grant/revoke), batch results model, BatchOpsResultsActivity strings.
  Acceptance: after each mutating batch op the executor re-reads the relevant state (component/enabled/appop/permission) and results distinguish verified / unverified (state unreadable) / failed (state contradicts); a unit test simulating a silent no-op sees it reported as failed, not success; readback degrades to "unverified", never blocks.
  Complexity: M

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

- [ ] P2 — Toolchain security pass: Kotlin 2.4.20, dependency-check 13.0.0, AGP 9.3.0
  Why: the Kotlin toolchain sits on a CVE'd line (CVE-2026-53914, build-cache deserialization — build-only today, supply-chain-relevant if a shared cache ever appears); bundling the three bumps pays the dependency-locking/verification churn once, and AGP 9.3's keepRules source sets structurally mitigate the v0.6.12 BC-keeps class of R8 bug.
  Evidence: GHSA-r937-wjx7-w2jp; dependency-check 13.0.0 (2026-08-03) release notes; AGP 9.3.0 release notes; docs/distribution/dependency-verification.md (22-run churn cost precedent).
  Touches: versions.gradle, buildscript-gradle.lockfile + module lockfiles, gradle/verification-metadata.xml, config/owasp-suppressions.xml (drop the CVE-2026-53914 rule post-upgrade — the gate flags unused rules), optionally migrate BC keeps into src/*/keepRules/; check for LSPosed HiddenApiBypass 6.2+ while touching pins (AppManager.java:121).
  Acceptance: build + full host suite green; locking/verification refreshed per the documented procedure (publisher checksums, not local cache); release gate incl. CVE stage passes; the Kotlin suppression is removed and the gate stays green.
  Complexity: M

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
