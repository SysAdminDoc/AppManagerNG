<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Research-Driven Additions

### P0

- [ ] P0 — Audit bundled jadx 1.4.7 line for CVE-2024-32653 and remediate
  Why: CVSS-9 command-injection fixed in upstream jadx 1.5.0; NG feeds untrusted APKs into the bundled MuntashirAkon/jadx-android 1.4.7 decompiler; applicability to the Android fork's code path is unverified either way.
  Evidence: https://www.tenable.com/cve/CVE-2024-32653 ; versions.gradle:35 ; dex/DexClasses.java, scanner/
  Touches: versions.gradle, app/src/main/java/io/github/muntashirakon/AppManager/dex/, docs/audits/ (new dated audit doc per doctrine)
  Acceptance: dated audit doc with verdict (clean/remediated/deferred); if affected, jadx-android bumped or patched and a hostile-package-name APK decompiles without command execution.
  Complexity: S (audit) → M (if remediation needed)

- [ ] P0 — Developer Verification install-failure handling + ADB escape-hatch UX
  Why: Enforcement starts 2026-09-30 (BR/ID/SG/TH); the platform verifier intercedes in PackageInstallerSession.handleInstall() and NG's session installs will surface new failure statuses; ADB installs are exempt, and NG uniquely owns a wireless-ADB mode it can offer as the documented fallback.
  Evidence: https://developer.android.com/developer-verification/guides/faq ; https://developer.android.com/about/versions/16/qpr2/release-notes ; agnostic-apollo gist (RESEARCH.md Sources); docs/sideload-verification.md (predates QPR2 APIs)
  Touches: apk/installer/ (PackageInstallerActivity, PackageInstallerService, InstallerPrivilegeCascade.java), docs/sideload-verification.md
  Acceptance: a verifier-blocked install shows a specific explanation (not a generic failure) with a one-tap path to retry via ADB mode when available; QPR2 force-verification adb test command produces the new dialog in an emulator run.
  Complexity: M

