// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JVM-testable option catalog for the App Details profiling dialogs.
 */
public final class ProfileCaptureOptionCatalog {
    private static final int[] DURATION_SECONDS = {5, 10, 30, 60, 120};

    private ProfileCaptureOptionCatalog() {
    }

    @NonNull
    public static int[] durationSeconds() {
        return DURATION_SECONDS.clone();
    }

    @NonNull
    public static List<String> durationLabels() {
        List<String> labels = new ArrayList<>(DURATION_SECONDS.length);
        for (int seconds : DURATION_SECONDS) {
            labels.add(formatDurationSeconds(seconds));
        }
        return Collections.unmodifiableList(labels);
    }

    @NonNull
    public static String formatDurationSeconds(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    public static int indexOfDuration(int seconds) {
        int clamped = CpuProfileCommandBuilder.clampDuration(seconds);
        for (int i = 0; i < DURATION_SECONDS.length; ++i) {
            if (DURATION_SECONDS[i] == clamped) {
                return i;
            }
        }
        return indexOfDuration(CpuProfileCommandBuilder.DEFAULT_DURATION_SECONDS);
    }

    public static int durationFromLabel(@Nullable CharSequence label, int fallbackSeconds) {
        if (label == null) {
            return CpuProfileCommandBuilder.clampDuration(fallbackSeconds);
        }
        String value = label.toString().trim();
        for (int seconds : DURATION_SECONDS) {
            if (formatDurationSeconds(seconds).equals(value)) {
                return seconds;
            }
        }
        return CpuProfileCommandBuilder.clampDuration(fallbackSeconds);
    }

    @NonNull
    public static List<String> cpuEventsForDevice(int apiLevel, @Nullable String[] supportedAbis) {
        String abi = supportedAbis != null && supportedAbis.length > 0 && supportedAbis[0] != null
                ? supportedAbis[0]
                : "";
        Set<String> allowed = CpuProfileCommandBuilder.allowedEvents();
        LinkedHashSet<String> events = new LinkedHashSet<>();
        for (String event : CpuProfileEventCatalog.availableEvents(apiLevel, abi)) {
            if (allowed.contains(event)) {
                events.add(event);
            }
        }
        if (events.isEmpty()) {
            events.add(CpuProfileCommandBuilder.DEFAULT_EVENT);
        }
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    @NonNull
    public static String eventFromLabel(@Nullable CharSequence label,
                                        @NonNull List<String> availableEvents) {
        if (label != null) {
            String value = label.toString().trim();
            if (availableEvents.contains(value)) {
                return value;
            }
        }
        if (!availableEvents.isEmpty()) {
            return availableEvents.get(0);
        }
        return CpuProfileCommandBuilder.DEFAULT_EVENT;
    }
}
