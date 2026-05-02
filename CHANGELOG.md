# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Changed — Utils.java flag-string i18n (ROADMAP T3 closed)
- `Utils.getSoftInputString`, `getServiceFlagsString`,
  `getActivitiesFlagsString`, and `getInputFeaturesString` now read their
  flag labels from `strings.xml` (`soft_input_flag_*`, `service_flag_*`,
  `activity_flag_*`, `input_feature_*`) via `ContextUtils.getContext()`,
  so the App Details Activities / Services / Other tabs respect the
  device locale instead of hardcoded English.
- `Utils.getProtectionLevelString` keeps Android's canonical manifest
  `android:protectionLevel="..."` tokens (`dangerous`, `signature`,
  `signature|privileged`, etc.) untranslated by design — they are
  technical identifiers, and `AppDetailsPermissionsFragment` does a
  `protectionLevel.contains("dangerous")` check that must keep working.
  Replaced the stale `FIXME` with a comment documenting the rationale.

### Added — Settings: Mode-of-Ops live capability refresh
- Capability badges (Root / Wireless ADB / USB ADB) now refresh every time
  the Mode-of-Ops settings screen resumes. Toggling Wireless debugging in
  Quick Settings or granting root in another app while Settings is in the
  background now reflects on return — no need to leave the screen entirely.

### Added — Android TV launcher parity (audit)
- Confirmed `SplashActivity` already declares `LEANBACK_LAUNCHER`, the
  manifest declares leanback `uses-feature` with `required="false"` and
  optional touchscreen, and the `ic_banner` mipmap is wired. AppManagerNG
  appears on Android TV / Google TV launchers with no additional work.
  ROADMAP item closed.

### Added — App Info bloatware safety rating
- **Bloatware tag now surfaces the safety call directly** — App Info →
  tag cloud previously showed a generic "Bloatware" chip coloured by
  removal type. Tag text now reads "Bloatware · Safe", "Bloatware ·
  Replace", "Bloatware · Caution", or "Bloatware · Unsafe", so users can
  read the recommendation without tapping into the details dialog.
  Colour is preserved (`ColorCodes.getBloatwareIndicatorColor`).

### Added — Onboarding replay (v0.4.0)
- **Replay welcome wizard** action in Settings → Troubleshooting; clears
  `PREF_ONBOARDING_SHOWN_BOOL` and immediately surfaces the privilege-mode
  picker (Auto / Root / Wireless ADB / ADB-TCP / No-root) so power users
  and testers can revisit the explainers without a fresh install. The
  picker writes the flag back on pick/cancel, so the flow self-heals.
- **Replay quick tour** action in Settings → Troubleshooting; clears
  `PREF_MAIN_TOUR_SHOWN_BOOL` so the main-list tour re-arms on the next
  launch. Toast confirms the reset.
- **Active-mode highlight in onboarding** — when the wizard opens (first
  run or replay), the card matching the currently saved mode is ringed
  with a 2dp `colorPrimary` stroke, so users replaying see at a glance
  which mode is in effect. A11y description is prefixed with "Currently
  active." for screen-reader parity.
- **Pick-Root-without-detection guardrail** — when a user taps the Root
  card and `Ops.hasRoot()` returns false, a confirmation dialog explains
  the situation (root managers can hide su until first request, but most
  ops will fail until granted) and lets them cancel without burning the
  onboarding-shown flag.
- **Re-check capabilities button** in the onboarding sheet — refreshes
  the Root / Wireless-ADB / USB-ADB badges and the active-mode highlight
  in place without dismissing the sheet, so users who toggle Wireless
  debugging from quick-settings or grant root from another app can see
  the new state immediately. Snackbar confirms the refresh.

### Added — Premium facelift design system (foundation)
- **`design/` deliverable folder** (audit, spec, impl, plan, README) — full
  v2 design system reference: palette, typography, 4dp spacing ladder,
  elevation tokens, motion vocabulary, iconography choice, and 5 drop-in
  reference XML files (themes-v2, colors-v2, dimens-v2, item_main_v2,
  activity_main_v2). Read order: [design/README.md](design/README.md).
