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

## Research-Driven Additions

- [ ] P2 — Rebaseline packaged offline manual source truth
  Why: the packaged manual source still says upstream App Manager v4.0.1 and routes reports/translations to upstream destinations, while generated HTML contains a partial AppManagerNG fork notice, so `:docs:buildDocs` can regress fork identity and support guidance.
  Evidence: `docs/raw/en/intro/main.tex:4`; `docs/raw/en/intro/main.tex:12`; `docs/raw/en/intro/main.tex:40`; `docs/raw/en/intro/main.tex:52`; `docs/raw/en/intro/main.tex:79`; `docs/raw/en/strings.xml:21`; `docs/raw/en/strings.xml:23`; `docs/raw/en/index.html:156`; `docs/raw/en/index.html:185`; `CHANGELOG.md:348`.
  Touches: `docs/raw/en/intro/main.tex`; generated `docs/raw/en/strings.xml`; generated `docs/raw/en/index.html`; docs build/source-contract tests.
  Acceptance: packaged English manual source, generated XML, and generated HTML agree on AppManagerNG identity, current support policy, fork-owned issue destinations, distribution links, and translation status; `rtk .\gradlew.bat :docs:buildDocs` preserves those strings; a grep/source-contract test fails on upstream-only support/version links outside historical changelog or explicit upstream-credit sections.
  Complexity: M

- [ ] P2 — Restore archive-link truth for tracked documentation
  Why: README/ROADMAP/docs index pages link to archive directories and files that are absent from tracked Git content, so published GitHub docs can point readers at missing history while local archive markdown remains untracked.
  Evidence: `README.md:96`; `ROADMAP.md:5`; `docs/roadmap/README.md:14`; `docs/roadmap/README.md:16`; `git ls-files docs/archive docs/roadmap/archive docs/patch-references docs/raw/changelog_old.md`; GitHub ignore-file documentation.
  Touches: `README.md`; `ROADMAP.md`; `docs/roadmap/README.md`; `.gitignore`; docs link/source-contract checks.
  Acceptance: every tracked link to `docs/archive/`, `docs/roadmap/archive/`, or `docs/patch-references/` either resolves in `git ls-files` or is removed/reworded to local-only; a clean docs build/research pass no longer leaves archive markdown directories as unexpected untracked files; a docs-link/source-contract check covers archive links.
  Complexity: S

- [ ] P3 — Ignore local JVM crash and replay artifacts under app
  Why: failed Gradle/test JVM runs leave `hs_err_pid*.log` and `replay_pid*.log` files under `app/`, polluting the worktree and increasing accidental staging risk.
  Evidence: `app/hs_err_pid10832.log`; `app/replay_pid10832.log`; `.gitignore`; GitHub ignore-file documentation.
  Touches: `.gitignore`.
  Acceptance: `.gitignore` excludes `app/hs_err_pid*.log` and `app/replay_pid*.log`; existing local logs remain untracked and ignored after `rtk git status --short --ignored`; no crash/replay logs are staged.
  Complexity: S

## Research-Driven Additions

- [ ] P2 - Add bounded progress and partial-failure reporting to component-rule reset
  Why: upstream reports "Remove All Rules" freezing; NG's component-rule reset loops every rule through privileged PackageManager/IFW paths and collapses failures into a boolean, leaving users without progress or a recovery ledger.
  Evidence: upstream MuntashirAkon/AppManager#1986; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:401`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:417`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:476`.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java`; component-rule settings UI; operation history; component-rule tests.
  Acceptance: bulk reset exposes determinate package/component progress, keeps UI cancellable/responsive, records per-component successes/failures with retry data, and tests prove partial failures preserve unapplied rules instead of silently reporting a generic false.
  Complexity: M
