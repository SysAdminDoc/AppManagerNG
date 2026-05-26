// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MotionScaleGateTest {

    @Test
    public void isReducedMotionFlagsAnyZeroAcrossThreeKeys() {
        assertTrue(MotionScaleGate.isReducedMotion(0f, 1f, 1f));
        assertTrue(MotionScaleGate.isReducedMotion(1f, 0f, 1f));
        assertTrue(MotionScaleGate.isReducedMotion(1f, 1f, 0f));
        assertFalse(MotionScaleGate.isReducedMotion(1f, 1f, 1f));
        assertFalse(MotionScaleGate.isReducedMotion(0.5f, 0.5f, 0.5f));
    }

    @Test
    public void nullScalesAreTreatedAsDefault() {
        // Settings.Global missing keys read as null - those default to 1.0,
        // which is NOT reduced-motion.
        assertFalse(MotionScaleGate.isReducedMotion(null, null, null));
        // But a single zero still flips the gate.
        assertTrue(MotionScaleGate.isReducedMotion(null, null, 0f));
    }

    @Test
    public void singleScalePredicateMirrorsThreeArg() {
        assertTrue(MotionScaleGate.isReducedMotion(0f));
        assertFalse(MotionScaleGate.isReducedMotion(0.001f));
        assertFalse(MotionScaleGate.isReducedMotion(1f));
    }

    @Test
    public void parseScaleHandlesMalformedStrings() {
        assertEquals(Float.valueOf(0f), MotionScaleGate.parseScale("0"));
        assertEquals(Float.valueOf(1.0f), MotionScaleGate.parseScale("1.0"));
        assertEquals(Float.valueOf(0.5f), MotionScaleGate.parseScale("0.5"));
        assertNull(MotionScaleGate.parseScale(null));
        assertNull(MotionScaleGate.parseScale(""));
        assertNull(MotionScaleGate.parseScale("not-a-number"));
    }

    @Test
    public void clampMultiplierEnforcesAbsoluteFloorAndCeiling() {
        assertEquals(MotionScaleGate.MIN_SCALE, MotionScaleGate.clampMultiplier(0f), 1e-6f);
        assertEquals(MotionScaleGate.MIN_SCALE, MotionScaleGate.clampMultiplier(-1f), 1e-6f);
        assertEquals(MotionScaleGate.MIN_SCALE, MotionScaleGate.clampMultiplier(0.1f), 1e-6f);
        assertEquals(MotionScaleGate.MAX_SCALE, MotionScaleGate.clampMultiplier(10f), 1e-6f);
        assertEquals(1.0f, MotionScaleGate.clampMultiplier(1.0f), 1e-6f);
    }

    @Test
    public void scaledDurationMillisScalesByClampedFactor() {
        assertEquals(500L, MotionScaleGate.scaledDurationMillis(1_000L, 0f));   // floor 0.5
        assertEquals(1_000L, MotionScaleGate.scaledDurationMillis(1_000L, 1.0f));
        assertEquals(4_000L, MotionScaleGate.scaledDurationMillis(1_000L, 4f));
        assertEquals(4_000L, MotionScaleGate.scaledDurationMillis(1_000L, 99f)); // clamped to MAX
        assertEquals(0L, MotionScaleGate.scaledDurationMillis(0L, 1.0f));
        assertEquals(0L, MotionScaleGate.scaledDurationMillis(-100L, 1.0f));
    }

    @Test
    public void diagnoseReturnsTheActiveReducedMotionSource() {
        // Animator key zero wins because it is the most-restrictive: setting
        // it to zero zeroes property animations too, which transition and
        // window-anim already imply.
        assertEquals(MotionScaleGate.Source.ANIMATOR_DURATION,
                MotionScaleGate.diagnose(0f, 0f, 0f));
        assertEquals(MotionScaleGate.Source.TRANSITION,
                MotionScaleGate.diagnose(0f, 0f, 1f));
        assertEquals(MotionScaleGate.Source.WINDOW,
                MotionScaleGate.diagnose(0f, 1f, 1f));
        assertEquals(MotionScaleGate.Source.NONE,
                MotionScaleGate.diagnose(1f, 1f, 1f));
        assertEquals(MotionScaleGate.Source.NONE,
                MotionScaleGate.diagnose(null, null, null));
    }
}