- [ ] P0 — Port upstream Android 16 hidden-API refresh
  Why: Upstream refreshed the hidden-API surface for Android 16 after the fork pin (commits eff7f58 + 04ed88d, 2026-05-25/27); NG's privileged core (hiddenapi/ stubs + compat wrappers) predates it and the project's own audit doctrine calls this the "80-hour cliff" to amortize.
  Evidence: https://github.com/MuntashirAkon/AppManager/commits/master ; docs/architecture/README.md (doc 03); docs/audits/README.md
  Touches: hiddenapi/src/main/java/**, app/src/main/java/io/github/muntashirakon/AppManager/compat/
  Acceptance: upstream A16 hidden-API commits cherry-picked/adapted; privileged ops (app-ops edit, freeze, net policy) verified on an Android 16 emulator in CI; doc 03 updated with the audit verdict.
  Complexity: M

- [ ] P0 — Android 17 behavior-change audit batch (API 37)
  Why: A17 stable is imminent (Beta 4.1 2026-06-01); targetSdk-37 changes hit NG's core mechanics: static-final fields unmodifiable via reflection (hidden-API bypass stack), lock-free MessageQueue (reflection into privates breaks), ACCESS_LOCAL_NETWORK runtime permission (wireless-ADB mDNS discovery), cleartext-attribute deprecation (localhost carve-out).
  Evidence: https://developer.android.com/about/versions/17/behavior-changes-17 ; https://developer.android.com/about/versions/17/behavior-changes-all
  Touches: docs/audits/ (one dated audit per change), hiddenapi/, libserver/, adb/, app/src/main/res/xml/network_security_config.xml
  Acceptance: four dated audit docs with verdicts per docs/audits doctrine; confirmed-needs-fix findings get their own rows; app runs its privileged paths on an A17 emulator (android17-emulator.yml) without regressions.
  Complexity: M

### P1

- [ ] P1 — Post-backup verification pass
  Why: The category's #1 verified user pain is discovering corrupt/unrestorable backups at restore time; verifying at backup time (per-file checksums against the metadata manifest + test-extract of archive headers) converts silent failure into immediate, actionable failure.
  Evidence: XDA thread 4783472 (AM/Swift/Neo all failed restore); GrapheneOS forum 22082 (Seedvault distrust); RESEARCH.md §Security
  Touches: backup/BackupManager.java, backup/BackupOp.java, backup/MetadataManager, backup/dialog/ (result surfacing)
  Acceptance: every backup ends with a verify step whose result (verified / N files failed) is recorded in the backup metadata and shown in the backup list UI; a deliberately truncated archive is flagged at backup time in a unit test.
  Complexity: M

- [ ] P1 — Backup/restore round-trip integration tests in emulator CI
  Why: The backup engine has the repo's highest debt concentration (10+ TODOs), zero integration coverage, and is the subsystem users distrust most; the android17-emulator.yml workflow already exists to ride on.
  Evidence: RESEARCH.md §Architecture (test gaps); backup/adb/AndroidBackupHeader.java:375 FIXME; .github/workflows/android17-emulator.yml
  Touches: app/src/androidTest/ (new backup round-trip suite), .github/workflows/android17-emulator.yml
  Acceptance: CI installs a fixture app, backs up (no-crypto + AES), uninstalls, restores, and asserts data equality; suite runs on every PR touching backup/.
  Complexity: M

- [ ] P1 — Port upstream restore fixes from the v4.1.0 milestone
  Why: Upstream closed 39 issues for v4.1.0 (due 2026-06-21) including #1286 (non-root restore SecurityException on Samsung/A14 — "package com.google.android.packageinstaller does not belong to 10053"); NG's restore path predates these fixes.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1286 ; upstream commits since 3d11bcb (RESEARCH.md §Competitive)
  Touches: backup/RestoreOp.java, apk/installer/, compat/PackageManagerCompat.java
  Acceptance: the #1286 reproduction (non-root restore with a session-based installer on API 34) succeeds; ported commits listed in CHANGELOG with upstream attribution.
  Complexity: M

- [ ] P1 — Pause target app during backup (suspend/SIGSTOP) for data consistency
  Why: Live app writes during backup produce silently inconsistent archives; Neo Backup pauses via `pm suspend`/`kill -STOP` and resumes after — a small change that removes a whole class of corrupt backups.
  Evidence: Neo Backup README/FAQ (RESEARCH.md §Competitive); backup/BackupOp.java has no suspend/STOP today (verified)
  Touches: backup/BackupOp.java, compat/PackageManagerCompat.java (suspend), runner/ (root SIGSTOP fallback)
  Acceptance: with root or privileged mode, the target app is suspended for the duration of data backup and resumed after (also on failure paths); behavior is a user-visible toggle defaulting on for privileged modes.
  Complexity: S

- [ ] P1 — Port HMAC mutual auth + native run_server for the local privileged channel
  Why: Upstream hardened the app↔ADB-server channel with HMAC challenge-response and converted run_server to a native executable (fixing root mode broken since 3.0.0, #948, and reducing detectable service footprint); NG's channel lacks both.
  Evidence: upstream commits 88eb453, 07c7199, b42efbb, f8d3126 (RESEARCH.md §Competitive); grep: no HMAC in adb/ or libserver/ (verified)
  Touches: libserver/, server/, adb/, servermanager/
  Acceptance: server rejects unauthenticated connections (negative test); root mode works on a rooted A16 emulator; ported commits attributed.
  Complexity: M

- [ ] P1 — Debloat safety net: critical-package guard + pre-op snapshot + ADB rescue script
  Why: Bricking fear is the #1 adoption blocker for debloaters (documented OneUI bootloop from a debloat list; F-Droid forum recovery thread); NG's critical-package guard exists only in Permission Inspector recovery, not in Debloater/batch uninstall; Canta wins recommendations on undo alone.
  Evidence: https://gitlab.com/W1nst0n/universal-android-debloater/-/issues/43 ; forum.f-droid.org/t/29341 ; upstream #1161; permissions/PermissionRecovery.java (existing guard)
  Touches: debloat/, batchops/, oneclickops/, permissions/PermissionRecovery.java (extract shared guard), snapshot/SnapshotBundle.java (pre-op auto-export)
  Acceptance: batch uninstall/disable of a guarded package requires an explicit second confirmation naming the risk; every batch system-app operation auto-exports a snapshot + generates a plain-text `adb shell cmd package install-existing ...` rescue script in the backup volume.
  Complexity: M

- [ ] P1 — Scheduler self-heal: detect and fix AM's own battery-optimization restriction
  Why: RoutineScheduler and AutoBackup ride WorkManager, which OEM battery managers silently kill (the same root cause as upstream's pinned #1596 "ADB mode lost" on Samsung); SD Maid SE ships "auto-fix battery optimization via root or ADB" for exactly this.
  Evidence: SD Maid SE releases (RESEARCH.md §Competitive); https://github.com/MuntashirAkon/AppManager/issues/1596
  Touches: profiles/trigger/RoutineScheduler.java, profiles/trigger/RoutineDiagnostics.java, backup/AutoBackupDiagnostics, settings/ (health surface)
  Acceptance: when AM is battery-restricted, diagnostics show it and (in privileged modes) offer one-tap `deviceidle whitelist` self-exemption; restriction state is logged in trigger run results.
  Complexity: S

- [ ] P1 — Port upstream main-list performance/correctness batch
  Why: Upstream fixed the "app list loads forever" class (#1982 remains its last v4.1.0 blocker) with a search debouncer, ListAdapter migration, scroll-position restore, IME fixes, and filter-highlight fixes — all post-pin and directly applicable to NG's main list.
  Evidence: upstream commits bba53eb, 8cf2c1e, 69b28cb, 5418038, 886ad90, ab2b17f (RESEARCH.md §Competitive)
  Touches: main/ (MainActivity, adapters, view models)
  Acceptance: typing in main-list search does not re-filter per keystroke (debounced); rotation/return preserves scroll position; ported commits attributed.
  Complexity: M

### P2

- [ ] P2 — Malformed-APK parser robustness pass
  Why: The Konfety malware wave deliberately ships APKs (bogus encryption flag, declared-unsupported compression, malformed string pools) that crash OSS parsers; NG users feed it exactly such hostile APKs via installer/scanner/manifest viewer.
  Evidence: Zimperium Konfety report (RESEARCH.md Sources); apk/, ARSCLib usage
  Touches: apk/ (ApkFile, splitapk), scanner/, details/ManifestViewer paths; app/src/test/ corpus
  Acceptance: a regression corpus of malformed APKs (Konfety-style tricks) parses to a graceful per-file error — no crash, no hang — enforced by unit tests.
  Complexity: M

- [ ] P2 — Pithus integration decision: verify service, then remove or keep
  Why: Upstream deleted its Pithus scanner + pinned certificates on 2026-05-26; if the service is dead, NG's full flavor ships a dead online feature with stale cert pins (scanner/Pithus.java, network_security_config.xml).
  Evidence: upstream commits 0e187e8 + 2c00f69; scanner/Pithus.java (verified present)
  Touches: scanner/Pithus.java, scanner/ScannerViewModel.java, scanner/ScannerFragment.java, res/xml/network_security_config.xml, settings/PrivacyPreferences.java
  Acceptance: dated audit doc records the service status check; integration removed (with cert pins) or kept with a recorded working-endpoint verdict.
  Complexity: S

- [ ] P2 — MDC 1.14 / minSdk-23 one-way-door decision
  Why: material-components-android entered maintenance mode (1.14.0 final feature release, requires minSdk 23; Views get critical fixes only) — NG must decide between minSdk 21 on a frozen 1.13 forever vs taking the terminal 1.14 (M3 Expressive) with a minSdk bump; the gating policy doc is referenced everywhere but absent on disk.
  Evidence: https://github.com/material-components/material-components-android/releases/tag/1.14.0 ; Compose-first blog (RESEARCH.md Sources); versions.gradle:39
  Touches: docs/policy/minsdk-21-ceiling.md (restore/extend on the machine holding it), versions.gradle (ledger comments)
  Acceptance: a dated decision section in the policy doc with the chosen path and the dependency-ledger consequences (activity/biometric/room/webkit lines move or stay in lockstep).
  Complexity: S

- [ ] P2 — App Change Auditor: component/tracker diffs + unified change feed
  Why: Change-over-time auditing is the ecosystem's 2025-26 innovation wave (Permission Pilot watcher, LibChecker snapshot diffs); NG already ships permission + signing-cert monitors (T9) — adding component/tracker diffing and one browsable feed makes NG first in its niche to unify install/update auditing.
  Evidence: permission/monitor/PermissionChangeMonitor.java (T9, verified); LibChecker snapshots, permission-pilot README (RESEARCH.md §Competitive)
  Touches: permission/monitor/ (new ComponentChangeMonitor/TrackerChangeMonitor + feed store), scanner/ (tracker sigs), main/ or settings/ (feed UI entry)
  Acceptance: updating a fixture app that adds a tracker + exported component produces a feed entry and notification listing both diffs; feed persists and is reachable from the main menu.
  Complexity: M

- [ ] P2 — Permission/app-op reference states (desired-vs-actual drift)
  Why: PermissionManagerX's reference-state model (pin desired value per permission/app-op, surface drift, restore references) is the only audit-grade permission pattern in the ecosystem and slots into NG's existing rule store + Permission Inspector.
  Evidence: https://github.com/mirfatif/PermissionManagerX (README, verified)
  Touches: rules/RulesStorageManager.java, rules/struct/, permissions/ (Inspector drift badges), details/AppDetailsPermissionsFragment
  Acceptance: user pins a reference for a permission/app-op; subsequent drift shows a visible indicator in Permission Inspector with one-tap restore-to-reference; references survive app reinstall via the rule store.
  Complexity: L

- [ ] P2 — Dhizuku freeze/suspend executor parity
  Why: Upstream permanently rejected Shizuku (issue #55, closed not_planned 2026-06-02) — rootless power is NG's structural lane; Hail proves device-owner delegation (Dhizuku) can freeze/suspend without root, and NG's DhizukuBridge currently feeds only the installer cascade + mode doctor.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/55 ; Hail README capability matrix; dhizuku/DhizukuBridge.java, apk/installer/InstallerPrivilegeCascade.java (verified)
  Touches: dhizuku/DhizukuBridge.java, batchops/, compat/ (freeze/suspend paths), settings/Ops.java
  Acceptance: with Dhizuku active and no root/Shizuku, freeze/unfreeze and suspend succeed from app details and batch ops; capability matrix in onboarding reflects it.
  Complexity: M

- [ ] P2 — Wireless-ADB resilience: trusted-network auto-reconnect + pairing-state surface
  Why: "ADB mode silently lost" is upstream's pinned unsolved bug (#1596, Samsung kills the server); Shizuku 13.6.0 already ships trusted-WLAN auto-restart and Android won't ship native auto-reconnect before QPR3/A17 (2027) — NG can close the gap now and own the most reliable on-device ADB mode.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1596 ; Shizuku 13.6.0 release notes; androidauthority wireless-adb-auto-reconnect (RESEARCH.md Sources); adb/ has no trusted-network logic (verified)
  Touches: adb/ (AdbPairingService, connection manager), servermanager/, settings/PrivilegeHealthPreferences.java, onboarding/
  Acceptance: on reconnecting to a user-designated trusted Wi-Fi, NG re-establishes its ADB connection unattended (Android 11+); pairing/connection state (paired, expired, server killed) is visible in Mode Doctor with recovery steps. Cross-check item "Android 17 audit batch" for ACCESS_LOCAL_NETWORK before targeting API 37.
  Complexity: L

- [ ] P2 — Fork-owned translation pipeline (Weblate) + NG-string catch-up
  Why: 44 inherited locales sit at 30-40% coverage and every NG-added string (Permission Inspector, onboarding, changelog viewer) is English-only; README says "Weblate (link TBD)" — the fork has no translation intake at all.
  Evidence: app/src/main/res/values-*/ counts (RESEARCH.md §Architecture); README.md:183
  Touches: .github/ (Weblate config/webhook), app/src/main/res/values-*/, CONTRIBUTING.md translation section
  Acceptance: a hosted Weblate (or equivalent) project is live and linked from README; at least the top-5 inherited locales receive NG-string components; CI accepts translation commits without manual XML fixes.
  Complexity: M

