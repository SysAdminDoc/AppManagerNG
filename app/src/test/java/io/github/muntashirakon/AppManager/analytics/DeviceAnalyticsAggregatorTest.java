// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.analytics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.analytics.DeviceAnalyticsAggregator.AnalyticsSummary;
import io.github.muntashirakon.AppManager.analytics.DeviceAnalyticsAggregator.AppDatapoint;

public class DeviceAnalyticsAggregatorTest {
    private static final String UNKNOWN = "Unknown source";
    private static final long NOW = 1_000L * DeviceAnalyticsAggregator.DAY_MILLIS; // day 1000

    private static long daysAgo(int days) {
        return NOW - days * DeviceAnalyticsAggregator.DAY_MILLIS;
    }

    @Test
    public void installerDistributionCountsAndOrdersByCountDescending() {
        List<AppDatapoint> data = new ArrayList<>();
        data.add(new AppDatapoint("F-Droid", 34, true, NOW));
        data.add(new AppDatapoint("Play Store", 34, true, NOW));
        data.add(new AppDatapoint("Play Store", 33, true, NOW));
        data.add(new AppDatapoint("Play Store", 33, true, NOW));
        data.add(new AppDatapoint(null, 30, true, NOW)); // unknown installer

        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(data, NOW, UNKNOWN);

        // Play Store (3) first, then F-Droid (1) and Unknown (1) tie -> alphabetical.
        List<String> keys = new ArrayList<>(s.installerDistribution.keySet());
        assertEquals("Play Store", keys.get(0));
        assertEquals(3, (int) s.installerDistribution.get("Play Store"));
        assertEquals(1, (int) s.installerDistribution.get("F-Droid"));
        assertEquals(1, (int) s.installerDistribution.get(UNKNOWN));
    }

    @Test
    public void targetSdkDistributionIsAscendingAndSkipsUnknown() {
        List<AppDatapoint> data = new ArrayList<>();
        data.add(new AppDatapoint("x", 34, true, NOW));
        data.add(new AppDatapoint("x", 30, true, NOW));
        data.add(new AppDatapoint("x", 34, true, NOW));
        data.add(new AppDatapoint("x", 0, true, NOW)); // unknown sdk -> excluded

        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(data, NOW, UNKNOWN);

        List<Integer> sdks = new ArrayList<>(s.targetSdkDistribution.keySet());
        assertEquals(Integer.valueOf(30), sdks.get(0));
        assertEquals(Integer.valueOf(34), sdks.get(1));
        assertEquals(2, (int) s.targetSdkDistribution.get(34));
        assertEquals(2, s.targetSdkDistribution.size());
    }

    @Test
    public void unusedBucketsAreCumulativeAndUseExactDayBoundaries() {
        List<AppDatapoint> data = new ArrayList<>();
        data.add(new AppDatapoint("x", 34, true, daysAgo(95))); // 30,60,90
        data.add(new AppDatapoint("x", 34, true, daysAgo(65))); // 30,60
        data.add(new AppDatapoint("x", 34, true, daysAgo(30))); // 30 (exact boundary)
        data.add(new AppDatapoint("x", 34, true, daysAgo(10))); // none

        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(data, NOW, UNKNOWN);

        assertEquals(3, s.unused30);
        assertEquals(2, s.unused60);
        assertEquals(1, s.unused90);
        assertEquals(0, s.unknownUsage);
    }

    @Test
    public void appsWithoutUsageDataAreUnknownNotUnused() {
        List<AppDatapoint> data = new ArrayList<>();
        data.add(new AppDatapoint("x", 34, true, 0L)); // no usage data
        data.add(new AppDatapoint("x", 34, true, daysAgo(200)));

        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(data, NOW, UNKNOWN);

        assertEquals(1, s.unknownUsage);
        assertEquals(1, s.unused30);
    }

    @Test
    public void uninstalledAppsCountInTotalsButNotUsageBuckets() {
        List<AppDatapoint> data = new ArrayList<>();
        data.add(new AppDatapoint("x", 34, false, 0L));      // uninstalled
        data.add(new AppDatapoint("x", 34, true, daysAgo(200)));

        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(data, NOW, UNKNOWN);

        assertEquals(2, s.totalApps);
        assertEquals(1, s.totalInstalled);
        assertEquals(0, s.unknownUsage);
        assertEquals(1, s.unused30);
    }

    @Test
    public void emptyInputProducesEmptySummary() {
        AnalyticsSummary s = DeviceAnalyticsAggregator.summarize(new ArrayList<>(), NOW, UNKNOWN);
        assertEquals(0, s.totalApps);
        assertEquals(0, s.totalInstalled);
        assertEquals(0, s.installerDistribution.size());
        assertEquals(0, s.targetSdkDistribution.size());
    }
}
