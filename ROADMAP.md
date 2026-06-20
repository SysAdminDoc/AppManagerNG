<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-19)

### P1

- [ ] P1 — OWASP Dependency-Check upgrade 10.0.3 → 12.2.2
  Why: current CI weekly scan lacks CVSS v4 scoring, grouped suppression rules, and unused-rule detection; 2 major versions behind
  Evidence: OWASP DependencyCheck releases (10.0.3 → 11.0 H2 schema change → 12.0 CVSS v4 → 12.1 grouped suppressions → 12.2 unused-rule flags)
  Touches: versions.gradle (`dependency_check_version`), .github/workflows/dependency-scan.yml (verify SARIF upload handles CVSS v4 schema)
  Acceptance: `./gradlew dependencyCheckAnalyze` succeeds with 12.2.2; CI weekly scan uploads SARIF with CVSS v4 scores; H2 DB regenerates automatically on first run
  Complexity: S

- [ ] P1 — AGP 9.3.0 + Gradle 9.5.1 build-infrastructure upgrade
  Why: AGP 9.2.0 has a known `classes.jar` file handle leak preventing rebuilds on Windows (the project's primary dev environment); 9.3.0 fixes it and brings internal KGP 2.3.10
  Evidence: AGP 9.3.0 release notes (file handle leak fix, L8 obfuscation mapping, KGP 2.3.10); Gradle 9.5.1 (task provenance, wrapper retry)
  Touches: gradle/wrapper/gradle-wrapper.properties (9.4.1 → 9.5.1), build.gradle (`agp_version` 9.2.0 → 9.3.0), dependency lockfiles (regenerate with `./gradlew dependencies --write-locks`)
  Acceptance: `./gradlew assembleFlossDebug` succeeds with AGP 9.3.0 + Gradle 9.5.1; Windows incremental rebuild no longer stalls on `classes.jar`; ProGuard rules pass (no wildcard `-keepattributes` issues — already confirmed explicit-only); unit tests pass; CI green
  Complexity: S

### P2

- [ ] P2 — Roborazzi screenshot regression testing integration
  Why: the V2 premium design token system, AMOLED/dark/light themes, and ongoing layout polish create continuous visual regression risk with no automated catching; 143 layouts, 3 themes, no screenshot tests
  Evidence: Roborazzi (github.com/takahirom/roborazzi) integrates with existing JUnit 4 + Robolectric setup, supports Android Views, runs in CI without emulators
  Touches: app/build.gradle (add roborazzi plugin + dependency), app/src/test/ (new screenshot test classes for main list, app details, onboarding, mode selector), .github/workflows/tests.yml (add screenshot comparison step)
  Acceptance: `./gradlew :app:testFlossDebugUnitTest` captures baseline screenshots for main list (dark + light) and app details; CI PR checks compare against baselines and fail on pixel diff above threshold
  Complexity: M

- [ ] P2 — ViewModel extraction for permission toggle operations
  Why: 7 inline TODOs in AppDetailsPermissionsFragment.java (lines 185, 199, 244, 289, 791, 862, 900) mark permission grants, revokes, AppOp changes, and runtime permission updates running on the fragment's thread; concurrent operations can race
  Evidence: AppDetailsPermissionsFragment.java TODO comments; AppDetailsViewModel already has reference-rule loading and permission-read paths; pattern exists in other fragments
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsPermissionsFragment.java, app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsViewModel.java
  Acceptance: all 7 marked operations route through ViewModel methods running on a background executor; fragment observes results via LiveData; no permission toggle code remains on the UI thread; existing unit tests pass; add thread-safety test for concurrent permission changes on the same package
  Complexity: M

- [ ] P2 — Minor dependency bumps: zstd-jni 1.5.7-11 + AndroidX Core (if minSdk 21 safe)
  Why: zstd-jni -7 to -11 has JNI-level bug fixes (same underlying algorithm, patch-level only); AndroidX Core 1.17.0 → 1.19.0 adds compileSdk 36.1 support and NotificationCompat improvements if still API-21-compatible
  Evidence: zstd-jni Maven Central versions; AndroidX Core release notes
  Touches: versions.gradle (`zstd_version`, `androidx_core_version`), dependency lockfiles
  Acceptance: `./gradlew assembleFlossDebug` + `testFlossDebugUnitTest` pass; verify AndroidX Core 1.19.0 minSdk before bumping (if minSdk raised to 23, hold Core at 1.17.0 and only bump zstd-jni)
  Complexity: S

- [ ] P2 — Advanced Protection Mode detection and graceful degradation
  Why: Android's Advanced Protection Mode blocks sideloading entirely; NG already declares QUERY_ADVANCED_PROTECTION_MODE but does not check AdvancedProtectionManager at runtime, so install operations will fail with an opaque error when AAPM is active
  Evidence: Android 16/17 AdvancedProtectionManager API; AAPM enforcement on Pixel devices; app/src/main/AndroidManifest.xml already declares the permission
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerCompat.java (pre-check before install sessions), app/src/main/java/io/github/muntashirakon/AppManager/settings/ModeDoctorPreferences.java (AAPM status in mode doctor), new AdvancedProtectionCompat utility
  Acceptance: `AdvancedProtectionManager.isAdvancedProtectionEnabled()` is checked before install operations; when true, a clear dialog explains that sideloading is blocked by device policy; mode doctor shows AAPM status; unit test with mocked AdvancedProtectionManager
  Complexity: S

- [ ] P2 — Android 17 static-final-field reflection audit
  Why: Android 17 throws IllegalAccessException when any code calls Field.set() on a static final field; JNI modification crashes the app; root/Shizuku/hidden-API code paths that modify framework constants at runtime will break on API 37 devices
  Evidence: Android 17 behavior changes (targeting API 37); USE_NEW_MESSAGEQUEUE compat flag; compat/, hiddenapi/, servermanager/, ipc/ source roots all use reflection
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/compat/, hiddenapi/, app/src/main/java/io/github/muntashirakon/AppManager/servermanager/, app/src/main/java/io/github/muntashirakon/AppManager/ipc/
  Acceptance: `grep -rn "\.set(" --include="*.java"` across all source roots identifies every reflective field write; each hit on a static-final field is either replaced with a safe alternative or guarded by SDK_INT check; add a Robolectric test targeting SDK 37 that asserts no IllegalAccessException on startup paths
  Complexity: M

### P3

- [ ] P3 — Terminal command history persistence and minimal completion
  Why: TermActivity.java line 49 is the project's oldest TODO ("Replace it with an actual terminal"); lines 98, 107, 184 mark tab completion, command history, and init-script support as missing; all are standard terminal expectations
  Evidence: TermActivity.java TODOs; Inure ships a terminal with history; upstream #23 (21 comments) requests a proper terminal
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/terminal/TermActivity.java (history ring buffer, file-based persistence, input completion overlay)
  Acceptance: terminal persists command history across sessions (stored in app-private file); up/down arrow keys navigate history; tab key triggers path/command completion from available executables; init-script support loads `~/.amrc` or equivalent on session start; unit test for history ring buffer and persistence
  Complexity: M

- [ ] P3 — Auto-freeze on screen lock with optional re-freeze delay
  Why: Hail's auto-freeze-on-screen-lock is the most-requested freeze UX pattern (6k stars, explicitly highlighted as its killer feature); NG has freeze/unfreeze plumbing, QS tile, and shortcut-based unfreezing, but no automatic re-freeze after the user leaves the app
  Evidence: Hail's auto-freeze feature; upstream App Manager unfreeze-on-shortcut-launch feature; existing FreezeUnfreezeService.java and FreezeRule.java infrastructure
  Touches: app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/ (new ScreenLockFreezeReceiver for ACTION_SCREEN_OFF), app/src/main/java/io/github/muntashirakon/AppManager/settings/ (auto-freeze preference with optional delay), app/src/main/java/io/github/muntashirakon/AppManager/rules/struct/FreezeRule.java (auto-freeze flag)
  Acceptance: user can enable auto-freeze in settings; all apps with auto-freeze rules are frozen when ACTION_SCREEN_OFF fires (with optional configurable delay); unit test for the receiver logic and delay; preference is off by default
  Complexity: M

- [ ] P3 — IzzyOnDroid repository submission preparation
  Why: Fastlane metadata is already complete (title, descriptions, icon, 9 screenshots, changelogs); IzzyOnDroid is the fastest path to F-Droid ecosystem visibility in Neo Store and Droid-ify clients; distribution readiness is high
  Evidence: fastlane/metadata/android/en-US/ (complete); IzzyOnDroid inclusion policy (requires FLOSS license, Fastlane metadata, release-signed APK, distinct from original); upstream App Manager is already listed
  Touches: fastlane/metadata/android/en-US/ (verify currency of descriptions), docs/distribution/ (submission checklist), README.md (add IzzyOnDroid badge after listing)
  Acceptance: IzzyOnDroid submission request filed at codeberg.org/IzzyOnDroid/repo with correct metadata pointing to GitHub Releases; badge added to README after acceptance
  Complexity: S
