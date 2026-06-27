<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Research-Driven Additions

- [ ] P1 — Bound privileged shell result output
  Why: the privileged server accumulates command output in an unbounded `StringBuilder` before parceling it back to the app, so long-running or noisy shell commands can exhaust memory before IPC frame limits apply.
  Evidence: `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/Shell.java:201`; `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerHandler.java:134`; Android 17 app memory limits.
  Touches: `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/Shell.java`; `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerHandler.java`; server/common tests or source-contract tests.
  Acceptance: shell result text is capped by bytes or lines, includes a clear truncation marker, preserves exit status, and has a host-side regression test proving large output does not grow without bound.
  Complexity: M

- [ ] P1 — Replace privileged server direct stack traces with bounded diagnostics
  Why: privileged server/common production paths still call `printStackTrace()` directly and catch broad `Throwable`, bypassing structured diagnostics after the app-layer logging cleanup.
  Evidence: `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerRunner.java`; `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerHandler.java`; `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/FLog.java`; `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/Shell.java`.
  Touches: `server/src/main/java/io/github/muntashirakon/AppManager/server/`; `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/`; source-contract tests.
  Acceptance: no direct `printStackTrace()` remains in production `server/` or `libserver/`; expected broad catches are narrowed or documented; diagnostics route through bounded `FLog` without leaking session tokens or full user data.
  Complexity: M

- [ ] P2 — Make release verification local-only and remove stale CI claims
  Why: GitHub Actions workflows were removed, but README/build comments/scripts still describe CI release gates and use GitHub Actions error syntax.
  Evidence: commit `4ebc3f9ec`; `README.md:161`; `build.gradle:19`; `build.gradle:52`; `build.gradle:63`; `versions.gradle:15`; `scripts/verify-release-consistency.sh:55`; `scripts/verify-dependency-floor.sh:42`.
  Touches: `README.md`; `CHANGELOG.md`; `build.gradle`; `versions.gradle`; `scripts/verify-release-consistency.sh`; `scripts/verify-dependency-floor.sh`; reproducible-build docs if needed.
  Acceptance: release/reproducibility/dependency verification is documented as local commands only; scripts print plain CLI-friendly failures; no active docs or build comments claim GitHub Actions/CI runs the gates.
  Complexity: S

- [ ] P2 — Extend version and SDK consistency gates to planning surfaces
  Why: `app/build.gradle` and README report version 0.6.3/compileSdk 37, while ignored planning files still claim older Gradle/AGP/version/SDK state, and the current gate cannot catch that drift.
  Evidence: `app/build.gradle:18`; `app/build.gradle:19`; `versions.gradle:4`; `README.md:14`; `CLAUDE.md:20`; `CLAUDE.md:131`; `PROJECT_CONTEXT.md:13`; `PROJECT_CONTEXT.md:14`; `scripts/verify-release-consistency.sh`.
  Touches: `scripts/verify-release-consistency.sh`; `PROJECT_CONTEXT.md`; `CLAUDE.md`; `README.md`; `CHANGELOG.md`.
  Acceptance: the local consistency script fails when README, CHANGELOG/Fastlane, `versions.gradle`, `PROJECT_CONTEXT.md`, or `CLAUDE.md` disagree with `app/build.gradle` and the current SDK pins; stale planning surfaces are updated.
  Complexity: S

- [ ] P2 — Add explicit backup delete scope for base-only versus all versions
  Why: destructive backup deletion currently deletes base backups only, while named backups remain without an explicit count or opt-in all-versions action.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogFragment.java:382`; Neo Backup/AppDash versioned-backup expectations.
  Touches: `backup/dialog/BackupRestoreDialogFragment.java`; backup dialog ViewModel; `BatchOpsManager` delete-backup path; strings; backup tests.
  Acceptance: delete confirmation shows base and named-backup counts, defaults to base-only, offers an authenticated all-versions option, records the selected scope in operation history, and tests cover base-only versus all-versions deletion.
  Complexity: M

- [ ] P2 — Report unsupported intent extras instead of silently dropping them
  Why: Activity Interceptor command/export text skips unsupported extras without warning, so copied commands and descriptions can omit user-visible intent data.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/intercept/IntentCompat.java:538`; `IntentCompat.java:641`; `IntentCompat.java:687`; Android 18 explicit URI grant guidance.
  Touches: `intercept/IntentCompat.java`; `intercept/ActivityInterceptor.java`; shortcut/export strings; intent compat tests.
  Acceptance: unsupported extras are counted and listed by key/type in command/export descriptions while parsable output remains backwards-compatible; tests cover unsupported Parcelable, Binder, and nested Bundle values.
  Complexity: S

