// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.pm.PackageInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

// Robolectric for ApplicationItem, a PackageItemInfo.
@RunWith(RobolectricTestRunner.class)
public class AdSourceSignalsTest {
    private static final String FULL_SCREEN = "android.permission.USE_FULL_SCREEN_INTENT";
    private static final int GRANTED = PackageInfo.REQUESTED_PERMISSION_GRANTED;

    @Test
    public void theUsersOverlaySwitchDecides() {
        String[] requested = {Manifest.permission.SYSTEM_ALERT_WINDOW};

        assertEquals(AdSourceSignals.OVERLAY,
                AdSourceSignals.compute(requested, new int[]{0}, AppOpsManager.MODE_ALLOWED));
        assertEquals(0, AdSourceSignals.compute(requested, new int[]{GRANTED}, AppOpsManager.MODE_IGNORED));
        assertEquals(0, AdSourceSignals.compute(requested, new int[]{GRANTED}, AppOpsManager.MODE_ERRORED));
    }

    @Test
    public void leftAtItsDefaultTheGrantDecides() {
        String[] requested = {Manifest.permission.SYSTEM_ALERT_WINDOW};

        assertEquals(AdSourceSignals.OVERLAY,
                AdSourceSignals.compute(requested, new int[]{GRANTED}, AppOpsManager.MODE_DEFAULT));
        assertEquals(0, AdSourceSignals.compute(requested, new int[]{0}, AppOpsManager.MODE_DEFAULT));
        assertEquals(AdSourceSignals.OVERLAY,
                AdSourceSignals.compute(requested, new int[]{GRANTED}, AdSourceSignals.MODE_UNKNOWN));
        assertEquals(0, AdSourceSignals.compute(requested, null, AdSourceSignals.MODE_UNKNOWN));
    }

    @Test
    public void startingAtBootNeedsNoGrantButFullScreenDoes() {
        String[] requested = {Manifest.permission.RECEIVE_BOOT_COMPLETED, FULL_SCREEN};

        assertEquals(AdSourceSignals.BOOT, AdSourceSignals.compute(requested, new int[]{0, 0},
                AdSourceSignals.MODE_UNKNOWN));
        assertEquals(AdSourceSignals.BOOT | AdSourceSignals.FULL_SCREEN, AdSourceSignals.compute(requested,
                new int[]{0, GRANTED}, AdSourceSignals.MODE_UNKNOWN));
    }

    @Test
    public void nothingRequestedIsNothing() {
        assertEquals(0, AdSourceSignals.compute(null, null, AppOpsManager.MODE_ALLOWED));
        assertEquals(0, AdSourceSignals.compute(new String[]{Manifest.permission.INTERNET}, new int[]{GRANTED},
                AppOpsManager.MODE_ALLOWED));
    }

    @Test
    public void aShortFlagsArrayReadsAsNotGranted() {
        String[] requested = {Manifest.permission.INTERNET, FULL_SCREEN};

        assertEquals(0, AdSourceSignals.compute(requested, new int[]{GRANTED}, AdSourceSignals.MODE_UNKNOWN));
    }

    @Test
    public void itTakesAnOverlayAndAWayToComeUpUnopened() {
        assertTrue(AdSourceSignals.isPossibleAdSource(AdSourceSignals.OVERLAY | AdSourceSignals.BOOT, false));
        assertTrue(AdSourceSignals.isPossibleAdSource(AdSourceSignals.OVERLAY | AdSourceSignals.FULL_SCREEN,
                false));
        assertFalse(AdSourceSignals.isPossibleAdSource(AdSourceSignals.OVERLAY, false));
        assertFalse(AdSourceSignals.isPossibleAdSource(AdSourceSignals.BOOT | AdSourceSignals.FULL_SCREEN, false));
    }

    @Test
    public void systemAppsAreLeftOut() {
        int all = AdSourceSignals.OVERLAY | AdSourceSignals.BOOT | AdSourceSignals.FULL_SCREEN;

        assertFalse(AdSourceSignals.isPossibleAdSource(all, true));
    }

    @Test
    public void anItemWithNoSignalsYetIsNotFlagged() {
        ApplicationItem item = new ApplicationItem();
        item.adSourceSignals = null;

        assertFalse(AdSourceSignals.isPossibleAdSource(item));

        item.adSourceSignals = AdSourceSignals.OVERLAY | AdSourceSignals.BOOT;
        assertTrue(AdSourceSignals.isPossibleAdSource(item));
    }
}
