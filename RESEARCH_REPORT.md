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

- **B1. Privacy Sandbox / SDK Runtime enumeration** — `SdkSandboxManager.getSandboxedSdks()` (API 34+); runtime-truth "SDK Sandbox" row in the Trackers tab.
- **B2. Domain Verification audit** — `DomainVerificationManager.getDomainVerificationUserState()` (API 31+); per-domain verified/declined/unverified + deep-link hijack inspector; system-wide "Deep Link Conflicts" finder as follow-up.
- **B3. App Archiving** — `PackageInstaller.requestArchive()/requestUnarchive()` (API 35+); third app state (Active/Frozen/Archived), zero-privilege storage reclaim; App Info action + batch entry.
- **B4. Memory Tagging Extension (MTE) status** — manifest `allowNativeHeapPointerTagging` + runtime MTE state; security chip in the App Info tag cloud.
- **B5. Health Connect dashboard** — `HealthConnectClient.getGrantedPermissions()` (API 34); "which apps read my heart rate / sleep / steps?".
- **B6. Credential Manager audit** — passkey/password/federated-identity provider registration; pairs with B5 as a Privacy Dashboard tab.
- **B7. Restricted Settings unlock walkthrough** — first-run wizard detecting the Android 13+ restriction and deep-linking to the right Settings screen (the #1 sideload support question); fold into the T5 Privilege Health-Check screen.
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
`TileService.requestAddTileService()`: ship "Run Freeze Profile" + "Force-Stop
Pinned App". DocumentsProvider exposing `am://backups` + `am://profiles` makes
backups/profiles first-class SAF citizens.

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
- **O-07 Macrobenchmark module** (effort 4) — `:benchmark` measuring cold-start / list-scroll jank / Backups TTI; nightly device CI; Baseline Profile for the app-list path.
- **O-08 Espresso + UI Automator smoke pack** (effort 4) — headless device suite (open list, freeze/unfreeze, component blocker, one-shot rule) in `connectedCheck` on API 26/30/34/35.
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

- #1968 Automating Save APK — replace/extend the Activity profile launcher with a `ProfileApplierReceiver` accepting `extra_pkg` template substitution (also fixes the Recents-foregrounding complaint and the Android 17 BAL deprecation).
- #1967 Root not detected after reinstall on KernelSU — audit `RootSession`/`Ops.java` KernelSU probe order; add a "force re-grant" recovery flow.
- #1966 App Info popup density / SDK row position — restructure `app_info_card.xml` (SDK row up, two-column trackers/SDK, expose cert Subject, max-height 92%).
- #1965 Clear-data no-op on Android 16 QPR2 — add fallback `pm clear --user N` shell path + post-call disk-usage delta as ground truth.
- #1964 File Manager search/filter — SearchView on `FmActivity` + debounced recursive filter on `FmAdapter`.
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
