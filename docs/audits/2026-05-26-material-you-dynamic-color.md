<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Material You Dynamic-Color Audit (T21-J)

Date: 2026-05-26.
Status: source path audit clean; manual device walkthrough remains open.

## Why this audit

T21-J asks us to confirm both the v2 design tokens and the widget palette
honor Android 12+ wallpaper-derived `dynamic_*` colors, and that the
graceful fallback still works when dynamic colors are disabled or
unsupported (Android 11 and older, OEM ROMs that drop `monet`, AMOLED-only
configs, etc.).

## Findings

### Activity path

- [`AppearanceUtils.ActivityAppearanceCallback.onActivityPreCreated`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java)
  calls `DynamicColors.applyToActivityIfAvailable(activity)` after applying
  the configured base theme. The Material library decides at runtime whether
  the device exposes the `system_accent*` / `system_neutral*` overlay; if
  not, the brand palette baked into [`themes-v2.xml`](../../app/src/main/res/values/themes-v2.xml)
  applies unchanged.
- The same `AppearanceUtils` static activity-lifecycle callback re-applies
  dynamic colors when the appearance preference changes, so a user who
  toggles theme / AMOLED keeps the wallpaper-derived overlay in sync.

### Widget path

- [`AppearanceUtils.getThemedWidgetContext`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java)
  wraps the widget provider's `Context` through
  `DynamicColors.wrapContextIfAvailable(newCtx)` before the widget palette
  resolves any color.
- [`AppWidgetThemeUtils.getPalette`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppWidgetThemeUtils.java)
  reads every color through `MaterialColors.getColor(context, attr, fallback)`,
  passing a brand fallback that is non-zero in the v2 palette. That means a
  device with no Material You overlay still renders a coherent widget, and
  a device with the overlay sees the wallpaper-derived tones.
- The widget surface tint paths are gated on Android S+
  (`Build.VERSION_CODES.S`), so the dynamic-color overlay only mutates the
  pixels where the platform supports the corresponding `RemoteViews` color
  setter. Pre-Android-12 widgets fall back to the static XML layout palette.

### Theme fallback

- [`themes-v2.xml`](../../app/src/main/res/values/themes-v2.xml) declares
  every M3 color attribute (primary, primary-container, secondary, tertiary,
  error, surface, surface-variant, surface-container, outline) against
  `@color/premium_*` resources. Dynamic-color overlays from Material override
  these at runtime where available; the static values keep the build working
  on pre-Android-12 devices and OEM ROMs that drop the overlay.
- AMOLED and dark variants follow the same pattern, so dynamic colors are
  free to override into either base.
- No theme-level `dynamicColor="false"` opt-out exists. There is no current
  user-preference path that disables dynamic colors; the legacy roadmap
  notes some users may eventually want this as an accessibility toggle,
  but no row exists for it yet.

### High-contrast theme interaction

- The 2026-05-26 high-contrast theme pass did not introduce a separate
  theme variant; it hardened text within the existing v2 palette by
  replacing hardcoded warning HTML spans with `colorError` / `colorOnError`
  pairs. Because those pairs are M3 attributes, the dynamic-color overlay
  still drives them when active.

## Fixed in this pass

- Added [`DynamicColorContractTest`](../../app/src/test/java/io/github/muntashirakon/AppManager/accessibility/DynamicColorContractTest.java)
  to pin three regression-prevention invariants:
  - `AppearanceUtils.ActivityAppearanceCallback` keeps calling
    `DynamicColors.applyToActivityIfAvailable` so the per-activity overlay
    cannot be silently dropped.
  - `AppearanceUtils.getThemedWidgetContext` keeps calling
    `DynamicColors.wrapContextIfAvailable` so widget palettes still see the
    overlay.
  - `AppWidgetThemeUtils.getPalette` keeps calling `MaterialColors.getColor`
    with non-zero fallbacks, so the palette never returns black on devices
    without the dynamic-color overlay.

## Still open

- Manual device walkthrough on:
  - A stock Pixel 8+ (Android 14/15) confirming wallpaper-derived accent
    colors propagate to App List, App Details, Settings, Backup/Restore,
    Logcat viewer, and every shipped widget (Clear Cache, Data Usage,
    Screen Time, Log Recording).
  - An OEM ROM that disables Material You (e.g. some HyperOS / OneUI
    builds) confirming the brand palette still renders without empty
    contrast pairs.
  - An Android 11-or-older device where `DynamicColors.applyToActivity` is
    a no-op, confirming the brand palette is unchanged.
- Optional future work: a Settings -> Appearance -> "Use system wallpaper
  colors" toggle for users who prefer the NG brand palette over the
  wallpaper overlay. The infrastructure for the toggle exists in
  `Prefs.Appearance`; the implementation would call a custom
  `DynamicColorsOptions.Builder().setPrecondition(...)` from
  `Application.onCreate` and re-apply via `applyConfigurationChangesToActivities`.
