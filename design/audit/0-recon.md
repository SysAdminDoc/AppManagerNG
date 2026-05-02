<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Phase 0 Recon

Date: 2026-05-02

Scope: `C:\Users\--\repos\AppManagerNG`, Android Views, Material Components 1.13, minSdk 21, targetSdk 36.

## Source Truth

- `app/src/main/res/values/themes.xml` does not exist in this checkout. Theme declarations are in `app/src/main/res/values/styles.xml`, `app/src/main/res/values-v31/styles.xml`, `app/src/main/res/values-night-v31/styles.xml`, and `libcore/ui/src/main/res/values/styles.xml`.
- `app/src/main/res/values-night/colors.xml` does not exist. There is one app color file at `app/src/main/res/values/colors.xml`; the larger named-color palette is in `libcore/ui/src/main/res/values/colors.xml`.
- The manifest declares 39 concrete activities and 4 activity aliases, matching the recent adaptive-layout audit's 43 activity-level entries.
- Recent tablet density overrides exist in `app/src/main/res/values-w600dp/dimens.xml` and `libcore/ui/src/main/res/values-w600dp/dimens.xml`; this pass must extend them instead of replacing them.

## Manifest Surface Map

Concrete activities:

| Activity | Exported | Label | Theme | Intent filters |
| --- | --- | --- | --- | --- |
| `.filters.FinderActivity` | false | `@string/finder_title` | default `AppTheme` | 0 |
| `.oneclickops.OneClickOpsActivity` | false | `@string/one_click_ops` | default `AppTheme` | 0 |
| `.runningapps.RunningAppsActivity` | true | `@string/running_apps` | default `AppTheme` | 0 |
| `.settings.SettingsActivity` | true | `@string/app_settings` | default `AppTheme` | 2, including `APPLICATION_PREFERENCES` and `app-manager://settings` |
| `.sharedpref.SharedPrefsActivity` | false | none | default `AppTheme` | 0 |
| `.usage.AppUsageActivity` | true | none | default `AppTheme` | 0 |
| `.editor.CodeEditorActivity` | false | `@string/title_code_editor` | default `AppTheme` | 0 |
| `.scanner.ScannerActivity` | true | `@string/scanner` | default `AppTheme` | SEND/VIEW/install/package/file/camera/music filters |
| `.viewer.ExplorerActivity` | true | `@string/explore` | `AppTheme.TransparentBackground` | file/content VIEW |
| `.main.MainActivity` | true | none | default `AppTheme` | market search deep links and main entry handoff |
| `.terminal.TermActivity` | false | `@string/title_terminal_emulator` | default `AppTheme` | 0 |
| `.accessibility.activity.LeadingActivityTrackerActivity` | false | `@string/title_ui_tracker` | `AppTheme.TransparentBackground` | 0 |
| `.misc.LabsActivity` | true | `@string/title_labs_activity` | default `AppTheme` | 0 |
| `.debloat.DebloaterActivity` | false | `@string/debloater_title` | default `AppTheme` | 0 |
| `.details.AppDetailsActivity` | false | none | default `AppTheme` | 0 |
| `.details.manifest.ManifestViewerActivity` | true | `@string/manifest_viewer` | default `AppTheme` | manifest VIEW |
| `.apk.installer.PackageInstallerActivity` | true | `@string/install` | `AppTheme.TransparentBackground` | install/send/view package filters |
| `.crypto.OpenPGPCryptoActivity` | false | none | `AppTheme.TransparentBackground` | 0 |
| `.crypto.ks.KeyStoreActivity` | false | none | `AppTheme.TransparentBackground` | 0 |
| `.profiles.ProfilesActivity` | true | `@string/profiles` | default `AppTheme` | 0 |
| `.profiles.AppsProfileActivity` | false | none | default `AppTheme` | 0 |
| `.profiles.AppsFilterProfileActivity` | false | none | default `AppTheme` | 0 |
| `.sysconfig.SysConfigActivity` | false | `@string/sys_config` | default `AppTheme` | 0 |
| `.batchops.BatchOpsResultsActivity` | false | none | default `AppTheme` | 0 |
| `.history.ops.OpHistoryActivity` | true | `@string/op_history` | default `AppTheme` | 0 |
| `.intercept.ActivityInterceptor` | true | `@string/interceptor` | default `AppTheme` | broad intercept filters |
| `.misc.HelpActivity` | true | none | default `AppTheme` | 0 |
| `.crypto.auth.AuthManagerActivity` | false | `@string/auth_manager_title` | default `AppTheme` | 0 |
| `.crypto.auth.AuthFeatureDemultiplexer` | true | none | default `AppTheme` | 0 |
| `.details.ActivityLauncherShortcutActivity` | false | none | `AppTheme.TransparentBackground` | 0 |
| `.apk.behavior.FreezeUnfreezeActivity` | false | none | `AppTheme.TransparentBackground` | 0 |
| `.profiles.ProfileApplierActivity` | false | none | `AppTheme.TransparentBackground` | 0 |
| `.main.SplashActivity` | true | none | `AppTheme.Splash` | MAIN, LAUNCHER, LEANBACK_LAUNCHER |
| `.logcat.LogViewerActivity` | true | `@string/log_viewer` | default `AppTheme` | SEND/VIEW `.am.log` |
| `.logcat.RecordLogDialogActivity` | true | none | `AppTheme.TransparentBackground` | 0 |
| `.fm.FmActivity` | true | `@string/files` | default `AppTheme` | directory browse filters |
| `.fm.OpenWithActivity` | false | `@string/file_open_with` | `AppTheme.TransparentBackground` | 0 |
| `.viewer.audio.AudioPlayerActivity` | true | `@string/title_audio_player` | `AppTheme.TransparentBackground` | audio SEND/SEND_MULTIPLE/VIEW |
| `.DummyActivity` | default | none | `@android:style/Theme.NoDisplay` | 0 |

