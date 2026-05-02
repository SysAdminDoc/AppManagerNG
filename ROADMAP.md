# AppManagerNG — Roadmap

**Status:** Living document — update on every version bump.  
**Baseline:** v0.1.0, forked from [App Manager](https://github.com/MuntashirAkon/AppManager) @ `3d11bcb` (post-v4.0.5), 2026-04-30.  
**Last updated:** 2026-05-02 (factory iter-10: Dhizuku DeviceOwner privilege path, Force Stop via Shizuku, AppOps quick-toggle in list, Notification.ProgressStyle for backup, Force DEX compile optimization, S71; iter-9: ProfilingManager OOM triggers, graduated tracker blocking modes, per-app tracker stats, permission change monitor, PQC signing pipeline note, DDG Tracker Radar license flag, AlarmManager OnAlarmListener note, VirusTotal poll TODO, S69–S70; iter-8: multi-tag per app, relevance/description search, permission policy flags, native lib sizes, batch APK installer, Android 17 Keystore key cap + Android 18 URI grant preemptive fixes, T19 Package-Aware Storage Analysis, S65–S68; iter-7: Android 17 MessageQueue audit, Adaptive Layout audit, tablet density overrides; iter-6: sort-by-dangerous-perms, elegantTextHeight audit, Obtainium config; baseline post-v0.3.0 fifth full research cycle 2026-05-01).  
**Next revision due:** v0.6.0 release.

**Related research:** [Android power-tool competitive research](docs/research/2026-05-02-android-power-tools.md).

---

## Guiding Principles

AppManagerNG is a "friendlier front door" to App Manager's engine. Three commitments shape every prioritization call:

1. **Progressive disclosure, not feature removal.** Power-user depth stays; it just doesn't ambush newcomers. The Pro Mode toggle is the primary mechanism.
2. **Upstream as reference, not competitor.** Pull fixes and features from upstream aggressively. Diverge only where UX philosophy demands it.
3. **Rootless first.** Shizuku-based operations are first-class citizens alongside root. The majority of Android users have no root; they shouldn't hit a dead end.

Hard constraints:
- License: **GPL-3.0-or-later** throughout. Every new dependency must be compatible.
- minSdk **21** (Android 5.0). Many modern APIs need compat paths or conditional activation.
- No AI-generated code contributions (inheriting upstream policy; NG will publish its own CONTRIBUTING.md in v0.2.0).

---

## Committed Version Targets

| Version | Theme | Key Deliverables |
|---------|-------|-----------------|
| **v0.2.0** ✅ | Identity | `applicationId` + namespace rename → `io.github.sysadmindoc.AppManagerNG`; new release keystore; GitHub Actions release pipeline; NG-specific CONTRIBUTING.md |
| **v0.3.0** ✅ | UX Refresh | Material 3 dashboard; Pro Mode toggle; edge-to-edge (Android 15/16 compliance); AMOLED/dark/light themes |
| **v0.4.0** 🔨 In Progress | Onboarding | Root/Shizuku/ADB capability detection wizard; plain-language privilege explainer; first-run flow |
| **v0.5.0** | Settings & Discovery | Settings reorganization by task; global in-app search; contextual help tooltips; in-app changelog viewer |
| **v0.6.0** | Rootless Power | Shizuku integration; rootless debloat; wireless ADB auto-pairing |

---

## Tier Definitions

| Tier | Meaning |
|------|---------|
| **Now** | Tied to v0.2.0–v0.3.0 or is a critical bug/regression fix |
| **Next** | Target v0.4.0–v0.6.0 horizon; high-value, well-scoped |
| **Later** | Post-v0.6.0; valuable but requires upstream landing or prior work |
| **Under Consideration** | Not rejected — needs architecture decision or external dependency |
| **Rejected** | Explicitly out of scope; reasoning provided |

---

## Now

### T1 — Identity & Distribution

Required before any APK reaches real users. Every item here is blocking F-Droid listing.

| Item | Description | Effort |
|------|-------------|--------|
| ~~**applicationId Rename**~~ ✅ v0.2.0 | Change `io.github.muntashirakon.AppManager` → `io.github.sysadmindoc.AppManagerNG`; update all `BuildConfig` references | Low |
| ~~**New Release Keystore**~~ ✅ v0.2.0 | Generate NG-specific signing key; document SHA-256 fingerprint in README for AppVerifier compatibility | Low |
| ~~**GitHub Actions Release Pipeline**~~ ✅ v0.2.0 | `release.yml`: tag push → build → sign → upload APK to GitHub Releases; parallel arm64-v8a / universal ABIs | Medium |
| **Reproducible Builds** | Match upstream's reproducible build config (added in upstream v4.0.5); CI diff step compares release APK binary hash | Medium |
| **IzzyOnDroid Listing** | Submit after rename; IzzyOnDroid is faster than F-Droid proper and the primary privacy-community distribution channel | Low |
| **F-Droid Listing** | Submit to F-Droid proper after IzzyOnDroid pass; requires REUSE compliance (already in place) | Low–Med |
| ~~**Obtainium Config**~~ ✅ 2026-05-01 | Publish pre-built app config at `apps.obtainium.imranr.dev` so users can track NG updates directly. **Shipped at [docs/distribution/obtainium-config.json](docs/distribution/obtainium-config.json) with README "Install via Obtainium" section. Submission to apps.obtainium.imranr.dev is a separate manual PR step (out-of-band).** | Trivial |
| **Accrescent Listing** | Submit to Accrescent store after reproducible builds land; provides key-pinned first-install verification and no-account downloads for privacy-conscious users ([S62]). Requires developer console account + meeting reproducible build requirement (N2 dependency). | Low–Med |
| ~~**NG-Specific CONTRIBUTING.md**~~ ✅ v0.2.0 | Replace upstream's CONTRIBUTING.rst; define AI code policy, commit format, PR expectations, upstream sync protocol | Low |
| ~~**AppVerifier Fingerprint**~~ ✅ v0.2.0 | Add signing certificate SHA-256 to README (model: SAI, Obtainium, Canta all do this) | Trivial |

### T2 — Platform Compliance (Android 15/16)

Unaddressed items here will become regressions when targetSdk=36 is enforced on Google Play and will break on Android 16 devices in the field.

| Item | Description | Effort |
|------|-------------|--------|
| ~~**Edge-to-Edge Enforcement**~~ ✅ v0.3.0 | Remove `windowOptOutEdgeToEdgeEnforcement` (removed in Android 16 targetSdk=36); handle all window insets across every screen | Medium |
| ~~**Predictive Back (Android 16)**~~ ✅ v0.3.0 | `enableOnBackInvokedCallback = true` is now the default for targetSdk=36; audit and migrate all `onBackPressed` / `KEYCODE_BACK` consumers | Medium |
| ~~**Themed App Icons**~~ ✅ v0.3.0 | Add monochrome adaptive icon variant (Android 16 auto-applies themed icons; supply the vector to control the output) | Low |
| ~~**16KB Page Size Compliance**~~ ✅ v0.3.0 | Recompile all NDK `.so` libraries with `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and verify linker 16KB alignment; required for Android 15+ physical devices | Medium |
| ~~**`announceForAccessibility` Migration**~~ ✅ v0.3.0 | Replace deprecated `announceForAccessibility` calls (deprecated Android 16) with `ViewCompat.performAccessibilityAction` equivalents | Low |
| ~~**Adaptive Layout for Large Screens — Compliance Audit**~~ ✅ 2026-05-02 | Remove any fixed orientation/aspect ratio restrictions; audit for layout breakage on tablets/foldables. Android 16 ignores `screenOrientation` and resizability restrictions for targetSdk=36 apps on displays ≥600dp ([S44]). **Manifest sweep clean: 0 fixed orientations / 0 resizeable=false / 0 aspect-ratio limits across 43 activities. Density bumps shipped via new `values-w600dp/dimens.xml` overrides (app + libcore/ui).** See [docs/audits/2026-05-02-adaptive-layout.md](docs/audits/2026-05-02-adaptive-layout.md). | Medium |
| **Adaptive Layout — Master/Detail UX Iteration** | Follow-up to the compliance audit above. Apply the existing SettingsActivity dual-pane pattern to high-traffic flows (Main → AppDetails, BackupRestoreActivity, OneClickOpsActivity) on ≥ 900dp displays. Substantial UX restructure with motion + state design — gets its own iteration. | Medium-High |
| ~~**`elegantTextHeight` Audit**~~ ✅ 2026-05-01 | Audit all layouts for reliance on `elegantTextHeight` attribute — it is ignored for apps targeting API 36. Affects text rendering in Arabic, Thai, Indic scripts ([S44]). **Result: zero source matches; no remediation required.** See [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md). | Low |
| **WorkManager Quota Testing (Android 16)** ⏸ blocked-by Scheduled Auto-Backup | Backup scheduler (T6) must be tested under Android 16's stricter JobScheduler quota model: quota now applies when app is in top-state or running alongside a foreground service. Use `WorkInfo.getStopReason()` / `JobParameters.getStopReason()` for diagnostics ([S45]). **Pre-flight 2026-05-02: zero WorkManager / androidx.work / JobScheduler references in current source — there is no backup scheduler yet to instrument. Item is parked until ROADMAP T6 'Scheduled Auto-Backup' lands; the diagnostics will be wired in as part of that feature, not as a standalone task.** | Low (after dependency lands) |
| ~~**Android 17 MessageQueue Compatibility**~~ ✅ 2026-05-02 | Android 17 ships a lock-free `MessageQueue` for targetSdk=37 apps ([S55]). NG must audit any direct access to `MessageQueue` private fields (used in some root-shell polling paths); `IllegalReflectiveAccess` will escalate to crashes under this impl. **Audit clean — zero source matches; root-shell IPC uses libsu shell processes, not MessageQueue reflection.** See [docs/audits/2026-05-02-android17-messagequeue.md](docs/audits/2026-05-02-android17-messagequeue.md). Other Android 17 behaviour-change gates remain. | Low |
| **Android 17 Keystore Per-App Key Cap** | Android 17 enforces a 50,000-key limit per non-system app targeting API 37 ([S55]). NG's backup encryption and per-app signing operations may accumulate keys over time; audit `KeyStoreManager` and all backup-crypto paths before the targetSdk=37 bump to confirm key count stays within the cap and any excess is pruned. | Low |

### T3 — Critical Bug Fixes & Security Debt

These are `FIXME` / security issues identified in source that should not wait for a feature release.

| Item | File | Description | Effort |
|------|------|-------------|--------|
| ~~**Keystore Password Security**~~ ✅ v0.3.0 | `KeyStoreUtils.java` | Use `char[]` instead of `String` for keystore passwords (String is interned in heap; not clearable) | Low |
| ~~**ABX-to-XML Lossless Fix**~~ ✅ v0.3.0 | `CodeEditorViewModel.java` | Current ABX→XML conversion is lossy; update serializer to round-trip without data loss | Medium |
| ~~**Intent Interceptor OPEN_DOCUMENT**~~ ✅ v0.3.0 | `ActivityInterceptor.java` | Interceptor permanently breaks `android.intent.action.OPEN_DOCUMENT` (Issue #1767, 2 reactions); fix dispatch logic | Medium |
| **Utils.java i18n (×5)** ⚠️ deferred v0.4.0 | `Utils.java` | Five hardcoded string instances that bypass Android's localization pipeline | Low |
| **Android 18 Implicit URI Grant Removal** | `PackageInstaller` / share intents | Android 17 signals that `Send`, `SendMultiple`, and `ImageCapture` intents will stop auto-granting URI read/write to the target app in Android 18 ([S55]). Pre-emptively audit all APK-sharing and share-via-intent paths; add explicit `grantUriPermission()` calls before breakage ships. | Low |

### T4 — Observability & Process

| Item | Description | Effort |
|------|-------------|--------|
| ~~**Opt-In Crash Reporting**~~ ✅ v0.3.0 | On uncaught exception: write crash log to app-private storage + show "Share crash report" dialog that deep-links to GitHub Issues. Zero network egress without explicit user action. | Low |
| ~~**In-App Diagnostic Dump**~~ ✅ v0.3.0 | Export logcat (filtered to AM process) + app state snapshot as shareable ZIP for bug reports | Low |
| ~~**CodeQL Alert Triage**~~ ✅ v0.3.0 | Audit all open CodeQL alerts (`.github/workflows/codeql.yml` already present); ensure zero blanket suppressions | Low |
| **ProfilingManager OOM/Anomaly Triggers** | Android 17 adds `TRIGGER_TYPE_OOM`, `TRIGGER_TYPE_COLD_START`, and `TRIGGER_TYPE_ANOMALY` to `ProfilingManager` ([S53]). On API 37+, register these triggers to auto-capture heap profiles when NG is killed during JADX decompile or APK parsing; attach the profile to the existing shareable diagnostic ZIP. `Build.VERSION.SDK_INT >= 37` guard; graceful no-op below. | Low |

---

## Next

### T5 — Rootless Users (Shizuku)

Shizuku support is the single most-requested upstream feature with 31 reactions across 5 years ([S02]). It unblocks the majority of AM operations for users without root. Canta ([S19]) and SmartPack Package Manager ([S21]) already ship Shizuku integration; AM/NG without it is at a structural disadvantage for casual users.

| Item | Description | Effort |
|------|-------------|--------|
| **Shizuku Privilege Provider** | Add Shizuku as a third privilege path alongside root/ADB via `ShizukuProvider` binder; make privilege selection automatic at runtime | High |
| **Wireless ADB Auto-Pairing** | Guide user through Android 11+ wireless ADB pairing in onboarding; persist paired device | Medium |
| **Rootless Debloat (Shizuku)** | Expose `pm uninstall --user 0` via Shizuku; integrate android-debloat-list ([S23]) safety ratings and dependency warnings | Medium |
| **Factory-Reset Before System App Uninstall** | Factory-reset a system app to its shipping state before uninstalling; prevents stub-app stalling issues on some ROMs. Canta model ([S43]). | Low |
| **Debloat Presets** | Named debloat configurations (e.g. "Privacy", "Gaming", "Minimal OEM") that batch-apply recommended freeze/remove actions. Canta model ([S43]). | Medium |
| **Install Without Staging APK** | Direct `PackageInstaller` session without staging to cache; faster installs on constrained storage (Issue #1671 [S17]) | Medium |
| **Install Existing Apps via `package:` URI** | Support `package:package-name` installer URI to install an already-present APK for the current user (e.g. system apps installed for another user); pull upstream v4.0.5 model ([S01]). Combine-safe with `SEND_MULTIPLE` for mixed-URI install batches. | Low (pull) |
| **Onboarding Capability Wizard** | Detect root/Shizuku/wireless-ADB at first launch; show plain-language capability matrix ("What you can do with each privilege level"). Include Shizuku ≥v13.6.0 minimum version check (required for Android 16 QPR1 [S22]) and auto-start on trusted WLAN tip for Android 13+ ([S22]). | Medium |
| **USB Debugging Prompt in Shizuku Setup** | During Shizuku setup wizard, explicitly prompt user to also enable USB Debugging alongside Developer Options — without it, `adb pair` and `adb connect` fail silently. Canta v3.2.0 model (fix for issue #284 [S43]). | Low |
| **KernelSU Detection in Capability Wizard** | Explicitly detect KernelSU v3.2.3+ `adb root` pathway in the privilege wizard alongside Magisk and libadb paths; KernelSU reports ~10 M active users and its `su` binary is broadly compatible with `libsu` shell invocations ([S56]). | Low |
| **Force Stop via Shizuku (Rootless)** | Expose `ActivityManager.forceStopPackage()` via the Shizuku binder as a rootless "Force Stop" action in App Details. Kills all running processes and services for the target app without freezing or hiding it; useful for clearing hung states. Hail v1.9.0 model ([S65]). Requires `FORCE_STOP_PACKAGES` permission, grantable via Shizuku; not available via ADB user-level. | Low |
| **Dhizuku (DeviceOwner) Privilege Path** | Add Dhizuku (GPL-3.0, [S71]) as a fourth rootless privilege tier alongside Root/Shizuku/ADB. Dhizuku shares `DevicePolicyManager` (DPM) ownership with third-party apps via a Binder proxy; activation requires a one-time `adb shell dpm set-device-owner com.rosan.dhizuku/.server.Server`. Unlocks DPM-based hide/suspend/freeze that Shizuku alone cannot perform. Supported Android 8–16. Capability wizard should detect Dhizuku like it detects Shizuku. | Medium |

### T6 — Backup Polish

Neo Backup ([S20]) and Titanium Backup are the benchmark. AM's backup engine is competitive; the UX around scheduling, retention, and verification is not.

| Item | Description | Effort |
|------|-------------|--------|
| **Scheduled Auto-Backup** | WorkManager-based scheduler; triggers: time-of-day, charging state, network availability ([S09]). On API 37+, use `AlarmManager.setExactAndAllowWhileIdle` with the new `OnAlarmListener` callback instead of `PendingIntent` to eliminate wake lock dependency for scheduled triggers ([S53]). | Medium |
| **Backup Retention Policy** | Set max backup count per app and age-based pruning (e.g. "keep last 3"); automatic cleanup on schedule ([S41] model) | Low |
| **1-Click Delete Old Backups** | Batch-clean oldest revisions across all apps from Backup list (Issue #387) | Low |
| **Backup Integrity Verification** | SHA-256-verify backup archives at creation and again at restore; alert on mismatch | Medium |
| **AES-256 Backup Encryption** | Password-based AES-256 encryption for backup archives; BouncyCastle already dep'd; key derivation via PBKDF2 | Medium |
| **PGP Backup Encryption** | Optional PGP key-based encryption for backup archives alongside password-based AES-256; `libopenpgp` module already present; user-verifiable with public key. Neo Backup model (returned in v8.3.12 [S41]). | Medium |
| **Backup Sharing Button** | Share individual backup archive directly from the backup list as a single user action. Neo Backup v8.3.17 model ([S41]). | Low |
| **Launcher Shortcuts for Backup Schedules** | Pin a specific backup schedule as a home screen launcher shortcut for one-tap execution. Neo Backup v8.3.17 model ([S41]). | Low |
| **Rich Backup Result Notifications** | Show per-app name, backup count, and total size in the completion notification rather than a generic "done" message. SD Maid SE scheduler model ([S42]). Distinct from the in-progress backup progress notification item. | Low |
| **Backup Progress Notifications** | Rich ongoing notification with per-app name, progress bar, and ETA. On API 36+, use `Notification.ProgressStyle` with milestone `Point` and `Segment` markers to show per-stage progress ("preparing → 3/12 apps → verifying → done") in the notification shade and lock screen; `Build.VERSION.SDK_INT >= 36` guard with graceful `setProgress()` fallback below ([S46]). | Low |
| **Pre-Backup Storage Check** | Verify available storage is sufficient before starting a backup operation; alert user with dismiss-able dialog if likely to fail mid-way. Neo Backup v8.3.15 model ([S41]). | Low |
| **Separated Active/Paused Schedule Lists** | Display enabled and disabled backup schedules in distinct sections rather than a flat unsorted list; mirrors how the system separates active vs. silent alarms. Neo Backup v8.3.15 model ([S41]). | Low |
| **Backup Tag Autocomplete** | When labeling a backup, suggest existing tags as autocomplete chip options to encourage consistent tagging across devices and schedules. Neo Backup v8.3.17 model ([S41]). | Low |
| **Scheduler Battery Optimization Auto-Fix** | When user enables a backup schedule, auto-detect if battery optimization is active for the NG process and, if root or ADB is available, disable it automatically; otherwise surface a one-tap "Exempt from battery optimization" prompt. Prevents system job-killing from silently skipping scheduled backups. SD Maid SE v1.7.2 model ([S42]). | Low |
| **JobDebugInfo Schedule Diagnostics** | When a scheduled backup fails to fire on Android 17+ (API 37), surface `JobDebugInfo.getPendingJobReasonStats()` output in the operation log to explain the skip (e.g. "quota exhausted", "app in background-restricted bucket"). Conditional on `Build.VERSION.SDK_INT >= 37`; graceful no-op below ([S53]). | Low |
| **Export/Import App List** | Export selected/filtered app list to JSON; import to recreate filter or drive batch ops (Issue #122 [S05], 8 reactions) | Medium |

### T7 — Finder (Cross-App Search)

Code exists (`FinderActivity`, `FinderViewModel`) but is incomplete per `TODO` markers. Issue #321 ([S04]) has 9 reactions and is referenced as an "upcoming feature" in upstream README.

| Item | Description | Effort |
|------|-------------|--------|
| **Finder: Components** | Complete `FinderActivity` — search activities, services, receivers, providers by name or authority | Medium |
| **Finder: Permissions** | Find all apps declaring or holding a given permission (granted/denied/custom) | Low |
| **Finder: AppOps** | Find all apps with a specific app op in a specific state | Low |
| **Finder: Regex Support** | Full regex pattern matching in component/class name fields (TrackersOption.java TODO) | Low |
| **Finder: Tracker Name Search** | Filter by tracker SDK name in Finder (TrackersOption.java TODO) | Low |
| **Finder: Multi-User Scope** | Include other user profiles in Finder results (FinderViewModel TODO) | Medium |
| **Finder: Uninstalled App Backups** | Optionally search AM backup archives of uninstalled apps (FinderViewModel TODO) | Medium |
| **Filter: Permission Flags** | Filter by granted/denied/custom/fixed permission flags in app detail permission list (PermissionsOption.java TODO) | Low |
| **Filter: Data Usage Split** | Show separate mobile vs Wi-Fi data totals in data usage filter (DataUsageOption.java TODO) | Low |
| **Frozen/Disabled Filter in App List** | Surface "frozen" and "disabled" as distinct, first-class filter options in the main app list alongside existing User/System filters. Canta v3.2.x model ([S43]). | Low |
| **Finder: Relevance-Based Search Scoring** | Apply Levenshtein distance scoring to Finder results; surface closest package-name and component-name matches first rather than flat substring order. Inure build106.3.3 model ([S66]). | Low |
| **Finder: Description-Field Search** | Include debloat-list human-readable descriptions in the Finder search index so users can find system apps by plain-language purpose (e.g. "fax" → `TelephonyUI.apk`). UAD-NG v1.2.0 model ([S67]); requires android-debloat-list metadata bundle ([S23]). | Low |

### T8 — Profiles & Automation

Routine Operations is the #2 requested feature (21 reactions, Issue #61 [S03]). Muntashir himself published the full technical spec in that issue in 2020; implementation was deferred. NG should ship this if upstream continues to stall.

| Item | Description | Effort |
|------|-------------|--------|
| **Routine Operations / Scheduler** | Event-triggered profile execution: boot, custom interval, network available, charging, screen on/off, app foreground/background (full spec: Issue #61 [S03]) | High |
| **Saved Filter Presets** | Persist named filter configurations that auto-include all user apps, allow excluding specific user apps, and manually include specific system apps (Issue #1718 [S38]). Distinct from Finder — these are persistent main-list presets. | Medium |
| **Launcher Shortcuts for AM Features** | Configurable home screen shortcuts to up to 4 user-chosen AM functions (1-Click Ops, App Usage, Running Apps, Profiles, etc.). Android GSI Shortcuts API (Issue #660 [S32]). | Low |
| **Profile State Conditions** | Profiles with conditions (execute only if battery > X%, only between hours Y–Z) | Medium |
| **Profile Import/Export** | Share profile definitions as JSON between devices or via backup | Low |
| **Intent Interceptor: Return Results** | Send activity results back to the original calling app after interception (ActivityInterceptor.java TODO) | Medium |
| **Operation Activity Log** | Persistent log of every operation AM has performed (freeze, backup, AppOps change, etc.); filterable by app, type, date (Issue #143 [S13], 3 reactions) | Medium |
| **Multi-Tag per App** | Allow assigning multiple named tags to each app; batch freeze, backup, profile, and filter operations target tag sets instead of hand-picked lists. Hail v1.10.0 model ([S65]). Schema decision required — tags stored in the Room `apps` table alongside existing `tracker_count`/`dangerous_perm_total` columns; many-to-many via a `app_tags` join table. | Medium |

### T9 — Privacy & Security

| Item | Description | Effort |
|------|-------------|--------|
| **Biometric App Lock** | BiometricPrompt-based lock for AM itself on app open (BiometricPrompt dep already present at 1.4.0-alpha04) | Low |
| **App-Ops Per-UID Control** | Allow setting app ops by UID (`--uid`) in addition to per-package; essential for shared-UID system packages where per-package mode is ambiguous (Issue #1863 [S37]). | Medium |
| **AppOps Quick-Toggle in List** | Add an inline allow/deny/default toggle chip directly on each AppOps row in the App Details AppOps tab; avoids opening a full dialog for simple permission mode changes. Reduces interaction depth for bulk AppOps editing sessions. Inure build106.5.1 model ([S66]). | Low |
| **Tracker Blocking (AppOps)** | One-click disable specific tracker components via `AppOps.setUidMode`; show which trackers are currently active. Three graduated blocking intensities: minimal (detect + report only), standard (disable known problematic SDKs), strict (disable all detected trackers); balances privacy vs. app compatibility. TrackerControl 2026040301 model ([S69]). | Medium |
| **Per-App Tracker Statistics Panel** | In App Details, show the count of detected trackers and their associated tracking companies (aggregated by organization, not just SDK name); source data from the εxodus database already embedded in-app. Informational regardless of blocking status; pairs with Tracker Blocking (AppOps). TrackerControl 2026040301 model ([S69]). | Low |
| **Sort by Dangerous Permissions** | New main-list sort option using `dangerous_perm_total` / `dangerous_perm_granted` columns added in Room schema v9 (shipped v0.4.0 dev); surfaces most-permissive apps at the top. Sort infrastructure already exists for `tracker_count` — minimal extension required. | Low |
| **Privacy Dashboard Integration** | Surface Android 12+ permission usage history (timeline view) inside AM app details | Medium |
| **App Signing Key Change Alert** | Alert when the signing certificate of an installed update differs from the previously stored one | Medium |
| **Permission Policy Flags Display** | Surface `requestedPermissionsFlags` bits (system-fixed, user-set, review-required, preinstalled, installer-exempt) alongside each permission entry in the App Details permissions list. `PackageInfo.requestedPermissionsFlags` already available; pure UI addition. Inure build106.5.0 model ([S66]). | Low |
| **MiUI-Specific AppOps Mapping** | Surface MIUI extended op-codes with human-readable descriptions and full edit support for MIUI/Xiaomi devices. Conditional on `Build.MANUFACTURER.equals("Xiaomi")`; requires embedding a MIUI op-code lookup table. Inure build106.5.0 model ([S66]). | Low–Med |
| **Permission Change Monitor** | Notify when an installed app update gains new dangerous or sensitive permissions vs. the previously installed version; tap to deep-link to App Details permission list for review. Root/Shizuku not required — `PackageManager` broadcast + stored per-app permission snapshot suffices. Permission Manager X Perm Watcher model ([S70]). | Medium |

### T10 — i18n & Accessibility

| Item | Description | Effort |
|------|-------------|--------|
| **Translation Platform (Weblate)** | Self-host or use Hosted Weblate (model: Neo Backup [S20]); link from CONTRIBUTING.md | Low |
| **RTL Layout Verification** | Full test pass under Arabic/Hebrew locale; fix any mirroring or truncation issues | Medium |
| **Plural String Audit** | Audit codebase for quantity strings that use `%d item` instead of `<plurals>` | Low |
| **Locale-Aware Date/Number Formatting** | Replace raw `SimpleDateFormat` instances with `DateTimeFormatter` + locale-aware formatting | Low |
| **TalkBack Navigation Audit** | Full TalkBack pass on every major screen; fix traversal order, group headings, action labels | Medium |
| **Content Descriptions on Icon Buttons** | Add `contentDescription` to all icon-only action buttons across the app | Low |
| **Font Scale Stress Test** | Test all screens at 200% font scale; fix overflow and truncation | Medium |

### T11 — Developer Experience

| Item | Description | Effort |
|------|-------------|--------|
| **Upstream Diff Tracking** | CI check that weekly reports divergence between NG main and upstream main; summarize cherry-pick candidates | Low |
| **Changelog Automation** | GitHub Action: extract PR titles tagged `[changelog]` into CHANGELOG.md draft on each merge | Low |
| **Architecture Documentation** | Document AIDL service architecture, privilege escalation paths, backup format spec in `/docs/architecture/` | Medium |
| **Unit Test Coverage Expansion** | Extend Robolectric test suite to cover backup/restore engine, permissions resolution, and Finder query logic (currently only basic unit tests exist per `tests.yml`) | High |

---

## Later

Items below depend on upstream shipping features, on prior work in the Now/Next tier landing, or on architectural decisions not yet made.

### T12 — APK Editing & Analysis

Upstream has committed to "basic APK editing" in its Upcoming Features list. NG tracks this closely. Engineering debt in `DexClasses.java` and `CodeEditorViewModel.java` must be resolved (see T3) before this tier is viable.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Basic APK Editing (upstream pull)** | Pull upstream's APK editing when it ships | Low (pull) | Upstream |
| **Smali/Baksmali Roundtrip for API<26** | Complete DexClasses.java FIXME — baksmali 3.0.9 already dep'd | High | T3 ABX fix |
| **APK Signing with Custom Key** | Sign modified APKs with user-provided keystore file | Medium | — |
| **ARSC Resource Table Editing** | Visual editor for string/resource tables via ARSCLib (already dep'd) | High | — |
| **AndroidManifest Visual Editor** | Point-and-click editor for common manifest attributes | High | — |
| **APK Merge (splits → universal)** | Merge split APK set into a single universal APK (model: APKEditor V1.4.8 [S48]) | Medium | — |
| **Native Library Sizes in App Details** | In the App Details library tab, display the file size of each loaded `.so` alongside the library name; enumerate `ApplicationInfo.nativeLibraryDir` entries. Inure build107.0.1 model ([S66]). | Low | — |
| **Batch APK Installer from File Manager** | Allow selecting multiple APK/APKS files in the built-in File Manager and installing them in a single batch `PackageInstaller` session. Inure build107.0.0 model ([S66]). | Medium | — |
| **Force DEX Compile Optimization** | Add "Force Optimize" action in App Details triggering `cmd package compile -m speed <pkg>` (or `-m speed-profile` for profile-guided AOT); surfaces three profiles: `speed` (full AOT), `speed-profile`, and `quicken`; requires Root or Shizuku. Useful for apps that ended up in interpreter mode after sideloading or OTA. AppBooster (awesome-shizuku Miscellaneous section [S68]) model. | Low |

### T13 — Database & File System

Upstream lists "database viewer and editor" in Upcoming Features (Issue #14 [S11], 3 reactions).

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Database Viewer (upstream pull)** | Pull when upstream ships; read-only first | Low (pull) | Upstream |
| **Database Editor** | Write capability after viewer is stable | High | DB Viewer |
| **DocumentsProvider** | Implement proper `DocumentsProvider` for third-party file manager access (FmProvider.java TODO, Issue #516 [S06], 7 reactions) | High | — |
| **File Manager Compression** | ZIP/tar archive creation and extraction in built-in file manager | Medium | — |
| **File Hash Display** | SHA-256/MD5 display for files viewed in file manager | Low | — |

### T14 — Terminal & Code Editor Polish

Upstream's "more advanced terminal emulator" is an upcoming feature. NG will pull rather than reimplement.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Advanced Terminal (upstream pull)** | Pull upstream's terminal improvements when they ship | Low (pull) | Upstream |
| **Code Editor i18n** | Add localization to code editor strings (CodeEditorFragment.java TODO) | Medium | — |
| **Code Editor Tab Size Setting** | Configurable indent width (CodeEditorFragment.java TODO) | Low | — |
| **Code Editor Language Display** | Show detected language name in editor status bar (CodeEditorFragment.java TODO) | Low | — |

### T15 — Systemless Operations

Issue #150 ([S10], 4 reactions). Magisk/overlayfs-based reversible system app management without modifying `/system`.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Systemless Freeze/Disable** | Disable system apps without removing them; uses overlayfs or Magisk module approach | High | Root |
| **Systemless Uninstall** | Remove system apps without modifying the system partition; reversible via module removal | High | Root + Magisk API |
| **Boot Loop Guard** | Automatically re-enable apps if boot does not complete within N seconds after systemless op (model: Issue #1161) | Medium | Systemless freeze |

### T16 — Clone & Isolation

Issue #1029 ([S14], 2 reactions).

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Clone Application** | Install a duplicate app instance under a separate Android user ID with data isolation | High | Multi-user API + Root |

### T17 — Advanced Accessibility & High-Contrast

| Item | Description | Effort |
|------|-------------|--------|
| **High-Contrast Theme** | High-contrast color variant alongside dark/light/AMOLED | Low |
| **Dynamic Font Size Compliance** | Full compliance with Android's font scaling up to 200% | Medium |

### T18 — Overlay Management & Deep Links

Low-effort, high-usability items gated on platform API availability or upstream pull.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Per-App Overlay Management** | Pull upstream v4.0.1's "Overlays" tab in App Details: enable/disable per-app system overlays (root/ADB, Android 8+). Upstream landed it — pull rather than reimplement ([S39]). | Low (pull) | Upstream v4.0.1+ |
| **app-manager:// Deep Link** | Support `app-manager://details?id=<pkg>&user=<uid>` deep links; enables Quick Settings tiles, widgets, and Tasker actions to open specific app's detail page. Upstream ships this in v4.0.x. | Low (pull) | Upstream |
| **Unfreeze on Shortcut Launch** | If user taps a home-screen shortcut for a frozen app, offer to unfreeze temporarily; auto-re-freeze when screen is locked. Pull upstream v4.0.1 model ([S39]). Pairs with T8 "Saved Filter Presets" for freeze-group workflows. | Low (pull) | Upstream v4.0.1+ |
| **Certificate Hash in App Detail** | Surface signing certificate SHA-256 prominently in App Details for easy cross-verification with AppVerifier/AppChecker. Obtainium v1.3.0 model ([S51]). AM already collects cert data; this is UI surfacing only. | Low | — |

### T19 — Package-Aware Storage Analysis

App-centric storage views informed by SD Maid SE's CorpseFinder ([S24]) and UAD-NG's description-search model ([S67]). NG will not replicate SD Maid SE's full storage engine — scope is limited to per-app storage panels and post-uninstall cleanup that naturally extends the existing App Details view. Items in this tier gate on storage access permissions or root.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **App Details Storage Panel** | In App Details, show APK size, split sizes, installed data, cache, OBB, external data, backup archive sizes, and orphan leftovers in a single collapsible panel. SD Maid SE CorpseFinder model ([S24]). | Medium | — |
| **Leftover Detection After Uninstall** | After uninstalling an app, scan for orphan directories (`Android/data/<pkg>`, `Android/obb/<pkg>`, root-accessible `/data/data/<pkg>` stubs) and offer one-tap cleanup. SD Maid SE CorpseFinder model ([S24]). | Medium | Root or `MANAGE_EXTERNAL_STORAGE` |
| **APK Duplicate Finder** | Find multiple copies of the same package/version APK across Downloads, backup dirs, and SD card by matching package name + signing cert + version code; flag in File Manager and backup list. SD Maid SE deduplicator model ([S42]). | Medium | File access |
| **Backup Duplicate Cleaner** | Detect the same package + version + variant backed up more than once across backup roots; offer to delete redundant copies. Extension of Backup Retention Policy (T6). | Low | T6 Retention Policy |

---

## Under Consideration

Items that need architectural decisions or external dependencies resolved before scheduling.

| Item | Blocker / Open Question |
|------|------------------------|
| **Shizuku as *default* privilege path** | AppManagerNG currently uses libadb+libsu. Adding Shizuku is a third path that must be maintained across Shizuku API changes (currently v13.6.0, Android 16 QPR1 supported [S22]). The community demand is unambiguous (31 reactions [S02]). Decision: integrate in T5 (Next) as an optional path, not mandatory. |
| **CVE Cross-Reference for Installed Apps** | Requires a reliable, privacy-respecting data source (NVD API, OSV.dev). Any network call needs explicit user consent. Limited MVP scoped to querying OSV.dev for known `packageName`+`versionCode` pairs. Track OSV Android ecosystem feed. |
| **Tasker / MacroDroid Plugin API** | Requires a stable plugin contract. Upstream does not expose a Tasker plugin. Scope: expose AM profile execution and batch ops as Tasker/Locale conditions/actions via `com.twofortyfouram:android-plugin-api`. Revisit after Routine Operations (T8) is stable. |
| **Backup Extras (SMS/Wi-Fi/Calls)** | ContentProvider approach works for standard Android. Fragile across OEM ROMs. Issue #568 asks for this ([S34]). Scoped to AOSP standard ContentProviders only; custom ROM extensions explicitly out. |
| **App "Health Score" Aggregate** | Opinionated composite risk score. Risk: obscures the raw data that AM's power users rely on. Alternative: surfacing existing scores (tracker count, dangerous permissions) more prominently in app list card without collapsing them into a single number. |
| **APK Transparency Log Verification** | Google's APK key attestation transparency requires network + Android 14+ APIs. Low priority for minSdk 21. Revisit when minSdk raises to 26 or higher. |
| **F-Droid / Aurora Store In-App Update** | Issue #464 ([S08], 12 reactions). Deep-link to app's store listing rather than replicate a store. Obtainium ([S26]) solves the full update tracking problem better. Decision: implement deep-link badges (store button on app detail page); reject full in-app store. |
| **Storage Trend Tracking** | Track per-app storage usage over time and display a trend graph. SD Maid SE v1.6.5 model ([S42]). Requires persistent historical data (~1 row/day/app). Non-trivial storage overhead for large app counts; may fit a future "Storage Analyzer" view that's opt-in. |
| **android-debloat-list Safety Ratings in App Detail** | Surface UAD-NG removal rating ("safe", "unsafe", "expert", "untested") directly in the App Details page for system apps. Requires resolving the debloat list JSON at install time vs. at query time. Low network impact if list is bundled. |
| **KernelSU as Third Root Provider** | ~~Promoted to T5 (Next).~~ KernelSU v3.2.3+ `adb root` pathway has sufficient signal (~10 M users, `libsu`-compatible `su` binary) to warrant explicit detection in the capability wizard. See T5 "KernelSU Detection in Capability Wizard" ([S56]). |
| **Android 17 Advanced Protection Mode (AAPM) Integration** | `AdvancedProtectionManager` API (Android 17, API 37 [S53]) lets apps detect if the user has opted into AAPM. NG could surface "Device in Advanced Protection Mode" in the Privacy & Security section and automatically restrict high-risk operations (e.g. USB-debugging auto-pair, unknown-source installs) when active. Gated on minSdk raise to 37. |
| **JADX Plugin API for Analysis Extensions** | JADX 1.5.0+ ships an external plugin ecosystem ([S60]); scripts are now loadable as ZIP artifacts. Could expose custom class detectors (e.g. tracker pattern matching, hardcoded-secret detection) as user-installable NG analysis plugins. Architecture decisions: plugin trust model, sandboxing, GPL-compatible licensing chain. |
| **DDG Tracker Radar as Secondary Tracker Source** | TrackerControl 2026040301 ([S69]) integrates DuckDuckGo Tracker Radar (mobile-specific tracker database) alongside Exodus definitions for broader coverage. DDG Tracker Radar data is CC-BY-NC-SA — the Non-Commercial clause is incompatible with GPL-3.0 redistribution without explicit licensing. **Do not bundle DDG Tracker Radar data until legal clearance is obtained.** Alternative: contribute new SDK signatures upstream to the Exodus database (MIT-licensed), which NG already ships. |

---

## Rejected

| Item | Reason |
|------|--------|
| **Multi-device simultaneous ADB** (UAD-NG model) | UAD-NG is a desktop tool; AM/NG is on-device. This is a fundamentally different UX. WONTFIX. |
| **LSPatch integration** | LSPatch archived Dec 2023 (last release v0.6); no longer maintained; no viable path to integration ([S61]). WONTFIX. |
| **Play Store / APKMirror update tracking built-in** | Obtainium ([S26]) solves this with more sources and better maintenance. Out of scope; deep-link instead. |
| **Full on-device app store** | Aurora Store and F-Droid exist and are actively maintained. NG should complement, not replicate. |
| **App health score (single composite number)** | Collapses nuanced data into a single opinionated metric. AM's value is raw transparency; a score obscures it. Rejected — surface the component scores better instead. |
| **announceForAccessibility suppress-only fix** | Must do a real accessibility audit (T10), not suppress the deprecated API call without fixing the underlying UX. |
| **Backup of in-app purchase tokens / DRM state** | Not possible without root + OEM cooperation. Out of scope. |

---

## Engineering Debt Register

Issues catalogued in source that slow or block future work. Resolve before the feature theme that depends on them ships.

| File | Type | Issue | Severity | Theme |
|------|------|-------|----------|-------|
| `KeyStoreUtils.java` | FIXME | Keystore password stored in `String` (not clearable) | **High** | T3 |
| `CodeEditorViewModel.java` | FIXME | ABX→XML conversion is lossy; serializer needs update | **High** | T3, T12 |
| `DexClasses.java` | FIXME | No Smali/Baksmali for API < 26 | Medium | T12 |
| `FmProvider.java` | TODO | DocumentsProvider not properly implemented | Medium | T13 |
| `FinderViewModel.java` | TODO | Multi-user support + uninstalled app backups missing | Medium | T7 |
| `ActivityInterceptor.java` | TODO | Results not sent back to original calling app | Medium | T8 |
| `Utils.java` (×5) | FIXME | Hardcoded strings bypass i18n | Low–Med | T10 |
| `VirusTotal.java` | TODO | `fetchFileReportOrScan` poll intervals are hardcoded (60 s then 30 s); should scale with `file.length()` per inline comment (L164, 2022-05-23). Low-urgency polish. | Low | Scanner |
| `AppTypeOption.java` | TODO | Play App Signing / PWA / overlay detection missing | Low | App Details |
| `PermissionsOption.java` | TODO | Permission flags not exposed in filter | Low | T7 |
| `DataUsageOption.java` | TODO | Mobile/Wi-Fi split not shown | Low | T7 |
| `TrackersOption.java` | TODO | Regex + tracker-name search not implemented | Low | T7 |
| `CodeEditorFragment.java` | TODO | i18n, custom tab size, language display missing | Low | T14 |
| BouncyCastle `1.83` | Dependency | Latest is `1.84`; no CVE exposure at 1.83 (GHSA-8xfc-gm6g-vgpv fixed in 1.78 [S49]); upgrade is low-urgency maintenance ([S50]) | Low | Security |
| libsu `6.0.0` | API | `Shell.sh/su` removed in 6.0.0; replaced by `Shell.cmd` — verify no legacy `Shell.sh/su` calls survive in NG source. `FLAG_REDIRECT_STDERR` also deprecated ([S47]). | Medium | All root ops |
| ~~All layouts~~ ✅ 2026-05-01 | Compliance | Audit for `elegantTextHeight` attribute usage — ignored for targetSdk=36; text rendering affected for Arabic/Thai/Indic scripts ([S44]). **Audit clean — zero source matches.** See [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md). | Low | T2 |
| WorkManager/JobScheduler | Compliance | Backup scheduler must be tested under Android 16 quota model; log stop reasons with `WorkInfo.getStopReason()` ([S45]). **⏸ Blocked: no WorkManager in source today — wire diagnostics into T6 Scheduled Auto-Backup when that lands.** | Low | T6 |
| JADX `1.4.7` | Dependency | Latest is `1.5.5` (7 releases behind [S60]); includes critical multi-thread UI fix (v1.5.5), `.apks` support, CJK font rendering, and an external plugin system (v1.5.4+). Upgrade before T12 work begins. | Medium | T12 |
| Gson `2.13.2` | Dependency | Latest is `2.14.0` ([S58]); new `java.time` adapters; stricter integer ASCII validation; `Serializable` removed from internal Type impl classes (minor security hygiene). Low-risk upgrade. | Low | All |
| Material Components `1.13.x` | Ceiling | Material `1.14.0-rc01` raises `minSdkVersion` to 23 ([S57]); blocked until NG raises `minSdk` from 21. Action: document in `versions.gradle` comment; schedule minSdk raise to 23 before any Material 1.14.x adoption. | Medium | All |
| AGP `8.13.2` | Dependency | Latest stable is AGP `9.2`; requires Gradle 9.4.1 and SDK Build Tools 36 ([S59]). R8 tightens `-keepattributes *Annotation*` wildcard behavior in 9.x — auditing ProGuard rules required at upgrade. Not urgent but track for annual build-toolchain cycle. | Medium | Build |
| MagiskSU capability drop | Behavior | MagiskSU `≥v30.5` no longer drops Linux capabilities by default; explicit `--drop-cap` now required ([S52]). Audit all root shell invocations in NG that assumed automatic capability restriction before shipping T15 systemless ops. | Medium | T15 |
| Android 17 targetSdk=37 compliance | Compliance batch | Four items required before bumping `targetSdkVersion` to 37: (1) declare `ACCESS_LOCAL_NETWORK` runtime permission for wireless ADB LAN discovery ([S55]); (2) audit hidden-API bypass code paths for `static final` field reflection — `IllegalAccessException` for targetSdk=37 apps ([S54]); (3) migrate `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` → `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` in all `ActivityOptions` usage ([S54]); (4) verify Network Security Config for Certificate Transparency (CT default-on for targetSdk=37 [S54]). | High | T5, T8, T9 |
| Android 17 memory limits | Compliance | All-apps change: Android 17 introduces RAM-based per-app `AnonSwap` memory limits; exit detected via `ApplicationExitInfo.REASON_OTHER` with `"MemoryLimiter:AnonSwap"` description ([S55]). JADX decompile and APK-signature parsing are the heaviest NG operations; profile under low-memory devices before T12 ships. | Medium | T12 |
| Android 17 `usesCleartextTraffic` deprecation | Compliance | Android 17 begins enforcement of `usesCleartextTraffic`; apps that omit the Network Security Config manifest attribute will trigger lint errors on targetSdk=37 ([S55]). Audit NG's `network_security_config.xml` and all `http://` URLs in manifests before bumping targetSdk. | Low | T9 |
| Android 17 ECH default-on | Compliance | Apps targeting API 37 get ECH (Encrypted Client Hello) applied to all TLS connections by default ([S54]). NG must add a `<domainEncryption>` element to `network_security_config.xml` if any network endpoint (e.g. wireless ADB LAN pair) must opt out of ECH to avoid negotiation failures on older firmware. | Low | T5, T9 |
| Android 17 per-app Keystore key cap | Compliance | Non-system apps targeting API 37 are limited to 50,000 Keystore keys ([S55]). Backup encryption and per-app signing paths may accumulate keys over time; audit `KeyStoreManager` before the targetSdk=37 bump and prune any excess. | Low | T2, T9 |
| Android 18 implicit URI grant removal (planned) | Compliance | Android 17 preview signals that `Send`/`SendMultiple`/`ImageCapture` intents will stop auto-granting URI permissions to targets in Android 18 ([S55]). Pre-emptively audit APK-sharing intent paths; add explicit `grantUriPermission()` calls to prevent silent share breakage. | Low | T3, T5 |
| GitHub Actions `release.yml` | Future | Android 17 introduces ML-DSA hybrid APK signing for post-quantum forward security ([S53]). Evaluate adding a PQC co-signer to the release pipeline when bumping targetSdk=37; fully backward-compatible with older Android versions and does not affect F-Droid or IzzyOnDroid builds. | Low | T1 |

---

## Upstream Sync Strategy

AppManagerNG is not a hard fork. The intent is to stay semantically close to upstream while diverging on UX.

**Pull policy:**
- Security fixes from upstream: pull immediately regardless of conflict cost.
- Bug fixes: cherry-pick within one upstream release cycle.
- Upstream "Upcoming Features" (Finder, APK editing, Routine Ops, Crash Monitor, Database Viewer, Terminal): pull when upstream ships rather than reimplement. NG reimplements only if upstream stalls for >12 months.

**Divergence tracking:**
- Weekly CI check comparing NG `main` against upstream `master`; generates cherry-pick candidate list.
- Maintained in `.github/upstream-sync.md` (to be created in v0.2.0).

**Current upstream "Upcoming Features" status:**

| Feature | Upstream Status | NG Action |
|---------|----------------|-----------|
| Finder | Partial code exists | Complete in NG (T7) |
| Basic APK editing | Committed, not shipped | Pull when ready (T12) |
| Routine operations | Spec published (Issue #61), not shipped | Implement in NG (T8) if stalled |
| Crash monitor | Mentioned only | Pull when ready |
| Database viewer/editor | Issue #14, not shipped | Pull when ready (T13) |
| Advanced terminal | Mentioned only | Pull when ready (T14) |
| Systemless ops | Issue #150, not shipped | Implement in NG (T15) |
| Import app list | Issue #122, not shipped | Implement in NG (T6) |
| Overlay management | Shipped v4.0.1 ([S39]) | Pull into T18 |
| app-manager:// deep link | Shipped v4.0.x ([S40]) | Pull into T18 |
| Operation history log | Shipped v4.0.0 ([S01]) | Implement in NG (T8) as medium-effort standalone feature — AM's implementation covers freeze, uninstall, backup; NG should match |
| Installer `package:` URI | Shipped v4.0.5 ([S01]) | Pull into T5 |
| Profile shortcut configured-state execution | Shipped v4.0.4 ([S01]) | Pull into T8 |
| v4.1.0 milestone (24 issues, 0 closed) | Due 2026-09-18; not yet started ([S40]) | Monitor; cherry-pick security/bug fixes when they land |
| v4.2.0 milestone (20+ issues confirmed open) | Issues #138, #150, #163, #221, #237, #256, #309, #321, #327, #333, #360, #385, #387, #402, #529, #568, #846, #1158, #1274, #1366, #1412, #1488 milestoned; none closed; upstream in-progress on #1017 | Implement #321 (Finder), #150 (Systemless), #568 (Backup extras), #163 (Crash monitor) in NG if upstream stalls past 12 months from milestone due date |

---

## Source Appendix

All external sources cited above.

| ID | URL | Used For |
|----|-----|----------|
| S01 | https://github.com/MuntashirAkon/AppManager | Upstream README, release notes v4.0.0–v4.0.5, upcoming features |
| S02 | https://github.com/MuntashirAkon/AppManager/issues/55 | Shizuku support (31 reactions) |
| S03 | https://github.com/MuntashirAkon/AppManager/issues/61 | Routine Operations full spec (21 reactions) |
| S04 | https://github.com/MuntashirAkon/AppManager/issues/321 | Finder cross-app search (9 reactions) |
| S05 | https://github.com/MuntashirAkon/AppManager/issues/122 | Export/Import App List (8 reactions) |
| S06 | https://github.com/MuntashirAkon/AppManager/issues/516 | DocumentsProvider (7 reactions) |
| S07 | https://github.com/MuntashirAkon/AppManager/issues/138 | APK Editor (7 reactions) |
| S08 | https://github.com/MuntashirAkon/AppManager/issues/464 | Aurora/F-Droid in-app unification (12 reactions) |
| S09 | https://github.com/MuntashirAkon/AppManager/issues/555 | Scheduled auto-backup (4 reactions) |
| S10 | https://github.com/MuntashirAkon/AppManager/issues/150 | Systemless features (4 reactions) |
| S11 | https://github.com/MuntashirAkon/AppManager/issues/14 | Database viewer (3 reactions) |
| S12 | https://github.com/MuntashirAkon/AppManager/issues/163 | Crash monitor (3 reactions) |
| S13 | https://github.com/MuntashirAkon/AppManager/issues/143 | Activity log (3 reactions) |
| S14 | https://github.com/MuntashirAkon/AppManager/issues/1029 | Clone applications (2 reactions) |
| S15 | https://github.com/MuntashirAkon/AppManager/issues/561 | Profile enhancements (2 reactions) |
| S16 | https://github.com/MuntashirAkon/AppManager/issues/1767 | Interceptor breaks OPEN_DOCUMENT (2 reactions) |
| S17 | https://github.com/MuntashirAkon/AppManager/issues/1671 | Install without staging APK (2 reactions) |
| S18 | https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation | UAD-NG: debloat list, multi-device model, dependency schema |
| S19 | https://github.com/samolego/Canta | Canta: rootless Shizuku debloater, UAD-NG list integration |
| S20 | https://github.com/NeoApplications/Neo-Backup | Neo Backup: backup UX reference, Weblate model, AES-256 encryption |
| S21 | https://github.com/SmartPack/PackageManager | SmartPack Package Manager: Shizuku feature comparison |
| S22 | https://github.com/RikkaApps/Shizuku/releases | Shizuku v13.6.0: Android 16 QPR1 support, auto-start on trusted WLAN |
| S23 | https://github.com/MuntashirAkon/android-debloat-list | android-debloat-list JSON schema (dependencies, required_by, removal safety) |
| S24 | https://github.com/d4rken-org/sdmaid-se | SD Maid SE: CorpseFinder, scheduler model, Shizuku integration |
| S25 | https://github.com/Aefyr/SAI | SAI: split APK installer (archived); installation method reference |
| S26 | https://github.com/ImranR98/Obtainium | Obtainium: multi-source update tracking, AppVerifier integration |
| S27 | https://github.com/topjohnwu/libsu/releases | libsu release history (6.0.0 is current; 5.x was prior) |
| S28 | https://developer.android.com/about/versions/16/summary | Android 16 behavior changes (edge-to-edge, predictive back, themed icons) |
| S29 | https://developer.android.com/about/versions/15/features | Android 15 features (16KB page size, PDF renderer, OpenJDK 17) |
| S30 | https://github.com/termux/termux-app | Termux: terminal emulator reference for advanced terminal feature |
| S31 | https://github.com/MuntashirAkon/AppManager/issues/1161 | Boot loop guard for critical system app removal |
| S32 | https://github.com/MuntashirAkon/AppManager/issues/660 | App shortcuts / Quick Settings tile |
| S33 | https://github.com/MuntashirAkon/AppManager/issues/387 | 1-click delete old backups |
| S34 | https://github.com/MuntashirAkon/AppManager/issues/568 | Backup extras (SMS/calls/Wi-Fi) |
| S35 | https://github.com/MuntashirAkon/AppManager/issues/447 | Greenify-like prescription rules |
| S36 | https://github.com/MuntashirAkon/AppManager/issues/75 | Intent intercept feature improvements |
| S37 | https://github.com/MuntashirAkon/AppManager/issues/1863 | AppOps precision control |
| S38 | https://github.com/MuntashirAkon/AppManager/issues/1718 | Custom filter presets / saved app lists |
| S39 | https://github.com/MuntashirAkon/AppManager/releases/tag/v4.0.1 | Upstream v4.0.1: overlay management, per-session installer options, market URL scheme |
| S40 | https://github.com/MuntashirAkon/AppManager/releases/tag/v4.0.5 | Upstream v4.0.5: reproducible builds, logcat omit-sensitive-info |
| S41 | https://github.com/NeoApplications/Neo-Backup/releases | Neo Backup 8.3.12–8.3.17: PGP encryption, backup sharing, launcher schedule shortcuts, tags filter |
| S42 | https://github.com/d4rken-org/sdmaid-se/releases | SD Maid SE 1.6.5–1.7.2: storage trend tracking, scheduler result notifications, deduplicator, scheduler battery optimization auto-fix |
| S43 | https://github.com/samolego/Canta/releases | Canta 3.0.0–3.2.2: Disabled filter, factory-reset before uninstall, debloat presets, Android 16 Shizuku fix |
| S44 | https://developer.android.com/about/versions/16/behavior-changes-16 | Android 16 targetSdk=36 behavior: adaptive layouts, `elegantTextHeight` deprecation, `scheduleAtFixedRate` change |
| S45 | https://developer.android.com/about/versions/16/behavior-changes-all | Android 16 all-apps: JobScheduler quota changes, abandoned job stop reason, broadcast priority scope |
| S46 | https://developer.android.com/about/versions/16/features | Android 16 new APIs: `SDK_INT_FULL`, `Notification.ProgressStyle`, two-release cadence for 2025 |
| S47 | https://github.com/topjohnwu/libsu/blob/master/CHANGELOG.md | libsu 6.0.0: `Shell.sh/su` removed, `Shell.cmd` API, `FLAG_REDIRECT_STDERR` deprecated |
| S48 | https://github.com/REAndroid/APKEditor/releases | APKEditor V1.4.8: Android 36 support, dex v042, `--remove-annotation`, smali performance |
| S49 | https://osv.dev/vulnerability/GHSA-8xfc-gm6g-vgpv | BouncyCastle CVE: affects <1.78; fixed in 1.78; NG uses 1.83 (clean) |
| S50 | https://www.bouncycastle.org/releasenotes.html | BouncyCastle 1.84 current; NG on 1.83 |
| S51 | https://github.com/ImranR98/Obtainium/releases | Obtainium v1.3.0–v1.4.3: certificate hash display; dynamic color fix API 34+; GitHub repo rename detection; Android TV remote focus |
| S52 | https://github.com/topjohnwu/Magisk/releases | Magisk v30.5–v30.7: MagiskSU no longer drops capabilities by default; Android 16 QPR2 Zygisk support; Rust migration |
| S53 | https://developer.android.com/about/versions/17/features | Android 17 features: ProfilingManager triggers, JobDebugInfo APIs, ECH, AAPM, PQC APK signing, contact picker |
| S54 | https://developer.android.com/about/versions/17/behavior-changes-17 | Android 17 targetSdk=37: static-final field restriction, ACCESS_LOCAL_NETWORK, BAL IntentSender, CT default, native DCL |
| S55 | https://developer.android.com/about/versions/17/behavior-changes-all | Android 17 all-apps: RAM-based memory limits (AnonSwap), Keystore per-app key cap, usesCleartextTraffic deprecation plan |
| S56 | https://github.com/tiann/KernelSU/releases | KernelSU v3.2.3: adb root feature; v3.2.4: seccomp status display, su log screen |
| S57 | https://github.com/material-components/material-components-android/releases | Material 1.14.0-rc01: minSdkVersion raised to 23 — blocks upgrade while NG remains on minSdk 21 |
| S58 | https://github.com/google/gson/releases | Gson 2.14.0: java.time adapters, stricter integer validation, Serializable removed from internal types |
| S59 | https://developer.android.com/build/releases/gradle-plugin | AGP 9.2 release notes: Gradle 9.4.1 required; R8 annotation keepattr wildcard tightened; unified test/coverage dashboards |
| S60 | https://github.com/skylot/jadx/releases | JADX 1.5.5: critical multi-thread UI fix; 1.5.4: plugin system (external ZIP artifacts), .apks support, CJK fonts, UI zoom |
| S61 | https://github.com/LSPosed/LSPatch/releases | LSPatch v0.6 — archived Dec 2023; no longer actively maintained; integration with NG deferred indefinitely |
| S62 | https://accrescent.app/features | Accrescent: key-pinning on first install, signed metadata, no TOFU, no accounts required, unattended updates, split APK support, min-version pinning |
| S63 | https://github.com/soupslurpr/AppVerifier/releases | AppVerifier v0.5.0–v0.8.2: APK file verification via VIEW/SEND intent, cert hash display, multi-signer support; v0.8.0 removed Material dep (-1 MB) |
| S64 | https://f-droid.org/docs/Inclusion_How-To/ | F-Droid inclusion requirements: FOSS-only deps, Fastlane metadata directory, CLI buildable, reproducible builds preferred |
| S65 | https://github.com/aistra0528/Hail/releases | Hail v1.9.0: Force Stop (Root + Shizuku), Reinstall uninstalled system apps via `pm install-existing` (Root + Shizuku), System App Disable mode; v1.10.0: multi-tag per app, URI schema for automation API, KernelSU App Profile support, Xposed auto-unfreeze hooks, Auto-Freeze QS tile |
| S66 | https://github.com/Hamza417/Inure/releases | Inure build106.3.3–build107.0.1: Levenshtein relevance search, native library sizes, MiUI AppOps support, permission policy flags display, batch APK installer, terminal security fix |
| S67 | https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases/tag/v1.2.0 | UAD-NG v1.2.0: dynamically fetched package list (no app update needed for list changes), search in descriptions, package state + cross-user verification, selectable/copyable descriptions |
| S68 | https://github.com/timschneeb/awesome-shizuku | awesome-shizuku: curated list of 50+ apps using Shizuku for elevated operations — reference for rootless capability expansion, vendor-specific Shizuku tooling landscape |
| S69 | https://github.com/TrackerControl/tracker-control-android/releases | TrackerControl 2026040301: graduated blocking modes (minimal/standard/strict), DuckDuckGo Tracker Radar integration, multi-blocklist auto-updates, per-app tracker statistics, Show Unprotected Apps, Material 3 migration started |
| S70 | https://github.com/mirfatif/PermissionManagerX/releases | Permission Manager X v1.25–v1.31: Perm Watcher real-time permission change monitoring, 25+ language localizations, Android 16 target compliance |
| S71 | https://github.com/iamr0s/Dhizuku | Dhizuku: DeviceOwner permission sharing daemon (GPL-3.0); Android 8–16; activation via `adb shell dpm set-device-owner com.rosan.dhizuku/.server.Server`; Dhizuku-API library for third-party app integration; enables DPM-based freeze/hide/suspend without root |
