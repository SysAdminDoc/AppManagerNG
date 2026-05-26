// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-function builder for the Perfetto text-proto trace config used by the
 * T20-A "Export Perfetto trace" action.
 *
 * <p>App-targeted Perfetto traces require three blocks at minimum: a
 * {@code buffers} block to allocate the in-memory ring, a
 * {@code linux.ftrace} data source restricted to the target via
 * {@code atrace_apps}, and a {@code linux.process_stats} data source so the
 * trace UI can resolve threads back to their owning process. The builder
 * produces exactly that shape in text-proto form.
 *
 * <p>Hardening rules:
 * <ul>
 *   <li>Package name is validated by the shared predicate.</li>
 *   <li>Duration is clamped to {@code [MIN_DURATION_MS, MAX_DURATION_MS]}.</li>
 *   <li>Buffer size and ftrace events come from compile-time constants -
 *       no caller-supplied data flows into the config body verbatim.</li>
 * </ul>
 *
 * <p>The resulting text-proto is fed to {@code perfetto -c <file> --txt -o
 * <output>}; see {@link PerfettoCommandBuilder} for the argv shape.
 */
public final class PerfettoTraceConfigBuilder {

    public static final long MIN_DURATION_MS = 500L;
    public static final long MAX_DURATION_MS = 120_000L;
    public static final long DEFAULT_DURATION_MS = 10_000L;

    public static final int DEFAULT_BUFFER_KB = 65_536;

    /**
     * Default ftrace event set. Mirrors the {@code RECORD_LONG_TRACE} preset
     * used by Android Studio Profiler when capturing app-targeted traces.
     */
    private static final List<String> FTRACE_EVENTS = Arrays.asList(
            "sched/sched_switch",
            "sched/sched_wakeup",
            "sched/sched_wakeup_new",
            "sched/sched_waking",
            "sched/sched_process_exit",
            "sched/sched_process_free",
            "task/task_newtask",
            "task/task_rename",
            "power/cpu_frequency",
            "power/cpu_idle",
            "power/suspend_resume"
    );

    private PerfettoTraceConfigBuilder() {
    }

    /**
     * Build the text-proto config body. Duration is clamped; package name
     * must validate via {@link CpuProfileCommandBuilder#isValidPackageName}.
     *
     * @throws IllegalArgumentException if the package name is malformed.
     */
    @NonNull
    public static String buildTextProto(@NonNull String packageName, long durationMillis) {
        return buildTextProto(packageName, durationMillis, DEFAULT_BUFFER_KB);
    }

    /**
     * Same as {@link #buildTextProto(String, long)} but with an explicit
     * in-memory buffer size. Useful for long traces that need more headroom
     * than the default 64 MiB.
     */
    @NonNull
    public static String buildTextProto(@NonNull String packageName, long durationMillis,
                                         int bufferSizeKb) {
        if (!CpuProfileCommandBuilder.isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Bad package name: " + packageName);
        }
        long duration = clampDuration(durationMillis);
        int buffer = clampBuffer(bufferSizeKb);

        StringBuilder sb = new StringBuilder(1024);
        sb.append("# AppManagerNG-generated Perfetto trace config\n");
        sb.append("duration_ms: ").append(duration).append('\n');
        sb.append("buffers: {\n");
        sb.append("    size_kb: ").append(buffer).append('\n');
        sb.append("    fill_policy: RING_BUFFER\n");
        sb.append("}\n");
        sb.append("data_sources: {\n");
        sb.append("    config {\n");
        sb.append("        name: \"linux.ftrace\"\n");
        sb.append("        ftrace_config {\n");
        for (String event : FTRACE_EVENTS) {
            sb.append("            ftrace_events: \"").append(event).append("\"\n");
        }
        sb.append("            atrace_apps: \"").append(packageName).append("\"\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("data_sources: {\n");
        sb.append("    config {\n");
        sb.append("        name: \"linux.process_stats\"\n");
        sb.append("        process_stats_config {\n");
        sb.append("            scan_all_processes_on_start: true\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static long clampDuration(long requestedMillis) {
        if (requestedMillis <= 0) return DEFAULT_DURATION_MS;
        if (requestedMillis < MIN_DURATION_MS) return MIN_DURATION_MS;
        if (requestedMillis > MAX_DURATION_MS) return MAX_DURATION_MS;
        return requestedMillis;
    }

    public static int clampBuffer(int requestedKb) {
        if (requestedKb < 1024) return 1024;          // never below 1 MiB
        if (requestedKb > 262_144) return 262_144;    // cap at 256 MiB
        return requestedKb;
    }

    /** Read-only view of the ftrace events the builder injects. */
    @NonNull
    public static Set<String> ftraceEvents() {
        return new LinkedHashSet<>(FTRACE_EVENTS);
    }
}
