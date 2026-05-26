// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.transition.MaterialSharedAxis;

import io.github.muntashirakon.AppManager.R;

public final class MotionUtils {
    public static final int AXIS_Y = MaterialSharedAxis.Y;

    private static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
    private static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
    private static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

    private MotionUtils() {
    }

    public static boolean shouldReduceMotion(@NonNull Context context) {
        ContentResolver resolver = context.getContentResolver();
        return isAnimationScaleDisabled(resolver, WINDOW_ANIMATION_SCALE)
                || isAnimationScaleDisabled(resolver, TRANSITION_ANIMATION_SCALE)
                || isAnimationScaleDisabled(resolver, ANIMATOR_DURATION_SCALE);
    }

    public static void applySharedAxisZTransition(@NonNull Fragment fragment) {
        if (shouldReduceMotion(fragment.requireContext())) {
            fragment.setEnterTransition(null);
            fragment.setReturnTransition(null);
            return;
        }
        fragment.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        fragment.setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    public static void beginSharedAxisDelayedTransition(@NonNull ViewGroup sceneRoot,
                                                       int axis,
                                                       boolean forward) {
        if (shouldReduceMotion(sceneRoot.getContext())) {
            return;
        }
        Transition sharedAxis = new MaterialSharedAxis(axis, forward);
        TransitionManager.beginDelayedTransition(sceneRoot, sharedAxis);
    }

    @NonNull
    public static FragmentTransaction maybeSetDefaultFragmentAnimations(@NonNull Context context,
                                                                        @NonNull FragmentTransaction transaction) {
        if (shouldReduceMotion(context)) {
            return transaction;
        }
        return transaction.setCustomAnimations(
                R.animator.enter_from_left,
                R.animator.enter_from_right,
                R.animator.exit_from_right,
                R.animator.exit_from_left
        );
    }

    public static void setWindowAnimations(@NonNull Context context,
                                           @NonNull WindowManager.LayoutParams params,
                                           @StyleRes int animationStyle) {
        params.windowAnimations = shouldReduceMotion(context) ? 0 : animationStyle;
    }

    private static boolean isAnimationScaleDisabled(@NonNull ContentResolver resolver, @NonNull String key) {
        try {
            String value = Settings.Global.getString(resolver, key);
            return value != null && Float.parseFloat(value) == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
