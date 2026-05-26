// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class CpuProfileEventCatalogTest {

    @Test
    public void softwareEventsAlwaysAvailable() {
        Set<String> events = CpuProfileEventCatalog.availableEvents(21, "armeabi-v7a");
        // 7 software events ship on every device.
        for (String e : CpuProfileEventCatalog.eventsFor(
                CpuProfileEventCatalog.Class.SOFTWARE)) {
            assertTrue("missing software event " + e, events.contains(e));
        }
        assertTrue(events.contains("cpu-clock"));
        assertTrue(events.contains("task-clock"));
        assertTrue(events.contains("page-faults"));
    }

    @Test
    public void apiBelowPmuBasicHidesPmuEvents() {
        Set<String> events = CpuProfileEventCatalog.availableEvents(22, "arm64-v8a");
        // API 22 is below MIN_PMU_BASIC_API; no PMU events at all.
        assertFalse(events.contains("cpu-cycles"));
        assertFalse(events.contains("instructions"));
        // Still has the software events.
        assertTrue(events.contains("cpu-clock"));
    }

    @Test
    public void apiAtPmuBasicMinExposesBasicEvents() {
        Set<String> events = CpuProfileEventCatalog.availableEvents(
                CpuProfileEventCatalog.MIN_PMU_BASIC_API, "arm64-v8a");
        assertTrue(events.contains("cpu-cycles"));
        assertTrue(events.contains("instructions"));
        // But NOT advanced events.
        assertFalse(events.contains("stalled-cycles-frontend"));
    }

    @Test
    public void apiAtPmuAdvancedMinExposesAdvancedEvents() {
        Set<String> events = CpuProfileEventCatalog.availableEvents(
                CpuProfileEventCatalog.MIN_PMU_ADVANCED_API, "arm64-v8a");
        assertTrue(events.contains("cpu-cycles"));
        assertTrue(events.contains("stalled-cycles-frontend"));
        assertTrue(events.contains("L1-dcache-loads"));
        assertTrue(events.contains("iTLB-load-misses"));
    }

    @Test
    public void unknownAbiHidesAllPmuEvents() {
        // Some custom build target like "mips64" - no PMU known to us.
        Set<String> events = CpuProfileEventCatalog.availableEvents(34, "mips64");
        assertFalse(events.contains("cpu-cycles"));
        assertFalse(events.contains("stalled-cycles-frontend"));
        assertTrue(events.contains("cpu-clock"));
    }

    @Test
    public void availableEventsIsImmutable() {
        Set<String> events = CpuProfileEventCatalog.availableEvents(34, "arm64-v8a");
        try {
            events.add("hostile-event");
            // If add silently succeeded on this JDK, the returned set is a
            // copy; just assert the canonical set did not grow.
            assertFalse(CpuProfileEventCatalog.availableEvents(34, "arm64-v8a")
                    .contains("hostile-event"));
        } catch (UnsupportedOperationException expected) {
            // Acceptable: returned set is unmodifiable.
        }
    }

    @Test
    public void unavailableOnReportsApiTooLow() {
        List<CpuProfileEventCatalog.UnavailableEvent> unavail =
                CpuProfileEventCatalog.unavailableOn(21, "arm64-v8a");
        // 21 < both PMU mins, so every PMU event lands here.
        assertFalse(unavail.isEmpty());
        boolean sawApiLow = false;
        for (CpuProfileEventCatalog.UnavailableEvent ue : unavail) {
            if (ue.reason == CpuProfileEventCatalog.Reason.API_TOO_LOW) sawApiLow = true;
        }
        assertTrue("expected API_TOO_LOW reason among entries", sawApiLow);
    }

    @Test
    public void unavailableOnReportsNoPmuForAbi() {
        List<CpuProfileEventCatalog.UnavailableEvent> unavail =
                CpuProfileEventCatalog.unavailableOn(34, "riscv64");
        assertFalse(unavail.isEmpty());
        for (CpuProfileEventCatalog.UnavailableEvent ue : unavail) {
            // Every PMU event must be marked NO_PMU_FOR_ABI on a non-PMU ABI.
            assertEquals(CpuProfileEventCatalog.Reason.NO_PMU_FOR_ABI, ue.reason);
            assertTrue(ue.explanation.contains("riscv64"));
        }
    }

    @Test
    public void unavailableOnIsEmptyWhenEveryEventIsAvailable() {
        List<CpuProfileEventCatalog.UnavailableEvent> unavail =
                CpuProfileEventCatalog.unavailableOn(34, "arm64-v8a");
        // API 34 + arm64-v8a -> every PMU event available.
        assertTrue(unavail.isEmpty());
    }

    @Test
    public void hasPmuPredicateIsCorrect() {
        assertTrue(CpuProfileEventCatalog.hasPmu("arm64-v8a"));
        assertTrue(CpuProfileEventCatalog.hasPmu("armeabi-v7a"));
        assertTrue(CpuProfileEventCatalog.hasPmu("x86_64"));
        assertTrue(CpuProfileEventCatalog.hasPmu("x86"));
        assertFalse(CpuProfileEventCatalog.hasPmu("riscv64"));
        assertFalse(CpuProfileEventCatalog.hasPmu("mips64"));
        assertFalse(CpuProfileEventCatalog.hasPmu(""));
    }

    @Test
    public void eventsForReturnsImmutableClassListing() {
        Set<String> software = CpuProfileEventCatalog.eventsFor(
                CpuProfileEventCatalog.Class.SOFTWARE);
        try {
            software.clear();
            assertFalse(software.isEmpty());
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    @Test
    public void eventsForBucketsAreDistinct() {
        Set<String> sw = CpuProfileEventCatalog.eventsFor(
                CpuProfileEventCatalog.Class.SOFTWARE);
        Set<String> basic = CpuProfileEventCatalog.eventsFor(
                CpuProfileEventCatalog.Class.PMU_BASIC);
        Set<String> adv = CpuProfileEventCatalog.eventsFor(
                CpuProfileEventCatalog.Class.PMU_ADVANCED);
        for (String s : sw) {
            assertFalse("software event " + s + " also in PMU_BASIC", basic.contains(s));
            assertFalse("software event " + s + " also in PMU_ADVANCED", adv.contains(s));
        }
        for (String s : basic) {
            assertFalse("basic event " + s + " also in PMU_ADVANCED", adv.contains(s));
        }
    }
}
