// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Tiny formatting layer for the T20-C memory snapshot UI. Centralises the
 * unit ladders so the App Details memory panel renders kB / bytes / jank
 * percentage / latency consistently.
 *
 * <p>All methods are JVM-clean (no Android API), use {@link Locale#ROOT},
 * and treat the canonical {@code -1L} / {@code -1.0} missing-value
 * sentinels as "n/a" rather than as zero.
 */
public final class MemoryFormat {

    /** Rendered when a parser returned the missing-value sentinel. */
    public static final String NOT_AVAILABLE = "n/a";

    private MemoryFormat() {
    }

    /**
     * Render a kilobyte value (as used by every meminfo / proc-status row)
     * with base-1024 units. Negative input -> {@code "n/a"}; zero -> "0 KB".
     */
    @NonNull
    public static String formatKb(long kilobytes) {
        if (kilobytes < 0L) return NOT_AVAILABLE;
        if (kilobytes < 1024L) return kilobytes + " KB";
        double value = kilobytes / 1024.0;
        String unit = "MB";
        if (value >= 1024.0) {
            value /= 1024.0;
            unit = "GB";
        }
        if (value >= 1024.0) {
            value /= 1024.0;
            unit = "TB";
        }
        return String.format(Locale.ROOT, "%.1f %s", value, unit);
    }

    /**
     * Render a byte value (as used by `/proc/maps` rollups) with base-1024
     * units. Negative input -> {@code "n/a"}.
     */
    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 0L) return NOT_AVAILABLE;
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024.0;
        String unit = "KB";
        if (value >= 1024.0) {
            value /= 1024.0;
            unit = "MB";
        }
        if (value >= 1024.0) {
            value /= 1024.0;
            unit = "GB";
        }
        if (value >= 1024.0) {
            value /= 1024.0;
            unit = "TB";
        }
        return String.format(Locale.ROOT, "%.1f %s", value, unit);
    }

    /**
     * Render a percentage (e.g. {@code GfxInfoParser.Snapshot.jankyFramesPercent})
     * with one decimal place. Negative input -> {@code "n/a"}; zero -> "0.0%".
     */
    @NonNull
    public static String formatPercent(double pct) {
        if (pct < 0.0) return NOT_AVAILABLE;
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    /**
     * Render a frame-latency value already in milliseconds. Negative -> "n/a".
     * Zero -> "0 ms".
     */
    @NonNull
    public static String formatLatencyMs(long ms) {
        if (ms < 0L) return NOT_AVAILABLE;
        return ms + " ms";
    }

    /**
     * Render a thread count. Negative -> "n/a"; otherwise just the integer.
     */
    @NonNull
    public static String formatThreadCount(int threads) {
        if (threads < 0) return NOT_AVAILABLE;
        return Integer.toString(threads);
    }

    /**
     * Render a swap value (kilobytes) but make zero collapse to "none"
     * because the App Details panel should not show "0 KB swap" as an
     * actionable metric.
     */
    @NonNull
    public static String formatSwapKb(long kilobytes) {
        if (kilobytes < 0L) return NOT_AVAILABLE;
        if (kilobytes == 0L) return "none";
        return formatKb(kilobytes);
    }
}
