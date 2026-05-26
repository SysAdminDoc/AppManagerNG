<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG — Research and Feature Plan (2026-05-25)

> **Scope.** Companion plan, not a replacement. `ROADMAP.md` remains the live planning
> surface (1848 lines, 363 numbered external sources, T1–T21 + Eng-Debt Register + Premium
> Polish Track + iter-141 status header). `CHANGELOG.md` (1890 lines) is the authoritative
> per-release ledger. `PROJECT_CONTEXT.md` is the entry-point index. **This file collects
> the deltas that today's audit pass surfaced — uncommitted work, release-cut readiness,
> drift between docs, and a small set of net-new opportunities not already on the
> roadmap.**
>
> Authored 2026-05-25 against working tree at `bd11078` (`main` is `ahead 177 / behind 177`
> from `origin/main` — investigate before pushing). v0.4.2 (`versionCode 6`) is the last
> tagged release on 2026-05-13; ~177 unreleased commits sit on top through iter-141.

---

## Executive Summary

AppManagerNG is a Material-3-led continuation of `MuntashirAkon/AppManager`, deliberately
keeping every power-user surface (root / Shizuku / ADB privilege paths, component
blocking, tracker scanner, multi-format backup, app-ops editor, hidden-API reach) while
rebranding under `io.github.sysadmindoc.AppManagerNG` and ramping a Premium Polish
visual-token track behind `PREF_PREMIUM_PREVIEW_BOOL`. Its strongest current shape is the
**privilege-provider story**: root + Shizuku + Wireless ADB + Dhizuku detection + ADB
`tcpip 5555` reuse, all surfaced in onboarding, Settings → Privileges, and Mode Doctor
with active-probe diagnostics (`PrivilegeModeDoctor`, `RootCapabilityDiagnostics`,
`KernelSuDiagnostics`, `ShizukuBridge`). The highest-value direction for the next quarter
is **release/ship discipline plus user-facing discovery**: cut v0.5.0 to bank the iter-91
→ iter-141 work, fix the Permission Inspector / changelog drift in surface docs, then
ship the v0.5.x Discovery theme (in-app changelog viewer, global in-app search, settings
reorganization, contextual help) and Premium Polish Phase 2 surfaces (App Details, App
Usage, Settings v2 layouts).

### Top opportunities, prioritised

1. **Cut v0.5.0 release** — 177 commits of shipped work sit in `Unreleased`; tag-and-publish
   converts them into a user-facing artifact. Without it, fastlane changelogs lag the code
   by six weeks and IzzyOnDroid / F-Droid submission cannot include the new work.
2. **Commit the in-progress Component Rules editor** — 3 modified files plus 3 new files
   in the working tree (Settings → Rules → Component rules preview). Either complete and
   ship or revert; do not let it bit-rot.
3. **Fix README + CLAUDE.md + bundled-changelog drift** — README v0.5.0 theme still names
   onboarding (shipped at v0.4.0); `CLAUDE.md` Status section stops at factory-iter-7
   (2026-05-02); `app/src/main/res/raw/changelog.xml` ships upstream v4.0.5 content
   verbatim despite the launch-time popup being stripped.
4. **In-app changelog viewer (v0.5.x deliverable)** — `ChangelogParser` reads
   `raw/changelog.xml` which is upstream-only. Until NG ships its own changelog data file
   *and* a `Settings → About → What's new` entry point, the in-app surface lies to users
   about every change since v0.1.0.
5. **Premium Polish Phase 2** — `design/plan/3-rollout.md` calls for AppDetails / AppUsage
   / Settings v2 layouts behind the existing `PREF_PREMIUM_PREVIEW_BOOL` toggle. Phase 1
   (Main, item rows) already shipped 2026-05-02; Phase 2 is the next chunk before the
   v0.6.x default flip and v0.7.x toggle removal.
6. **Routine Operations / Scheduler (T8)** — Upstream issue #61 is the #2 most-requested
   feature (21 reactions, 2020-era spec). Auto-Backup core landed in iter-92 → iter-99
   with WorkManager 2.10.5; the underlying scheduler infrastructure can extend to
   event-triggered profile execution (boot, charging, network, app foreground) for v0.6.x.
7. **Tracker Blocking via AppOps (T9)** — Open. TrackerControl-style three-tier blocking
   (minimal / standard / strict) layered over the existing tracker scanner dataset. Lands
   ahead of any Dhizuku DPM work because it doesn't need DPM and complements the existing
   εxodus signature pipeline already in production.
8. **Multi-Tag per App + Saved Filter Presets (T8)** — Combined, these unlock real
   power-user workflows ("freeze all `:work` tagged apps", "back up all `:critical`")
   without inventing a new abstraction. Schema decision required (Room `apps` table + new
   join table). Larger Bet.
9. **JADX 1.4.7 → 1.5.5 dep bump (Eng-Debt)** — Seven releases behind. Required before any
   T12 APK-editing surface, opens `.apks` ingestion, multi-thread UI fix, plugin API.
10. **JaCoCo coverage badge + lychee link checker** — Cheap observability deltas the
    pass-40 research already flagged. Test count is now 125 JVM tests + 2 instrumented;
    coverage trend is invisible.

---

## Evidence Reviewed

### Local files and directories inspected (this pass)

- Top-level: `README.md`, `CLAUDE.md`, `AGENTS.md`, `PROJECT_CONTEXT.md`, `CONTRIBUTING.md`,
  `ROADMAP.md` (selected sections), `CHANGELOG.md` (head + skim), `BUILDING.rst`,
  `versions.gradle` (via PROJECT_CONTEXT.md ledger), `build.gradle`, `settings.gradle`.
- Code surface: `app/src/main/java/io/github/muntashirakon/AppManager/` package tree
  (49 packages), `app/src/main/AndroidManifest.xml` (1709 lines), `app/src/main/res/raw/`,
  `app/src/main/assets/`, `app/src/main/java/.../onboarding/OnboardingFragment.java`,
  `app/src/main/java/.../main/MainActivity.java`, `.../intercept/ActivityInterceptor.java`,
  `.../fm/FmActivity.java`, `.../backup/BackupManager.java`,
  `.../settings/MainPreferences.java`, `.../settings/PrivilegeHealthPreferences.java`,
  `.../rules/compontents/ComponentUtils.java`, the in-progress
  `.../settings/ComponentRulesPreferences.java` + sibling new files.
- Tests: 125 files under `app/src/test/java/`, 2 under `app/src/androidTest/java/`.
- Design system: `design/spec/1-design-system.md`, `design/impl/{values,layout}/*-v2.xml`,
  `design/plan/3-rollout.md`, `design/audit/{0-recon.md,4-painpoints.md}`,
  `codexprompt.md`.
- Distribution: `docs/distribution/{obtainium-config.json, build-flavors.md,
  reproducible-builds.md, backup-destinations.md, package-visibility.md,
  rom-fdroid-preseed.md}`, `docs/fingerprints.txt`, `docs/sideload-verification.md`,
  `docs/intent-api.md`.
- Architecture: `docs/architecture/{README.md, 01-privilege-providers.md,
  02-backup-format.md, 03-hidden-api-bypass.md}`.
- Policy: `docs/policy/minsdk-21-ceiling.md`.
- Research: `.ai/research/2026-05-17/{STATE_OF_REPO, COMPETITOR_MATRIX, FEATURE_BACKLOG,
  PRIORITIZATION_MATRIX, MEMORY_CONSOLIDATION, SECURITY_AND_DEPENDENCY_REVIEW,
  SOURCE_REGISTER, CHANGESET_SUMMARY, CONTINUE_FROM_HERE, RESEARCH_LOG,
  DATASET_MODEL_INTEGRATION_REVIEW}.md`, plus passes 2-40 (40 dirs), plus iter-90 → iter-141
  (51 dirs).
- Audits: `docs/audits/README.md` + 23 dated audit files (Android 16/17/18 batches,
  crypto/dependency bumps, Shizuku Android-17 compat, GCM IV reuse, Predictive Back).
- Workflows: `.github/workflows/{codeql.yml, dependency-scan.yml, docs-link-check.yml,
  lint.yml, release.yml, shizuku-release-watch.yml, tests.yml, upstream-rename-watch.yml}`.

### Git history reviewed

- Last 50 commits: 2026-05-18 (iter-141 `bd11078`) back through iter-87. Themes: backup
  hardening, installer privilege cascade, scheduled-backup feature track, Android 17 /
  ML-DSA / 16 KB page-size hardening, AGP 9.2 / Gradle 9.4.1 migration.
- Tags: `v0.2.0` (2026-04-30) → `v0.4.2` (2026-05-13). **No `v0.5.0` tag yet.**
- `main` is `ahead 177 / behind 177` from `origin/main` — looks like a parallel history
  (force-push or local-only rebase). **Verify before pushing.**

### External sources

- `ROADMAP.md` Source Appendix S01–S364 covers the entire mined external surface (upstream
  issues, competitor changelogs, Android platform docs, FOSS reference apps). I did **not**
  open the external sources again this pass — re-mining would duplicate iter-18 → iter-141
  work. Where I cite competitor or upstream behaviour below, the `[S###]` reference is to
  the same appendix.

### Areas not verified this pass

- Hardware regression matrix (Samsung S25 Ultra SM-S938B, Pixel 9a, Poco F3, LineageOS
  23.2, Moto g22, Galaxy A57, Redmi Note 13 Pro 5G + KernelSU). Hardware diagnostic
  evidence is recorded in CHANGELOG and audit docs; not re-tested.
- TalkBack / screen-reader pass on the new v0.5.x surfaces (chip rows, Privileges screen,
  Mode Doctor, scheduled-backup status row). ROADMAP T10 `TalkBack Navigation Audit` row
  is still open.
- A full RTL Arabic/Hebrew layout pass (ROADMAP T10 `RTL Layout Verification` open).
- A 200% font-scale stress test on the new Privileges, Mode Doctor, scheduled-backup, and
  per-app rollback surfaces.

---

## Current Product Map

### Core workflows

- **Inspect** — Main list → App Details (Info / Components / Permissions / AppOps /
  Signatures / Libraries / Activities / Services / Receivers / Providers / Usage / More).
- **Operate** — Freeze/unfreeze, force-stop, clear data/cache, disable component, app-op
  edit, network policy, battery-optimization, per-app language, `pm hide` toggle, audio
  volume preset.
- **Investigate** — Tracker scanner (εxodus dataset), library scanner, signing-cert
  chain, manifest viewer, SELinux contexts, native-lib sizes + 16 KB alignment, install
  source attribution.
- **Bulk-operate** — Profiles, 1-Click Ops, Permission Inspector, Debloater
  (Privacy/Gaming/Minimal OEM presets + OEM-uninstall-blocker bypass), batch operations.
- **Move** — Backup/restore (APK + data + extras), tag system, AES-GCM with HKDF-derived
  per-archive keys (metadata v7), Scheduled Auto-Backup (WorkManager 2.10.5), provider-
  backed SMB/WebDAV/SFTP/cloud destinations, retention policy, integrity verification.
- **Edit** — Activity launcher, activity interceptor, code editor (sora-editor),
  file manager (with archive create/extract + recursive search + checksums), shared-prefs
  editor, SSAID view/edit.
- **Diagnose** — Mode Doctor, Privileges screen (root / Shizuku / ADB / battery / capability
  drop / KernelSU profile / Magisk drop-cap / Restricted Settings unlock / Doze diff),
  Support Info Bundle, Operation history (JSON/CSV/text export), OS-Revert detection.

### Existing features (broad-strokes inventory)

The full feature inventory lives across `ROADMAP.md` (shipped-marker `~~strikethrough~~`)
and `CHANGELOG.md`. Below is the **delta-since-v0.4.2** snapshot — features in the
`Unreleased` section that will become v0.5.0 once cut.

