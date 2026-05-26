// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure-function gate for the three Android animation-scale settings.
 *
 * <p>{@link MotionUtils} owns the Android-side {@code ContentResolver}
 * lookup; this class is its JVM-clean engine. The data layer is shaped to
 * be callable from anywhere that has a {@code float} scale value (e.g. an
 * already-resolved {@code Settings.Global.getFloat(...)} read, or the
 * {@link io.github.muntashirakon.AppManager.batchops.SnackbarDurationPolicy}
 * call site) without needing a {@code Context}.
 *
 * <p>Centralising the predicate keeps the reduced-motion gate consistent
 * across every animation surface: the OS reports three keys
 * ({@code window_animation_scale}, {@code transition_animation_scale},
 * {@code animator_duration_scale}); any one of them being {@code 0}
 * means the user opted into reduced motion.
 *
 * <p>Bounds:
 * <ul>
 *   <li>{@link #MIN_SCALE} = {@value #MIN_SCALE} - absolute floor used by
 *       UI surfaces that want a non-zero duration even under reduced
 *       motion (so the user can still see the affordance, but at half
 *       speed).</li>
 *   <li>{@link #MAX_SCALE} = {@value #MAX_SCALE} - absolute ceiling so a
 *       malformed system setting cannot stretch an animation to absurd
 *       durations.</li>
 * </ul>
 */
public final class MotionScaleGate {

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 4.0f;

    private MotionScaleGate() {
    }

    /**
     * The user requested reduced motion when any of the three animation
     * scales is exactly zero. A null value (string not present) means the
     * setting is the system default 1.0 and is not reduced-motion.
     */
    public static boolean isReducedMotion(@Nullable Float windowScale,
                                          @Nullable Float transitionScale,
                                          @Nullable Float animatorScale) {
        return isZero(windowScale) || isZero(transitionScale) || isZero(animatorScale);
    }

    /** Convenience: a single scale value is reduced-motion when it is zero. */
    public static boolean isReducedMotion(float scale) {
        return scale == 0f;
    }

    /**
     * Parse an Android {@code Settings.Global} string value into a float.
     * Returns {@code null} if the value cannot be parsed - mirrors the
     * legacy {@code MotionUtils.isAnimationScaleDisabled} swallow-on-fail
     * behavior without folding the predicate into the parser.
     */
    @Nullable
    public static Float parseScale(@Nullable String rawValue) {
        if (rawValue == null) return null;
        try {
            return Float.parseFloat(rawValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Clamp a raw scale into the {@code [MIN_SCALE, MAX_SCALE]} window.
     * Zero collapses to {@link #MIN_SCALE} so reduced-motion surfaces that
     * <em>do</em> need a visible duration (snackbars, banners, focus rings)
     * remain perceivable. Negative scales also clamp to the floor.
     */
    public static float clampMultiplier(float scale) {
        if (scale <= 0f) return MIN_SCALE;
        if (scale < MIN_SCALE) return MIN_SCALE;
        if (scale > MAX_SCALE) return MAX_SCALE;
        return scale;
    }

    /**
     * Apply the clamped multiplier to a base duration. Returns a long for
     * the common case where the value will be fed back into the platform
     * animation APIs.
     */
    public static long scaledDurationMillis(long baseMillis, float scale) {
        if (baseMillis <= 0L) return 0L;
        return (long) (baseMillis * clampMultiplier(scale));
    }

    private static boolean isZero(@Nullable Float value) {
        return value != null && value.floatValue() == 0f;
    }

    /** Returns the active reduced-motion source for diagnostics / UI hints. */
    @NonNull
    public static Source diagnose(@Nullable Float windowScale,
                                  @Nullable Float transitionScale,
                                  @Nullable Float animatorScale) {
        if (isZero(animatorScale)) return Source.ANIMATOR_DURATION;
        if (isZero(transitionScale)) return Source.TRANSITION;
        if (isZero(windowScale)) return Source.WINDOW;
        return Source.NONE;
    }

    public enum Source {
        NONE,
        WINDOW,
        TRANSITION,
        ANIMATOR_DURATION
    }
}