- **Pain-point inventory** ([design/audit/4-painpoints.md](design/audit/4-painpoints.md))
  catalogues 16 dated surfaces with concrete fix proposals and benchmark
  references (Linear, Arc, Things 3, 1Password 8, Obsidian).
- **4-release rollout plan** ([design/plan/3-rollout.md](design/plan/3-rollout.md))
  for shipping the facelift behind a Pro Mode "Preview new design" toggle
  (v0.4.x foundation → v0.5.x top-5 migration → v0.6.x long tail → v0.7.x
  toggle removal).

### Added — Main list polish (preview)
- **Semantic threshold tinting** for the tracker indicator (green ≤4,
  amber 5-19, red ≥20) and the dangerous-permission badge (success when
  zero granted, warning below 50%, danger 50%+).
- **Risk-tinted package name** on app rows when a heuristic combination
  of granted-perms + tracker count crosses a danger threshold.
- **Restyled main status banner** with stronger metric typography,
  clearer "filtered N of M" affordance, and tonal background.
- **Refined empty state** (`view_main_empty_state.xml`) with explicit
  active-filter description and reset-filter affordance.
- New `ColorCodes` semantic colors and `Widget.AppTheme.Chip.MainFilter` /
  `Widget.AppTheme.Chip.MainSuggestion` chip styles.

### Added — Settings & onboarding polish
- Tighter `m3_preference*.xml` row treatments (preference category
  indicator, dual-pane divider, focused-pane state).
- Onboarding fragment redesign: tonally-tinted mode-of-operation cards,
  status background drawables, plain-language privilege explainer.
- Reorganised `preferences_main.xml` to match the new tier hierarchy.

### Added — Operation Activity Log (ROADMAP T8 closed)
- Persistent journal of every operation AppManagerNG performs (freeze,
  backup, batch, install, profile execution). Per-entry metadata: target
  app(s), operation type, timestamp, mode (root/ADB/Shizuku/no-root),
  risk tier, success/failure, scope.
- `OpHistoryActivity` reachable from main overflow → "History" with
  aggregate summary, package/operation/mode/target search, success/risk
  filter chips, risk-tinted card borders, FAB.
- New helpers: `OperationJournalMetadata`, `OperationPreflight`,
  `OperationHistoryExporter` (with Robolectric test). Per-entry copy/
  delete actions; rerun preflight gates dangerous reruns.
- Debug-only "Add sample entries" menu action under `BuildConfig.DEBUG`
  for development verification — never visible in release builds.

### ROADMAP additions (research-driven)
- New T19 tier: **Package-Aware Storage Analysis** (App Details Storage
  Panel, Leftover Detection After Uninstall, APK Duplicate Finder,
  Backup Duplicate Cleaner — SD Maid SE / UAD-NG models).
- T2 row: **Android 17 Keystore Per-App Key Cap** (50,000-key audit
  before targetSdk=37 bump).
- T3 row: **Android 18 Implicit URI Grant Removal** (preemptive
  `grantUriPermission()` audit before Android 18 ships).
- T7 rows: **Finder Relevance-Based Search Scoring** (Levenshtein) and
  **Finder Description-Field Search** (debloat-list metadata).
- T8 row: **Multi-Tag per App** (Hail v1.10.0 model, many-to-many join
  table on the existing Room schema).
- T9 rows: **Permission Policy Flags Display** + **MiUI-Specific AppOps
  Mapping** (Inure 106.5.0 model).
- T12 rows: **Native Library Sizes in App Details** + **Batch APK
  Installer from File Manager** (Inure 107.0.0/.1 model).
- New sources S65–S68 logged. Full research at
  [docs/research/2026-05-02-android-power-tools.md](docs/research/2026-05-02-android-power-tools.md).

