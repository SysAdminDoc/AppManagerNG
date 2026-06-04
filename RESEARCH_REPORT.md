<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research Report

Consolidated on 2026-06-03 from the dated research notes previously scattered
under `docs/research/` and `research/`. The originals are preserved verbatim in
[`docs/archive/`](docs/archive/). This is the canonical research surface; the
active backlog it feeds is in [`ROADMAP.md`](ROADMAP.md), and shipped/rejected
outcomes are tracked in [`COMPLETED.md`](COMPLETED.md).

Candidate rows below are research findings, not commitments. Promote them into
`ROADMAP.md` only after they are scoped against current code; items already
rejected on license/privacy/scope grounds are recorded as STALE in
`COMPLETED.md`.

## Index

1. Android Power-Tool Competitive Research (2026-05-02)
2. Capability Extension — Datasets, Privacy Intelligence, Modern Android APIs (2026-05-09)
3. Roadmap Extension Phase 2 — Operational Layer (2026-05-09)
4. Observability, Telemetry & Testing Audit (2026-05-09)
5. iter-20 Research Delta — issues, competitors, Android 17, CVEs, deps (2026-04-15 to 2026-05-08)
6. iter-6 Delta — null-result research-cycle note (2026-05-01)
7. Deep-Research Pass — code-verified delta (2026-06-03)
8. Freshness Refresh — submodule and dependency delta (2026-06-04)
9. Research Refresh - upstream issue delta (2026-06-04 Cycle 2)
10. Research Refresh - mode and terminal delta (2026-06-04 Cycle 3)
11. Research Refresh - recovery and headset pairing delta (2026-06-04 Cycle 4)
12. Research Refresh - release trust and overlay/profile delta (2026-06-04 Cycle 5)

---

## 1. Android Power-Tool Competitive Research (2026-05-02)

*Source: docs/research/2026-05-02-android-power-tools.md*

Competitive survey vs. Canta, Universal Android Debloater, Hail, Inure, SD Maid
SE, Shelter, TrackerControl, Neo Backup, Permission Manager X, the Shizuku
ecosystem, Obtainium, and AppVerifier. Thesis: the differentiator is not
capability count but turning capability into clear jobs — "debloat safely",
"freeze distractions", "isolate risky apps", "prove who is tracking me", "clean
storage", "restore my phone", "verify this APK" — by making the app an
*operation cockpit*.

Twelve product bets (most now shipped or planned — see ROADMAP/COMPLETED):

1. Capability-first onboarding + per-operation preflight sheets (required
   capability / current state / setup action / risk / rollback; "Fix access"
   CTA instead of disabled buttons).
2. Safe Debloat Studio — UAD/Canta-style package metadata (recommendation,
   vendor, family, breakage, restore command, affected profiles), plan review,
   presets, dry-run, export/import, restore center.
3. App Trust & Risk dashboard — evidence chips (dangerous perms, sensitive
   AppOps, tracker SDKs/components, installer source, signing-cert continuity,
   backup freshness, FOSS signal, run-at-boot) + recommended actions.
4. Operation Journal & Rollback center — persist every privileged op
   (ts/actor-mode/target/command/before-after/rollback/result), filterable,
   "undo last op" when safe.
5. Automation as a public contract — intents/deep-links, Quick Settings tiles,
   launcher shortcuts, Tasker-compatible extras/result broadcasts.
6. Backup reliability upgrade — schedules with constraints, named sets/tags,
   retention, preflight storage estimate, integrity hashing, restore dry-run,
   rich completion reports, backup-freshness signal in the list.
7. Profile / multi-user awareness — package-presence matrix per user/profile,
   explicit scope selection on batch ops, per-user install/uninstall/freeze.
8. Permission/AppOps drift monitoring — snapshot + diff after package change,
   severity-thresholded notify, one-tap rollback preset.
9. Storage / leftover cleanup adjacent to app management — storage panel,
   safe-cleanup-after-uninstall, backup + APK duplicate finders.
10. Finder as command palette — global search across apps/components/perms/
    AppOps/trackers/backups/logs/files/settings/ops with inline preflighted
    actions.
11. FOSS / source / signature trust signals — first-seen cert, cert-change
    alert, installer source, F-Droid/Izzy/GitHub provenance, AppVerifier
    deep-link.
12. Accessibility & motion controls for power UI — reduced motion, high-contrast
    state colors, clickable-element highlighting, dense-list separators,
    TalkBack grouping, 200% font-scale pass.

Deferred (recorded STALE in COMPLETED): full VPN tracker blocker;
general-purpose cleaner clone; device-owner enrollment by default; new
third-party metadata deps before the local data model stabilizes.

---

## 2. Capability Extension — Datasets, Privacy Intelligence, Modern Android APIs (2026-05-09)

*Source: docs/research/2026-05-09-capability-extension.md*

Follow-up to #1; only deltas (datasets feeding debloat/tracker/replacement
verdicts, plus Android 14–16 APIs not yet exploited). Gap audit device: Samsung
Galaxy S25 Ultra (One UI 8.0, Android 16, API 36) — 112 entries contributed
upstream as `SysAdminDoc/android-debloat-list@b53089f`.

**Part A — Datasets to ingest (license-cleared candidates):**

- **A1. UAD-NG `uad_lists.json`** (GPL-3.0, ~5,357 entries) — dense
  `dependencies`/`neededBy` graph to power honest "removing X breaks Y, Z, W"
  warnings. Merge *edges only* via `tools/import_uad.py` into a sidecar
  `dependencies.json`; weekly CI refresh.
- **A2. UAD-NG `universal-android-preinstalled-lists`** (GPL-3.0) — OEM/model/
  region provenance; expose via `IFilterableAppInfo.getKnownPreinstallOems()`
  for Finder chips + an App Info "Provenance" row.
- **A3. IzzyOnDroid `apkscanner-data`** (GPL-2.0, Codeberg) — SDK/anti-feature
  signatures complementary to Exodus (~30–40% additional library coverage);
  bundle as a second scan-pass asset.
- **A4. Achno/debloat-samsung-ADB-shizuku** — Samsung CSC/region annotations;
  one-time cross-check against `oem.json`'s Samsung block, not ongoing ingest.

Decision log (NOT ingesting — see STALE in COMPLETED): DDG Tracker Radar
(CC-BY-NC-SA), AppCensus (paper artifacts), GrapheneOS/DivestOS/CalyxOS catalogs
(editorial, hand-hint only), TrackerControl bundled DB (DDG-tainted), original
0x192 UAD (archived).

**Part B — Modern Android API exposures (candidate rows):**

