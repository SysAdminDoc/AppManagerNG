# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added — Main List & Item Layout: v2 Design System Integration (v0.5.x surface migration phase 1)
- New `activity_main_v2.xml` and `item_main_v2.xml` wired behind the `PREF_PREMIUM_PREVIEW_BOOL` 
  toggle. When enabled, MainActivity and MainRecyclerAdapter load v2 layouts with refined v2 token 
  palette (calmer surfaces, tighter typography, pill-shaped search, outlined card variants). 
  Layout switching is conditional per-view, allowing zero-impact on classic theme users. 
  Completes v0.5.x phase 1 (top-5 surface migration). Next phases: AppDetails, AppUsage, Settings.

### Added — Troubleshooting: auto-fix battery optimization via root/ADB (SD-Maid parity, ROADMAP iter-18 T20)
- Settings → Troubleshooting → "Battery optimization" now auto-applies the
  exemption when NG has root or ADB privileges (checks `DEVICE_POWER`). If
  permitted: silently grants the whitelist in background, updates summary,
  and shows a confirmation toast. If not: falls back to the system dialog.
  Matches SD-Maid's "auto-fix" UX pattern. No user setup needed.

### Added — App Details: copyable error dialog helper (UAD parity, ROADMAP iter-18 T4)
- New `UIUtils.displayCopyableErrorDialog(context, title, message)` shows a
  Material alert with OK + **Copy** buttons. Copy invokes `ClipboardUtils`
  (which already handles >1MB error blobs via FileProvider URI fallback)
  so users can paste failure detail straight into a bug report instead
  of screenshotting + transcribing. Foundation only; high-traffic toast
  failure sites migrate in a follow-up commit.

### Changed — App Info / AppOps / Permissions: descriptions now selectable (UAD parity, ROADMAP iter-18 T10)
- `item_app_details_appop.xml` and `item_app_details_perm.xml` now mark
  `perm_description`, `perm_protection_level`, `op_mode_running_duration`
  and `op_accept_reject_time` as `textIsSelectable="true"`. Long-press to
  copy permission/op descriptions and runtime metadata directly from the
  list — matches Universal Android Debloater's selectable-description
  affordance.

### Added — Appearance: Preview new design (BETA) toggle (premium polish v0.4.x foundation)
- New Settings → Appearance → "Preview new design (BETA)" switch
  (default OFF, key `PREF_PREMIUM_PREVIEW_BOOL`). When enabled the
  app inflates the v2 design system: a refined teal-leaning palette
  with crisper contrast tiers, tightened typography (no letter-
  spacing hangs on titles), pill-shaped FABs and search surfaces,
  and outlined card variants that respect the layered surface
  hierarchy. Pure-black mode routes to `AppTheme.V2.Amoled` so the
  premium look composes with the existing AMOLED preference.
  Layouts and widget IDs are intentionally untouched in this
  release; only the theme/token plane changes. Restart applies.
  Resources copied verbatim from `design/impl/values/{themes,colors,
  dimens}-v2.xml`; rollout plan: `design/plan/3-rollout.md`.

### Added — Backup: Android 14+ "Keep device awake" warning toast (ROADMAP iter-18 item closed)
- When a backup operation begins on Android 14+ (`SDK_INT >=
  UPSIDE_DOWN_CAKE`), NG now displays a long Toast asking the user
  to keep the device awake and AppManager open until the backup
  finishes. Mitigates Android 14's tightened JobScheduler quotas
  and aggressive Doze kills of long-running foreground services.
  Mirrors Neo Backup 8.3.17 behavior. Source: ROADMAP S135.

### Audit — App list / Finder search history (ROADMAP iter-18 item closed)
- Audited persistent search-term storage per Inure build107.0.1
  privacy posture. NG's `SearchView` usage is already session-only
  in memory — `recent_search`, `searchHistory`,
  `SearchRecentSuggestionsProvider` grep all return zero hits.
  No persistent storage exists; no remediation needed. Source:
  ROADMAP S131.

