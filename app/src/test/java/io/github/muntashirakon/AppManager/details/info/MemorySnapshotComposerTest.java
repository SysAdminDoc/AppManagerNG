// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;

public class MemorySnapshotComposerTest {

    private static AppMemoryInfoParser.Snapshot meminfo(long javaPss, long nativePss,
                                                        long totalPss, long totalRss,
                                                        long totalSwap) {
        AppMemoryInfoParser.Snapshot s = new AppMemoryInfoParser.Snapshot();
        s.javaHeapPssKb = javaPss;
        s.nativeHeapPssKb = nativePss;
        s.totalPssKb = totalPss;
        s.totalRssKb = totalRss;
        s.totalSwapKb = totalSwap;
        return s;
    }

    private static ProcStatusParser.Snapshot status(int threads, long rss, long swap) {
        return new ProcStatusParser.Snapshot(
                "com.example", 1234, 1234, 567, threads,
                -1L, -1L, -1L, rss,
                -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, swap);
    }

    private static GfxInfoParser.Snapshot gfx(double jankPct, long p50, long p99) {
        GfxInfoParser.Snapshot s = new GfxInfoParser.Snapshot();
        s.totalFrames = 1000L;
        s.jankyFramesPercent = jankPct;
        s.p50LatencyMs = p50;
        s.p90LatencyMs = -1L;
        s.p95LatencyMs = -1L;
        s.p99LatencyMs = p99;
        return s;
    }

    private static ProcMapsSummary.Summary maps(long dalvik, long nativeHeap,
                                                long stack, long code, long library) {
        return new ProcMapsSummary.Summary(dalvik, nativeHeap, stack, code, library,
                0L, 0L, dalvik + nativeHeap + stack + code + library, 5, 0);
    }

