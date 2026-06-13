<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Dyslexia-font Compatibility Audit

Date: 2026-05-26.
Status: source hardening complete; manual device verification remains open.

## Why this audit

Several Android distributions and OEM accessibility surfaces let users choose a
dyslexia-friendly system font (LineageOS ships an OpenDyslexic option, Samsung
exposes "Accessibility -> Visibility enhancements -> Bold font / large font /
custom font", and several Pixel ROMs route through a system-font replacement
mod). Any app that pins `android:fontFamily` at the theme level, or that uses
hand-rolled `Typeface.create(...)` calls on body-level text, silently
overrides the user's choice.

The roadmap row "Dyslexia-font compatibility audit" had no prior code work, so
this pass establishes the audit boundary and prevents regression.

## Findings

### Theme-level fonts (audit clean)

- `app/src/main/res/values/themes-v2.xml`: no `android:fontFamily` attributes.
- `app/src/main/res/values/styles.xml`: no `android:fontFamily` attributes.
- `app/src/main/res/values/libs.xml`: the `fontFamily` substring appears only
  inside `com.mikepenz.community_material_typeface_library` (an icon-font
  package name), not as a style attribute.

NG already honors the system-wide font preference everywhere it inherits from
the Material 3 theme.

### Per-layout font overrides

26 layouts override `android:fontFamily` or `android:typeface`. After this
pass:

- **25** force `monospace` (or `Typeface.MONOSPACE` from Java) on intentional
  technical surfaces: code editor, hex viewer, logcat viewer, terminal,
  passwords / PINs / keystore inputs, package / class / SSAID identifiers,
  running-process state lines, profile-apply preview, and the in-process UI
  tracker overlay. Monospace is the right choice for these surfaces; readers
  rely on glyph alignment, and dyslexia-targeted proportional fonts would
  hurt rather than help. **These remain monospace.**
- **1** previously forced `sans-serif-condensed` on the record-log launcher
  widget subtitle (`app/src/main/res/layout/widget_recording.xml`). That
  override is removed in the same pass so the widget honors the user's
  system font choice (including dyslexia-targeted replacements). The text
  size is unchanged.

### Java / Kotlin `setTypeface` call sites

- `UIUtils.setTypefaceSpan(...)` and `UIUtils.getMonospacedText(...)` apply a
  span-level `TypefaceSpan("monospace")` for inline package / class /
  identifier rendering. These are the same technical-surface rationale and
  remain.
- `UIUtils.getPrimaryText(...)` uses `sans-serif-medium` purely to bold
  small inline emphasis (App Info "primary" lines). This is a weight cue,
  not a typeface override; the system "Bold font" accessibility option
  already produces the same visual effect.
- `Typeface.MONOSPACE` references in `BackupFragment`, `VirusTotalPreferences`,
  `LogViewerPreferences`, `BackupRestorePreferences`, `ConfPreferences`,
  `CodeEditorFragment`, `ClassListingFragment`,
  `AppInfoRecyclerAdapter.subtitle`, `CursorTableAdapter`, and
  `HexLineAdapter` all guard inputs / data dumps where monospace is
  load-bearing. These remain.
- `UIUtils.drawCenteredText(...)` falls back to `Typeface.SANS_SERIF` only
  when no caller-supplied typeface is provided. That is a bitmap-rendering
  helper, not a user-facing TextView style, and is out of scope.

## Fixed in this pass

- Removed the `android:fontFamily="sans-serif-condensed"` override from the
  record-log launcher widget subtitle so the system font - including any
  dyslexia-friendly replacement - reaches the only non-technical surface
  where it was suppressed.
- Added `DyslexiaFontCompatibilityContractTest` to pin three invariants:
  - `themes-v2.xml` and `styles.xml` never introduce a global
    `android:fontFamily` (which would override the user's system font
    everywhere).
  - The known set of intentional monospace surfaces stays monospace, so a
    drive-by removal cannot regress code / hex / log alignment.
  - The recording widget keeps no font-family override, so the system font
    keeps cascading.

## Still open

- Manual device walkthrough on a ROM with a dyslexia-friendly system font
  (LineageOS OpenDyslexic, Samsung Accessibility "Bold font" + custom font,
  or a Magisk Font Override module) covering: main app list, App Details
  primary/secondary/storage sections, Settings tree, Backup/Restore dialogs,
  Logcat viewer (should remain monospace), and the record-log widget on a
  launcher.
- Material Components internals: dialog titles, snackbars, and bottom
  sheets all inherit from `?attr/fontFamily`, which is `null` at the
  AppCompat / M3 layer in this audit. Confirm that the OEM font path
  routes through these.
- Optional future work: an in-app "Accessibility -> Reading" Settings
  screen that links out to the system font picker for users who do not
  know where the OS-level toggle lives.
