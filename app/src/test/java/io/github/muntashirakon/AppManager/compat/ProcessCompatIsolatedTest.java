// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowProcess;

/**
 * The isolated thumbnail process skips the app's startup, so telling it apart from the app's own
 * processes has to hold on every Android version, in every user.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36})
public class ProcessCompatIsolatedTest {
    @Test
    public void isolatedUidRanges() {
        assertFalse(ProcessCompat.isIsolatedUid(1000));
        assertFalse(ProcessCompat.isIsolatedUid(10123));
        assertFalse(ProcessCompat.isIsolatedUid(89999));
        assertTrue(ProcessCompat.isIsolatedUid(90000));
        assertTrue(ProcessCompat.isIsolatedUid(98999));
        assertTrue(ProcessCompat.isIsolatedUid(99000));
        assertTrue(ProcessCompat.isIsolatedUid(99999));
        // User 1's first app id, and app ids in other users
        assertFalse(ProcessCompat.isIsolatedUid(100000));
        assertFalse(ProcessCompat.isIsolatedUid(1010123));
        assertTrue(ProcessCompat.isIsolatedUid(1099000));
        assertTrue(ProcessCompat.isIsolatedUid(1099999));
    }

    @Test
    public void appProcessIsNotIsolated() {
        ShadowProcess.setUid(10123);
        assertFalse(ProcessCompat.isIsolatedProcess());
    }

    @Test
    public void isolatedProcessIsRecognized() {
        ShadowProcess.setUid(99012);
        assertTrue(ProcessCompat.isIsolatedProcess());
        ShadowProcess.setUid(1099012);
        assertTrue(ProcessCompat.isIsolatedProcess());
    }
}
