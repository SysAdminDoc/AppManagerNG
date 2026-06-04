// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * App Details glue for the T20-C memory-allocations inspector. Runs the four
 * privileged capture commands ({@code dumpsys meminfo}, {@code dumpsys gfxinfo},
 * {@code /proc/&lt;pid&gt;/status}, {@code /proc/&lt;pid&gt;/maps}), feeds the raw
 * output through the JVM-tested parsers, and unifies them with
 * {@link MemorySnapshotComposer}.
 *
 * <p>The capture half ({@link #load}) needs a privileged runner (root / Shizuku
 * / ADB); the format half ({@link #format}) is pure and JVM-testable against a
 * composed snapshot.
 */
public final class AppMemorySnapshotLoader {

    private AppMemorySnapshotLoader() {
    }

    /**
     * Capture and compose a point-in-time memory snapshot for {@code packageName}.
     * Each command failure degrades gracefully — the corresponding parser sees an
     * empty string and the composer leaves those fields unavailable. When the
     * package has no running process the {@code /proc} sources are skipped.
     */
    @WorkerThread
    @NonNull
    public static MemorySnapshotComposer.AppMemorySnapshot load(@NonNull String packageName) {
        String meminfoRaw = runQuietly(new String[]{"dumpsys", "meminfo", packageName});
        String gfxinfoRaw = runQuietly(new String[]{"dumpsys", "gfxinfo", packageName});
        AppMemoryInfoParser.Snapshot meminfo = meminfoRaw != null
                ? AppMemoryInfoParser.parseAppSummary(meminfoRaw) : null;
        GfxInfoParser.Snapshot gfxinfo = gfxinfoRaw != null
                ? GfxInfoParser.parse(gfxinfoRaw) : null;

        ProcStatusParser.Snapshot status = null;
        ProcMapsSummary.Summary maps = null;
        String pid = firstPid(runQuietly(new String[]{"pidof", packageName}));
        if (pid != null && !ThreadUtils.isInterrupted()) {
            String statusRaw = runQuietly(new String[]{"cat", "/proc/" + pid + "/status"});
            if (statusRaw != null) {
                status = ProcStatusParser.parse(statusRaw);
            }
            String mapsRaw = runQuietly(new String[]{"cat", "/proc/" + pid + "/maps"});
            if (mapsRaw != null) {
                maps = ProcMapsSummary.parse(mapsRaw);
            }
        }
        return MemorySnapshotComposer.compose(meminfo, gfxinfo, status, maps);
    }

    @Nullable
    private static String runQuietly(@NonNull String[] command) {
        try {
            Runner.Result result = Runner.runCommand(command);
            if (result == null) return null;
            String output = result.getOutput();
            return (output == null || output.isEmpty()) ? null : output;
        } catch (Throwable th) {
            return null;
        }
    }

    /**
     * First PID token from {@code pidof} output (a process may have several;
     * the lowest-listed one is the parent for a single-process app).
     */
    @VisibleForTesting
    @Nullable
    static String firstPid(@Nullable String pidofOutput) {
        if (pidofOutput == null) return null;
        String trimmed = pidofOutput.trim();
        if (trimmed.isEmpty()) return null;
        String first = trimmed.split("\\s+")[0];
        for (int i = 0; i < first.length(); ++i) {
            if (first.charAt(i) < '0' || first.charAt(i) > '9') return null;
        }
        return first.isEmpty() ? null : first;
    }

    /**
     * Render a composed snapshot to a human-readable multi-line block for the
     * App Details memory dialog. Provenance ("via /proc/maps", "via dumpsys")
     * is appended where the underlying number type differs (PSS vs RSS vs
     * virtual) so the value is never silently misread. Pure function.
     */
    @NonNull
    public static CharSequence format(@NonNull Context context,
                                      @NonNull MemorySnapshotComposer.AppMemorySnapshot s) {
        if (s.isEmpty()) {
            return context.getString(R.string.memory_snapshot_unavailable);
        }
        StringBuilder sb = new StringBuilder();
        line(sb, context.getString(R.string.memory_total_pss), MemoryFormat.formatKb(s.totalPssKb), s.totalPssSource);
        line(sb, context.getString(R.string.memory_total_rss), MemoryFormat.formatKb(s.totalRssKb), s.totalRssSource);
        line(sb, context.getString(R.string.memory_swap), MemoryFormat.formatSwapKb(s.totalSwapKb), s.totalSwapSource);
        line(sb, context.getString(R.string.memory_java_heap), MemoryFormat.formatKb(s.javaPssKb), s.javaPssSource);
        line(sb, context.getString(R.string.memory_native_heap), MemoryFormat.formatKb(s.nativePssKb), s.nativePssSource);
        line(sb, context.getString(R.string.memory_code), MemoryFormat.formatKb(s.codePssKb), s.codePssSource);
        line(sb, context.getString(R.string.memory_stack), MemoryFormat.formatKb(s.stackPssKb), s.stackPssSource);
        line(sb, context.getString(R.string.memory_graphics), MemoryFormat.formatKb(s.graphicsPssKb), s.graphicsPssSource);
        line(sb, context.getString(R.string.memory_threads), MemoryFormat.formatThreadCount(s.threadCount), s.threadCountSource);
        if (s.frameStatsSource != MemorySnapshotComposer.FieldSource.UNAVAILABLE) {
            line(sb, context.getString(R.string.memory_jank), MemoryFormat.formatPercent(s.jankyFramesPercent), s.frameStatsSource);
            line(sb, context.getString(R.string.memory_frame_p90), MemoryFormat.formatLatencyMs(s.p90LatencyNs), s.frameStatsSource);
            line(sb, context.getString(R.string.memory_frame_p99), MemoryFormat.formatLatencyMs(s.p99LatencyNs), s.frameStatsSource);
        }
        if (s.regionSource != MemorySnapshotComposer.FieldSource.UNAVAILABLE) {
            line(sb, context.getString(R.string.memory_region_dalvik), MemoryFormat.formatBytes(s.dalvikRegionBytes), s.regionSource);
            line(sb, context.getString(R.string.memory_region_native), MemoryFormat.formatBytes(s.nativeRegionBytes), s.regionSource);
            line(sb, context.getString(R.string.memory_region_library), MemoryFormat.formatBytes(s.libraryRegionBytes), s.regionSource);
            String chart = regionChart(context, s);
            if (!chart.isEmpty()) {
                sb.append('\n').append(context.getString(R.string.memory_region_chart)).append('\n')
                        .append(chart).append('\n');
            }
        }
        if (s.truncated) {
            sb.append('\n').append(context.getString(R.string.memory_snapshot_truncated));
        }
        return sb.toString().trim();
    }

    @NonNull
    private static String regionChart(@NonNull Context context,
                                      @NonNull MemorySnapshotComposer.AppMemorySnapshot s) {
        List<MemoryRegionChart.Segment> segments = new ArrayList<>(5);
        segments.add(new MemoryRegionChart.Segment(context.getString(R.string.memory_region_dalvik), s.dalvikRegionBytes));
        segments.add(new MemoryRegionChart.Segment(context.getString(R.string.memory_region_native), s.nativeRegionBytes));
        segments.add(new MemoryRegionChart.Segment(context.getString(R.string.memory_region_stack), s.stackRegionBytes));
        segments.add(new MemoryRegionChart.Segment(context.getString(R.string.memory_region_code), s.codeRegionBytes));
        segments.add(new MemoryRegionChart.Segment(context.getString(R.string.memory_region_library), s.libraryRegionBytes));
        return MemoryRegionChart.render(segments, 24);
    }

    private static void line(@NonNull StringBuilder sb, @NonNull String label,
                             @NonNull String value, @NonNull MemorySnapshotComposer.FieldSource source) {
        if (source == MemorySnapshotComposer.FieldSource.UNAVAILABLE) {
            return;
        }
        sb.append(label).append(": ").append(value);
        String provenance = provenanceFor(source);
        if (provenance != null) {
            sb.append("  (").append(provenance).append(')');
        }
        sb.append('\n');
    }

    @Nullable
    @VisibleForTesting
    static String provenanceFor(@NonNull MemorySnapshotComposer.FieldSource source) {
        switch (source) {
            case PROC_MAPS:
                return "via /proc/maps · virtual";
            case PROC_STATUS:
                return "via /proc/status";
            case DUMPSYS_GFXINFO:
                return "via gfxinfo";
            case DUMPSYS_MEMINFO:
            case UNAVAILABLE:
            default:
                return null;
        }
    }
}
