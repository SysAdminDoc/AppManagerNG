// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

/**
 * Pure-function resolver for the T21-H Adaptive layout width thresholds.
 *
 * <p>Mirrors the canonical {@code androidx.window.core.layout.WindowWidthSizeClass}
 * breakpoints so AppManagerNG layouts can gate two-pane / split-pane behavior
 * on <em>available width</em> rather than fixed device classes:
 * <ul>
 *   <li>{@link #COMPACT} - width &lt; {@value #MEDIUM_MIN_DP} dp. Single-pane.</li>
 *   <li>{@link #MEDIUM} - width &isin; [{@value #MEDIUM_MIN_DP}, {@value #EXPANDED_MIN_DP}) dp.
 *       Split-pane allowed but optional; typical phone landscape and small
 *       foldable inner display.</li>
 *   <li>{@link #EXPANDED} - width &ge; {@value #EXPANDED_MIN_DP} dp. Split-pane
 *       strongly preferred; typical tablet portrait + ChromeOS.</li>
 * </ul>
 *
 * <p>The resolver is JVM-only and uses no Android API. The activity layer
 * passes the current dp width through {@link #resolve(int)}; layout managers
 * gate their two-pane construction on the result. Keeping the threshold
 * decision in one place lets us audit every gate from a single grep.
 */
public enum WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    /** Lower bound of the MEDIUM bucket in density-independent pixels. */
    public static final int MEDIUM_MIN_DP = 600;
    /** Lower bound of the EXPANDED bucket in density-independent pixels. */
    public static final int EXPANDED_MIN_DP = 840;

    /**
     * Return the bucket {@code widthDp} falls into. Negative widths are
     * treated as zero (i.e. COMPACT) so a malformed configuration never
     * crashes the layout pipeline.
     */
    @NonNull
    public static WindowWidthSizeClass resolve(int widthDp) {
        int safeWidth = Math.max(0, widthDp);
        if (safeWidth < MEDIUM_MIN_DP) return COMPACT;
        if (safeWidth < EXPANDED_MIN_DP) return MEDIUM;
        return EXPANDED;
    }

    /**
     * Convenience predicate the activity / fragment layer can call without
     * having to import the enum. Returns {@code true} for MEDIUM and
     * EXPANDED widths.
     */
    public static boolean supportsTwoPane(int widthDp) {
        return resolve(widthDp) != COMPACT;
    }

    /**
     * Predicate for "definitely two-pane" - the EXPANDED bucket only. Used
     * by surfaces that need a clearer two-pane affordance (Settings list
     * vs detail, App List vs App Details) and would crowd at MEDIUM widths.
     */
    public static boolean requiresTwoPane(int widthDp) {
        return resolve(widthDp) == EXPANDED;
    }
}