- [ ] P2 — SAF DocumentsProvider exposure of app-private directories (privileged)
  Why: Upstream's #516 (7 reactions) asks for third-party access to Android/data and app-private dirs via a documents provider when AM holds privilege; NG already ships AppManagerDocumentsProvider — extending it leapfrogs upstream's open request.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/516 ; fm/AppManagerDocumentsProvider (verified in manifest)
  Touches: fm/ (documents provider), ipc/ (privileged file streams), settings/ (opt-in toggle, default off)
  Acceptance: with the toggle on and privilege available, a third-party SAF file manager can browse/copy a test app's /data/data dir through NG's provider; toggle off = provider hides those roots.
  Complexity: M

- [ ] P2 — Per-app crash feed (system-wide crash monitor)
  Why: Upstream's accepted-but-unbuilt #163 (crash monitor with widget + viewer) and Inure's per-app battery/boot panels show demand for "what's wrong with this app" surfaces; NG already parses logcat and tracks ApplicationExitInfo-adjacent data in usage/.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/163 ; logcat/ package (verified)
  Touches: logcat/ (crash extraction), details/ (per-app crash tab/card), usage/ (exit info)
  Acceptance: app details shows recent crashes (timestamp + stack head) for the selected app sourced from ApplicationExitInfo (API 30+) and privileged logcat where available; empty state explains required privilege.
  Complexity: M

