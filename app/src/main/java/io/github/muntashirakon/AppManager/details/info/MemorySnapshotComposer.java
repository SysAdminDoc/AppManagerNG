// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Combines the four T20-C memory-allocations parsers into a single
 * unified snapshot the App Details memory panel can render.
 *
 * <p>The four data sources observe the same process from different
 * angles, with overlapping but not identical coverage:
 * <ul>
 *   <li>{@link AppMemoryInfoParser} - {@code dumpsys meminfo} App Summary
 *       block. Best coverage of PSS-style numbers (Java/Native/Code/
 *       Stack/Graphics/Private Other/System) plus totals.</li>
 *   <li>{@link GfxInfoParser} - {@code dumpsys gfxinfo} jank and frame
 *       latency. Frame stats only; no byte counts.</li>
 *   <li>{@link ProcStatusParser} - {@code /proc/<pid>/status}. Best for
 *       Vm* / Rss* aggregates and a thread count; survives when dumpsys
 *       is truncated by system_server.</li>
 *   <li>{@link ProcMapsSummary} - {@code /proc/<pid>/maps} per-region
 *       rollup. The only source that tells us per-mapping virtual size
 *       (dalvik / native-heap / stack / code / library / other).</li>
 * </ul>
 *
 * <p>The composer picks the best available field from each source:
 * <ul>
 *   <li>Java + Native + Stack PSS come from {@code AppMemoryInfoParser}
 *       (the canonical source); if absent, fall back to the
 *       per-region rollup from {@code ProcMapsSummary} (note: that
 *       reports <em>virtual</em> bytes, not PSS).</li>
 *   <li>Total RSS comes from {@code ProcStatusParser.vmRssKb} if known,
 *       otherwise from {@code AppMemoryInfoParser.totalRssKb}.</li>
 *   <li>Thread count comes from {@code ProcStatusParser.threads}.</li>
 *   <li>Frame stats come from {@code GfxInfoParser}.</li>
 *   <li>Per-mapping byte counts come from {@code ProcMapsSummary}.</li>
 * </ul>
 *
 * <p>Each field on {@link AppMemorySnapshot} preserves a source-of-truth
 * marker so the UI can show "via /proc/maps" or "via dumpsys" next to
 * values where the difference matters (PSS vs RSS vs virtual). The
 * composer is JVM-only and uses no Android API.
 */
public final class MemorySnapshotComposer {

    public enum FieldSource {
        UNAVAILABLE,
        DUMPSYS_MEMINFO,
        DUMPSYS_GFXINFO,
        PROC_STATUS,
        PROC_MAPS
    }

    public static final class AppMemorySnapshot {
        // PSS-style numbers (kilobytes) and where each came from.
        public final long javaPssKb;
        public final FieldSource javaPssSource;
        public final long nativePssKb;
        public final FieldSource nativePssSource;
        public final long codePssKb;
        public final FieldSource codePssSource;
        public final long stackPssKb;
        public final FieldSource stackPssSource;
        public final long graphicsPssKb;
        public final FieldSource graphicsPssSource;
        public final long totalPssKb;
        public final FieldSource totalPssSource;
        public final long totalRssKb;
        public final FieldSource totalRssSource;
        public final long totalSwapKb;
        public final FieldSource totalSwapSource;
        public final int threadCount;
        public final FieldSource threadCountSource;
        // Jank/frame stats.
        public final double jankyFramesPercent;
        public final long p50LatencyNs;
        public final long p90LatencyNs;
        public final long p95LatencyNs;
        public final long p99LatencyNs;
        public final FieldSource frameStatsSource;
        // Per-region virtual byte counts from /proc/maps.
        public final long dalvikRegionBytes;
        public final long nativeRegionBytes;
        public final long stackRegionBytes;
        public final long codeRegionBytes;
        public final long libraryRegionBytes;
        public final FieldSource regionSource;
        public final boolean truncated;

