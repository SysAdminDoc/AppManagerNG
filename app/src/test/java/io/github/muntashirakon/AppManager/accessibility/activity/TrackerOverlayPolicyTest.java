// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.WindowManager;

import org.junit.Test;

public class TrackerOverlayPolicyTest {
    @Test
    @SuppressWarnings("deprecation")
    public void windowTypeUsesAccessibilityOverlayWhenPlatformSupportsIt() {
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE,
                TrackerOverlayPolicy.windowTypeForSdk(Build.VERSION_CODES.LOLLIPOP));
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                TrackerOverlayPolicy.windowTypeForSdk(Build.VERSION_CODES.LOLLIPOP_MR1));
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                TrackerOverlayPolicy.windowTypeForSdk(Build.VERSION_CODES.O));
    }

    @Test
    public void flagsAvoidNoLimitsAndPassOutsideTouches() {
        int flags = TrackerOverlayPolicy.windowFlags();

        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
        assertEquals(0, flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        assertEquals(0, flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    @Test
    public void expandedWidthStaysInsideDisplayMargins() {
        int width = TrackerOverlayPolicy.expandedWidthPx(1080, 3f);

        assertTrue(width >= 840);
        assertTrue(width <= 984);
    }

    @Test
    public void expandedWidthHandlesVerySmallDisplays() {
        int width = TrackerOverlayPolicy.expandedWidthPx(260, 1f);

        assertEquals(228, width);
    }

    @Test
    public void centeredOffsetsClampToVisibleBounds() {
        int clampedLeft = TrackerOverlayPolicy.clampHorizontalOffset(-500, 1080, 840, 48);
        int clampedRight = TrackerOverlayPolicy.clampHorizontalOffset(500, 1080, 840, 48);
        int clampedMiddle = TrackerOverlayPolicy.clampHorizontalOffset(24, 1080, 840, 48);

        assertEquals(-72, clampedLeft);
        assertEquals(72, clampedRight);
        assertEquals(24, clampedMiddle);
    }

    @Test
    public void centeredOffsetsCollapseWhenWindowConsumesSafeArea() {
        assertEquals(0, TrackerOverlayPolicy.clampVerticalOffset(200, 400, 390, 16));
        assertEquals(0, TrackerOverlayPolicy.clampVerticalOffset(-200, 400, 390, 16));
    }

    @Test
    public void layoutUpdatesAreThrottled() {
        assertTrue(TrackerOverlayPolicy.shouldApplyLayoutUpdate(-1, 100));
        assertFalse(TrackerOverlayPolicy.shouldApplyLayoutUpdate(100, 120));
        assertTrue(TrackerOverlayPolicy.shouldApplyLayoutUpdate(100, 132));
        assertTrue(TrackerOverlayPolicy.shouldApplyLayoutUpdate(100, 90));
    }

    @Test
    public void repeatedWindowManagerFailuresDisableTracker() {
        assertFalse(TrackerOverlayPolicy.shouldDisableAfterFailure(1));
        assertFalse(TrackerOverlayPolicy.shouldDisableAfterFailure(2));
        assertTrue(TrackerOverlayPolicy.shouldDisableAfterFailure(3));
    }
}
