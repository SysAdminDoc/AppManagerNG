<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Phase 3 Rollout Plan

Goal: ship the facelift without changing behavior. The new design enters behind a feature toggle, expands screen by screen, then becomes default after screenshot and regression coverage is stable.

Screenshot convention:

- Capture each changed screen in light, dark, AMOLED, and `w600dp` landscape.
- Use the same device density for before/after pairs. Record device model, Android version, density bucket, font scale, display size, and theme mode beside each screenshot set.
- Include at least one dense data state and one empty/loading/error state per migrated surface.
- Keep screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/` only when they are release-ready; exploratory audit screenshots should live under `docs/raw/screenshots/<release>/`.

## v0.4.x: Foundation

Theme: Foundation.

Toggle:

- Add `Settings -> Appearance -> Preview new design (BETA)`.
- Default OFF.
- Store as a new preference key. Do not remove or rename existing theme settings.

Scope:

- Add v2 resource files beside existing resources:
  - `app/src/main/res/values/colors-v2.xml`
  - `app/src/main/res/values-night/colors-v2.xml`
  - `app/src/main/res/values/dimens-v2.xml`
  - `app/src/main/res/values-w600dp/dimens-v2.xml`
  - `app/src/main/res/values/themes-v2.xml`
- Add `AppTheme.V2`, `AppTheme.V2.Dark`, `AppTheme.V2.Amoled`.
- Add styles for toolbar, list rows, chips, dialogs, sheets, buttons, empty states, and skeletons.
- Wire theme selection only at activity theme application time; no layout rewrites yet.
- Add Pro Mode toggle copy using new string keys only; do not delete existing localized strings.

Risk:

- Theme attr mismatch can crash during inflation.
- Dark/AMOLED mappings can regress contrast.
- Preference interaction can override existing dynamic color/theme choices.
- AppWidget themes may inherit unwanted v2 colors if parent linkage is too broad.

Verification:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:lint`
- Smoke launch Main, Settings, App Details, App Usage, Debloater, File Manager, Log Viewer.
- Toggle v2 ON/OFF and rotate after each toggle.
- Verify AppWidgets still render using RemoteViews and system widget radii.
- TalkBack focus through Settings Appearance section and Main list.

Rollback:

- Disable the toggle server-side equivalent is not available, so rollback is a patch release that forces default OFF and hides the preference.
- Keep old `AppTheme` untouched so reverting only removes preference routing.

Screenshot checklist:

- Main list with 100+ apps: light/dark/AMOLED/`w600dp` landscape.
- Settings Appearance toggle: light/dark/AMOLED/`w600dp` landscape.
- AppWidget previews on API 31+ and API 21 emulator.
- Font scale 1.0 and 1.3.

## v0.5.x: Migrate the Top 5

Theme: Highest-traffic surfaces.

Toggle:

- Still gates activation.
- Default OFF.

Scope:

- Migrate:
  - MainActivity shell: `activity_main.xml`
  - Main list row: `item_main.xml` plus `layout-w600dp/item_main.xml`
  - App Details header: new/updated header layout inside App Details pager stack
  - Settings: `activity_settings.xml`, `activity_settings_dual_pane.xml`, preference row styles
  - Filter/sort sheet: `dialog_list_options.xml` and chip/filter styles
- Add semantic badge drawables/selectors for tracker and dangerous-permission thresholds.
- Add shimmer/skeleton drawable resources without adding third-party dependencies.
- Preserve every bound ID in `MainActivity`, `MainRecyclerAdapter`, `SettingsActivity`, and App Details fragments.

Risk:

- Main row ID changes can break binding or click listeners.
- MainActivity search is injected through the ActionBar custom view; a visual search rewrite can accidentally break market search deep-link prefill.
- Settings dual-pane can regress width thresholds or secondary pane restoration.
- Semantic badge tinting can conflict with adapter-set text colors.
- Skeleton loading can hide real empty states if visibility transitions are wrong.

Verification:

- Grep `findViewById(R.id.*)` in `MainActivity`, `MainRecyclerAdapter`, `SettingsActivity`, and App Details fragments against migrated layouts.
- Launch from launcher and from `market://search/?q=<query>`.
- Main list: search, clear search, quick filters, clear filters, sort chip, pull-to-refresh, empty filtered result, tracker badge tap, dangerous permission badge tap, icon tap, icon long-press, card long-press multi-select.
- Settings: phone single pane and `w600dp` dual pane, back stack, deep link `app-manager://settings`.
- App Details: all tabs still load and back navigation still works.
- Root/ADB operations not directly changed, but smoke one non-destructive action path and one privileged dialog path.