        AppMemorySnapshot(long javaPssKb, FieldSource javaPssSource,
                          long nativePssKb, FieldSource nativePssSource,
                          long codePssKb, FieldSource codePssSource,
                          long stackPssKb, FieldSource stackPssSource,
                          long graphicsPssKb, FieldSource graphicsPssSource,
                          long totalPssKb, FieldSource totalPssSource,
                          long totalRssKb, FieldSource totalRssSource,
                          long totalSwapKb, FieldSource totalSwapSource,
                          int threadCount, FieldSource threadCountSource,
                          double jankyFramesPercent, long p50LatencyNs, long p90LatencyNs,
                          long p95LatencyNs, long p99LatencyNs, FieldSource frameStatsSource,
                          long dalvikRegionBytes, long nativeRegionBytes,
                          long stackRegionBytes, long codeRegionBytes,
                          long libraryRegionBytes, FieldSource regionSource,
                          boolean truncated) {
            this.javaPssKb = javaPssKb;
            this.javaPssSource = javaPssSource;
            this.nativePssKb = nativePssKb;
            this.nativePssSource = nativePssSource;
            this.codePssKb = codePssKb;
            this.codePssSource = codePssSource;
            this.stackPssKb = stackPssKb;
            this.stackPssSource = stackPssSource;
            this.graphicsPssKb = graphicsPssKb;
            this.graphicsPssSource = graphicsPssSource;
            this.totalPssKb = totalPssKb;
            this.totalPssSource = totalPssSource;
            this.totalRssKb = totalRssKb;
            this.totalRssSource = totalRssSource;
            this.totalSwapKb = totalSwapKb;
            this.totalSwapSource = totalSwapSource;
            this.threadCount = threadCount;
            this.threadCountSource = threadCountSource;
            this.jankyFramesPercent = jankyFramesPercent;
            this.p50LatencyNs = p50LatencyNs;
            this.p90LatencyNs = p90LatencyNs;
            this.p95LatencyNs = p95LatencyNs;
            this.p99LatencyNs = p99LatencyNs;
            this.frameStatsSource = frameStatsSource;
            this.dalvikRegionBytes = dalvikRegionBytes;
            this.nativeRegionBytes = nativeRegionBytes;
            this.stackRegionBytes = stackRegionBytes;
            this.codeRegionBytes = codeRegionBytes;
            this.libraryRegionBytes = libraryRegionBytes;
            this.regionSource = regionSource;
            this.truncated = truncated;
        }

        /** True when every numeric field is at its missing-value sentinel. */
        public boolean isEmpty() {
            return totalPssSource == FieldSource.UNAVAILABLE
                    && totalRssSource == FieldSource.UNAVAILABLE
                    && threadCountSource == FieldSource.UNAVAILABLE
                    && frameStatsSource == FieldSource.UNAVAILABLE
                    && regionSource == FieldSource.UNAVAILABLE;
        }
    }

    private MemorySnapshotComposer() {
    }

    /**
     * Compose an aggregate snapshot from the four sources. Any source may
     * be {@code null} when its capture was unavailable; the composer just
     * leaves the corresponding fields at their {@code -1L} / 0 sentinel
     * and tags them {@link FieldSource#UNAVAILABLE}.
     */
    @NonNull
    public static AppMemorySnapshot compose(@Nullable AppMemoryInfoParser.Snapshot meminfo,
                                            @Nullable GfxInfoParser.Snapshot gfxinfo,
                                            @Nullable ProcStatusParser.Snapshot status,
                                            @Nullable ProcMapsSummary.Summary maps) {
        // PSS-style values: prefer meminfo, otherwise fall back to /proc/maps virtual size.
        long javaPss = -1L;
        FieldSource javaPssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.javaHeapPssKb >= 0L) {
            javaPss = meminfo.javaHeapPssKb;
            javaPssSrc = FieldSource.DUMPSYS_MEMINFO;
        } else if (maps != null && maps.dalvikHeapBytes > 0L) {
            javaPss = bytesToKb(maps.dalvikHeapBytes);
            javaPssSrc = FieldSource.PROC_MAPS;
        }

