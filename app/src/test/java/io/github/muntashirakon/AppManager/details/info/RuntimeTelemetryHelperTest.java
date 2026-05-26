// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class RuntimeTelemetryHelperTest {

    @Test
    public void zeroAndNegativeDurationsRenderAsZeroSeconds() {
        assertEquals("0s", RuntimeTelemetryHelper.formatDuration(0));
        assertEquals("0s", RuntimeTelemetryHelper.formatDuration(-1234));
    }

    @Test
    public void subMinuteDurationsRenderAsSecondsOnly() {
        assertEquals("5s", RuntimeTelemetryHelper.formatDuration(5_000L));
        assertEquals("59s", RuntimeTelemetryHelper.formatDuration(59_999L));
    }

    @Test
    public void subHourDurationsRenderAsMinutesAndSeconds() {
        long fiveMinTwoSec = TimeUnit.MINUTES.toMillis(5) + TimeUnit.SECONDS.toMillis(2);
        assertEquals("5m 2s", RuntimeTelemetryHelper.formatDuration(fiveMinTwoSec));
    }

    @Test
    public void multiHourDurationsIncludeHoursMinutesSeconds() {
        long twoHrThreeMinFourSec = TimeUnit.HOURS.toMillis(2)
                + TimeUnit.MINUTES.toMillis(3)
                + TimeUnit.SECONDS.toMillis(4);
        assertEquals("2h 3m 4s", RuntimeTelemetryHelper.formatDuration(twoHrThreeMinFourSec));
    }

    @Test
    public void snapshotPreservesFieldValues() {
        RuntimeTelemetryHelper.Snapshot snap = new RuntimeTelemetryHelper.Snapshot(
                /* screenTime */ 10_000L,
                /* lastUsage */ 20_000L,
                /* timesOpened */ 7,
                /* mobileTx */ 100L,
                /* mobileRx */ 200L,
                /* wifiTx */ 300L,
                /* wifiRx */ 400L);
        assertEquals(10_000L, snap.screenTimeMillis);
        assertEquals(7, snap.timesOpened);
        assertEquals(100L, snap.mobileTxBytes);
        assertEquals(400L, snap.wifiRxBytes);
        assertTrue(snap.lastUsageTime > 0);
    }
}
