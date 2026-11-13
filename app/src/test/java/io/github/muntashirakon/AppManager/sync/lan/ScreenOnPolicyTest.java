// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ScreenOnPolicyTest {
    private static final long START = 1_000_000L;
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

    @Test
    public void untilTransfersFinishHoldsForAsLongAsTheyRun() {
        ScreenOnPolicy policy = new ScreenOnPolicy();

        ScreenOnPolicy.Decision idle = policy.update(LanSyncSettings.SCREEN_ON_UNTIL_DONE, false, START);
        assertFalse("Nothing running, nothing held", idle.keepOn);

        ScreenOnPolicy.Decision sharing = policy.update(LanSyncSettings.SCREEN_ON_UNTIL_DONE, true, START);
        assertTrue(sharing.keepOn);
        assertEquals("No timer to wait for", 0, sharing.recheckAt);
        assertTrue(policy.update(LanSyncSettings.SCREEN_ON_UNTIL_DONE, true, START + 600 * MINUTE).keepOn);

        assertFalse("Released once the transfers end",
                policy.update(LanSyncSettings.SCREEN_ON_UNTIL_DONE, false, START + 601 * MINUTE).keepOn);
    }

    @Test
    public void offNeverHoldsTheScreen() {
        ScreenOnPolicy policy = new ScreenOnPolicy();
        ScreenOnPolicy.Decision decision = policy.update(LanSyncSettings.SCREEN_ON_OFF, true, START);
        assertFalse(decision.keepOn);
        assertEquals(0, decision.recheckAt);
    }

    @Test
    public void aTimeLimitCountsFromWhenTheWorkStarted() {
        ScreenOnPolicy policy = new ScreenOnPolicy();

        ScreenOnPolicy.Decision first = policy.update(LanSyncSettings.SCREEN_ON_10_MINUTES, true, START);
        assertTrue(first.keepOn);
        assertEquals("Ask again when the time is up", START + 10 * MINUTE, first.recheckAt);

        // A download starting while sharing is on doesn't buy more time
        ScreenOnPolicy.Decision later = policy.update(LanSyncSettings.SCREEN_ON_10_MINUTES, true, START + 4 * MINUTE);
        assertTrue(later.keepOn);
        assertEquals(START + 10 * MINUTE, later.recheckAt);

        ScreenOnPolicy.Decision timeUp = policy.update(LanSyncSettings.SCREEN_ON_10_MINUTES, true, START + 10 * MINUTE);
        assertFalse("The screen may go off after ten minutes", timeUp.keepOn);
        assertEquals(0, timeUp.recheckAt);

        // Switching to a longer limit while the work runs keeps the same starting point
        ScreenOnPolicy.Decision longer = policy.update(LanSyncSettings.SCREEN_ON_30_MINUTES, true, START + 10 * MINUTE);
        assertTrue(longer.keepOn);
        assertEquals(START + 30 * MINUTE, longer.recheckAt);

        // Once everything has finished, the next transfer gets the full time again
        policy.update(LanSyncSettings.SCREEN_ON_10_MINUTES, false, START + 40 * MINUTE);
        ScreenOnPolicy.Decision next = policy.update(LanSyncSettings.SCREEN_ON_10_MINUTES, true, START + 50 * MINUTE);
        assertTrue(next.keepOn);
        assertEquals(START + 60 * MINUTE, next.recheckAt);
    }

    @Test
    public void limitsMatchTheMenu() {
        assertEquals(10 * MINUTE, ScreenOnPolicy.limitMillis(LanSyncSettings.SCREEN_ON_10_MINUTES));
        assertEquals(30 * MINUTE, ScreenOnPolicy.limitMillis(LanSyncSettings.SCREEN_ON_30_MINUTES));
        assertEquals(0, ScreenOnPolicy.limitMillis(LanSyncSettings.SCREEN_ON_UNTIL_DONE));
    }
}