- **B1. Privacy Sandbox / SDK Runtime enumeration** — `SdkSandboxManager.getSandboxedSdks()` (API 34+); runtime-truth "SDK Sandbox" row in the Trackers tab. Shipped 2026-06-04 manifest slice: App Details parses target APK `uses-sdk-library` declarations and labels them as manifest metadata. Remaining caveat: the public manager reports SDKs for the calling app, so live loaded-SDK auditing needs a truthful target-scoped data source before this should be shown for arbitrary apps.
- **B2. Domain Verification audit** — `DomainVerificationManager.getDomainVerificationUserState()` (API 31+); per-domain verified/declined/unverified + deep-link hijack inspector. Shipped 2026-06-04: App Details annotates supported domains with same-user claim conflicts, and Finder exposes `domain_links` predicates for claimed/conflicted hosts and conflicting packages.
- **B3. App Archiving** — `PackageInstaller.requestArchive()/requestUnarchive()` (API 35+); third app state (Active/Frozen/Archived), zero-privilege storage reclaim. Shipped 2026-06-04 App Info state/action slice: archived-state tag plus Archive/Unarchive actions. Shipped 2026-06-04 batch/listing slice: main-list Archive/Unarchive batch requests and Finder active/archived app-state predicates. Remaining: API-35 device verification.
- **B4. Memory Tagging Extension (MTE) status** — manifest `allowNativeHeapPointerTagging` + runtime MTE state; security chip in the App Info tag cloud. Shipped 2026-06-04 as `MemoryTaggingInfo`: API 30 reads native-heap pointer-tagging from `ApplicationInfo` private flags, API 31+ reads `getMemtagMode()`, and the App Info chip/dialog avoids overclaiming device hardware enforcement.
- **B5. Health Connect dashboard** — `HealthConnectClient.getGrantedPermissions()` (API 34); "which apps read my heart rate / sleep / steps?". Shipped 2026-06-04 manifest slice: App Details now shows declared `android.permission.health.*` requests with read/write counts and a Health Connect permission-management deep link. Remaining caveat: live granted Health Connect state still needs a truthful target-scoped source before arbitrary-app grant lists should be shown.
- **B6. Credential Manager audit** — passkey/password/federated-identity provider registration; pairs with B5 as a Privacy Dashboard tab. Shipped 2026-06-04 manifest slice: App Details detects manifest services advertising Credential Manager provider actions and flags providers missing `BIND_CREDENTIAL_PROVIDER_SERVICE`. Remaining caveat: this does not reveal saved credential contents or enabled-provider user state; public provider-enabled checks are caller/privilege scoped.
- **B7. Restricted Settings unlock walkthrough** — first-run wizard detecting the Android 13+ restriction and deep-linking to the right Settings screen (the #1 sideload support question); shipped/reverified 2026-06-04 through the existing Privilege Health/Mode Doctor diagnostics and fix targets.
- **B8. MMRL + LSPosed module browser** — read `/data/adb/modules/` + `/data/adb/lspd/` `module.prop`; new "Modules" entry under Privilege Health-Check, gated on root; read-only v1, toggle v2, install v3.

Verdict: twelve net-new rows (five datasets under Finder/Debloater; seven
capability rows across Privacy & Security / Storage / Rootless). License posture
uniformly safe (GPL-2.0+/GPL-3.0/CC-BY-SA). Full local-VPN per-app firewall
explicitly NOT recommended (effort 5/5; AppOps blocking covers day-1 need).

---

## 3. Roadmap Extension Phase 2 — Operational Layer (2026-05-09)

*Source: docs/research/2026-05-09-roadmap-extension-phase-2.md*

Four research streams covering the operational layer: privileged-action
accountability, recoverability when shells die, automation surfaces, non-phone
form factors, localization tooling, and in/out migration paths. 35 net-new
candidate rows; license posture uniformly safe.

**Stream 1 — Observability + Testing** (deep-dive in section 4):
audit-log accountability is the unclaimed differentiator (append-only
`(ts, mode, op, target, exit_code, signature)` SQLite table); an active-probe
"Doctor" screen distinct from the display-only T5 Health-Check; Macrobenchmark +
Baseline Profiles (zero privacy cost, cold-start dominated by `PackageManager`
enumeration); a `floss` build flavour stripping optional network calls. Hard
rejects: Firebase, always-on cloud Sentry, anonymous usage counters, Play
Console signals (all recorded STALE).

**Stream 2 — Plugin Ecosystem + Inter-App Integration:**
no app in the AM space ships a Tasker plugin, a QS-tile suite, or a Wear OS
companion. Tasker plugin can live in-app via one Activity + one
BroadcastReceiver (~120 KB, effort 2/5, novelty 5/5). QS tiles via
`StatusBarManager.requestAddTileService()`: host-side code now ships "Run
Freeze Profile" plus "Force-Stop Pinned App". The 2026-06-04 DocumentsProvider
slice exposes AppManagerNG backup/profile roots through the standard
`${applicationId}.documents` SAF authority. The 2026-06-04 Locale/Tasker plugin
slice adds signed in-app edit/fire brokers for `am://` automation URIs;
remaining integration work is device SAF/QS/Tasker manual verification.

**Stream 3 — Non-Phone Form Factors:**
killer addition is a Wear OS phone-side package manager (no FOSS app does this;
ADB-over-WiFi + `WearableListenerService`/`MessageClient` + ~200 KB watch
companion; effort 4/5, max differentiation). Floor table-stakes:
`NavigationSuiteScaffold`, `ListDetailPaneScaffold`, `SupportingPaneScaffold`,
5-breakpoint WindowSizeClass incl. XL, `FoldingFeature` posture awareness.
ChromeOS ARCVM detection, Samsung DeX multi-instance, Galaxy XR variant — each
~2 effort. Hard rejects: Android Automotive OS, Android Auto projection
(template-only, no package-management category).

**Stream 4 — Localization, Migration, Offline Resilience:**
bridge to upstream's hosted Weblate as a separate component (free, ~1 day);
pseudolocale `en_XA`/`en_XB` CI variants. **Critical pre-rename gap**: a snapshot
bundle export/import (`{prefs/, profiles/, tags/, history.db}` ZIP w/ schema
header) must ship *before* the applicationId rename to prevent data loss.
Importers: Canta presets (~30 LoC), UAD-NG selection, Hail freeze tags.
Offline resilience: privileged-shell journal + DeathRecipient replay for
mid-batch shell death; bundled-data freshness banner (>30d warn / >180d block);
air-gap toggle; multi-mirror debloat-defs fetcher; battery-aware JobScheduler;
atomic-write profile dir + Syncthing-friendly conflict-picker.

Top-5 highest-leverage rows: privileged-shell journal + DeathRecipient replay;
privileged op audit log; snapshot bundle export/import (pre-rename); in-app
Tasker plugin; Wear OS phone-side companion.

---

## 4. Observability, Telemetry & Testing Audit (2026-05-09)

*Source: docs/research/2026-05-09-observability-testing-audit.md*

Comparative survey of how 13 AM-class / adjacent OSS projects handle
diagnostics, crash reporting, telemetry, perf tracing, smoke tests, OEM
fixtures, CI, and audit logs. Bright-line finding: only LibChecker ships
Firebase (and pays an F-Droid `Tracking` antifeature + a parallel FLOSS
flavour). Every other peer ships zero remote telemetry; the dominant pattern is
a **user-initiated support bundle**, not an automated crash reporter.

Net-new candidate rows:

- **O-01 Support Info Bundle** (effort 2) — one-tap composer snapshotting Android/ROM/mode/feature/bootstrap-signature/scrubbed-logcat to `support-info-<device>-<ts>.txt`; PII-scrubbed; zero network.
- **O-02 Privileged Op Audit Log** (effort 3) — append-only SQLite `(ts, mode, op, target, exit_code, signature)` for freeze/unfreeze/uninstall/permission-grant/component-toggle; viewer + JSON export + retention slider. Pure differentiator.
- **O-03 Mode Self-Test ("Doctor")** (effort 3) — ordered probes (root binary, su grant, Shizuku binder ping, Sui detection, ADB pairing, ABI/SELinux, KSU API) with pass/fail/cause + "fix it" deep-links.
- **O-04 OEM Quirk Panel** (effort 3) — detect Knox/MIUI/EMUI/OxygenOS/ColorOS/OneUI/HyperOS at runtime; surface known limitations + workaround page.
- **O-05 Opt-In Local Crash Sink (ACRA-style)** (effort 2) — write crash JSON to `Documents/AppManagerNG/crashes/`, no network, default OFF; shared via O-01.
- **O-06 LocalServer Bootstrap Smoke Test** (effort 2) — run bootstrap end-to-end against the current mode and print the failure-signature line on success too.
- **O-07 Macrobenchmark module** (effort 4) — `:benchmark` measuring cold-start / list-scroll jank / Backups TTI; nightly device CI; Baseline Profile for the app-list path. Partial scaffold shipped 2026-06-04: `:benchmark` compiles cold-start Macrobenchmark and BaselineProfileRule startup tests against the debug app, and a seed startup profile is checked in under `app/src/main`.
- **O-08 Espresso + UI Automator smoke pack** (effort 4) — headless device suite (open list, freeze/unfreeze, component blocker, one-shot rule) in `connectedCheck` on API 26/30/34/35. Partial scaffold shipped 2026-06-04: app androidTest has a LargeTest UIAutomator launch smoke for the main app list; privileged smoke flows and device-matrix CI remain open.
- **O-09 CI dependency CVE scan** (effort 2) — `dependency-check-gradle` + `dependency-review-action`; fail PR on HIGH/CRITICAL.
- **O-10 "F-Droid clean" build flavor** (effort 3) — `floss` (no optional network) vs `full` (ACRA file sink, update check, MOTD); F-Droid pinned to `floss`.
- **O-11 In-app log viewer w/ severity filter + redaction** (effort 2) — structured viewer, one-tap "redact UIDs/paths", "copy line".
- **O-12 "Was it me?" reverse audit** (effort 2) — given a package, list every privileged op AM performed against it (composes with O-02).

Hard rejects (recorded STALE): Firebase Analytics/Crashlytics/Performance;
always-on cloud Sentry; anonymous feature-usage counters; Play Console Android
Vitals; Play Integrity / SafetyNet telemetry; Google Play In-App Update.

---

## 5. iter-20 Research Delta (2026-04-15 to 2026-05-08)

*Source: research/iter-20-delta.md*

Dated delta scan of upstream + competitor issue trackers, new competitors,
Android 17 behavior changes, the May 2026 Android Security Bulletin, and
dependency updates. 35 extension findings; floor (30) cleared. Highlights below;
the original carries the full per-issue source URLs.

**Upstream MuntashirAkon/AppManager issues (NG actions):**

- #1968 Automating Save APK — shipped 2026-06-04: authenticated automatic profile triggers route through `ProfileApplierReceiver` instead of `ProfileApplierActivity`, and profile automation accepts Tasker-style `extra_pkg` as a one-shot package override, including signed Locale plugin fire-time variables.
- #1967 Root not detected after reinstall on KernelSU — shipped 2026-06-04: KernelSU diagnostics classify package-only, unknown, and restricted App Profile states into recovery actions; the details dialog can request a fresh root grant, reinitialize root ops on success, refresh diagnostics, and direct restricted profiles back to KernelSU Manager policy review.
- #1966 App Info popup density / SDK row position — shipped 2026-06-04 as an NG-specific App Info pager fix: the first vertical metadata group now surfaces SDK bounds, SDK Runtime manifest state, and signing certificate SHA-256/Subject/Issuer identity, with full SDK Runtime and certificate dialogs still one tap away. Upstream's `app_info_card.xml` popup does not exist in NG.
- #1965 Clear-data no-op on Android 16 QPR2 — shipped before this pass and regression-covered 2026-06-04: `PackageManagerCompat.clearApplicationUserData()` snapshots storage stats before/after IPC clear, falls back to `pm clear --user N <pkg>` when data/cache bytes fail to drop past the tolerance window, and now has focused JVM coverage for the fallback decision and shell success-output parser.
- #1964 File Manager search/filter — shipped before active-roadmap reconciliation and reverified 2026-06-04: File Manager has toolbar SearchView filtering, debounced recursive matching via `FmSearchUtils.searchRecursive()`, active search chips, whole-volume scan warnings, empty-result clear-search recovery, row subtitles with containing-folder context, hidden-dot-file option handling, and `FmSearchUtilsTest` coverage.
- #1963 `ActivityNotFoundException: DebloaterActivity` (moto g22) — shipped before this pass and regression-covered 2026-06-04: `.debloat.DebloaterActivityAlias` is registered as an exported stable shortcut component, and `ShortcutManifestContractTest` now resolves every static shortcut target to an exported manifest component, verifies trampoline action filters, verifies alias targets, and locks the Debloater alias no-launcher shape.
- #1962 / #1961 Android 16 root/binder regressions — shipped 2026-06-04 as a host-verifiable binder compat slice: `Android16BinderCompat` centralizes raw `IBinder.transact()` calls from `ProxyBinder`, `AMService`, and `BaseParceledListSlice`, preserves pre-Android-16 direct behavior, attempts reflective fallback on Android 16+ runtime/linkage failures, and has a JVM source contract rejecting new raw transact call sites. LOS 23.2 device validation still needs a matching online ROM/device.
- #1957 Dolphin `sqfs_open_image` warning — reverified and regression-covered 2026-06-04 as a wrong-repository/absent-backend item: upstream closed the report as Linux AppManager, NG has no SquashFS writer/dependency and writes tar-family backups through `TarUtils.createDurable()`, and `BackupArchiveFormatContractTest` now proves supported backup extensions remain `.tar.gz` / `.tar.bz2` / `.tar.zst` while scanning production source/build files for any SquashFS backend surface.
- #1960 Shizuku mode support — reverified 2026-06-04 as already covered in NG: upstream closed the request as duplicate #55, while NG already exposes `Ops.MODE_SHIZUKU` in the settings mode list and active privilege-health probes.
- #1956 AppOps permission mode reverting to ignored on Android 16/NothingOS — regression-covered 2026-06-04: `AppOpsManagerCompat.setMode()` continues to schedule `OsRevertMonitor.watchAppOp()` after every AppOps write, and `OsRevertMonitorTest` now locks the banner payload for an allow -> ignore revert with target package, AppOp name, expected/current modes, and the AppOps revert hint. The existing Doze allowlist watcher remains the battery-optimization-specific OS-revert path.

**Competitor issues worth importing (Canta / Hail / Neo Backup / SD Maid SE):**
export-installed-list, accidental-disable recovery, password-protected hidden
mode, frozen-folder leftovers, old-backup-skipped scheduling, Wi-Fi config
backup, uninstalled-with-backups blocklist UI, CIFS null-byte injection,
Samsung A17/Android 16 check aborts.

**New competitors:** restoid (restic-style backup engine — strategic leapfrog
for AM's recurring backup-encryption pain); Sui detection + AppOps UID-mode
write (closes upstream #1863).

**Android 17 compliance (target SDK 37):**

- Reflection-on-static-final audit — likely-severe break; audit every `Field.setAccessible(true)` against a static-final target. (Top actionable.)
- BAL allow flag — migrate the profile-trigger Activity-launch-from-Service to `_ALLOW_IF_VISIBLE` or a BroadcastReceiver.
- Keystore 50,000-key per-app limit — switch backup AES to HKDF-from-master deterministic derivation; audit `crypto/AESCrypto`.
- Implicit URI grants removed in Android 18 — add explicit `FLAG_GRANT_READ_URI_PERMISSION` + `grantUriPermission()` to "Save APK" / "Share backup" now.
- Large-screen orientation can't be opted out — remove `screenOrientation="portrait"` locks on sw>=600dp paths.

**Security / dependencies:**

- CVE-2026-0073 (adbd zero-click proximal RCE, CRITICAL) — review whether the Shizuku-via-wireless-debug bootstrap surface is affected; add a release note requiring patch level >= 2026-05-01 for ADB mode.
- BouncyCastle 1.83 -> 1.84 — closes 4 CVEs incl. PGP AEAD chunk DoS (CVE-2026-3505) relevant to GPG-encrypted backups; verify the GPG backup smoke test.
- AGP 8.13 -> 9.x bump needed before the next major release (AGP 10 mid-2026 removes the old DSL; there is no AGP 8.14).
- Watch-only: libsu 6.0.0 (no new release), sora-editor (Maven frozen 2025-06-22), smali 3.0.7 current.

**Strategic / docs:** publish a `docs/SIDELOAD_VERIFICATION.md` position document
ahead of Google Developer Verification rollout; update accessibility-enablement
docs for Android 17 Advanced Protection Mode.

**Carry-over for the next iteration:** re-poll Reddit (WebFetch blocked);
watch InstallerX-Revived v2.4, restoid v0.6.0, and Android 17 stable notes
(Google I/O 2026, May 19).

---

## 6. iter-6 Delta — null-result note (2026-05-01)

*Source: docs/research/iter-6-delta.md*

No net-new sources or feature candidates this iteration. Phase-1 delta scan
against the iter-5 source appendix (S01–S64) over an hours-long window yielded no
upstream releases, CVE advisories, or adjacent-project drops. Recorded so the
research cadence has a documented null result. Next full Phase 1–5 cycle triggers
on: upstream v4.1.0/v4.0.x publish, an Android 17 Developer Preview drop, a major
adjacent-project release, a new BouncyCastle CVE, or 14 days elapsed.

---

## 7. Deep-Research Pass — code-verified delta (2026-06-03)

This section is the result of a code-reading pass run **after** the 2026-06-03
consolidation. The consolidation produced an honest paper merge of sections 1–6
but surfaced its candidate rows into `ROADMAP.md` without checking the current
implementation. Reading the actual source showed that a large share of those
candidates had **already shipped**, and surfaced a small set of precise,
evidence-tied net-new items that the paper merge could not see. Every non-obvious
claim below is labelled `[Verified]` (read in source/CI this pass),
`[Likely]`, `[Assumption]`, or `[Needs validation]`.

### Executive Summary

AppManagerNG is a mature Java/Kotlin fork of `MuntashirAkon/AppManager`
(`versionName 0.5.0`, `versionCode 7`, minSdk 21, compileSdk/targetSdk 36, AGP
9.2.0) with an unusually complete operational layer for its category: a
structured operation log with exit codes / reversibility metadata, a
privileged-shell death journal with a retry/clear recovery dialog, reproducible
double-build release CI, an OWASP + dependency-review CVE pipeline, CodeQL, a
`floss`/`full` flavour split, and a full slate of Android 17 behaviour-change
audits. The headline finding of this pass is that the operational backlog the
research framed as "to build" is mostly **built** — so the remaining value is in
*coverage* and *correctness* of those systems, not in net-new subsystems. Top
opportunities, one line each:

1. `[Verified]` Batch-op "Retry" re-runs the **whole** batch, re-applying already-completed destructive ops (no completion cursor in `BatchQueueItem`). **P0 data-safety.**
2. `[Verified]` The operation audit log misses **single-app App Details** privileged actions (only batch/installer/profile/cleanup are recorded). **P1 coverage hole.**
3. `[Verified]` The weekly OWASP CVE job uses `continue-on-error: true` — a CRITICAL CVE against an unchanged pinned dep never fails the run. **P2 hardening.**
4. `[Verified]` `op_history` retention prune runs only when the history screen is opened, and early-returns when retention is 0 — unbounded growth for users who never visit it. **P3.**
5. `[Verified]` `compile_sdk`/`target_sdk` are still 36; the Android 17 per-item audits are written and HKDF/reflection items implemented, so the open work is the bump-gate, not the audits. **P0 (gated).**
6. `[Updated 2026-06-04]` `:benchmark`, a seed Baseline Profile, and a LargeTest UIAutomator launch smoke now exist; the remaining O-07/O-08 work is device execution, list-scroll / Backups TTI coverage, privileged smoke flows, and CI wiring. **P2.**
7. `[Likely]` Snapshot bundle export/import is still absent and is the only migration path off the already-divergent `applicationId`. **P2 data-loss guard.**

### Evidence Reviewed

- **Key files / dirs (read this pass):** `versions.gradle`; `app/build.gradle`
  (flavours, applicationId, splits); `settings.gradle`;
  `app/src/main/java/.../batchops/{BatchOpsService,BatchOpsJournal,BatchQueueItem,BatchOpsManager}.java`;
  `app/src/main/java/.../main/MainActivity.java` (`maybeShowInterruptedBatchOpRecovery`);
  `app/src/main/java/.../history/ops/{OpHistoryManager,OperationJournalMetadata,OperationHistoryExporter,OpHistoryItem,OpHistoryActivity}.java`;
  `app/src/main/java/.../db/entity/OpHistory.java`, `db/dao/OpHistoryDao.java`;
  `app/src/main/java/.../crypto/AESCrypto.java`;
  `.github/workflows/{dependency-scan,release,android17-emulator}.yml`;
  `docs/audits/` (Android 17 set), `docs/distribution/build-flavors.md`,
  `docs/sideload-verification.md`.
- **Git range:** `7c08171` (consolidation) → `800f6b9` (current `main`; a
  parallel agent shipped T19-B/T19-C follow-ups + `ApkBundleBaseExtractor`
  during this pass). `git log -30 --oneline` reviewed.
- **External sources:**
  [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17)
  (static-final reflection → `IllegalAccessException`; lock-free `MessageQueue`),
  [Android 16 QPR2](https://developer.android.com/about/versions/16/qpr2),
  [Inure App Manager](https://github.com/Hamza417/Inure),
  [Shizuku releases](https://github.com/RikkaApps/Shizuku/releases). These
  corroborate the existing Android-17 audit set and the competitor framing; they
  did not surface new candidates.
- **Unverifiable on a CI host `[Needs validation]`:** all device-gated rows
  (T21-H two-pane, the accessibility audits, Android-17 on-image behaviour, the
  batch-retry resume on a real interrupted shell). These are correctly parked in
  ROADMAP buckets C / device-gated.

### Current Product Map

A privilege-mode (Auto/Root/Shizuku/ADB) Android package-operation cockpit:
App List → App Details (components, permissions, AppOps, signing, overlays,
profiling capture) · Batch Ops + One-Click Ops (debloat/cleanup/duplicate
finders) · Profiles + Routine scheduler · Backup/Restore (encrypted, scheduled)
· Operation History (structured, exportable) · Finder/Scanner · File Manager ·
Code/Dex/Hex viewers · Settings (appearance, privacy, glossary, health-check).
Release path: tag-triggered reproducible double-build signed APK with SHA-256
sidecar.

### Feature Inventory (delta — systems touched this pass)

- **Operation log** `[Verified]` — `op_history` Room table + `OperationJournalMetadata`
  (`mode`/`exit_code`/`targets`/`failures`/`replayable`/`reversible`), viewer
  (`OpHistoryActivity`), CSV export (`OperationHistoryExporter`), retention prune.
  Maturity: complete for **queued** ops; **incomplete** for single-app actions.
- **Batch reliability** `[Verified]` — `BatchOpsJournal` (intent→executing→interrupted),
  Shizuku `OnBinderDeadListener` death-watch, `MainActivity` retry/clear dialog,
  `BatchOpsJournalTest`. Maturity: recovery exists; **resume granularity missing**.
- **Backup crypto** `[Verified]` — `AESCrypto` HKDF (`HmacSHA256`) +
  `deriveArchiveKey`; the Android-17 keystore-key-cap mitigation is already in.
- **CI/security** `[Verified]` — reproducible release, OWASP + dependency-review,
  CodeQL, `android17-emulator.yml` (page-alignment + hidden-API + migration tests).
  Gap: the OWASP job is non-gating.
- **Distribution** `[Verified]` — `floss`/`full` flavours with
  `ALLOW_OPTIONAL_NETWORK_FEATURES`; F-Droid/Izzy/Accrescent listing packets;
  `docs/sideload-verification.md` already published.

### Competitive Landscape

- **Inure (Hamza417)** `[Verified]` — Root+Shizuku, terminal, analytics,
  VirusTotal, debloat, custom theme engine, reproducible build. *Learn:* the
  reproducible-build + custom-theme polish bar. *Avoid:* bundling VirusTotal as a
  default (network/privacy cost — NG correctly gates this behind `full`).
- **Canta** `[Verified]` — single-purpose debloat. *Learn:* the one-tap
  recommendation UX. *Avoid:* no operation log / no rollback (NG's differentiator).
- **Universal Android Debloater-NG (UAD-NG)** — dataset source (A1–A3); dense
  dependency graph. *Learn:* "removing X breaks Y/Z" edges. *Avoid:* desktop-ADB-only flow.
- **Hail / Shelter / SD Maid SE / Neo Backup** — freeze, work-profile isolation,
  storage cleanup, backup. *Learn:* per-feature focus. *Avoid:* fragmentation —
  NG's "cockpit" thesis is the counter-position.
- **Shizuku ecosystem** `[Verified]` (Shizuku ≥13.x, Android-16 QPR support) —
  the rootless privilege substrate NG already targets via `shizuku_version 13.1.5`.
- **Standards:** Android platform behaviour changes (16 QPR2 / 17 API-37), F-Droid
  inclusion + antifeature policy (drives the `floss` split), WCAG 2.2 AA (the
  parked accessibility audits).

### Quality & Friction Findings

- **Critical** — Batch "Retry" re-applies completed destructive ops → roadmap
  *P0 Batch-op retry must resume from the failed target*. `[Verified]`
- **Major** — Single-app App Details privileged actions are invisible to the
  audit log and to the "was it me?" reverse audit → roadmap *P1 Extend the
  operation audit log to single-app App Details actions*. `[Verified]`
- **Major** — Weekly CVE audit cannot fail the build → roadmap *P2 Fail the
  weekly OWASP CVE audit on CRITICAL*. `[Verified]`
- **Minor** — `op_history` prune is screen-open-only and no-ops at retention 0 →
  roadmap *P3 Schedule OpHistory retention prune as a periodic job*. `[Verified]`
- **Minor** — `applicationId` already diverged from upstream with no import path
  → roadmap *P2 Snapshot bundle export/import*. `[Verified]` (divergence)
  / `[Likely]` (no importer in source).
- **Cosmetic** — Two committed audit docs + `PROJECT_CONTEXT.md` retain stale
  relative links into the moved `docs/research/` paths (flagged by the
  consolidation pass; left for a follow-up since edits are scoped to planning docs).

### Architecture & Technical Findings

- `[Verified]` `BatchQueueItem` is the single source of batch scope and is a flat
  `(packages, users, options)` carrier — no completion state — so any resume
  feature must persist progress in `BatchOpsJournal`, not in the queue item.
- `[Verified]` `BatchOpsManager.performOp` already returns a `failedPackages`
  result per op branch; the data needed for a resume exists at runtime but is
  discarded at the service boundary.
- `[Verified]` The audit log's coverage is determined by *where* `addHistoryItem`
  is called — four service/VM call sites — making "extend coverage" a
  call-site-insertion task, not a schema change.
- `[Verified]` Dependency health is current: BouncyCastle 1.84, AGP 9.2.0, Gson
  2.14.0, Room 2.7.2; minSdk-21 ceiling deps (material 1.13, work 2.10.x,
  biometric 1.4.0-alpha04) are pinned with documented reasons in `versions.gradle`.
- `[Updated 2026-06-04]` `settings.gradle` includes `:benchmark`;
  `app/src/main/baseline-prof.txt` carries a seed startup Baseline Profile.
  Device-generated profile refresh and emulator-matrix execution remain
  open.

### Security / Privacy / Data Safety

- `[Verified]` Privacy posture is intact: `floss` strips optional network; no
  Firebase/GMS; CVE pipeline present. The one weakness is the **non-gating**
  weekly OWASP step (detection without enforcement).
- `[Verified]` Data-safety: the batch-retry re-application is the highest-impact
  issue this pass — it can repeat irreversible operations on user data.
- `[Likely]` The absent snapshot bundle is a latent data-loss path for upstream
  AM users migrating to NG (already-diverged `applicationId`).

### Explicit Non-Goals (rejected, unchanged this pass)

Full local-VPN per-app firewall; any remote telemetry (Firebase/Sentry/anonymous
counters/Play signals); DDG Tracker Radar / TrackerControl DB ingest
(licence-tainted); Android Automotive / Android Auto; Compose migration (NG is
View-based). All recorded in `COMPLETED.md` *Stale / Obsolete Items*; this pass
found no reason to revisit them.

### Open Questions (genuine blockers only)

- `[Needs validation]` Does any single-app App Details action *already* route
  through a batch/queue path (and thus already log)? The call-site survey says no,
  but a runtime trace on each action would confirm before wiring `addHistoryItem`
  to avoid a double-record. Blocks final scoping of the P1 audit-coverage item.
- `[Needs validation]` Is the `op_history` retention pref default finite or
  "keep forever" (0)? Determines whether the P3 periodic-prune item is a latent
  unbounded-growth bug or only a convenience. Pref default not read this pass.
- `[Needs validation]` On-image Android 17 behaviour for the BAL allow-flag +
  explicit-URI-grant paths before flipping `targetSdk` to 37 — device/emulator
  gated, not resolvable on the build host.

---

## 8. Freshness Refresh — submodule and dependency delta (2026-06-04)

This pass re-read the current `main` checkout at `c43601d`, the live roadmap,
`versions.gradle`, `.gitmodules`, and the initialized dataset directories. It
also checked current Maven/Google metadata for the pinned toolchain and ceiling
dependencies.

### New Finding

- **Major — Required `android-libraries` dataset is not tracked as a submodule.**
  `.gitmodules`, `CLAUDE.md`, and `PROJECT_CONTEXT.md` say both
  `scripts/android-libraries` and `scripts/android-debloat-list` are fetched by
  `git submodule update --init --recursive`, but the superproject only tracks
  `scripts/android-debloat-list`. The working tree contains an untracked local
  `scripts/android-libraries` clone at `8fb3919`, so this host can see
  `libs.json`, but a fresh checkout will not fetch it from the superproject.
  This was promoted to `ROADMAP.md` as a P1 build-host hygiene item. Resolved
  2026-06-04 by tracking `scripts/android-libraries` as a gitlink at
  `8fb3919828e9c9f6e75faaaa322c5af59c6d05fa`. [Verified]

### Current Checks

- **Cycle 1 handoff check:** current `main` is `9cdbb22` after the Tasker plugin
  broker slice. The remaining AppManagerNG rows are primarily device/manual
  verification or external-submission gated: SAF DocumentsProvider picker
  behavior, Quick Settings tile install/fire, Tasker plugin fire, large-screen /
  accessibility walkthroughs, and Android 17 image compliance. [Verified]
- **Dependency/toolchain metadata:** AGP `9.2.0` has newer alpha metadata
  (`9.3.0-alpha09`); Material Components `1.13.0`, Activity `1.11.0`, Room
  `2.7.2`, Work `2.10.5`, and WebKit-related ceiling pins all remain governed by
  the documented minSdk-21 policy. Current metadata shows newer versions for
  several of those lines (`material 1.14.0`, `activity 1.13.0`, `room 2.8.4`,
  `work 2.11.2`), but the repo already documents that these lines drop API
  21-22 support or need coordinated ceiling decisions. BouncyCastle `1.84`,
  Robolectric `4.16.1`, and OWASP dependency-check `10.0.3` are current. [Verified]
- **Already-closed research rows stayed closed:** weekly OWASP critical-CVE
  gating now passes `-PdependencyCheckFailBuildOnCvss=9.0`; operation-history
  pruning is scheduled through WorkManager; batch retry, single-app audit
  coverage, Mode Doctor, and the opt-in local crash sink are documented as
  shipped in the live roadmap. [Verified]
- **External source refresh:** official Android developer-verification docs now
  describe the 2026 rollout and Android Developer Console registration path for
  apps distributed outside Play. That keeps `docs/sideload-verification.md` and
  the distribution-verification posture current; it did not create a new row.
  [Verified, external]
- **Validation boundary:** no Gradle build/test was run on this PC because
  `local.properties` points at `C:\Users\--\AppData\Local\Android\Sdk` and no
  Android SDK exists under the usual local paths. This mirrors the NovaCut host
  limitation and should be resolved with a real SDK or an NTFS verification
  mirror before compile/test claims are made. [Needs validation]

---

## 9. Research Refresh — upstream issue delta (2026-06-04 Cycle 2)

This pass re-synced `main` at `3bb0a78`, re-read the live roadmap after the
pass-2 audit commits, checked AppManager v4.0.5 release notes, Shizuku v13.6.0
release notes, and searched recently updated upstream AppManager/Shizuku issues.
No build/test was run because this was a documentation-only research cycle.

### Promoted to `ROADMAP.md`

- **P1 — Backup Extras restore clarity and audit trail.** Upstream issue #1980
  asks what "Depending on permissions, not all extras can be restored" means.
  The current string in `res/values/strings.xml` lists broad examples but does
  not tell users which extra was skipped or why. The roadmap now asks for a
  compact capability/details surface and per-extra restore skip reasons.
  [Verified, external]
- **P2 — Generic manifest `<meta-data>` explorer.** Upstream issue #1766 asks
  for a view of `<meta-data>` entries. NG has targeted manifest/privacy surfaces
  and test fixture manifests with metadata, but no generic App Details metadata
  group for application or component bundles. [Verified, external]
- **P2 — Optional extended metadata in app-list exports.** Upstream issue #1773
  asks for more exported metadata. `ListExporter` currently writes stable
  identity/version/signature/install-time/installer fields only; the roadmap now
  scopes an opt-in extended mode so legacy imports remain compatible.
  [Verified, external]
- **P2 — Installer notification final-state wording.** Upstream issue #1805
  reports a collapsed "Done" notification. `PackageInstallerService` still sets
  the last progress body to `R.string.done` during result handoff, so the
  roadmap now calls for the concrete final subject to survive in collapsed and
  expanded views. [Verified, external]
- **P3 — File Manager "Open with" defaults and keyboard focus.** Upstream issue
  #1810 plus local TODOs in `OpenWithDialogFragment` show the dialog still needs
  no-keyboard initial focus and first-class per-file/per-extension defaults.
  [Verified, external]

### Checked but not promoted

- **AppManager v4.0.5 release notes** mention reproducible releases, uninstalled
  app data clearing, installer options, terminal visual fixes, and accessibility
  action labels. Current NG roadmap/source already has distribution,
  terminal/accessibility, installer, and data-operation lanes; no unique new row
  was promoted from the release text alone. [Likely]
- **Shizuku v13.6.0 release notes and recent Android 16/17 issue searches**
  point at Android 16 QPR1 support, copyable start commands, trusted-WLAN
  auto-start, pairing failures, and Android 17 visibility limits. NG already has
  device-gated Android 17, Shizuku/ADB verification, and Mode Doctor rows; this
  pass did not duplicate them. [Likely]
- **Dex optimization profile reset in ADB mode (#1733)** maps to existing
  DexOpt code that disables clear-profile/force-dexopt outside root or system
  privilege. This may need user-facing explanation if implementers touch DexOpt,
  but it was not promoted as a separate row until current-mode intent is
  confirmed. [Needs validation]
- **Wireless-debugging pairing on Quest (#1975)** overlaps the existing
  Shizuku/ADB and Android-device verification lanes and appears device-specific;
  no planning row was added without hardware evidence. [Needs validation]

---

## 10. Research Refresh — mode and terminal delta (2026-06-04 Cycle 3)

This pass re-synced `main` at `3d4c4f3`, scanned current source TODO/FIXME
clusters, reviewed recently updated upstream AppManager issues, and checked the
live roadmap/completed ledger to avoid re-adding shipped NF-28/NF-29 component
actions. No build/test was run because this was a documentation-only research
cycle.

### Promoted to `ROADMAP.md`

- **P1 — DexOpt root-only option sanitization.** Issue #1733 shows ADB-mode
  DexOpt reaching `clearApplicationProfileData()` and failing with the platform
  root/system-only SecurityException. NG's dialog disables that checkbox for
  non-root/system UIDs, but `BatchOpsManager.opPerformDexOpt()` trusts the
  serialized option and runs the root-only call whenever the flag is true. The
  roadmap now asks for worker-side sanitization and clear skipped-suboperation
  reporting. [Verified, external]
- **P2 — Mode picker lifecycle and rollback safety.** Issue #1817 reports a
  crash when closing the Mode of Operation picker while pressing Apply.
  `ModeOfOpsPreference` persists the selected mode before async initialization
  finishes, then relies on later callbacks/progress-dialog dismissal. The
  roadmap now scopes a single-flight lifecycle guard and rollback-on-failure
  contract. [Verified, external]
- **P2 — Terminal active-provider routing.** Issue #1727 reports Terminal doing
  nothing in wireless debugging mode. NG ships `TermActivity`, but it starts a
  local `sh -i` process and carries TODOs for an actual terminal and error
  handling. The roadmap now asks for explicit local/root/Shizuku/ADB route
  selection, failure surfacing, and session tests. [Verified, external]

### Checked but not promoted

- **Assistant trampoline for ADB services/broadcasts (#1973).** The request is
  accepted upstream, but NG already records NF-28 Receivers send broadcast,
  NF-29 Services start/stop, and the Hail-style assistant action as completed
  work. The exact Android-16 assistant-trampoline trick may deserve a future
  implementation spike, but this pass did not add a duplicate row until a source
  gap is proven against the shipped component actions. [Needs validation]
- **File Manager current-directory filter (#1964).** Already shipped and
  reverified in the active roadmap: toolbar search, recursive matching, active
  chips, whole-volume scan warning, hidden-file handling, and
  `FmSearchUtilsTest` coverage. No new row. [Verified]

---

## 11. Research Refresh - recovery and headset pairing delta (2026-06-04 Cycle 4)

This pass re-synced `main` at `1f4b32e`, re-read the active roadmap and current
App Details / Debloater / Wireless ADB source, checked the latest upstream
AppManager issue list through #1980, and refreshed relevant Android platform
documentation. No code was changed; the output is a planning delta promoted to
`ROADMAP.md`.

### Executive Summary

AppManagerNG's strongest current shape is an operational package-management
cockpit: rich App Details actions, debloat/remove-for-user workflows, rootless
ADB/Shizuku setup, structured operation history, and recovery surfaces. The
highest-value direction from this refresh is not a new subsystem; it is closing
three workflow completion gaps where the engine already exists but the user path
is incomplete: Wireless ADB pairing on surfaces without notification inline
reply, Debloater recovery for removed system packages, and the fixed App Info
action rail pushing common actions off-screen.

Top opportunities:

1. `[Promoted P1]` Add in-app Wireless ADB pairing-code fallback for Quest and
   other no-inline-reply surfaces.
2. `[Promoted P1]` Wire Debloater "Put back" to existing install-existing
   support for removed system packages.
3. `[Promoted P2]` Make the App Info action rail priority-aware/customizable.
4. `[Checked]` Android 16 foreground-service special-use crash is already
   addressed in NG by manifest permission and service types.
5. `[Checked]` Material/Monet widget theming is already addressed in NG through
   `AppWidgetThemeUtils`.

### Evidence Reviewed

- **Local files and code paths:** `ROADMAP.md`, `RESEARCH_REPORT.md`,
  `CHANGELOG.md`, `versions.gradle`, `app/build.gradle`,
  `app/src/main/AndroidManifest.xml`, `settings/Ops.java`,
  `adb/AdbPairingService.java`, `debloat/DebloaterActivity.java`,
  `batchops/BatchOpsManager.java`, `apk/installer/PackageInstallerCompat.java`,
  `apk/installer/PackageInstallerService.java`,
  `details/info/AppInfoFragment.java`, `details/info/ActionItem.java`,
  `res/layout/pager_app_info.xml`, and `utils/appearance/AppWidgetThemeUtils.java`.
- **Git history:** `rtk git log -10` from `1f4b32e` back through the pass-2
  audit, upstream issue refresh, Android 16/17 binder gate, and backup archive
  contract work.
- **External sources:** upstream issues
  [#1975](https://github.com/MuntashirAkon/AppManager/issues/1975),
  [#1977](https://github.com/MuntashirAkon/AppManager/issues/1977),
  [#1969](https://github.com/MuntashirAkon/AppManager/issues/1969),
  [#1953](https://github.com/MuntashirAkon/AppManager/issues/1953),
  [#1978](https://github.com/MuntashirAkon/AppManager/issues/1978),
  [#1944](https://github.com/MuntashirAkon/AppManager/issues/1944),
  [#1954](https://github.com/MuntashirAkon/AppManager/issues/1954),
  [#1974](https://github.com/MuntashirAkon/AppManager/issues/1974),
  [#1959](https://github.com/MuntashirAkon/AppManager/issues/1959);
  Android docs for
  [wireless ADB pairing](https://developer.android.com/guide/developing/tools/adb.html),
  [RemoteInput](https://developer.android.com/reference/android/app/RemoteInput),
  [PackageInstaller.installExistingPackage](https://developer.android.com/reference/android/content/pm/PackageInstaller),
  [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
  and [widget dynamic colors](https://developer.android.com/develop/ui/views/appwidgets/enhance).
- **Could not verify:** Quest 3 notification reply behavior, actual Wireless
  ADB pairing completion on headset hardware, and install-existing restore
  behavior on a removed OEM/system package. These remain manual/device gates.

### Current Product Map Delta

- **Wireless ADB setup** already has a foreground pairing service and mDNS port
  discovery. The current input path is notification `RemoteInput` only after a
  port is found.
- **Debloater** already lists uninstalled apps, supports remove-for-user with
  OEM-safe fallback, and warns that system packages can usually be restored
  through Install existing. The selected action branch for `action_put_back` is
  still empty.
- **Installer internals** already support install-existing through
  `PackageInstallerCompat.installExisting()` and `PackageInstallerService`; that
  capability is not exposed as a Debloater batch recovery action.
- **App Info actions** are created in fixed source order into a horizontal
  strip, not an adapter with stable action IDs or a user-order model.

### Feature Inventory Delta

- **Wireless ADB pairing**
  - User value: rootless privileged mode without a PC after first pairing.
  - Entry point: Settings/Onboarding mode selection -> `Ops.pairAdbInput()`.
  - Main code: `Ops`, `AdbPairingService`, `ServerConfig`, `AdbMdns`.
  - Maturity: partial for non-phone surfaces; notification inline input is a
    single input channel.
  - Improvement: in-app port/code fallback that reuses the same pairing path.

- **Debloater removed-package recovery**
  - User value: undo a remove-for-user debloat mistake without hunting through
    Android shell commands.
  - Entry point: Debloater selection action `action_put_back`.
  - Main code: `DebloaterActivity`, `PackageInstallerCompat`, `PackageInstallerService`.
  - Maturity: hidden/stale; TODO exists, install-existing engine exists, no UI
    or batch op connects them.
  - Improvement: selected-row Put back with per-package results and audit rows.

- **App Info action rail**
  - User value: keep frequent actions reachable on phone-width screens.
  - Entry point: App Details -> Info tab horizontal actions.
  - Main code: `AppInfoFragment.getHorizontalActions()`, `ActionItem`,
    `pager_app_info.xml`.
  - Maturity: complete but rigid.
  - Improvement: stable action IDs, priority/default order, and user reset/reorder.

### Competitive and Ecosystem Research

- **Permission Manager X / Quest pairing workaround** was cited in upstream
  #1975 as using an in-app pairing prompt rather than notification reply. This
  fits NG because it keeps all ADB credentials local and still uses the existing
  mDNS/ADB pairing engine. Avoid making it Quest-only; expose it as a generic
  fallback for surfaces where `RemoteInput` cannot collect text.
- **Android platform wireless debugging** requires QR or pairing-code pairing.
  NG's in-app fallback should therefore collect only the same host/port/code
  tuple the platform already expects, not invent a separate credential model.
- **PackageInstaller install-existing** is a platform-supported operation for a
  package that already exists on-device for the target user. NG already wraps
  it; Debloater recovery should reuse that support instead of shell-building
  bespoke commands where the installer path works.
- **App Info action density** mirrors the upstream complaint in #1953. The
  maintainer's response points to a broader action-surface overhaul, so NG's
  scoped version should prioritize common actions and customization without
  adding another large redesign layer.

### Promoted Roadmap Items

- **P1 - In-app Wireless ADB pairing-code fallback**
  - Evidence: #1975 plus `AdbPairingService.inputPairingCode()` RemoteInput-only
    port/code collection.
  - Verification: host tests for port/code handoff and cancellation; manual
    pairing on a normal device plus Quest/multi-window hardware.

- **P1 - Debloater "Put back" install-existing restore**
  - Evidence: #1977/#1969 plus `DebloaterActivity` `action_put_back` TODO and
    existing `PackageInstallerCompat.installExisting()`.
  - Verification: eligibility/result tests plus remove-for-user -> put-back on
    a known restorable system package.

- **P2 - App Info action rail priority/customization**
  - Evidence: #1953 plus fixed action append order in
    `AppInfoFragment.getHorizontalActions()`.
  - Verification: order-resolution tests and phone-width App Info checks across
    force-stop/frozen/archived/data-only states.

### Reliability, Security, Privacy, and Data Safety

- Wireless pairing fallback should keep pairing code handling ephemeral: no
  persistence beyond existing `ServerConfig` host/port state, no logs containing
  the code, and cancellation must stop mDNS scanning/foreground service state.
- Debloater Put back is a data-safety/recovery feature. It should clearly
  distinguish "package base still exists for this user/device" from "cannot be
  restored without an APK/backup" and should not imply private app data is
  restored by install-existing alone.
- App Info action customization must not weaken existing destructive-action
  confirmations/auth gates; it only changes ordering/visibility.

### UX, Accessibility, and Trust

- Wireless pairing should not require users to leave the app to reply to a
  notification when the device UI does not support that pattern.
- Debloater recovery belongs near the same selection context that removed the
  package. A per-package result list should tell the user exactly what was
  restored or skipped.
- App Info action order should keep high-frequency actions reachable while
  preserving TalkBack labels and predictable reset defaults.

### Architecture and Maintainability

- Pairing fallback should avoid duplicating pairing logic: expose a small
  service/broker method that accepts `(port, code)` and keeps
  `AdbConnectionManager.pairLiveData()` as the single execution path.
- Debloater Put back can either add a new `BatchOpsManager` op or a narrowly
  scoped Debloater worker; if it needs operation history and notifications, the
  batch path is likely cleaner.
- App Info action ordering needs stable action IDs in `ActionItem`; relying on
  string/icon pairs will make preference migration brittle.

### Quick Wins

- Add tests around `AdbPairingService` input parsing and cancellation before the
  UI fallback.
- Wire `action_put_back` to an eligibility dialog first, even before broad batch
  polish.
- Give `ActionItem` stable IDs and a default priority table; customization can
  layer on top.

### Larger Bets

- Full App Details action-surface overhaul after T21-H two-pane work, including
  adaptive primary actions on large screens.
- A broader recovery center that merges Debloater Put back, per-app rollback,
  install-existing, and backup restore into one review surface.

### Explicit Non-Goals

- **Split APK to monolithic APK conversion (#1954):** upstream rejected it as a
  destructive resigning process with no guarantee the output works. NG should
  keep native split-session install and split backup/export paths instead.
- **Duplicate APK verification row (#1974):** NG already exposes certificate and
  installer verification surfaces; no new row without a source gap.
- **Duplicate per-app rollback row (#1959):** App Details already has
  "Revert AppManager changes" and operation-history-backed rollback planning.
- **Duplicate Android 17 app-list row (#1948):** covered by the active Android
  17 target SDK 37 pre-bump/device gate.
- **Duplicate widget dynamic-color row (#1944):** NG already uses
  `AppWidgetThemeUtils` on Screen Time, Data Usage, Clear Cache, and log
  recording widgets.
- **Foreground-service crash duplicate (#1978):** NG already declares
  `FOREGROUND_SERVICE_SPECIAL_USE` and service foreground types. Play-specific
  subtype review properties can be folded into a future Play/distribution
  checklist if AppManagerNG ever targets Play.

### Open Questions

- Does Quest 3 expose enough multi-window support for an in-app pairing dialog
  to stay visible while the platform pairing code is displayed? Issue #1975
  says yes, but NG still needs device verification.
- Should Debloater Put back go through `BatchOpsManager` or a Debloater-local
  worker? The answer depends on whether maintainers want op-history/retry
  semantics identical to other batch operations.
- Should App Info action order be user-customizable immediately, or should the
  first pass only ship a smarter default priority order? The upstream feedback
  points to a larger overhaul, but the source change can be staged.

---

## 12. Research Refresh - release trust and overlay/profile delta (2026-06-04 Cycle 5)

This pass re-synced `main` at `889ecd1`, re-read the live roadmap/completed
ledger, scanned the repo for release trust controls, overlay/profile/AppOps
surfaces, and refreshed upstream AppManager, Shizuku, UAD-NG, Canta, Android,
Gradle, GitHub Actions, F-Droid, Accrescent, CycloneDX, SPDX, SLSA, and
OpenSSF source material. No feature code was changed; the output is a planning
delta promoted to `ROADMAP.md`.

### Executive Summary

AppManagerNG already closed the most visible June 4 upstream issue deltas:
Wireless ADB pairing fallback, Debloater Put back, App Info action rail,
KernelSU diagnostics, File Manager search, clear-data fallback, and AppOps
revert monitoring are documented as shipped or represented by device gates. The
remaining net-new opportunity is to improve trust and edge-case honesty around
the app rather than add another large feature surface.

Promoted opportunities:

1. `[Promoted P1]` Add Gradle dependency verification and dependency locking.
2. `[Promoted P2]` Publish release SBOM and provenance attestation.
3. `[Promoted P1]` Harden the foreground UI tracker overlay against
   device-freeze reports.
4. `[Promoted P2]` Add Private Space/profile visibility diagnostics.
5. `[Promoted P2]` Add profile membership inverse filters.

### Evidence Reviewed

- **Local files and code paths:** `ROADMAP.md`, `COMPLETED.md`,
  `RESEARCH_REPORT.md`, `README.md`, `PROJECT_CONTEXT.md`, `versions.gradle`,
  `build.gradle`, `settings.gradle`, `gradle/wrapper/gradle-wrapper.properties`,
  `.github/workflows/release.yml`, `.github/workflows/tests.yml`,
  `.github/workflows/dependency-scan.yml`,
  `docs/distribution/reproducible-builds.md`,
  `app/src/main/AndroidManifest.xml`, `users/Users.java`,
  `compat/PackageManagerCompat.java`, `compat/AppOpsManagerCompat.java`,
  `filters/options/AppOpsOption.java`, `main/MainListOptions.java`,
  `main/MainViewModel.java`, `accessibility/NoRootAccessibilityService.java`,
  and `accessibility/activity/TrackerWindow.java`.
- **Git history:** `rtk git log -10` from `889ecd1` back through the Wear OS
  blocker, root module inventory, benchmark smoke journeys, device-gate splits,
  Material/card transitions, and Android 17 blocker refresh.
- **Local source-truth deltas:** Gradle wrapper distribution checksum exists,
  OWASP dependency checking exists, and release reproducibility exists; no
  `gradle/verification-metadata.xml`, dependency lockfile, SBOM release asset,
  or provenance attestation workflow step was found. [Verified]
- **Could not verify:** physical-device overlay freeze behavior, Android 15+
  Private Space locked/hidden profile behavior, release attestation after a real
  tag, and any external store submission state. These remain device/external
  gates.

### External Source Register

Primary and project-source material reviewed or rechecked:

1. https://docs.gradle.org/current/userguide/dependency_verification.html
2. https://docs.gradle.org/current/userguide/dependency_locking.html
3. https://docs.gradle.org/current/userguide/gradle_wrapper.html
4. https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations
5. https://github.com/actions/attest
6. https://github.com/actions/attest-build-provenance
7. https://slsa.dev/spec/v1.0/provenance
8. https://github.com/slsa-framework/slsa-github-generator
9. https://github.com/CycloneDX/cyclonedx-gradle-plugin
10. https://cyclonedx.org/docs/
11. https://spdx.dev/learn/areas-of-interest/software-bill-of-materials/
12. https://docs.github.com/en/rest/dependency-graph/dependency-submission
13. https://securityscorecards.dev/
14. https://github.com/ossf/scorecard
15. https://f-droid.org/docs/Reproducible_Builds/
16. https://f-droid.org/docs/All_our_APIs/
17. https://accrescent.app/docs/
18. https://accrescent.app/features
19. https://accrescent.app/docs/guide/getting-started/new-app.html
20. https://developer.android.com/developer-verification
21. https://developer.android.com/about/versions/17/behavior-changes-17
22. https://developer.android.com/about/versions/17/setup-sdk
23. https://developer.android.com/about/versions/15/behavior-changes-all#private-space
24. https://source.android.com/docs/security/features/private-space
25. https://developer.android.com/reference/android/Manifest.permission#ACCESS_HIDDEN_PROFILES
26. https://developer.android.com/reference/android/os/UserManager
27. https://developer.android.com/reference/android/content/pm/LauncherApps
28. https://developer.android.com/training/package-visibility
29. https://developer.android.com/reference/android/view/WindowManager.LayoutParams
30. https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
31. https://developer.android.com/topic/security/risks/tapjacking
32. https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
33. https://developer.android.com/reference/android/app/AppOpsManager
34. https://developer.android.com/reference/android/content/pm/PackageInstaller
35. https://github.com/MuntashirAkon/AppManager/issues/1848
36. https://github.com/MuntashirAkon/AppManager/issues/1755
37. https://github.com/MuntashirAkon/AppManager/issues/1863
38. https://github.com/MuntashirAkon/AppManager/issues/1948
39. https://github.com/MuntashirAkon/AppManager/issues/1980
40. https://github.com/MuntashirAkon/AppManager/issues/1975
41. https://github.com/MuntashirAkon/AppManager/issues/1969
42. https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0
43. https://github.com/RikkaApps/Shizuku/issues/2095
44. https://github.com/RikkaApps/Shizuku/issues/2114
45. https://github.com/RikkaApps/Shizuku/issues/2128
46. https://github.com/RikkaApps/Shizuku/issues/2136
47. https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases/tag/v1.2.0
48. https://github.com/samolego/Canta/releases/tag/3.2.2
49. https://github.com/ImranR98/Obtainium

### Current Feature Inventory Delta

- **Release integrity:** `scripts/verify_reproducible_release.sh` and
  `.github/workflows/release.yml` already perform two signed builds and compare
  APK bytes. Release assets are APKs plus `.sha256` sidecars. This is strong
  reproducibility, but it is not component inventory or provenance. [Verified]
- **Dependency integrity:** the wrapper has a distribution SHA-256, and OWASP
  dependency-check can fail scheduled audits on high CVSS findings. The build
  still resolves dependencies from three remote repositories without Gradle
  verification metadata or locks, so malicious or unexpected artifact drift can
  be detected only after resolution, not blocked by known-good checksums.
  [Verified]
- **Foreground UI Tracker:** `TrackerWindow` is constructed inside an
  accessibility-service flow but uses `TYPE_APPLICATION_OVERLAY` on Android O+
  and `FLAG_LAYOUT_NO_LIMITS`. The window width uses a fixed display-width
  expression, and add/remove/update calls are direct. The existing accessibility
  label tests prove action text, not device/OEM overlay safety. [Verified]
- **Private/profile visibility:** the app declares `ACCESS_HIDDEN_PROFILES`,
  falls back to `UserManager.getProfiles()`, and uses `LauncherApps` for launch
  checks. No source-visible Private Space type/quiet/hidden diagnostic path was
  found. [Verified]
- **Profile filtering:** selecting a profile in `MainListOptions` stores one
  filter profile name, and `MainViewModel` expands that into positive include
  filters for package-list or filter-based profiles. No inverse membership state
  was found. [Verified]
- **AppOps:** `AppOpsOption` already supports mode predicates, and
  `AppOpsManagerCompat` merges UID/package ops for reads and chooses
  `setUidMode()` vs `setMode()` for writes, with unit tests around
  `usesUidModeForSetMode()`. Upstream #1863 is therefore not promoted as a
  fresh roadmap item until a narrower UI source gap is proven. [Verified]

### Promoted Roadmap Items

- **P1 - Gradle dependency verification and dependency locking**
  - Why: blocks remote artifact/checksum drift before build use.
  - Evidence: `gradle/wrapper/gradle-wrapper.properties:3`,
    `build.gradle:24-29`, `build.gradle:52-68`, plus Gradle dependency
    verification and locking docs.
  - Verification: metadata bootstrap, lock generation, normal test task, and a
    seeded temporary mismatch failure.

- **P2 - Release SBOM and provenance attestation**
  - Why: turns reproducible APK bytes into externally inspectable release trust
    artifacts.
  - Evidence: `.github/workflows/release.yml:1-4`, `:19-20`, `:73-94`,
    `docs/distribution/reproducible-builds.md:5-8`, `:23-41`, plus GitHub
    attestation and CycloneDX Gradle plugin docs.
  - Verification: SBOM schema validation, workflow lint/dry-run, and
    `gh attestation verify` after a real tag release.

- **P1 - Foreground UI tracker overlay safety**
  - Why: an upstream device-freeze report maps to local no-limit application
    overlay behavior.
  - Evidence: upstream #1848 plus `TrackerWindow.java:73-84`, `:183-186`,
    `:225`, `:269-271`, Android `WindowManager.LayoutParams`, and Android 12
    untrusted-touch docs.
  - Verification: host policy tests plus manual Android 11/12+/current-target
    tracker walkthroughs.

- **P2 - Private Space/profile visibility diagnostics**
  - Why: hidden/private profile visibility needs explicit user-facing honesty in
    a package-management app that already declares the hidden-profile permission.
  - Evidence: `AndroidManifest.xml:15`, `Users.java:89-93`,
    `PackageManagerCompat.java:345-355`, AOSP Private Space docs, Android 15
    Private Space behavior docs, `UserManager`, and `LauncherApps`.
  - Verification: profile label/state tests plus Android 15+ Private Space
    locked/unlocked/hidden manual pass.

- **P2 - Profile membership inverse filters**
  - Why: lets users find apps not covered by a selected profile without exporting
    or manually diffing package lists.
  - Evidence: upstream #1755, `MainListOptions.java:250-258`, and
    `MainViewModel.java:617-632`.
  - Verification: static AppsProfile and filter-profile include/exclude tests.

### Checked but Not Promoted

- **AppOps per-UID/package issue (#1863):** source already merges UID/package op
  reads and chooses UID-mode writes where required. Do not add a duplicate until
  a specific UI/operation mismatch is reproduced in NG. [Verified]
- **Upstream #1980, #1975, #1969, #1967, #1964, #1963, #1958, #1956, and
  #1953:** already shipped or represented in `COMPLETED.md` / the active
  roadmap. [Verified]
- **Shizuku v13.6.0 and issues #2095/#2114/#2128/#2136:** reinforce existing
  Android 17, Shizuku/ADB, Quest/wireless pairing, and device-gated validation
  rows; no duplicate source item was promoted. [Verified, external]
- **UAD-NG v1.2.0 and Canta v3.2.2 releases:** no new machine-readable
  package/model/region data source changed the existing UAD external blocker.
  [Verified, external]
- **F-Droid/Accrescent/Android developer verification:** current distribution
  rows already cover external submission/developer verification. The only new
  release-trust work promoted here is SBOM/provenance, not another store row.
  [Verified, external]

### Quality, Security, Accessibility, and Operations Notes

- Dependency verification and locks should cover production, test, benchmark,
  plugin, and build-logic configurations. JitPack deserves explicit review
  notes because it builds artifacts from upstream repositories rather than from a
  central signed publication flow.
- SBOM/provenance work should preserve existing two-build reproducibility. It
  adds release evidence; it must not weaken byte-for-byte comparison or release
  signing.
- Tracker overlay work should prefer smaller extracted policy helpers so bounds,
  throttle, and failure behavior can be host-tested without needing a live
  WindowManager.
- Private Space diagnostics must avoid false certainty. If a profile is hidden,
  quiet, locked, or inaccessible from the current privilege mode, the UI should
  say that plainly rather than claiming all apps were scanned.
- Profile inverse filters are safe as app-list filtering only. They must not
  change profile apply/import/export semantics unless that is separately scoped.

### Explicit Non-Goals

- Do not bump `compile_sdk`/`target_sdk` to 37 in this cycle; the existing
  Android 17 pre-bump gate remains blocked on an API 37 SDK/image/device.
- Do not add new AppOps mode-filter rows; mode predicates and UID-mode write
  handling are already present in source/tests.
- Do not replace the reproducible release workflow. SBOM and provenance are
  additive release assets.
- Do not implement feature code from this report in the research lane.