        long nativePss = -1L;
        FieldSource nativePssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.nativeHeapPssKb >= 0L) {
            nativePss = meminfo.nativeHeapPssKb;
            nativePssSrc = FieldSource.DUMPSYS_MEMINFO;
        } else if (maps != null && maps.nativeHeapBytes > 0L) {
            nativePss = bytesToKb(maps.nativeHeapBytes);
            nativePssSrc = FieldSource.PROC_MAPS;
        }

        long codePss = -1L;
        FieldSource codePssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.codePssKb >= 0L) {
            codePss = meminfo.codePssKb;
            codePssSrc = FieldSource.DUMPSYS_MEMINFO;
        } else if (maps != null && (maps.codeBytes > 0L || maps.libraryBytes > 0L)) {
            codePss = bytesToKb(maps.codeBytes + maps.libraryBytes);
            codePssSrc = FieldSource.PROC_MAPS;
        }

        long stackPss = -1L;
        FieldSource stackPssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.stackPssKb >= 0L) {
            stackPss = meminfo.stackPssKb;
            stackPssSrc = FieldSource.DUMPSYS_MEMINFO;
        } else if (maps != null && maps.stackBytes > 0L) {
            stackPss = bytesToKb(maps.stackBytes);
            stackPssSrc = FieldSource.PROC_MAPS;
        }

        long graphicsPss = -1L;
        FieldSource graphicsPssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.graphicsPssKb >= 0L) {
            graphicsPss = meminfo.graphicsPssKb;
            graphicsPssSrc = FieldSource.DUMPSYS_MEMINFO;
        }

        long totalPss = -1L;
        FieldSource totalPssSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.totalPssKb >= 0L) {
            totalPss = meminfo.totalPssKb;
            totalPssSrc = FieldSource.DUMPSYS_MEMINFO;
        }

        // RSS: prefer /proc/status (most accurate); fall back to meminfo's TOTAL RSS.
        long totalRss = -1L;
        FieldSource totalRssSrc = FieldSource.UNAVAILABLE;
        if (status != null && status.vmRssKb >= 0L) {
            totalRss = status.vmRssKb;
            totalRssSrc = FieldSource.PROC_STATUS;
        } else if (meminfo != null && meminfo.totalRssKb >= 0L) {
            totalRss = meminfo.totalRssKb;
            totalRssSrc = FieldSource.DUMPSYS_MEMINFO;
        }

        // SWAP: meminfo first, /proc/status as backup.
        long totalSwap = -1L;
        FieldSource totalSwapSrc = FieldSource.UNAVAILABLE;
        if (meminfo != null && meminfo.totalSwapKb >= 0L) {
            totalSwap = meminfo.totalSwapKb;
            totalSwapSrc = FieldSource.DUMPSYS_MEMINFO;
        } else if (status != null && status.vmSwapKb >= 0L) {
            totalSwap = status.vmSwapKb;
            totalSwapSrc = FieldSource.PROC_STATUS;
        }

        // Thread count is /proc/status-only.
        int threads = -1;
        FieldSource threadSrc = FieldSource.UNAVAILABLE;
        if (status != null && status.threads >= 0) {
            threads = status.threads;
            threadSrc = FieldSource.PROC_STATUS;
        }

        // Frame stats are gfxinfo-only.
        double jank = -1.0;
        long p50 = -1L;
        long p90 = -1L;
        long p95 = -1L;
        long p99 = -1L;
        FieldSource frameSrc = FieldSource.UNAVAILABLE;
        if (gfxinfo != null) {
            if (gfxinfo.jankyFramesPercent >= 0.0 || gfxinfo.totalFrames > 0) {
                jank = gfxinfo.jankyFramesPercent;
                p50 = gfxinfo.p50LatencyMs;
                p90 = gfxinfo.p90LatencyMs;
                p95 = gfxinfo.p95LatencyMs;
                p99 = gfxinfo.p99LatencyMs;
                frameSrc = FieldSource.DUMPSYS_GFXINFO;
            }
        }

        // Region byte counts are /proc/maps-only.
        long dalvikRegion = 0L;
        long nativeRegion = 0L;
        long stackRegion = 0L;
        long codeRegion = 0L;
        long libraryRegion = 0L;
        FieldSource regionSrc = FieldSource.UNAVAILABLE;
        if (maps != null && maps.regions > 0) {
            dalvikRegion = maps.dalvikHeapBytes;
            nativeRegion = maps.nativeHeapBytes;
            stackRegion = maps.stackBytes;
            codeRegion = maps.codeBytes;
            libraryRegion = maps.libraryBytes;
            regionSrc = FieldSource.PROC_MAPS;
        }

        boolean truncated = (maps != null && maps.unparsedRegions > 0);

        return new AppMemorySnapshot(javaPss, javaPssSrc,
                nativePss, nativePssSrc,
                codePss, codePssSrc,
                stackPss, stackPssSrc,
                graphicsPss, graphicsPssSrc,
                totalPss, totalPssSrc,
                totalRss, totalRssSrc,
                totalSwap, totalSwapSrc,
                threads, threadSrc,
                jank, p50, p90, p95, p99, frameSrc,
                dalvikRegion, nativeRegion, stackRegion, codeRegion, libraryRegion, regionSrc,
                truncated);
    }

    static long bytesToKb(long bytes) {
        if (bytes <= 0L) return 0L;
        return bytes / 1024L;
    }
}