| Category | Feature (shipped post-v0.4.2) | Where |
|---|---|---|
| Onboarding | Persistent ADB `tcpip 5555` detection + reuse | `AdbTcpipProbe`, `OnboardingFragment` |
| Backup | AES metadata v7 HKDF-derived per-archive keys | `AESCrypto`, `BackupMetadataV5/V7` |
| Backup | CIFS/SMB streaming hardening (256 KiB chunks, fsync, byte-count verify) | `SplitInputStream/OutputStream`, `TarUtils` |
| Backup | Root-only Android System data backups | `SystemDataBackupHelper` |
| Backup | Profile blocklist picker enumerates backup roots | `ProfilesActivity`, profiles picker |
| Backup | Scheduled-backup newest-age gate | `AutoBackupScheduler`, `AutoBackupWorker` |
| Backup | Scheduled-backup progress notifications (API 36+ ProgressStyle) | `AutoBackupWorker` |
| Backup | Launcher shortcut for scheduled backup | `AutoBackupShortcutActivity`, `shortcuts.xml` |
| Backup | Default-app role rebind after restore | `DefaultAppRoleBackupHelper` |
| Backup | Backup path-exclusion globs | `BackupPathExclusionPatterns` |
| Backup | Provider-backed Network destination | Settings → Backup/Restore → Network |
| Installer | Privilege cascade (ADB → Shizuku → root) | `PackageInstallerCompat`, `InstallerActivity` |
| Installer | Split-APK cert-mismatch dialog | `SplitApkSignatureMismatchHelper` |
| Installer | Batch APK install from File Manager | `FmBatchApkInstallUtils` |
| Installer | Installer SHA-256 checksum dialog | `InstallChecksumDisplay` |
| Security | Sensitive-action auth gate (install/uninstall/clear) | `ActionAuthGate` |
| Security | Android 17 ML-DSA key algorithm display | `Utils`, `PackageUtils` |
| Security | Android 17 `usesCleartextTraffic` warning | `AppInfoFragment` |
| Privileges | KernelSU diagnostics (seccomp, sulog, App Profile) | `KernelSuDiagnostics` |
| Privileges | Magisk `--drop-cap` policy surface | `RootCapabilityDiagnostics` |
| Privileges | Dhizuku Provider detection (no DPM yet) | `DhizukuBridge` |
| Privileges | Restricted Settings unlock walkthrough | `RestrictedSettingsDiagnostics` |
| Privileges | OS-revert detection banner + Doze diff | `OsRevertMonitor`, `DozeAllowlistDiagnostics` |
| Filters | Install-date filter + filter-applied chip | `InstallDateOption`, main list |
| Filters | Permission flags filter | `PermissionsOption`, `FilterablePermissionInfo` |
| Filters | Finder relevance scoring | `FinderRelevanceScorer` |
| Filters | Backup-only Finder results | `BackupFilterableAppInfo` |
| Filters | Tracker name search | `TrackersOption` |
| App Info | Per-app language picker | `AppLocaleOptions` |
| App Info | `pm hide` toggle | `AppInfoFragment` |
| App Info | SELinux context display | `AppSelinuxContexts` |
| AppOps | Audio-volume preset (UID-mode) | `AppOpsManagerCompat` |
| Automation | Tasker parameterized intents (`am://`) | `AutomationUriActivity`, `AutomationRequest` |
| Automation | Signature-gated broadcast API | `AutomationReceiver` |
| Profiles | Quick Settings freeze tile | `QuickFreezeTileService` |
| Profiles | Profile import (Canta/UAD-NG/Hail) | `ExternalProfileImporter` |
| Profiles | Snapshot Bundle Settings portability v2 (TSV rules) | `SnapshotBundle` |
| Permission | Permission Change Monitor | `PermissionChangeMonitor` |
| Permission | Signing-cert change alert | `SigningCertChangeMonitor` |
| FM | Recursive in-folder search | `FmSearchUtils`, `FmFragment` |
| FM | ZIP create / extract + zip-slip guard | `FmArchiveUtils` |
| FM | Smali decode level (`none`/`basic`/`verbose`) | `SmaliDecodeOptions`, `DexFileSystem` |
| Distribution | F-Droid 2.0 ROM preseed templates | `docs/distribution/rom-fdroid-preseed.md` |
| Build | AGP 9.2.0 / Gradle 9.4.1 migration | `versions.gradle`, `build.gradle` |
| Widgets | Material You / Monet AppWidget theming | `widget/AppWidgetPaletteHelper` |
| Diagnostics | Support Info Bundle composer | `SupportInfoBundle` |
| Diagnostics | LocalServer bootstrap smoke test | `LocalServer` |
| History | Per-app rollback planner | `PerAppRollbackManager`, App Details |
| Debloat | OEM uninstall-blocker bypass (Samsung/MIUI/OPlus) | `OemBloatRiskTable` |
| Debloat | Auto-update debloat definitions | `DebloatDefinitionsUpdater` |
| Main list | App-list export/import (CSV/JSON/XML/Markdown) | `ListImporter`, `ListExporter` |
| Assistant | `ACTION_ASSIST` quick actions | `AssistActionActivity` |

This is the change-set that converts to v0.5.0. ROADMAP §"Iter-91 → Iter-141" lists each
slice; CHANGELOG `Unreleased` contains the user-facing summary.

### User personas

1. **The power admin** — root + Magisk/KernelSU/APatch user, Permission Inspector,
   component blocking, profiles, scheduled backup. Already strong fit.
2. **The rootless cleanup user** — Shizuku + ADB + Wireless ADB pairing; Debloater
   presets + OEM bypass. The single most-requested upstream demographic (S02, 31
   reactions). Onboarding wizard, Mode Doctor, and Privileges screen carry them.
3. **The privacy auditor** — Tracker scanner, Permission Change Monitor, Signing-Cert
   Change Alert, SELinux context display, install-source attribution. Strong on
   detection, weak on **action** (Tracker Blocking via AppOps still open).
4. **The Tasker / automation user** — `AutomationReceiver` (signature-gated broadcast) +
   `AutomationUriActivity` (`am://` URIs) + parameterized intent overrides. Now reaches
   parity with Hail; missing piece is **Routine Operations / Scheduler** (T8) for the
   time/event-triggered profile-run flow.
5. **The forensic analyst / RE user** — APK analysis, native-lib inspection, dex/smali
   decode, signing-scheme chain, manifest viewer. Held back by JADX 1.4.7 (Eng-Debt).

### Platforms and distribution channels

- **Android 5.0 (API 21) → Android 16 (compile/target 36)** — minSdk-21 floor documented
  at `docs/policy/minsdk-21-ceiling.md`. Material 1.13 ceiling driven by this.
- **Distribution channels live or planned**: GitHub Releases (live, signed APKs with
  reproducible-builds gate), Obtainium (live, config + README link), IzzyOnDroid (open,
  T1), F-Droid (open, T1), Accrescent (open, T1), F-Droid 2.0 ROM preseed (live, templates
  shipped).
- **Build flavors**: `floss` (default — F-Droid clean, optional-network features off) and
  `full` (Internet feature gate on, VirusTotal, Pithus, debloat-definition auto-update
  reachable). Both signed identically; release pipeline collects both.

### Important integrations, permissions, storage, or data flows

- **Privilege providers**: libsu 6.0.0 (root), Shizuku 13.1.5 compile-time floor / 13.6.0+
  runtime guidance, libadb-android 3.1.1 (ADB Wireless + ADB-over-TCP), Dhizuku detected
  (DPM not wired yet).
- **Backup storage**: app-private + SAF + provider-backed tree URIs. Crypto modes: AES,
  RSA, ECC, OpenPGP. Master key in `am_keystore.bks` (file-backed BKS, outside Android
  Keystore 50K cap); per-archive HKDF in metadata v7.
- **External APIs (full flavor only)**: VirusTotal, Pithus, debloat-definition update
  manifest fetch (from pinned SysAdminDoc/AppManagerNG raw GitHub). All gated by Settings
  → Privacy "Use the Internet" feature switch.
- **Manifest-declared permissions** include `FORCE_STOP_PACKAGES`, `DELETE_PACKAGES`,
  `MANAGE_USERS`, `BIND_QUICK_SETTINGS_TILE`, `BIND_VPN_SERVICE` is **not** declared
  (intentional), `REQUEST_INSTALL_PACKAGES`, biometric, accessibility (foreground
  component tracking), Dhizuku `com.rosan.dhizuku.permission.API`.
- **CI**: `tests.yml` (JVM tests), `lint.yml`, `codeql.yml`, `dependency-scan.yml`,
  `release.yml` (two-build reproducible gate), `shizuku-release-watch.yml` (weekly
  upstream watcher), `upstream-rename-watch.yml`, `docs-link-check.yml`.

---

## Feature Inventory (notable gaps and partials only)

Because the live feature set is enormous, this section limits itself to features that are
**partial / hidden / undocumented / drifted / blocked** — items where today's audit
surfaces something worth deciding before v0.5.0 ships.

### Component Rules editor (Settings → Rules → "Component rules") — **partial, uncommitted**

- **Entry point (planned)**: Settings → Rules → "Component rules" (new
  `<Preference android:fragment=".settings.ComponentRulesPreferences"/>` row).
- **Code locations**: `app/src/main/java/.../settings/ComponentRulesPreferences.java`
  (new, ~250 lines from preview); `.../rules/compontents/ComponentRulesPreview.java`
  (new); `.../rules/compontents/ComponentUtils.java` (`getAllPackagesWithComponentRuleFiles`
  + `getAllPackagesWithIfwRuleFiles` helpers added);
  `app/src/test/java/.../rules/compontents/ComponentRulesPreviewTest.java` (new).
- **Maturity**: **partial**, uncommitted in working tree. 3 modified + 3 new files,
  ~52-line net diff. Wires through but is not on a CHANGELOG row.
- **Improvement opportunity**: Commit if intended for v0.5.0, otherwise revert and reopen
  as a roadmap row. The shape is close to a "Blocker-style IFW Rule Editor UI" entry that
  the iter-141 CONTINUE_FROM_HERE.md called out as the next visible Next row — confirm
  before merging.

### In-app changelog viewer — **partial / stale data**

- **Entry point**: None reachable from the UI today; launch-time popup was stripped per
  CLAUDE.md §Status.
- **Code locations**: `app/src/main/java/.../changelog/ChangelogParser.java`,
  `ChangelogActivity.java`, `Changelog.java`; data file
  `app/src/main/res/raw/changelog.xml` (1824 lines, **upstream v4.0.5 content verbatim**).
- **Maturity**: parser/viewer exists; data file is stale; entry point missing.
- **Improvement opportunity**: Author NG-native `raw/changelog.xml` covering v0.1.0 →
  v0.5.0 (use CHANGELOG.md as source-of-truth → XML transformer); wire a Settings →
  About → "What's new" entry; show on first-launch-after-update with a quiet bottom-sheet
  rather than the upstream blocking dialog. v0.5.0 deliverable per ROADMAP §Committed
  Version Targets.

### Global in-app search — **not started**

- **Entry point (planned)**: Settings root (mirrors AOSP Settings search).
- **Code locations**: none yet. Indexable surface lives in `xml/preferences_*.xml`.
- **Maturity**: not started. Listed in v0.5.0 deliverables.
- **Improvement opportunity**: Build an in-memory preference index by walking the
  `PreferenceFragment` tree at app start, store `{label, summary, fragment, key}` triples
  in a Room table or in-memory list, expose a `SearchView` in `SettingsActivity` toolbar
  that filters and navigates. Pattern: AOSP `SettingsLib.search`. Effort M.

### Contextual help tooltips — **not started**

- **Entry point (planned)**: `?` icon next to dense controls (mode picker, AppOps cycle,
  backup encryption mode, profile state condition).
- **Code locations**: none. New shared `HelpTip` helper would live in `utils/`.
- **Improvement opportunity**: Material 3 `TooltipBox`/`TooltipDrawable`-equivalent for
  Views; ship with a hard-coded help registry for the ~20 most-friction controls. v0.5.0.

### Settings reorganization by task — **not started**

