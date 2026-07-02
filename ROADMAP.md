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

- [ ] P3 — Migrate remaining non-dedicated UI helper raw threads
  Why: a few UI/helper paths still create raw threads instead of using project executors, leaving uncaught-exception and lifecycle behavior inconsistent with recent threading hygiene.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/OpenPgpKeySelectionDialogFragment.java:54`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java:270`.
  Touches: `settings/crypto/OpenPgpKeySelectionDialogFragment.java`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java`; related unit/Robolectric tests.
  Acceptance: non-dedicated UI helper work uses `ThreadUtils` or an owned executor with clear main-thread handoff and cancellation/lifecycle behavior; no raw `new Thread()` remains in those UI helper paths.
  Complexity: S

## Research-Driven Additions

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

## Research-Driven Additions

- [ ] P2 - Add bounded progress and partial-failure reporting to component-rule reset
  Why: upstream reports "Remove All Rules" freezing; NG's component-rule reset loops every rule through privileged PackageManager/IFW paths and collapses failures into a boolean, leaving users without progress or a recovery ledger.
  Evidence: upstream MuntashirAkon/AppManager#1986; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:401`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:417`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:476`.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java`; component-rule settings UI; operation history; component-rule tests.
  Acceptance: bulk reset exposes determinate package/component progress, keeps UI cancellable/responsive, records per-component successes/failures with retry data, and tests prove partial failures preserve unapplied rules instead of silently reporting a generic false.
  Complexity: M

## Research-Driven Additions

- [ ] P1 — Exclude or rotate local privileged-server secrets from Android backup and transfer
  Why: Android cloud/D2D backup rules include all shared preferences, but the local privileged-server handshake token is persisted in `server_config` shared preferences.
  Evidence: `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/full_backup_rules.xml`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java`; Android Auto Backup documentation.
  Touches: `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/full_backup_rules.xml`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java`; backup-rule/source-contract tests.
  Acceptance: local server authentication tokens are never restored unchanged from cloud/D2D backup; non-secret preferences are intentionally preserved or intentionally excluded; a host/source-contract test fails if secret-bearing preference files become backup-eligible again.
  Complexity: S

- [ ] P2 — Reconcile local privileged-server port changes with live server state
  Why: the advanced setting saves a new ADB local-server port but only tells the user to restart, while the running server/session and UI status can remain bound to stale state.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/AdvancedPreferences.java:153`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerStatusChangeReceiver.java:63`; Thor and Hail working-mode reliability signals.
  Touches: `AdvancedPreferences.java`; `LocalServer.java`; `LocalServerManager.java`; `ServerStatusChangeReceiver.java`; `Ops.java`; privilege health UI/tests.
  Acceptance: changing the local-server port either restarts/rebinds the local server safely or marks the current server stale with actionable status; UI receives a fresh server-state update; tests pin port-change behavior without requiring a device.
  Complexity: M

- [ ] P2 — Preserve Activity Interceptor output when extras contain unknown Parcelables
  Why: one `BadParcelableException` currently nulls the entire generated intent URI, so users lose actionable action/data/component output when only an extra is unreadable.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/intercept/ActivityInterceptor.java:1227`; Android `BadParcelableException` documentation; Android Parcelables and Bundles documentation.
  Touches: `ActivityInterceptor.java`; `IntentCompat.java`; interceptor UI strings; unit/Robolectric tests for unknown Parcelable extras.
  Acceptance: an intercepted intent with an unknown Parcelable still displays/export base action, data, component, categories, and safe extras; skipped extras are counted and named when possible; cloning malformed intent URIs fails visibly without crashing.
  Complexity: M

- [ ] P3 — Codify the Robolectric SDK36 and JDK test matrix
  Why: Robolectric 4.16 supports SDK36 but documents JDK21 as required for SDK36-target tests, while project setup docs still say JDK 17+.
  Evidence: `BUILDING.rst:12`; `app/build.gradle`; `versions.gradle`; Robolectric 4.16 release notes.
  Touches: `BUILDING.rst`; Gradle test configuration; local verification scripts/docs; test source-contract checks.
  Acceptance: contributors get a single documented JVM requirement for app builds versus SDK36 Robolectric tests; local test commands fail early with an actionable message or pin compatible SDK/toolchain behavior; `:app:testFlossDebugUnitTest` requirements are unambiguous.
  Complexity: S

- [ ] P3 — Complete runtime feature truth in the System Config viewer
  Why: the root-only system configuration surface still has commented AOSP runtime feature additions for encryption, adoptable storage, incremental delivery, and app enumeration.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/sysconfig/SystemConfig.java:1248`; upstream App Manager system-configuration feature documentation; Android `PackageManager` feature documentation.
  Touches: `SystemConfig.java`; `SysConfigWrapper.java`; `SystemConfigTest.java`; any sysconfig UI labels for unknown/runtime-only features.
  Acceptance: available public/compat runtime feature sources are added with API guards, unavailable hidden-only checks are represented as unknown instead of silently absent, and tests pin low-RAM plus runtime feature behavior.
  Complexity: M
