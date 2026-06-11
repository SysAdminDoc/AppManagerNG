// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.analytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure, dependency-free aggregation for the device-wide analytics / discovery dashboard (INIT-4).
 *
 * <p>It turns a flat list of per-app datapoints (extracted from the already-loaded app list on the
 * UI side) into installer-source / target-SDK distributions and "unused in N days" counts that a
 * dashboard screen can render and tap through to a filtered main list. Kept free of any Android
 * dependency so the aggregation contract is unit-tested without an emulator; the screen wiring is
 * device-gated UI and tracked separately on the roadmap.
 */
public final class DeviceAnalyticsAggregator {
    public static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private DeviceAnalyticsAggregator() {
    }

    /** Immutable per-app input. Extracted from ApplicationItem on the device side. */
    public static final class AppDatapoint {
        @Nullable
        public final String installerLabel;
        public final int targetSdk;          // 0 = unknown
        public final boolean installed;
        public final long lastUsedMillis;    // 0 = never used / no usage data

        public AppDatapoint(@Nullable String installerLabel, int targetSdk, boolean installed,
                            long lastUsedMillis) {
            this.installerLabel = installerLabel;
            this.targetSdk = targetSdk;
            this.installed = installed;
            this.lastUsedMillis = Math.max(0L, lastUsedMillis);
        }
    }

    /** Aggregated result. Maps are ordered for stable, sensible presentation. */
    public static final class AnalyticsSummary {
        /** Installer source -> app count, ordered by count descending then label. */
        public final LinkedHashMap<String, Integer> installerDistribution;
        /** Target SDK -> app count, ascending by SDK. */
        public final TreeMap<Integer, Integer> targetSdkDistribution;
        public final int unused30;
        public final int unused60;
        public final int unused90;
        /** Installed apps with no usage data (lastUsedMillis == 0) — not counted as "unused". */
        public final int unknownUsage;
        public final int totalApps;
        public final int totalInstalled;

        AnalyticsSummary(LinkedHashMap<String, Integer> installerDistribution,
                         TreeMap<Integer, Integer> targetSdkDistribution,
                         int unused30, int unused60, int unused90, int unknownUsage,
                         int totalApps, int totalInstalled) {
            this.installerDistribution = installerDistribution;
            this.targetSdkDistribution = targetSdkDistribution;
            this.unused30 = unused30;
            this.unused60 = unused60;
            this.unused90 = unused90;
            this.unknownUsage = unknownUsage;
            this.totalApps = totalApps;
            this.totalInstalled = totalInstalled;
        }
    }

    /**
     * Aggregates the datapoints as of {@code nowMillis}. {@code nowMillis} is passed in (not read
     * from the clock) so the "unused in N days" buckets are deterministic and testable.
     */
    @NonNull
    public static AnalyticsSummary summarize(@NonNull List<AppDatapoint> datapoints, long nowMillis,
                                             @NonNull String unknownInstallerLabel) {
        Map<String, Integer> installerCounts = new LinkedHashMap<>();
        TreeMap<Integer, Integer> targetSdkCounts = new TreeMap<>();
        int unused30 = 0, unused60 = 0, unused90 = 0, unknownUsage = 0;
        int totalInstalled = 0;

        for (AppDatapoint dp : datapoints) {
            String installer = (dp.installerLabel == null || dp.installerLabel.trim().isEmpty())
                    ? unknownInstallerLabel : dp.installerLabel;
            increment(installerCounts, installer);
            if (dp.targetSdk > 0) {
                Integer key = dp.targetSdk;
                targetSdkCounts.put(key, targetSdkCounts.getOrDefault(key, 0) + 1);
            }
            if (dp.installed) {
                totalInstalled++;
                if (dp.lastUsedMillis <= 0L) {
                    unknownUsage++;
                } else {
                    long ageDays = (nowMillis - dp.lastUsedMillis) / DAY_MILLIS;
                    if (ageDays >= 30) unused30++;
                    if (ageDays >= 60) unused60++;
                    if (ageDays >= 90) unused90++;
                }
            }
        }

        return new AnalyticsSummary(
                orderByCountDescending(installerCounts),
                targetSdkCounts,
                unused30, unused60, unused90, unknownUsage,
                datapoints.size(), totalInstalled);
    }

    private static void increment(@NonNull Map<String, Integer> map, @NonNull String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    @NonNull
    private static LinkedHashMap<String, Integer> orderByCountDescending(@NonNull Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            ordered.put(e.getKey(), e.getValue());
        }
        return ordered;
    }
}
