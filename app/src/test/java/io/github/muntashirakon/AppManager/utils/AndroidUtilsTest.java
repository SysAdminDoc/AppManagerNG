// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AndroidUtilsTest {
    @Test
    public void sdkAtLeast_matchesMajorOnlyGuardsAtMinorZero() {
        int current = AndroidUtils.makeSdkIntFull(36, 0);

        assertTrue(AndroidUtils.sdkAtLeast(current, 35, 0));
        assertTrue(AndroidUtils.sdkAtLeast(current, 36, 0));
        assertFalse(AndroidUtils.sdkAtLeast(current, 37, 0));
    }

    @Test
    public void sdkAtLeast_canGateMinorReleases() {
        int current = AndroidUtils.makeSdkIntFull(36, 1);

        assertTrue(AndroidUtils.sdkAtLeast(current, 36, 0));
        assertTrue(AndroidUtils.sdkAtLeast(current, 36, 1));
        assertFalse(AndroidUtils.sdkAtLeast(current, 36, 2));
    }

    @Test
    public void sdkIntFullEncoding_roundTripsMajorAndMinor() {
        int sdkIntFull = AndroidUtils.makeSdkIntFull(36, 2);

        assertEquals(36, AndroidUtils.getMajorSdkVersion(sdkIntFull));
        assertEquals(2, AndroidUtils.getMinorSdkVersion(sdkIntFull));
    }

    @Test(expected = IllegalArgumentException.class)
    public void makeSdkIntFull_rejectsNegativeMinor() {
        AndroidUtils.makeSdkIntFull(36, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void makeSdkIntFull_rejectsOutOfRangeMinor() {
        AndroidUtils.makeSdkIntFull(36, 100_000);
    }
}
