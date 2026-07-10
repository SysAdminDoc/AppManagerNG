<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

- [ ] P3 — Update home-screen widgets on system theme flips
  Why: baked RemoteViews colors go stale until the next widget update (up to 30 min; indefinitely for the clear-cache widget) when the system light/dark mode changes. Needs a CONFIGURATION_CHANGED-driven refresh while the process lives; verify on-device.
  Where: `usage/ScreenTimeAppWidget.java`; `usage/DataUsageAppWidget.java`; `oneclickops/ClearCacheAppWidget.java`; `logcat/helper/WidgetHelper.java`.
- [ ] P3 — Add a night-mode preview for the clear-cache widget
  Why: `drawable-night-nodpi/` has night previews for the screen-time and data-usage widgets but not clear-cache, so the dark widget picker mixes light and dark previews. Needs an asset render.
  Where: `app/src/main/res/drawable-nodpi/app_widget_preview_clear_cache.png`.
- [ ] P3 — Strengthen the weak ZipFileSystem test assertions
  Why: the 2026-06-27 stub implementations assert tautologies — `isHidden()` never calls `isHidden()`, `lastAccess()`/`creationTime()`/`getMode()` assert `>= 0` which passes for the unsupported-default 0, and mounts are not unmounted on assertion failure (global VFS state leaks into later tests).
  Where: `app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java`.
- [ ] P3 — Convert remaining "(s)" pluralization hacks to plurals
  Why: user-visible strings still render "1 backup(s)"-style copy: strings.xml lines with "app(s)", "folder(s)", "APK file(s)", "pattern(s)", "key(s)", "module(s)", "rule(s)", "backup(s)", "permission(s)", "action(s)"; also Title Case drift in `pref_export_diagnostics` and exit-reason labels.
  Where: `app/src/main/res/values/strings.xml` + each consumer call site.

## Research-Driven Additions

- [ ] P3 — Migrate remaining non-dedicated UI helper raw threads
  Why: a few UI/helper paths still create raw threads instead of using project executors, leaving uncaught-exception and lifecycle behavior inconsistent with recent threading hygiene.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/OpenPgpKeySelectionDialogFragment.java:54`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java:270`.
  Touches: `settings/crypto/OpenPgpKeySelectionDialogFragment.java`; `libcore/ui/src/main/java/io/github/muntashirakon/dialog/SearchableMultiChoiceDialogBuilder.java`; related unit/Robolectric tests.
  Acceptance: non-dedicated UI helper work uses `ThreadUtils` or an owned executor with clear main-thread handoff and cancellation/lifecycle behavior; no raw `new Thread()` remains in those UI helper paths.
  Complexity: S

## Research-Driven Additions

## Research-Driven Additions

- [ ] P2 — Rebaseline packaged offline manual source truth
  Why: the packaged manual source still says upstream App Manager v4.0.1 and routes reports/translations to upstream destinations, while generated HTML contains a partial AppManagerNG fork notice, so `:docs:buildDocs` can regress fork identity and support guidance.
  Evidence: `docs/raw/en/intro/main.tex:4`; `docs/raw/en/intro/main.tex:12`; `docs/raw/en/intro/main.tex:40`; `docs/raw/en/intro/main.tex:52`; `docs/raw/en/intro/main.tex:79`; `docs/raw/en/strings.xml:21`; `docs/raw/en/strings.xml:23`; `docs/raw/en/index.html:156`; `docs/raw/en/index.html:185`; `CHANGELOG.md:348`.
  Touches: `docs/raw/en/intro/main.tex`; generated `docs/raw/en/strings.xml`; generated `docs/raw/en/index.html`; docs build/source-contract tests.
  Acceptance: packaged English manual source, generated XML, and generated HTML agree on AppManagerNG identity, current support policy, fork-owned issue destinations, distribution links, and translation status; `rtk .\gradlew.bat :docs:buildDocs` preserves those strings; a grep/source-contract test fails on upstream-only support/version links outside historical changelog or explicit upstream-credit sections.
  Complexity: M

## Research-Driven Additions

- [ ] P2 - Add bounded progress and partial-failure reporting to component-rule reset
  Why: upstream reports "Remove All Rules" freezing; NG's component-rule reset loops every rule through privileged PackageManager/IFW paths and collapses failures into a boolean, leaving users without progress or a recovery ledger.
  Evidence: upstream MuntashirAkon/AppManager#1986; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:401`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:417`; `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java:476`.
  Touches: `app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentsBlocker.java`; component-rule settings UI; operation history; component-rule tests.
  Acceptance: bulk reset exposes determinate package/component progress, keeps UI cancellable/responsive, records per-component successes/failures with retry data, and tests prove partial failures preserve unapplied rules instead of silently reporting a generic false.
  Complexity: M

## Research-Driven Additions

- [ ] P2 — Reconcile local privileged-server port changes with live server state
  Why: the advanced setting saves a new ADB local-server port but only tells the user to restart, while the running server/session and UI status can remain bound to stale state.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/settings/AdvancedPreferences.java:153`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java`; `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerStatusChangeReceiver.java:63`; Thor and Hail working-mode reliability signals.
  Touches: `AdvancedPreferences.java`; `LocalServer.java`; `LocalServerManager.java`; `ServerStatusChangeReceiver.java`; `Ops.java`; privilege health UI/tests.
  Acceptance: changing the local-server port either restarts/rebinds the local server safely or marks the current server stale with actionable status; UI receives a fresh server-state update; tests pin port-change behavior without requiring a device.
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