- **Entry point**: `Settings root`.
- **Code locations**: `app/src/main/res/xml/preferences*.xml` (15 files),
  `app/src/main/java/.../settings/*Preferences.java` (16 classes).
- **Maturity**: legacy organization — by sub-feature (Appearance, Backup/Restore, Rules,
  Privacy, Privileges, Installer, Apk Signing, VirusTotal, Mode of Ops, File Manager,
  Log Viewer, Troubleshooting). v0.5.0 deliverable.
- **Improvement opportunity**: Group by task — **Get set up** (Mode of Ops, Privileges,
  Onboarding replay), **Use power tools** (Rules, Installer, AppOps, APK Signing), **Take
  control of data** (Backup/Restore, File Manager, Snapshot Bundle), **Stay safe**
  (Privacy, VirusTotal, Sensitive-action gate), **Diagnose** (Privileges health, Mode
  Doctor, Support info, Log viewer, Troubleshooting), **Make it yours** (Appearance,
  Language). Preserve all existing `PreferenceScreen` keys; only reorganize navigation.
  Effort M.

### Pro Mode toggle discovery — **hidden**

- **Entry point**: Settings → Advanced (`PrefAdvanced` row).
- **Code locations**: `app/src/main/java/.../settings/AdvancedPreferences.java`,
  `Prefs.kt`-equivalent constants in Java.
- **Maturity**: complete but undersold. `design/audit/4-painpoints.md` flags it as
  "strategically important but visually undersold".
- **Improvement opportunity**: Hero card on Onboarding final step + an About-screen Pro
  Mode comparison card (locked / unlocked capability matrix). No new behaviour, just
  discovery. S.

### Operation Activity Log (Issue #143 [S13]) — **partial**

- **Entry point**: Settings → Privacy → History.
- **Code locations**: `OpHistoryActivity`, Room-backed append-only log, JSON/CSV/text
  export, rerun, rollback guidance.
- **Maturity**: viewer + filters + export + per-row rollback action all present (closed
  in iter-54). The remaining ROADMAP row (T8 "Operation Activity Log") asks for what is
  already in code — confirm in next ROADMAP audit pass.

### ActivityInterceptor: return results — **partial / open TODO**

- **Entry point**: Intent Interceptor screen, "Run" button.
- **Code locations**: `app/src/main/java/.../intercept/ActivityInterceptor.java`, 1357
  lines. **4 TODOs from 2022-02 referencing "Support sending activity result back to the
  original app".**
- **Maturity**: partial. Intercepted activities run but no `setResult()` is plumbed back.
- **Improvement opportunity**: Honour `Intent.FLAG_ACTIVITY_FORWARD_RESULT` when the
  originating activity launched the interceptor with `startActivityForResult`. Effort M;
  cite Issue #1717 if exists (verify).

### DocumentsProvider implementation (Issue #516 [S06]) — **TODO / blocked**

- **Entry point**: Other apps' SAF pickers (would land NG's file manager in Android's
  document picker).
- **Code locations**: `app/src/main/java/.../fm/FmProvider.java` carries TODO.
- **Maturity**: not started.
- **Improvement opportunity**: `DocumentsProvider` subclass exposing app-private and
  root-readable trees as a SAF root. High user value (other apps could read NG-managed
  trees) but Android SAF reach into root partitions has known sharp edges. L. Could fit
  T13 (post-v0.6.x).

### DexClasses smali roundtrip for API < 26 — **FIXME**

- **Entry point**: APK Editor (when shipped).
- **Code locations**: `app/src/main/java/.../dex/DexClasses.java`.
- **Maturity**: FIXME blocks T12 progress.
- **Improvement opportunity**: Upgrade baksmali 3.0.9 → 3.1.x (if released) and complete
  the roundtrip; gates T12 APK editing.

---

## Competitive and Ecosystem Research

Full mining of competitors is in `ROADMAP.md` (Iter-18 → Iter-23 deltas) plus
`.ai/research/2026-05-17/COMPETITOR_MATRIX.md`. Re-mining the same set today would
duplicate that work. **This pass surfaces only deltas worth a fresh action.**

### Direct competitors (key learnings still un-actioned)

