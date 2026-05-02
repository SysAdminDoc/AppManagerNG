# AppManagerNG — Roadmap

**Status:** Living document — update on every version bump.  
**Baseline:** v0.1.0, forked from [App Manager](https://github.com/MuntashirAkon/AppManager) @ `3d11bcb` (post-v4.0.5), 2026-04-30.  
**Last updated:** 2026-05-02 (iter-18: UAD selectable descriptions, UAD copy-error helper, SD-Maid auto-fix battery optimization, Inure native-lib size + AppOps IGNORE verified; v0.5.x Phase 1 layout migration shipped).
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
| **v0.4.0** 🔨 In Progress | Onboarding & Premium Polish Foundation | Root/Shizuku/ADB capability detection wizard; plain-language privilege explainer; first-run flow; **v2 design system foundation behind opt-in toggle** (calmer palette, tighter typography, pill controls — see [Premium Polish Track](#premium-polish-track-v04x--v07x)) |
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
| ~~**Android TV / Google TV Launcher Support**~~ ✅ pre-existing | `LEANBACK_LAUNCHER` category, `<uses-feature android:name="android.software.leanback" android:required="false"/>`, optional touchscreen, and `android:banner="@mipmap/ic_banner"` are already wired on `SplashActivity` (the user-facing launcher). Audited 2026-05-02; matches SD Maid SE v1.7.x model ([S112]). No work required. | Trivial |
| ~~**Publish Signing Cert Fingerprint at Stable URL**~~ ✅ 2026-05-02 | SHA-256 fingerprint published in machine-parseable form at [`docs/fingerprints.txt`](docs/fingerprints.txt), served via the stable `https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/docs/fingerprints.txt` URL (no GitHub Pages activation required). Comment-tolerant `package:` / `sha256:` record pairs follow the SD Maid SE precedent ([S112]). README "Verifying releases" section links to the URL. | Trivial |

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
| ~~**Android 17 Keystore Per-App Key Cap**~~ ✅ 2026-05-02 | Audit clean. NG creates at most **2** `AndroidKeyStore` aliases over its lifetime (`aes_local_protection` on M+, `rsa_wrap_local_protection` on pre-M legacy path), both in `CompatUtil.getAesGcmLocalProtectionKey()` and both `containsAlias`-guarded so they're idempotent. All backup-crypto paths route through a **file-backed BKS keystore** (`am_keystore.bks` via `KeyStoreManager`) which is outside the platform-managed Keystore and therefore outside the 50,000-key cap. Zero `AndroidKeyStore` references in `backup/**` or vendored `libs/**`. See [docs/audits/2026-05-02-android17-keystore-key-cap.md](docs/audits/2026-05-02-android17-keystore-key-cap.md). | Low |

### T3 — Critical Bug Fixes & Security Debt

These are `FIXME` / security issues identified in source that should not wait for a feature release.

| Item | File | Description | Effort |
|------|------|-------------|--------|
| ~~**Keystore Password Security**~~ ✅ v0.3.0 | `KeyStoreUtils.java` | Use `char[]` instead of `String` for keystore passwords (String is interned in heap; not clearable) | Low |
| ~~**ABX-to-XML Lossless Fix**~~ ✅ v0.3.0 | `CodeEditorViewModel.java` | Current ABX→XML conversion is lossy; update serializer to round-trip without data loss | Medium |
| ~~**Intent Interceptor OPEN_DOCUMENT**~~ ✅ v0.3.0 | `ActivityInterceptor.java` | Interceptor permanently breaks `android.intent.action.OPEN_DOCUMENT` (Issue #1767, 2 reactions); fix dispatch logic | Medium |
| ~~**Utils.java i18n (×5)**~~ ✅ 2026-05-02 | `Utils.java` | Soft-input / service-flag / activity-flag / input-feature flag strings now pull from `strings.xml`; `getProtectionLevelString` keeps Android's canonical manifest-protection-level tokens (`dangerous`, `signature\|privileged`, etc.) intentionally untranslated — they are technical identifiers, and a `protectionLevel.contains("dangerous")` check exists in `AppDetailsPermissionsFragment`. | Low |
| ~~**Android 18 Implicit URI Grant Removal**~~ ✅ 2026-05-02 | `PackageInstaller` / share intents | All seven outgoing `ACTION_SEND` / `ACTION_SEND_MULTIPLE` paths that carry a content URI now set both `FLAG_GRANT_READ_URI_PERMISSION` and an explicit `ClipData` (multi-item for `SEND_MULTIPLE`) so the chooser target receives read access without relying on Android's implicit auto-grant. Three text-only sender paths (no URI) left untouched. Zero `IMAGE_CAPTURE` callers in source. `PackageInstaller` install path streams via `openWrite()` and is unaffected. See [docs/audits/2026-05-02-android18-implicit-uri-grant.md](docs/audits/2026-05-02-android18-implicit-uri-grant.md). | Low |

### T4 — Observability & Process

| Item | Description | Effort |
|------|-------------|--------|
| ~~**Opt-In Crash Reporting**~~ ✅ v0.3.0 | On uncaught exception: write crash log to app-private storage + show "Share crash report" dialog that deep-links to GitHub Issues. Zero network egress without explicit user action. | Low |
| ~~**In-App Diagnostic Dump**~~ ✅ v0.3.0 | Export logcat (filtered to AM process) + app state snapshot as shareable ZIP for bug reports | Low |
| ~~**CodeQL Alert Triage**~~ ✅ v0.3.0 | Audit all open CodeQL alerts (`.github/workflows/codeql.yml` already present); ensure zero blanket suppressions | Low |
| ~~**ProfilingManager OOM/Anomaly Triggers**~~ ✅ 2026-05-02 (registration) | New `ProfilingTriggerHelper.registerTriggersIfSupported(Context)` (`misc/`) registers `TRIGGER_TYPE_OOM` + `TRIGGER_TYPE_ANOMALY` via reflection on API 37+; silent no-op below or on any reflective-lookup failure (compileSdk is still 36). Wired from `AppManager.onCreate()`. Listener-side wiring to harvest the resulting profile artifacts and attach them to the diagnostic ZIP is deferred until API 37 is on a real device for end-to-end test. | Low |

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
| ~~**KernelSU Detection in Capability Wizard**~~ ✅ 2026-05-02 | New `runner/RootManagerInfo` probes `/data/adb/ksu` via the privileged shell (when root is granted) and falls back to the installed `me.weishu.kernelsu` / `com.rifsxd.ksunext` packages otherwise. Surfaced as a " · KernelSU" suffix on the onboarding sheet's Root status line; refresh fires async on bind + on Re-check. Pairs with Magisk + APatch detection in the same helper (one shell round-trip). | Low |
| **Force Stop via Shizuku (Rootless)** | Expose `ActivityManager.forceStopPackage()` via the Shizuku binder as a rootless "Force Stop" action in App Details. Kills all running processes and services for the target app without freezing or hiding it; useful for clearing hung states. Hail v1.9.0 model ([S65]). Requires `FORCE_STOP_PACKAGES` permission, grantable via Shizuku; not available via ADB user-level. | Low |
| **Dhizuku (DeviceOwner) Privilege Path** | Add Dhizuku (GPL-3.0, [S71]) as a fourth rootless privilege tier alongside Root/Shizuku/ADB. Dhizuku shares `DevicePolicyManager` (DPM) ownership with third-party apps via a Binder proxy; activation requires a one-time `adb shell dpm set-device-owner com.rosan.dhizuku/.server.Server`. Unlocks DPM-based hide/suspend/freeze that Shizuku alone cannot perform. Supported Android 8–16. Capability wizard should detect Dhizuku like it detects Shizuku. | Medium |
| ~~**APatch Detection in Capability Wizard**~~ ✅ 2026-05-02 | Same `runner/RootManagerInfo` helper probes `/data/adb/ap/` and the `me.bmax.apatch` package fallback. Surfaced as " · APatch" on the onboarding sheet's Root status line. SuperKey availability and per-module count surfacing deferred to the standalone Privilege Health-Check Screen (separate row, T5). | Low |
| **Privilege Health-Check Screen** | Persistent diagnostics screen under Settings > Privileges (supplements the first-run capability wizard). Shows: root manager in use (Magisk / KernelSU / APatch / none), Shizuku binding status + API version + minimum-version pass/fail, wireless ADB pairing reachability, battery optimization state for the NG process with one-tap root/ADB auto-disable (SD Maid SE v1.7.2 model [S42]), and per-tier self-test result. FreezeYou v11.0 self-diagnosis page model ([S75]). | Medium |
| ~~**ZygiskNext Detection**~~ ✅ 2026-05-02 | `runner/RootManagerInfo` issues a follow-up `[ -d /data/adb/modules/zygisksu ]` check whenever a non-NONE root manager is detected; result surfaces as " + ZygiskNext" appended to the manager name on the onboarding sheet (e.g. "Detected · KernelSU + ZygiskNext"). Module-error-count via the v1.3.4+ WebUI is deferred to the standalone Privilege Health-Check Screen. | Low |
| **Installer APK SHA256 Toast** | After streaming APK bytes into a `PackageInstaller` session but before committing, compute and display the SHA256 of the written bytes in a dismissible info chip; lets user cross-verify against the publisher's posted checksum before accepting the system install prompt. No extra permissions required — pure UI addition to the existing install flow. Droid-ify v0.7.0 model ([S81]). | Low |
| ~~**Battery Optimization Bypass Toggle**~~ ✅ shipped 2026-05-02 | Added "Battery optimization" entry under Settings → Troubleshooting; surfaces live exemption state via `PowerManager.isIgnoringBatteryOptimizations()` and routes to either `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (if optimized) or `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (if exempt, so the user can revoke). Refreshes summary on `onResume`. Pre-M devices show a disabled entry with explanatory copy. Manifest declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Dhizuku v2.11.2 model ([S84]). | Low |
| **Android 16 Capability Dropping UI** | Surface capability-dropping policy in Privilege Health-Check Screen: show whether `--drop-cap` (revoke non-essential Linux capabilities for non-root UIDs) is enabled. Magisk v30.7 added explicit capability-dropping control ([S85]); expose this for power users who need to verify security posture. Read from root manager's config or expose as a test action in diagnostics. | Low |
| **VPN Plugin Flags Control** | In Privilege Health-Check Screen, expose an opt-in toggle for system VPN flags (when using Shizuku VPN binding). The VPN-mode flag must be explicitly requested at session time; mirrors shadowsocks-android v5.3.5's opt-in model ([S87]) to surface user choice and avoid silent VPN-mode activation. Relevant for apps using Shizuku-controlled VPN services. | Low |
| **Auto-fix Battery Optimization (Root/ADB Path)** | When user schedules a routine operation, NG detects whether battery-optimization exemption is granted; if not, offer to auto-grant via root or ADB (`dumpsys deviceidle whitelist +<pkg>`). Removes the manual "go to Settings, find NG, exempt" friction. SD Maid SE v1.7.x ([S112]) "Scheduler: Auto-fix battery optimization via root or ADB". Pairs with the manual Battery Optimization Bypass Toggle row above. | Low |
| **Cross-User Package State Detection** | When examining apps on multi-user devices (work profile, secondary users), detect and display per-user package state. Show "enabled for user 0, disabled for user 10" with explicit user-IDs. UAD-ng v1.2.0 ([S113]) "add package state verification and cross-user detection". Requires `pm` query with `--user N` parsed from `UserInfo`. | Medium |
| **Auto-Update Debloat Definitions** | Auto-fetch the latest debloat package definitions on app launch (with offline fallback to bundled snapshot). Decouples package-list updates from app releases. UAD-ng v1.2.0 ([S113]) ships package list updates without new app version. Privacy: opt-in via Settings; honor "Network usage" preference and avoid leaking telemetry. | Medium |

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
| **SMB / WebDAV Network Backup Destination** | Allow backup archives to be written directly to an SMB share or WebDAV endpoint in addition to local storage; requires root or `MANAGE_EXTERNAL_STORAGE` for full data backup access; scope limited to backup storage-destination selection — no change to the backup engine itself. Android-DataBackup v2.x model ([S79]). | High |

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
| ~~**Frozen/Disabled Filter in App List**~~ ✅ pre-existing | The main-list quick-filter chip row already includes a first-class **Frozen** chip alongside User / System / Running / Backed Up / Stopped / Trackers (see `chip_frozen` in [activity_main.xml:76-82](app/src/main/res/layout/activity_main.xml#L76-L82), wired to `MainListOptions.FILTER_FROZEN_APPS`). AM's freeze model already covers the disable/freeze cases that Canta exposes separately (component-disable, suspend, cloak, hide), so a distinct "Disabled" chip on top of "Frozen" would be redundant on this stack. Audit row was stale; closing it during the 2026-05-02 batch. | Low |
| **Finder: Relevance-Based Search Scoring** | Apply Levenshtein distance scoring to Finder results; surface closest package-name and component-name matches first rather than flat substring order. Inure build106.3.3 model ([S66]). | Low |
| **Finder: Description-Field Search** | Include debloat-list human-readable descriptions in the Finder search index so users can find system apps by plain-language purpose (e.g. "fax" → `TelephonyUI.apk`). UAD-NG v1.2.0 model ([S67]); requires android-debloat-list metadata bundle ([S23]). | Low |
| ~~**Bloatware Safety Rating in App Detail**~~ ✅ pre-existing | App Info tag cloud surfaces "Bloatware · Safe / Replace / Caution / Unsafe" via `getBloatwareSafetyLabel(context, removalType)` (`AppInfoFragment.java:1206`) coloured by `ColorCodes.getBloatwareIndicatorColor`; tap opens `BloatwareDetailsDialog` which lists declared `dependencies` from the debloat-list JSON. Rating + dependencies surfacing both shipped; the audit row was stale. | Low |

### T8 — Profiles & Automation

Routine Operations is the #2 requested feature (21 reactions, Issue #61 [S03]). Muntashir himself published the full technical spec in that issue in 2020; implementation was deferred. NG should ship this if upstream continues to stall.

| Item | Description | Effort |
|------|-------------|--------|
| **Routine Operations / Scheduler** | Event-triggered profile execution: boot, custom interval, network available, charging, screen on/off, app foreground/background (full spec: Issue #61 [S03]) | High |
| **Saved Filter Presets** | Persist named filter configurations that auto-include all user apps, allow excluding specific user apps, and manually include specific system apps (Issue #1718 [S38]). Distinct from Finder — these are persistent main-list presets. | Medium |
| **Launcher Shortcuts for AM Features** | Configurable home screen shortcuts to up to 4 user-chosen AM functions (1-Click Ops, App Usage, Running Apps, Profiles, etc.). Android GSI Shortcuts API (Issue #660 [S32]). | Low |
| **Profile State Conditions** | Profiles with conditions (execute only if battery > X%, only between hours Y–Z) | Medium |
| **Schedule Result Notifications with Detail** | When a scheduled operation completes, include in the result notification *what was actually done*: e.g. "Backup complete: 47 apps, 2.3GB, 0 errors". Reference: SD Maid SE v1.7.x ([S112]) "Show what was cleaned in result notifications". Use `Notification.BigTextStyle` or Android 16+ `Notification.ProgressStyle` for finished work summary. | Low |
| **Searchable Schedule Tags** | Allow tagging schedules and routine operations (e.g. "weekly", "pre-update", "test"); search/filter by tag in scheduler view. Reference: Neo Backup v8.3.17 ([S114]) "Existing tags suggestion in the adding dialog". Reduces clutter in heavy-user scheduler dashboards. | Low |
| **KeepAndroidOpen Banner for Long Operations** | Display a "Keep this app open" persistent banner during long-running operations (full backups, batch ops, debloating); explains to users that backgrounding may pause work and shows estimated time remaining. Reference: Neo Backup v8.3.17 ([S114]) KeepAndroidOpen banner. Improves operation-completion rate on devices with aggressive battery management. | Low |
| ~~**Profile Import/Export**~~ ✅ 2026-05-02 | File-roundtrip path was already wired (`ProfilesActivity.mImportProfile.launch("application/json")` + `mExportProfile.launch(profile.name + ".am.json")`). New **Share as JSON** popup action (`action_share`) sends the same JSON via `Intent.ACTION_SEND` for one-tap cross-device transfer (Telegram / KDE Connect / email / Gmail draft) without a SAF round-trip. `BaseProfile.serializeToJson()` already produces the wire format consumed by `BaseProfile.fromPath`, so the receiving NG instance imports through the existing Import action verbatim. | Low |
| **Launcher Backup Restoration** | Support importing backup archives from competing launcher tools (Nova Launcher, Action Launcher, Lawnchair); restores icon grid layout, widgets, folders, and app organization within NG's App Manager view. Lawnchair 15 Beta 3 model ([S86]). Scope: launcher icon grid and widget placement only; does not include app installation. | Medium |
| **Intent Interceptor: Return Results** | Send activity results back to the original calling app after interception (ActivityInterceptor.java TODO) | Medium |
| **Operation Activity Log** | Persistent log of every operation AM has performed (freeze, backup, AppOps change, etc.); filterable by app, type, date (Issue #143 [S13], 3 reactions) | Medium |
| **Multi-Tag per App** | Allow assigning multiple named tags to each app; batch freeze, backup, profile, and filter operations target tag sets instead of hand-picked lists. Hail v1.10.0 model ([S65]). Schema decision required — tags stored in the Room `apps` table alongside existing `tracker_count`/`dangerous_perm_total` columns; many-to-many via a `app_tags` join table. | Medium |
| **URI / Intent Automation API** | Expose a documented `am://` URI scheme for triggering freeze, backup, and profile operations from external apps (Tasker, home screen shortcuts, quick-settings tiles); mirrors Hail v1.10.0's URI automation schema ([S65]). Prerequisite for the Tasker Plugin (Under Consideration). Pairs with T18 deep-link infrastructure — same URL-dispatch layer. | Medium |

### T9 — Privacy & Security

| Item | Description | Effort |
|------|-------------|--------|
| **Biometric App Lock** | BiometricPrompt-based lock for AM itself on app open (BiometricPrompt dep already present at 1.4.0-alpha04) | Low |
| **App-Ops Per-UID Control** | Allow setting app ops by UID (`--uid`) in addition to per-package; essential for shared-UID system packages where per-package mode is ambiguous (Issue #1863 [S37]). | Medium |
| **AppOps Quick-Toggle in List** | Add an inline allow/deny/default toggle chip directly on each AppOps row in the App Details AppOps tab; avoids opening a full dialog for simple permission mode changes. Reduces interaction depth for bulk AppOps editing sessions. Inure build106.5.1 model ([S66]). | Low |
| **Tracker Blocking (AppOps)** | One-click disable specific tracker components via `AppOps.setUidMode`; show which trackers are currently active. Three graduated blocking intensities: minimal (detect + report only), standard (disable known problematic SDKs), strict (disable all detected trackers); balances privacy vs. app compatibility. TrackerControl 2026040301 model ([S69]). | Medium |
| **Per-App Tracker Statistics Panel** | In App Details, show the count of detected trackers and their associated tracking companies (aggregated by organization, not just SDK name); source data from the εxodus database already embedded in-app. Informational regardless of blocking status; pairs with Tracker Blocking (AppOps). TrackerControl 2026040301 model ([S69]). | Low |
| ~~**Sort by Dangerous Permissions**~~ ✅ pre-existing | `MainListOptions.SORT_BY_DANGEROUS_PERMS = 18` is wired into `MainListOptions.SORT_OPTIONS_MAP` and consumed by `MainViewModel` (sort rule at `MainViewModel.java:658` — primary `dangerousPermGranted` desc, secondary `dangerousPermTotal` desc, mirrors `SORT_BY_TRACKERS` shape). Room v9 columns (`dangerous_perm_total` / `dangerous_perm_granted`) backing the sort are populated. The audit row was stale; closing it during the 2026-05-02 batch. | Low |
| **Privacy Dashboard Integration** | Surface Android 12+ permission usage history (timeline view) inside AM app details | Medium |
| **App Signing Key Change Alert** | Alert when the signing certificate of an installed update differs from the previously stored one | Medium |
| **Permission Policy Flags Display** | Surface `requestedPermissionsFlags` bits (system-fixed, user-set, review-required, preinstalled, installer-exempt) alongside each permission entry in the App Details permissions list. `PackageInfo.requestedPermissionsFlags` already available; pure UI addition. Inure build106.5.0 model ([S66]). | Low |
| **MiUI-Specific AppOps Mapping** | Surface MIUI extended op-codes with human-readable descriptions and full edit support for MIUI/Xiaomi devices. Conditional on `Build.MANUFACTURER.equals("Xiaomi")`; requires embedding a MIUI op-code lookup table. Inure build106.5.0 model ([S66]). | Low–Med |
| **Permission Change Monitor** | Notify when an installed app update gains new dangerous or sensitive permissions vs. the previously installed version; tap to deep-link to App Details permission list for review. Root/Shizuku not required — `PackageManager` broadcast + stored per-app permission snapshot suffices. Permission Manager X Perm Watcher model ([S70]). | Medium |
| **APK Signing Scheme Display** | Show all active APK signing schemes (V1 JAR, V2 APK Scheme, V3 key rotation, V4 incremental) in the App Details Signatures tab. `ApkSignatureSchemeV2Verifier` and `ApkSignatureSchemeV3Verifier` are already part of AOSP; scheme flags are readable without root. LibChecker 2.5.2 model ([S72]). | Low |
| **Permission Provider Attribution** | For each custom permission declared by installed apps, show which installed package provides (declares) that permission in the permissions list. Uses `PackageManager.resolvePermission()` with the declaring package name. Surfaces hidden cross-app permission dependencies that could persist after partial uninstalls. LibChecker 2.5.3 model ([S72]). | Low |
| **Package Visibility Analysis** | In App Details, surface: (a) whether the app holds `QUERY_ALL_PACKAGES` (can enumerate all installed apps), (b) its `<queries>` manifest entries (targeted package-visibility declarations), and (c) other installed apps that explicitly list this package in their own `<queries>`. Uses `PackageInfo.requestedPermissions` + manifest parsing — no root. Highlights apps that enumerate the full package list (privacy/attack-surface signal). Island v6.4 visibility-filter model ([S78]). | Low |
| **Install Source Attribution Detail** | In App Details, distinguish `getInitiatingPackageName()` (the app that triggered the install session) from `getOriginatingPackageName()` (the declared source); surface the installer's signing-cert digest (`getInstallInitiatingPackageCertificateDigest()`, API 34+). Detects cases where an APK's stated origin differs from the app that actually opened the `PackageInstaller` session. `PackageManager.getInstallSourceInfo()` (API 30+) ([S83]). | Low |

### T10 — i18n & Accessibility

| Item | Description | Effort |
|------|-------------|--------|
| **Translation Platform (Weblate)** | Self-host or use Hosted Weblate (model: Neo Backup [S20]); link from CONTRIBUTING.md | Low |
| **RTL Layout Verification** | Full test pass under Arabic/Hebrew locale; fix any mirroring or truncation issues | Medium |
| ~~**Plural String Audit**~~ ✅ 2026-05-02 | Sweep complete. Codebase already shipped 66 `<plurals>` blocks; the remaining `%d`-using format strings split into pluralizable (`main_status_showing_apps`, `main_status_all_apps`, `bar_chart_content_description`) and non-pluralizable (IDs/positions/range bounds: `pid_and_ppid`, `user_with_id`, `external_multiple_data_dir`, `vt_success`, `tag_dangerous_perms` "X of Y" composites, etc.). The three pluralizable strings are now `<plurals>` blocks consumed via `getQuantityString()`; orphan `selected_items_accessibility_description` removed. | Low |
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
| **Structured APK Editing Architecture** | When implementing T12 full APK editing, reference APKEditor v1.4.8's three-layer structure: manifest editing (XML parsing + validation), resource table editing (ARSC parsing), and dex/smali editing (baksmali roundtrip). Allows incremental rollout: resource table first, then manifest, then code. APKEditor v1.4.8 ([S89]) is the reference implementation in the OSS space. | High | — |
| **JADX Decompiler GUI Integration (Read-Only)** | Embed or link to JADX v1.5.5+ decompiler GUI for java source code viewing within App Details "Code" tab (read-only; no editing). JADX v1.5.5 offers cross-platform GUI, split-view mode for parallel viewing, and `.apks` support. External app launch or in-process WebView fallback ([S94]). Provides power users with one-click decompile without leaving NG. | High | JADX binary distribution |
| **Split APK (.apks) Installer Integration** | When opening a `.apks` or `.xapk` file in File Manager, auto-detect and parse the archive; surface split variants (arch, density, language) in a selection dialog; install selected splits alongside the base APK in a single `PackageInstaller` session. Mirrors SAI v4.3 model ([S25]); JADX v1.5.5 and Bundletool v1.18.3 both added `.apks` support signal ([S94], [S100]). | Medium | — |
| **Native Library Sizes in App Details** | In the App Details library tab, display the file size of each loaded `.so` alongside the library name; enumerate `ApplicationInfo.nativeLibraryDir` entries. Inure build107.0.1 model ([S66]). | Low | — |
| **Batch APK Installer from File Manager** | Allow selecting multiple APK/APKS files in the built-in File Manager and installing them in a single batch `PackageInstaller` session. Inure build107.0.0 model ([S66]). | Medium | — |
| **Force DEX Compile Optimization** | Add "Force Optimize" action in App Details triggering `cmd package compile -m speed <pkg>` (or `-m speed-profile` for profile-guided AOT); surfaces three profiles: `speed` (full AOT), `speed-profile`, and `quicken`; requires Root or Shizuku. Useful for apps that ended up in interpreter mode after sideloading or OTA. AppBooster (awesome-shizuku Miscellaneous section [S68]) model. | Low |
| **Large APK Pre-Install Warning** | When user attempts to install an APK > 150MB via File Manager or Intent Interceptor, show a warning dialog with file size, free space remaining, and an "Are you sure?" confirmation. Reference: SAI v4.5 ([S116]) "Added warning when installing huge (>150MB) apps". Prevents accidental installs of large packages on low-storage devices. | Low | — |
| **APKM/XAPK Format Support** | Extend File Manager and split APK installer to recognize `.apkm` (encrypted/unencrypted) and `.xapk` archive formats in addition to standard `.apks`. Reference: SAI v4.3 ([S116]) APKM support, partial XAPK with `.obb` known to need manual placement. Decoupling format-detection from install path keeps the Universal Installer clean. | Medium | — |
| **Per-Lib 16 KB Page-Alignment Indicator** | In the App Details native library tab, badge each `.so` entry with its 16 KB page alignment status. Non-aligned libs cause `dlopen()` failures on Android 15+ 64-bit kernels (including Pixel 9 and any device shipping with a 16 KB page-size kernel). Flags impacted apps in the main list for user action. LibChecker 2.5.2 model ([S72]); alignment check uses ELF `p_align` field, no root required. | Low | — |
| **App State Snapshot & Version-Diff Timeline** | On each install/update, record a snapshot: version code/name, signing certificate fingerprint, min/target SDK, ABI split list, declared permissions, and Build ID. Show a diff between any two snapshots in App Details under a new "History" tab. Detects silent cert rotations, ABI drops, and permission creep across update chains. LibChecker 2.5.3 model ([S72]); requires Room schema extension + per-app history table. | High | — |
| **APK URL Metadata Preview** | Analyze an APK from a direct download URL without fully downloading it: HTTP Range request fetches the ZIP central directory + compressed manifest; parse `AndroidManifest.xml`, signing cert, and ABI list; present summary in a bottom sheet before committing to a full download. LibChecker 2.5.3 model ([S72]). Useful for vetting sideloaded APKs from Obtainium-managed sources. | Medium | — |
| **Native Library Extraction** | Extract a selected `.so` file from an installed or locally-opened APK to the device's Downloads folder. Targeted at reverse-engineering workflows: extract the specific library, inspect ELF headers, compare ABI builds. LibChecker 2.5.3 model ([S72]); requires read access to the APK path (no root for installed apps; root for data partitions). | Low | — |

### T13 — Database & File System

Upstream lists "database viewer and editor" in Upcoming Features (Issue #14 [S11], 3 reactions).

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Database Viewer (upstream pull)** | Pull when upstream ships; read-only first | Low (pull) | Upstream |
| **Database Editor** | Write capability after viewer is stable | High | DB Viewer |
| **DocumentsProvider** | Implement proper `DocumentsProvider` for third-party file manager access (FmProvider.java TODO, Issue #516 [S06], 7 reactions) | High | — |
| **File Manager Compression** | ZIP/tar archive creation and extraction in built-in file manager | Medium | — |
| **File Hash Display** | SHA-256/MD5 display for files viewed in file manager | Low | — |
| **File MIME Type Recognition** | Detect and display MIME types for files in the File Manager (e.g. `application/vnd.android.package-archive` for APK). Termux v0.118.3 added basic MIME type recognition in its ContentProvider ([S91]); NG can apply similar logic in file browsing contexts. | Low | — |

### T14 — Terminal & Code Editor Polish

Upstream's "more advanced terminal emulator" is an upcoming feature. NG will pull rather than reimplement.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Advanced Terminal (upstream pull)** | Pull upstream's terminal improvements when they ship | Low (pull) | Upstream |
| ~~**Code Editor i18n**~~ ✅ 2026-05-02 | Hardcoded "tabs"/"spaces" suffix on the toolbar now reads from `R.plurals.editor_tab_size_option_{tabs,spaces}` so locales pluralise correctly. Picker titles + go-to-line labels are also `R.string` resources. | Medium | — |
| ~~**Code Editor Tab Size Setting**~~ ✅ 2026-05-02 | Tap the indent-size toolbar button → popup with **2 / 4 / 8** options (configurable per indent-mode). Wires to `mEditor.setTabWidth(n)` and refreshes the label live. | Low | — |
| ~~**Code Editor Language Display**~~ ✅ 2026-05-02 | Tap the language toolbar button → popup listing all seven tmLanguage-backed languages bundled in `assets/languages/` (java / json / kotlin / properties / sh / smali / xml). Picking switches the highlighter and persists via new `CodeEditorViewModel.setLanguage()`; the indent label re-renders against the new language's `useTab/useSpace` default. | Low | — |

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
| **Dynamic Font Size Compliance** | Full compliance with Android's font scaling up to 200%. Audit dashboard progress cards, app cards, and detail pages so they handle larger fonts without truncation or clipping. Pattern: SD Maid SE v1.7.x ([S112]) "make dashboard progress cards handle larger font sizes" PR series. | Medium |
| **TalkBack/Screen Reader Audit** | Comprehensive Compose Semantics audit: every clickable, toggleable, and image element has `contentDescription` or `semantics { role = ... }`. Validate with Google's TalkBack 6.1.1 ([S119]) and Compose UI tests via `assertIsDisplayed()` + `onNodeWithContentDescription()`. Wire announcement events for batch operation progress and result. Required for IAAP / WCAG 2.1 AA compliance. | Medium |
| **Reduced Motion Support** | Respect system "Remove animations" toggle (`Settings.Global.TRANSITION_ANIMATION_SCALE`); skip page transitions, ripple animations, predictive back when set to 0. Compose: detect via `LocalDensity` + `Settings.System` query at composition root. Reference: PodAura v3.2 ([S118]) Material 3 Expressive accessibility patterns. | Low |
| **Dyslexia-Friendly Font Option** | Add OpenDyslexic or Atkinson Hyperlegible as optional Settings > Appearance > Font. Reference: Markor v2.15.0 dyslexia-friendly font ([S117]). Bundles ~250KB; gate behind opt-in to avoid bloating non-users. | Low |

### T18 — Overlay Management & Deep Links

Low-effort, high-usability items gated on platform API availability or upstream pull.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Per-App Overlay Management** | Pull upstream v4.0.1's "Overlays" tab in App Details: enable/disable per-app system overlays (root/ADB, Android 8+). Upstream landed it — pull rather than reimplement ([S39]). | Low (pull) | Upstream v4.0.1+ |
| **app-manager:// Deep Link** | Support `app-manager://details?id=<pkg>&user=<uid>` deep links; enables Quick Settings tiles, widgets, and Tasker actions to open specific app's detail page. Upstream ships this in v4.0.x. | Low (pull) | Upstream |
| **Unfreeze on Shortcut Launch** | If user taps a home-screen shortcut for a frozen app, offer to unfreeze temporarily; auto-re-freeze when screen is locked. Pull upstream v4.0.1 model ([S39]). Pairs with T8 "Saved Filter Presets" for freeze-group workflows. | Low (pull) | Upstream v4.0.1+ |
| ~~**Certificate Hash in App Detail**~~ ✅ 2026-05-02 | New "Sign · SHA-256 21:5F…38:6C" chip in the App Info tag cloud surfaces the colon-separated upper-case fingerprint with first/last-4 truncation. Tap opens a `MaterialAlertDialog` showing the full digest plus a one-tap **Copy** button so users can paste it straight into AppVerifier or compare against `apksigner verify --print-certs` output. Computed worker-side in `AppInfoViewModel.computeSigningCertSha256()` and exposed as `TagCloud.signingCertSha256`. Obtainium v1.3.0 / [S51] precedent. | Low | — |

### T19 — Package-Aware Storage Analysis

App-centric storage views informed by SD Maid SE's CorpseFinder ([S24]) and UAD-NG's description-search model ([S67]). NG will not replicate SD Maid SE's full storage engine — scope is limited to per-app storage panels and post-uninstall cleanup that naturally extends the existing App Details view. Items in this tier gate on storage access permissions or root.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **App Details Storage Panel** | In App Details, show APK size, split sizes, installed data, cache, OBB, external data, backup archive sizes, and orphan leftovers in a single collapsible panel. SD Maid SE CorpseFinder model ([S24]). | Medium | — |
| **Leftover Detection After Uninstall** | After uninstalling an app, scan for orphan directories (`Android/data/<pkg>`, `Android/obb/<pkg>`, root-accessible `/data/data/<pkg>` stubs) and offer one-tap cleanup. SD Maid SE CorpseFinder model ([S24]). | Medium | Root or `MANAGE_EXTERNAL_STORAGE` |
| **APK Duplicate Finder** | Find multiple copies of the same package/version APK across Downloads, backup dirs, and SD card by matching package name + signing cert + version code; flag in File Manager and backup list. SD Maid SE deduplicator model ([S42]). | Medium | File access |
| **Backup Duplicate Cleaner** | Detect the same package + version + variant backed up more than once across backup roots; offer to delete redundant copies. Extension of Backup Retention Policy (T6). | Low | T6 Retention Policy |

### T20 — Performance & Profiling (System-Level)

Advanced users and developers need visibility into app runtime behavior and system performance impact. Perfetto v54.0 and LeakCanary v3.0+ signal emerging OSS maturity in this space.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Perfetto System Trace Export** | Integrate Perfetto v54.0 tracing infrastructure on Android 11+ to capture system-wide traces (CPU scheduling, I/O, frame pacing, memory allocations) filtered to a target app and its dependencies. Export trace as `.pbtxt` or Perfetto UI deep-link (https://ui.perfetto.dev). Expose as "Export trace" action in App Details (requires ADB or Shizuku); save to Downloads. Target power users and developers; non-expert users directed to device Settings > Developer options builtin Perfetto UI. Perfetto v54.0 ([S97]). | High | ADB/Shizuku or direct tracefs access |
| **LeakCanary Leak Detection Wrapper** | Optional dependency: when user enables "Memory profiling" in developer settings, attach LeakCanary v3.0+ as an agent to the target app's process to detect heap leaks during interactive testing. Surface detected leaks in a new "Leaks" tab in App Details with heap dump access. Non-intrusive; only activates if explicitly enabled. Requires debuggable app or root. LeakCanary v3.0-alpha series ([S96]); high-risk for stability; gate on opt-in developer mode. | High | LeakCanary binary, ADB/Shizuku, debuggable app |
| **CPU Profiler via simpleperf** | Expose simpleperf (LLVM-based CPU profiler, built into Android SDK) as a "Record CPU profile" action in App Details. Capture call stacks during a time window; export as flame graph SVG or callgrind format for analysis in desktop tools (Speedscope, kcachegrind). Gate on ADB/root/Shizuku availability. | High | simpleperf binary, ADB/Shizuku |
| **Memory Allocations Inspector** | Capture heap allocations for a target app (dumpsys meminfo, hwui memory, native memory stats) and surface in a new panel in App Details. Overlay native lib leaks (detected via ELF symbols and linker audit) onto the allocation breakdown. Signals memory pressure and leaks without requiring full Perfetto traces. | Medium | — |

### T21 — UI/Design Polish & Premium Feel

NG is feature-rich but visually dated compared to premium OSS exemplars (Auxio, Now in Android, ProtonMail, Mullvad). Modernizing the visual language and interaction patterns without bloating the codebase is a focus. Material 3 Adaptive is table-stakes for 2026 Android apps.

| Item | Description | Effort | Dependency |
|------|-------------|--------|------------|
| **Material 3 Adaptive Layouts for Tablets** | On Android 12+ (API 31+), use `foldable` and `posture` APIs + Material 3 Adaptive to automatically switch to two-column layouts on tablets, foldables, and landscape mode. Apply to: App List (detail sidebar), Settings (detail pane), Backup/Restore (list + log), Finder results. Reference: Mullvad VPN v2026.4 ([S110]) and Now in Android v0.1.1 ([S108]). Upstream-first pull when available; NG implements if not. | High | Material 3 Adaptive library (androidx.compose.material3.adaptive) |
| **Fast List Rendering for 10k+ Apps** | Audit and optimize app list rendering for devices with large installed app counts (corporate, developer devices can exceed 5k–10k apps). Implement virtualization + incremental search filtering. Reference Auxio v4.0.10's "Musikr" library scan speed ([S109]): native tag parsing + on-device caching reduces initial load from minutes to seconds. Apply same principles to APK metadata parsing. | High | LazyColumn optimization + caching strategy |
| **Landscape-Aware Density Overrides** | On landscape orientation or narrow screens, dynamically adjust list item density (compact vs. normal) and action bar layout. App card can hide less-critical metadata (version code, minSdk) in ultra-compact mode; surface as expandable chip on tap. Maintains usability on small screens and tablets. | Medium | Compose state management |
| **Unread/Attention Badges** | In the app list, add small circular badge counters to apps with pending operations: e.g. "3" badge if app has 3 disabled services, or "!" badge if app needs permission grant. Reference: Now in Android v0.1.1 ([S108]) unread badge pattern. Provides at-a-glance actionability. | Low | Compose Badge component |
| **Undo/SnackBar for Destructive Ops** | When user disables, uninstalls, or clears data for an app, show a SnackBar with "Undo" action for 2–5 seconds before commit. Reduces accidental data loss anxiety. Reference: Now in Android ([S108]) undo bookmark removal. Requires transactional batching of operations. | Medium | Transactional operation model |
| **Discreet/Generic App Icon Mode** | In Settings > Appearance, add option to use a generic system-colored icon (rounded square, NG logo minimal) instead of the distinct AppManagerNG icon. Useful for privacy-conscious users who want the app to blend in on home screen. Reference: ProtonMail v7.9.5 discreet icon mode ([S111]). Low-effort: ship 2–3 icon variants + selection spinner. | Low | Asset variants |
| **In-App Language Selector** | On Android 13+, add Settings > Localization > "App Language" selector to override system locale *per-app* only. Allows user to keep system in English but use NG in their preferred language without system-wide impact. Reference: Mullvad VPN v2026.4 ([S110]). Requires `LocaleCompat.setLocales()` (API 33+) + graceful fallback. | Medium | androidx.appcompat LocaleCompat |
| **Custom Actions & Batch Operations** | Allow power users to define custom shell commands/Tasker actions that run on a selected set of apps. E.g. "Uninstall + Clear Cache for all Google apps" one tap. Reference: Droid-ify v0.7.1 custom actions ([S104]). Requires a safe script-sandboxing model + audit logging. Non-trivial security review needed. | High | Script sandbox + Tasker IPC |
| **Improved Dark Mode + Material You Integration** | Ensure dark mode respects device's Material You color palette (accent, primary, tertiary colors from wallpaper). NG already targets dark by default; audit Compose theme setup to ensure `dynamicColor = true` and color harmonization. Reference: Auxio v4.0.10 Material Design refresh ([S109]). | Low | Audit + theme sync |
| **Smooth Page Transitions & Animations** | Add predictable, spring-eased transitions between app detail, settings, and search results pages. Reduce motion for accessibility. Polish gesture responses (long-press, swipe-to-dismiss) with haptic feedback. Reference: Now in Android v0.1.1 ([S108]) predictive back animation support. Compose animation APIs already handle this elegantly. | Medium | Compose Animation, haptic feedback |
| **Material 3 Expressive Migration** | Adopt Material 3 Expressive (the next iteration of Material You) for shape system, motion choreography, and component variants (FilledTonal buttons, ExtraLarge corner radii, expressive typography). Reference: PodAura v3.2 ([S118]) shipping "More Material 3 Expressive" as a tracked feature. Migrate incrementally to avoid disruptive UI churn. Pin Compose BOM to a version that ships expressive variants. | Medium | Compose Material3 expressive components |
| **Compose Performance: Stable VM States** | Audit ViewModel state holders to ensure all exposed types are `compose-compiler-stable` (annotated `@Stable` / `@Immutable`); avoid `MutableList` / non-data classes in state. Reduces unnecessary recompositions in app list and detail screens. Reference: Neo Backup v8.3.17 ([S114]) "Make VM-states compose-compiler-stable" PR series. Measurable perf win for 5k+ app devices. | Medium | Compose compiler metrics, baseline profiles |
| **Launcher Shortcuts (Pinned Schedules)** | Allow users to pin a frequent NG schedule (e.g. "Backup all user apps", "Force-stop top 10 most-used") to home screen as a shortcut. Tap = run schedule directly. Reference: Neo Backup v8.3.17 ([S114]) "Launcher shortcuts support for running schedules". Uses `ShortcutManager` API; supports Android 7.1+. | Low | ShortcutManager, schedule export model |
| **Backup Sharing Button** | One-tap share button to export and share a backup archive directly to another app via `Intent.ACTION_SEND`. Reference: Neo Backup v8.3.17 ([S114]). Useful for quick app migrations between phones. Encrypted backups remain encrypted; recipient needs same key. | Low | StorageAccessFramework |
| **Material 3 Result Notifications** | When a long-running operation (backup, batch op, install) completes, show what was done in the result notification using `Notification.ProgressStyle` (Android 16+) or expanded MessagingStyle. Reference: SD Maid SE v1.7.x ([S112]) "Show what was cleaned in result notifications". Bridges work-completion to user awareness without opening the app. | Low | Notification.ProgressStyle (covered T5 row) |



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
| ~~**android-debloat-list Safety Ratings in App Detail**~~ | ~~Surface UAD-NG removal rating ("safe", "unsafe", "expert", "untested") directly in the App Details page for system apps.~~ **Promoted to T7 (Next).** AM v4.0.5 shipped "display unsafe bloatware info" in its debloater ([S01]), establishing upstream precedent. Low-effort bundle path confirmed viable. |
| **KernelSU as Third Root Provider** | ~~Promoted to T5 (Next).~~ KernelSU v3.2.3+ `adb root` pathway has sufficient signal (~10 M users, `libsu`-compatible `su` binary) to warrant explicit detection in the capability wizard. See T5 "KernelSU Detection in Capability Wizard" ([S56]). |
| **Android 17 Advanced Protection Mode (AAPM) Integration** | `AdvancedProtectionManager` API (Android 17, API 37 [S53]) lets apps detect if the user has opted into AAPM. NG could surface "Device in Advanced Protection Mode" in the Privacy & Security section and automatically restrict high-risk operations (e.g. USB-debugging auto-pair, unknown-source installs) when active. Gated on minSdk raise to 37. |
| **JADX Plugin API for Analysis Extensions** | JADX 1.5.0+ ships an external plugin ecosystem ([S60]); scripts are now loadable as ZIP artifacts. Could expose custom class detectors (e.g. tracker pattern matching, hardcoded-secret detection) as user-installable NG analysis plugins. Architecture decisions: plugin trust model, sandboxing, GPL-compatible licensing chain. |
| **DDG Tracker Radar as Secondary Tracker Source** | TrackerControl 2026040301 ([S69]) integrates DuckDuckGo Tracker Radar (mobile-specific tracker database) alongside Exodus definitions for broader coverage. DDG Tracker Radar data is CC-BY-NC-SA — the Non-Commercial clause is incompatible with GPL-3.0 redistribution without explicit licensing. **Do not bundle DDG Tracker Radar data until legal clearance is obtained.** Alternative: contribute new SDK signatures upstream to the Exodus database (MIT-licensed), which NG already ships. |
| **Hardware Key Attestation Panel** | Surface KeyMint version, attestation level (software / TEE / StrongBox), and full certificate chain in an App Details or Device Security panel. Requires Shizuku for device-unique-ID attestation; falls back to software attestation without elevated access. KeyAttestation v1.8.4 model ([S73]). Gated on Shizuku T5 integration; architectural decision on placement (per-app vs. device-level) unresolved. |
| **Non-Root APK Module Injection Framework** | Architectural exploration: model a non-root Xposed-like framework (e.g. LSPatch v0.6 or Magisk modules) to selectively inject code into managed apps *without root* — e.g. to hook `PackageManager.getInstalledPackages()` for advanced filtering, or to intercept `Activity.onCreate()` for custom UI overlays. Would require NG to ship a companion Zygisk module or implement an embedded LSPosed core. Significant risk: module crashes can cascade; SELinux denies are non-trivial to debug. **Blocked on architecture consensus and team capacity.** LSPatch v0.6 (archived) was a reference; ZygiskNext v1.3.4 shows current non-root innovation ([S92], [S77]). |
| **Android 15 App Archiving Support** | `PackageInfo.isArchived` (API 35) flags apps in the platform's "archived" state — APK removed, icon + user data preserved, auto-restores on launch. `ArchivedPackageInfo` holds minimal metadata for archived packages ([S82]). NG could surface "Archived" as a distinct app state in the main list (badge alongside installed/disabled/frozen), add Archive and Unarchive actions in App Details, and include archived apps in Finder results. No root required — any `REQUEST_INSTALL_PACKAGES` holder can archive. Blocked on API 35+ conditional activation; NG's minSdk 21 means the feature targets only Android 15+ devices. |
| **Frida-Based Runtime Hook Tracing** | Architectural exploration: integrate Frida v17.9.4+ as an optional advanced-user feature to dynamically hook and trace app runtime behavior (method calls, return values, SELinux denials, system service interactions) without static analysis. Frida Server can run on rooted or ADB-accessible devices; NG could expose a "Record trace" action in App Details that spawns a Frida script targeting the selected package and streams the hook output to an in-app log viewer. High-risk: Frida gadgets and scripts require careful sandboxing and versioning; incompatible Frida client/server versions can crash. **Blocked on architecture consensus, security review, and team capacity.** Frida v17.9.4 ([S93]) and JADX v1.5.5 decompiler integration would unlock post-trace source-to-trace mapping workflows. |

---

## Iter-18 Research Additions (2026-05-03)

Cross-cutting research deltas mined from KernelSU v3.2, Magisk v30.7, Shizuku v13.6.0, Termux v0.118.3, Apktool v3.0.2, JADX v1.5.5, APKEditor v1.4.7-8, Hail v1.10.0, Inure build107, Material Files v1.7.4, SD Maid SE v1.7.2-rc0, UAD-NG v1.2.0, Neo Backup 8.3.17, and Android 16/17 platform docs. Each row carries the home-theme tag in brackets and tier verdict; Now/Next/Later items will be migrated into their themes during normal merging — this section is the working ledger that the next code-bearing iteration draws from.

| Item | Description | Theme | Tier |
|------|-------------|-------|------|
| **Shizuku Trusted-WLAN Auto-Start Awareness** | Shizuku v13.6.0 added rootless auto-start without root on Android 13+ via a trusted-WLAN allowlist managed inside Shizuku itself ([S121]). NG's "Operating Mode → Shizuku not running" hint should detect Shizuku ≥13.6.0 and surface a "Configure auto-start in Shizuku" deep-link button (Intent `moe.shizuku.privileged.api/.AUTO_START`) so users don't have to relaunch Shizuku manually after every reboot. Effort 2/5; high UX win for the rootless majority. | T5 | **Now** |
| **Magisk `--drop-cap` Opt-In Surface** | Magisk v30.7 inverted `--drop-cap` semantics — capability dropping is now opt-in per-app, not opt-out ([S122]). NG's Privilege Health-Check screen should detect Magisk version, parse `magiskpolicy --live` output, and report whether the AppManagerNG process itself is running with the full or dropped capability set. Effort 2/5; ties into existing `Os.prctl(PR_CAPBSET_READ)` plumbing. | T9 | **Next** |
| **KernelSU Sulog & Seccomp Status** | KernelSU v3.2 ships `sulog` (kernel-side su event log) and a per-app seccomp filter status surface ([S123]). When NG detects KernelSU as the root provider, Privilege Health-Check should pull sulog tail (`/data/adb/ksu/log/sulog`) and the seccomp filter mode (`prctl PR_GET_SECCOMP`), display recent denials, and link out to KernelSU Manager. Effort 3/5. | T9 | **Next** |
| **Android 16 `SDK_INT_FULL` Plumbing Audit** | Android 16 introduced `Build.VERSION.SDK_INT_FULL` and `Build.getMinorSdkVersion()` so apps can branch on Q/Q-prime minor releases ([S124]). NG has ~120 `Build.VERSION.SDK_INT >=` call-sites; a single utility (`AndroidUtils.sdkAtLeast(int major, int minor)`) should centralize comparisons and let new code target Android 16.1/16.2 quirks without bloating call-sites. Effort 2/5; pure refactor, zero behavior change at v0 minor. | T2 | **Next** |
| **JobScheduler Quota Stop-Reason Surfacing** | Android 16 tightened JobScheduler quotas; `WorkInfo.getStopReason()` and `JobScheduler#getPendingJobReasonsHistory()` (API 34/35/36) now expose the precise reason a scheduled run was deferred or killed ([S124], [S125]). NG's Schedules screen and backup-result detail notification should surface the reason ("STOP_REASON_QUOTA", "STOP_REASON_DEVICE_STATE", "STOP_REASON_PREEMPT") so users can act on quota throttling instead of silently retrying. Effort 2/5; pairs with already-shipped Battery Optimization toggle. | T6 | **Now** |
| **Termux SELinux Context Display** | Termux v0.118.3 surfaces `SE_PROCESS_CONTEXT`, `SE_FILE_CONTEXT`, and `SE_INFO` in its environment ([S126]). NG's App Info "Security" section should display the same trio per-app — sourced via `am.getRunningAppProcesses()` cross-checked with `/proc/<pid>/attr/current` — so power users can audit SELinux domain assignments alongside permissions and AppOps. Effort 3/5. | T9 | **Later** |
| ~~**Termux 16k Page-Size Device Indicator**~~ ✅ shipped 2026-05-02 | Termux v0.118.3 detects 16k page-size devices via `getconf PAGESIZE` and adjusts mmap/exec strategy ([S126]). NG already has a per-lib 16KB-alignment indicator (iter-11) but lacks a *device-side* indicator. Add a one-line "Device page size: 4 KB / 16 KB" row in App Info → Native libs section so 16k-incompatible libs visibly explain the warning instead of looking spurious on 4k devices. Shipped: new "Device page size" row in App Info under Primary ABI, populated via `Os.sysconf(_SC_PAGESIZE)` with explicit 4 KB / 16 KB / Other / Unknown labels. | T19 | **Now** |
| **APKEditor `--smali-comment-level basic`** | APKEditor v1.4.7 added a smali comment-level option (`none`, `basic`, `verbose`) and a top-level `-remove-annotation` flag for stripping `@Nullable`/`@NotNull`/`@RequiresApi` annotations during decode ([S127]). NG's APK Editor flow should expose both as user-toggleable options; "basic" is the new sane default for casual users (smaller diffs, faster recompile) and `--remove-annotation` materially reduces noise when comparing decoded smali across builds. Effort 2/5. | T12 | **Next** |
| **Apktool 3.0.2 "Remastered" Migration** | Apktool 3.0.2 ("Apktool Remastered") shipped Feb 2026 — substantial internal refactor (`ResChunkPullParser`, `BinaryDataInputStream`), headless-server build support, improved `ResAttribute` value formatting, Guava 33.5, commons-lang 3.20 ([S128]). NG's eng-debt iter-12 entry to migrate from Apktool 2.x → 3.x is now actionable; pin to `3.0.2` in `app/build.gradle` and re-test split-APK rebuild + signing pipeline. Effort 2/5; eng-debt closure. | Eng-Debt | **Now** |
| **JADX 1.5.5 `.apks` Ingestion + UI Zoom** | JADX v1.5.5 added `.apks` (split APK) ingestion via `--input-apks` and a UI zoom factor option ([S129]). NG's planned JADX integration (T12 iter-15) should pass split APK paths through the new `.apks` entry-point instead of stitching APK Editor's merged-APK output. Effort 1/5 once T12 JADX work begins. | T12 | **Later** |
| **JADX 1.5.5 FlatLaf CJK Composite Font** | JADX v1.5.5 switched to FlatLaf composite-font fallback so CJK glyphs render correctly without the Noto-CJK system pack ([S129]). When NG ships an embedded JADX viewer, set the same FlatLaf composite-font UIManager defaults so Chinese/Japanese/Korean class names render in the decompile pane. Effort 1/5. | T10 | **Later** |
| **Hail-Style Auto-Freeze QuickSettings Tile** | Hail v1.10.0 added a system QuickSettings tile that triggers a one-tap freeze of the configured app set ([S130]). NG already ships freeze infrastructure; expose a `TileService` (manifest declared, `android:icon="@drawable/ic_ac_unit"`) that runs the user's saved freeze profile in one tap from the notification shade. Effort 2/5. | T8 | **Now** |
| **Hail-Style Digital-Assistant Launch** | Hail v1.10.0 registered as a Digital Assistant target so long-press home / power gesture launches Hail's freeze flow ([S130]). NG could ship a dedicated `android.intent.action.ASSIST` activity that surfaces a fast-action sheet (Force Stop, Freeze, Open App Info) for whatever app is currently in the foreground when the assistant is invoked. Effort 3/5; novel. | T8 | **Later** |
| **KernelSU App Profile Awareness** | Hail v1.10.0 reads KernelSU's per-app "App Profile" (uid, gid, capabilities, SELinux domain) and respects it ([S130]). NG's Privilege Health-Check should also display the active App Profile when launched under KernelSU and warn when capabilities have been reduced below NG's expected set. Effort 2/5; pairs with [S123]. | T9 | **Next** |
| ~~**Inure-Style Native-Lib Size Surface**~~ ✅ shipped 2026-05-03 | Inure build107 displays per-`.so` sizes in App Info → Native libs ([S131]). Verified already shipped in NG: `NativeLibraries.NativeLib.toLocalizedString()` (both ELF + InvalidLib variants) prepends `Formatter.formatFileSize(context, getSize())` to the description rendered in `AppDetailsOtherFragment` line 330. No code change required — auditing closed the row. | T19 | **Now** |
| **Inure-Style AppOps IGNORE Flag** | Inure build106.5.0 added IGNORE as a third state alongside ALLOW/DENY in the AppOps dialog ([S131]). NG's AppOps editor currently exposes only ALLOW/DENY in the toggle UI; adding IGNORE (which silently no-ops the op without throwing `SecurityException`) matches platform behavior and is the correct option for ops that misbehaving apps would otherwise crash on if DENY-ed. Effort 1/5. | T9 | **Now** |
| **Inure-Style Batch APK Installer** | Inure build107.0.0 added a Batch Installer that consumes multiple APK paths in one operation ([S131]). NG already has a Batch Operations menu but split-APK / multi-APK install requires per-package interaction; a single "Install all selected" action that streams `Session.commit()` calls would close this gap. Effort 2/5. | T11 | **Next** |
| ~~**Inure-Style Removed Persistent Search History**~~ ✅ already-compliant 2026-05-02 | Inure build107.0.1 removed persistent search-term storage as a privacy win ([S131]). NG's main app list and Finder both persist recent searches indefinitely; either move to a session-only in-memory cache or expose a "Remember search history" preference (default off) to match Inure's posture. Audit result: NG's `SearchView` usage is session-only in memory — `recent_search`/`searchHistory`/`SearchRecentSuggestionsProvider` grep all return zero hits. No persistent storage exists; nothing to remove. | T9 | **Now** |
| **Material Files Checksum Properties Tab** | Material Files v1.7.2 added a Checksum properties tab (MD5/SHA-1/SHA-256/SHA-512) inside the per-file properties dialog ([S132]). NG's APK Info already shows SHA-256 of the APK; broaden the same checksum-row display to NG's File Manager Properties dialog for arbitrary files (not just APKs). Effort 1/5. | T13 | **Next** |
| **Material Files User-Trust for Self-Signed WebDAV Certs** | Material Files v1.7.2 marks user-installed certs in the Android system credential store as trusted for WebDAV connections with self-signed certificates ([S132]). NG's planned WebDAV/SMB backup destination (T6 iter-12) should consume `KeyChain.getCertificateChain()` for user-installed certs by default rather than rolling its own trust store, matching Material Files' UX. Effort 2/5. | T6 | **Next** |
| **SD-Maid-Style Auto-Fix Battery Optimization** | SD Maid SE v1.7.2-rc0 auto-fixes battery optimization for the scheduler via root or ADB ([S133]). NG now silently whitelists the app via `DeviceIdleManagerCompat.disableBatteryOptimization()` when user has `DEVICE_POWER` permission instead of opening system dialog; UX matches SD-Maid. | T20 | ~~**Now**~~ ✅ **shipped 2026-05-02** |
| **SD-Maid-Style Warn-Before-Volume-Scan** | SD Maid SE v1.7.2-rc0 added a warning before scanning an entire storage volume ([S133]). NG's Storage Analysis (T19) and File Manager search both happily walk the entire `/storage/emulated/0` tree without warning; a "This will take ~N minutes" pre-flight using `StorageStatsManager.getTotalBytes()` over device speed would prevent surprise UI freezes. Effort 1/5. | T19 | **Next** |
| **UAD-Style Auto-Fetch Debloat Definitions** | UAD-NG v1.2.0 fetches the latest debloat package definitions on launch over HTTP, requiring no app update for list changes ([S134]). NG's bloatware-rating data is currently bundled at build time; an opt-in (default off) "Update debloat definitions on launch" preference fetching from a pinned GitHub raw URL with checksum verification would let users get rating updates without waiting for NG releases. Effort 2/5; matches T7 philosophy. | T7 | **Next** |
| **UAD-Style Cross-User Package State Detection** | UAD-NG v1.2.0 added cross-user package state verification — checks whether a package is installed/disabled/uninstalled per-user, not just for the current profile ([S134]). NG already has multi-user support but Finder filters and the main list don't surface "installed for user 0 only / installed for work profile only / split state across users". Effort 3/5. | T7 | **Next** |
| ~~**UAD-Style Selectable & Copyable Descriptions**~~ ✅ shipped 2026-05-03 | UAD-NG v1.2.0 made every package description in its UI selectable and copyable ([S134]). Shipped: `item_app_details_appop.xml` and `item_app_details_perm.xml` mark `perm_description`, `perm_protection_level`, `op_mode_running_duration`, `op_accept_reject_time` as `textIsSelectable="true"`. Long-press now reveals the system Copy affordance directly on the list rows. | T10 | **Now** |
| ~~**UAD-Style Copy-Error Button on Failure Toasts**~~ ✅ shipped 2026-05-03 (foundation) | UAD-NG v1.2.0 added a one-tap "Copy error" button on every error toast ([S134]). Shipped: new `UIUtils.displayCopyableErrorDialog(context, title, message)` helper backed by `ClipboardUtils` (already handles >1MB error blobs via FileProvider URI fallback). Foundation only; high-traffic toast failure sites migrate in a follow-up commit. | T4 | **Now** |
| **Neo-Backup-Style Backup Sharing Button** | Neo Backup 8.3.17 added a one-tap "Share" button on completed backup items ([S135]). NG's Backups screen has no per-item Share action; wiring `ACTION_SEND_MULTIPLE` over the encrypted backup-archive Uri (with a strong-warning sheet about archive contents containing app data) closes a frequent feature request. Effort 2/5. | T6 | **Next** |
| **Neo-Backup-Style Existing-Tag Suggestions** | Neo Backup 8.3.17 suggests existing tags as you type in the tag-add dialog ([S135]). NG's multi-tag system (iter-8) does not autocomplete from prior tags; an `AutoCompleteTextView` adapter populated from `TagDao.getAllTags()` is a low-effort polish. Effort 1/5. | T8 | **Now** |
| ~~**Neo-Backup-Style KeepAndroidOpen Banner**~~ ✅ shipped 2026-05-02 | Neo Backup 8.3.17 surfaces a "Keep Android open during long backups" banner when a backup operation begins on Android 14+ to combat aggressive Doze/standby kills ([S135]). NG's backup operation should display the same banner under Android 14+ given the new JobScheduler quota tightening. Shipped: long Toast warning fired from `BackupRestoreDialogFragment.startOperation()` whenever `MODE_BACKUP` runs on `SDK_INT >= UPSIDE_DOWN_CAKE` (API 34+). | T6 | **Now** |
| **Glance Widget Parity Audit** | androidx.glance enables Compose-style home-screen widgets ([S136]). NG ships zero home-screen widgets today; an exploration pass to evaluate a 1×1 "Last backup status", 2×1 "Foreground app freeze toggle", and 4×1 "Schedule next-run" widget set would unlock a use-case category competitors (Hail, Neo Backup) already cover. Effort 3/5; UC. | T8 | **Under Consideration** |
| **Repo-Rename Detection for Upstream Pin** | Obtainium v1.4.x added GitHub repo-rename detection so its source watcher follows owner/name changes ([S121]). NG's eng-debt register pins the upstream-source URL to `MuntashirAkon/AppManager`; a CI check that hits the GitHub API and asserts the repo still resolves to that name (and otherwise opens an issue) would prevent silent breakage of the upstream-sync flow. Effort 1/5. | Eng-Debt | **Next** |

---

## Premium Polish Track (v0.4.x → v0.7.x)

Parallel work-stream off the Onboarding theme (v0.4.0). The four-step rollout drafted in `design/plan/3-rollout.md` migrates AppManagerNG from the existing M3 token plane to a refined v2 token plane (calmer surfaces, tighter typography, pill-shaped FAB/search, outlined card variants). Each phase is independently revertable.

| Phase | Status | Scope |
|-------|--------|-------|
| ~~**v0.4.x Foundation**~~ ✅ shipped 2026-05-03 | Done | `app/src/main/res/values/{colors,dimens,themes}-v2.xml` copied verbatim from `design/impl/`. New pref `PREF_PREMIUM_PREVIEW_BOOL` (default OFF). `Prefs.Appearance.getAppTheme()` routes to `AppTheme.V2` / `AppTheme.V2.Amoled` when enabled, classic `AppTheme` / `AppTheme.Black` otherwise. Toggle exposed at Settings → Appearance → "Preview new design (BETA)". `getTransparentAppTheme()` deferred — transparent surfaces stay classic until v0.5.x. No layout files modified. |
| **v0.5.x Top-5 Surface Migration** — Phase 1 ✅ shipped 2026-05-02 | In Progress | Wired `activity_main_v2.xml` and `item_main_v2.xml` behind the toggle. MainActivity and MainRecyclerAdapter conditionally inflate v2 layouts. Phase 2: AppDetails / AppUsage / Settings surfaces. |
| **v0.6.x Default Flip** | Pending | Default `PREF_PREMIUM_PREVIEW_BOOL` to `true`; expose a "Use legacy design" escape hatch in Appearance for one release. |
| **v0.7.x Toggle Removal** | Pending | Delete the toggle, drop classic `AppTheme*` definitions, retire `colors-v2.xml`/`themes-v2.xml` filenames in favor of canonical `colors.xml`/`themes.xml`. |

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
| ~~`Utils.java` (×5)~~ ✅ 2026-05-02 | FIXME (resolved) | Soft-input/service-flag/activity-flag/input-feature labels moved to `strings.xml`; protection-level tokens kept canonical (Android API identifiers) | Low–Med | T10 |
| ~~`VirusTotal.java`~~ ✅ 2026-05-02 | TODO (resolved) | New `computeInitialPollWait(fileSize)` ramps the *first* poll wait roughly +1 s per MB above a 10 MB threshold, clamped to [60 s, 240 s] — no more polling a 200 MB upload at 60 s and burning the 4 req/min rate-limit quota. Subsequent polls stay at the 30 s floor. | Low | Scanner |
| ~~`AppTypeOption.java`~~ ✅ 2026-05-02 (partial) | TODO (resolved 2/3) | **Play App Signing** + **Overlay app** flags now active in the AppType filter (`mFrozenFlags` + `withFlagsCheck` / `withoutFlagsCheck`); backed by new `IFilterableAppInfo.usesPlayAppSigning()` / `isOverlay()` methods on both `FilterableAppInfo` (uses `PackageUtils.usesPlayAppSigning` + `PackageInfoCompat2.getOverlayTarget`) and `ApplicationItem` (lazy `fetchPackageInfo()` paths). PWA and short-code deferred — TWA detection requires manifest service-tag sniffing and short-code is not a stable PackageManager signal; would ship a half-accurate heuristic. | Low | App Details |
| `PermissionsOption.java` | TODO | Permission flags not exposed in filter | Low | T7 |
| `DataUsageOption.java` | TODO | Mobile/Wi-Fi split not shown | Low | T7 |
| `TrackersOption.java` | TODO | Regex + tracker-name search not implemented | Low | T7 |
| ~~`CodeEditorFragment.java`~~ ✅ 2026-05-02 | TODO (resolved) | Three pickers landed: **language** popup (java/json/kotlin/properties/sh/smali/xml from `assets/languages/` via `Languages.getLanguage()`), **tab-size** popup (2/4/8 with `mEditor.setTabWidth`), **go-to-line** dialog (`TextInputDialogBuilder` → `mEditor.setSelection(line - 1, 0)`). The previously hardcoded "tabs"/"spaces" suffix on the indent toolbar now reads from `R.plurals.editor_tab_size_option_{tabs,spaces}` so locales pluralise the unit. New `CodeEditorViewModel.setLanguage()` setter persists the choice across language switches. | Low | T14 |
| BouncyCastle `1.83` | Dependency | Latest is `1.84`; no CVE exposure at 1.83 (GHSA-8xfc-gm6g-vgpv fixed in 1.78 [S49]); upgrade is low-urgency maintenance ([S50]) | Low | Security |
| libsu `6.0.0` | API | `Shell.sh/su` removed in 6.0.0; replaced by `Shell.cmd` — verify no legacy `Shell.sh/su` calls survive in NG source. `FLAG_REDIRECT_STDERR` also deprecated ([S47]). | Medium | All root ops |
| ~~All layouts~~ ✅ 2026-05-01 | Compliance | Audit for `elegantTextHeight` attribute usage — ignored for targetSdk=36; text rendering affected for Arabic/Thai/Indic scripts ([S44]). **Audit clean — zero source matches.** See [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md). | Low | T2 |
| WorkManager/JobScheduler | Compliance | Backup scheduler must be tested under Android 16 quota model; log stop reasons with `WorkInfo.getStopReason()` ([S45]). **⏸ Blocked: no WorkManager in source today — wire diagnostics into T6 Scheduled Auto-Backup when that lands.** | Low | T6 |
| Predictive Back / `onBackPressed` removal (API 36) | Compliance | For apps targeting API 36 (Android 16), `onBackPressed()` and `KEYCODE_BACK` are no longer dispatched; must migrate all back-press handling to `OnBackInvokedCallback` / `BackHandler` (Compose). Audit every `Activity.onBackPressed()` call and every `KeyEvent.KEYCODE_BACK` handler in NG before bumping `targetSdkVersion` to 36 ([S44]). v0.3.0 already ships edge-to-edge; predictive back gesture registration is the remaining compliance gap. | Medium | T2 |
| Zip-slip protection in APK/backup extraction | Security | Before writing any entry from a ZIP/tar archive (backup restore, APK zip extraction), canonicalize the output path and assert it begins with the intended destination directory; reject entries containing `../` traversal sequences. AM v4.0.0-alpha02 added this guard ([S01]); verify NG's own extraction paths match. | Medium | T2, T6 |
| ADB mDNS backend change (platform-tools 37) | Compatibility | ADB platform-tools 37.0.0 deprecated the `openscreen` mDNS backend in favor of `libadbmdns` ([S74]). Verify that NG's wireless ADB pairing code path (T5) does not hard-depend on `openscreen` discovery signals; use `adb mdns check` to test backend availability on target device before connecting. | Low | T5 |
| JADX `1.4.7` | Dependency | Latest is `1.5.5` (7 releases behind [S60]); includes critical multi-thread UI fix (v1.5.5), `.apks` support, CJK font rendering, and an external plugin system (v1.5.4+). Upgrade before T12 work begins. | Medium | T12 |
| Apktool 2.x → 3.x migration | Future | Apktool 3.0.x ("Apktool Remastered" [S80]) is a complete rewrite with a new smali fork and rewritten ARSC/`BinaryDataInputStream` parsers; Apache-2.0 licensed. If NG wraps Apktool for T12 APK editing, target 3.x not 2.x. Evaluate whether Apktool 3.x can replace or complement the currently dep'd baksmali 3.0.9 at T12 implementation time. | Low | T12 |
| Gson `2.13.2` | Dependency | Latest is `2.14.0` ([S58]); new `java.time` adapters; stricter integer ASCII validation; `Serializable` removed from internal Type impl classes (minor security hygiene). Low-risk upgrade. | Low | All |
| Material Components `1.13.x` | Ceiling | Material `1.14.0-rc01` raises `minSdkVersion` to 23 ([S57]); blocked until NG raises `minSdk` from 21. Action: document in `versions.gradle` comment; schedule minSdk raise to 23 before any Material 1.14.x adoption. | Medium | All |
| AGP `8.13.2` | Dependency | Latest stable is AGP `9.2`; requires Gradle 9.4.1 and SDK Build Tools 36 ([S59]). R8 tightens `-keepattributes *Annotation*` wildcard behavior in 9.x — auditing ProGuard rules required at upgrade. Not urgent but track for annual build-toolchain cycle. | Medium | Build |
| MagiskSU capability drop | Behavior | MagiskSU `≥v30.5` no longer drops Linux capabilities by default; explicit `--drop-cap` now required ([S52]). Audit all root shell invocations in NG that assumed automatic capability restriction before shipping T15 systemless ops. | Medium | T15 |
| Android 17 targetSdk=37 compliance | Compliance batch | Four items required before bumping `targetSdkVersion` to 37: (1) declare `ACCESS_LOCAL_NETWORK` runtime permission for wireless ADB LAN discovery ([S55]); (2) audit hidden-API bypass code paths for `static final` field reflection — `IllegalAccessException` for targetSdk=37 apps ([S54]); (3) migrate `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` → `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` in all `ActivityOptions` usage ([S54]); (4) verify Network Security Config for Certificate Transparency (CT default-on for targetSdk=37 [S54]). | High | T5, T8, T9 |
| Android 17 memory limits | Compliance | All-apps change: Android 17 introduces RAM-based per-app `AnonSwap` memory limits; exit detected via `ApplicationExitInfo.REASON_OTHER` with `"MemoryLimiter:AnonSwap"` description ([S55]). JADX decompile and APK-signature parsing are the heaviest NG operations; profile under low-memory devices before T12 ships. | Medium | T12 |
| Android 17 `usesCleartextTraffic` deprecation | Compliance | Android 17 begins enforcement of `usesCleartextTraffic`; apps that omit the Network Security Config manifest attribute will trigger lint errors on targetSdk=37 ([S55]). Audit NG's `network_security_config.xml` and all `http://` URLs in manifests before bumping targetSdk. | Low | T9 |
| Android 17 ECH default-on | Compliance | Apps targeting API 37 get ECH (Encrypted Client Hello) applied to all TLS connections by default ([S54]). NG must add a `<domainEncryption>` element to `network_security_config.xml` if any network endpoint (e.g. wireless ADB LAN pair) must opt out of ECH to avoid negotiation failures on older firmware. | Low | T5, T9 |
| ~~Android 17 per-app Keystore key cap~~ ✅ 2026-05-02 | Compliance | NG creates ≤2 `AndroidKeyStore` aliases total (both static, both `containsAlias`-guarded in `CompatUtil`); backup-crypto routes through file-backed BKS (`am_keystore.bks`) which is outside the cap. Audit at [docs/audits/2026-05-02-android17-keystore-key-cap.md](docs/audits/2026-05-02-android17-keystore-key-cap.md). | Low | T2, T9 |
| ~~Android 18 implicit URI grant removal (planned)~~ ✅ 2026-05-02 | Compliance | All seven URI-bearing share-out paths now set explicit `ClipData` alongside `FLAG_GRANT_READ_URI_PERMISSION`; chooser targets receive read access without relying on the implicit auto-grant scheduled for removal in Android 18 ([S55]). Audit + remediation at [docs/audits/2026-05-02-android18-implicit-uri-grant.md](docs/audits/2026-05-02-android18-implicit-uri-grant.md). | Low | T3, T5 |
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
| S121 | https://github.com/RikkaApps/Shizuku/releases | Shizuku v13.6.0: trusted-WLAN auto-start without root on Android 13+, removed LEANBACK_LAUNCHER per Play policy |
| S122 | https://github.com/topjohnwu/Magisk/releases | Magisk v30.7: Android 16 QPR2 sepolicy, Android XR support, `--drop-cap` semantics inverted to opt-in |
| S123 | https://github.com/tiann/KernelSU/releases | KernelSU v3.2.x: adb root mode, sulog screen, per-app seccomp filter status |
| S124 | https://developer.android.com/about/versions/16/behavior-changes-16 | Android 16 platform changes: `SDK_INT_FULL`, `VERSION_CODES_FULL`, `Build.getMinorSdkVersion()`, JobScheduler quota tightening |
| S125 | https://developer.android.com/reference/android/app/job/JobScheduler#getPendingJobReasonsHistory(int) | JobScheduler `getPendingJobReasonsHistory()` API for surfacing scheduled-job stop reasons |
| S126 | https://github.com/termux/termux-app/releases | Termux v0.118.3: SE_PROCESS_CONTEXT/SE_FILE_CONTEXT/SE_INFO env surface, 16k page size detection |
| S127 | https://github.com/REAndroid/APKEditor/releases | APKEditor v1.4.7-8: smali comment level "basic", `-remove-annotation`, framework v36 support |
| S128 | https://github.com/iBotPeaches/Apktool/releases | Apktool v3.0.2 "Remastered": ResChunkPullParser refactor, headless build support, Guava 33.5 |
| S129 | https://github.com/skylot/jadx/releases | JADX v1.5.5: `.apks` ingestion, UI zoom factor, FlatLaf composite font for CJK, plugin exception reporting |
| S130 | https://github.com/aistra0528/Hail/releases | Hail v1.10.0: Auto-Freeze QS Tile, Digital Assistant launch, KernelSU App Profile, multi-tag, Xposed unfreeze hook |
| S131 | https://github.com/Hamza417/Inure/releases | Inure build106.5–107.0.1: AppOps IGNORE flag, MiUI ops mappings, native-lib sizes, batch installer, removed persistent search |
| S132 | https://github.com/zhanghai/MaterialFiles/releases | Material Files v1.7.2–v1.7.4: WebDAV with self-signed-cert trust, checksum properties tab, Save As, Android TV launcher icon |
| S133 | https://github.com/d4rken-org/sdmaid-se/releases | SD Maid SE v1.7.2-rc0: auto-fix battery optimization via root/ADB, warn-before-volume-scan, signing-cert fingerprints publish |
| S134 | https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases | UAD-NG v1.2.0: cross-user package state verification, auto-fetch debloat definitions, selectable descriptions, copy-error button |
| S135 | https://github.com/NeoApplications/Neo-Backup/releases | Neo Backup 8.3.17: launcher shortcuts for schedules, backup sharing, KeepAndroidOpen banner, existing-tag suggestions |
| S136 | https://developer.android.com/jetpack/androidx/releases/glance | androidx.glance: Compose-style home-screen widgets, app widgets parity surface |
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
| S72 | https://github.com/LibChecker/LibChecker/releases | LibChecker 2.5.2–2.5.3: APK signing scheme multi-flag display (V1/V2/V3/V4), 16 KB page-alignment badge per .so, app state snapshot + version-diff timeline, APK URL partial-download metadata preview, native library extraction to Downloads, permission provider attribution |
| S73 | https://github.com/vvb2060/KeyAttestation | KeyAttestation: hardware key attestation panel — KeyMint version, attestation level (software/TEE/StrongBox), certificate chain; Shizuku needed for device-unique-ID attestation |
| S74 | https://developer.android.com/tools/releases/platform-tools | ADB platform-tools 37.0.0: `openscreen` mDNS backend deprecated in favor of `libadbmdns`; affects wireless ADB pairing code paths |
| S75 | https://github.com/FreezeYou/FreezeYou/releases | FreezeYou v11.0–v11.1: self-diagnosis page (privilege health-check model); Profile Owner DPM path via `adb shell dpm set-profile-owner` (no factory reset needed, work-profile scope) |
| S76 | https://github.com/bmax121/APatch | APatch: kernel-based root via KernelPatch; ARM64 only, kernel 3.18–6.12; SuperKey (higher privilege than su); APM (Magisk-style) + KPM (kernel injection) modules; GPL-3.0; `/data/adb/ap/` detection marker |
| S77 | https://github.com/Dr-TSNG/ZygiskNext/releases | ZygiskNext v1.3.0–v1.3.4: anonymous memory module loading; multi-zygote support; KernelSU/APatch Zygisk compatibility layer; WebUI Zygisk module list; module error check mechanism (v1.3.4); Android 16 QPR2 + virtual-machine (Redroid) support |
| S78 | https://github.com/oasisfeng/island/releases | Island v6.3–v6.4: scoped settings (per-feature DPC capability toggle), expanded app details sheet (scoped storage access level, Android 11+ package visibility scope), app visibility filters (can-see / can-access-shared-storage toggles), Open DPC API for third-party DPC operations via Binder |
| S79 | https://github.com/XayahSuSuSu/Android-DataBackup | Android-DataBackup v2.x: Magisk/KernelSU/APatch root support, multi-user backup, cloud + SMB backup destinations, label-based app filtering, config export, GPL-3.0 |
| S80 | https://github.com/iBotPeaches/Apktool/releases | Apktool 3.0.x "Apktool Remastered": complete internal rewrite; new smali fork; rewritten ARSC + `BinaryDataInputStream` parsers; Apache-2.0; `main` branch is 3.x; `2.x` branch maintenance-only |
| S81 | https://github.com/Droid-ify/client/releases | Droid-ify v0.7.0–v0.7.1: SHA256 index-integrity verification (long-press refresh), custom actions, delete-APK-on-install option, fdroid.link URL parsing, incompatible-version disclaimer, signed releases |
| S82 | https://developer.android.com/reference/android/content/pm/ArchivedPackageInfo | Android 15 (API 35) `ArchivedPackageInfo`: archived-state class holding package name, signing info, and launcher activities; paired with `PackageInfo.isArchived` and `PackageInstaller.UnarchivalState` |
| S83 | https://developer.android.com/reference/android/content/pm/PackageManager#getInstallSourceInfo(java.lang.String) | `PackageManager.getInstallSourceInfo()` (API 30+): returns initiating package, originating package, and (API 34+) installer signing-cert digest — enables distinguishing who triggered vs. who declared the install source |
| S84 | https://github.com/iamr0s/Dhizuku/releases | Dhizuku v2.11.0–v2.11.2: minSdk bumped to 26 (Android 8.0), battery optimization bypass (`requestIgnoreBatteryOptimizations()`), whitelist mode, DhizukuService performance optimization (non-isolated binderWrapper), Android 16 support |
| S85 | https://github.com/topjohnwu/Magisk/releases | Magisk v30.5–v30.7: Rust codebase migration, Android 16 QPR2 support, Android XR support, `--drop-cap` explicit capability restriction for non-root UIDs, improved sepolicy handling, vendor_boot partition support |
| S86 | https://github.com/LawnchairLauncher/Lawnchair/releases | Lawnchair 15 Beta 2–Beta 3: Nova Launcher backup restoration (icon grid, widgets, folders), infinite scrolling, app-drawer folder reordering, launcher search backend redesign (IronFox + Kagi providers) |
| S87 | https://github.com/shadowsocks/shadowsocks-android/releases | shadowsocks-android v5.3.5: VPN flags opt-in for plugins, DNS resolution fixes (OnePlus 12 / Android 15), desugaring fixes, E2E test CI/CD workflow, eliminate gson dependency |
| S88 | https://github.com/M66B/FairEmail/releases | FairEmail v1.2315: active maintenance; email security improvements; Android 16 support; multi-release consistent updates
| S89 | https://github.com/REAndroid/APKEditor/releases | APKEditor v1.4.8: structured three-layer APK editing (manifest/resources/dex), framework v36 support, smali optimization; reference OSS implementation for full APK editing architecture
| S90 | https://github.com/soupslurpr/AppVerifier/releases | AppVerifier v0.8.2: APK verification UI with signature database, donation screen, Material 3 design, focus on package authenticity
| S91 | https://github.com/termux/termux-app/releases | Termux v0.118.3–v0.119.0-beta: Android 16 QPR1 HiddenAPI fix, MIME type recognition in ContentProvider, terminal pixel dimension reporting, AutoFill support; signal for file manager MIME detection
| S92 | https://github.com/LSPosed/LSPatch/releases | LSPatch v0.6 (archived Dec 2023): non-root Xposed framework via APK patching and modification, integrated mode, module selection, dynamic loader; reference for non-root code injection architectural patterns
| S93 | https://github.com/frida/frida/releases | Frida v17.9.4: dynamic instrumentation framework for real-time code patching on Android; root not strictly required (Frida Server model); runtime method hooking, memory inspection, SELinux bypass; advanced power-user tool
| S94 | https://github.com/skylot/jadx/releases | JADX v1.5.5: cross-platform GUI decompiler with split-view mode, CJK font support, .apks archive support, NPE fixes, generics preservation; reference implementation for APK analysis UI patterns
| S95 | https://github.com/mobile-shell/mosh/releases | Mosh v1.4.0: mobile shell with OSC 52 clipboard integration, prediction modes, true color support, syslog integration; terminal networking innovation for unreliable connections
| S96 | https://github.com/square/leakcanary/releases | LeakCanary v3.0-alpha series: memory leak detection library for Android apps; automated heap dump analysis and leak report; integrates into development/testing workflows
| S97 | https://github.com/google/perfetto/releases | Perfetto v54.0: comprehensive system profiling platform (tracing service, trace processor with SQL queries, UI); Android 16 support for heap profiling, jank metrics, CUJ analysis, deep-linking for public traces
| S98 | https://github.com/appium/appium/releases | Appium v3.3.1+: cross-platform test automation framework with plugin architecture; supports Android via UIAutomator and Espresso integrations; extensibility model for custom plugins
| S99 | https://github.com/google/play-services-plugins/releases | OSS Licenses Plugin v0.11.0: Gradle build-time dependency license scanning and in-app display; transparency pattern for third-party attribution
| S100 | https://github.com/google/bundletool/releases | Bundletool v1.18.3: APK/AAB generation, install simulation, device group targeting, sparse encoding (SDK 32+), .asar format support, uncompressed dex (Android Q+)
| S101 | https://github.com/JetBrains/compose-multiplatform/releases | Compose Multiplatform v1.11.0-beta03: cross-platform declarative UI framework; Material 3 Adaptive, drag-to-scroll, KSP v2 compatibility, navigation and lifecycle integrations
| S102 | https://github.com/raamcosta/compose-destinations/releases | Compose Destinations v2.2.0: Jetpack Compose navigation library with KSP v2 fixes, result delivery lifecycle control (resume vs. start), destination labels for logging/monitoring
| S103 | https://github.com/nextcloud/android/releases | Nextcloud Android v33.1.0: cloud storage sync client; auto-upload, unified search, smart folder refresh, chat/translation in Nextcloud Assistant; on-device sync model precedent
| S104 | https://github.com/Iamlooker/Droid-ify/releases | Droid-ify v0.7.1: F-Droid client with custom actions (shell scripts per app), SHA256 installer verification, delete-APK-on-install toggle, proxy icon/screenshots download, Material 3 design patterns
| S105 | https://github.com/oasisfeng/Island/releases | Island v6.4.2: device-admin app isolation and duplication framework; scoped settings (per-feature capability toggles), widget access filtering, custom image capture provider; architectural pattern for safe app containment
| S106 | https://github.com/Kotlin/kmp-production-sample/releases | Kotlin Multiplatform (KMM) Production Sample: Material Design 3 patterns across platform; Compose Multiplatform for cross-platform Material 3 UI consistency; reference implementation
| S107 | https://github.com/GoogleChrome/android-browser-helper/releases | android-browser-helper v2.7.0: Trusted Web Activity (TWA) framework; edge-to-edge splash screens, custom tabs, billing integration, web API bridges for native app features
| S108 | https://github.com/android/nowinandroid/releases | Now in Android v0.1.1: Material 3 reference app; search + recent searches, push-based notifications, unread badge pattern, undo bookmarks SnackBar, comprehensive Compose + Kotlin example
| S109 | https://github.com/OxygenCobalt/Auxio/releases | Auxio v4.0.10: Material Design refresh with Material You integration; fast scrolling, native tag parsing (Musikr), cover-art caching, adaptive/small-split-screen layouts, Android 15 support
| S110 | https://github.com/mullvad/mullvadvpn-app/releases | Mullvad VPN Android v2026.4: two-column landscape layout (list + detail), in-app language selector (Android 13+), split tunneling search, accessibility polish, reconnect action in notification
| S111 | https://github.com/ProtonMail/android-mail/releases | ProtonMail v7.9.5: premium email UX with improved PIN layout, push notification handling, encryption visibility toggle, discreet/generic icon mode for privacy, Material 3 patterns
| S112 | https://github.com/d4rken-org/sdmaid-se/releases | SD Maid SE v1.7.2-rc0: scheduler auto-fix battery optimization via root/ADB, publish APK signing cert fingerprints, Android TV/Google TV launcher support, deduplicator UX (similar audio/video files, accurate freeable space)
| S113 | https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases | UAD-ng v1.2.0: package state verification, cross-user detection, searchable package descriptions, copy-error-message UI, auto-fetch package definitions on launch (decoupled from app version)
| S114 | https://github.com/NeoApplications/Neo-Backup/releases | Neo Backup v8.3.17: launcher shortcuts for schedules, KeepAndroidOpen banner, backup sharing button, existing tags suggestion, Compose-compiler-stable VM states migration, Nav3 bottom sheet navigation
| S115 | https://github.com/google/accompanist/releases | Accompanist v0.37.3: official Compose utilities (drawable painter, system UI controllers); navigation-material deprecation in favor of Compose Material3 navigation
| S116 | https://github.com/Aefyr/SAI/releases | SAI v4.5: split APK installer with Sui/Shizuku support, large APK install warning (>150MB), APKM unencrypted format, XAPK partial support, .apks parsing, managed backups (beta)
| S117 | https://github.com/gsantner/markor/releases | Markor v2.15.0: dyslexia-friendly font option, SHA-256 in file info dialog, per-folder sort order, ShareInto tracking parameter filtering (Amazon)
| S118 | https://github.com/SkyD666/PodAura/releases | PodAura v3.2: Material 3 Expressive design migration, persistent article filter state, long-press group reordering, font size tuning on reading page, fast forward/replay portrait player
| S119 | https://github.com/google/talkback/releases | TalkBack 6.1.1: Google's official Android screen reader and accessibility services; baseline for Compose Semantics audit and contentDescription validation
| S120 | https://github.com/zaneschepke/wgtunnel/releases | WG Tunnel v4.3.1: WireGuard Android client with endpoint latency filtering, traffic stats in notification, Doze mode handshake fix, SHA-256 4096-bit signing fingerprint