Activity aliases:

| Alias | Target | Exported | Intent filters |
| --- | --- | --- | --- |
| `.editor.EditorActivity` | `.editor.CodeEditorActivity` | true | 3 |
| `.apk.installer.AppInfoActivity` | `.details.AppDetailsActivity` | true | 1 |
| `.details.AppInfoActivity` | `.details.AppDetailsActivity` | true | 4 |
| `.fm.FilesActivity` | `.fm.FmActivity` | true | 1 |

AppWidgets and receivers that must keep behavior unchanged:

- `.usage.ScreenTimeAppWidget`, `.usage.DataUsageAppWidget`, `.oneclickops.ClearCacheAppWidget`, `.logcat.RecordingWidgetProvider`.
- `app_widget_screen_time*.xml`, `app_widget_data_usage_small.xml`, `app_widget_clear_cache.xml`, `widget_recording.xml`, and their provider XML must stay compatible with RemoteViews limits.

## Theme and Style Inventory

App module style declarations:

| File | Styles |
| --- | --- |
| `app/src/main/res/values/styles.xml` | `AppTheme.Splash <- Theme.SplashScreen`; `AppTheme.Splash.Black <- AppTheme.Splash`; `AppTheme.AppWidgetContainerParent <- AppTheme`; `AppTheme.AppWidgetContainer <- AppTheme.AppWidgetContainerParent`; `AppTheme.AppWidgetContainer.IconOnly <- AppTheme.AppWidgetContainerParent`; `Widget.AppTheme.AppWidgetOverlay <- android:Widget`; `Widget.AppTheme.AppWidget <- Widget.AppTheme.AppWidgetOverlay`; `Widget.AppTheme.BarChartView <- android:Widget`; `Widget.AppTheme.CardView.TrustCta <- Widget.Material3.CardView.Filled`; `Widget.AppTheme.Chip.MainFilter <- Widget.AppTheme.Chip.Filter`; `Widget.AppTheme.Chip.MainSuggestion <- Widget.AppTheme.Chip.Suggestion` |
| `app/src/main/res/values-v31/styles.xml` | `AppTheme.AppWidgetContainerParent <- @android:style/Theme.DeviceDefault.DayNight`; `Widget.AppTheme.AppWidget <- Widget.AppTheme.AppWidgetOverlay` |
| `app/src/main/res/values-night-v31/styles.xml` | `AppTheme.AppWidgetContainerParent <- @android:style/Theme.DeviceDefault.DayNight` |

Base UI module style declarations:

| Group | Styles |
| --- | --- |
| Theme shell | `AppThemeOverlay <- Theme.Material3.Light.NoActionBar`; `AppTheme <- AppThemeOverlay`; `AppTheme.Black <- AppTheme`; `AppTheme.TransparentBackground <- AppTheme`; `AppTheme.TransparentBackground.Black <- AppTheme.TransparentBackground`; `AppTheme.FullScreenDialog.Animation`; `AppTheme.BottomSheetDialog <- ThemeOverlay.Material3.BottomSheetDialog`; `AppTheme.BottomSheetModal <- Widget.Material3.BottomSheet.Modal`; `AppTheme.BottomSheetAnimation` |
| Inputs | `Widget.AppTheme.MaterialSpinner`; `Widget.AppTheme.MaterialSpinner.Small`; `Widget.AppTheme.MaterialSpinner.Spinner`; `Widget.AppTheme.MaterialSpinner.Spinner.Small`; `ThemeOverlay.AppTheme.MaterialSpinner`; `ThemeOverlay.AppTheme.MaterialSpinner.Small`; `Widget.AppTheme.MaterialSpinner.AutoCompleteTextView`; `Widget.AppTheme.MaterialSpinner.AutoCompleteTextView.Small`; `Widget.AppTheme.TextInputLayout`; `Widget.AppTheme.TextInputLayout.Small`; `Widget.AppTheme.TextInputLayout.ExposedDropdownMenu`; `Widget.AppTheme.TextInputLayout.ExposedDropdownMenu.Small`; `ThemeOverlay.AppTheme.TextInputLayout`; `ThemeOverlay.AppTheme.TextInputLayout.Small`; `Widget.AppTheme.TextInputEditText`; `Widget.AppTheme.TextInputEditText.Small`; `Widget.AppTheme.AutoCompleteTextView`; `Widget.AppTheme.AutoCompleteTextView.Small` |
| Chips | `Widget.AppTheme.Chip.Assist`; `Widget.AppTheme.Chip.Assist.Elevated`; `Widget.AppTheme.Chip.Assist.Elevated.Padded`; `Widget.AppTheme.Chip.Filter`; `Widget.AppTheme.Chip.Input`; `Widget.AppTheme.Chip.Suggestion`; `Widget.AppTheme.ChipGroup`; `Widget.AppTheme.ChipGroup.Assist`; `Widget.AppTheme.ChipGroup.Assist.Elevated`; `Widget.AppTheme.ChipGroup.Filter`; `Widget.AppTheme.ChipGroup.Input`; `Widget.AppTheme.ChipGroup.Suggestion` |
| Cards, sheets, dividers | `Widget.AppTheme.CardView.Elevated`; `Widget.AppTheme.CardView.ListItem`; `Widget.AppTheme.CardView.ListItem.Outlined`; `Widget.AppTheme.CardView.ListItem.Padded`; `Widget.AppTheme.CardView.Filled`; `Widget.AppTheme.CardView.Outlined`; `Widget.AppTheme.CardView.Outlined.DiagonallyRounded`; `Widget.AppTheme.Divider`; `Widget.AppTheme.MaterialCalendar`; `Widget.AppTheme.MaterialAlertView`; `Widget.AppTheme.MaterialAlertView.Warn`; `MaterialAlertDialog.Material3.Body.Text.Large` |
| Navigation and chrome | `Widget.AppTheme.MaterialToolbar`; `Widget.AppTheme.PopupMenu`; `Widget.AppTheme.PopupMenu.ListPopupWindow`; `Widget.AppTheme.PopupMenu.ContextMenu`; `Widget.AppTheme.PopupMenu.Overflow`; `Widget.AppTheme.PopupMenu.OverflowButton`; `Widget.AppTheme.PopupMenu.DropDown`; `Widget.AppTheme.MultiSelectionActionsView`; `Widget.AppTheme.SearchView`; `Widget.AppTheme.SearchView.Small`; `Widget.AppTheme.SwipeRefreshLayout` |
| Buttons and controls | `Widget.AppTheme.CompoundButton.CheckBox`; `Widget.AppTheme.MaterialSwitch`; `Widget.AppTheme.MaterialSwitch.Large`; `Widget.AppTheme.Button.ElevatedButton`; `Widget.AppTheme.Button.ElevatedButton.Dense`; `Widget.AppTheme.Button.FilledButton`; `Widget.AppTheme.Button.FilledButton.Dense`; `Widget.AppTheme.Button.FilledTonalButton`; `Widget.AppTheme.Button.FilledTonalButton.Dense`; `Widget.AppTheme.Button.OutlinedButton`; `Widget.AppTheme.Button.OutlinedButton.Dense`; `Widget.AppTheme.Button.TextButton`; `Widget.AppTheme.Button.TextIconButton`; `Widget.AppTheme.Button.TextButton.Icon`; `Widget.AppTheme.Button.IconButton`; `Widget.AppTheme.Button.IconButton.InverseColor`; `Widget.AppTheme.Button.FAB`; `Widget.AppTheme.Button.EFAB` |
| Progress | `Widget.AppTheme.LinearProgressIndicator`; `Widget.AppTheme.CircularProgressIndicator`; `Widget.AppTheme.CircularProgressIndicator.Medium`; `Widget.AppTheme.CircularProgressIndicator.Small`; `Widget.AppTheme.CircularProgressIndicator.ExtraSmall` |
| Preferences | `Preference.M3`; `Preference.M3.Alert`; `Preference.M3.Category`; `Preference.M3.CheckBoxPreference`; `Preference.M3.DialogPreference`; `Preference.M3.DialogPreference.EditTextPreference`; `Preference.M3.PreferenceScreen`; `Preference.M3.ButtonPreference.Primary`; `Preference.M3.SwitchPreference`; `Preference.M3.SwitchPreferenceCompat`; `Preference.M3.TopSwitchPreference` |
| Shapes | `ShapeAppearance.AppTheme.None`; `ShapeAppearance.AppTheme.SmallComponent`; `ShapeAppearance.AppTheme.MediumComponent`; `ShapeAppearance.AppTheme.LargeComponent`; `ShapeAppearance.AppTheme.CircleComponent`; `ShapeAppearance.AppTheme.DiagonallyRounded`; `ShapeAppearance.AppTheme.LeftRounded`; `ShapeAppearance.AppTheme.RightRounded`; `ShapeAppearance.AppTheme.MediumComponent.RoundedTop` |
| Typography | `TextAppearance.AppTheme.DisplayLarge`; `DisplayMedium`; `DisplaySmall`; `HeadlineLarge`; `HeadlineMedium`; `HeadlineSmall`; `TitleLarge`; `TitleMedium`; `TitleSmall`; `BodyLarge`; `BodyMedium`; `BodyMedium.Secondary`; `BodySmall`; `LabelLarge`; `LabelMedium`; `LabelSmall`; `LabelSmaller` |

