// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ProfileCaptureOptionCatalogTest {
    @Test
    public void durationLabelsAreStableAndParseBack() {
        List<String> labels = ProfileCaptureOptionCatalog.durationLabels();

        assertEquals("5s", labels.get(0));
        assertEquals("10s", labels.get(1));
        assertEquals("1m", labels.get(3));
        assertEquals(30, ProfileCaptureOptionCatalog.durationFromLabel("30s", 10));
        assertEquals(60, ProfileCaptureOptionCatalog.durationFromLabel("1m", 10));
    }

    @Test
    public void invalidDurationLabelFallsBackThroughClamp() {
        assertEquals(10, ProfileCaptureOptionCatalog.durationFromLabel("custom", 10));
        assertEquals(CpuProfileCommandBuilder.DEFAULT_DURATION_SECONDS,
                ProfileCaptureOptionCatalog.durationFromLabel(null, -99));
    }

    @Test
    public void cpuEventsUsePrimaryAbiAndBuilderAllowList() {
        List<String> events = ProfileCaptureOptionCatalog.cpuEventsForDevice(
                34, new String[]{"arm64-v8a"});

        assertTrue(events.contains("cpu-cycles"));
        assertTrue(events.contains("task-clock"));
        assertTrue(events.contains("stalled-cycles-frontend"));
        for (String event : events) {
            assertTrue(CpuProfileCommandBuilder.allowedEvents().contains(event));
        }
    }

    @Test
    public void cpuEventsHidePmuEventsForUnknownAbi() {
        List<String> events = ProfileCaptureOptionCatalog.cpuEventsForDevice(
                34, new String[]{"riscv64"});

        assertTrue(events.contains("cpu-clock"));
        assertFalse(events.contains("cpu-cycles"));
    }

    @Test
    public void eventLabelFallsBackToFirstAvailableEvent() {
        List<String> events = ProfileCaptureOptionCatalog.cpuEventsForDevice(
                34, new String[]{"arm64-v8a"});

        assertEquals(events.get(0), ProfileCaptureOptionCatalog.eventFromLabel("not-real", events));
        assertEquals("task-clock", ProfileCaptureOptionCatalog.eventFromLabel("task-clock", events));
    }
}
