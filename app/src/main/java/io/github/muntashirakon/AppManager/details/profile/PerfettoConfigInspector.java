// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspect a Perfetto text-proto trace config and report the effective
 * duration, buffer size, target package, and the list of ftrace events
 * actually enabled.
 *
 * <p>{@link PerfettoTraceConfigBuilder} produces the config; the App
 * Details T20-A surface needs a "what will this capture do?" preview
 * before the user pulls the trigger. This inspector is the read-side
 * counterpart, parsing the subset of text-proto we emit. It does not
 * attempt to be a general-purpose perfetto-cfg parser - only what the
 * builder writes round-trips through.
 *
 * <p>Pure JVM; no Android API.
 */
public final class PerfettoConfigInspector {

    private static final Pattern DURATION = Pattern.compile(
            "(?m)^\\s*duration_ms\\s*:\\s*(\\d+)\\s*$");
    private static final Pattern BUFFER_SIZE = Pattern.compile(
            "(?m)^\\s*size_kb\\s*:\\s*(\\d+)\\s*$");
    private static final Pattern ATRACE_APP = Pattern.compile(
            "(?m)^\\s*atrace_apps\\s*:\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern FTRACE_EVENT = Pattern.compile(
            "(?m)^\\s*ftrace_events\\s*:\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern DATA_SOURCE = Pattern.compile(
            "(?m)^\\s*name\\s*:\\s*\"([^\"]+)\"\\s*$");

    public static final class Inspection {
        public final long durationMillis;
        public final int bufferKb;
        @NonNull
        public final List<String> targetPackages;
        @NonNull
        public final Set<String> ftraceEvents;
        @NonNull
        public final Set<String> dataSources;

        Inspection(long durationMillis, int bufferKb,
                   @NonNull List<String> targetPackages,
                   @NonNull Set<String> ftraceEvents,
                   @NonNull Set<String> dataSources) {
            this.durationMillis = durationMillis;
            this.bufferKb = bufferKb;
            this.targetPackages = Collections.unmodifiableList(targetPackages);
            this.ftraceEvents = Collections.unmodifiableSet(ftraceEvents);
            this.dataSources = Collections.unmodifiableSet(dataSources);
        }

        /** Has the inspector decoded enough to render a preview? */
        public boolean isValid() {
            return durationMillis > 0L && bufferKb > 0 && !targetPackages.isEmpty();
        }
    }

    private PerfettoConfigInspector() {
    }

    /**
     * Parse a Perfetto text-proto config and report what it would do.
     * Missing fields default to zero / empty; the caller checks
     * {@link Inspection#isValid()} before rendering.
     */
    @NonNull
    public static Inspection inspect(@Nullable String textProto) {
        if (textProto == null || textProto.isEmpty()) {
            return new Inspection(0L, 0, new ArrayList<>(),
                    new LinkedHashSet<>(), new LinkedHashSet<>());
        }
        long duration = firstLong(textProto, DURATION);
        int bufferKb = firstInt(textProto, BUFFER_SIZE);
        List<String> targets = allStrings(textProto, ATRACE_APP);
        Set<String> events = new LinkedHashSet<>(allStrings(textProto, FTRACE_EVENT));
        Set<String> sources = new LinkedHashSet<>(allStrings(textProto, DATA_SOURCE));
        return new Inspection(duration, bufferKb, targets, events, sources);
    }

    private static long firstLong(@NonNull String text, @NonNull Pattern p) {
        Matcher m = p.matcher(text);
        if (!m.find()) return 0L;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int firstInt(@NonNull String text, @NonNull Pattern p) {
        long v = firstLong(text, p);
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    @NonNull
    private static List<String> allStrings(@NonNull String text, @NonNull Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (v != null && !v.isEmpty()) out.add(v);
        }
        return out;
    }

    /**
     * Render a one-line summary for the App Details preview chip.
     * Format: {@code "<duration>s · <buffer>KB ring · <event-count>
     * ftrace events · <pkg>"}.
     */
    @NonNull
    public static String oneLineSummary(@NonNull Inspection inspection) {
        if (!inspection.isValid()) return "Invalid trace config";
        long durationSec = inspection.durationMillis / 1000L;
        String pkg = inspection.targetPackages.isEmpty()
                ? "all apps"
                : inspection.targetPackages.get(0);
        return durationSec + "s · " + inspection.bufferKb + " KB ring · "
                + inspection.ftraceEvents.size() + " ftrace events · " + pkg;
    }
}
