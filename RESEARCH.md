<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG
Date: 2026-08-11 — replaces all prior research.

Confidence labels: **[Verified]** reproduced in this repository or read from an authoritative
source during this pass; **[Likely]** supported by reachable code plus ecosystem evidence but not
executed; **[Assumption]** a design choice to validate during implementation;
**[Needs live validation]** requires a device, release artifact, or external service.

## Executive Summary

[Verified] AppManagerNG is a GPL-3.0-or-later, offline-first Android package-management suite:
1,650 Java files (**zero Kotlin**), 10 Gradle modules, ~2,557 host tests, minSdk 21 /
targetSdk 36 / compileSdk 37, Views + Material 1.13.0, five privilege modes, a fail-closed
reproducible release gate. Working tree is clean at `1cb5fa6ad` (`chore: release v0.6.13`).

Two facts reframe the whole roadmap:

1. **[Verified] Upstream is code-frozen.** `MuntashirAkon/AppManager` has had **zero commits on
   any branch since 2026-06-29**, and the four post-`v4.1.0` commits are already absorbed,
   inapplicable, or upstream-release-tooling only. The fork owes upstream nothing. The
   corollary is expensive: every Android 17 QPR and OEM quirk is now NG's problem alone, and the
   prior research pass's headline direction ("reconcile with upstream v4.1.0") is **closed**.
2. **[Verified] NG has not published a release since v0.6.12 (2026-08-08), and cannot.** The
   release-consistency gate fails at HEAD, by construction — see finding 1. v0.6.8, v0.6.9,
   v0.6.10, v0.6.11 and v0.6.13 all exist in `CHANGELOG.md` with dates but have **no git tag and
   no GitHub release**. The project's core trust claim ("every release is built twice and
   published only if byte-identical, with a receipt") is currently describing a pipeline that
   cannot complete.

Top opportunities, in priority order:

1. **[P0, Verified] The release gate has a circular dependency and blocks its own publish.**
   `scripts/verify-release-consistency.sh:146-165` runs `verify_release_metadata.py`, which
   asserts `docs/distribution/release-receipt.json` (a record of the *last published* release)
   matches the working tree's `versionName`/`versionCode`. Those can only agree after publishing.
   Live run at HEAD: `versionName 0.6.13 != receipt 0.6.12` / `versionCode 21 != receipt 20` →
   `FAILED`. Five of the last six documented releases have no artifact.
2. **[P1, Verified] BouncyCastle 1.84 is no longer the ceiling.** `bcprov-jdk18on` **1.85**
   shipped 2026-07-12 and **1.85.2** on 2026-08-07 (Maven Central directory timestamps). NVD
   published ~30 "Bouncy Castle for Java before 1.85" CVEs on 2026-08-03, several on paths this
   app reaches: BKS/UBER keystore untrusted-length allocation (CVE-2026-12185), legacy BKS
   16-bit MAC key (CVE-2026-59651), PKCS#12 and PBES2 unbounded KDF cost (CVE-2026-13586,
   CVE-2026-15055), RSA PKCS#1 verification skipping two hash bytes (CVE-2026-12860), unbounded
   ASN.1 allocation and nesting-guard reset (CVE-2026-14682, CVE-2026-13506), quadratic X.500
   name stringification (CVE-2026-58059). The prior pass's "runtime dependencies are at their
   security ceiling" is superseded.