Theme inheritance from Material 3:

- The active app theme chain is `AppTheme <- AppThemeOverlay <- Theme.Material3.Light.NoActionBar`.
- `AppTheme.Black` inherits `AppTheme` and currently only redirects full-screen alert dialog theme; it does not define a full AMOLED palette.
- The widget v31 variants inherit `Theme.DeviceDefault.DayNight` to use system widget radii.
- Material 3 component families are already used, but the visual language still reads as early M3 because color, elevation, surface tiering, and row hierarchy are thin.

## Color Palette Inventory

App colors:

| Role | Names |
| --- | --- |
| File manager | `fm_icon_background = @color/m3_sys_color_light_on_surface_variant`; `fm_symbolic_link = #f37785` |

Libcore colors grouped by role:

| Role | Names and values |
| --- | --- |
| Text and overlay | `textColorPrimary #DE000000`; `textColorSecondary #99000000`; `semi_transparent #10000000`; `sixty_percent_white #9AFFFFFF`; `sixty_percent_black #9A000000`; `white_layover #ffffff` |
| Current semantic/state | `disabled_user #FF8A80`; `highlight #FFFF9F`; `red #B3FF0000`; `running #EA80FC`; `stopped @color/blue_mountain`; `tracker #FFFF8017`; `changelog_new #198754`; `changelog_improve #563d7c`; `changelog_fix #ffc107`; `pure_red #FF0000`; `electric_red #ff0028`; `painful_red #eb1736` |
| Current brand/accent candidates | `salem_green #1b8654`; `lilac_bush_purple #8267b8`; `pumpkin_orange #ff8017`; `original_orange #ff9702`; `dark_orange #ff8017`; `ocean_blue #2B65EC`; `green_mountain #3d7c47`; `blue_mountain #09868b`; `blue_green #1dbab4`; `bold_2019_green #007f4f`; `lightning_blue #51d0de`; `lightning_purple #bf4aa8`; `purple #431c5d`; `orange #e05915` |
| Legacy palette swatches | `grey #ffe0e0e0`; `pink #7D1B7E`; `bright_green #BEEF00`; `deep_green #657a00`; `power_blue #1400c6`; `background_tan #fceed1`; `purple_y #7d3cff`; `yellow_gloves #f2d53c`; `readhead #c80e13`; `sand_tan #E1B382`; `sand_tan_shadow #c89666`; `night_blue #2D545e`; `night_blue_shadow #12343B`; `ragin_beige #fff5d7`; `coral_pink #ff5e6c`; `sleuthe_yellow #feb300`; `pink_leaf #ffaaab`; `grassy_green #9bc400`; `purple_mountains_majesty #8076a3`; `misty_mountain_pink #f9c5bd`; `factory_stone_purple #7c677f`; `green_treeline #478559`; `purple_baseline #161748`; `pink_highlight #f95d9b`; `bluewater_lowlight #39a0ca`; `yellow_background #ffde22`; `pink_red_circle #ff414e`; `orange_circle #ff8928`; `mountain_shadow_blue #101357`; `old_makeup_pink #fea49f`; `goldenrod_yellow #fbaf08`; `bluebell_light_blue #00a0a0`; `brian_wrinkle_white #d9d9d9`; `blue_popsicle #0f2862`; `redline #9e363a`; `purple_shadow #091f36`; `grey_blue_leaf #4f5f76`; `blueberry #6b7a8f`; `apricot #f7882f`; `citrus #f7c331`; `apple_core #dcc7aa`; `left_blue #1561ad`; `right_blue_muted #1c77ac`; `red_orange #fc5226`; `redder_than_you #ff3a22`; `goldi_lots #c7af6b`; `darker_gold #a4893d`; `silver_tongue #628078`; `barely_green #acb7ae`; `the_brown_shirts #82716e`; `tan_blonde #e4decd`; `blondey #c2b490`; `light_blue_backdrop #76c1d4`; `barely_gray_edge #f7f7f7`; `grey_silver #bccbde`; `lightsaber_blue #c2dde6`; `yellowbrite #cdd422`; `_35_years_old_purple #5252d4`; `lighter_purple_on_the_gradient #7575dd`; `shadow_purple_red #781a44`; `green #8bf0ba`; `ironic_blues #0e0fed`; `blue_underling #94f0f1`; `pinky_ring #f2b1d8`; `egg_yellows #ffdc6a` |
| Android tag colors | `android_theme_tag_color_01 #FF222222`; `02 #FF996666`; `03 #FFFFCC99`; `04 #FFCC0000`; `05 #FF666633`; `06 #FF990099`; `07 #FF006600`; `08 #FF660066`; `09 #FF003399`; `10 #FFFF6633`; `11 #FFFFFFFF`; `12 #FFCC3333`; `13 #FFFFFF00`; `14 #FF0099CC`; `15 #FF009933`; `16 #FFCC3399`; `17 #FFFFCC00` |