### Compliance
- **Android 17 `MessageQueue` audit (clean)**: lock-free `MessageQueue`
  shipping in Android 17 / targetSdk=37 crashes apps that reach into
  private fields via reflection. Recursive sweep across all source roots
  returned zero matches; root-shell IPC routes through libsu shell
  processes, not `MessageQueue` reflection. Audit at
  [docs/audits/2026-05-02-android17-messagequeue.md](docs/audits/2026-05-02-android17-messagequeue.md).
- **Adaptive Layout for Large Screens audit (clean)**: Android 16 /
  targetSdk=36 ignores `screenOrientation`, `resizeableActivity=false`,
  and aspect-ratio limits on ≥ 600dp displays. Manifest sweep across 43
  activities returned zero fixed-orientation declarations, zero resize
  blockers, zero aspect-ratio limits. Audit at
  [docs/audits/2026-05-02-adaptive-layout.md](docs/audits/2026-05-02-adaptive-layout.md).

### Added
- **Tablet density overrides** (`app/src/main/res/values-w600dp/dimens.xml`,
  `libcore/ui/src/main/res/values-w600dp/dimens.xml`): bumps icon sizes,
  list-row min-height, font sizes, and medium/large/very-large padding
  tiers when the available width is ≥ 600dp (tablets, foldables in
  landscape, Chromebooks, free-form windowed mode). Phone-sized devices
  read the existing `values/dimens.xml` unchanged. No per-layout edits
  required — the new values propagate to every layout already consuming
  these tokens.
- **Sort by Dangerous Permissions**: new `SORT_BY_DANGEROUS_PERMS` option in
  the main app list (Sort menu). Mirrors the `SORT_BY_TRACKERS` shape —
  primary key is granted dangerous perms (most-privileged-by-actual-grant
  apps surface first); secondary key is total declared dangerous perms.
  Wires `dangerous_perm_total` / `dangerous_perm_granted` (Room schema v9)
  into the user-facing UI.
- **Obtainium config** (`docs/distribution/obtainium-config.json`):
  ready-to-import Obtainium AppConfig pointing at GitHub Releases with
  artifact regex for the signed `AppManagerNG-<version>-{arm64-v8a,universal}.apk`
  files (auto-ABI selection enabled). README "Install" section adds an
  "Install via Obtainium" subsection with paste-and-go instructions plus
  an AppVerifier pairing tip.

### Compliance
- **`elegantTextHeight` audit (clean)**: Android 16 / targetSdk=36 silently
  ignores `android:elegantTextHeight`; affects Arabic/Thai/Indic text
  rendering. Recursive sweep across all source roots returned zero
  matches — no remediation required. Audit recorded at
  [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md).

## v0.3.0 — 2026-06-05

Platform compliance, bug fixes, and observability hardening.

### Fixed
- **BarChartView accessibility** (`usage/BarChartView.java`): replaced deprecated
  `announceForAccessibility()` (ignored by TalkBack on Android 16+) with
  `ViewCompat.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE)`. The 3
  redundant announcement calls alongside virtual-view events were removed; 1 was replaced
  with `updateOverallContentDescription()` so the live region fires on data change.
- **KeyStoreUtils secure memory** (`crypto/ks/KeyStoreUtils.java`): `StringBuilder`
  (non-zeroable) replaced with `CharArrayWriter`; key material byte arrays explicitly zeroed
  after `generatePrivate()` to reduce key-in-memory exposure window.
- **ABX editor** (`editor/CodeEditorViewModel.java`): Android Binary XML files can now be
  opened for inspection in the code editor. Write-back is blocked via `canWrite() = false`
  when `mXmlType == XML_TYPE_ABX` to prevent lossy typed-value → string round-trip.
- **ActivityInterceptor `ACTION_OPEN_DOCUMENT`** (`intercept/ActivityInterceptor.java`):
  `FLAG_ACTIVITY_NEW_TASK` was being added to the document-picker intent, which broke result
  delivery (Android bug: new-task flag + `startActivityForResult` never delivers result).
  Flag is now stripped with `removeFlags()` before launching the picker.

