<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG
Date: 2026-08-10 — replaces all prior research.

Confidence labels: [Verified] found in the repository or an authoritative source;
[Likely] supported by reachable code and ecosystem evidence but not reproduced in a
release/device run; [Assumption] a design choice to validate during implementation;
[Needs live validation] requires an OEM/device, release artifact, or external service.

## Executive Summary

[Verified] AppManagerNG (v0.6.12, 2026-08-08) is a local-first, GPL-3.0-or-later Android
package-management suite: inspection, Finder queries, privileged operations across five
privilege modes, installer with preflight disclosure, encrypted backups, profiles/routines,
debloater, and a fail-closed reproducible release gate. The active roadmap was drained on
2026-08-10; ~50 items sit in `Roadmap_Blocked.md`, mostly device/external-gated. The
highest-value direction now is **reconciling with upstream's v4.1.0 (2026-06-29) bugfix run**
— the first upstream release since the fork point — plus a small set of truthfulness and
data-completeness fixes the internal scan surfaced, rather than any new subsystem.

Top opportunities, in priority order:

1. [P1, Verified] README still claims the CVE gate blocks on 12 unassessed findings; v0.6.8
   resolved all 12 with evidence-backed suppressions. The project's own trust surface misstates
   its release integrity.
2. [P1, Verified] Upstream shipped ~15 portable correctness fixes between 2026-05-25 and
   2026-06-29 (v4.1.0) that post-date the fork point; none are recorded as ported.
3. [P1, Likely] Upstream refreshed its hidden-API mirrors from Android 16 sources
   (`eff7f587`+`04ed88d0`); the fork's `hiddenapi/` module has not taken the refresh.
4. [P2, Verified] The `full` flavor still integrates Pithus, a service upstream deleted as
   defunct on 2026-05-26 — a dead network trust surface.
5. [P2, Verified] Backups silently skip Android KeyStore v2 entries (`BackupOp.java:531`,
   `RestoreOp.java:538`) with no user-facing disclosure — a completeness-truth gap in the
   feature the app stakes its trust story on.
6. ~~Update-ownership (API 34+) is entirely unused~~ → corrected 2026-08-11: claiming ownership
   on install was already fully implemented (`InstallerOptions.requestUpdateOwnership`, a
   preference, and `SessionParams.setRequestUpdateOwnership`); the original grep searched for the
   wrong symbol. Only the read side was missing, and App Details now shows the update owner.
7. [P2, Verified] Finder's AppOps predicate ships without mode filtering although the data
   layer exposes modes and the option class declares the constants (`AppOpsOption.java:26-29`).
8. [P2, Likely] Privileged batch operations trust `pm`/`appops` exit codes; Thor's 2026 arc
   (readback verification) shows silent no-ops are the field's live failure mode.
9. [P2, Verified] Kotlin toolchain sits on a CVE'd line (CVE-2026-53914, build-only today);
   one deliberate toolchain pass (Kotlin 2.4.20 + dependency-check 13.0.0 + AGP 9.3.0)
   shares the dependency-locking churn cost.
10. [P3, Verified] Cheap API 36/37 diagnostics (pending-job reasons, MemoryLimiter exit
    descriptions, Advanced Protection detection) extend panels that already exist.

## Product Map

- **Core workflows:** inventory/search installed packages; Finder predicates + saved presets +
  batch actions; per-app inspection (components, permissions, app ops, signatures, trackers,
  native libs, recent exits); install APK/APKS/APKM/XAPK with preflight disclosure; encrypted
  backup/restore + snapshot bundles; profiles, routines, scheduled ops; debloat with OEM
  provenance; freeze/QS tiles; file manager, log viewer, terminal, code editor.
- **Personas:** privacy-focused offline users; root/ADB/Shizuku/Dhizuku/KernelSU power users;
  ROM/OEM debloaters; developers and mobile-security analysts.
- **Platforms and distribution:** Android API 21+ (policy ceiling, `docs/policy/minsdk-21-ceiling.md`),
  compileSdk 37 / targetSdk 36; `floss` (offline) and `full` flavors; GitHub Releases +
  Obtainium; IzzyOnDroid submission is operator-gated (`Roadmap_Blocked.md`); reproducible
  two-build releases with hash receipts and SBOM.