Findings:

- The named palette is broad but not role-based. Many colors read as old palette experiments rather than production tokens.
- Existing visual semantics use orange/teal/magenta/red, but the color names do not encode state meaning consistently.
- Light/dark/AMOLED are not represented as explicit surface tiers in app-owned resources.

## Typography Inventory

Current type system:

| Token/style | Current value |
| --- | --- |
| `@dimen/title_font` | `16sp`, `18sp` in `values-w600dp` |
| `@dimen/subtitle_font` | `14sp`, `16sp` in `values-w600dp` |
| `@dimen/font_size_larger` | `16sp` |
| `@dimen/font_size_large` | `14sp` |
| `@dimen/font_size_medium` | `12sp` |
| `@dimen/font_size_small` | `10sp` |
| `@dimen/font_size_smaller` | `9sp` |
| `TextAppearance.AppTheme.DisplayLarge` through `LabelSmall` | Inherit Material 3 text appearances unchanged |
| `TextAppearance.AppTheme.TitleMedium` | Material 3 title medium with `18sp`, letter spacing `0`, `@string/m3_ref_typeface_brand_regular` |
| `TextAppearance.AppTheme.BodyMedium.Secondary` | Body medium with `?android:attr/textColorSecondary` |
| `TextAppearance.AppTheme.LabelSmaller` | Material 3 label small with `10sp` |

Font family:

- No `res/font` directory exists in `app` or `libcore/ui`.
- The app relies on Material 3's default typeface aliases and Android system fonts.
- Monospace is used in operational surfaces: terminal, log viewer, keystore password, SSAID, file manager paths, mode-of-ops commands, running-app details, signatures, and activity tracker window.

Typography issues:

- The main list mixes `?android:attr/textAppearanceListItem`, `?attr/textAppearanceBodySmall`, and raw dimen sizes; hierarchy is functional but flat.
- Data-dense technical surfaces use system monospace directly; this is acceptable but should be formalized as a token for code/signature/manifest text.
- Letter spacing is mostly inherited from Material; the app does not own a clear type scale.

## Iconography Inventory

Drawable and mipmap counts:

| Directory | Files | XML | Vector | Shape | Selector | Raster |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `app/src/main/res/drawable` | 128 | 128 | 118 | 8 | 0 | 0 |
| `app/src/main/res/drawable-anydpi-v24` | 1 | 1 | 1 | 0 | 0 | 0 |
| `app/src/main/res/drawable-hdpi` | 1 | 0 | 0 | 0 | 0 | 1 |
| `app/src/main/res/drawable-mdpi` | 1 | 0 | 0 | 0 | 0 | 1 |
| `app/src/main/res/drawable-night-nodpi` | 2 | 0 | 0 | 0 | 0 | 2 |
| `app/src/main/res/drawable-nodpi` | 3 | 0 | 0 | 0 | 0 | 3 |
| `app/src/main/res/drawable-xhdpi` | 1 | 0 | 0 | 0 | 0 | 1 |
| `app/src/main/res/drawable-xxhdpi` | 2 | 0 | 0 | 0 | 0 | 2 |
| `app/src/main/res/mipmap-anydpi-v26` | 5 | 5 | 0 | 0 | 0 | 0 |
| `app/src/main/res/mipmap-hdpi` | 4 | 0 | 0 | 0 | 0 | 4 |
| `app/src/main/res/mipmap-mdpi` | 4 | 0 | 0 | 0 | 0 | 4 |
| `app/src/main/res/mipmap-xhdpi` | 6 | 0 | 0 | 0 | 0 | 6 |
| `app/src/main/res/mipmap-xxhdpi` | 4 | 0 | 0 | 0 | 0 | 4 |
| `app/src/main/res/mipmap-xxxhdpi` | 4 | 0 | 0 | 0 | 0 | 4 |
| `libcore/ui/src/main/res/drawable` | 30 | 30 | 13 | 6 | 1 | 0 |
| `libcore/ui/src/main/res/drawable-v23` | 1 | 1 | 0 | 1 | 0 | 0 |

