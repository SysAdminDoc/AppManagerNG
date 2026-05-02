<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Design System v2

Design intent: keep AppManagerNG's power-user density, but make every surface feel deliberate. The new system uses restrained contrast, typed semantic colors, stronger text hierarchy, quieter cards, and operational state signals that can be scanned under pressure.

Constraints held:

- Android Views and Material Components 1.13 only.
- minSdk 21 compatible resources.
- No feature, intent, deep link, AppWidget, root, ADB, Shizuku, backup, installer, scanner, or profile behavior changes.
- No existing strings removed.
- New files require SPDX headers.

## Palette

### Brand Color

Brand color: **Control Teal `#16A394`**.

Rationale: upstream already uses generic Material teal patterns, and AppManagerNG's branding notes ask for green-to-cyan package/control imagery. `#16A394` stays Android-adjacent without copying `#009688`, reads cleaner than saturated neon green in dark mode, and has enough blue to signal security/control instead of consumer wellness. It pairs well with amber and red semantic warnings, so tracker/permission risk colors remain legible.

### Brand Tonal System

| Token | Hex | Use |
| --- | --- | --- |
| `brand-25` | `#F3FFFC` | light hover wash |
| `brand-50` | `#E7FFF9` | light selected row background |
| `brand-100` | `#C8FFF0` | light active chip background |
| `brand-200` | `#90F5DF` | decorative icon wash |
| `brand-300` | `#58E4CA` | dark active chip content |
| `brand-400` | `#29CDB7` | primary pressed |
| `brand-500` | `#16A394` | primary brand |
| `brand-600` | `#0B8278` | light theme primary |
| `brand-700` | `#086A63` | selected outline |
| `brand-800` | `#07534F` | dark surface tint |
| `brand-900` | `#063F3D` | AMOLED elevated tint |
| `brand-950` | `#022A29` | deepest accent container |

### Surface Tiers

| Tier | Light | Dark | AMOLED |
| --- | --- | --- | --- |
| `surface-0` background | `#F8FAF8` | `#101413` | `#000000` |
| `surface-1` cards/list rows | `#FFFFFF` | `#161B1A` | `#090D0C` |
| `surface-2` elevated cards | `#F1F6F4` | `#1D2321` | `#101513` |
| `surface-3` dialogs/menus/sheets | `#EAF1EF` | `#252C2A` | `#18201D` |

Rules:

- `surface-0` is full-screen background only.
- `surface-1` is rows, cards, AppWidget surfaces, and preference rows.
- `surface-2` is selected rows, inline banners, status cards, and pinned toolbars.
- `surface-3` is modal surfaces: dialogs, popups, bottom sheets, overflow menus.
- AMOLED keeps only `surface-0` pure black; elevated content still uses nonzero surfaces so boundaries remain visible.

### Semantic Colors

| Role | Light bg | Light content | Dark bg | Dark content |
| --- | --- | --- | --- | --- |
| Success | `#DDF7E8` | `#0D6B3E` | `#123923` | `#8FE6B3` |
| Warning | `#FFF0CC` | `#8A5800` | `#3F2D09` | `#FFD16B` |
| Danger | `#FFE0DE` | `#B42318` | `#471815` | `#FFB4AB` |
| Info | `#DCEBFF` | `#1D5FAE` | `#102B4D` | `#A9C7FF` |

Usage:

- Success: all trackers blocked, backup current, safe debloat.
- Warning: trackers 5-19, partial dangerous permissions, outdated backups, root/ADB setup warning.
- Danger: trackers >= 20, destructive root operations, dangerous permission concentration, unsafe debloat.
- Info: onboarding guidance, scanner summaries, neutral diagnostics.

### Text Emphasis

Approximate Material 3 alpha behavior, but owned by AppManagerNG:

| Tier | Light on surface | Dark on surface | Use |
| --- | --- | --- | --- |
| High | 92 percent | 94 percent | titles, app names, primary values |
| Medium | 68 percent | 72 percent | package names, metadata, helper copy |
| Disabled | 38 percent | 38 percent | unavailable actions, stale secondary data |

Implementation target:

- Light high text: `#171D1B`.
- Light medium text: `#53605C`.
- Light disabled text: `#8E9995`.
- Dark high text: `#F1F5F3`.
- Dark medium text: `#B4C0BC`.
- Dark disabled text: `#68736F`.

### Outline Tier

