<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

### P2

- [ ] P2 — HiddenApiBypass Android 17 deprecation audit
  Why: `addHiddenApiExemptions` is deprecated in HiddenApiBypass main (commit bac48e5, 2026-06-05). NG calls it in `AppManager.attachBaseContext()` with a blanket `"L"` exemption. When v6.2+ ships, the old API will break on Android 17.
  Evidence: LSPosed/AndroidHiddenApiBypass commit bac48e5; AppManager.java:69
  Touches: AppManager.java (attachBaseContext), versions.gradle (hiddenapibypass_version), hiddenapi/ module
  Acceptance: audit documents which hidden API exemptions NG actually needs; a version-gated fallback path is ready so that when HiddenApiBypass 6.2+ is released, updating the dep + switching to the new API is a single-commit change.
  Complexity: M

- [ ] P2 — Android 17 Background Activity Launch (BAL) hardening audit
  Why: `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is deprecated for apps targeting API 37. NG's routine scheduler, batch operations, and Tasker intent handlers may trigger background activity starts that will be blocked.
  Evidence: developer.android.com/about/versions/17/behavior-changes-17 (BAL section)
  Touches: routines/ (RoutineExecutor), batchops/ (BatchOpsManager), intercept/ (ActivityInterceptor), app/src/main/AndroidManifest.xml
  Acceptance: grep for MODE_BACKGROUND_ACTIVITY_START and all background-to-foreground activity transitions; replace with MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE or add explicit user-visible notification before starting activities from background; test verifies no deprecated BAL mode usage.
  Complexity: S

- [ ] P2 — Android 18 implicit URI grant preparation
  Why: Android 18 preview removes auto-grant of URI read/write permissions on ACTION_SEND/ACTION_SEND_MULTIPLE/ACTION_IMAGE_CAPTURE. NG's APK sharing, installer flows, and support bundle sharing use ACTION_SEND and will break without explicit FLAG_GRANT_READ_URI_PERMISSION.
  Evidence: developer.android.com/about/versions/17/behavior-changes-all (URI grants section — previewed for Android 18)
  Touches: utils/ (intent builders), apk/ (sharing), misc/SupportInfoBundle (share intent), intercept/ (intent forwarding)
  Acceptance: every ACTION_SEND/ACTION_SEND_MULTIPLE intent that includes a content:// URI also sets FLAG_GRANT_READ_URI_PERMISSION; a grep-based contract test verifies no ACTION_SEND with content URI lacks the flag.
  Complexity: S

### P3

- [ ] P3 — clearApplicationUserData → IActivityManager migration
  Why: PackageManagerCompat.java:582 TODO notes that IActivityManager#clearApplicationUserData() is more stable than the current IPackageManager path, which has API-version-dependent method signatures.
  Evidence: PackageManagerCompat.java:582 TODO comment (dated 5/25/26)
  Touches: compat/PackageManagerCompat.java (clearApplicationUserDataViaIpc method)
  Acceptance: data-clear operations use IActivityManager when available (API 30+), falling back to IPackageManager on older APIs; existing backup/clear tests still pass.
  Complexity: S

- [ ] P3 — ZipFileSystem test stub completion
  Why: 8+ empty test methods in ZipFileSystemTest.java marked TODO since 2022. These represent untested VFS paths that could silently regress.
  Evidence: app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java lines 63-912 (8 TODO markers)
  Touches: app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java
  Acceptance: all 8+ stubbed test methods have implementations that exercise the ZipFileSystem paths they were designed to cover; test suite still passes.
  Complexity: S

- [ ] P3 — Gradle 9.5.1 → 9.6.0
  Why: Gradle 9.6.0 (released 2026-06-19) improves configuration cache hit rates. No security driver but keeps build infra current.
  Evidence: https://github.com/gradle/gradle/releases/tag/v9.6.0
  Touches: gradle/wrapper/gradle-wrapper.properties, lockfiles
  Acceptance: Gradle wrapper updated, ./gradlew assembleFlossDebug succeeds, all tests pass.
  Complexity: S

All remaining blocked items are in `Roadmap_Blocked.md`.
