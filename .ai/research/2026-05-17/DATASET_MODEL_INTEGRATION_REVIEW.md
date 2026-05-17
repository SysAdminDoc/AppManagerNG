<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# DATASET_MODEL_INTEGRATION_REVIEW — 2026-05-17

Datasets, models, APIs, benchmarks, and integrations the project relies on or could
plausibly add. AppManagerNG is **not an ML / AI project**; the relevant entries here are
the external **datasets** (debloat lists, library/tracker fingerprints, Anti-Features
flags) and the external **APIs** (Android platform, Shizuku, F-Droid, OSV.dev). There
are no model artifacts.

---

## 1. Datasets bundled or referenced

### Active (bundled at build time)

| Dataset | License | How it ships | Source | Used for |
|---|---|---|---|---|
| **MuntashirAkon/android-libraries** | (varies per-entry) | Git submodule under `scripts/android-libraries`, required for build | https://github.com/MuntashirAkon/android-libraries | Tracker scanner — class-name → vendor mapping. Required for the Tracker Scan in App Details. |
| **MuntashirAkon/android-debloat-list** | GPL-3.0-or-later (with per-package metadata) | Git submodule under `scripts/android-debloat-list` | https://github.com/MuntashirAkon/android-debloat-list | Debloater — package-name → bloat metadata (removal safety, dependencies, description, OEM). NG references both the upstream repo and a **`SysAdminDoc/android-debloat-list` fork** ([S234]) for the auto-update path. The fork added 112 entries (S22 Ultra US scrape) + 562 entries (UAD-NG delta sync). |
| **UAD-NG `uad_lists.json`** | GPL-3.0 | Referenced only (not bundled) | https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation | Bidirectional `dependencies`/`neededBy` graph and 4-tier `removal` taxonomy. The Debloater Presets row (✅ shipped 2026-05-14) consumes this via the SysAdminDoc fork. |
| **UAD-NG `universal-android-preinstalled-lists`** | GPL-3.0 | Referenced only | https://github.com/Universal-Debloater-Alliance/universal-android-preinstalled-lists | Per-OEM ground-truth: "package X is known preinstalled on OEM Y, model Z, region R". **Powers Finder OEM filter chips and App Info Provenance row** when those rows land. Last upstream push was 2025-01-06 (predates fork timeline). |

### Referenced but not bundled (data sources)

| Dataset | License | Status | Source |
|---|---|---|---|
| **εxodus tracker DB** | MIT | Embedded via `android-libraries` submodule (composition of Exodus + custom NG signatures) | https://github.com/Exodus-Privacy/exodus-standalone |
| **IzzyOnDroid `apkscanner-data`** | GPL-2.0 (migrated from GitLab early 2026) | Referenced [S214] — **not** bundled | https://codeberg.org/Katastima/apkscanner-data — would add ~30-40% library coverage but bundling it pulls a GPL-2.0 dep into a GPL-3.0-or-later project. Compatibility OK (GPL-2.0 → GPL-3.0 unilaterally permissible) but the audit hasn't been performed; deferred. |
| **F-Droid Anti-Features rules** | Documentation (no dataset to bundle) | Referenced [S244] | https://f-droid.org/docs/Anti-Features/ — the bright-line constraint that rules out Firebase/Crashlytics/cloud-Sentry for NG. |
| **DuckDuckGo Tracker Radar** | **CC-BY-NC-SA** | **Rejected** — UC row | License's non-commercial clause incompatible with GPL-3.0 redistribution. Path forward: contribute new SDK signatures upstream to Exodus (MIT) instead. |
| **Achno Samsung-specific debloat list** | (per Achno) | One-time validation cross-check against `oem.json` Samsung block, [S215] | https://github.com/Achno/debloat-samsung-ADB-shizuku |

---

## 2. External Android-platform APIs relied on

These are not datasets per se but are the platform-side surfaces NG consumes. Listed because the depth of platform-API coverage is a meaningful part of what makes NG NG.

