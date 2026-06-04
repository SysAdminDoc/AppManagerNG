// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MemoryRegionChartTest {
    @Test
    public void renderBuildsProportionalBarsAndSizes() {
        String chart = MemoryRegionChart.render(Arrays.asList(
                new MemoryRegionChart.Segment("Dalvik", 3L * 1024L * 1024L),
                new MemoryRegionChart.Segment("Native", 1L * 1024L * 1024L)), 16);

        assertTrue(chart.contains("Dalvik: ############ 3.0 MB"));
        assertTrue(chart.contains("Native: #### 1.0 MB"));
    }

    @Test
    public void renderSkipsEmptySegments() {
        String chart = MemoryRegionChart.render(Arrays.asList(
                new MemoryRegionChart.Segment("Dalvik", 0L),
                new MemoryRegionChart.Segment("Native", -1L),
                new MemoryRegionChart.Segment("Code", 4096L)), 16);

        assertFalse(chart.contains("Dalvik"));
        assertFalse(chart.contains("Native"));
        assertTrue(chart.contains("Code: ################ 4.0 KB"));
    }

    @Test
    public void renderReturnsEmptyWhenNoPositiveBytes() {
        assertTrue(MemoryRegionChart.render(Collections.singletonList(
                new MemoryRegionChart.Segment("Dalvik", 0L)), 16).isEmpty());
    }
}