| Token | Light | Dark | AMOLED | Use |
| --- | --- | --- | --- | --- |
| `outline-hairline` | `#D7E2DE` | `#303A37` | `#202A27` | 1px dividers |
| `outline-muted` | `#BCC9C4` | `#43504C` | `#323E3A` | card strokes |
| `outline-focus` | `#16A394` | `#58E4CA` | `#58E4CA` | keyboard/TalkBack focus |
| `outline-danger` | `#D92D20` | `#FFB4AB` | `#FFB4AB` | destructive focus/selection |

## Typography

Typeface choice: **system default for app UI, JetBrains Mono only as a future optional code/manifest asset if its Apache-2.0 font files are explicitly added and REUSE-audited**.

Decision: ship v2 on system default first. It has zero binary/license cost, it respects OEM text rendering and language coverage, and it avoids bundling a font into downstream F-Droid/IzzyOnDroid builds. The premium change comes from scale, weight, line height, and color discipline, not a bundled font. Technical fields continue to use Android `monospace`; a later pass can introduce a licensed mono resource if needed.

### Type Scale

| Token | Size | Line height | Weight | Letter spacing | Use |
| --- | ---: | ---: | ---: | ---: | --- |
| `display-large` | 32sp | 40sp | 600 | 0 | onboarding hero, empty state title on tablets |
| `headline-large` | 28sp | 36sp | 600 | 0 | major screen title on expanded layouts |
| `headline-medium` | 24sp | 32sp | 600 | 0 | App Details header app name |
| `headline-small` | 22sp | 28sp | 600 | 0 | modal sheet title |
| `title-large` | 20sp | 28sp | 600 | 0 | toolbar large title, section title |
| `title-medium` | 18sp | 24sp | 600 | 0 | main row label, preference title |
| `title-small` | 16sp | 22sp | 600 | 0 | dense section title |
| `body-large` | 16sp | 24sp | 400 | 0 | readable paragraphs, dialog body |
| `body-medium` | 14sp | 20sp | 400 | 0 | package names, row metadata |
| `body-small` | 12sp | 16sp | 400 | 0 | secondary row metadata |
| `label-large` | 14sp | 20sp | 600 | 0 | buttons, chips |
| `label-medium` | 12sp | 16sp | 600 | 0 | badges, compact controls |
| `label-small` | 11sp | 14sp | 600 | 0 | tiny count badges |

Mapping from current tokens:

- `title_font 16sp` maps to `title-small` for compact rows; main app labels should move to `title-medium`.
- `subtitle_font 14sp` maps to `body-medium`.
- `font_size_medium 12sp` maps to `body-small` or `label-medium` depending on whether it is prose or a badge.
- `font_size_small 10sp` and `font_size_smaller 9sp` should be retired from normal text and kept only for exceptional compact labels, because they are fragile for accessibility.

## Spacing and Density

Base grid: 4dp.

| Token | Value | Use |
| --- | ---: | --- |
| `space-4` | 4dp | icon/text gap, micro row gap |
| `space-8` | 8dp | compact padding, chip gap |
| `space-12` | 12dp | row internal gap |
| `space-16` | 16dp | default screen/card padding |
| `space-20` | 20dp | tablet medium padding |
| `space-24` | 24dp | section padding, app bar side padding |
| `space-32` | 32dp | large section gap |
| `space-40` | 40dp | expanded layout gap |
| `space-48` | 48dp | minimum touch target |
| `space-64` | 64dp | hero/icon block |
| `space-80` | 80dp | empty-state illustration block |
| `space-96` | 96dp | large-screen rail/header area |

Current token mapping:

- `padding_very_small 4dp` -> `space-4`, unchanged on `w600dp`.
- `padding_small 8dp` -> `space-8`, unchanged on `w600dp`.
- `padding_medium 16dp` -> `space-16`, current `w600dp` override `20dp` maps to `space-20`.
- `padding_large 24dp` -> `space-24`, current `w600dp` override `32dp` maps to `space-32`.
- `padding_very_large 32dp` -> `space-32`, current `w600dp` override `40dp` maps to `space-40`.

Large-screen rule:

- Do not override `space-4` or `space-8` at `w600dp`.
- Increase container and section spacing only: `space-16 -> 20`, `space-24 -> 32`, `space-32 -> 40`.
- Main row icon remains tokenized: `main_list_icon_size 44 -> 60`.