- [ ] P2 — Cancel or generation-skip stale file-manager attribute loads
  Why: file-manager rows launch background attribute caching for uncached items, but the adapter does not own or cancel obsolete jobs during fast scrolls or dataset swaps.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmAdapter.java:158`; file-manager responsiveness expectations from SD Maid SE and AppDash.
  Touches: `fm/FmAdapter.java`; `fm/FmItem`; file-manager adapter tests or Robolectric/source-contract tests.
  Acceptance: stale attribute jobs are cancelled or skipped by generation, recycled rows never show stale attributes after rapid navigation/scrolling, and a host-side test covers generation mismatch behavior.
  Complexity: S

- [ ] P2 — Refresh Gradle wrapper to 9.6.1
  Why: Gradle 9.6.1 is current and the repo is on 9.6.0; the update is low-risk and keeps the local build baseline current.
  Evidence: `gradle/wrapper/gradle-wrapper.properties:4`; Gradle 9.6.1 release notes.
  Touches: `gradle/wrapper/gradle-wrapper.properties`; `gradle/wrapper/gradle-wrapper.jar`; release consistency docs if wrapper SHA handling changes.
  Acceptance: wrapper points to Gradle 9.6.1 with verified distribution metadata; `rtk .\gradlew.bat --version`, `rtk .\gradlew.bat :app:compileFlossDebugJavaWithJavac`, and release/dependency gates pass.
  Complexity: S

- [ ] P3 — Migrate remaining non-dedicated UI helper raw threads
  Why: a few UI/helper paths still create raw threads instead of using project executors, leaving uncaught-exception and lifecycle behavior inconsistent with recent threading hygiene.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/OpenPgpKeySelectionDialogFragment.java:54`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java:270`.
  Touches: `settings/crypto/OpenPgpKeySelectionDialogFragment.java`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java`; related unit/Robolectric tests.
  Acceptance: non-dedicated UI helper work uses `ThreadUtils` or an owned executor with clear main-thread handoff and cancellation/lifecycle behavior; no raw `new Thread()` remains in those UI helper paths.
  Complexity: S

## Research-Driven Additions

- [ ] P2 — Re-enable pre-2026-05-25 ignored Robolectric fixture tests
  Why: five host-side tests are still skipped behind stale `@Ignore` markers, masking ZIP/VFS/TAR/OAB/settings-search regressions and referencing a roadmap item that no longer exists.
  Evidence: `app/src/test/java/androidx/documentfile/provider/ZipDocumentFileTest.java:32`; `app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java:32`; `app/src/test/java/io/github/muntashirakon/AppManager/utils/TarUtilsTest.java:37`; `app/src/test/java/io/github/muntashirakon/AppManager/backup/convert/OABConverterTest.java:31`; `app/src/test/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndexTest.java:21`; JUnit `@Ignore` documentation; Robolectric local-test documentation.
  Touches: `app/src/test/java/androidx/documentfile/provider/ZipDocumentFileTest.java`; `app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java`; `app/src/test/java/io/github/muntashirakon/AppManager/utils/TarUtilsTest.java`; `app/src/test/java/io/github/muntashirakon/AppManager/backup/convert/OABConverterTest.java`; `app/src/test/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndexTest.java`; related fixture resources under `app/src/test/resources`.
  Acceptance: no class-level `@Ignore("env-fixture missing pre-2026-05-25; tracked in ROADMAP.md Test Suite Hygiene")` remains; each test either runs deterministically with local fixtures or is split into supported focused tests; `rtk .\gradlew.bat :app:testFlossDebugUnitTest` exercises the restored coverage.
  Complexity: M

- [ ] P2 — Extend production stack-trace logging cleanup to app and libcore paths
  Why: after app-layer and server-focused logging cleanup, shared file/proc/UI utility paths still print directly to stderr instead of structured or intentionally suppressed diagnostics.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/logs/Log.java:51`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:621`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java:1494`; `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java:123`; `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java:170`; `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java:189`; `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java:242`; `app/src/main/java/io/github/muntashirakon/proc/ProcMappedFiles.java:49`; `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java:575`; `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java:599`; `libcore/ui/src/main/java/io/github/muntashirakon/view/AutoCompleteTextViewCompat.java:30`; `libcore/ui/src/main/java/io/github/muntashirakon/view/AutoCompleteTextViewCompat.java:45`.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/logs/Log.java`; `app/src/main/java/io/github/muntashirakon/io/PathImpl.java`; `app/src/main/java/io/github/muntashirakon/proc/ProcFs.java`; `app/src/main/java/io/github/muntashirakon/proc/ProcMappedFiles.java`; `libcore/io/src/main/java/io/github/muntashirakon/io/Path.java`; `libcore/ui/src/main/java/io/github/muntashirakon/view/AutoCompleteTextViewCompat.java`; source-contract tests.
  Acceptance: no production `printStackTrace()` remains outside the already-active `server/` and `libserver/` diagnostic item; optional reflection/proc/file failures are logged through an app/libcore logger or intentionally ignored with tests; a source-contract test pins the allowed production exception list.
  Complexity: S