Icon-set source:

- Mostly Material-style vector drawables with custom names such as `ic_add`, `ic_archive`, `ic_backup_restore`, `ic_cctv_off`, `ic_database`, `ic_filter_list`, and `ic_more_vert`.
- Several icons are custom project assets: Exodus Privacy icon, launcher foregrounds, splash logo, README icon, and AppWidget assets.
- The set is mixed: Material Icons baseline plus custom privacy/package-management symbols. The line weight is not guaranteed consistent across every screen.

## Motion Inventory

Animation files:

| File | Type | Duration | Interpolator | Invoked by |
| --- | --- | --- | --- | --- |
| `app/src/main/res/animator/enter_from_left.xml` | objectAnimator x `1000 -> 0` | `@android:integer/config_shortAnimTime` | default | `ScannerActivity`, `SettingsActivity` fragment transitions |
| `app/src/main/res/animator/enter_from_right.xml` | objectAnimator x `0 -> -1000` | `config_shortAnimTime` | default | `ScannerActivity`, `SettingsActivity` fragment transitions |
| `app/src/main/res/animator/exit_from_left.xml` | objectAnimator x `0 -> 1000` | `config_shortAnimTime` | default | `ScannerActivity`, `SettingsActivity` fragment transitions |
| `app/src/main/res/animator/exit_from_right.xml` | objectAnimator x `-1000 -> 0` | `config_shortAnimTime` | default | `ScannerActivity`, `SettingsActivity` fragment transitions |
| `libcore/ui/src/main/res/anim/bottom_sheet_slide_up.xml` | translate from bottom | `config_mediumAnimTime` | `@android:anim/accelerate_interpolator` | `AppTheme.BottomSheetAnimation` |
| `libcore/ui/src/main/res/anim/bottom_sheet_slide_down.xml` | translate to bottom | `config_mediumAnimTime` | `@android:anim/accelerate_interpolator` | `AppTheme.BottomSheetAnimation` |
| `libcore/ui/src/main/res/anim/fullscreen_dialog_enter.xml` | alpha + scale | alpha 50ms after 50ms, scale 200ms | linear + accelerate/decelerate | `AppTheme.FullScreenDialog.Animation` |
| `libcore/ui/src/main/res/anim/fullscreen_dialog_exit.xml` | alpha + scale | alpha 100ms after 50ms, scale 250ms | linear + accelerate/decelerate | `AppTheme.FullScreenDialog.Animation` |

Other motion:

- `ActivityInterceptor` calls `overridePendingTransition(0, 0)` to remove transition motion.
- App theme enables `android:windowActivityTransitions`, `android:windowContentTransitions`, enter overlap, and return overlap globally.

Motion issues:

- Fragment transitions use raw pixel x translations (`1000`) rather than percent/container-aware values.
- Bottom sheet slide-up contains `android:toXDelta="0%p"` where the intent is likely `toYDelta="0%p`; this is a legacy polish bug to fix during the motion pass.
- There is no central reduced-motion helper or tokenized duration naming.

## Density Ladder

App module:

| Token | Phone | `w600dp` |
| --- | ---: | ---: |
| `icon_size` | `40dp` | `56dp` |
| `main_list_icon_size` | `44dp` | `60dp` |
| `main_list_min_height` | `88dp` | `96dp` |
| `title_font` | `16sp` | `18sp` |
| `subtitle_font` | `14sp` | `16sp` |
| `app_widget_background_padding` | `16dp` | `24dp` |

Libcore UI:

| Token | Phone | `w600dp` |
| --- | ---: | ---: |
| `padding_very_small` | `4dp` | unchanged |
| `padding_small` | `8dp` | unchanged |
| `padding_medium` | `16dp` | `20dp` |
| `padding_large` | `24dp` | `32dp` |
| `padding_very_large` | `32dp` | `40dp` |

Findings:

- The existing foundation is a 4dp grid: `4 / 8 / 16 / 24 / 32`.
- The new design system should extend the ladder with `12 / 20 / 40 / 48 / 64 / 80 / 96`, not reset the shipped `w600dp` behavior.

## Component Inventory

Counts:

- 124 layout XML files in `app/src/main/res/layout`.
- 1 layout XML file in `app/src/main/res/layout-w600dp`: `item_main.xml`.

Activities:

- `activity_app_details.xml` [heavy, tabs]: toolbar, TabLayout, ViewPager2.
- `activity_app_usage.xml` [list, empty-state]: SwipeRefreshLayout, RecyclerView, `view_list_empty_state`.
- `activity_apps_profile.xml` [pager]: ViewPager2.
- `activity_audio_player.xml` [utility].
- `activity_auth_management.xml` [card-heavy].
- `activity_authentication.xml` [loading].
- `activity_batch_ops_results.xml` [list].
- `activity_code_editor.xml` [editor].
- `activity_debloater.xml` [list].
- `activity_finder.xml` [list/search].
- `activity_fm.xml` [file manager/list].
- `activity_help.xml` [doc/web-style].
- `activity_interceptor.xml` [very heavy, multi-list].
- `activity_labs.xml` [settings/experimental].
- `activity_logcat.xml` [log list].
- `activity_main.xml` [highest traffic, list/search/chips/status/empty].
- `activity_one_click_ops.xml` [command grid].
- `activity_op_history.xml` [list].
- `activity_profiles.xml` [list].
- `activity_running_apps.xml` [list].
- `activity_settings.xml` [settings].
- `activity_settings_dual_pane.xml` [tablet settings].
- `activity_shared_prefs.xml` [list/editor].
- `activity_sys_config.xml` [list].
- `activity_term.xml` [terminal].

Dialogs:

- `dialog_app_usage_details.xml`, `dialog_audio_player.xml`, `dialog_backup_restore.xml`, `dialog_backup_tasks.xml`, `dialog_bloatware_details.xml`, `dialog_certificate_generator.xml`, `dialog_change_file_mode.xml`, `dialog_checksums.xml`, `dialog_create_shortcut.xml`, `dialog_debloater_list_options.xml`, `dialog_dexopt.xml`, `dialog_disclaimer.xml`, `dialog_edit_filter_item.xml`, `dialog_edit_filter_option.xml`, `dialog_edit_pref_item.xml`, `dialog_file_properties.xml`, `dialog_icon_picker.xml`, `dialog_installer_options.xml`, `dialog_installer.xml`, `dialog_key_pair_importer.xml`, `dialog_keystore_password.xml`, `dialog_list_options.xml`, `dialog_new_file.xml`, `dialog_new_symlink.xml`, `dialog_open_with.xml`, `dialog_profile_backup_restore.xml`, `dialog_progress_circular.xml`, `dialog_progress.xml`, `dialog_progress2.xml`, `dialog_rename.xml`, `dialog_restore_tasks.xml`, `dialog_running_app_details.xml`, `dialog_searchable_multi_choice.xml`, `dialog_searchby.xml`, `dialog_send_log.xml`, `dialog_set_apk_format.xml`, `dialog_ssaid_info.xml`, `dialog_whats_new.xml`.

Fragments and pagers:

- `fragment_class_lister.xml`, `fragment_code_editor.xml`, `fragment_container.xml`, `fragment_dialog_backup.xml`, `fragment_dialog_restore_multiple.xml`, `fragment_dialog_restore_single.xml`, `fragment_fm.xml`, `fragment_log_viewer.xml`, `fragment_logcat.xml`, `fragment_mode_of_ops.xml`, `fragment_onboarding.xml`, `fragment_scanner.xml`, `pager_app_details.xml`, `pager_app_info.xml`.

Rows, cards, and repeated items:

- `item_app_details_appop.xml`, `item_app_details_overlay.xml`, `item_app_details_perm.xml`, `item_app_details_primary.xml`, `item_app_details_secondary.xml`, `item_app_details_signature.xml`, `item_app_details_tertiary.xml`, `item_app_info_action.xml`, `item_app_usage_header.xml`, `item_app_usage.xml`, `item_bloatware_details.xml`, `item_changelog_header.xml`, `item_changelog_item.xml`, `item_checkbox.xml`, `item_chip.xml`, `item_debloater.xml`, `item_finder.xml`, `item_fm_drawer.xml`, `item_fm.xml`, `item_icon_title_subtitle.xml`, `item_logcat.xml`, `item_main.xml`, `item_op_history.xml`, `item_path.xml`, `item_right_standalone_action.xml`, `item_right_summary.xml`, `item_running_app.xml`, `item_shared_lib.xml`, `item_switch.xml`, `item_sys_config.xml`, `item_text_input_layout_monospace.xml`, `item_text_view.xml`, `item_title_action.xml`, `item_whats_new.xml`.

Widgets, empty states, and windows:

- AppWidget layouts: `app_widget_clear_cache.xml`, `app_widget_data_usage_small.xml`, `app_widget_refresh_button.xml`, `app_widget_screen_time_large.xml`, `app_widget_screen_time_small.xml`, `app_widget_screen_time.xml`, `widget_recording.xml`.
- Empty states: `view_app_details_empty_state.xml`, `view_list_empty_state.xml`, `view_main_empty_state.xml`.
- Other: `appbar.xml`, `header_running_apps_memory_info.xml`, `window_activity_tracker.xml`.

## MainActivity and MainRecyclerAdapter ID Contracts

`MainActivity` binds these IDs from `activity_main.xml`:

