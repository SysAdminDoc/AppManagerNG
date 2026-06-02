// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.main.LauncherIconAliasPlan.Change;
import io.github.muntashirakon.AppManager.main.LauncherIconAliasPlan.LauncherIconStyle;

/**
 * Android-side controller (Layer 3) for the T21-E discreet launcher-icon
 * feature. Maps each {@link LauncherIconStyle} to its manifest component and
 * applies the {@link LauncherIconAliasPlan} diff through
 * {@link PackageManager#setComponentEnabledSetting}.
 *
 * <p>The current state is read from PackageManager (not a preferences mirror)
 * so the controller stays the source of truth even if a prefs file drifts.
 * {@link PackageManager#DONT_KILL_APP} is critical: without it, flipping an
 * alias kills the process and pinned shortcuts go through a re-create
 * roundtrip.
 *
 * <p>The {@code DEFAULT} style is the manifest-enabled {@code SplashActivity}
 * launcher entry; the other three are {@code enabled="false"} aliases that
 * target it. An app can always change its own component states, so this needs
 * no special permission.
 *
 * <p><b>Device verification pending:</b> the alias enable/disable round-trip
 * and the launcher's re-enumeration behavior cannot be exercised from a CI
 * host. See {@code docs/architecture/launcher-icon-aliases.md}.
 */
public final class LauncherIconAliasController {

    // Manifest class names are declared against the source namespace
    // (io.github.muntashirakon.AppManager), independent of the NG applicationId.
    private static final String NAMESPACE = "io.github.muntashirakon.AppManager.main.";
    @VisibleForTesting
    static final String CLASS_DEFAULT = NAMESPACE + "SplashActivity";
    @VisibleForTesting
    static final String CLASS_NG_MARK = NAMESPACE + "SplashAliasNgMark";
    @VisibleForTesting
    static final String CLASS_NEUTRAL = NAMESPACE + "SplashAliasNeutral";
    @VisibleForTesting
    static final String CLASS_MONOCHROME = NAMESPACE + "SplashAliasMonochrome";

    private LauncherIconAliasController() {
    }

    /**
     * Resolve the style the launcher is currently showing from the live
     * component-enabled state.
     */
    @NonNull
    public static LauncherIconStyle currentStyle(@NonNull Context context) {
        return LauncherIconAliasPlan.resolveCurrent(readCurrentlyEnabled(context));
    }

    /**
     * Switch the launcher icon to {@code target}, enabling exactly one alias
     * and disabling the rest. A no-op when the launcher already shows the
     * target style.
     */
    public static void apply(@NonNull Context context, @NonNull LauncherIconStyle target) {
        PackageManager pm = context.getPackageManager();
        Set<LauncherIconStyle> current = readCurrentlyEnabled(context);
        java.util.List<Change> plan = LauncherIconAliasPlan.plan(current, target);
        // Apply enables BEFORE disables, regardless of the plan's canonical
        // iteration order. Each setComponentEnabledSetting() is persisted
        // independently, so applying a disable-then-enable plan in order opens a
        // real window with zero enabled launcher aliases; if the process is
        // killed or the second call throws in that window the app disappears
        // from the launcher (unlaunchable except via system app-info). Enabling
        // the target first means a mid-sequence failure at worst leaves two
        // aliases enabled — a benign state resolveCurrent() already tolerates.
        for (Change change : plan) {
            if (change.enabled) {
                pm.setComponentEnabledSetting(componentFor(context, change.alias),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
        }
        for (Change change : plan) {
            if (!change.enabled) {
                pm.setComponentEnabledSetting(componentFor(context, change.alias),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }

    @NonNull
    private static Set<LauncherIconStyle> readCurrentlyEnabled(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        Set<LauncherIconStyle> enabled = new LinkedHashSet<>();
        for (LauncherIconStyle style : LauncherIconStyle.values()) {
            if (isEnabled(pm, componentFor(context, style), style)) {
                enabled.add(style);
            }
        }
        return enabled;
    }

    private static boolean isEnabled(@NonNull PackageManager pm, @NonNull ComponentName component,
                                     @NonNull LauncherIconStyle style) {
        int state;
        try {
            state = pm.getComponentEnabledSetting(component);
        } catch (Throwable th) {
            return manifestDefaultEnabled(style);
        }
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return false;
            default:
                // DEFAULT / unset: fall back to the manifest's android:enabled.
                return manifestDefaultEnabled(style);
        }
    }

    private static boolean manifestDefaultEnabled(@NonNull LauncherIconStyle style) {
        // Only the SplashActivity launcher entry is enabled in the manifest.
        return style == LauncherIconStyle.DEFAULT;
    }

    @NonNull
    private static ComponentName componentFor(@NonNull Context context, @NonNull LauncherIconStyle style) {
        return new ComponentName(context.getPackageName(), classNameFor(style));
    }

    @VisibleForTesting
    @NonNull
    static String classNameFor(@NonNull LauncherIconStyle style) {
        switch (style) {
            case NG_MARK:
                return CLASS_NG_MARK;
            case NEUTRAL_SQUARE:
                return CLASS_NEUTRAL;
            case MONOCHROME:
                return CLASS_MONOCHROME;
            case DEFAULT:
            default:
                return CLASS_DEFAULT;
        }
    }
}
