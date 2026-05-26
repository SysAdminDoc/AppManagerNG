// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnackbarDurationPolicyTest {

    @Test
    public void normalSeverityUsesFourSecondBaseAtScaleOne() {
        assertEquals(SnackbarDurationPolicy.BASE_NORMAL_MS,
                SnackbarDurationPolicy.windowFor(
                        SnackbarDurationPolicy.Severity.NORMAL, 1.0f));
    }

    @Test
    public void highSeverityUsesSevenSecondBaseAtScaleOne() {
        assertEquals(SnackbarDurationPolicy.BASE_HIGH_MS,
                SnackbarDurationPolicy.windowFor(
                        SnackbarDurationPolicy.Severity.HIGH, 1.0f));
    }

    @Test
    public void criticalSeverityUsesTenSecondBaseAtScaleOne() {
        assertEquals(SnackbarDurationPolicy.BASE_CRITICAL_MS,
                SnackbarDurationPolicy.windowFor(
                        SnackbarDurationPolicy.Severity.CRITICAL, 1.0f));
    }

    @Test
    public void scaleZeroIsTreatedAsReducedMotionFloorNotInstant() {
        // Reduced-motion = scale 0 must still produce a usable undo window.
        long window = SnackbarDurationPolicy.windowFor(
                SnackbarDurationPolicy.Severity.NORMAL, 0.0f);
        assertEquals(SnackbarDurationPolicy.BASE_NORMAL_MS / 2, window);
        // And never less than the absolute floor.
        assertTrue(window >= SnackbarDurationPolicy.MIN_WINDOW_MS);
    }

    @Test
    public void negativeScaleClampsToFloor() {
        long window = SnackbarDurationPolicy.windowFor(
                SnackbarDurationPolicy.Severity.CRITICAL, -2.0f);
        // CRITICAL base 10_000 * 0.5 = 5_000
        assertEquals(5_000L, window);
    }

    @Test
    public void scaleAboveCeilingClampsToFourX() {
        long window = SnackbarDurationPolicy.windowFor(
                SnackbarDurationPolicy.Severity.NORMAL, 99.0f);
        // 4000 * 4.0 = 16000
        assertEquals(16_000L, window);
    }

    @Test
    public void minWindowFloorAppliesEvenAfterShrinking() {
        // A hypothetical NORMAL base * 0.3 would land at 1200ms; clamp to MIN.
        long window = SnackbarDurationPolicy.windowFor(
                SnackbarDurationPolicy.Severity.NORMAL, 0.3f);
        assertEquals(SnackbarDurationPolicy.MIN_WINDOW_MS, window);
    }

    @Test
    public void maxWindowCeilingAppliesEvenWhenScaleWouldOverflow() {
        // CRITICAL base * 4.0 = 40_000ms which is under the ceiling, so the
        // straightforward result wins. Verify ceiling by feeding an absurd
        // scale - the clamp_scale caps at 4x, so 40_000 stays.
        long window = SnackbarDurationPolicy.windowFor(
                SnackbarDurationPolicy.Severity.CRITICAL, 99.0f);
        assertEquals(40_000L, window);
        assertTrue(window <= SnackbarDurationPolicy.MAX_WINDOW_MS);
    }

    @Test
    public void allSeveritiesObserveMinFloor() {
        // No combination of severity + scale should ever return less than MIN.
        float[] scales = {-1f, 0f, 0.001f, 0.1f, 0.49f, 0.5f};
        for (SnackbarDurationPolicy.Severity s : SnackbarDurationPolicy.Severity.values()) {
            for (float scale : scales) {
                long w = SnackbarDurationPolicy.windowFor(s, scale);
                assertTrue("Severity " + s + " scale " + scale + " under floor: " + w,
                        w >= SnackbarDurationPolicy.MIN_WINDOW_MS);
            }
        }
    }
}