### Added — App Info: Device page size row (ROADMAP iter-18 item closed)
- New "Device page size" row in App Info under Primary ABI for any
  app with native code. Populated via `Os.sysconf(_SC_PAGESIZE)` and
  rendered as "4 KB", "16 KB (page-size compatibility required)",
  raw bytes for any other value, or "Unknown" if the syscall throws.
  Pairs with the per-lib 16KB-alignment indicator (iter-11) so
  16k-incompatible libs visibly explain the warning instead of
  looking spurious on 4k devices. Source: Termux v0.118.3
  page-size detection (ROADMAP S126).

### Docs — ROADMAP iter-18 research (no code change)
- Research-only iteration. 29 new candidate items added under a new
  "Iter-18 Research Additions" section in `ROADMAP.md`, drawn from
  Shizuku v13.6.0, Magisk v30.7, KernelSU v3.2, Termux v0.118.3,
  Apktool v3.0.2, JADX v1.5.5, APKEditor v1.4.7-8, Hail v1.10.0,
  Inure build107, Material Files v1.7.4, SD Maid SE v1.7.2-rc0,
  UAD-NG v1.2.0, Neo Backup 8.3.17, androidx.glance, and Android
  16/17 platform docs. Highlights: Shizuku trusted-WLAN auto-start
  banner, Magisk `--drop-cap` opt-in semantics surface, KernelSU
  sulog/seccomp parity, Android 16 `SDK_INT_FULL` plumbing audit,
  JobScheduler quota stop-reason surfacing, APKEditor smali
  comment-level "basic", Hail-style auto-freeze QS tile,
  Inure-style AppOps IGNORE flag, UAD-style cross-user package
  state detection, Neo-Backup-style backup sharing button.
  Sources S121–S136 appended to the appendix; baseline line bumped
  with iter-18 summary.

