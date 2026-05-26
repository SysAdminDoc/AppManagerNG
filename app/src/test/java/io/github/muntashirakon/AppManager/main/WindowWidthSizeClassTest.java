// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowWidthSizeClassTest {

    @Test
    public void compactCoversBelowMediumMin() {
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(0));
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(360));
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(411));
        assertEquals(WindowWidthSizeClass.COMPACT,
                WindowWidthSizeClass.resolve(WindowWidthSizeClass.MEDIUM_MIN_DP - 1));
    }

    @Test
    public void mediumBucketIsInclusiveOnLowerBound() {
        assertEquals(WindowWidthSizeClass.MEDIUM,
                WindowWidthSizeClass.resolve(WindowWidthSizeClass.MEDIUM_MIN_DP));
        assertEquals(WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.resolve(720));
        assertEquals(WindowWidthSizeClass.MEDIUM,
                WindowWidthSizeClass.resolve(WindowWidthSizeClass.EXPANDED_MIN_DP - 1));
    }

    @Test
    public void expandedBucketIsInclusiveOnLowerBound() {
        assertEquals(WindowWidthSizeClass.EXPANDED,
                WindowWidthSizeClass.resolve(WindowWidthSizeClass.EXPANDED_MIN_DP));
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.resolve(1024));
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.resolve(2560));
    }

    @Test
    public void negativeWidthsClampToCompact() {
        // A malformed configuration (zero / negative width) must never crash;
        // resolve treats the width as zero -> COMPACT.
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(-1));
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(Integer.MIN_VALUE));
    }

    @Test
    public void supportsTwoPaneIsTrueAboveMediumThreshold() {
        assertFalse(WindowWidthSizeClass.supportsTwoPane(599));
        assertTrue(WindowWidthSizeClass.supportsTwoPane(600));
        assertTrue(WindowWidthSizeClass.supportsTwoPane(839));
        assertTrue(WindowWidthSizeClass.supportsTwoPane(840));
    }

    @Test
    public void requiresTwoPaneOnlyAtExpanded() {
        assertFalse(WindowWidthSizeClass.requiresTwoPane(0));
        assertFalse(WindowWidthSizeClass.requiresTwoPane(599));
        assertFalse(WindowWidthSizeClass.requiresTwoPane(600));
        assertFalse(WindowWidthSizeClass.requiresTwoPane(839));
        assertTrue(WindowWidthSizeClass.requiresTwoPane(840));
        assertTrue(WindowWidthSizeClass.requiresTwoPane(1280));
    }

    @Test
    public void thresholdsMatchAndroidxWindowConstants() {
        // These values are stable across androidx.window 1.x; if they ever
        // change, this test deliberately fails so the gate is audited.
        assertEquals(600, WindowWidthSizeClass.MEDIUM_MIN_DP);
        assertEquals(840, WindowWidthSizeClass.EXPANDED_MIN_DP);
    }

    @Test
    public void everyAndroidDeviceFormFactorMapsToExactlyOneBucket() {
        // Representative form factors (dp width, post-orientation).
        // Pixel 7 portrait
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.resolve(411));
        // Pixel 7 landscape
        assertEquals(WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.resolve(800));
        // Pixel Fold inner portrait
        assertEquals(WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.resolve(673));
        // Pixel Fold inner landscape
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.resolve(841));
        // Tab S9 Ultra portrait
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.resolve(1280));
    }
}