- **Integrations and data flows:** PackageManager/PackageInstaller + hidden APIs; AppOps; IFW
  component rules; WorkManager routines; SAF; Room (schema 10 — migration ladder is the P0
  blocked item); OpenPGP/BouncyCastle crypto; optional `full`-flavor VirusTotal/Pithus (see
  finding 4)/debloat-definition updates behind `FeatureController` + master Internet toggle.

## Competitive Landscape

- **Upstream App Manager** (MuntashirAkon, 7.7k★) — v4.1.0 (2026-06-29) is the only release
  since the fork point (3d11bcbc, 2026-04-16 — note: earlier docs said 2026-04-30; the commit
  itself dates 2026-04-16). Its headline features (ADB `.ab` backup, filter profiles, native
  scanner) predate the fork and are in NG's base; the real delta is the 2026-05-25→06-29 fix
  run plus HMAC ADB auth (already a blocked P1 port item). Learn: their v4.1.1/v4.2.0
  milestones (#1995 app-list export bug, #321 cross-app Finder, #138 APK editor) confirm NG is
  ahead on Finder/routines. Avoid: blind-merging v4.1.0 — it carries open perf (#2000) and
  reproducible-build (#1997) regressions.
- **Thor** (506★ but fastest-moving, v1.94.1 2026-08-09) — readback verification of privileged
  ops, UAD safety gating with hard-block of "Unsafe", extension catalog. Learn: verify state
  after `pm` claims success; publish corrections. Avoid: silent privilege escalation between
  op types (their #366 data-loss incident).
- **Hail** (6.4k★) — multi-mode freeze, Dhizuku, auto-freeze triggers, URI automation. NG has
  freeze + QS tiles + Tasker; the deltas (auto-freeze predicates, freeze surface/widget,
  launch-through) are all already tracked in `Roadmap_Blocked.md`. Avoid duplicating them.
- **LibChecker** (7.1k★, 2.5.4 2026-06-17) — per-app update snapshots + diffs, 16 KB
  alignment checks (NG shipped the Finder filter in v0.6.7), lib rules DB. Learn: the update
  snapshot/diff surface — NG records op history but cannot answer "what changed when this app
  updated". Avoid: their WebUI export (scope creep for NG).
- **Inure** (build107.2.0, 2026-07-24) — recent-exits panel (NG already ships this in App
  Details), per-app notes + tags (NG has tags only), batch profiles. Learn: notes as the
  missing companion to tags. Avoid: its everything-panel sprawl.
- **SD Maid SE** (7.2k★) — explanatory failure UX ("why cleaning was skipped"), per-schedule
  conditions, settings export. Learn: NG's failure surfaces should always name the privilege
  reason (partially shipped in Mode Doctor). Avoid: cleaner scope beyond leftover scans NG has.
- **UAD-NG** (8.8k★) + **Canta** (5.5k★) — debloat safety ratings (NG ingests the list with
  provenance since 2026-06-04), package-list updates decoupled from releases (NG: `full`
  auto-updater exists), cross-user detection. Learn: work-profile/Private Space is the field's
  weakest axis and NG's blocked multi-user matrix item is the right bet when a device is
  available. Avoid: crowdsourced vote databases (rejected 2026-08-02, still rejected).
- **InstallerX-Revived** (6.1k★) — default-installer role, notification-only install UI.
  NG covers bypass-low-target-SDK and EXTRA_RETURN_RESULT already [Verified by grep
  2026-08-10]; the remaining delta (dialog-less install UX) conflicts with NG's
  disclosure-first installer philosophy — intentionally avoid.

## Security, Privacy, and Reliability

- [Verified] **README trust-surface drift** — `README.md:264-277` says the CVE gate "currently
  blocks", findings are unassessed, and points at a P1 ROADMAP item; all three claims false
  since v0.6.8 (`config/owasp-suppressions.xml`, 13 entries with justifications; CHANGELOG
  v0.6.8). An external 2026-08-10 re-triage independently reached the same dispositions:
  androidx.sqlite = CPE false positives (DependencyCheck #1727/#6292 lineage), io.netty =
  AGP/UTP buildscript-only, Kotlin CVE-2026-53914 = real but build-tooling-only (JetBrains CNA
  vector 6.7 vs NVD 9.8).
- [Verified] **Kotlin 2.2.10 → 2.4.20** clears CVE-2026-53914 (build-cache deserialization →
  code exec). Risk is currently confined to local build tooling; it becomes a real
  supply-chain hole if a shared/remote Gradle cache is ever adopted. Bundle with
  dependency-check 13.0.0 (2026-08-03) and AGP 9.3.0 (keepRules source sets — structural
  mitigation for the v0.6.12 BC-keeps class of R8 bug) to pay the locking churn once.
- [Verified] **Runtime dependencies are at their security ceiling**: BC 1.84 (fixes
  CVE-2026-3505 PGP AEAD pre-auth DoS — relevant to OpenPGP restore), protobuf 3.25.5, guava
  32.1.3-android, zstd-jni 1.5.7-11, libsu 6.0.0, Shizuku-API 13.1.5 (no newer artifact
  exists). Vendored Commons Compress subset: no 2025-2026 CVEs; CVE-2023-42503 code path
  verified absent (`TarArchiveEntry.java:1318` uses the pre-1.22 parse). Material/Room/Work/
  Activity are policy-pinned — every newer line raises minSdk to 23; Material 1.15 does not
  exist (1.14.0, 2026-05-13, is the minSdk-23 release).
- [Verified] **Pithus is defunct** (upstream removal `0e187e83`+`2c00f69f`, 2026-05-26, deleted
  code + pinned certs) yet `scanner/Pithus.java` and its `NetworkTransparencyLedger` rows
  remain in `full` — offering users an upload to a dead endpoint.
- [Verified] **KeyStore v2 backup hole** — `backup/BackupOp.java:531` / `RestoreOp.java:538`
  skip KeyStore v2 entries with no disclosure anywhere in the backup UI or metadata.
- [Likely] **Batch ops trust exit codes** — no readback layer; a `pm disable` that silently
  no-ops on an OEM build is reported as success (Thor v1.94.0 demonstrates the failure mode
  and the fix pattern).
- [Verified] **Privileged-server status can go stale in UI** —
  `servermanager/ServerStatusChangeReceiver.java:72` drops status changes (in-code TODO).
- ~~`FEAT_INTERNET` is missing from `sFeatureFlagsMap`, so the master Internet gate can never
  appear in the feature-toggle list~~ → corrected 2026-08-11: the omission is deliberate and
  correct. The bit has its own "Use the Internet" switch in Settings → Privacy
  (`PrivacyPreferences`, `toggle_internet`); listing it again in the generic feature chooser
  would give one bit two controls that could disagree. The real exposure was that nothing
  asserted the map's completeness, so a genuinely forgotten flag would look identical — now
  covered by `FeatureFlagCoverageTest`, which names the deliberate exclusion.
- [Verified] **Developer Verification timeline** — enforcement 2026-09-30 (BR/ID/SG/TH,
  store-listed apps only) does not touch GitHub/Obtainium distribution; global certified-device
  rollout 2027 does. ADB installs are exempt (no wait, no verification), making NG's ADB/
  Shizuku session path structurally verification-proof. Maintainer decision (register $25
  console vs stay unverified) is needed before 2027, not now.
- [Verified] **Android 17 (API 37) targeting is low-risk** — no package-visibility or
  PackageInstaller behavior changes documented. The two audits worth doing ahead of the parked
  target bump: reflective static-final writes (now throw) and IntentSender background-activity
  -launch opt-ins. Native DCL read-only-`.so` rule and `ACCESS_LOCAL_NETWORK` need a check
  when the bump lands.
- **Recovery needs:** Pithus removal must leave the `floss` scanner UI unchanged; KeyStore
  disclosure must not fail backups that previously succeeded; readback verification must
  degrade to "unverified" (never block) where state cannot be re-read; the toolchain pass must
  follow `docs/distribution/dependency-verification.md` and keep the release gate green.

## Architecture Assessment

- **Boundaries to improve:** batch-op executors need a readback/verification seam so results
  carry observed state, not exit codes; `FeatureController`'s flag map should be pinned by a
  completeness test against declared flags; privileged-server status needs one observable
  path from receiver to UI.
- **Refactor candidates (from in-code TODOs, in value order):**
  `settings/Ops.java:1041` (three overlapping privileged-mode entry points — consolidation is
  upstream's own TODO but touchy; do alongside readback work, not as a drive-by);
  `profiles/struct/BaseProfile.java:49` (legacy non-UUID profile IDs — fold into any future
  profile schema change, requires migration test);
  `permission/PermUtils.java:336-379` (`FLAG_PERMISSION_POLICY_FIXED` deliberately commented
  out twice — investigate and either honor or document before any permission-UI work);
  `editor/CodeEditorViewModel.java:68,219` (language defs not shipped as assets; stale
  serializer).
- **Test gaps:** `:libcore:io` (remote FD/path transport) has zero tests and no proxy suite —
  host-testable parts belong in `app/src/test` per the dependency-locking constraint recorded
  2026-08-02 (do NOT add a new test source set); `SBConverterTest.java:158` is commented out
  (Robolectric can't parse APKs) — the zip-comment JSON validation fix should revive it with a
  parser-level fixture; readback verification needs a simulated silent-no-op fixture;
  `FeatureController` map completeness test absent.
- **Docs debt:** `RESEARCH_REPORT.md` (2026-06-03) is two months stale and cites a removed
  workflow — treat as historical only. `COMPLETED.md` ledgers stop before the 2026-08-02 pass;
  CHANGELOG is the accurate record. README CVE block is finding 1.
- **Upgrade constraints:** minSdk 21 ceiling intact; AndroidX/Material upgrades remain
  policy-blocked. Room schema changes (notes item) must land with explicit migrations while
  the destructive-fallback P0 stays blocked — new tables only, no destructive path reliance.
- **Coverage decisions (2026-08-10):** security/reliability/data-truth are the P1/P2 additions;
  accessibility, visual polish, empty-state system, multi-user matrix, and device walkthroughs
  are consciously excluded here because each already has a `Roadmap_Blocked.md` row awaiting
  device access; i18n intake stays external-gated (fork-owned Weblate row) with the host-side
  ratchet already shipped; distribution (IzzyOnDroid submission, screenshots) stays
  operator/device-gated; plugin ecosystem, cloud/multi-device, and desktop remain rejected;
  migration paths are constrained by the blocked P0 Room ladder as noted above.

## Rejected Ideas

- **Freeze layer as a "biggest gap"** — competitor-research suggestion; wrong: NG inherits
  freeze/unfreeze, QS freeze tile, and FreezeUnfreezeActivity, and the real deltas
  (auto-freeze predicates, freeze surface/widget, launch-through frozen apps, Dhizuku parity)
  are each already in `Roadmap_Blocked.md`. (Hail/Thor comparison, 2026-08-10.)
- **Recent-exits panel, EXTRA_RETURN_RESULT install results, low-target-SDK install bypass,
  200 ms search debounce** — proposed by competitor/upstream research; all already shipped
  [Verified by grep 2026-08-10: `AppInfoFragment.java:3768`, `PackageInstallerActivity.java:264`,
  `PackageInstallerCompat.java:415`, `MainActivity.java:122`]. Future researchers: grep first.
- **Default-installer role / notification-only installs** (InstallerX) — conflicts with NG's
  disclosure-first installer contract; the prompt IS the product.
- **Cloud backup targets (Drive/WebDAV/S3), SMS/call-log backup** (Swift Backup) — offline/
  floss boundary; rejected 2026-08-02, unchanged.
- **Crowdsourced debloat votes, AI privacy scores, telemetry** — rejected 2026-08-02, unchanged.
- **Compose rewrite, desktop/iOS, Android TV bet** — rejected 2026-08-02; TV D-pad item stays
  blocked. Material 1.14+ (minSdk 23, M3 Expressive) additionally hard-blocks any near-term
  M3-Expressive restyle — also skip upstream's M3 preference restyle commits (`e24eb8d0` etc.)
  since NG has its own V2 theme system.
- **Upstream sora-editor/ARSCLib pin adoption** — fork is AHEAD of upstream on both (0.24.6 vs
  0.22.2; V1.4.0 vs older fork commit). Nothing to take.
- **xz-java CVE work** — `org.tukaani` is not in any lockfile; recent xz CVEs are Go-port/
  native-only. No exposure.
- **Netty/androidx.sqlite dependency upgrades to clear the CVE gate** — findings are FP/
  build-only; suppressions (shipped v0.6.8) are the correct mechanism, independently
  re-validated 2026-08-10.
- **LibChecker WebUI export, Inure panel sprawl, PDF viewer (upstream #2011)** — scope creep
  beyond package management.

## Sources

### Direct OSS
https://github.com/MuntashirAkon/AppManager (v4.1.0 release, commits 2026-05-25→06-29, issues #1956/#1963/#1967/#1995/#2000/#1997/#2003, milestones v4.1.1/v4.2.0)
https://github.com/trinadhthatakula/Thor (v1.94.0/v1.94.1)
https://github.com/aistra0528/Hail (v1.10.0; issues #258/#88/#266/#352)
https://github.com/LibChecker/LibChecker (2.5.4)
https://github.com/Hamza417/Inure (build107.1.0/107.2.0)
https://github.com/d4rken-org/sdmaid-se (v2.0.1-beta1/v2.0.2-rc0; issues #1364/#1496/#1649)
https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation (v1.2.0; issues #1311/#583)
https://github.com/samolego/Canta (v3.2.2; issue #350)
https://github.com/lihenggui/blocker (v2.0.6339; issue #1565)
https://github.com/wxxsfxyzm/InstallerX-Revived (26.08 preview)
https://github.com/NeoApplications/Neo-Backup (8.3.17/8.3.18)
https://github.com/thedjchi/Shizuku (Android 16/17-compatible fork)
https://github.com/timschneeb/awesome-shizuku

### Commercial and adjacent
https://swiftbackup.app
https://appdash.app
https://adbappcontrol.com

### Platform and standards
https://developer.android.com/about/versions/17/behavior-changes-17
https://developer.android.com/about/versions/17/features
https://developer.android.com/about/versions/16/features
https://developer.android.com/developer-verification
https://developer.android.com/developer-verification/guides/faq
https://android-developers.googleblog.com/2026/06/android-developer-verification.html
https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
https://izzyondroid.org/docs/general/AppInclusionPolicy/
https://izzyondroid.org/docs/reproducibleBuilds/
https://reproducible-builds.org/reports/2026-06/
https://developer.android.com/guide/practices/page-sizes
https://developer.android.com/jetpack/androidx/releases/room
https://developer.android.com/jetpack/androidx/releases/work

### Dependencies, advisories, tooling
https://github.com/material-components/material-components-android/releases (1.14.0 = minSdk 23)
https://developer.android.com/build/releases/agp-9-3-0-release-notes
https://github.com/advisories/GHSA-r937-wjx7-w2jp (CVE-2026-53914)
https://www.tenable.com/cve/CVE-2026-53914
https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/
https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/
https://github.com/dependency-check/DependencyCheck/releases (13.0.0)
https://github.com/jeremylong/DependencyCheck/issues/1727 (androidx.sqlite CPE FP lineage)
https://commons.apache.org/compress/security.html
https://netty.io/news/2025/09/03/4-2-5.html
https://github.com/topjohnwu/libsu/releases
https://central.sonatype.com/artifact/dev.rikka.shizuku/api
https://github.com/CodeIntelligenceTesting/jazzer/releases

## Open Questions

- Whether `beta.pithus.org` is permanently dead or migrated (affects removal vs re-point;
  upstream chose removal — default to removal, record the probe result in the commit).
- Maintainer decision, needed before the 2027 global Developer Verification rollout: register
  in the Android Developer Console ($25, government ID) vs remain unverified (advanced-flow/
  ADB-only installs on certified devices). Not answerable by research; no 2026 deadline.
- Whether LSPosed HiddenApiBypass has published a 6.2+ release lifting the deprecated
  `addHiddenApiExemptions` path (`AppManager.java:121` pin note) — check during the toolchain
  pass; not release-gating.