## Elevation, Overlays, and Shape

### Elevation Levels

Dark surface tint math:

```
result_channel = round(surface_channel * (1 - alpha) + tint_channel * alpha)
```

Use `brand-500` as tint for dark and AMOLED elevated surfaces. Light theme should prefer real shadow/stroke over tint.

| Level | Z | Overlay alpha dark | Primary use |
| --- | ---: | ---: | --- |
| 0 | 0dp | 0 percent | full background, flat lists |
| 1 | 1dp | 5 percent | normal cards, list rows |
| 2 | 3dp | 8 percent | selected rows, sticky status |
| 3 | 6dp | 11 percent | toolbar scrolled state, menus |
| 4 | 8dp | 12 percent | dialogs, bottom sheets |
| 5 | 12dp | 14 percent | destructive confirmation, modal focus |

Operational rules:

- Main list rows: level 1, 1dp outline, no heavy shadow.
- Active/selected row: level 2, brand outline.
- Dialogs and bottom sheets: level 4, surface-3, sticky action area.
- Floating action button: level 3 resting, level 4 pressed.

### Corner Radius

| Token | Value | Use |
| --- | ---: | --- |
| `radius-none` | 0dp | full-bleed bars, dividers |
| `radius-compact` | 4dp | code token, small focus rect |
| `radius-default` | 8dp | compact button, technical chip |
| `radius-card` | 12dp | list row/card |
| `radius-sheet` | 16dp | bottom sheet top corners |
| `radius-modal` | 24dp | dialogs, onboarding cards |
| `radius-pill` | 9999dp | chips, badges, search bar |

## Iconography

Primary icon family: **Material Symbols Rounded**, exported as vector drawables where needed.

Rationale: Apache-2.0, comprehensive, Android-native visual expectations, and rounded endpoints fit the premium-but-approachable direction. The current repo already uses many Material-style vectors, so this is a consolidation rather than a dependency shift.

Alternative considered: Phosphor Icons. It has a broad set and clean weights, but it would introduce a second design language and more asset licensing review. Not worth it for this pass.

Size scale:

- `16dp`: inline metadata icon.
- `20dp`: badge/chip leading icon.
- `24dp`: toolbar/action icon.
- `32dp`: list-row secondary action or empty-state supporting icon.
- `40dp`: AppWidget and detail header action.
- `48dp`: launcher/app icon target in rows.
- `64dp`: empty-state and onboarding illustration base.

Weight and fill rules:

- Default: rounded outlined icons, optical weight 400.
- Selected/active: filled icon only when selection state benefits from immediate recognition.
- Destructive: outlined icon plus danger color; do not use filled danger icons unless the action is active.
- Disabled: icon content uses disabled text tier, not lowered opacity on the whole button.

## Motion

### Durations

| Token | Duration | Use |
| --- | ---: | --- |
| `instant` | 0ms | reduced motion, state already visible |
| `micro` | 100ms | icon tint, badge count, ripple exit |
| `short` | 200ms | chip selection, row press, menu fade |
| `medium` | 300ms | dialog/sheet transform |
| `entrance` | 400ms | screen/content entrance |
| `long` | 500ms | onboarding hero and large layout transitions |

### Interpolators

- Standard ease: `FastOutSlowInInterpolator` (`cubic-bezier(0.4, 0.0, 0.2, 1.0)`).
- Emphasized ease: `cubic-bezier(0.2, 0.0, 0.0, 1.0)` for sheets/dialog entrances.
- Emphasized exit: `cubic-bezier(0.3, 0.0, 0.8, 0.15)` for dismissals.
- Spring: damping ratio `0.82`, stiffness `500f` for chip selection and draggable sheet settling when implemented in code.

### Recipes

- Bottom sheet: translate Y `100% -> 0`, alpha `0 -> 1`, 300ms emphasized ease. Exit: translate Y `0 -> 100%`, alpha `1 -> 0`, 200ms emphasized exit.
- Dialog: scale `0.96 -> 1.0`, alpha `0 -> 1`, 300ms emphasized ease. Exit: scale `1.0 -> 0.98`, alpha `1 -> 0`, 200ms standard ease.
- List items: first 12 visible rows stagger by 20ms, max delay 120ms, alpha `0 -> 1`, Y `8dp -> 0`, 300ms standard ease.
- FAB/extended FAB: scale `0.92 -> 1.0`, alpha `0 -> 1`, 200ms standard ease. Hide reverses in 100ms.
- Navigation transitions: use container-aware slide/alpha, not raw 1000px translations.

