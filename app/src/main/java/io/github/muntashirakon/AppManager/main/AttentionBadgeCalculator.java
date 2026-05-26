// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

/**
 * Pure-function badge calculator for the T21-G "attention badges" main-list
 * row decoration.
 *
 * <p>Each row in the app list can surface a tiny badge that signals
 * actionable state - similar to Now in Android's unread-bookmark badge. The
 * calculator owns the prioritisation rules so the adapter only has to render
 * what {@link #compute(Signals)} returns; the data sources that feed
 * {@link Signals} stay in the existing app-cache layer.
 *
 * <p>Priority order, from highest to lowest:
 * <ol>
 *   <li>{@link Kind#OS_REVERT} - the OS reverted a recent privileged
 *       mutation. This usually means the user's change did not stick and
 *       needs investigation; surfacing it overrides every other badge.</li>
 *   <li>{@link Kind#DANGEROUS_PERMISSION} - the app requested a dangerous
 *       permission at install / upgrade time that is still ungranted.</li>
 *   <li>{@link Kind#DISABLED_COMPONENT} - the app has user-disabled
 *       components that AppManagerNG (or an external tool) flipped off
 *       earlier and the user may want to remember are off.</li>
 *   <li>{@link Kind#NONE} - no badge.</li>
 * </ol>
 *
 * <p>Severity layers on top of kind and only affects rendering tint:
 * {@link Severity#WARN} for OS revert, {@link Severity#INFO} otherwise.
 *
 * <p>The calculator is intentionally JVM-only; tests pass plain ints and
 * verify the priority + count + severity contract.
 */
public final class AttentionBadgeCalculator {

    /** The factual inputs from the app-cache layer. */
    public static final class Signals {
        public final int dangerousPermissionsRequestedNotGranted;
        public final int userDisabledComponentCount;
        public final int recentOsRevertCount;

        public Signals(int dangerousPermissionsRequestedNotGranted,
                       int userDisabledComponentCount,
                       int recentOsRevertCount) {
            this.dangerousPermissionsRequestedNotGranted =
                    Math.max(0, dangerousPermissionsRequestedNotGranted);
            this.userDisabledComponentCount = Math.max(0, userDisabledComponentCount);
            this.recentOsRevertCount = Math.max(0, recentOsRevertCount);
        }
    }

    public enum Kind {
        NONE,
        DISABLED_COMPONENT,
        DANGEROUS_PERMISSION,
        OS_REVERT
    }

    public enum Severity {
        INFO,
        WARN
    }

    /** Computed badge to render. {@link #count} is the number to draw. */
    public static final class Badge {
        @NonNull
        public final Kind kind;
        public final int count;
        @NonNull
        public final Severity severity;

        public Badge(@NonNull Kind kind, int count, @NonNull Severity severity) {
            this.kind = kind;
            this.count = count;
            this.severity = severity;
        }

        @NonNull
        public static Badge none() {
            return new Badge(Kind.NONE, 0, Severity.INFO);
        }

        public boolean isNone() {
            return kind == Kind.NONE;
        }
    }

    private AttentionBadgeCalculator() {
    }

    /** Apply the priority rules to a row's signals and return the badge. */
    @NonNull
    public static Badge compute(@NonNull Signals signals) {
        if (signals.recentOsRevertCount > 0) {
            return new Badge(Kind.OS_REVERT, signals.recentOsRevertCount, Severity.WARN);
        }
        if (signals.dangerousPermissionsRequestedNotGranted > 0) {
            return new Badge(Kind.DANGEROUS_PERMISSION,
                    signals.dangerousPermissionsRequestedNotGranted, Severity.INFO);
        }
        if (signals.userDisabledComponentCount > 0) {
            return new Badge(Kind.DISABLED_COMPONENT,
                    signals.userDisabledComponentCount, Severity.INFO);
        }
        return Badge.none();
    }

    /**
     * Format a count for the badge label. Counts above 99 collapse to "99+"
     * the same way Material badge components do; passing zero or negative
     * returns an empty string so callers never render a bare zero.
     */
    @NonNull
    public static String formatCount(int count) {
        if (count <= 0) return "";
        if (count > 99) return "99+";
        return Integer.toString(count);
    }
}