### P3

- [ ] P3 — APK Signature Scheme v3.2 (hybrid PQC) display/verify readiness
  Why: Android 17 introduces v3.2 hybrid post-quantum signing (classical + ML-DSA); NG's signature panels and apksig 4.4.0 line must at minimum not mislabel v3.2-signed APKs.
  Evidence: https://developer.android.com/about/versions/17/release-notes ; versions.gradle:21 (apksig 4.4.0)
  Touches: versions.gradle (apksig bump when available), details/ signature display, apk/signing/
  Acceptance: a v3.2-signed sample APK shows the correct scheme list (not "unknown"/crash); audit doc records the verdict if apksig upstream lags.
  Complexity: S

- [ ] P3 — AppVerifier-format share integration
  Why: NG's README already tells users to verify releases with AppVerifier; an in-app "share verification info" (package + SHA-256 cert hash in AppVerifier's expected text format) closes the loop for any installed app at S cost.
  Evidence: https://github.com/soupslurpr/AppVerifier (README format, verified); README.md:131
  Touches: details/info/AppInfoFragment.java (share action), utils/ (formatter)
  Acceptance: share sheet from an app's signature card produces text AppVerifier parses directly (package name + colon-separated SHA-256).
  Complexity: S

- [ ] P3 — APK export device-specificity labeling
  Why: Power users archive APKs ahead of verification enforcement and are burned by device-trimmed splits rendering wrong elsewhere — an XDA PSA exists solely to explain AM's extract vs Aurora's export; labeling exports (and warning on share) is cheap clarity.
  Evidence: XDA PSA thread 4784234 (RESEARCH.md Sources); apk/splitapk/SplitApkExporter.java
  Touches: apk/splitapk/SplitApkExporter.java, details/ share/export dialogs (string resources)
  Acceptance: export/share dialogs state which splits are included and that the set is device-specific; exported .apks filename or manifest notes the source device ABI/DPI.
  Complexity: S