3. **[P1, Verified] Enumeration can succeed and still be wrong.**
   `compat/PackageManagerCompat.java:132-172` cross-checks two *privileged* enumeration calls
   against each other; when the privileged path itself short-returns, both agree and the code
   returns "everything's loaded correctly". This is exactly the field's most-reported 2026
   failure (upstream #1948/#2002/#2019: one-app or empty lists on Pixel 7/9a and OneUI 8.5).
4. **[P1, Verified] Default export filenames are built from the locale date/time format**, so
   they contain `/` and `:` and SAF rejects or mangles them (upstream #1995). Six sites; the
   repo already has the correct sanitiser and a test for it, used only by profiles.
5. **[P2, Verified] Eleven languages have no base-language string resources.** bn, in, it, nb,
   ru, tr and zh have no `values-<lang>` directory at all; cs, es and uk have one containing only
   `disclaimer.xml`. Pre-API-24 resource resolution has no same-language/other-region fallback,
   so an API 21-23 device set to `ru-UA`, `es-MX`, `cs-SK` or `zh-HK` gets English despite a
   complete translation shipping — precisely the users the minSdk-21 policy exists to protect.
   Only ar and pt are wired correctly (base translation plus a regional overlay).
6. **[P2, Verified] The Neo Backup importer is keyed to a format Neo Backup stopped writing.**
   `backup/convert/OABConverter.java:197-215` parses `<packageName>.log`; current Neo Backup
   stores `backup.properties` inside `YYYY-MM-DD-HH-MM-SS[-mmm]-user_N` directories
   (`Constants.kt: BACKUP_INSTANCE_PROPERTIES_INDIR`, `BACKUP_INSTANCE_REGEX_PATTERN`).
   Neo Backup has been unpushed since 2026-05-03 with 239 open issues; upstream #2020 (filed
   2026-08-06) is a user asking for exactly this import.
7. **[P2, Verified] The no-root accessibility path blocks its own main thread for up to 5 s.**
   `accessibility/NoRootAccessibilityService.java:73,147` sleep inside `onAccessibilityEvent`,
   and `BaseAccessibilityService.java:242-248` loops `SystemClock.sleep(500)` ten times.
8. **[P2, Verified] `buildTime()` silently falls back to wall-clock** outside a git tree
   (`app/build.gradle:336-343`), writing a non-deterministic `BUILD_TIME_MILLIS` into
   `BuildConfig`. The two-build gate runs in one tree and cannot see it. Upstream's
   reproducibility has been publicly written off by IzzySoft over a related `am.jar`/`main.jar`
   nondeterminism (upstream #1997) — an axis IzzyOnDroid actively grades and NG could win.
9. **[P2, Verified] Developer Verification enforcement starts 2026-09-30** (BR/ID/SG/TH) and the
   installer-facing APIs landed in **API 36.1** (`getDeveloperVerificationServiceProvider()`,
   `EXTRA_DEVELOPER_VERIFICATION_*`, `SessionParams.setExtensionParams`). Nobody in the field has
   shipped advanced-flow-aware install disclosure. **[Likely]** an installer at
   `targetSdk > 36` loses the user-bypass path for unverified APKs — a concrete argument for
   holding targetSdk at 36.
10. **[P2, Verified] Toolchain floors moved**: AGP 9.3.1 (2026-07-23), Gradle 9.7.0 (2026-08-06),
    Kotlin stable 2.4.10 (2026-07-14 — **2.4.20 is a Beta**, correcting the existing roadmap
    row). Kotlin appears only on the buildscript classpath via AGP
    (`buildscript-gradle.lockfile:127-132`, all `2.2.10`), so AGP is the lever, not a Kotlin pin.

## Product Map

- **Core workflows:** inventory + Finder predicates with saved presets and batch actions;
  per-app inspection (components, permissions, app ops, signatures, trackers, native libs,
  recent exits); privileged operations across root / ADB-over-TCP / Shizuku / Dhizuku / no-root;
  install APK/APKS/APKM/XAPK with preflight disclosure; encrypted backup/restore + snapshot
  bundles; profiles, routines, scheduled ops; debloat with OEM provenance; freeze + QS tiles;
  file manager, log viewer, terminal, code editor.
- **Personas:** privacy-focused offline users; root/ADB/Shizuku/Dhizuku/KernelSU power users;
  ROM and OEM debloaters; developers and mobile-security analysts.
- **Platforms and distribution:** API 21+ (`docs/policy/minsdk-21-ceiling.md`), compileSdk 37 /
  targetSdk 36; `floss` (offline, default) and `full` flavors; GitHub Releases + Obtainium +
  AppVerifier pairing + ROM F-Droid pre-seed. IzzyOnDroid and F-Droid packets are written but
  unfiled (operator-gated, `Roadmap_Blocked.md`).
- **Integrations and data flows:** PackageManager/PackageInstaller + hidden APIs; AppOps; IFW
  component rules; WorkManager routines; SAF; Room schema 10; BouncyCastle + OpenIntents OpenPGP
  IPC (`libopenpgp` is an AIDL client, **not** a BC PGP implementation — the OpenPGP-specific
  BC CVEs do not apply); optional `full`-flavor VirusTotal and debloat-definition updates behind
  `FeatureController` plus the master Internet toggle.

## Competitive Landscape

- **Upstream App Manager** (8.7k★, v4.1.0 2026-06-29) — frozen since 2026-06-29; zero PRs merged
  since 2026-03-01; 212 open issues. **Learn:** its unfixed bugs are NG's marketing — the v4.1.0
  app-list perf regression (#2000, users downgrading to v4.0.5), AES-GCM cipher reuse on large
  encrypted backups (#1958, NG fixed in metadata-v6), and the Android 17 empty-list class
  (#2019). NG's manifest already declares `ACCESS_LOCAL_NETWORK` that upstream master still
  lacks (#2017). **Avoid:** treating upstream as a fix source. There is nothing left to port.
- **Thor** (509★, v1.94.0 2026-08-05) — the fastest-moving newcomer, and the best-argued release
  notes in the field. Shipped a readback sweep after finding `pm` freeze silently *uninstalling*
  preinstalled apps. **Learn:** it publishes capability *removals* plainly when a path proves
  unsafe. **Avoid:** its `pm uninstall -k` freeze fallback entirely — NG's freeze already uses
  disable/hide/suspend (`filters/options/FreezeOption.java:18-20`), which is the correct choice.
- **LibChecker** (7.1k★, 2.5.4 2026-06-17) — the healthiest competitor and decisively better at
  binary forensics: exact ZIP-alignment values for libs that are 16 KB page-aligned but not
  ZIP-aligned, stripped-symbol-table detection, Modern Xposed API detection. **Learn:** those
  three signals are host-computable from an APK and NG's native-libs panel lacks them.
  **Avoid:** its WebUI export (scope creep).
- **SD Maid SE** (7.2k★, v2.0.2-rc0 2026-08-07) — independently ran the same truthfulness sweep:
  "stop claiming Shizuku setup is done when it isn't installed", "fix cache cleaning reporting
  success when it cleared nothing", "stop endless loading when Shizuku or root fails to connect".
  **Learn:** the pattern of naming the *absence* in the UI. **Avoid:** cleaner scope beyond the
  leftover scans NG has.
- **Canta** (5.5k★, feature-frozen since 2026-03) and **Neo Backup** (3.8k★, unpushed since
  2026-05-03, 239 open issues) — both have public, high-reaction requests for things NG already
  ships (Canta #148 disable apps, 15 reactions; #64 freeze, 6; Neo Backup #195 schedule
  conditions, 15; #241 changed-data filters, 6). **Learn:** a Neo Backup importer plus a listing
  in `awesome-android-root` (4.3k★, pushed 2026-08-10, currently lists upstream AM and not NG) is
  the cheapest reach available. **Avoid:** rebuilding what they abandoned.
- **UAD-NG** (8.8k★, v1.2.0 2026-01-12, 259 open issues) — its most-wanted feature is not more
  packages but *risk classification*: #583 "add a Safe removal list" (10 reactions, 2 years
  open), #770 "treat system UIDs as unsafe", #1164 per-package × OEM × ROM outcome matrix. Its
  bricking reports are concrete (#1311 bootloop from an item inside `[Recommended]`).
  **Learn:** NG's REMOVAL_SAFE ratings + OEM provenance are already the raw material for the
  answer nobody shipped. **Avoid:** the crowdsourced outcome matrix itself (see Rejected).
- **InstallerX-Revived** (6.1k★, 26.05 2026-05-30) — per-profile signature-mismatch policy,
  install-initiator display, update-ownership handling in session installs. Its top open issue
  (#773) publicly retires the "offline flavor" claim as security theater: omitting `INTERNET`
  does not stop a privileged process from reaching the network. **Learn:** NG's `floss` claim
  needs the same honesty scoping — the `NetworkTransparencyLedger` is the right answer, but the
  README wording should not promise more than compile-time removal delivers. **Avoid:**
  dialog-less installs; the prompt is NG's product.
- **Shizuku ecosystem** — upstream Shizuku's last commit is **2025-06-18**, last release
  2025-05-25, broken on Android 17 (#2180), and has fragmented into three 2026 forks with
  incompatible package names (Shevery 719★, ShizukuPlus 677★, Nightzuku 193★). **NG is already
  correct here**: `shizuku/ShizukuBridge.java:199-208` resolves the manager package by permission
  ownership rather than a hardcoded identity, and `MIN_ANDROID_17_COMPATIBLE_VERSION = null`
  records that no compatible release exists. Do not "fix" this. **Avoid:** binding to any fork.

## Security, Privacy, and Reliability

- [Verified] **Release pipeline cannot publish** — finding 1. Consequence beyond version drift:
  `consistency` is stage 2 of `release_gate.py`'s 8 (`release_gate.py:685`), and the dependency
  CVE scan runs inside `verify_reproducible_release` — stage 7 (`verify_reproducible_release.sh:117`).
  So while consistency fails, tests, lint, reproducibility and the CVE scan are all unreachable
  *on the gate path*; each can still be run standalone, as
  `docs/distribution/dependency-verification.md` documents. [Likely] the BouncyCastle batch of
  2026-08-03 has not been through the gate, since the pin and its comment are unchanged.
- [Verified] **BouncyCastle 1.84 → 1.85.2** — finding 2. Reachable surfaces confirmed by import
  scan: `crypto/ks/KeyStoreUtils.java` (X.509 build, BKS keystore), `settings/crypto/
  ImportExportKeyStoreDialogFragment.java` + `KeyPairImporterDialogFragment.java` (user-supplied
  keystore/PKCS#12/PKCS#8 import), `snapshot/SnapshotCrypto.java` (`Argon2BytesGenerator`),
  `crypto/AESCrypto.java` (GCM — the CCM-family CVE does not apply), plus arbitrary APK signer
  certificate parsing. `versions.gradle` line for `bouncycastle_version` is **stale**: it cites
  CVE-2026-5588 and CVE-2026-5598 as affecting 1.84 when GHSA records both as *fixed in* 1.84,
  and cites CVE-2026-3505, which this pass could not confirm.
- [Verified] **Version-string parse can brick launch.**
  `self/life/BuildExpiryChecker.java:117-133` throws `IllegalStateException("Invalid App Manager
  version")` for any `versionName` suffix that is not `alphaNN`/`betaNN`/`rcNN`, and
  `substring(0, len-2)` on a one-character suffix throws first. `buildExpired()` is called from
  `main/SplashActivity.java:117`, `BaseActivity.java:86` and `crypto/ks/KeyStoreActivity.java:33`
  with no guard. Any `-rc1`/`-pre` release build crashes at launch. No test covers the parser.
- [Verified] **179 empty catch blocks** in main sources. The ones that matter sit on destructive
  or trust paths: `apk/installer/PackageInstallerCompat.java:1516` (`Throwable`),
  `batchops/BatchOpsService.java:240,250`, `backup/RestoreOp.java:176,422`,
  `backup/BackupOp.java:621`, `crypto/ks/KeyStoreManager.java:280`,
  `rules/compontents/ComponentsBlocker.java:613`, `settings/Ops.java:790,1057`,
  `self/SelfPermissions.java:52,61` (a swallowed failure here makes every permission self-check
  answer "no"). The 2026-06 narrowing pass did not reach these.
- [Verified] **Accessibility service main-thread stalls** — finding 7. An AccessibilityService
  that blocks its own main thread for seconds stops delivering events and can be dropped by the
  system; this is the no-root force-stop / clear-data path.
- [Verified] **Release builds sign silently or not at all.** `app/build.gradle:55-65`: if
  `app/keystore.properties` is absent, `signingConfigs.release` is left empty and
  `assembleRelease` emits an **unsigned** APK with no error. The release gate's artifact stage
  catches this at publish time, so the exposure is limited to ad-hoc builds — noted, not
  proposed, because failing hard would break the F-Droid and rebuilder paths that *want*
  unsigned output.
- [Verified] **`floss` is not a network guarantee.** `ALLOW_OPTIONAL_NETWORK_FEATURES=false`
  removes the optional online surfaces at compile time, but the privileged modes still execute
  shell and binder calls that can reach the network. InstallerX #773 makes this critique
  publicly. NG's `NetworkTransparencyLedger` is the right mechanism; the claim wording is the
  exposure.
- [Verified] **Advanced Protection is closing the ADB escape hatch.** AAPM (API 36) already
  blocks sideloading; Play Services strings indicate it will disable Developer Options outright,
  which removes both USB and wireless debugging — i.e. NG's entire ADB privilege mode. The
  existing P3 roadmap row (detect AAPM and explain blocked installs) is the right hedge and
  should be treated as higher-value than its tier suggests. **[Needs live validation]**
- [Verified] **Android 17 all-apps changes that bite a reflection-heavy tool**: `static final`
  fields are no longer writable by reflection (`IllegalAccessException`; JNI
  `SetStaticLongField` crashes), the lock-free `MessageQueue` breaks reflection on its private
  fields, and `System.load()` on a writable `.so` throws. The 2026-05-02 MessageQueue audit was
  clean; the static-final and native-DCL audits are still owed before any targetSdk 37 bump.
- **Recovery needs:** the release-gate fix must not weaken the artifact-identity check; the BC
  bump must keep legacy backup restore working (metadata v5 and older) and must follow
  `docs/distribution/dependency-verification.md` (never `--write-verification-metadata`); the
  enumeration sanity floor must warn, never block; locale aliasing must not regress the
  translation ratchet baseline.

## Architecture Assessment

- **Boundaries to improve:** the release-metadata verifier conflates "the last published
  release" with "the release being prepared" — it needs two facts, not one equality
  (`scripts/verify_release_metadata.py`). Enumeration needs an unprivileged reference count as a
  third opinion, not two privileged calls checking each other
  (`compat/PackageManagerCompat.java:132`). Export filename construction needs one shared
  sanitiser; the correct one already exists at `profiles/ProfilesActivity.java:346` with a test
  at `app/src/test/.../profiles/ProfilesActivityTest.java:26` and is used by exactly one caller.
- **Refactor candidates (in value order):** `libcore/ui/.../util/AdapterUtils.java:108`
  (`areContentsTheSame` returns `false` unconditionally, defeating DiffUtil across 82 call
  sites); `settings/Ops.java:1067` (three overlapping privileged-mode entry points — upstream's
  own TODO, touchy); `permission/PermUtils.java:336-379` (`FLAG_PERMISSION_POLICY_FIXED`
  commented out of both read and write paths — resolve or document before any permission-UI
  work); `profiles/struct/BaseProfile.java:49` (legacy non-UUID profile IDs — fold into any
  future profile schema change); `filters/BackupFilterableAppInfo.java:79-126` (five predicates
  hard-return `false`/`null`, so backup-scoped Finder queries on installed/frozen/backup-allowed/
  signer silently match nothing).
- **Test gaps:** `:libcore:io` (29 files, remote FD/path transport), `:libcore:ui` (59 files),
  `:libserver`, `:server`, `:hiddenapi` all have **zero** tests — and adding a new test source
  set breaks strict dependency locking (recorded 2026-08-02), so host-testable logic must land in
  an already-locked configuration. `SBConverterTest.java:158` is still commented out.
  `app/lint-baseline.xml` suppresses **3,935** issues including 1,057 `DuplicateStrings`, 208
  `UnusedQuantity` and 69 `ThreadConstraint` — the last of which is why the main-thread sleeps
  above are invisible to lint.
- **i18n residue:** thirteen user-visible English strings are still built in Java and therefore
  never translate — seven `"Error: " + …` toasts plus two permission messages in
  `details/info/AppInfoFragment.java` (:536, :610, :658, :1048, :1215, :2870, :2939, :3111,
  :3363), and default names in `fm/dialogs/NewFolderDialogFragment.java:51` and
  `NewSymbolicLinkDialogFragment.java:83`. Small enough to close completely, and the translation
  ratchet cannot see strings that never reach `strings.xml`. Otherwise very clean for 3,457 base
  strings.
- **Docs debt:** `docs/distribution/release-receipt.json` is pinned to v0.6.12 while the manifest
  says 0.6.13. `CHANGELOG.md` v0.6.13 has **three separate `### Added` blocks** (a Keep-a-
  Changelog structural defect that has recurred once before). Root-level `COMPLETED.md` (140 KB,
  gitignored) collides by name with the tracked, README-linked `docs/roadmap/COMPLETED.md`.
  `RESEARCH_REPORT.md` (2026-06-05), `AUTONOMOUS-LOOP-STATE.md` and `codexprompt.md` are stale
  by 2+ months and gitignored — historical only. The local `CLAUDE.md` still describes the stack
  as "Java + Kotlin" (there is no Kotlin), points at a deleted `PROJECT_CONTEXT.md`, and cites
  `.ai/research/...` paths in a directory that is now empty.
- **Gate hygiene:** six of `verify-release-consistency.sh`'s assertions read the **untracked**
  `CLAUDE.md` and are skipped on a clean checkout, so the gate's apparent coverage is
  environment-dependent. `:server`'s `am.jar`/`main.jar` class split is a **filename-prefix
  match**, so any future class named `ServerUtils*`/`RootServiceMain*`/`IRootServiceManager*`
  silently changes which jar it lands in.
- **Upgrade constraints:** minSdk 21 ceiling intact and correct — Material 1.14.0, Room 2.8.x,
  WorkManager 2.11.x, activity 1.12.x all require 23. Room schema changes must ship explicit
  migrations while the P0 destructive-fallback ladder stays blocked. Note the durable tables
  (`log_filter`, `backup`, `op_history`, `fm_favorite`, `freeze_type`) live in the same database
  as the rebuildable `app` cache, which is what makes that blocked P0 a data-loss item rather
  than a cache-loss one; the in-code justification comment at `db/AppsDb.java:116-119` describes
  only the cache.
- **Coverage decisions (2026-08-11):** accessibility *presentation* (touch targets, contrast,
  TalkBack), visual polish, the empty/error/loading state system, the multi-user/work-profile
  matrix, D-pad/TV, and storefront screenshots are consciously excluded — each already has a
  device-gated row in `Roadmap_Blocked.md`; the accessibility item proposed here is a
  main-thread *reliability* defect, not a presentation one. i18n intake stays external-gated
  (fork-owned Weblate); the two host-verifiable i18n defects (locale folder resolution,
  hardcoded strings) are proposed. Observability is addressed only through the scoped
  swallowed-failure pass — NG already has structured logging, an op-history table, a network
  transparency ledger and a support-bundle export, so no new telemetry surface is warranted (and
  telemetry proper stays rejected). Distribution *submission* (IzzyOnDroid, F-Droid, and a
  listing in `awesome-android-root`, which today names upstream and not NG) stays operator-gated
  alongside the existing blocked row; distribution *machinery* is finding 1. Plugin ecosystem,
  cloud/multi-device, desktop, and Compose remain rejected. Migration paths remain constrained by
  the blocked Room ladder; upgrade strategy is the toolchain row plus the intact minSdk ceiling.

## Rejected Ideas

- **Detect the Shizuku binder capability instead of the package name** (competitor analysis,
  2026-08-11) — already implemented: `ShizukuBridge.getManagerPackageName()` resolves the manager
  via `API_PERMISSION` / `LEGACY_SERVICE_PERMISSION` ownership and only falls back to the
  canonical name. Verify before believing a research row.
- **Readback verification of privileged batch operations** (Thor v1.94.0, SD Maid v2.0.2-rc0) —
  shipped. `PackageStateVerifier` covers eight operations and v0.6.13 made the outcome three-way
  (confirmed / unchecked / contradicted).
- **Drop the `pm uninstall -k` freeze fallback** (Thor v1.94.1 data-loss incident) — NG never had
  it; freeze is disable/hide/suspend only.
- **Per-package × OEM × ROM debloat outcome matrix** (UAD-NG #1164) — the data source is
  crowdsourced user reports, which was rejected 2026-08-02 and stays rejected. The
  host-computable half (safety tiers from the ingested list, OEM provenance) already shipped
  2026-06-04.
- **Port anything from upstream** — all four post-v4.1.0 commits are absorbed (`836c7248e`
  Android 17 enumeration), inapplicable (`ae30473d3` CMake — NG has no `run_server` target and
  already carries 16 KB link flags upstream lacks; `fc1e70074` — NG already migrated
  `server/build.gradle` to `androidComponents { onVariants }`), or upstream release tooling
  (`2e2fdaf42`).
- **Raise targetSdk to 37 now** — no compliance pressure (Play requires 36 from 2026-08-31, and
  NG is at 36), and **[Likely]** an installer above targetSdk 36 loses the user-bypass path for
  unverified-developer APKs once verification enforces. Revisit after 2027 with device evidence.
- **Fail the build when `app/keystore.properties` is missing** — would break the F-Droid and
  independent-rebuilder paths that intentionally build unsigned. The gate's artifact stage
  already catches an unsigned publish.
- **Cloud backup targets, SMS/call-log backup, crowdsourced votes, AI privacy scores, telemetry,
  Compose rewrite, desktop/iOS, LibChecker WebUI export, PDF viewer** — rejected 2026-08-02 and
  2026-08-10, unchanged.
- **Material 1.14+ / M3 Expressive restyle** — hard-blocked by minSdk 23; NG has its own V2 theme
  system regardless.

## Sources

### Direct OSS
https://github.com/MuntashirAkon/AppManager (commits since v4.1.0; issues #1948/#1958/#1967/#1995/#1997/#1998/#2000/#2017/#2019/#2020; milestones v4.1.1/v4.2.0)
https://github.com/trinadhthatakula/Thor (v1.94.0, v1.94.1-dev-12)
https://github.com/LibChecker/LibChecker (2.5.4)
https://github.com/d4rken-org/sdmaid-se (v2.0.2-rc0)
https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation (issues #583/#770/#1164/#1311/#1436)
https://github.com/samolego/Canta (issues #148/#64/#350)
https://github.com/NeoApplications/Neo-Backup (issues #195/#241/#209; `Constants.kt` backup layout)
https://github.com/wxxsfxyzm/InstallerX-Revived (26.05; issue #773)
https://github.com/aistra0528/Hail
https://github.com/lihenggui/blocker (issues #60/#77)
https://github.com/RikkaApps/Shizuku (issue #2180)
https://github.com/XayahSuSuSu/Android-DataBackup
https://github.com/timschneeb/awesome-shizuku
https://github.com/awesome-android-root/awesome-android-root

### New entrants (2026)
https://github.com/HmnDev-Tech/shevery
https://github.com/thejaustin/ShizukuPlus
https://github.com/kerneldroid/Nightzuku
https://github.com/pass-with-high-score/universal-installer
https://github.com/hddq/restoid

### Platform and standards
https://source.android.com/docs/whatsnew/android-17-release
https://developer.android.com/about/versions/17/behavior-changes-17
https://developer.android.com/about/versions/17/behavior-changes-all
https://developer.android.com/about/versions/17/features
https://developer.android.com/about/versions/16/behavior-changes-all
https://developer.android.com/reference/android/content/pm/PackageInstaller
https://developer.android.com/reference/android/content/pm/PackageManager
https://developer.android.com/about/versions/16/qpr2/release-notes
https://developer.android.com/privacy-and-security/advanced-protection-mode
https://developer.android.com/developer-verification/guides
https://developer.android.com/google/play/requirements/target-sdk
https://developer.android.com/guide/practices/page-sizes
https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html

### Dependencies, advisories, tooling
https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/ (1.84 2026-04-14, 1.85 2026-07-12, 1.85.2 2026-08-07)
https://services.nvd.nist.gov/rest/json/cves/2.0?keywordSearch=bouncy+castle (2026-08-03 batch, "before 1.85")
https://github.com/bcgit/bc-java/wiki/CVEs
https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml (AGP 9.3.1)
https://services.gradle.org/versions/current (Gradle 9.7.0)
https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/ (2.4.10 stable 2026-07-14)
https://developer.android.com/build/releases/gradle-plugin
https://github.com/advisories/GHSA-r937-wjx7-w2jp (CVE-2026-53914)
https://github.com/dependency-check/DependencyCheck/releases (13.0.0)
https://github.com/material-components/material-components-android/releases (1.14.0 = minSdk 23)

## Open Questions

- Which Kotlin version AGP 9.3.1 pulls onto the buildscript classpath, and whether that clears
  CVE-2026-53914 — determinable only from a refreshed `buildscript-gradle.lockfile`, and it
  decides whether the existing toolchain row needs any Kotlin action at all.
- Whether an installer at `targetSdk > 36` genuinely loses the unverified-APK user-bypass path.
  Testable on an API 36.1+ device with `pm set-developer-verification-result`; it decides the
  targetSdk 37 timing and nothing else on this list depends on it.
- Maintainer decision, unchanged and still not answerable by research: register AppManagerNG's
  package name and signing key in the Android Developer Console ($25 + government ID) before the
  2027 global rollout, versus remaining unverified. New evidence raising its weight: impersonators
  had already squatted upstream App Manager's package names before its maintainer registered.
