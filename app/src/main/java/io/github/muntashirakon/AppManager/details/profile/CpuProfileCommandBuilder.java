// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-function command-line builder for the T20-B simpleperf CPU profile
 * action.
 *
 * <p>"Record CPU profile" in App Details runs Android's bundled
 * {@code simpleperf} binary for a bounded sampling window targeted at a
 * specific package. The actual privileged process spawn lives in the
 * runner / Shizuku / ADB layer; this builder is the JVM-testable shape that
 * decides exactly which argv to hand them.
 *
 * <p>Invariants enforced here:
 * <ul>
 *   <li>Package name must look like a real Android package (the same
 *       conservative predicate used by the leftover-detection scanner).</li>
 *   <li>Duration is clamped to {@code [1, MAX_DURATION_SECONDS]} so a
 *       runaway dialog cannot start an indefinite trace.</li>
 *   <li>Output path is treated as a literal value; any caller-supplied path
 *       fragment that contains a shell metacharacter is rejected so the
 *       builder cannot be coerced into argument-injection.</li>
 *   <li>Event names accept the simpleperf default
 *       ({@code cpu-cycles}, {@code task-clock}) and the canonical
 *       hardware counters; anything else falls back to {@code cpu-cycles}
 *       so we never hand the privileged shell a free-form event spec.</li>
 * </ul>
 *
 * <p>The argv shape is the modern simpleperf 1.6+ form:
 * <pre>
 *   simpleperf record --app &lt;package&gt; --duration &lt;seconds&gt;
 *                     -e &lt;event&gt; -g -o &lt;output&gt; --call-graph dwarf
 * </pre>
 * <p>{@code --app} is preferred over {@code -p &lt;pid&gt;} because the
 * userspace simpleperf wrapper resolves the package -> uid -> pid mapping
 * across multi-process apps without races, and stays valid if the target
 * relaunches mid-capture.
 */
public final class CpuProfileCommandBuilder {

    public static final int MIN_DURATION_SECONDS = 1;
    public static final int MAX_DURATION_SECONDS = 120;
    public static final int DEFAULT_DURATION_SECONDS = 10;

    public static final String DEFAULT_EVENT = "cpu-cycles";

    private static final Set<String> ALLOWED_EVENTS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "cpu-cycles",
                    "task-clock",
                    "cpu-clock",
                    "instructions",
                    "cache-references",
                    "cache-misses",
                    "branch-instructions",
                    "branch-misses",
                    "bus-cycles",
                    "page-faults",
                    "cpu-migrations",
                    "minor-faults",
                    "major-faults",
                    "context-switches",
                    "stalled-cycles-frontend",
                    "stalled-cycles-backend",
                    "L1-dcache-loads",
                    "L1-dcache-load-misses",
                    "L1-icache-loads",
                    "L1-icache-load-misses",
                    "dTLB-loads",
                    "dTLB-load-misses",
                    "iTLB-loads",
                    "iTLB-load-misses"
            )));

    /**
     * Returns the canonical event name to record. Unknown / malformed inputs
     * fall back to {@link #DEFAULT_EVENT} so the privileged shell never sees
     * arbitrary user input here.
     */
    @NonNull
    public static String normalizeEvent(@Nullable String requestedEvent) {
        if (requestedEvent == null) return DEFAULT_EVENT;
        String trimmed = requestedEvent.trim();
        if (trimmed.isEmpty()) return DEFAULT_EVENT;
        return ALLOWED_EVENTS.contains(trimmed) ? trimmed : DEFAULT_EVENT;
    }

    /**
     * Clamp {@code requestedSeconds} into the supported window. Returns
     * {@link #DEFAULT_DURATION_SECONDS} for non-positive values.
     */
    public static int clampDuration(int requestedSeconds) {
        if (requestedSeconds <= 0) return DEFAULT_DURATION_SECONDS;
        if (requestedSeconds < MIN_DURATION_SECONDS) return MIN_DURATION_SECONDS;
        if (requestedSeconds > MAX_DURATION_SECONDS) return MAX_DURATION_SECONDS;
        return requestedSeconds;
    }

    /**
     * Build the argv array for a {@code simpleperf record} command targeted
     * at {@code packageName}.
     *
     * @throws IllegalArgumentException if the package name is malformed or
     *         the output path contains shell metacharacters.
     */
    @NonNull
    public static String[] build(@NonNull String packageName, int durationSeconds,
                                  @Nullable String event, @NonNull String outputPath) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Bad package name: " + packageName);
        }
        if (!isSafeOutputPath(outputPath)) {
            throw new IllegalArgumentException("Unsafe output path: " + outputPath);
        }
        int duration = clampDuration(durationSeconds);
        String normalizedEvent = normalizeEvent(event);
        List<String> argv = new ArrayList<>(11);
        argv.add("simpleperf");
        argv.add("record");
        argv.add("--app");
        argv.add(packageName);
        argv.add("--duration");
        argv.add(Integer.toString(duration));
        argv.add("-e");
        argv.add(normalizedEvent);
        argv.add("-g");
        argv.add("--call-graph");
        argv.add("dwarf");
        argv.add("-o");
        argv.add(outputPath);
        return argv.toArray(new String[0]);
    }

    /**
     * Validation predicate exposed for callers that need to gate the action
     * before they invoke {@link #build}.
     */
    public static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty() || name.length() > 200) return false;
        if (name.charAt(0) == '.') return false;
        if (name.indexOf('.') < 0) return false;
        boolean lastWasDot = true;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '.') {
                if (lastWasDot) return false;
                lastWasDot = true;
                continue;
            }
            boolean valid;
            if (lastWasDot) {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            } else {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '_';
            }
            if (!valid) return false;
            lastWasDot = false;
        }
        return !lastWasDot;
    }

    /**
     * Reject paths that contain shell metacharacters, control bytes, a space,
     * a {@code ..} path-traversal segment, or are absent. simpleperf/perfetto
     * accept a single literal path argument; passing anything that a shell
     * could re-parse - even though we use ProcessBuilder with a literal argv -
     * is a defense-in-depth measure for the rare runner implementation that
     * goes through {@code sh -c}.
     *
     * <p>The predicate is deliberately kept in lock-step with
     * {@link PrivilegedRunnerArgValidator#classifyPath}, which is the gate the
     * privileged runner applies to the assembled argv: a space is rejected
     * here (the runner rejects it too, so allowing it in the builder only
     * produced a confusing late "unsafe argv" failure), and a {@code ..}
     * segment is rejected so a caller cannot direct the root/Shizuku write
     * outside the intended directory via traversal.
     */
    public static boolean isSafeOutputPath(@NonNull String path) {
        if (path.isEmpty() || path.length() > 1024) return false;
        for (int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7f) return false;
            switch (c) {
                case '`':
                case '$':
                case '"':
                case '\'':
                case ';':
                case '&':
                case '|':
                case '<':
                case '>':
                case '*':
                case '?':
                case '!':
                case '\\':
                case '\n':
                case '\r':
                case ' ':
                    return false;
                default:
                    break;
            }
        }
        return !PrivilegedRunnerArgValidator.containsTraversalSegment(path);
    }

    /** Read-only view of the events the builder currently honors. */
    @NonNull
    public static Set<String> allowedEvents() {
        return ALLOWED_EVENTS;
    }

    private CpuProfileCommandBuilder() {
    }
}