- [ ] P3 — Per-app notes
  Why: Top-tier competitors (Inure, AppDash) and upstream request #1269 (tags + notes per app) treat notes as table stakes; NG shipped the tags data layer — notes is the missing sibling and feeds the planned tag UI.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/1269 ; Inure feature list; tags/AppTagStore.java (pattern to clone)
  Touches: tags/ (new AppNoteStore, same SharedPrefs-JSON pattern), details/info/ (note card), snapshot/SnapshotBundle.java (include notes)
  Acceptance: a note set on an app persists across restarts, appears in app details, round-trips through snapshot bundles, and is searchable from the main list.
  Complexity: S

- [ ] P3 — Backup protect-flag and per-backup notes
  Why: Swift Backup v5's deletion-locked backups + notes are the cheap half of its premium tier; NG's retention pruning (BackupRetentionPolicy) needs a protect-flag anyway so rotation never deletes a backup the user marked keep-forever.
  Evidence: Swift Backup feature/FAQ pages (RESEARCH.md §Competitive); backup/BackupRetentionPolicy.java (verified — prunes without protect concept)
  Touches: backup/ (metadata field), backup/BackupRetentionPolicy.java (skip protected), backup/dialog/ (UI flag + note)
  Acceptance: a protected backup survives retention pruning and bulk delete prompts; notes display in the backup list.
  Complexity: S

