// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.WindowManager;

import org.junit.Test;

public class TopAppOverlayPolicyTest {
    @Test
    public void overlayPermissionComesFirst() {
        assertEquals(TopAppOverlayPolicy.StartStep.OVERLAY_PERMISSION,
                TopAppOverlayPolicy.nextStep(false, true, true, true));
    }

    @Test
    public void eitherSourceIsEnoughToStart() {
        assertEquals(TopAppOverlayPolicy.StartStep.START, TopAppOverlayPolicy.nextStep(true, true, false, true));
        assertEquals(TopAppOverlayPolicy.StartStep.START, TopAppOverlayPolicy.nextStep(true, false, true, true));
    }

    @Test
    public void accessibilityAloneIsEnoughWhenUsageAccessIsSwitchedOff() {
        assertEquals(TopAppOverlayPolicy.StartStep.START, TopAppOverlayPolicy.nextStep(true, true, false, false));
    }

    @Test
    public void noSourceAsksForUsageAccess() {
        assertEquals(TopAppOverlayPolicy.StartStep.USAGE_ACCESS,
                TopAppOverlayPolicy.nextStep(true, false, false, true));
    }

    @Test
    public void aSystemGrantDoesNotOverrideTheUsersOwnSwitch() {
        assertEquals(TopAppOverlayPolicy.StartStep.ACCESSIBILITY,
                TopAppOverlayPolicy.nextStep(true, false, true, false));
        assertEquals(TopAppOverlayPolicy.StartStep.ACCESSIBILITY,
                TopAppOverlayPolicy.nextStep(true, false, false, false));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void windowTypeFollowsTheVersion() {
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE,
                TopAppOverlayPolicy.windowTypeForSdk(Build.VERSION_CODES.N_MR1));
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                TopAppOverlayPolicy.windowTypeForSdk(Build.VERSION_CODES.O));
    }

    @Test
    public void theWindowNeverTakesTouchesOutsideItself() {
        int flags = TopAppOverlayPolicy.windowFlags();

        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
    }

    @Test
    public void collapsedBubbleStaysUnderTheUntrustedTouchLimit() {
        assertTrue(TopAppOverlayPolicy.COLLAPSED_ALPHA < 0.8f);
    }

    @Test
    public void clampKeepsTheBubbleOnScreen() {
        assertEquals(8, TopAppOverlayPolicy.clamp(-100, 200, 1000, 8));
        assertEquals(500, TopAppOverlayPolicy.clamp(500, 200, 1000, 8));
        assertEquals(792, TopAppOverlayPolicy.clamp(5000, 200, 1000, 8));
    }

    @Test
    public void clampSurvivesABubbleWiderThanTheDisplay() {
        assertEquals(8, TopAppOverlayPolicy.clamp(500, 2000, 1000, 8));
    }

    @Test
    public void remapKeepsTheRelativeSpotAcrossARotation() {
        // Halfway along a range of 1000 - 200 - 16 = 784 is 400, and halfway along 2000 - 200 - 16 = 1784 is 900.
        assertEquals(900, TopAppOverlayPolicy.remap(400, 200, 1000, 2000, 8));
    }

    @Test
    public void remapKeepsAnEdgePositionAtThatEdge() {
        assertEquals(8, TopAppOverlayPolicy.remap(8, 200, 1000, 2000, 8));
        assertEquals(1792, TopAppOverlayPolicy.remap(792, 200, 1000, 2000, 8));
    }

    @Test
    public void remapSurvivesABubbleThatFilledTheOldDisplay() {
        assertEquals(500, TopAppOverlayPolicy.remap(500, 1000, 1000, 2000, 8));
    }

    @Test
    public void dragStartsOnlyPastTheSlop() {
        assertFalse(TopAppOverlayPolicy.isDrag(3, 4, 5));
        assertTrue(TopAppOverlayPolicy.isDrag(4, 4, 5));
        assertTrue(TopAppOverlayPolicy.isDrag(-6, 0, 5));
    }
}
