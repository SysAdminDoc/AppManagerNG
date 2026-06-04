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
- #1963 `ActivityNotFoundException: DebloaterActivity` (moto g22) — add CI lint that resolves every shortcut/launcher target on minSdk; register the missing `<activity-alias>`.
- #1962 / #1961 / #1960 / #1957 / #1956 — Android 16 root/binder regressions, an "Android 16 compat" tracking effort gating every `IBinder` call site by API level + reflective fallback (`compat/android16/`), Shizuku mode-picker promotion to a M3 SegmentedButton row, squashfs writer validation vs `mksquashfs 4.6`, and an "OS reverted your change" banner for battery-optimization re-flagging.

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
