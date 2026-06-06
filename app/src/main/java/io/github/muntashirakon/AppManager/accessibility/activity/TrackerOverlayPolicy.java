// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import android.os.Build;
import android.view.WindowManager;

final class TrackerOverlayPolicy {
    static final int EDGE_MARGIN_DP = 16;
    static final int MIN_EXPANDED_WIDTH_DP = 280;
    static final int MAX_EXPANDED_WIDTH_DP = 560;
    static final int FALLBACK_WINDOW_SIZE_DP = 56;
    static final long MIN_LAYOUT_UPDATE_INTERVAL_MS = 32;
    static final int MAX_WINDOW_MANAGER_FAILURES = 3;

    private TrackerOverlayPolicy() {
    }

    @SuppressWarnings("deprecation")
    static int windowTypeForSdk(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    static int windowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    static int edgeMarginPx(float density) {
        return dp(EDGE_MARGIN_DP, density);
    }

    static int fallbackWindowSizePx(float density) {
        return dp(FALLBACK_WINDOW_SIZE_DP, density);
    }

    static int expandedWidthPx(int displayWidthPx, float density) {
        int marginPx = edgeMarginPx(density);
        int safeWidthPx = Math.max(1, displayWidthPx - (marginPx * 2));
        int minWidthPx = Math.min(dp(MIN_EXPANDED_WIDTH_DP, density), safeWidthPx);
        int maxWidthPx = Math.min(dp(MAX_EXPANDED_WIDTH_DP, density), safeWidthPx);
        int targetWidthPx = Math.round(displayWidthPx * 0.72f);
        return clamp(Math.max(minWidthPx, targetWidthPx), minWidthPx, maxWidthPx);
    }

    static int displayedWidth(boolean iconified, int expandedWidthPx) {
        return iconified ? WindowManager.LayoutParams.WRAP_CONTENT : expandedWidthPx;
    }

    static int clampHorizontalOffset(int offsetPx, int displayWidthPx, int windowWidthPx, int marginPx) {
        return clampCenteredOffset(offsetPx, displayWidthPx, windowWidthPx, marginPx);
    }

    static int clampVerticalOffset(int offsetPx, int displayHeightPx, int windowHeightPx, int marginPx) {
        return clampCenteredOffset(offsetPx, displayHeightPx, windowHeightPx, marginPx);
    }

    static boolean shouldApplyLayoutUpdate(long lastUpdateUptimeMillis, long nowUptimeMillis) {
        return lastUpdateUptimeMillis < 0
                || nowUptimeMillis < lastUpdateUptimeMillis
                || nowUptimeMillis - lastUpdateUptimeMillis >= MIN_LAYOUT_UPDATE_INTERVAL_MS;
    }

    static boolean shouldDisableAfterFailure(int failureCount) {
        return failureCount >= MAX_WINDOW_MANAGER_FAILURES;
    }

    private static int clampCenteredOffset(int offsetPx, int displaySizePx, int windowSizePx, int marginPx) {
        int safeWindowSizePx = Math.max(1, windowSizePx);
        int maxOffsetPx = Math.max(0, ((displaySizePx - safeWindowSizePx) / 2) - Math.max(0, marginPx));
        return clamp(offsetPx, -maxOffsetPx, maxOffsetPx);
    }

    private static int dp(int value, float density) {
        float safeDensity = density > 0 ? density : 1f;
        return Math.max(1, Math.round(value * safeDensity));
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            return max;
        }
        return Math.max(min, Math.min(max, value));
    }
}