| Source / project | Notable capability | What NG should learn (open) | What NG should avoid |
|---|---|---|---|
| **`MuntashirAkon/AppManager` (upstream)** | Routine Operations spec (Issue #61, 2020) | NG to **implement** if upstream stalls past 12 months — that window closes 2027-Q3; planning lead-time is now. | Reimplementing upstream's APK editing or terminal before they ship — pull when ready. |
| **`aistra0528/Hail`** | Multi-tag per app, URI automation, QS-tile auto-freeze | Multi-tag schema (T8) — Hail demonstrates that tag-based ops outperform hand-picked lists at scale. Already matched QS-tile + Digital Assistant launch (iter-119/QS-tile iter-128 era). | Hail's AccessibilityService-based auto-freeze (rejected in ROADMAP Premium Polish — privacy/policy posture mismatch). |
| **`Hamza417/Inure`** | Material-3 expressive surface, App Logs/Analytics dashboard | **App-runtime telemetry panel** in App Details (open Bet) — surface foreground time, network bursts, wakelocks for the selected app from Usage Stats / `dumpsys batterystats`. | Inure's paywall — NG must keep "Pro Mode" free / opt-in, not commercial. |
| **`samolego/Canta`** | Curated debloat presets + safety ratings + factory-reset-before-uninstall | Match: shipped. Continued opportunity is **a "what changes if I remove this" preview** that simulates downstream effects (calendar / SMS / dialer-default impact). Quick Win-grade. | Canta-style hard rules-as-code; keep NG's data-driven (`debloat.json`) approach. |
| **`d4rken-org/sdmaid-se`** | CorpseFinder for leftover dirs, per-app storage panel | Open in T19 — Leftover Detection After Uninstall + App Details Storage Panel are still on the roadmap. Strong Quick-Win fit. | SD Maid's full-system cleaner scope — NG must stay per-app, not whole-device. |
| **`NeoApplications/Neo-Backup`** | KeepAndroidOpen banner during long ops, schedule tags | KeepAndroidOpen banner still on roadmap (T8). One-day delivery. | Neo-Backup's plug-in-architecture overhead — NG can keep a static UI. |
| **`XayahSuSuSu/Android-DataBackup`** | Native SMB / WebDAV clients | NG delegates to SAF DocumentsProvider apps (Material Files, DAVx5, FolderSync). **Native client is parked**, do not chase. ROADMAP iter-99 / iter-100 closed correctly. | Re-implementing SMB/WebDAV when DAVx5/FolderSync already exist. |
| **`LibChecker/LibChecker`** | Per-app package-visibility analysis (QUERY_ALL_PACKAGES + `<queries>`) | **Open in T9** — surface which apps enumerate the full package list (privacy/attack-surface signal). M. | LibChecker's reverse-tracking display style — too dense for NG's audience. |
| **`soupslurpr/AppVerifier`** + **`ImranR98/Obtainium`** | Fingerprint-pinned install + update tracking | Done. NG publishes `docs/fingerprints.txt`. | Building update-tracker in-tree (rejected). |

### Net-new from external landscape (2026-05-25 sweep cross-check vs ROADMAP)

The `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` already filed 14 net-new items
(F-NEW-01 → F-NEW-14) from external + repo state. **Eight of those have shipped or were
closed** as of iter-141 (regex fix, install transcript redactor, onboarding race fix,
docs/architecture stand-up, etc.). The still-open net-new items are: **F-NEW-06** (Top-of-
ROADMAP summary), **F-NEW-10** (Issue-state refresh script), **F-NEW-11** (JaCoCo
coverage badge), **F-NEW-13** (Markdown link checker for `PROJECT_CONTEXT.md`).

This pass adds eight more items in §"Highest-Value New Features" below.

---

## Highest-Value New Features

Each feature is grounded in evidence already in-repo (uncommitted work, audit notes,
roadmap rows, source comments) or in the ROADMAP Source Appendix.

### NF-01 — Cut **v0.5.0 release** (Discovery theme)

- **User problem solved**: 177 commits of polish + 50+ feature slices sit in `Unreleased`
  six weeks after v0.4.2. Users on Obtainium / IzzyOnDroid see no new tag. F-Droid
  metadata cannot reflect the shipped work.
- **Evidence**: `git log v0.4.2..HEAD --oneline | wc -l` (≈ 177); `CHANGELOG.md
  Unreleased` is the longest section by far; `fastlane/metadata/.../changelogs/` jumps
  from `6.txt` (v0.4.2) to nothing for v0.5.0.
- **Proposed behavior**: Tag `v0.5.0`, `versionCode 7`, "Discovery & Polish". Includes
  every shipped iter-91 → iter-141 slice. Pushes both `floss` and `full` flavor APKs
  through the existing reproducible-build gate. Writes
  `fastlane/metadata/android/en-US/changelogs/7.txt`.
- **Implementation areas**: `versions.gradle`, `app/build.gradle` versionCode/versionName,
  CHANGELOG.md (`## v0.5.0 - 2026-05-25` section), `fastlane/metadata/.../changelogs/7.txt`,
  `app/src/main/res/raw/changelog.xml` (NG-native entry — see NF-02),
  README badge, ROADMAP "Last updated" line.
- **Data model / API / UI implications**: None new.
- **Risks and edge cases**: Local `main` is `ahead 177 / behind 177` from origin/main —
  investigate before pushing the tag (looks like a parallel history; might require
  `git push --force-with-lease` and an audit). Reproducible-build gate sometimes flakes on
  Windows; verify on CI. Battle-test on at least one Android 17 device (Pixel 9a).
- **Verification plan**: Local `./gradlew :app:assembleFlossRelease :app:assembleFullRelease`
  → `scripts/verify_reproducible_release.{ps1,sh}` → install on S25 Ultra + Pixel 9a +
  one Shizuku-only device; smoke-test Mode Doctor, scheduled-backup, batch APK install,
  per-app rollback.
- **Estimated complexity**: **S** (release plumbing) but ships a large change-set.
- **Priority**: **P0**.

### NF-02 — In-app changelog viewer (NG-native data)

- **User problem solved**: The in-app `ChangelogActivity` parses
  `app/src/main/res/raw/changelog.xml` which ships upstream v4.0.5 content verbatim. Users
  who hit the viewer see content from a project they're not running.
- **Evidence**: `head -5 app/src/main/res/raw/changelog.xml` shows DTD pointing at
  `MuntashirAkon/AppManager/master/schema/changlelog.dtd` and `<release ... version=
  "v4.0.5">`; 1824 lines of upstream notes; CLAUDE.md notes the launch-time popup was
  stripped 2026-05-02.
- **Proposed behavior**: Replace `raw/changelog.xml` with NG-native content (v0.1.0 →
  v0.5.0). Add Settings → About → "What's new" entry. On first launch after a version
  bump, show a quiet bottom-sheet (not blocking dialog) summarising the release with a
  "Show full changelog" link. Use existing `ChangelogParser` shape; do not reinvent DTD.
- **Implementation areas**: `app/src/main/res/raw/changelog.xml` (full rewrite from
  CHANGELOG.md), `schema/changelog.dtd` (mirror upstream's DTD locally so the parser
  validates offline), `changelog/ChangelogParser.java` (no API change),
  `settings/AboutPreferences.java` (+ "What's new" row), `MainActivity.onCreate()`
  (show-once-after-version-bump bottom-sheet), strings.
- **Data model / API / UI implications**: New `BottomSheetDialogFragment`
  subclass (`ChangelogBottomSheet`). Persisted "last-seen versionCode" pref to know when
  to show.
- **Risks and edge cases**: Translation work — only English in v0.5.0; defer locale
  variants. DTD path must be served locally or stripped from `<!DOCTYPE>` so the parser
  does not attempt a network fetch.
- **Verification plan**: Open Settings → About → What's new on real device; bump
  versionCode in a side build and confirm bottom-sheet appears once and only once.
- **Estimated complexity**: **M**.
- **Priority**: **P0** (v0.5.0 deliverable per ROADMAP).

### NF-03 — Global in-app search for Settings

- **User problem solved**: 16 Preference fragments + 15 XML files. Users hunting for "AES
  master key" or "Wireless ADB pairing" must remember the category. AOSP Settings has had
  this since Android 7.
- **Evidence**: ROADMAP §Committed Version Targets → v0.5.0 → "global in-app search";
  `.ai/research/2026-05-17/FEATURE_BACKLOG.md` §3 implies missing.
- **Proposed behavior**: `SearchView` in `SettingsActivity` toolbar. On focus, hide list,
  show search-result list (preference label + parent category breadcrumb). Tap → navigate
  to the parent fragment + scroll-to-key.
- **Implementation areas**: New `settings/SettingsSearchIndex.java` (walks
  `xml/preferences_*.xml` via `PreferenceManager.inflateFromResource` once per locale
  change, harvests `{title, summary, key, fragment}`), `settings/SettingsActivity.java`
  (toolbar SearchView), `settings/SettingsSearchAdapter.java` (RecyclerView adapter).
- **Data model / API / UI implications**: In-memory index, ~150 entries. Locale-aware:
  rebuild on `onConfigurationChanged`.
- **Risks and edge cases**: Some preferences are added dynamically (e.g. flavor-gated
  rows). Index must also walk dynamically-added rows after `onResume`. The "scroll-to-
  key" anchor needs `PreferenceFragmentCompat.scrollToPreference()`.
- **Verification plan**: Robolectric test exercising the index walker with two fixture
  XML files. Manual: search "AES", "tcpip", "Doze", "Material Files", "Pro Mode" → ensure
  each lands on the right fragment.
- **Estimated complexity**: **M**.
- **Priority**: **P0** (v0.5.0 deliverable).

### NF-04 — Settings reorganization by task

- **User problem solved**: Sub-feature-organized settings tree forces users to know NG's
  internals to find the toggle they want. Task-organized tree mirrors how users describe
  their goal ("I want to set up backup", "I want to diagnose root").
- **Evidence**: ROADMAP v0.5.0 deliverable; `.ai/research/2026-05-17/FEATURE_BACKLOG.md`
  signals settings sprawl.
- **Proposed behavior**: Six task groups (Get set up / Use power tools / Take control of
  data / Stay safe / Diagnose / Make it yours) as **collapsible top-level categories** in
  `MainPreferences.java`. Inside each category, preserve all existing `PreferenceScreen`
  references — only the navigation changes.
- **Implementation areas**: `MainPreferences.java`, `xml/preferences.xml` (top-level),
  strings.
- **Data model / API / UI implications**: None — pure rearrangement. Existing key
  contracts intact, so deep-link via key (e.g. `am://settings?key=mode_of_ops`) keeps
  working.
- **Risks and edge cases**: Translation churn (6 new category strings, ~20 locale files);
  acceptable cost. Ensure dual-pane (`SettingsActivity` w900dp) still feels right.
- **Verification plan**: Pixel 9a portrait + landscape walkthrough; tablet w900dp
  walkthrough on the emulator.
- **Estimated complexity**: **M**.
- **Priority**: **P1** (v0.5.0 deliverable).

### NF-05 — Contextual help tooltips

- **User problem solved**: AppOps cycle (`ALLOWED → IGNORED → ERRORED`), backup
  encryption modes (AES vs RSA vs ECC vs OpenPGP), profile state condition triggers, Doze
  diagnostics, Mode-of-Ops chooser are all opaque to first-time users.
- **Evidence**: ROADMAP v0.5.0; `design/audit/4-painpoints.md` flags dialogs as "dense
  forms".
- **Proposed behavior**: Material `TooltipCompat` or custom `HelpTip` overlay attached to
  an `?` icon next to ~20 known-friction controls. One-tap opens a bottom-sheet with a
  short plain-language explainer (~3 paragraphs) + "Learn more" deep link to the relevant
  audit doc / ROADMAP source.
- **Implementation areas**: New `utils/HelpTipRegistry.java` (id → string-resource map),
  `utils/HelpTip.java` shared overlay; integration points (`AppOpsAdapter`,
  `BackupDialogFragment`, `ProfileEditor`, `ModeOfOpsPreference`, `Onboarding wizard`).
- **Data model / API / UI implications**: Pure additive UI.
- **Risks and edge cases**: Translation burden (20 plain-language strings). Visual
  crowding — apply sparingly.
- **Verification plan**: Pixel 9a smoke; TalkBack on each registered help target.
- **Estimated complexity**: **M**.
- **Priority**: **P1** (v0.5.0 deliverable).

### NF-06 — Pro Mode discovery (hero + comparison)

- **User problem solved**: Pro Mode is the v0.3.0+ disclosure mechanism for advanced
  features but is buried in Settings → Advanced. Onboarding survey didn't see it.
- **Evidence**: `design/audit/4-painpoints.md` row "Pro Mode toggle" — "strategically
  important but visually undersold".
- **Proposed behavior**: Hero card on Onboarding final step ("Power-user controls — turn
  on Pro Mode in Settings → Advanced to expose advanced filters, schema dumps, AppOps
  cycling defaults") and a one-screen About → Pro Mode comparison card listing the 8-10
  controls Pro Mode gates.
- **Implementation areas**: `onboarding/OnboardingFragment.java` (final-step card),
  `settings/AboutPreferences.java` (new "Pro Mode" row → `ProModeComparisonActivity`).
- **Data model / API / UI implications**: None.
- **Risks and edge cases**: Don't make the card pushy; preserve the "Skip" affordance.
- **Verification plan**: Manual smoke; TalkBack pass.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### NF-07 — Tracker Blocking via AppOps (T9 open)

- **User problem solved**: NG detects trackers (εxodus dataset + organization rollup) but
  cannot block them without component-blocking, which requires root. AppOps-based block
  (`AppOpsManager.OP_INTERNET`-equivalents per package/UID via `setUidMode`) works under
  Shizuku/ADB. TrackerControl v2026-04-03 demonstrates the pattern.
- **Evidence**: ROADMAP T9 "Tracker Blocking (AppOps)" row — open.
- **Proposed behavior**: Three intensities: **Detect-only** (current behaviour),
  **Standard** (disable known problematic SDKs by setting their AppOps to `IGNORED` for
  the parent UID — uses the existing per-UID `setUidMode` path), **Strict** (disable all
  detected tracker components). Per-app override. New "Block trackers" action in App
  Details → Trackers tab beside the existing tracker breakdown.
- **Implementation areas**: New `tracker/TrackerBlockingPolicy.java`, `tracker/
  TrackerBlockingApplier.java`; integration with `AppDetailsFragment` Trackers tab,
  `OneClickOpsActivity` (batch apply Strict to selected user apps), `BatchOpsManager`
  (new op).
- **Data model / API / UI implications**: New Room column `tracker_blocking_intensity`
  on `apps` table. Possibly new `app_tracker_overrides` join table for per-component
  exceptions.
- **Risks and edge cases**: Some apps refuse to run with trackers blocked (anti-cheat,
  some banking SDKs); user-facing "what broke?" guidance required. Privileges path: ADB
  cannot always reach `setUidMode` for system UIDs.
- **Verification plan**: Robolectric test for policy → action conversion. Manual smoke:
  enable Strict on a test app with Firebase Analytics; verify Firebase init fails as
  expected. Survey 5-10 apps for incidental breakage.
- **Estimated complexity**: **M**.
- **Priority**: **P1**.

### NF-08 — Multi-Tag per App + Saved Filter Presets (T8 combined)

- **User problem solved**: Power users want to operate on logical groups ("freeze all
  `:work` tagged apps", "back up `:critical` weekly") without manually re-picking lists.
  Hail demonstrates the demand.
- **Evidence**: ROADMAP T8 "Multi-Tag per App" + "Saved Filter Presets" — both open;
  Hail v1.10.0 model [S65].
- **Proposed behavior**: New `app_tags` table (`tag` TEXT, indexed); join table
  `app_tag_assignments` (`packageName`, `userId`, `tag`). UI: App Details → "Tags" chip
  row with add/remove; main list new tag chip filter row (multi-select). Saved Filter
  Presets reuse the same chip-row picker plus exclusion / inclusion overrides; persist
  as `MainListOptions.Preset` rows.
- **Implementation areas**: Room schema bump (`db/AppsDb.kt` equiv + migration), Room
  DAO, `main/MainViewModel.java` filter chain, App Details Info card, new
  `filters/options/TagsOption.java` for Finder reach.
- **Data model / API / UI implications**: **Schema migration**. New entities. Imports
  from Hail blocklists could populate tags as a natural Day 1 win.
- **Risks and edge cases**: Migration must be reversible (preserve existing rows; tag
  table is additive). Tag-name conflicts with reserved characters (`,`, `:`) need
  escaping. Backup/snapshot export must include tag rows.
- **Verification plan**: Room migration test (`AppsDbMigrationTest`). Pure-JVM
  `TagAssignmentDaoTest`. Manual: create tags `:work` / `:critical` / `:throwaway`,
  filter, run a profile against `:critical`.
- **Estimated complexity**: **L**.
- **Priority**: **P1** (target v0.6.x).

### NF-09 — Routine Operations / Scheduler (T8 open) — incremental on Scheduled Auto-Backup

- **User problem solved**: Upstream Issue #61 has 21 reactions across 5 years. Power
  users want event-triggered profile execution (boot, charging, network available, app
  foreground/background, time-of-day). Scheduled Auto-Backup core (iter-92) already
  proves the WorkManager skeleton.
- **Evidence**: ROADMAP T8 "Routine Operations / Scheduler"; iter-92 → iter-99 Auto-
  Backup work uses WorkManager 2.10.5 which can host arbitrary periodic tasks behind
  charging / network / battery constraints.
- **Proposed behavior**: Generalize `AutoBackupScheduler` into `RoutineScheduler` that
  accepts arbitrary `ProfileTrigger` records (cron / on-boot / on-charging / on-network /
  on-app-foreground). Reuse `ProfileApplierService` for execution. Add Settings →
  Profiles → "Schedules" sub-screen with per-schedule constraints + run-now/history.
- **Implementation areas**: `backup/schedule/AutoBackupScheduler.java` (refactor),
  `profiles/RoutineScheduler.java` (new), `profiles/ProfileTrigger.java` (new), Room
  schema (`profile_triggers` table), Settings UI.
- **Data model / API / UI implications**: New Room migration. New work-tag
  `routine-trigger-<id>`. UsageStats reads for app-foreground triggers require
  `PACKAGE_USAGE_STATS`.
- **Risks and edge cases**: Android 16+ JobScheduler quota model — the API-36
  diagnostics shipped in iter-97 cover this. Boot-triggered triggers need
  `RECEIVE_BOOT_COMPLETED` (already declared). App-foreground triggers will only fire
  with usage-stats granted.
- **Verification plan**: Robolectric `RoutineSchedulerTest`. Manual: configure a
  "charging + Wi-Fi" backup-and-freeze-all-`:throwaway` schedule and observe it fire.
- **Estimated complexity**: **L**.
- **Priority**: **P1** (target v0.6.x — Rootless Power theme already includes scheduler
  polish).

### NF-10 — Premium Polish Phase 2 (AppDetails, AppUsage, Settings v2 surfaces)

- **User problem solved**: Phase 1 of the design v2 token-and-layout rollout shipped
  2026-05-02 (Main activity + item rows). The remaining "Top-5 Surface Migration" called
  out in `design/plan/3-rollout.md` covers AppDetails / AppUsage / Settings — all of
  which `design/audit/4-painpoints.md` flagged as visually under-polished.
- **Evidence**: `design/plan/3-rollout.md` v0.5.x Phase 1 → Phase 2; ROADMAP "Premium
  Polish Track" table (Phase 1 In Progress, Phase 2 not started); existing v2 XML
  resources under `design/impl/{values,layout}/*-v2.xml` ready to copy verbatim.
- **Proposed behavior**: Apply the same `PREF_PREMIUM_PREVIEW_BOOL`-gated dual-layout
  approach used for Main: ship `activity_app_details_v2.xml`, `pager_app_info_v2.xml`,
  `item_app_info_action_v2.xml`, `activity_app_usage_v2.xml`, `item_app_usage_v2.xml`,
  `activity_settings_v2.xml`, `activity_settings_dual_pane_v2.xml`,
  `m3_preference_*_v2.xml`. Inflate v2 conditionally; preserve all view IDs.
- **Implementation areas**: `app/src/main/res/layout/*_v2.xml` (new), conditional
  inflation in `AppDetailsActivity`, `AppUsageActivity`, `SettingsActivity`,
  `AppearancePreferences` (no behaviour change, only token application).
- **Data model / API / UI implications**: None.
- **Risks and edge cases**: Preserve every `findViewById` site (adapters and activities
  bind by ID). WCAG 2.2 AA contrast lock per design notes — re-run contrast checks for
  the v2 palette against new App Details header surface.
- **Verification plan**: Manual: toggle Pro Mode off/on across both layouts; run on Pixel
  9a + Galaxy A57 (One UI 8.5) + Moto g22 (Android 12). TalkBack pass on each v2 layout.
- **Estimated complexity**: **L**.
- **Priority**: **P1** (gates v0.6.x default flip).

### NF-11 — Package Visibility Analysis panel (T9 open, LibChecker model)

- **User problem solved**: Holders of `QUERY_ALL_PACKAGES` can enumerate every installed
  app — a meaningful privacy/attack-surface signal. Today NG shows declared permissions
  but not a packaged-up "who can see what" panel.
- **Evidence**: ROADMAP T9 "Package Visibility Analysis" — open; LibChecker 2.5.2 model
  [S78].
- **Proposed behavior**: New App Details → "Visibility" sub-card (or row in Info card).
  Three sections: (a) does this app hold `QUERY_ALL_PACKAGES`? (b) `<queries>` manifest
  entries it declares; (c) other installed apps that explicitly list this package in
  their own `<queries>`. Uses `PackageInfo.requestedPermissions` + manifest parsing — no
  root.
- **Implementation areas**: `details/info/PackageVisibilityHelper.java` (new),
  `AppInfoFragment.java` card render, possibly Finder filter `visibility_query_all`.
- **Data model / API / UI implications**: Pure read; cache (c) lazily — needs a pass
  across all installed apps.
- **Risks and edge cases**: Section (c) is O(N·M) — debounce or compute on background;
  cache by `versionCode`.
- **Verification plan**: Pure-JVM `PackageVisibilityHelperTest` against fixtures.
- **Estimated complexity**: **M**.
- **Priority**: **P2**.

### NF-12 — Privacy Dashboard integration (T9 open)

- **User problem solved**: Android 12+ exposes per-permission usage history but it's only
  reachable from system Settings. Surfacing the same timeline in App Details closes the
  loop ("Permission Manager says Camera last used 3h ago — what was that?").
- **Evidence**: ROADMAP T9 "Privacy Dashboard Integration" — open.
- **Proposed behavior**: New App Details → Permissions tab → "Recent activity" expandable
  row. Reads `PermissionUsageHelper`-equivalent via `PermissionControllerManager` (API
  31+); falls back to deep-linking system Privacy Dashboard on older releases.
- **Implementation areas**: `permissions/PermissionUsageReader.java` (new, API 31+ +
  reflection fallback), `AppDetailsPermissionsFragment.java`.
- **Data model / API / UI implications**: Live read.
- **Risks and edge cases**: Requires `PACKAGE_USAGE_STATS` grant; fall back gracefully.
- **Verification plan**: Manual: Pixel 9a (Android 17) → grant usage-stats → confirm
  timeline; older device → confirm deep-link out.
- **Estimated complexity**: **M**.
- **Priority**: **P2**.

### NF-13 — JADX 1.4.7 → 1.5.5 dependency bump (Eng-Debt)

- **User problem solved**: Gates T12 APK editing work. JADX 1.5.5 includes the
  critical multi-thread UI fix, `.apks` ingestion, CJK composite font support, and a
  plugin API (1.5.4+).
- **Evidence**: ROADMAP Eng-Debt Register row "JADX `1.4.7`" — 7 releases behind;
  `versions.gradle` confirms.
- **Proposed behavior**: Update `versions.gradle` `jadx_version = "1.5.5"`. Re-test
  `DexUtils.toJavaCode` on a representative fixture; verify the MuntashirAkon
  android-jadx fork has a corresponding 1.5.5 tag — if not, file an upstream maintainer
  issue and consider pinning the fork at a specific commit.
- **Implementation areas**: `versions.gradle`, possibly `scripts/android-libraries`
  submodule pointer if MuntashirAkon fork tag exists.
- **Data model / API / UI implications**: None — internal dep.
- **Risks and edge cases**: API drift between 1.4.7 and 1.5.5 (`JadxArgs` evolution); the
  build will fail loudly if so.
- **Verification plan**: `./gradlew :app:assembleFlossDebug` + targeted `DexUtilsTest`.
- **Estimated complexity**: **S**.
- **Priority**: **P1**.

### NF-14 — JaCoCo coverage badge in `tests.yml`

- **User problem solved**: 125 JVM tests + 2 instrumented. No visibility into trend.
  Pass-40 (F-NEW-11) recommended this; not yet shipped.
- **Evidence**: `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` F-NEW-11;
  `.github/workflows/tests.yml`.
- **Proposed behavior**: Add JaCoCo plugin to `:app` and `:libcore`. Publish HTML
  coverage report as a workflow artifact + a shields.io-compatible JSON badge committed
  to `gh-pages` (or stored at `docs/coverage/badge.json` via the existing pages-free
  pattern used for `docs/fingerprints.txt`).
- **Implementation areas**: `build.gradle` (Jacoco plugin block), `tests.yml`,
  `docs/coverage/badge.json` + GH workflow that updates it on `main`.
- **Data model / API / UI implications**: None.
- **Risks and edge cases**: Initial coverage will look low (no panic — feature-test
  coverage is high but UI coverage isn't). Don't gate CI on coverage threshold; this is
  observability only.
- **Verification plan**: PR with the change → green workflow → README badge updates.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### NF-15 — Markdown link checker for `PROJECT_CONTEXT.md` + ROADMAP

- **User problem solved**: 20+ relative links in `PROJECT_CONTEXT.md` and many `[S###]`
  references in `ROADMAP.md`. Either silently bit-rot.
- **Evidence**: `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` F-NEW-13;
  `.github/workflows/docs-link-check.yml` exists — confirm scope covers
  `PROJECT_CONTEXT.md`.
- **Proposed behavior**: Verify `docs-link-check.yml` already covers root markdown files;
  if not, add `PROJECT_CONTEXT.md` and `ROADMAP.md` to its glob and add a follow-up
  workflow that flags broken `[S###]` references against the Source Appendix table.
- **Implementation areas**: `.github/workflows/docs-link-check.yml`; possibly a small
  `scripts/check-source-appendix.py` helper.
- **Data model / API / UI implications**: None.
- **Risks and edge cases**: External URLs in the Source Appendix may rate-limit; restrict
  to relative-link checks.
- **Verification plan**: Intentionally break a link in a draft branch; confirm the check
  fails.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### NF-16 — "What changes if I remove this?" debloat preview (Canta delta)

- **User problem solved**: Debloater shows safety rating + dependencies, but not the
  user-facing impact ("Remove SMS app → losing default-SMS role → no SMS Auth"). Canta
  approaches this; SD Maid hints at it.
- **Evidence**: ROADMAP T7 "Bloatware Safety Rating in App Detail" ✅ already surfaces
  Safe/Replace/Caution/Unsafe via `getBloatwareSafetyLabel`; no behavioural-impact summary
  beyond the existing label.
- **Proposed behavior**: New "Impact preview" expandable in the Debloater confirmation
  dialog: lists default-role losses (SMS / dialer / browser / launcher / home if
  applicable from `RoleManager` ownership), permission-grant changes that other apps may
  inherit, and any app that lists this package in its `<queries>` and might break.
- **Implementation areas**: `debloat/ImpactPreviewHelper.java` (new), `debloat/
  DebloaterActivity.java` confirmation dialog.
- **Data model / API / UI implications**: Live read; debloat-definition file already
  carries removal-type; supplement with `RoleManager.getRoleHolders` checks.
- **Risks and edge cases**: `RoleManager` is API 29+; surface a generic "May affect
  default app roles" copy below.
- **Verification plan**: Manual on Samsung S25 Ultra Debloater confirmation flow with
  Samsung SMS / dialer; pure-JVM test for the helper.
- **Estimated complexity**: **M**.
- **Priority**: **P2**.

### NF-17 — Per-app runtime telemetry panel (Inure model, partial)

- **User problem solved**: App Info currently shows installed metadata + signing certs +
  permissions. Power users want **observed behaviour**: foreground time today / week,
  network bursts, wakelocks, foreground service runtime, top-N battery consumers.
- **Evidence**: Inure ships this; ROADMAP T20 (Performance & Profiling) is broader and
  more invasive (Perfetto, LeakCanary, simpleperf). This is the **opt-out, no-extra-deps**
  slice that can ship before the heavy weights.
- **Proposed behavior**: New App Details → "Activity" sub-card (distinct from the
  existing Usage tab). Uses `UsageStatsManager` (already granted via opt-in) +
  `BatteryStatsManager` (API 31+) for wakelock summary +
  `NetworkStatsManager.queryDetailsForUid` for last-24h bursts.
- **Implementation areas**: `details/runtime/AppRuntimeTelemetryHelper.java`,
  `AppDetailsActivity` new pager fragment, layout.
- **Data model / API / UI implications**: Live reads; no storage.
- **Risks and edge cases**: `BatteryStatsManager` is rate-limited on API 33+; cache for
  60s. Some OEMs (One UI) restrict `NetworkStatsManager` for non-VPN apps.
- **Verification plan**: Manual on test device + Pixel 9a; observed numbers vs. system
  Settings → Battery / Network.
- **Estimated complexity**: **M**.
- **Priority**: **P2**.

### NF-18 — Keystore-password `char[]` lifecycle hardening (T3 follow-up)

- **User problem solved**: T3 closed the "use `char[]`" change in v0.3.0, but the
  iter-N audit cycle keeps surfacing keystore-password flow questions. Pin the contract
  with a JUnit invariant.
- **Evidence**: ROADMAP T3 row ✅ + Eng-Debt note `KeyStoreUtils.java` is closed; no
  pinned test enforces "no String captured during keystore unlock".
- **Proposed behavior**: New `KeyStorePasswordLifecycleTest` that asserts
  `KeyStoreManager` and `CompatUtil` never assign keystore-password parameters to
  `String` fields, and that `Arrays.fill(password, '\0')` is called in every release path
  (use reflection or a Lint custom rule).
- **Implementation areas**: `app/src/test/java/.../crypto/KeyStorePasswordLifecycleTest.java`;
  optional `lint-rules/` module for a Lint check.
- **Data model / API / UI implications**: None.
- **Risks and edge cases**: Lint custom-rule plumbing has historically been finicky; a
  reflection-based test is the lighter starting point.
- **Verification plan**: Test runs in `tests.yml`.
- **Estimated complexity**: **S**.
- **Priority**: **P3**.

---

## Existing Feature Improvements

### EI-01 — README v0.5.0 theme line is stale

- **Current behavior**: README §Roadmap line 93 says `v0.5.0 — Settings reorganization
  by task, global in-app search, contextual help tooltips, in-app changelog viewer`.
- **Problem**: matches ROADMAP, but the implication that v0.5.0 has not shipped is
  out of date — many v0.5.0 items shipped iter-91 → iter-141 already (per CHANGELOG
  `Unreleased`). README's `v0.4.0` line also already shows the Permission Inspector +
  Onboarding wizard as shipped, but the cut into a `v0.5.0` release tag has not happened
  yet. NF-01 fixes the underlying gap.
- **Recommended change**: After NF-01 lands, update README §Roadmap to mark `v0.5.0` ✅
  with the actual delivered set; add `v0.6.0` line ("Rootless Power: scheduler, multi-
  tag, tracker blocking") to keep the preview block one-release-ahead.
- **Code locations**: `README.md` lines 86-94.
- **Backward compatibility concerns**: None.
- **Verification plan**: Eyeball.
- **Estimated complexity**: **S**.
- **Priority**: **P0** (bundled with NF-01).

### EI-02 — `CLAUDE.md` Status section stops at factory-iter-7 (2026-05-02)

- **Current behavior**: `CLAUDE.md` Status section ends at `factory iter-7`. The
  intervening 18 → 23 iters and v0.4.0 → v0.4.2 releases plus iter-91 → iter-141 work
  are not represented.
- **Problem**: Future sessions land at the file expecting current state; instead they
  see a stale snapshot that contradicts CHANGELOG.
- **Recommended change**: Truncate `## Status` to a single line pointing at
  `PROJECT_CONTEXT.md` (which already has the current state), or add a "Status pointer"
  paragraph: "Current state lives in `PROJECT_CONTEXT.md`; iter-18 → iter-141 history
  lives in `CHANGELOG.md` and `.ai/research/2026-05-18-iter-*/`. Historical iter-6 /
  iter-7 notes are preserved below for archaeology."
- **Code locations**: `CLAUDE.md` lines 123-148 approx.
- **Backward compatibility concerns**: None (other tools read PROJECT_CONTEXT.md).
- **Verification plan**: Eyeball + the existing docs-link-check workflow.
- **Estimated complexity**: **S**.
- **Priority**: **P0**.

### EI-03 — Bundled `raw/changelog.xml` is upstream v4.0.5 verbatim

- **Current behavior**: `app/src/main/res/raw/changelog.xml` (1824 lines) ships upstream
  v4.0.5 release notes verbatim. `ChangelogActivity` can render it but is not currently
  reachable from any user-facing entry point.
- **Problem**: If a user opens the Activity (e.g. via an `am://` deep link or a future
  About-screen entry), they see notes that describe a project they're not running.
- **Recommended change**: Replace with NG-native v0.1.0 → v0.5.0 content (one
  `<release>` block per tag, drawn from CHANGELOG.md). Lock the DTD locally at
  `schema/changelog.dtd` (mirrored from upstream) to avoid the parser hitting the
  upstream URL.
- **Code locations**: `app/src/main/res/raw/changelog.xml`, `schema/changelog.dtd`,
  `changelog/ChangelogParser.java`.
- **Backward compatibility concerns**: None (parser shape unchanged).
- **Verification plan**: Run `ChangelogActivity` on debug build; verify content matches
  CHANGELOG.md.
- **Estimated complexity**: **M**.
- **Priority**: **P0** (paired with NF-02).

### EI-04 — Permission Inspector lacks a "find apps that should NOT have X" lens

- **Current behavior**: Permission Inspector lists apps that currently hold a given
  dangerous-permission group, with a per-app toggle and a bulk "Revoke for all apps"
  action.
- **Problem**: The mental flip — "find me everything that has Camera that I don't trust"
  — relies on the user knowing the trust answer. A "Show only apps installed in the last
  30 days holding X" or "Show only sideloaded apps holding X" filter would catch newly
  arrived apps with surprising permissions.
- **Recommended change**: Permission Inspector toolbar gains a chip row matching the main
  list (User / System / Recently installed / Sideloaded / Privileged signer-mismatch).
- **Code locations**: `permission/inspector/PermissionInspectorActivity.java`,
  `PermissionInspectorAdapter.java`, related XML.
- **Backward compatibility concerns**: None — additive.
- **Verification plan**: Manual smoke; toggle Camera + "Recently installed" → confirm
  result is the intersection.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### EI-05 — Onboarding wizard "next steps" hint after first-run

- **Current behavior**: Onboarding card renders capability matrix + Wireless ADB pairing
  + Shizuku setup; finishing dismisses it. No follow-up nudge that says "you set up
  Shizuku — open the Permission Inspector now?".
- **Problem**: First-run users complete onboarding then hit the bare main list with no
  signpost.
- **Recommended change**: Last onboarding step adds three "Next steps" tiles: (a) Open
  Permission Inspector, (b) Run a privilege smoke test (Mode Doctor), (c) Set up
  Scheduled Auto-Backup. Each tile is a `Intent`-launching button.
- **Code locations**: `onboarding/OnboardingFragment.java` final-step layout +
  `fragment_onboarding.xml`.
- **Backward compatibility concerns**: Onboarding survey logic unchanged.
- **Verification plan**: Manual: fresh install on emulator; ensure each tile lands on
  the right screen.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### EI-06 — Debloater confirmation should label OEM-detected packages inline

- **Current behavior**: Debloater confirmation summarises android-debloat-list safety
  ratings + dependency / required-by warnings + high-risk examples; iter-104 added
  OEM-uninstall-blocker bypass (Samsung One UI 8.5 SmartSuggestions, MIUI core, OPlus
  ColorOS / OxygenOS / Realme).
- **Problem**: The OEM-blocker label currently surfaces in `OemBloatRiskTable` warnings
  but is not inline on each row of the confirmation list — users may scan, miss the
  warning, and approve a freeze-by-default mass uninstall override.
- **Recommended change**: Add a small "OEM-protected" chip on each row in the
  confirmation list when `OemBloatRiskTable.isProtected(pkg)` returns true.
- **Code locations**: `debloat/DebloaterConfirmAdapter.java` (or analogous),
  `OemBloatRiskTable.java`.
- **Backward compatibility concerns**: None.
- **Verification plan**: Manual: Samsung One UI 8.5 device → select 5 SmartSuggestions /
  MIUI packages → confirm each row carries the chip.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### EI-07 — Scheduled-backup status row needs a "Why did the last run skip?" link

- **Current behavior**: Scheduled-backup status row shows WorkManager state + attempt +
  stop reason + next run time + JobScheduler pending-reason snapshot (API 36+).
- **Problem**: A user reading "Skipped 47 recent backups" doesn't know which packages
  were skipped because they were inside the freshness window vs. blocked by other
  policy.
- **Recommended change**: Tap the status row → bottom-sheet "Skip diagnostics" listing
  each skipped package + reason category (`fresh_backup_in_window`, `package_filtered`,
  `unavailable_user`, `backup_locked`).
- **Code locations**: `backup/schedule/AutoBackupDiagnostics.java`,
  `BackupRestorePreferences.java`.
- **Backward compatibility concerns**: None.
- **Verification plan**: Manual: force a scheduled run with freshness window 24h on a
  fresh-backup tree; observe row + bottom-sheet detail.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

### EI-08 — Mode Doctor probe report should expose share-as-Support-Info-Bundle action

- **Current behavior**: Mode Doctor opens a copyable PASS/WARN/FAIL/SKIP report with fix
  hints. Support Info Bundle Composer is a separate Settings → Troubleshooting entry.
- **Problem**: Two flows that should be one. A user diagnosing a Shizuku-or-root issue
  wants Mode Doctor results + Support Info Bundle in one share.
- **Recommended change**: Mode Doctor footer adds "Share with bundle" action that calls
  `SupportInfoBundle.compose()` with the Mode Doctor report inlined.
- **Code locations**: `settings/PrivilegeModeDoctor.java`, `settings/SupportInfoBundle.java`.
- **Backward compatibility concerns**: None.
- **Verification plan**: Manual: Mode Doctor → Share → chooser.
- **Estimated complexity**: **S**.
- **Priority**: **P3**.

### EI-09 — Per-app rollback planner: dry-run preview before confirming

- **Current behavior**: App Details → "Revert AppManager changes" runs an inverse-planner
  pass over op-history, surfaces non-invertible rows for manual review, confirms then
  applies.
- **Problem**: Users want to see *what specifically will revert* (e.g. "Re-enable the
  Camera permission you revoked on 2026-04-12") before tapping confirm.
- **Recommended change**: Pre-confirm bottom sheet listing each planned inverse op (e.g.
  "Re-grant `android.permission.CAMERA`", "Re-enable `com.app/.SyncService`") with a
  per-row enable/disable checkbox.
- **Code locations**: `history/PerAppRollbackManager.java`,
  `details/AppDetailsActivity.java`.
- **Backward compatibility concerns**: None — additive UI before existing confirm.
- **Verification plan**: Manual: revoke a permission + freeze a service → tap revert →
  bottom-sheet → un-check one row → confirm → verify only the other row reverted.
- **Estimated complexity**: **M**.
- **Priority**: **P2**.

### EI-10 — Onboarding "USB debugging off" preflight: re-check on resume

- **Current behavior**: Onboarding's Wireless ADB setup preflights
  `Settings.Global.adb_enabled`; if off, prompts the user to enable.
- **Problem**: After the user enables USB debugging in system settings and returns, the
  preflight doesn't auto-re-check.
- **Recommended change**: Re-run `adb_enabled` read in `onResume()` of the Wireless ADB
  setup card.
- **Code locations**: `onboarding/OnboardingFragment.java`,
  `onboarding/OnboardingWirelessAdbCard.java` (or equivalent).
- **Backward compatibility concerns**: None.
- **Verification plan**: Manual: emulator with Developer options closed → open onboarding
  → toggle USB debugging in settings → back to NG → confirm preflight re-evaluates.
- **Estimated complexity**: **S**.
- **Priority**: **P2**.

---

## Reliability, Security, Privacy, and Data Safety

- **CVE-2026-0073 ADB-mode advisory** lives at `docs/security-advisories/`. No new public
  advisories surfaced this pass.
- **Backup integrity** — metadata v7 + HKDF-SHA256 per-archive keys + per-file SHA-256
  digests + restore-time mismatch raises `BackupException`. Strong.
- **Zip-slip** — Audited clean 2026-05-08; FM ZIP create/extract (iter-90) uses normalized
  entry-name reject + post-create real-path containment. Strong.
- **Install transcript URI redactor** — userinfo / query / fragment leak closed
  (iter-23). Strong.
- **Restricted Settings unlock** — install-source-aware classification, not a public
  per-app bit. Wording is correctly probabilistic. Strong.
- **Sensitive-action auth gate (iter-122)** — gates install, uninstall, clear-data behind
  Android screen lock. Should be expanded to: explicit-component-disable, AppOps mode
  change for `READ_CALL_LOG`/`READ_SMS`/`ACCESS_FINE_LOCATION` groups, and tracker
  blocking (NF-07) actions. Low-effort follow-up; add to ROADMAP.
- **Permission Change Monitor + Signing-Cert Change Alert** — opt-in, default OFF. Strong
  privacy stance.
- **Doze allowlist revert diagnostics** — surfaces OEM-policy hints. Strong.
- **Sideload verification (BR/ID/SG/TH)** — `docs/sideload-verification.md` exists;
  installer warns when `developer_verifier` service is exposed. Strong; **action**:
  before 2026-09-30 enforcement date, add a one-screen "What to expect" walkthrough
  triggered first-launch for users in those locales (use `Locale.getDefault().getCountry()`
  as a non-authoritative hint, not a hard gate).
- **Self-update prompt** — NG distributes outside Play Store. If a future v0.6.0+ adds
  an Obtainium-style self-check, do it as an *opt-in* network call gated by the
  `full`-flavor Internet feature switch, not as a default behaviour. Listed under T1 as
  Obtainium delegation today — keep that posture.

### Gaps worth addressing

- **No app-internal sandbox for the smali decoder.** `DexClasses.toJavaCode` runs JADX
  in-process. JADX 1.4.7 has documented crash modes on malformed APKs (S94, S128). NF-13
  (JADX 1.5.5 bump) closes the most-cited mitigations.
- **Privileged-batch journal** (iter-87) handles binder-death recovery for Shizuku/Sui
  but does not survive a system-server crash that takes both the binder AND the
  AppManagerNG process. Acceptable; document it.
- **TLS to the debloat-definition manifest** — pinned to the SysAdminDoc/AppManagerNG raw
  GitHub URL with SHA-256 + byte-length verification at download time. Strong; consider
  adding a one-line `User-Agent` rotation comment so a future audit doesn't flag the
  generic UA as fingerprintable.

---

## UX, Accessibility, and Trust

### Onboarding gaps

- Captured by NF-05 (contextual tooltips) + EI-05 (next-steps tiles) + EI-10 (re-check
  on resume). Onboarding is otherwise strong (capability detection, Shizuku version
  check, USB-debugging preflight, ADB tcpip reuse, Dhizuku surface, banking-app
  side-effect warning, Android 17 Shizuku warning).

### Empty / loading / error / disabled states

- Main list: NG already ships `view_main_empty_state.xml` with title + summary + action.
  `design/audit/4-painpoints.md` flags it as "functional stock empty state, still
  generic". Phase 2 should ship a small package/filter illustration + active-filter
  echo. **NF-10 carries this**.
- Skeleton-row loading on main list / Finder / Permission Inspector: ROADMAP design row
  noted; not yet shipped. Roll into NF-10 Phase 2 surfaces.
- Error states for backup-stream IO failure (SAF provider disconnect mid-write) — the
  iter-130 CIFS hardening covers byte-count verification but doesn't surface a
  per-package "this app's data backup failed because the provider dropped at offset N"
  diagnostic. Low priority; backup retention policy + manual rerun mitigates.

### Destructive / irreversible actions

- Already gated: uninstall (iter-122), clear-data (iter-122), install (iter-122 for
  packages flagged as system-altering), revoke-all permissions (Permission Inspector).
- Not yet gated: **Bulk freeze via QS-tile / Assist action** — these dispatch through
  `ProfileApplierService` without re-prompting. Defensible (the profile is user-selected)
  but adding a one-tap "are you sure?" toast (not a blocking dialog) for >10 packages
  is cheap insurance. P3.

### Settings clarity

- NF-04 (reorganize by task) + NF-03 (in-app search) + NF-05 (contextual help) form the
  v0.5.0 Discovery theme.

### Accessibility issues

- T10 row "TalkBack Navigation Audit" — still open. Should be a v0.6.0 deliverable
  before the Premium Polish toggle flip. The v2 layouts shipping in NF-10 give us the
  natural moment for a TalkBack pass.
- T10 "Font Scale Stress Test" — open. New surfaces (Privileges screen, Mode Doctor,
  scheduled-backup status row, per-app rollback preview) need a 200%-font verification.
- T17 "High-Contrast Theme" — open. Cheap addition; the v2 token plane already separates
  semantic colors, making a high-contrast variant a small token swap.

### Microcopy and trust signals

- Mode-of-Ops chooser: the segmented-button promotion is closed (iter-23) — confirm on
  device.
- Backup encryption picker — `design/audit/4-painpoints.md` calls out dialog density.
  NF-05 (contextual tooltips) covers the explainer; Phase 2 of NF-10 covers the visual.

---

## Architecture and Maintainability

### Module or boundary improvements

- **`app/` is the bulk of the codebase (629 Java files)**. Two natural extractions if the
  team ever wants to split:
  - `app/.../filters/`, `filters/options/`, `filters/FilterableAppInfo` → could move to
    a `libcore-filters` Gradle module for use by Finder + main list + Permission
    Inspector + profiles. Worth it once Multi-Tag (NF-08) lands.
  - `app/.../backup/` → backup engine could be a `libbackup` module to insulate the rest
    of the app from BC/Tar code. Out of scope until T6 settles after iter-130/132.
- **No `app/src/main/kotlin/` directory exists** despite CLAUDE.md mentioning Kotlin
  newer additions. New features are landing in Java. Not a problem, just a documentation
  drift — note it in CLAUDE.md.

### Refactor candidates

- `MainActivity.java` (1303 lines) and `ActivityInterceptor.java` (1357 lines) carry
  long methods and pre-MVVM patterns. Don't break them up speculatively — but on the
  next significant change to either, opportunistically extract one cohesive piece.
- `OnboardingFragment.java` (858 lines) gathers many capability cards in one file. The
  iter-141 tcpip detection added complexity without a refactor; refactoring into
  `onboarding/cards/<Card>.java` pieces would help maintainability — defer until the
  next onboarding-tier feature lands.

### Test gaps

- 125 JVM tests + 2 instrumented. JVM coverage is high in `backup/`, `permission/`,
  `filters/`, `dex/`, `shizuku/`, `runner/`. Instrumented coverage is sparse — only
  hidden-API and onboarding fragment instrumented tests.
- **Recommend** adding instrumented Robolectric tests covering the new v2 layouts (NF-10)
  to lock the dual-layout contract (assert `findViewById` returns the same shape for
  both classic and v2 XML).
- **Recommend** instrumented coverage for `AutoBackupShortcutActivity` — shortcut
  intents are a common regression source.

### Documentation gaps

- `docs/architecture/` exists with 3 docs (privilege-providers, backup-format, hidden-api-
  bypass) — strong start.
- **Missing**: `docs/architecture/04-filter-finder.md` (FilterableAppInfo +
  FilterOptions + relevance scorer + multi-user load) — would help future filter / multi-
  tag work (NF-08).
- **Missing**: `docs/architecture/05-routine-scheduler.md` — when NF-09 lands, document
  the WorkManager + JobScheduler quota model.
- **Missing**: `docs/policy/permissions.md` — every Android permission NG declares + why.
  Manifest is 1709 lines and reads as a wall of permissions to reviewers; a one-pager
  lookup table would help F-Droid / IzzyOnDroid Anti-Features review.

### Release / build / deployment gaps

- Reproducible-build gate shipped (iter-23) and hardened (iter-23 2026-05-16). Strong.
- AGP 9.2 / Gradle 9.4.1 (iter-137) — strong.
- **Missing**: a single `scripts/release.{ps1,sh}` orchestrator that walks the
  versionCode bump, CHANGELOG `Unreleased` → `## v0.5.0 - <date>` rewrite, `fastlane/`
  changelog file creation, tag, and `gh release create`. NF-01 is the chance to write it.

---

## Prioritized Roadmap

### Phase 0 — Hygiene cut (target this week)

- [ ] P0 - Commit or revert the in-progress Component Rules editor
  - Why: 3 modified + 3 new files in working tree; either complete the feature for v0.5.0
    or revert to avoid drift.
  - Evidence: `git status -sb` working tree;
    `app/src/main/java/.../settings/ComponentRulesPreferences.java` is new.
  - Touches: `app/src/main/java/.../rules/compontents/ComponentUtils.java`,
    `app/src/main/java/.../rules/compontents/ComponentRulesPreview.java`,
    `app/src/main/java/.../settings/ComponentRulesPreferences.java`,
    `app/src/main/res/values/strings.xml`, `app/src/main/res/xml/preferences_rules.xml`,
    `app/src/test/.../rules/compontents/ComponentRulesPreviewTest.java`.
  - Acceptance: `git status` clean, or revert with explanatory CHANGELOG entry.
  - Verify: `./gradlew :app:testFlossDebugUnitTest --tests "*ComponentRulesPreviewTest"`
    passes; manually open Settings → Rules → Component rules and verify shape.

- [ ] P0 - Investigate `main` `ahead 177 / behind 177` parity with `origin/main`
  - Why: A parallel history blocks any clean push or tag. Likely a force-push from a
    different host.
  - Evidence: `git status -sb`.
  - Touches: git history.
  - Acceptance: branch state is one-sided ahead or in sync.
  - Verify: `git log --oneline origin/main..HEAD` and `git log --oneline HEAD..origin/main`
    yield expected disjoint sets.

- [ ] P0 - **NF-01**: Cut v0.5.0 release
  - Why: 177 commits + Premium polish + iter-141 sit in `Unreleased`. Bank them.
  - Evidence: `CHANGELOG.md Unreleased`; fastlane changelogs jump from 6.txt to nothing.
  - Touches: `versions.gradle`, `app/build.gradle`, `CHANGELOG.md`,
    `fastlane/metadata/android/en-US/changelogs/7.txt`, `app/src/main/res/raw/changelog.xml`
    (NG-native rewrite — see NF-02), README badge, ROADMAP "Last updated".
  - Acceptance: Tag `v0.5.0` exists; both flavor APKs build reproducibly; release notes
    publish at `github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.5.0`.
  - Verify: `scripts/verify_reproducible_release.{ps1,sh}` clean; install on Pixel 9a +
    S25 Ultra; smoke-test scheduled-backup, batch APK install, per-app rollback, Mode
    Doctor.

- [ ] P0 - **EI-01 + EI-02 + EI-03**: README / CLAUDE.md / bundled-changelog drift
  - Why: Three independent docs that lie about current state.
  - Evidence: README §Roadmap line 93; CLAUDE.md §Status ends 2026-05-02;
    `raw/changelog.xml` opens with `version="v4.0.5"`.
  - Touches: `README.md`, `CLAUDE.md`, `app/src/main/res/raw/changelog.xml`, `schema/
    changelog.dtd`.
  - Acceptance: README marks v0.5.0 ✅; CLAUDE.md §Status points at PROJECT_CONTEXT.md;
    `raw/changelog.xml` describes only NG releases.
  - Verify: Open ChangelogActivity on debug build; confirm content matches CHANGELOG.md.

### Phase 1 — v0.5.0 Discovery polish (next 2 weeks)

- [ ] P0 - **NF-02**: In-app changelog viewer (NG-native data)
  - Why: v0.5.0 deliverable.
  - Evidence: ROADMAP §Committed Version Targets.
  - Touches: `raw/changelog.xml`, `ChangelogParser`, `AboutPreferences`, `MainActivity`,
    new `ChangelogBottomSheet`.
  - Acceptance: Settings → About → "What's new" opens viewer with NG content; first
    launch after version bump shows the bottom-sheet once.
  - Verify: Manual; bump versionCode in a side build.

- [ ] P0 - **NF-03**: Global in-app search
  - Why: v0.5.0 deliverable.
  - Evidence: ROADMAP §Committed Version Targets.
  - Touches: `settings/SettingsSearchIndex.java` (new),
    `settings/SettingsActivity.java`, `SettingsSearchAdapter.java`.
  - Acceptance: SearchView in Settings toolbar; queries land on the right fragment.
  - Verify: Robolectric `SettingsSearchIndexTest`; manual smoke on 5-7 queries.

- [x] P1 - **NF-04**: Settings reorganization by task — **ALREADY SHIPPED (audit 2026-05-25)**
  - Why: v0.5.0 deliverable.
  - Evidence: ROADMAP §Committed Version Targets.
  - **Outcome (2026-05-25):** `xml/preferences_main.xml` already ships four
    task-organized `PreferenceCategory` groups with descriptive summaries:
    *Foundation* (Language, Appearance, Privacy, Mode of operations,
    Privileges, Guided mode), *Package workflows* (APK signing, Installer,
    Backup/Restore, VirusTotal), *Tools and data* (Log viewer, File Manager,
    Rules, Advanced), *Support and diagnostics* (About device, About,
    Troubleshooting). The original plan called for "6 groups (Get set up /
    Use power tools / Take control of data / Stay safe / Diagnose / Make it
    yours)"; on review the existing 4-category structure satisfies the same
    task-flow goal without further surgery. Leaving the row closed; future
    refinements (e.g. promoting Privacy + VirusTotal into a dedicated *Stay
    safe* group) belong to a separate UX iteration tied to TalkBack + RTL
    pass (T10).

- [ ] P1 - **NF-05**: Contextual help tooltips
  - Why: v0.5.0 deliverable.
  - Evidence: ROADMAP §Committed Version Targets;
    `design/audit/4-painpoints.md`.
  - Touches: new `utils/HelpTipRegistry.java`, `utils/HelpTip.java`, ~6 integration
    points.
  - Acceptance: `?` icon next to ~20 known-friction controls opens a plain-language
    bottom-sheet explainer.
  - Verify: Manual; TalkBack on each registered help target.

- [x] P1 - **NF-13**: JADX 1.4.7 → 1.5.5 dep bump — **PARKED 2026-05-25**
  - Why: Eng-Debt; gates T12 APK editing.
  - Evidence: ROADMAP Eng-Debt Register row "JADX 1.4.7".
  - **Outcome (2026-05-25):** `gh api repos/MuntashirAkon/jadx-android/tags` confirms the MuntashirAkon Android fork has no `1.5.5` tag — `1.4.7` is the most recent upstream-tracking tag (the fork's `v0.x`/`v1.x.0` tags are Android-only patches). Bump is gated on the fork publishing `1.5.5`; switching to upstream `jadx` from JetBrains is not viable because it lacks the desugared Android build. ROADMAP Eng-Debt row updated with the blocker and the reopen condition.

### Phase 2 — Premium Polish Phase 2 + ahead-of-v0.6.0 work (4-8 weeks)

- [x] P1 - **NF-10**: Premium Polish Phase 2 — **PARKED 2026-05-25 (next session)**
  - Why: Phase 1 shipped 2026-05-02; Phase 2 is the next chunk before v0.6.x default
    flip.
  - Evidence: `design/plan/3-rollout.md`; ROADMAP §Premium Polish Track.
  - **Outcome (2026-05-25):** `design/impl/layout/` only stages
    `activity_main_v2.xml` and `item_main_v2.xml` (Phase 1, already shipped). Phase 2
    layouts have not been authored yet — they need designer input, not just a token
    swap, and validation needs real-device or wide Robolectric layout-inflation tests.
    Plan + suggested next slice recorded in
    [`.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md`](.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md).

- [x] P1 - **NF-07**: Tracker Blocking via AppOps — **SHIPPED 2026-05-25**
  - Why: T9 open; complements existing tracker scanner with **action**.
  - Evidence: ROADMAP T9 "Tracker Blocking (AppOps)".
  - Touches: `tracker/TrackerBlockingPolicy.java`, `tracker/TrackerBlockingApplier.java`,
    App Details Trackers tab, `BatchOpsManager`.
  - Acceptance: 3-intensity policy; per-app override; works under Shizuku/ADB without
    root for non-system UIDs.
  - Verify: Robolectric policy → action conversion test; manual on a test app with
    Firebase Analytics.

- [x] P1 - **NF-09**: Routine Operations / Scheduler — **PARKED 2026-05-25 (next session)**
  - Why: T8 open; upstream Issue #61 (21 reactions).
  - Evidence: ROADMAP T8 "Routine Operations / Scheduler"; iter-92 → iter-99
    Scheduled Auto-Backup is the WorkManager skeleton.
  - **Outcome (2026-05-25):** Generalising the scheduler needs Room migration
    validation, a new Worker, and per-trigger plumbing (boot / charging / network /
    foreground). Migration + Worker execution cannot be validated from a CI host. Plan
    + a "suggested next slice" (SharedPreferences-backed trigger store, reuse
    `ProfileApplierService` as executor) recorded in
    [`.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md`](.ai/research/2026-05-25-iter-143/CONTINUE_FROM_HERE.md).

- [x] P1 - **NF-08**: Multi-Tag per App + Saved Filter Presets — **DATA LAYER SHIPPED 2026-05-25**
  - Why: T8 open; pairs with NF-09 (tag-targeted schedules).
  - Evidence: ROADMAP T8.
  - **Outcome (2026-05-25):** Shipped the SharedPreferences-backed
    `AppTagStore` + `TagsOption` Finder predicate (covering `any`, `none`,
    `has_all`, `has_any`, `missing_all`). The App Details tag editor + main-list
    chip + Room migration are deferred — the data layer is the contract a UI
    iteration can build against without further design work. Pure-JVM coverage at
    `AppTagStoreTest`.

### Phase 3 — Privacy / Diagnostics deepening (8-12 weeks)

- [ ] P2 - **NF-11**: Package Visibility Analysis panel
- [ ] P2 - **NF-12**: Privacy Dashboard integration (Android 12+ usage timeline)
- [ ] P2 - **NF-16**: "What changes if I remove this?" debloat preview
- [ ] P2 - **NF-17**: Per-app runtime telemetry panel (foreground time / network / wakelock)
- [ ] P2 - **NF-06**: Pro Mode discovery (hero card + comparison)
- [ ] P2 - **NF-14**: JaCoCo coverage badge
- [ ] P2 - **NF-15**: Markdown link checker scope check (`PROJECT_CONTEXT.md`, ROADMAP)
- [ ] P2 - **EI-04**: Permission Inspector "recently installed + sideloaded" lens
- [ ] P2 - **EI-05**: Onboarding final-step "Next steps" tiles
- [ ] P2 - **EI-06**: OEM-protected chip on Debloater confirmation rows
- [ ] P2 - **EI-07**: Scheduled-backup "Why did the last run skip?" bottom sheet
- [ ] P2 - **EI-09**: Per-app rollback dry-run preview
- [ ] P2 - **EI-10**: Onboarding USB-debugging preflight re-check on resume

### Phase 4 — Engineering polish (background)

- [ ] P3 - **NF-18**: Keystore-password `char[]` lifecycle JUnit invariant
- [ ] P3 - **EI-08**: Mode Doctor share-as-Support-Info-Bundle
- [ ] P3 - Architecture docs `04-filter-finder.md`, `05-routine-scheduler.md`,
        `docs/policy/permissions.md`
- [ ] P3 - Extract `filters/` to a Gradle module (after NF-08 schema lands)

---

## Quick Wins

These are <1-day items mostly captured above; reproduced for reference.

- EI-01: README v0.5.0 line refresh (bundles with NF-01)
- EI-02: CLAUDE.md Status pointer-to-PROJECT_CONTEXT.md rewrite
- EI-06: OEM-protected chip on Debloater rows
- EI-07: Scheduled-backup skip-reason bottom-sheet
- EI-10: Onboarding `adb_enabled` re-check `onResume`
- NF-06: Pro Mode hero card on Onboarding final step + About comparison
- NF-13: JADX 1.4.7 → 1.5.5 bump
- NF-14: JaCoCo coverage badge wiring
- NF-15: Markdown link-checker scope check
- NF-18: Keystore `char[]` invariant test

---

## Larger Bets

- **NF-08 Multi-Tag per App + Saved Filter Presets**: Room schema migration, multi-table
  join, cross-cutting filter integration. L. Target v0.6.0.
- **NF-09 Routine Operations / Scheduler**: WorkManager-orchestrated event triggers +
  schedule history + UI. L. Target v0.6.0 — pairs naturally with NF-08 (tag-targeted
  schedules).
- **NF-10 Premium Polish Phase 2**: AppDetails / AppUsage / Settings v2 layouts behind
  the existing toggle. L. Target v0.5.x → v0.6.x flip.
- **Upstream pulls** when shipped: APK editing (T12), Database viewer (T13), Advanced
  terminal (T14), per-app overlay management (T18), `app-manager://` deep link parity
  (T18). Watch monthly via `upstream-rename-watch.yml` follow-on.
- **Watch / Wear OS surface** (T18 Watch theme): Listed in
  `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` §4 as "first FOSS competitor opportunity"
  vs. closed-source GeminiMan. Out of v0.5.0 / v0.6.0 scope; revisit after the v0.6.x
  rootless-power theme lands.

---

## Explicit Non-Goals (this plan)

- **Re-running external competitor mining.** Iter-18 → iter-141 already mined the field.
  Re-doing it duplicates 30+ hours of work.
- **Compose migration.** `codexprompt.md` calls it a multi-year project; not in scope.
- **Native SMB / WebDAV client implementation.** ROADMAP iter-99/100 settled on the SAF
  DocumentsProvider delegation; honour that decision.
- **In-app store reimplementation.** Obtainium owns this lane; deep-link out.
- **Implementing Frida / LeakCanary / Perfetto integration.** ROADMAP T20 lists these but
  every one is **High** effort with stability risk; defer until at least v0.7.x.
- **AGP 10 migration in v0.5.0.** AGP 9.2 just landed in iter-137; AGP 10 GA is late-2026.
  Re-evaluate Q3.
- **Bumping minSdk to 23.** Would unblock Material 1.14.0 + activity 1.12 + biometric
  1.4.0-alpha05 + room 2.8 + webkit 1.15, but trades off API-21/22 device coverage.
  Decision is mature and gated — out of this plan's scope.
- **Adding Frida / LSPatch / Xposed plugin loading.** Architectural risk, GPL chain
  concerns, and stability risk are well-documented in ROADMAP UC table. Out of scope.

---

## Open Questions (only those that block prioritisation)

1. **Branch state**: `main` is `ahead 177 / behind 177` from `origin/main` on this
   checkout. Are these the same commits with different SHAs (rebase / force-push from
   another host), or genuinely parallel? Either way, NF-01 (release cut) cannot proceed
   until reconciled. **Verify on origin / on the canonical workstation before pushing.**
2. **Component Rules editor (uncommitted)**: Is this the start of the "Blocker-Style
   IFW Rule Editor UI" row called out in iter-141 CONTINUE_FROM_HERE.md, or scaffolding
   for something else? The row sits at T9 and was the next visible Next item. **Confirm
   before commit / revert.**
3. **JADX 1.5.5 fork status**: Does the MuntashirAkon android-jadx fork have a 1.5.5 tag
   yet? If not, NF-13 needs an upstream PR or a temporary pin to the latest available
   commit. **Check `MuntashirAkon/jadx-android` tags before scheduling NF-13.**
4. **v0.5.0 cadence**: Cut now (Phase 0) and treat the v0.5.x line as a continuous
   polish train, or hold for NF-02/NF-03/NF-04 to land first and cut v0.5.0 with the
   full Discovery theme? **Recommendation: cut now as v0.5.0-rc1, treat Discovery as
   v0.5.1.** This avoids gating the existing shipped work on the new feature train.

---

## Verification recap (what this plan is grounded in)

- ROADMAP.md (1848 lines, 363 sources, T1–T21 + Eng-Debt + Premium Polish + Iter-18 →
  Iter-141 deltas) — **read in full**.
- CHANGELOG.md (1890 lines) — **head + structure verified**; spot-checks across
  `Unreleased` confirm iter-91 → iter-141 entries.
- README.md, CLAUDE.md, AGENTS.md, PROJECT_CONTEXT.md, CONTRIBUTING.md, BUILDING.rst,
  COPYING — **read in full or scanned for drift**.
- `app/src/main/java/.../{onboarding,intercept,fm,backup,settings,details,rules,
  main}/*.java` — **structural read for size, TODOs, and surface count**.
- `app/src/main/AndroidManifest.xml` — **scanned for declared permissions + activity
  exports**.
- Working-tree diff (`git status -sb` + `git diff HEAD`) — **uncommitted Component Rules
  editor inspected**.
- `.ai/research/2026-05-17/{STATE_OF_REPO,COMPETITOR_MATRIX,FEATURE_BACKLOG,
  PRIORITIZATION_MATRIX}.md` and `.ai/research/2026-05-18-iter-141/CONTINUE_FROM_HERE.md`
  — **read for the prior session's deltas and continuation points**.
- `design/audit/4-painpoints.md` + `design/plan/3-rollout.md` — **read for Premium Polish
  Phase 2 surface scope**.
- `docs/audits/`, `docs/architecture/`, `docs/policy/minsdk-21-ceiling.md`,
  `docs/distribution/` — **directory listings + selective reads**.
- Test inventory: `find app/src/test/java -name "*.java" | wc -l` = **125**;
  `find app/src/androidTest/java -name "*.java" | wc -l` = **2**.
- Recent git log: 50 commits from `bd11078` (iter-141, 2026-05-18) back to iter-87 —
  **read inline**.

Date written: **2026-05-25**. Author: autonomous research pass.
