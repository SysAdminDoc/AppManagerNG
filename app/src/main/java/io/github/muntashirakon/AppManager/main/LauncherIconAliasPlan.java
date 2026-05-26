// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-function planner for the T21-E "Discreet / generic launcher-icon
 * mode" row. Given the user's desired launcher-icon style, produces the
 * deterministic set of activity-alias enable/disable changes a controller
 * (Android side, holds a {@code PackageManager}) needs to apply.
 *
 * <p>The contract is intentionally narrow:
 * <ul>
 *   <li>Exactly one alias is enabled at a time. The planner never produces
 *       a plan that leaves the launcher with zero enabled aliases or with
 *       more than one.</li>
 *   <li>Only the aliases whose enabled-state changes between
 *       {@code currentlyEnabled} and {@code target} are returned, so the
 *       caller can short-circuit a no-op invocation and avoid unnecessary
 *       {@code BOOT_COMPLETED} ripple in OEM launchers.</li>
 *   <li>The planner is JVM-clean: enums plus a deterministic
 *       {@link LinkedHashMap}. Real activity-alias names live in the
 *       Android manifest; the planner only references their canonical
 *       enum keys.</li>
 * </ul>
 *
 * <p>Allowed styles (mapped to manifest activity-aliases at controller
 * boundary):
 * <ul>
 *   <li>{@link LauncherIconStyle#DEFAULT} - the original SplashActivity
 *       launcher. Always available even if all aliases are off.</li>
 *   <li>{@link LauncherIconStyle#NG_MARK} - the current AppManagerNG
 *       branded icon.</li>
 *   <li>{@link LauncherIconStyle#NEUTRAL_SQUARE} - a neutral
 *       system-styled square; the "discreet" choice.</li>
 *   <li>{@link LauncherIconStyle#MONOCHROME} - a monochrome tile that
 *       takes the device theme tint (Material You).</li>
 * </ul>
 */
public final class LauncherIconAliasPlan {

    public enum LauncherIconStyle {
        DEFAULT,
        NG_MARK,
        NEUTRAL_SQUARE,
        MONOCHROME
    }

    /** A single change a controller needs to apply. */
    public static final class Change {
        @NonNull
        public final LauncherIconStyle alias;
        public final boolean enabled;

        public Change(@NonNull LauncherIconStyle alias, boolean enabled) {
            this.alias = alias;
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Change)) return false;
            Change c = (Change) o;
            return enabled == c.enabled && alias == c.alias;
        }

        @Override
        public int hashCode() {
            return alias.hashCode() * 31 + (enabled ? 1 : 0);
        }

        @Override
        public String toString() {
            return alias + "=" + (enabled ? "enabled" : "disabled");
        }
    }

    /** Iteration order used for plan diffs - keeps test output stable. */
    private static final List<LauncherIconStyle> CANONICAL_ORDER = Collections.unmodifiableList(
            Arrays.asList(LauncherIconStyle.DEFAULT, LauncherIconStyle.NG_MARK,
                    LauncherIconStyle.NEUTRAL_SQUARE, LauncherIconStyle.MONOCHROME));

    private LauncherIconAliasPlan() {
    }

    /**
     * Compute the set of changes to take the launcher from
     * {@code currentlyEnabled} to {@code target}. The current-state set is
     * sanitised (deduplicated, null-stripped) before diff. If the input
     * already matches the target, the returned list is empty.
     *
     * @throws IllegalArgumentException if {@code target} is {@code null}.
     */
    @NonNull
    public static List<Change> plan(@NonNull Set<LauncherIconStyle> currentlyEnabled,
                                    @NonNull LauncherIconStyle target) {
        Set<LauncherIconStyle> current = sanitise(currentlyEnabled);
        Set<LauncherIconStyle> desired = Collections.singleton(target);
        // Iterate in canonical order so two callers with the same diff get
        // the same Change list, including index-of-element identity.
        Map<LauncherIconStyle, Boolean> diff = new LinkedHashMap<>();
        for (LauncherIconStyle alias : CANONICAL_ORDER) {
            boolean wasOn = current.contains(alias);
            boolean shouldBeOn = desired.contains(alias);
            if (wasOn != shouldBeOn) {
                diff.put(alias, shouldBeOn);
            }
        }
        List<Change> result = new java.util.ArrayList<>(diff.size());
        for (Map.Entry<LauncherIconStyle, Boolean> e : diff.entrySet()) {
            result.add(new Change(e.getKey(), e.getValue()));
        }
        return result;
    }

    /**
     * Resolve the "currently enabled" style from a raw enabled-set. If the
     * set is malformed (zero or multiple aliases enabled), the resolver
     * picks the canonical fall-through: prefer {@link LauncherIconStyle#DEFAULT}
     * when present, otherwise the first alias in {@link #CANONICAL_ORDER}
     * that is enabled.
     */
    @NonNull
    public static LauncherIconStyle resolveCurrent(@NonNull Set<LauncherIconStyle> currentlyEnabled) {
        Set<LauncherIconStyle> current = sanitise(currentlyEnabled);
        if (current.isEmpty()) return LauncherIconStyle.DEFAULT;
        if (current.size() == 1) {
            for (LauncherIconStyle s : current) return s;
        }
        // Two or more enabled: pick by canonical order.
        if (current.contains(LauncherIconStyle.DEFAULT)) return LauncherIconStyle.DEFAULT;
        for (LauncherIconStyle alias : CANONICAL_ORDER) {
            if (current.contains(alias)) return alias;
        }
        return LauncherIconStyle.DEFAULT;
    }

    private static Set<LauncherIconStyle> sanitise(Set<LauncherIconStyle> raw) {
        Set<LauncherIconStyle> clean = new LinkedHashSet<>();
        if (raw == null) return clean;
        for (LauncherIconStyle s : raw) {
            if (s != null) clean.add(s);
        }
        return clean;
    }
}
