// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GfxInfoParserTest {

    private static final String FULL_DUMP = ""
            + "Applications Graphics Acceleration Info:\n"
            + "Uptime: 12345\n\n"
            + "** Graphics info for pid 1234 [com.example.app] **\n\n"
            + "Stats since: 1234567890ns\n"
            + "Total frames rendered: 4321\n"
            + "Janky frames: 87 (2.01%)\n"
            + "Janky frames (legacy): 79 (1.83%)\n"
            + "50th percentile: 4ms\n"
            + "90th percentile: 11ms\n"
            + "95th percentile: 17ms\n"
            + "99th percentile: 38ms\n"
            + "Number Missed Vsync: 12\n"
            + "Number High input latency: 0\n"
            + "Number Slow UI thread: 18\n"
            + "Number Slow bitmap uploads: 1\n"
            + "Number Slow issue draw commands: 7\n"
            + "Number Frame deadline missed: 22\n";

    @Test
    public void parsesEveryDocumentedField() {
        GfxInfoParser.Snapshot s = GfxInfoParser.parse(FULL_DUMP);
        assertNotNull(s);
        assertEquals(4321L, s.totalFrames);
        assertEquals(87L, s.jankyFrames);
        assertEquals(2.01, s.jankyFramesPercent, 0.001);
        assertEquals(4L, s.p50LatencyMs);
        assertEquals(11L, s.p90LatencyMs);
        assertEquals(17L, s.p95LatencyMs);
        assertEquals(38L, s.p99LatencyMs);
        assertEquals(12L, s.missedVsync);
        assertEquals(0L, s.highInputLatency);
        assertEquals(18L, s.slowUiThread);
        assertEquals(1L, s.slowBitmapUploads);
        assertEquals(7L, s.slowIssueDrawCommands);
        assertEquals(22L, s.frameDeadlineMissed);
    }

    @Test
    public void returnsNullOnUnrelatedDumpsys() {
        // Looks like a meminfo dump, not gfxinfo. Should be rejected by the
        // heuristic so we never publish bogus zeros to the UI.
        String dump = "Applications Memory Usage (in Kilobytes):\nUptime: 12345\n";
        assertNull(GfxInfoParser.parse(dump));
    }

    @Test
    public void returnsNullOnEmptyInput() {
        assertNull(GfxInfoParser.parse(""));
    }

    @Test
    public void partialDumpKeepsMissingFieldsAtSentinel() {
        // Dumpsys on an app with no frames produces percentile lines but no
        // breakdown counters. Missing fields stay at -1 so the UI shows n/a.
        String dump = ""
                + "Stats since: 0ns\n"
                + "Total frames rendered: 0\n"
                + "50th percentile: 0ms\n"
                + "90th percentile: 0ms\n";
        GfxInfoParser.Snapshot s = GfxInfoParser.parse(dump);
        assertNotNull(s);
        assertEquals(0L, s.totalFrames);
        assertEquals(0L, s.p50LatencyMs);
        assertEquals(0L, s.p90LatencyMs);
        // Missing fields keep the -1 sentinel.
        assertEquals(-1L, s.p95LatencyMs);
        assertEquals(-1L, s.p99LatencyMs);
        assertEquals(-1L, s.jankyFrames);
        assertEquals(-1.0, s.jankyFramesPercent, 0.0001);
        assertEquals(-1L, s.missedVsync);
    }

    @Test
    public void garbledNumbersAreSkippedNotCrashing() {
        String dump = ""
                + "Total frames rendered: ????\n"
                + "Janky frames: 5 (4.2%)\n"
                + "50th percentile: 7ms\n";
        GfxInfoParser.Snapshot s = GfxInfoParser.parse(dump);
        assertNotNull(s);
        assertEquals(-1L, s.totalFrames);
        assertEquals(5L, s.jankyFrames);
        assertEquals(4.2, s.jankyFramesPercent, 0.001);
        assertEquals(7L, s.p50LatencyMs);
    }

    @Test
    public void legacyJankyFramesRowDoesNotShadowModernRow() {
        // The dump contains BOTH "Janky frames:" and "Janky frames (legacy):".
        // The regex must lock to the modern row, not the legacy row.
        GfxInfoParser.Snapshot s = GfxInfoParser.parse(FULL_DUMP);
        assertNotNull(s);
        assertEquals(87L, s.jankyFrames);
        assertEquals(2.01, s.jankyFramesPercent, 0.001);
    }

    @Test
    public void multiplePercentileSectionsHonorLastWriteWins() {
        // Some OEM builds emit nested per-window gfxinfo blocks. The parser's
        // loop captures the last seen value for each percentile so the
        // app-level summary (typically the final block) wins.
        String dump = ""
                + "Total frames rendered: 1000\n"
                + "50th percentile: 99ms\n"  // first / inner block
                + "50th percentile: 4ms\n"  // outer / app summary
                + "90th percentile: 11ms\n";
        GfxInfoParser.Snapshot s = GfxInfoParser.parse(dump);
        assertNotNull(s);
        assertEquals(4L, s.p50LatencyMs);
        assertTrue(s.p50LatencyMs != 99L);
    }
}