### Added
- **Crash log persistence** (`misc/AMExceptionHandler.java`): crashes are written to
  `getFilesDir()/crashes/crash_TIMESTAMP.log` (capped at 10 files). The crash share
  notification now attaches the log file as a `content://` URI via FmProvider. Upstream
  hardcoded email removed; subject updated to "AppManager NG: Crash Report".
- **Diagnostic export** (`misc/DiagnosticUtils.java` + Settings → About): new "Export
  Diagnostic Report" preference bundles device info, all crash logs, and the last 2 000
  logcat lines (main/system/crash buffers) into a ZIP file and opens the share chooser.
- **CodeQL on main** (`.github/workflows/codeql.yml`): analysis now triggers on pushes to
  `main` (was limited to `master`); `workflow_dispatch` added for on-demand scans.

### Deferred
- `Utils.java` flag-string i18n (5 methods × ~50 string resources) — deferred to v0.4.0.
  Caller Context injection required before extracting strings.

Identity milestone: AppManagerNG now has its own install identity, signing key, and release
pipeline, fully separated from the upstream package.

### Added
- **`applicationId` rename**: install identity changed from `io.github.muntashirakon.AppManager`
  to `io.github.sysadmindoc.AppManagerNG`. Source namespace kept at
  `io.github.muntashirakon.AppManager` (full namespace rename is future work).
- **New release keystore**: `AppManagerNG-release.jks` — 4096-bit RSA, 10,000-day validity.
  SHA-256: `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- **GitHub Actions release pipeline** (`.github/workflows/release.yml`): tag push → build →
  sign → upload arm64-v8a + universal APKs to GitHub Releases.
- **CONTRIBUTING.md**: NG-specific contribution guidelines (replaces upstream CONTRIBUTING.rst
  reference); covers AI code policy, commit format, upstream sync protocol, translation note.
- **ROADMAP.md**: comprehensive prioritized roadmap through v0.6.0+ (17 themes, 37 sources).
- **AppVerifier fingerprint** in README for release verification.
- **16KB page size compliance**: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` CMake argument
  added to `app/build.gradle` for Android 15+ physical device compatibility.

### Fixed
- `LocalFileOverlay.java`: hardcoded application ID fallback now uses `BuildConfig.APPLICATION_ID`
  instead of a literal string, so it tracks applicationId changes automatically.
- `settings.gradle`: `rootProject.name` updated to `AppManagerNG`.

## v0.1.0 — 2026-04-30

Initial AppManagerNG release. Repo bootstrap from upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(2026-04-16, upstream tag context: post-v4.0.5).

### Added
- AppManagerNG-branded README.md with shields.io badges, GPL-3.0-or-later notice, and upstream credit
- CHANGELOG.md (this file)
- Branding/logo prompts directory (`branding/logo-prompts.md`)

### Changed
- App display name (`app_name` resValue): `App Manager` → `AppManagerNG` (release), `AM Debug` → `AM-NG Debug` (debug)
- Android `versionName`: `4.0.5` → `0.1.0`; `versionCode`: `445` → `1`

### Preserved (unchanged from upstream)
- All Java/Kotlin/Native sources
- Package name (`io.github.muntashirakon.AppManager`) and namespace — rebrand deferred to v0.2.0
- License files: `COPYING`, `LICENSES/` directory (REUSE-compliant), per-file SPDX headers
- Build configuration (Gradle, AGP version, dependencies, signing config)
- Documentation: `BUILDING.rst`, `CONTRIBUTING.rst`, `PRIVACY_POLICY.rst`, `docs/`
- F-Droid metadata (`fastlane/`)
- Submodule pointers (`scripts/android-libraries`, `scripts/android-debloat-list`)

### Roadmap
- **v0.2.0** — applicationId + namespace rename to `io.github.sysadmindoc.AppManagerNG`; fresh keystore
- **v0.3.0** — Material 3 dashboard refresh + Pro-mode toggle for advanced features
- **v0.4.0** — Onboarding flow (root/ADB capability detection + plain-language explainer)
- **v0.5.0** — Settings reorganization + in-app search and help
