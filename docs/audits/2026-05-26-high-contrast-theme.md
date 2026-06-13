<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# High-Contrast Theme Audit Slice

Date: 2026-05-26.
Status: partial static hardening; manual screen/device audit remains open.

## Scope

- v2 light and night color-token pairs in `colors-v2.xml` /
  `values-night/colors-v2.xml`.
- App strings that hardcoded HTML warning color.
- Shared palette regressions that can be pinned by JVM resource tests.

## Fixed

- Removed three hardcoded `<font fgcolor="#ff0000">` warning spans from app
  strings. The replacement uses styled warning labels without forcing a literal
  red that fails normal-text contrast on light surfaces and ignores dark theme
  colors.
- Added a resource contract test that verifies v2 high/medium text, primary and
  secondary pairs, and semantic success/warning/danger/info pairs meet a 4.5:1
  contrast floor in both light and night resources.
- Added a guard against reintroducing hardcoded warning-red `<font fgcolor>`
  spans in app strings.

## Still Open

- Manual high-contrast walkthrough of the major screens on device/emulator:
  Main list, App Details, Backup/Restore, File Manager, Code Editor, Logcat,
  Settings, dialogs, sheets, and onboarding.
- Platform high-contrast mode behavior where OEMs alter system color or font
  rendering.
- Screenshot-based visual QA for disabled text, icons used without text, and
  non-text contrast such as 1 dp strokes and separators.