Reduced motion:

- Add a Views helper such as `MotionPrefs.shouldReduceMotion(Context)`.
- Return true when `Settings.Global.TRANSITION_ANIMATION_SCALE == 0`.
- On API 33+, also respect the platform reduced-motion signal via the app helper when available.
- When true, replace transforms with alpha-only 100ms or no animation for destructive/recovery operations.

## Component Stubs

The stubs below show the target XML grammar. Full drop-in versions for MainActivity and `item_main` are in Phase 2.

### Top App Bar

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<com.google.android.material.appbar.MaterialToolbar
    android:id="@+id/toolbar"
    style="@style/Widget.AppTheme.V2.Toolbar"
    android:layout_width="match_parent"
    android:layout_height="@dimen/premium_app_bar_height"
    android:paddingHorizontal="@dimen/premium_space_16"
    app:navigationIconTint="?attr/colorOnSurface"
    app:titleTextAppearance="@style/TextAppearance.AppTheme.V2.TitleLarge" />
```

### Filter Chip Row

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<HorizontalScrollView
    android:id="@+id/quick_filter_scroll"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingHorizontal="@dimen/premium_space_16"
    android:paddingVertical="@dimen/premium_space_8"
    android:scrollbars="none">

    <com.google.android.material.chip.ChipGroup
        android:id="@+id/quick_filter_group"
        style="@style/Widget.AppTheme.V2.ChipGroup.Filter"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:chipSpacingHorizontal="@dimen/premium_space_8"
        app:singleLine="true" />
</HorizontalScrollView>
```

### Search Bar

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/search_bar_shell"
    style="@style/Widget.AppTheme.V2.SearchBarCard"
    android:layout_width="match_parent"
    android:layout_height="@dimen/premium_search_height"
    android:layout_marginHorizontal="@dimen/premium_space_16">

    <androidx.appcompat.widget.SearchView
        android:id="@+id/action_search"
        style="@style/Widget.AppTheme.V2.SearchView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</com.google.android.material.card.MaterialCardView>
```

### List Row

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.AppTheme.V2.Card.ListRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:checkable="true"
    android:clickable="true"
    android:focusable="true">
    <!-- Preserve MainRecyclerAdapter IDs: icon, favorite_icon, label, packageName, version, isSystem, date, size, shareid, issuer, sha, backup_indicator, backup_info, backup_info_ext, tracker_indicator, perm_indicator. -->
</com.google.android.material.card.MaterialCardView>
```

### App Detail Header

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/app_detail_header"
    style="@style/Widget.AppTheme.V2.Card.Header"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/premium_space_16">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/premium_space_16" />
</com.google.android.material.card.MaterialCardView>
```

### Bottom Sheet

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<LinearLayout
    style="@style/Widget.AppTheme.V2.BottomSheet.Content"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="@dimen/premium_space_24"
    android:paddingTop="@dimen/premium_space_16"
    android:paddingBottom="@dimen/premium_space_24" />
```

### FAB / Extended FAB

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    android:id="@+id/fab"
    style="@style/Widget.AppTheme.V2.Button.EFAB"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/app_name"
    app:icon="@drawable/ic_add" />
```

### Dialog

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<LinearLayout
    style="@style/Widget.AppTheme.V2.Dialog.Content"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="@dimen/premium_space_24" />
```

### Empty State

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<LinearLayout
    android:id="@android:id/empty"
    style="@style/Widget.AppTheme.V2.EmptyState"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="@dimen/premium_space_32" />
```

### Loading Skeleton

```xml
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<LinearLayout
    android:id="@+id/loading_skeleton"
    style="@style/Widget.AppTheme.V2.Skeleton"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="@dimen/premium_space_16" />
```

## Tradeoffs

- The system default typeface is less distinctive than Inter, but it avoids a binary font and licensing audit in the first ship path.
- Semantic risk tinting makes rows more informative, but it must be carefully thresholded to avoid turning the main list into an alarm panel.
- AMOLED pure black only at `surface-0` preserves battery and OLED expectations without sacrificing elevated-surface legibility.
- Material Symbols Rounded consolidation requires asset cleanup over time; Phase 2 avoids broad icon replacement to keep the reference implementation drop-in.
