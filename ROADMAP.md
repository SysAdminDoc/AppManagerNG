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
- [ ] P3 — Convert remaining "(s)" pluralization hacks to plurals
  Why: user-visible strings still render "1 backup(s)"-style copy: strings.xml lines with "app(s)", "folder(s)", "APK file(s)", "pattern(s)", "key(s)", "module(s)", "rule(s)", "backup(s)", "permission(s)", "action(s)"; also Title Case drift in `pref_export_diagnostics` and exit-reason labels.
  Where: `app/src/main/res/values/strings.xml` + each consumer call site.

## Research-Driven Additions

## Research-Driven Additions

## Research-Driven Additions

## Research-Driven Additions

## Research-Driven Additions

- [ ] P3 — Complete runtime feature truth in the System Config viewer
  Why: the root-only system configuration surface still has commented AOSP runtime feature additions for encryption, adoptable storage, incremental delivery, and app enumeration.
  Evidence: `app/src/main/java/io/github/muntashirakon/AppManager/sysconfig/SystemConfig.java:1248`; upstream App Manager system-configuration feature documentation; Android `PackageManager` feature documentation.
  Touches: `SystemConfig.java`; `SysConfigWrapper.java`; `SystemConfigTest.java`; any sysconfig UI labels for unknown/runtime-only features.
  Acceptance: available public/compat runtime feature sources are added with API guards, unavailable hidden-only checks are represented as unknown instead of silently absent, and tests pin low-RAM plus runtime feature behavior.
  Complexity: M