    @Test
    public void allNullsProduceEmptySnapshot() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, null, null, null);
        assertTrue(s.isEmpty());
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.totalPssSource);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.totalRssSource);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.threadCountSource);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.frameStatsSource);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.regionSource);
        assertFalse(s.truncated);
    }

    @Test
    public void meminfoAloneSuppliesPssAndRssTotals() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(800L, 1200L, 9000L, 25000L, 100L), null, null, null);
        assertEquals(800L, s.javaPssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.javaPssSource);
        assertEquals(1200L, s.nativePssKb);
        assertEquals(9000L, s.totalPssKb);
        assertEquals(25000L, s.totalRssKb);
        // status absent, so totalRss comes from meminfo, not proc-status.
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.totalRssSource);
        assertEquals(100L, s.totalSwapKb);
    }

    @Test
    public void procStatusOverridesMeminfoForRss() {
        // When both sources have a value, /proc/status wins for RSS (more accurate).
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(-1L, -1L, -1L, 25000L, -1L),
                null,
                status(42, 28000L, -1L),
                null);
        assertEquals(28000L, s.totalRssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_STATUS, s.totalRssSource);
        assertEquals(42, s.threadCount);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_STATUS, s.threadCountSource);
    }

    @Test
    public void procMapsFallsBackForPssWhenMeminfoMissing() {
        // No meminfo, but /proc/maps gives a virtual rollup. Composer uses
        // it for the per-region PSS-like fields and tags the source as
        // PROC_MAPS so the UI can label it accurately.
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, null, null,
                maps(8L * 1024L * 1024L, 4L * 1024L * 1024L,
                        128L * 1024L, 16L * 1024L * 1024L, 32L * 1024L * 1024L));
        // 8 MiB / 1024 = 8192 KB
        assertEquals(8 * 1024L, s.javaPssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.javaPssSource);
        assertEquals(4 * 1024L, s.nativePssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.nativePssSource);
        assertEquals(128L, s.stackPssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.stackPssSource);
        // Code + library are summed for the code bucket fallback.
        assertEquals((16 + 32) * 1024L, s.codePssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.codePssSource);
    }

    @Test
    public void meminfoWinsOverProcMapsForPssWhenBothPresent() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(800L, 1200L, -1L, -1L, -1L),
                null, null,
                maps(99L * 1024L * 1024L, 88L * 1024L * 1024L, 0L, 0L, 0L));
        // meminfo's Java/Native PSS wins.
        assertEquals(800L, s.javaPssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.javaPssSource);
        assertEquals(1200L, s.nativePssKb);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.nativePssSource);
    }

    @Test
    public void gfxinfoSuppliesJankAndPercentiles() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, gfx(4.5, 12L, 88L), null, null);
        assertEquals(4.5, s.jankyFramesPercent, 1e-6);
        assertEquals(12L, s.p50LatencyNs);
        assertEquals(88L, s.p99LatencyNs);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_GFXINFO, s.frameStatsSource);
    }

    @Test
    public void absentGfxinfoLeavesFrameStatsUnknown() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(1L, 1L, 1L, 1L, 1L), null, null, null);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.frameStatsSource);
        assertTrue(s.jankyFramesPercent < 0.0);
    }

    @Test
    public void procMapsRegionsFlowThroughEvenWithoutMeminfo() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, null, null,
                maps(1000L, 2000L, 500L, 4000L, 8000L));
        assertEquals(1000L, s.dalvikRegionBytes);
        assertEquals(2000L, s.nativeRegionBytes);
        assertEquals(500L, s.stackRegionBytes);
        assertEquals(4000L, s.codeRegionBytes);
        assertEquals(8000L, s.libraryRegionBytes);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.regionSource);
        assertFalse(s.truncated);
    }

    @Test
    public void truncatedFlagPropagatesFromMapsUnparsedRegions() {
        ProcMapsSummary.Summary partial = new ProcMapsSummary.Summary(
                1000L, 0L, 0L, 0L, 0L, 0L, 0L, 1000L, 1, 3);
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, null, null, partial);
        assertTrue(s.truncated);
    }

    @Test
    public void emptyMapsSummaryDoesNotMarkRegionSource() {
        ProcMapsSummary.Summary empty = new ProcMapsSummary.Summary(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0);
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                null, null, null, empty);
        assertEquals(MemorySnapshotComposer.FieldSource.UNAVAILABLE, s.regionSource);
    }

    @Test
    public void swapFallsBackToProcStatusWhenMeminfoSilent() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(-1L, -1L, -1L, -1L, -1L),
                null,
                status(8, -1L, 4096L),
                null);
        assertEquals(4096L, s.totalSwapKb);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_STATUS, s.totalSwapSource);
    }

    @Test
    public void fullComposeUsesEveryAvailableSource() {
        MemorySnapshotComposer.AppMemorySnapshot s = MemorySnapshotComposer.compose(
                meminfo(800L, 1200L, 9000L, 25000L, 100L),
                gfx(1.2, 9L, 33L),
                status(42, 28000L, 200L),
                maps(8L << 20, 4L << 20, 128L << 10, 16L << 20, 32L << 20));
        // Each source claims its expected field.
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.javaPssSource);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.totalPssSource);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_STATUS, s.totalRssSource);
        // SWAP: meminfo wins because its value is present.
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO, s.totalSwapSource);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_STATUS, s.threadCountSource);
        assertEquals(MemorySnapshotComposer.FieldSource.DUMPSYS_GFXINFO, s.frameStatsSource);
        assertEquals(MemorySnapshotComposer.FieldSource.PROC_MAPS, s.regionSource);
        assertFalse(s.isEmpty());
        // Sanity: as long as a Map of accessors round-trips, the snapshot is usable.
        LinkedHashMap<String, Object> roundTrip = new LinkedHashMap<>();
        roundTrip.put("totalPss", s.totalPssKb);
        roundTrip.put("totalRss", s.totalRssKb);
        roundTrip.put("threads", s.threadCount);
        assertEquals(9000L, roundTrip.get("totalPss"));
        assertEquals(28000L, roundTrip.get("totalRss"));
        assertEquals(42, roundTrip.get("threads"));
    }
}