Rollback:

- Toggle OFF returns users to classic layouts.
- If a migrated layout crashes with toggle ON, ship patch that forces classic resource selection for the failing surface only.
- Keep classic layout files until v0.7.x.

Screenshot checklist:

- Main list: normal, selected row, trackers high, backup present, empty filter result, loading skeleton.
- App Details header: user app, system app, app with trackers.
- Settings: top level, detail page, dual pane.
- Filter/sort sheet: default, active filters, destructive clear affordance.
- All sets in light/dark/AMOLED/`w600dp` landscape.

## v0.6.x: Migrate the Long Tail

Theme: Complete coverage and opt-out.

Toggle:

- Default ON.
- Rename user-facing toggle to `Restore classic look`.
- Keep opt-out available for one release train.

Scope:

- Migrate remaining high-volume and risk-sensitive layouts:
  - App Usage: `activity_app_usage.xml`, `item_app_usage.xml`, details dialog.
  - Debloater: `activity_debloater.xml`, `item_debloater.xml`, bloatware details/options dialogs.
  - Installer: installer activity/options/dialogs.
  - Backup/restore dialogs and task rows.
  - Profiles and One Click Ops.
  - Logcat, File Manager, Shared Preferences, SysConfig, Finder, Running Apps.
  - AppWidgets within RemoteViews limits.
- Add accessibility pass for every dialog/action sheet.
- Normalize all empty/loading/error states.
- Replace raw color usage with semantic tokens where behavior allows.

Risk:

- Long-tail dialogs contain operation-specific forms; visual regrouping can change perceived meaning even if code does not.
- RemoteViews do not support all Material/View features.
- File Manager and terminal have density and monospace needs that differ from the main app.
- Privileged operation dialogs must keep destructive hierarchy unambiguous.

Verification:

- Full debug build and lint.
- Manual smoke per manifest activity and activity alias.
- Deep-link smoke for settings, scanner, editor, manifest/app-info aliases, file manager directory intent, log viewer `.am.log`, package installer flows.
- AppWidget add/update/delete on API 21, API 31+, and current target device.
- TalkBack pass on destructive dialogs, lists, Settings, and empty states.
- Contrast spot checks for every semantic state.

Rollback:

- User can opt out with `Restore classic look`.
- Patch release can default opt-out ON again if a long-tail operation screen blocks release.
- Keep classic resources and routing until v0.7.x ships.

Screenshot checklist:

- App Usage rows/details, Debloater safety rows, Installer options, Backup/Restore, Profiles, Logcat, File Manager, Running Apps, Finder.
- For each: normal data, empty, loading/progress, destructive confirmation where applicable.
- Light/dark/AMOLED/`w600dp` landscape, font scale 1.0 and 1.3 for representative screens.

## v0.7.x: Remove the Toggle

Theme: New design becomes the only design.

Toggle:

- Remove `Preview new design (BETA)` / `Restore classic look`.
- Delete classic theme routing after migration metrics and manual QA are clean.

Scope:

- Promote `AppTheme.V2` family to `AppTheme` or make `AppTheme` inherit the v2 mapping.
- Delete obsolete classic-only styles/drawables/layout variants after verifying no references remain.
- Update `CHANGELOG.md` with migration end-state and user-facing behavior statement: visual update only, no feature removals.
- Update README screenshots and fastlane screenshots after final approval.
- Refresh adaptive icon/monochrome icon only if branding decision is final.

Risk:

- Removing old resources can expose forgotten references.
- Downstream distributors may compare screenshots and flag large UI changes; release notes must be explicit.
- Users lose visual rollback, so the new design must have full coverage.

Verification:

- `./gradlew clean :app:assembleDebug :app:lint`
- Resource reference grep for deleted classic names.
- Manifest/deep-link/AppWidget smoke matrix repeated.
- Accessibility/contrast pass repeated in light/dark/AMOLED.
- Verify no old toggle preference affects startup.

Rollback:

- Before merge, keep a branch/tag with v0.6.x classic resources.
- After release, rollback is a v0.7.x patch that restores v0.6.x resource routing from the tag.
- Do not delete classic resources in the same commit that flips default; delete only after one clean release candidate.

Screenshot checklist:

- Final release screenshots for README, fastlane, Obtainium/GitHub release notes.
- Required set: Main, App Details, Settings, Debloater, App Usage, Installer, Backup/Restore, File Manager, Logcat, AppWidgets.
- Each set captured in light/dark/AMOLED/`w600dp` landscape with recorded density and font scale.
