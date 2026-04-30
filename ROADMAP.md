# AppManagerNG — Roadmap

**Status:** Living document — update on every version bump.  
**Baseline:** v0.1.0, forked from [App Manager](https://github.com/MuntashirAkon/AppManager) @ `3d11bcb` (post-v4.0.5), 2026-04-30.  
**Next revision due:** v0.2.0 release.

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
| **v0.2.0** | Identity | `applicationId` + namespace rename → `io.github.sysadmindoc.AppManagerNG`; new release keystore; GitHub Actions release pipeline; NG-specific CONTRIBUTING.md |
| **v0.3.0** | UX Refresh | Material 3 dashboard; Pro Mode toggle; edge-to-edge (Android 15/16 compliance); AMOLED/dark/light themes |
| **v0.4.0** | Onboarding | Root/Shizuku/ADB capability detection wizard; plain-language privilege explainer; first-run flow |
| **v0.5.0** | Settings & Discovery | Settings reorganization by task; global in-app search; contextual help tooltips; in-app changelog viewer |

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
| **applicationId Rename** | Change `io.github.muntashirakon.AppManager` → `io.github.sysadmindoc.AppManagerNG`; update all `BuildConfig` references | Low |
| **New Release Keystore** | Generate NG-specific signing key; document SHA-256 fingerprint in README for AppVerifier compatibility | Low |
| **GitHub Actions Release Pipeline** | `release.yml`: tag push → build → sign → upload APK to GitHub Releases; parallel arm64-v8a / universal ABIs | Medium |
| **Reproducible Builds** | Match upstream's reproducible build config (added in upstream v4.0.5); CI diff step compares release APK binary hash | Medium |
| **IzzyOnDroid Listing** | Submit after rename; IzzyOnDroid is faster than F-Droid proper and the primary privacy-community distribution channel | Low |
| **F-Droid Listing** | Submit to F-Droid proper after IzzyOnDroid pass; requires REUSE compliance (already in place) | Low–Med |
| **Obtainium Config** | Publish pre-built app config at `apps.obtainium.imranr.dev` so users can track NG updates directly | Trivial |
| **NG-Specific CONTRIBUTING.md** | Replace upstream's CONTRIBUTING.rst; define AI code policy, commit format, PR expectations, upstream sync protocol | Low |
| **AppVerifier Fingerprint** | Add signing certificate SHA-256 to README (model: SAI, Obtainium, Canta all do this) | Trivial |

### T2 — Platform Compliance (Android 15/16)

Unaddressed items here will become regressions when targetSdk=36 is enforced on Google Play and will break on Android 16 devices in the field.

| Item | Description | Effort |
|------|-------------|--------|
| **Edge-to-Edge Enforcement** | Remove `windowOptOutEdgeToEdgeEnforcement` (removed in Android 16 targetSdk=36); handle all window insets across every screen | Medium |
| **Predictive Back (Android 16)** | `enableOnBackInvokedCallback = true` is now the default for targetSdk=36; audit and migrate all `onBackPressed` / `KEYCODE_BACK` consumers | Medium |
| **Themed App Icons** | Add monochrome adaptive icon variant (Android 16 auto-applies themed icons; supply the vector to control the output) | Low |
| **16KB Page Size Compliance** | Recompile all NDK `.so` libraries with `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and verify linker 16KB alignment; required for Android 15+ physical devices | Medium |
| **`announceForAccessibility` Migration** | Replace deprecated `announceForAccessibility` calls (deprecated Android 16) with `ViewCompat.performAccessibilityAction` equivalents | Low |

### T3 — Critical Bug Fixes & Security Debt

These are `FIXME` / security issues identified in source that should not wait for a feature release.

| Item | File | Description | Effort |
|------|------|-------------|--------|
| **Keystore Password Security** | `KeyStoreUtils.java` | Use `char[]` instead of `String` for keystore passwords (String is interned in heap; not clearable) | Low |
| **ABX-to-XML Lossless Fix** | `CodeEditorViewModel.java` | Current ABX→XML conversion is lossy; update serializer to round-trip without data loss | Medium |
| **Intent Interceptor OPEN_DOCUMENT** | `ActivityInterceptor.java` | Interceptor permanently breaks `android.intent.action.OPEN_DOCUMENT` (Issue #1767, 2 reactions); fix dispatch logic | Medium |
| **Utils.java i18n (×5)** | `Utils.java` | Five hardcoded string instances that bypass Android's localization pipeline | Low |

### T4 — Observability & Process

| Item | Description | Effort |
|------|-------------|--------|
| **Opt-In Crash Reporting** | On uncaught exception: write crash log to app-private storage + show "Share crash report" dialog that deep-links to GitHub Issues. Zero network egress without explicit user action. | Low |
| **In-App Diagnostic Dump** | Export logcat (filtered to AM process) + app state snapshot as shareable ZIP for bug reports | Low |
| **CodeQL Alert Triage** | Audit all open CodeQL alerts (`.github/workflows/codeql.yml` already present); ensure zero blanket suppressions | Low |

---

## Next

### T5 — Rootless Users (Shizuku)

Shizuku support is the single most-requested upstream feature with 31 reactions across 5 years ([S02]). It unblocks the majority of AM operations for users without root. Canta ([S19]) and SmartPack Package Manager ([S21]) already ship Shizuku integration; AM/NG without it is at a structural disadvantage for casual users.

| Item | Description | Effort |
|------|-------------|--------|
| **Shizuku Privilege Provider** | Add Shizuku as a third privilege path alongside root/ADB via `ShizukuProvider` binder; make privilege selection automatic at runtime | High |
| **Wireless ADB Auto-Pairing** | Guide user through Android 11+ wireless ADB pairing in onboarding; persist paired device | Medium |
| **Rootless Debloat (Shizuku)** | Expose `pm uninstall --user 0` via Shizuku; integrate android-debloat-list ([S23]) safety ratings and dependency warnings | Medium |
| **Install Without Staging APK** | Direct `PackageInstaller` session without staging to cache; faster installs on constrained storage (Issue #1671 [S17]) | Medium |
| **Onboarding Capability Wizard** | Detect root/Shizuku/wireless-ADB at first launch; show plain-language capability matrix ("What you can do with each privilege level") | Medium |

### T6 — Backup Polish

Neo Backup ([S20]) and Titanium Backup are the benchmark. AM's backup engine is competitive; the UX around scheduling, retention, and verification is not.

| Item | Description | Effort |
|------|-------------|--------|
| **Scheduled Auto-Backup** | WorkManager-based scheduler; triggers: time-of-day, charging state, network availability (per Issue #555 [S09]) | Medium |
| **Backup Retention Policy** | Set max backup count per app and age-based pruning (e.g. "keep last 3"); automatic cleanup on schedule ([S20] model) | Low |
| **1-Click Delete Old Backups** | Batch-clean oldest revisions across all apps from Backup list (Issue #387) | Low |
| **Backup Integrity Verification** | SHA-256-verify backup archives at creation and again at restore; alert on mismatch | Medium |
| **AES-256 Backup Encryption** | Password-based AES-256 encryption for backup archives; BouncyCastle already dep'd; key derivation via PBKDF2 | Medium |
| **Backup Progress Notifications** | Rich ongoing notification with per-app name, progress bar, and ETA | Low |
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

### T8 — Profiles & Automation

Routine Operations is the #2 requested feature (21 reactions, Issue #61 [S03]). Muntashir himself published the full technical spec in that issue in 2020; implementation was deferred. NG should ship this if upstream continues to stall.

| Item | Description | Effort |
|------|-------------|--------|
| **Routine Operations / Scheduler** | Event-triggered profile execution: boot, custom interval, network available, charging, screen on/off, app foreground/background (full spec: Issue #61 [S03]) | High |
| **Profile State Conditions** | Profiles with conditions (execute only if battery > X%, only between hours Y–Z) | Medium |
| **Profile Import/Export** | Share profile definitions as JSON between devices or via backup | Low |
| **Intent Interceptor: Return Results** | Send activity results back to the original calling app after interception (ActivityInterceptor.java TODO) | Medium |
| **Operation Activity Log** | Persistent log of every operation AM has performed (freeze, backup, AppOps change, etc.); filterable by app, type, date (Issue #143 [S13], 3 reactions) | Medium |

### T9 — Privacy & Security

| Item | Description | Effort |
|------|-------------|--------|
| **Biometric App Lock** | BiometricPrompt-based lock for AM itself on app open (BiometricPrompt dep already present at 1.4.0-alpha04) | Low |
| **Tracker Blocking (AppOps)** | One-click disable specific tracker components via `AppOps.setUidMode`; show which trackers are currently active | Medium |
| **Privacy Dashboard Integration** | Surface Android 12+ permission usage history (timeline view) inside AM app details | Medium |
| **App Signing Key Change Alert** | Alert when the signing certificate of an installed update differs from the previously stored one | Medium |

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
| **APK Merge (splits → universal)** | Merge split APK set into a single universal APK (model: APKEditor) | Medium | — |

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

---

## Rejected

| Item | Reason |
|------|--------|
| **Multi-device simultaneous ADB** (UAD-NG model) | UAD-NG is a desktop tool; AM/NG is on-device. This is a fundamentally different UX. WONTFIX. |
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
| `AppTypeOption.java` | TODO | Play App Signing / PWA / overlay detection missing | Low | App Details |
| `PermissionsOption.java` | TODO | Permission flags not exposed in filter | Low | T7 |
| `DataUsageOption.java` | TODO | Mobile/Wi-Fi split not shown | Low | T7 |
| `TrackersOption.java` | TODO | Regex + tracker-name search not implemented | Low | T7 |
| `CodeEditorFragment.java` | TODO | i18n, custom tab size, language display missing | Low | T14 |

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