| Surface | API floor | Used for | ROADMAP source |
|---|---|---|---|
| `PackageManager.*` | API 21+ | App enumeration, install/uninstall, signing certs, install source attribution | core |
| `PackageInstaller.*` | API 21+ with API 26+ session APIs | Install / uninstall, dependency check | [S309] |
| `AppOpsManager` (reflective) | API 19+ via hidden API | App-Ops editing, AppOp mode cycling | [S37] |
| `Shizuku.*` (UserService binder) | shizuku-api 13.x; runtime ≥13.6.0 | Rootless privilege provider | [S22, S121] |
| `LocaleManagerService.setApplicationLocales()` | API 33+ | Per-app locale picker, OS-Settings sync | [S164, S269] |
| `BiometricPrompt` | API 23+ (compat down to 21) | App-lock, install/uninstall gate (open) | [S159] |
| `JobScheduler / WorkManager` | API 21+ | Future: Scheduled Auto-Backup (T6 row, open) | [S45, S125] |
| `DocumentsProvider` | API 19+ | File Manager exposure (T13 row, open) | [S254] |
| `LauncherApps`, `ShortcutManager` | API 25+ / 26+ | Static + pinned shortcuts (✅ iter-22) | [S252] |
| `DomainVerificationManager.getDomainVerificationUserState()` | API 31+; expanded 34+ | Per-domain deep-link state for `autoVerify=true` claims (UC row) | [S217] |
| `PackageInstaller.requestArchive() / requestUnarchive()` | API 35+ | Android 15 archived-state support (UC row) | [S218] |
| `HealthConnectClient.getGrantedPermissions()` | API 34+ | Future privacy surface (UC) | [S220] |
| `SdkSandboxManager.getSandboxedSdks()` | API 34+ | Privacy Sandbox SDK enumeration (UC) | [S216] |
| `AdvancedProtectionManager` | API 37 | Android 17 AAPM integration (UC) | [S53] |
| `Build.VERSION.SDK_INT_FULL`, `Build.getMinorSdkVersion()` | API 36+ | Future: refactor 120 `SDK_INT >=` sites to centralized helper (T2 Next) | [S124] |
| `LocaleManager`, `AppCompatDelegate.setApplicationLocales()` | API 26+ backport, 33+ native | In-app language selector (T21 row, open) | [S269] |

The platform-API surface is broad and intentionally so — NG is positioned as **the** generalist Android power tool, not a specialist app.

---

## 3. External web APIs

NG is **offline-first** with strict opt-in for any network egress. Web APIs touched:

| API | Endpoint | Frequency | Privacy posture |
|---|---|---|---|
| GitHub Releases (upstream rename watcher) | `api.github.com/repos/MuntashirAkon/AppManager` | Weekly via CI workflow | CI-only, no client-side network surface |
| GitHub Releases (NG's own release feed) | `github.com/SysAdminDoc/AppManagerNG/releases` | Obtainium-driven, user-initiated | Tracked by user's Obtainium |
| **NG-internal: zero opt-out network egress today.** | — | — | Crash reporting (v0.3.0) writes to app-private storage and shows a "Share crash report" dialog that deep-links to GitHub Issues; **no automated network egress without explicit user action** per ROADMAP T4. |

**Considered, not yet shipped**:

- **OSV.dev API** — UC row "CVE Cross-Reference for Installed Apps" gates on explicit user consent.
- **Auto-update of debloat definitions** — opt-in (default OFF) preference; multi-mirror priority + auto-failover via `SysAdminDoc/android-debloat-list` primary, Codeberg secondary, IPFS gateway as deep fallback [S233 → S235].
- **F-Droid 2.0 protobuf index v2** — only if NG ever ships an update tracker (currently rejected; Obtainium does this better).

---

## 4. Evaluation / benchmark surface

There is no ML evaluation surface; the equivalent is the **audit doctrine** which serves
as a per-behavior-change regression test:

- 14 audit docs verify NG's compliance / hygiene status against Android-version /
  library-version behavior changes.
- Each audit names the sweep methodology (`grep` patterns + file roots).
- Verdict vocabulary: **clean** (no source matches), **clean (audit)** (matches found
  but pattern is correct), **remediated** (matches found and fixed), **confirmed**
  (matches found, fix scheduled), **n/a** (audit premise stale).

For correctness regression, the `app/src/test/` Robolectric suite covers a small set of
high-risk paths:

- AES backup crypto round-trip / per-file IV stability (post metadata-v6)
- Operation history CSV export (formula-injection defuse)
- Install transcript URI redaction (today: expanded)
- Filter-option regex/predicate compilation (today: new)

ROADMAP T11 row "Unit Test Coverage Expansion" tracks the goal; F-NEW-11 in
`FEATURE_BACKLOG.md` proposes a JaCoCo visibility gate.

---

## 5. Why this file is thin

Per the prompt's instruction: **if the project has no data, AI, ML, search, analytics,
scraping, evaluation, or external integration angle, explain why this file is thin.**

AppManagerNG is a power-user Android package manager. It consumes datasets (debloat
list, tracker fingerprints) and platform APIs, but it does not:

- Train or fine-tune any model
- Run inference at runtime
- Scrape external sites (Exodus / debloat-list datasets ship as bundled / submoduled JSON)
- Phone home for analytics (FOSS Anti-Features compliance forbids it)
- Provide a search index of any external corpus

The "model integration" surface for NG is **the platform's own APIs** plus **debloat /
tracker datasets**. Both are covered above. The dataset cross-section is genuinely well
handled in the existing ROADMAP — five primary datasets bundled or referenced; three
proposed augmentations on hold for license or scope reasons.

There is no shadow of unstated AI / data work that would justify expanding this file.