### Added — Settings: Battery optimization entry (ROADMAP Trivial closed)
- New "Battery optimization" preference under Settings → Troubleshooting.
  Summary reflects the current `PowerManager.isIgnoringBatteryOptimizations()`
  state and refreshes on resume. Tap routes to the per-app request prompt
  (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) when optimized, or to the
  system-wide list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) when
  already exempt so the user can revoke. Pre-M devices see a disabled
  entry with explanatory copy. Manifest now declares
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Added — AppType filter: Play App Signing + Overlay flags (eng-debt TODO partially closed)
- The AppType filter (used by Saved Filters and Finder) gains two
  previously-stubbed flags: **Uses Play App Signing** (APK signed by
  Google rather than the developer's release key) and **Overlay app**
  (Resource Runtime Overlay declaring an `<overlay>` manifest tag).
  Both flags work in `with_flags` and `without_flags` modes.
- New `IFilterableAppInfo.usesPlayAppSigning()` / `isOverlay()` methods
  implemented on both `FilterableAppInfo` (eager via
  `PackageUtils.usesPlayAppSigning` and `PackageInfoCompat2.getOverlayTarget`)
  and `ApplicationItem` (lazy via `fetchPackageInfo()`). PWA and
  short-code remain on the TODO list pending a stable detection signal —
  TWA detection requires manifest service-tag sniffing and short-code
  isn't exposed by `PackageManager`.

### Added — Code Editor: language / tab-size / go-to-line pickers (ROADMAP T14 ×3 closed)
- **Language toolbar button** now opens a popup listing all seven
  tmLanguage-backed languages bundled in `assets/languages/`
  (java / json / kotlin / properties / sh / smali / xml). Picking switches
  the syntax highlighter (`mEditor.setEditorLanguage`) and persists via
  the new `CodeEditorViewModel.setLanguage()` setter; the indent-mode
  label re-renders against the chosen language's `useTab/useSpace`
  default.
- **Tab-size toolbar button** opens a 2 / 4 / 8 popup wired to
  `mEditor.setTabWidth(n)` so the user can override the language default
  for files like Makefiles that need real tabs.
- **Position toolbar button** now opens a "Go to line" dialog
  (`TextInputDialogBuilder` numeric input) and moves the cursor via
  `mEditor.setSelection(line - 1, 0)`; out-of-range input clamps to
  `[1, lineCount]`.
- The hardcoded "tabs"/"spaces" suffix on the indent label now reads
  from `R.plurals.editor_tab_size_option_{tabs,spaces}` so non-English
  locales render the correct plural form. Closes the four `CodeEditorFragment.java`
  TODOs (`13/9/22 Display all the supported languages`, `13/9/22 Enable
  setting custom tab size`, `13/9/22 Enable going to custom places`,
  `13/9/22 Use localization`).

### Changed — Plural string audit (ROADMAP T10 closed)
- Three remaining pluralizable count strings converted to `<plurals>`:
  `main_status_showing_apps` ("Showing N of M apps"),
  `main_status_all_apps` ("Showing N apps"), and
  `bar_chart_content_description` ("Bar chart with N data points").
  Callers in `MainActivity.updateListStatus` and
  `BarChartView.updateContentDescription` now use
  `getQuantityString()` so locales whose plural form differs by count
  (Russian / Polish / Arabic / etc.) render correctly. Orphan
  `selected_items_accessibility_description` (no callers) removed.
- The remaining `%d`-using strings in `values/strings.xml` describe
  IDs, positions, range bounds, or "X of Y" composites — none are
  pluralizable, so the audit is closed.

### Added — Share profile as JSON (ROADMAP T8 closed)
- New **Share as JSON** popup action on each profile in the Profiles
  list (`action_share` between Export and Shortcut). Sends the profile's
  pretty-printed JSON via `Intent.ACTION_SEND` — Telegram / KDE Connect
  / email / Gmail draft / Slack pick it up directly, no SAF round-trip
  required. The wire format is identical to what `Export` writes, so
  the receiving NG instance can re-import via the existing Import
  action verbatim. File-export remains for share targets that need an
  attachment.
- The companion file-roundtrip Import + Export paths were already wired
  in `ProfilesActivity` (`ActivityResultContracts.GetContent` /
  `CreateDocument("application/json")`); ROADMAP row was stale, now
  closed.

### Added — Signing-cert SHA-256 chip in App Info (ROADMAP T18 closed)
- New "Sign · SHA-256 21:5F…38:6C" chip in the App Info tag cloud
  surfaces the colon-separated, upper-case SHA-256 fingerprint of the
  current signing certificate. Tap opens a Material dialog showing the
  full digest with a one-tap **Copy** button so users can paste the
  fingerprint directly into AppVerifier or compare against
  `apksigner verify --print-certs` output without leaving NG. Single-
  signer APKs only — multi-signer cases stay routed through the existing
  icon-tap verify-from-clipboard flow.
- Backed by `AppInfoViewModel.computeSigningCertSha256()` (worker-side
  via `PackageUtils.getSignerInfo` + `DigestUtils.SHA_256`); result is
  cached on `TagCloud.signingCertSha256`.

### Changed — VirusTotal poll-wait scales with upload size (engineering-debt TODO closed)
- `VirusTotal.fetchFileReportOrScan` now scales the *first* poll wait
  by file size via the new `computeInitialPollWait(fileSize)` helper —
  roughly +1 s per MB above a 10 MB threshold, clamped to [60 s, 240 s].
  Avoids burning the 4 req/min free-API rate-limit quota on a large
  upload that hasn't finished engine processing yet. Subsequent polls
  remain at the 30 s rate-limit floor. Closes the inline TODO at
  `VirusTotal.java` (originally filed 2022-05-23) and the matching
  Engineering Debt Register row.

### Added — Root manager detection: Magisk / KernelSU / APatch / ZygiskNext (ROADMAP T5 ×3 closed)
- New `runner/RootManagerInfo` helper probes `/data/adb/{magisk,ksu,ap}` via
  the privileged shell when root is granted, and falls back to a
  `PackageManager` lookup of the manager apps
  (`com.topjohnwu.magisk`, `me.weishu.kernelsu`, `com.rifsxd.ksunext`,
  `me.bmax.apatch`) when it isn't. Whenever a non-NONE manager is detected
  through the shell, a follow-up `[ -d /data/adb/modules/zygisksu ]`
  identifies the ZygiskNext layer.
- Onboarding sheet (`OnboardingFragment.refreshCapabilityStatuses`) now
  appends the resolved manager name (and " + ZygiskNext" if applicable)
  to the Root status line — e.g. "Detected · KernelSU + ZygiskNext". The
  probe runs on a background thread (one shell round-trip), result is
  posted back to the main thread, and the suffix update is idempotent so
  the Re-check button can be tapped repeatedly without stacking suffixes.
- Closes ROADMAP T5 rows: KernelSU Detection, APatch Detection,
  ZygiskNext Detection. SuperKey / per-module-count surfacing for APatch
  and ZygiskNext error-count surfacing remain on the Privilege
  Health-Check Screen row.

### Added — Stable signing-cert fingerprint URL (ROADMAP T1 closed)
- New [`docs/fingerprints.txt`](docs/fingerprints.txt) publishes the SHA-256
  signing-cert fingerprint in a comment-tolerant `package:` / `sha256:`
  record format (SD Maid SE precedent), served via the stable
  `raw.githubusercontent.com/.../docs/fingerprints.txt` URL — AppVerifier
  and similar tooling can fetch it programmatically without scraping the
  README. README "Verifying releases" section now points users at the URL.

### Added — Android 17 ProfilingManager OOM/anomaly triggers (ROADMAP T4 closed)
- New `misc/ProfilingTriggerHelper.registerTriggersIfSupported(Context)`
  registers `TRIGGER_TYPE_OOM` and `TRIGGER_TYPE_ANOMALY` via reflection on
  API 37+ devices, so the system auto-captures heap profiles when
  AppManagerNG hits low-memory or anomaly conditions during JADX decompile
  or APK parsing. Silent no-op on anything below API 37 and on any
  reflective lookup failure (compileSdk is still 36 so the profiling
  classes are not present at build time).
- Wired from `AppManager.onCreate()` once per process. The harvest +
  diagnostic-ZIP-attach side of the workflow is deferred until API 37 is
  available on a real device for end-to-end test.

### Compliance — Android 17 per-app Keystore key-cap audit (clean; ROADMAP T2 closed)
- Audit confirms NG can never exceed Android 17's 50,000-key per-app
  `AndroidKeyStore` cap: it generates at most **two** static, idempotently
  guarded aliases (`aes_local_protection` on API ≥ M, plus a legacy
  `rsa_wrap_local_protection` on pre-M devices) — both in
  `CompatUtil.getAesGcmLocalProtectionKey()` behind `containsAlias`
  checks. All backup-crypto paths route through a file-backed BKS
  keystore (`am_keystore.bks` via `KeyStoreManager`) which is outside
  the platform-managed Keystore. No remediation needed; roadmap row
  closed. Audit at
  [docs/audits/2026-05-02-android17-keystore-key-cap.md](docs/audits/2026-05-02-android17-keystore-key-cap.md).

### Changed — Pre-emptive Android 18 share-intent compliance (ROADMAP T3 closed)
- All seven outgoing `ACTION_SEND` / `ACTION_SEND_MULTIPLE` paths that carry
  a content URI (App Info APK share, log viewer attachment chooser, code
  editor share, single + multi file-manager share, diagnostic export, crash
  report) now set both `FLAG_GRANT_READ_URI_PERMISSION` and an explicit
  `ClipData` so the chooser target keeps receiving read access once
  Android 18 removes the implicit auto-grant for SEND/SEND_MULTIPLE/
  IMAGE_CAPTURE. Multi-URI shares from the file manager now build a
  multi-item `ClipData` rather than relying on the EXTRA_STREAM list alone.
- Audit and full inventory at
  [docs/audits/2026-05-02-android18-implicit-uri-grant.md](docs/audits/2026-05-02-android18-implicit-uri-grant.md).
  No `IMAGE_CAPTURE` callers in source; `PackageInstaller` install path
  streams via `openWrite()` and is unaffected.

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