- `toolbar`, `progress_linear`, `list_status`, `android.R.id.empty`, `empty_state_title`, `empty_state_summary`, `empty_state_action`, `item_list`, `swipe_refresh`, `selection_view`.
- Quick filter chips: `chip_user`, `chip_system`, `chip_frozen`, `chip_running`, `chip_backups`, `chip_stopped`, `chip_trackers`, `chip_rules`, `chip_granted_perms`, `chip_clear_filters`, `chip_sort`.
- There is no current `fab` ID in `activity_main.xml`; MainActivity actions are toolbar/menu, chip, row, and multi-selection driven.

`MainRecyclerAdapter.ViewHolder` binds these IDs from `item_main.xml`:

- `icon`, `favorite_icon`, `label`, `packageName`, `version`, `isSystem`, `date`, `size`, `shareid`, `issuer`, `sha`, `backup_indicator`, `backup_info`, `backup_info_ext`, `tracker_indicator`, `perm_indicator`.

Reference implementation layouts must preserve all of these without Java changes.

## Pain-Point Hunt: 10 Dated Layouts

| Layout | Current state | Why it reads dated | Benchmark direction |
| --- | --- | --- | --- |
| `item_main.xml` | Outlined MaterialCard row, 3dp vertical margin, dense stacked metadata, plain number badges. | Card-with-everything pattern reads Material 1/early M3; hierarchy is not scannable enough for 600-app lists. | 1Password 8 vault list: compact row, strong title, quiet metadata, semantic badges. |
| `activity_main.xml` | Generic toolbar, actionbar custom search, chip row, text-only aggregate status. | Search is behaviorally central but visually hidden inside toolbar plumbing; aggregate metrics have no visual grammar. | Linear command bar plus Things-style filter affordances. |
| `activity_app_details.xml` | Toolbar + TabLayout + ViewPager only; header is pushed into pager content. | App identity does not lead the screen; tabs dominate before context. | 1Password item detail header, Obsidian property panels. |
| `pager_app_info.xml` | Large mixed info stack with cards and many text rows. | Dense data lacks typographic zones and progressive grouping. | Notion database property rows with clearer section rhythm. |
| `item_app_details_primary.xml` | Technical primary rows with mixed label/value and monospace blocks. | Useful but visually flat; code-like data has no dedicated token system. | Obsidian code/metadata typography. |
| `activity_settings.xml` | Single pane plain toolbar + fragment container. | Functional but no premium hierarchy, no branded settings hub. | Arc settings sidebar and 1Password preference panes. |
| `activity_settings_dual_pane.xml` | 450dp left pane and full right pane with plain toolbars. | Good structure but visually utilitarian; pane relationship lacks surface contrast. | Arc sidebar treatment. |
| `dialog_list_options.xml` | Sort/filter/options dialog as a dense default dialog. | High-frequency control surface feels like a form, not a command sheet. | Linear filter menus, Things popovers. |
| `item_app_usage.xml` | Card row, small icon frame, split metrics, progress bar. | Too many equal-weight values; percent and usage do not become a premium dashboard row. | 1Password vault-list metrics and Bear's quiet typography. |
| `item_debloater.xml` | Outlined card with icon, list type, description text. | Safety-critical debloat context lacks semantic safety tint and confidence hierarchy. | Canta/UAD-NG style safety ratings, Notion callout tonality. |
| `activity_interceptor.xml` | Very large multi-section intercept surface. | Powerful, but visually heavy and hard to parse under pressure. | Arc command panels and Obsidian power-user sheets. |
| `fragment_onboarding.xml` | Existing first-run flow with large mixed content. | Good feature direction, but Pro Mode and capability messaging need more premium trust treatment. | 1Password onboarding trust cards. |

## Existing Brand Assets

Assets found:

- `branding/logo-prompts.md`: five logo directions. Common baseline: package/cube plus gear/key/shield, green-to-cyan or green-to-teal palette, transparent PNGs, no Android mascot, no Google Play bag.
- `docs/raw/images/icon.png`: README logo, 26 KB.
- `docs/raw/images/appops.svg` and `docs/raw/images/main_page_entry_info_labeled.svg`: documentation diagrams.
- Launcher/adaptive assets: `ic_launcher_foreground.xml`, `ic_launcher_fm_foreground.xml`, adaptive icon XML in `mipmap-anydpi-v26`, density PNG launcher fallbacks, splash logo.

Brand baseline:

- Silhouette direction should be package/cube/control shield, not a generic app grid.
- Dominant color should stay Android-adjacent but not generic Material teal. Green-to-cyan is already documented.
- Typographic feel from the prompts: modern geometric sans, tight but readable, with `NG` emphasized.

## Recon Conclusion

AppManagerNG already uses Material 3 components and has real interaction depth. The dated feeling comes from weak token ownership: broad unrole-based colors, inherited typography, flat surfaces, undifferentiated cards, and functional dialogs that do not express risk or state. The facelift should add a role-based palette, explicit surface tiers, premium list-row hierarchy, semantic metric chips, and a consistent motion system while preserving every manifest, deep link, AppWidget, and privileged operation.
