<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 127 — Material You widget theming

## Roadmap row

Premium-Polish **Material You / Monet Widget Theming** is shipped.

## What changed

- Added `AppWidgetThemeUtils`, a shared RemoteViews palette helper for home
  screen widgets.
- Replaced screen-time widget hard-coded usage-bubble colors with Material
  primary, secondary, and tertiary container roles.
- Applied runtime widget-surface, text, refresh-icon, and action-icon tints to
  Screen Time, Data Usage, Clear Cache, and Log Recording widgets.
- Updated widget XML layouts to use Material color attrs for previews and
  pre-runtime fallback.
- Added Android 12+ AppWidget theme mappings to `system_accent*` and
  `system_neutral*` resources so widget picker previews inherit Monet colors on
  launchers that render the preview layout directly.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Local validation

- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.

## Notes

- The first build attempt caught an invalid `MaterialColors` attr reference for
  `colorPrimary`; the final helper resolves that through the AppCompat theme
  attr instead.
