<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Discreet / Generic Launcher-Icon Mode — Architecture

**Date:** 2026-05-26
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — T21-E
"Discreet / generic launcher-icon mode".
**Status:** Planner data layer shipped 2026-05-26; manifest aliases,
drawables, and Settings entry remain on the roadmap row.

## Goal

Let users pick one of four launcher styles for AppManagerNG without
involving the system launcher restart side-effect that flipping aliases
typically produces:

1. **Default** — current SplashActivity launcher mark.
2. **NG mark** — the branded AppManagerNG mark on its own alias.
3. **Neutral square** — a discreet, system-styled square that looks
   like a stock-Android utility. The "I'd rather this app didn't shout
   what it is" choice.
4. **Monochrome** — a Material You themed tile that takes the device
   accent color.

## Constraints

- **Exactly one alias is enabled at a time.** Two enabled aliases
  produce two launcher entries; the user does not want that.
- **No `BOOT_COMPLETED` side-effects.** OEM launchers tend to
  re-enumerate when an alias toggles, but they should not re-fire
  boot-completed listeners that already ran for the package.
- **The widget palette is not affected.** Widgets bind to provider
  components, not to the launcher alias; flipping aliases must not
  invalidate pinned widgets.
- **The existing SplashActivity remains the default-enabled launcher.**
  Users who never open Settings -> Appearance see no behavior change.

## Implementation strategy

The work splits into three layers; the lowest is the only one shipped
so far.

### Layer 1 — Planner (shipped 2026-05-26)

[`LauncherIconAliasPlan`](../../app/src/main/java/io/github/muntashirakon/AppManager/main/LauncherIconAliasPlan.java)
is a pure-function diff: given the current enabled-set and a target
style, it returns the minimum set of `Change(alias, enabled)` records
the controller needs to apply. JVM-unit-tested with 11 cases covering
the default-to-neutral transition, no-op detection, multi-enabled
collapse, determinism, and value-based `Change.equals`.

The planner deliberately knows nothing about Android, PackageManager,
or activity-alias names — only the four-style enum. Manifest names
live in Layer 2 so they can be reordered without touching the
planner's tests.

### Layer 2 — Manifest activity-aliases (not yet shipped)

Three new `<activity-alias>` entries target `.main.SplashActivity`:

```xml
<activity-alias
    android:name=".main.SplashAliasNgMark"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:targetActivity=".main.SplashActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>

<activity-alias
    android:name=".main.SplashAliasNeutral"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_neutral"
    android:roundIcon="@mipmap/ic_launcher_neutral_round"
    android:targetActivity=".main.SplashActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>

<activity-alias
    android:name=".main.SplashAliasMonochrome"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_mono"
    android:roundIcon="@mipmap/ic_launcher_mono_round"
    android:targetActivity=".main.SplashActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

Each alias inherits its target's `roundIcon` setup; only the icon
resource differs. None declares an `android:label` — the launcher
shows the app's `applicationLabel` (still "AppManagerNG") regardless
of alias.

### Layer 3 — Controller (not yet shipped)

A small Android-side `LauncherIconAliasController` wraps
`PackageManager.setComponentEnabledSetting`:

```java
public void apply(@NonNull LauncherIconStyle target) {
    Set<LauncherIconStyle> current = readCurrentlyEnabled();
    for (Change c : LauncherIconAliasPlan.plan(current, target)) {
        ComponentName component = aliasComponentFor(c.alias);
        int state = c.enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(component, state,
                PackageManager.DONT_KILL_APP);  // critical
    }
    prefs.edit().putString(KEY_LAUNCHER_STYLE, target.name()).apply();
}
```

Notes:
- `DONT_KILL_APP` is critical — without it, flipping the alias kills
  the process and pinned shortcuts go through a re-create roundtrip.
- The current state is read from `PackageManager.getComponentEnabledSetting`
  per alias, not from the SharedPreferences key, so the controller
  remains the source of truth even if the prefs file drifts.
- The Settings UI surfaces all four options in a single
  `ListPreference` and calls `apply` synchronously; the actual
  PackageManager mutation is fast (sub-100ms in practice on Pixel 6+).

## Glossary copy (for the future in-app entry)

> **Launcher icon style** — controls which icon Android shows on the
> home screen for AppManagerNG. The "Default" option uses the
> standard NG mark; "Neutral square" shows a discreet system-styled
> square for users who'd rather the app didn't stand out; "Monochrome"
> uses a Material You themed tile that picks up the device accent
> color. Switching styles takes effect immediately; pinned widgets
> and shortcuts are not affected.

## Open follow-ups

- [ ] Author the three new launcher icon drawables (`ic_launcher_neutral`,
  `ic_launcher_neutral_round`, `ic_launcher_mono`,
  `ic_launcher_mono_round`). Designer input required.
- [ ] Add the three activity-aliases to `AndroidManifest.xml`.
- [ ] Ship `LauncherIconAliasController` and the
  `Settings -> Appearance -> Launcher icon style` entry.
- [ ] Add `pref_glossary_launcher_icon_*` strings using the glossary
  copy above.