- [ ] P3 — IzzyOnDroid submission readiness audit
  Why: IzzyOnDroid is the natural first repo for an Obtainium-era app (release-key signing and reproducible builds already in place) but enforces ~30 MB per-app reservation and zero-tracker scans — size per ABI split is unmeasured.
  Evidence: https://izzyondroid.org/docs/general/AppInclusionPolicy/ ; release.yml ABI splits
  Touches: docs/distribution/ (submission notes), possibly app/build.gradle (resource shrinking if over budget)
  Acceptance: dated audit doc records per-ABI release APK sizes and policy-compliance checklist; submission filed or blockers listed.
  Complexity: S

- [ ] P3 — File-manager trash bin (staged deletion)
  Why: NG's FM hard-deletes; Files-by-Google's staged trash with 30-day retention is the established data-safety pattern and FM batch ops magnify mistake cost.
  Evidence: Files by Google clean-flow walkthrough (RESEARCH.md Sources); fm/ has no trash concept (verified)
  Touches: fm/ (delete paths, trash root, restore UI), settings/ (retention pref)
  Acceptance: FM delete moves to a trash location with restore; trash auto-empties after the configured retention; "delete permanently" remains available.
  Complexity: M

- [ ] P3 — D-pad/TV navigation pass + Android TV banner
  Why: Upstream #107 (keyboard/remote navigation, "Partly Fixed") plus SD Maid SE's Android TV launcher support show the box-tinkerer segment is real (FireOS/Firestick issues already appear upstream: #1835, #1854); NG's M3 dashboard was not audited for focus traversal.
  Evidence: https://github.com/MuntashirAkon/AppManager/issues/107 ; SD Maid SE releases (TV support); upstream #1835/#1854 (FireOS users)
  Touches: app/src/main/res/ (focus order, leanback banner, manifest LEANBACK feature flags), main/, details/
  Acceptance: main list → app details → batch ops are fully operable with a D-pad on an Android TV emulator; app appears in the TV launcher with a banner.
  Complexity: M
