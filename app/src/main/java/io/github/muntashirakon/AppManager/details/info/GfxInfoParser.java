// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "Profile data" / "Janky frames" block of {@code dumpsys gfxinfo
 * &lt;package&gt;} output for the T20-C memory allocations inspector follow-up.
 *
 * <p>Sample slice on a modern Android build:
 * <pre>
 * Stats since: 12345678ns
 * Total frames rendered: 4321
 * Janky frames: 87 (2.01%)
 * Janky frames (legacy): 79 (1.83%)
 * 50th percentile: 4ms
 * 90th percentile: 11ms
 * 95th percentile: 17ms
 * 99th percentile: 38ms
 * Number Missed Vsync: 12
 * Number High input latency: 0
 * Number Slow UI thread: 18
 * Number Slow bitmap uploads: 1
 * Number Slow issue draw commands: 7
 * Number Frame deadline missed: 22
 * </pre>
 *
 * <p>The parser captures the headline jank ratio, the four percentile latency
 * numbers, and the canonical breakdown counters. Missing fields stay at
 * {@code -1} so the App Details UI can render "n/a" rather than fabricated
 * zeros. The parser is intentionally side-effect-free and uses no Android
 * APIs - it sees only the captured byte stream - so it stays JVM-unit-
 * testable against archived dumpsys samples.
 */
public final class GfxInfoParser {

    private static final Pattern TOTAL_FRAMES = Pattern.compile(
            "(?m)^\\s*Total frames rendered:\\s+(\\d+)");
    private static final Pattern JANKY_FRAMES = Pattern.compile(
            "(?m)^\\s*Janky frames:\\s+(\\d+)\\s*\\(\\s*([0-9.]+)%\\)");
    private static final Pattern PERCENTILE = Pattern.compile(
            "(?m)^\\s*(50|90|95|99)th percentile:\\s+(\\d+)ms");
    private static final Pattern MISSED_VSYNC = Pattern.compile(
            "(?m)^\\s*Number Missed Vsync:\\s+(\\d+)");
    private static final Pattern HIGH_INPUT_LATENCY = Pattern.compile(
            "(?m)^\\s*Number High input latency:\\s+(\\d+)");
    private static final Pattern SLOW_UI = Pattern.compile(
            "(?m)^\\s*Number Slow UI thread:\\s+(\\d+)");
    private static final Pattern SLOW_BITMAP_UPLOAD = Pattern.compile(
            "(?m)^\\s*Number Slow bitmap uploads:\\s+(\\d+)");
    private static final Pattern SLOW_DRAW = Pattern.compile(
            "(?m)^\\s*Number Slow issue draw commands:\\s+(\\d+)");
    private static final Pattern FRAME_DEADLINE_MISSED = Pattern.compile(
            "(?m)^\\s*Number Frame deadline missed:\\s+(\\d+)");

    private GfxInfoParser() {
    }

    @Nullable
    public static Snapshot parse(@NonNull String dumpsysOutput) {
        if (dumpsysOutput.isEmpty()) return null;
        // Heuristic: a real gfxinfo dump always contains at least one of the
        // "Total frames" or percentile rows. If neither exists, the caller
        // probably handed us an unrelated dumpsys section.
        if (!TOTAL_FRAMES.matcher(dumpsysOutput).find()
                && !PERCENTILE.matcher(dumpsysOutput).find()) {
            return null;
        }
        Snapshot s = new Snapshot();
        s.totalFrames = parseLongMatch(TOTAL_FRAMES, dumpsysOutput, 1);

        Matcher jankyMatcher = JANKY_FRAMES.matcher(dumpsysOutput);
        if (jankyMatcher.find()) {
            s.jankyFrames = parseLongSafe(jankyMatcher.group(1));
            s.jankyFramesPercent = parseDoubleSafe(jankyMatcher.group(2));
        }

        Matcher pctMatcher = PERCENTILE.matcher(dumpsysOutput);
        while (pctMatcher.find()) {
            String which = pctMatcher.group(1);
            long ms = parseLongSafe(pctMatcher.group(2));
            switch (which) {
                case "50":
                    s.p50LatencyMs = ms;
                    break;
                case "90":
                    s.p90LatencyMs = ms;
                    break;
                case "95":
                    s.p95LatencyMs = ms;
                    break;
                case "99":
                    s.p99LatencyMs = ms;
                    break;
                default:
                    break;
            }
        }

        s.missedVsync = parseLongMatch(MISSED_VSYNC, dumpsysOutput, 1);
        s.highInputLatency = parseLongMatch(HIGH_INPUT_LATENCY, dumpsysOutput, 1);
        s.slowUiThread = parseLongMatch(SLOW_UI, dumpsysOutput, 1);
        s.slowBitmapUploads = parseLongMatch(SLOW_BITMAP_UPLOAD, dumpsysOutput, 1);
        s.slowIssueDrawCommands = parseLongMatch(SLOW_DRAW, dumpsysOutput, 1);
        s.frameDeadlineMissed = parseLongMatch(FRAME_DEADLINE_MISSED, dumpsysOutput, 1);
        return s;
    }

    private static long parseLongMatch(@NonNull Pattern p, @NonNull String input, int group) {
        Matcher m = p.matcher(input);
        if (!m.find()) return -1L;
        return parseLongSafe(m.group(group));
    }

    private static long parseLongSafe(@Nullable String s) {
        if (s == null) return -1L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static double parseDoubleSafe(@Nullable String s) {
        if (s == null) return -1.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return -1.0;
        }
    }

    /** All values default to {@code -1} / {@code -1.0} when the dump did not contain them. */
    public static final class Snapshot {
        public long totalFrames = -1L;
        public long jankyFrames = -1L;
        public double jankyFramesPercent = -1.0;
        public long p50LatencyMs = -1L;
        public long p90LatencyMs = -1L;
        public long p95LatencyMs = -1L;
        public long p99LatencyMs = -1L;
        public long missedVsync = -1L;
        public long highInputLatency = -1L;
        public long slowUiThread = -1L;
        public long slowBitmapUploads = -1L;
        public long slowIssueDrawCommands = -1L;
        public long frameDeadlineMissed = -1L;
    }
}
