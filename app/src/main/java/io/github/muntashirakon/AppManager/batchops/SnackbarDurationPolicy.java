// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.NonNull;

/**
 * Pure-function policy for the SnackBar undo-window duration that pairs with
 * {@link UndoableActionQueue} (T21-F). The policy honours the system
 * animation scale so the "reduced motion" / Settings -> Accessibility ->
 * Animations user choice ripples into how long destructive ops stay
 * undoable.
 *
 * <p>Why this layer exists: the {@link UndoableActionQueue} contract is
 * intentionally minimal (it knows nothing about Android, accessibility
 * settings, or SnackBars). The duration computation is the part that
 * <em>does</em> need to read system state, and it is just enough logic to
 * benefit from JVM-only unit tests. By keeping it as a static pure
 * function, the UI wiring becomes:
 *
 * <pre>{@code
 * float animScale = Settings.Global.getFloat(resolver,
 *         Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
 * long ms = SnackbarDurationPolicy.windowFor(severity, animScale);
 * int handle = queue.defer(label, commit, ms);
 * showSnackbar(label, /* duration *&#47; ms, () -> queue.cancel(handle));
 * }</pre>
 *
 * <p>Reasoning behind the numbers, mirroring the
 * {@code BaseTransientBottomBar} duration constants from Material
 * Components 1.13:
 * <ul>
 *   <li>{@link Severity#NORMAL} - 4 s. Matches
 *       {@code Snackbar.LENGTH_LONG}.</li>
 *   <li>{@link Severity#HIGH} - 7 s. Wider window for destructive batch
 *       operations like clear-data and freeze-batch where the user is
 *       more likely to misclick.</li>
 *   <li>{@link Severity#CRITICAL} - 10 s. Used for uninstall and other
 *       on-disk-destructive actions; 10 s is the upper bound a SnackBar
 *       can hold open without becoming hostile.</li>
 * </ul>
 *
 * <p>The window is scaled by {@code animScale}, clamped to {@code [0.5x, 4x]}
 * so a malformed system setting cannot collapse the window to zero or
 * stretch it to an absurd value. {@code animScale = 0} (the canonical
 * "all animations off" setting) is treated as the explicit reduced-motion
 * floor of 0.5x rather than zero, because we want the undo affordance to
 * remain reachable even when animations are disabled.
 */
public final class SnackbarDurationPolicy {

    public enum Severity {
        NORMAL,
        HIGH,
        CRITICAL
    }

    /** Base windows (milliseconds) before scaling. */
    static final long BASE_NORMAL_MS = 4_000L;
    static final long BASE_HIGH_MS = 7_000L;
    static final long BASE_CRITICAL_MS = 10_000L;

    /** Clamp ceilings. */
    static final float MIN_SCALE = 0.5f;
    static final float MAX_SCALE = 4.0f;

    /** Absolute floor so an undo affordance never collapses to "no time". */
    public static final long MIN_WINDOW_MS = 1_500L;
    public static final long MAX_WINDOW_MS = 60_000L;

    private SnackbarDurationPolicy() {
    }

    /**
     * Compute the SnackBar undo window for a given severity and the
     * current Android {@code Settings.Global.ANIMATOR_DURATION_SCALE} (or
     * any equivalent reduced-motion signal). Returns a clamped millisecond
     * value.
     */
    public static long windowFor(@NonNull Severity severity, float animScale) {
        long base = baseWindowFor(severity);
        float clampedScale = clampScale(animScale);
        long scaled = (long) (base * clampedScale);
        if (scaled < MIN_WINDOW_MS) return MIN_WINDOW_MS;
        if (scaled > MAX_WINDOW_MS) return MAX_WINDOW_MS;
        return scaled;
    }

    static long baseWindowFor(@NonNull Severity severity) {
        switch (severity) {
            case CRITICAL:
                return BASE_CRITICAL_MS;
            case HIGH:
                return BASE_HIGH_MS;
            case NORMAL:
            default:
                return BASE_NORMAL_MS;
        }
    }

    static float clampScale(float animScale) {
        // Negative or zero scale -> treat as reduced-motion floor, not as
        // "instant", so the undo affordance is always reachable.
        if (animScale <= 0f) return MIN_SCALE;
        if (animScale < MIN_SCALE) return MIN_SCALE;
        if (animScale > MAX_SCALE) return MAX_SCALE;
        return animScale;
    }
}
