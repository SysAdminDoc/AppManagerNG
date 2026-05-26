// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-API-level / per-ABI catalog of simpleperf events known-safe to
 * record on a target device for the T20-B "Record CPU profile" action.
 *
 * <p>{@link CpuProfileCommandBuilder} already filters caller-supplied
 * event names through a single static allow-list; this catalog narrows
 * the list further per device class so the App Details UI does not
 * offer the user events the kernel won't deliver:
 *
 * <ul>
 *   <li>Software-clock events ({@code cpu-clock}, {@code task-clock},
 *       {@code page-faults}, {@code context-switches},
 *       {@code cpu-migrations}, {@code minor-faults},
 *       {@code major-faults}) work on every supported Android version
 *       (API 21+) on every ABI.</li>
 *   <li>Hardware PMU events ({@code cpu-cycles},
 *       {@code instructions}, {@code cache-references},
 *       {@code cache-misses}, {@code branch-instructions},
 *       {@code branch-misses}, {@code bus-cycles}) require kernel
 *       {@code perf_event_paranoid} &lt;= 1. ABIs that don't ship a
 *       PMU (most x86 emulators) cannot deliver them.</li>
 *   <li>Cache-prefetch / stalled-cycles events are only meaningful on
 *       ARM Cortex-A55 / A75 / X1 + Apple Firestorm-derived ARM cores
 *       and on Intel Skylake+ on x86; lumped under
 *       {@link Class#PMU_ADVANCED} so the UI hides them when the
 *       target API is &lt; 27 (Snapdragon 845 era).</li>
 * </ul>
 *
 * <p>The catalog is JVM-clean and uses no Android API; the caller
 * passes the target API level and ABI strings explicitly.
 */
public final class CpuProfileEventCatalog {

    /** Available event class. */
    public enum Class {
        /** Always available - software clock, page/fault counters. */
        SOFTWARE,
        /** Common hardware PMU events; require perf_event_paranoid &lt;= 1. */
        PMU_BASIC,
        /** Advanced PMU events; require API &gt;= 27 and an ABI with PMU. */
        PMU_ADVANCED
    }

    private static final Set<String> SOFTWARE = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "cpu-clock",
                    "task-clock",
                    "page-faults",
                    "context-switches",
                    "cpu-migrations",
                    "minor-faults",
                    "major-faults")));

    private static final Set<String> PMU_BASIC = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "cpu-cycles",
                    "instructions",
                    "cache-references",
                    "cache-misses",
                    "branch-instructions",
                    "branch-misses",
                    "bus-cycles")));

    private static final Set<String> PMU_ADVANCED = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "stalled-cycles-frontend",
                    "stalled-cycles-backend",
                    "L1-dcache-loads",
                    "L1-dcache-load-misses",
                    "L1-icache-loads",
                    "L1-icache-load-misses",
                    "dTLB-loads",
                    "dTLB-load-misses",
                    "iTLB-loads",
                    "iTLB-load-misses")));

    private static final Set<String> PMU_BEARING_ABIS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "arm64-v8a",
                    "armeabi-v7a",
                    "armeabi",
                    "x86_64",
                    "x86")));

    public static final int MIN_PMU_BASIC_API = 23; // Android 6.0 - first reliable PMU+simpleperf combo
    public static final int MIN_PMU_ADVANCED_API = 27; // Android 8.1 - first widely-shipped Cortex-A55+

    private CpuProfileEventCatalog() {
    }

    /** Events in this class regardless of API/ABI. */
    @NonNull
    public static Set<String> eventsFor(@NonNull Class clazz) {
        switch (clazz) {
            case SOFTWARE:
                return SOFTWARE;
            case PMU_BASIC:
                return PMU_BASIC;
            case PMU_ADVANCED:
                return PMU_ADVANCED;
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Effective event list for the supplied device. Returns the union of
     * SOFTWARE plus whichever PMU classes the API + ABI allow.
     */
    @NonNull
    public static Set<String> availableEvents(int apiLevel, @NonNull String abi) {
        LinkedHashSet<String> out = new LinkedHashSet<>(SOFTWARE);
        if (apiLevel >= MIN_PMU_BASIC_API && hasPmu(abi)) {
            out.addAll(PMU_BASIC);
        }
        if (apiLevel >= MIN_PMU_ADVANCED_API && hasPmu(abi)) {
            out.addAll(PMU_ADVANCED);
        }
        return Collections.unmodifiableSet(out);
    }

    /** UI helper: which events are <em>not</em> available on this device, with reason. */
    @NonNull
    public static List<UnavailableEvent> unavailableOn(int apiLevel, @NonNull String abi) {
        Set<String> available = availableEvents(apiLevel, abi);
        List<UnavailableEvent> out = new ArrayList<>();
        for (String e : PMU_BASIC) {
            if (!available.contains(e)) out.add(reasonFor(e, apiLevel, abi));
        }
        for (String e : PMU_ADVANCED) {
            if (!available.contains(e)) out.add(reasonFor(e, apiLevel, abi));
        }
        return out;
    }

    static boolean hasPmu(@NonNull String abi) {
        return PMU_BEARING_ABIS.contains(abi);
    }

    @NonNull
    private static UnavailableEvent reasonFor(@NonNull String event, int apiLevel,
                                              @NonNull String abi) {
        if (!hasPmu(abi)) {
            return new UnavailableEvent(event, Reason.NO_PMU_FOR_ABI,
                    "ABI " + abi + " has no PMU");
        }
        if (PMU_ADVANCED.contains(event) && apiLevel < MIN_PMU_ADVANCED_API) {
            return new UnavailableEvent(event, Reason.API_TOO_LOW,
                    "Requires API " + MIN_PMU_ADVANCED_API + ", device on " + apiLevel);
        }
        if (PMU_BASIC.contains(event) && apiLevel < MIN_PMU_BASIC_API) {
            return new UnavailableEvent(event, Reason.API_TOO_LOW,
                    "Requires API " + MIN_PMU_BASIC_API + ", device on " + apiLevel);
        }
        return new UnavailableEvent(event, Reason.OTHER,
                "Unavailable on this device");
    }

    public enum Reason {
        NO_PMU_FOR_ABI,
        API_TOO_LOW,
        OTHER
    }

    public static final class UnavailableEvent {
        @NonNull public final String name;
        @NonNull public final Reason reason;
        @NonNull public final String explanation;

        UnavailableEvent(@NonNull String name, @NonNull Reason reason,
                         @NonNull String explanation) {
            this.name = name;
            this.reason = reason;
            this.explanation = explanation;
        }
    }
}
