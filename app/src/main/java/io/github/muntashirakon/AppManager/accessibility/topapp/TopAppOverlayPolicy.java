// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.NonNull;

final class TopAppOverlayPolicy {
    /** Android 12+ refuses touches passing through an overlay above 0.8, so stay clearly under it while collapsed. */
    static final float COLLAPSED_ALPHA = 0.75f;
    static final float EXPANDED_ALPHA = 0.95f;
    static final int EDGE_MARGIN_DP = 8;
    static final float INITIAL_VERTICAL_FRACTION = 0.15f;
    /** Left, not start: a drag moves with the finger whatever the layout direction. */
    static final int GRAVITY = Gravity.TOP | Gravity.LEFT;

    enum StartStep {
        START,
        OVERLAY_PERMISSION,
        USAGE_ACCESS,
        ACCESSIBILITY,
    }

    private TopAppOverlayPolicy() {
    }

    /**
     * What stands between the user and a working bubble. Usage access comes before accessibility because it's one
     * switch and Android 13+ can grey the accessibility switch out for a sideloaded app.
     */
    @NonNull
    static StartStep nextStep(boolean canDrawOverlays, boolean accessibilityEnabled, boolean usageAccessGranted,
                              boolean usageFeatureEnabled) {
        if (!canDrawOverlays) {
            return StartStep.OVERLAY_PERMISSION;
        }
        if (accessibilityEnabled || (usageFeatureEnabled && usageAccessGranted)) {
            return StartStep.START;
        }
        return usageFeatureEnabled ? StartStep.USAGE_ACCESS : StartStep.ACCESSIBILITY;
    }

    @SuppressWarnings("deprecation")
    static int windowTypeForSdk(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    /** Not focusable and not touch modal: every touch outside the bubble's own bounds goes to the app under it. */
    static int windowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    static int edgeMarginPx(float density) {
        return Math.round(EDGE_MARGIN_DP * density);
    }

    static int clamp(int positionPx, int sizePx, int displayPx, int marginPx) {
        int max = Math.max(marginPx, displayPx - sizePx - marginPx);
        return Math.max(marginPx, Math.min(positionPx, max));
    }

    /** Keeps the bubble at the same relative spot when the display changes shape, such as on a rotation. */
    static int remap(int positionPx, int sizePx, int oldDisplayPx, int newDisplayPx, int marginPx) {
        int oldRange = oldDisplayPx - sizePx - 2 * marginPx;
        int newRange = newDisplayPx - sizePx - 2 * marginPx;
        if (oldRange <= 0 || newRange <= 0) {
            return clamp(positionPx, sizePx, newDisplayPx, marginPx);
        }
        float fraction = (positionPx - marginPx) / (float) oldRange;
        return clamp(marginPx + Math.round(fraction * newRange), sizePx, newDisplayPx, marginPx);
    }

    static boolean isDrag(float deltaXPx, float deltaYPx, int touchSlopPx) {
        return deltaXPx * deltaXPx + deltaYPx * deltaYPx > touchSlopPx * touchSlopPx;
    }
}
