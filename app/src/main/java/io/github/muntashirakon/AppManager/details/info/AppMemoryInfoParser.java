// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "App Summary" block of {@code dumpsys meminfo &lt;pid&gt;} output
 * for the T20-C memory allocations inspector data layer.
 *
 * <p>The "App Summary" section has been a stable surface across Android 6
 * through Android 17. It looks like this on a modern device:
 *
 * <pre>
 *  App Summary
 *                         Pss(KB)                        Rss(KB)
 *                          ------                         ------
 *             Java Heap:      801                         12912
 *           Native Heap:     1234                          5678
 *                  Code:     2789                          7800
 *                 Stack:      120                           567
 *              Graphics:      456                           678
 *         Private Other:      245
 *                System:     1080
 *               Unknown:                                     34
 *             TOTAL PSS:    6725            TOTAL RSS:    27669       TOTAL SWAP (KB):       0
 * </pre>
 *
 * <p>Older Android builds omit the {@code Rss(KB)} column entirely; this
 * parser tolerates both single- and double-column rows so a single capture
 * pipeline serves API 21 -> 37. Any field that the input did not contain is
 * left at {@code -1} in the returned {@link Snapshot}, so callers can show
 * "n/a" rather than fabricated zeros.
 *
 * <p>The parser is intentionally side-effect-free and uses no Android
 * APIs - it only sees the captured byte stream - so it is JVM-unit-testable
 * against archived dumpsys samples.
 */
public final class AppMemoryInfoParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "(?m)^\\s*App\\s+Summary\\s*$");

    /**
     * Match a row like {@code Java Heap:   801    12912}. Allows an optional
     * second number for the RSS column on Android 8+. Whitespace between
     * label and numbers is variable; the regex normalises that.
     */
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z ()]+?):\\s+(\\d+)(?:\\s+(\\d+))?(?:\\s+TOTAL\\s+SWAP.*)?\\s*$");

    /**
     * Match the final TOTAL-prefixed lines. The TOTAL PSS line on some Android
     * versions packs three TOTAL values onto one line (PSS, RSS, SWAP), so
     * the row regex above is intentionally permissive there; this regex pulls
     * out the explicit numeric trios.
     */
    private static final Pattern TOTAL_LINE_PATTERN = Pattern.compile(
            "TOTAL\\s+PSS:\\s+(\\d+)(?:.*?TOTAL\\s+RSS:\\s+(\\d+))?(?:.*?TOTAL\\s+SWAP(?:\\s*\\(KB\\))?:\\s+(\\d+))?");

    private AppMemoryInfoParser() {
    }

    /**
     * Parse the "App Summary" section of a {@code dumpsys meminfo} dump.
     * Returns {@code null} if the section header is missing - the caller
     * should treat that as "dumpsys returned an unrecognized format" rather
     * than "empty snapshot".
     */
    @Nullable
    public static Snapshot parseAppSummary(@NonNull String dumpsysOutput) {
        Matcher header = HEADER_PATTERN.matcher(dumpsysOutput);
        if (!header.find()) return null;
        // Operate only on the section after the App Summary header so we don't
        // accidentally capture rows from the per-region table above.
        String section = dumpsysOutput.substring(header.end());
        Snapshot snapshot = new Snapshot();

        Matcher rowMatcher = ROW_PATTERN.matcher(section);
        while (rowMatcher.find()) {
            String label = rowMatcher.group(1).trim();
            long pss = parseLongSafe(rowMatcher.group(2));
            long rss = parseLongSafe(rowMatcher.group(3));
            applyRow(snapshot, label, pss, rss);
        }

        Matcher totalMatcher = TOTAL_LINE_PATTERN.matcher(section);
        if (totalMatcher.find()) {
            snapshot.totalPssKb = parseLongSafe(totalMatcher.group(1));
            long rss = parseLongSafe(totalMatcher.group(2));
            if (rss >= 0) snapshot.totalRssKb = rss;
            long swap = parseLongSafe(totalMatcher.group(3));
            if (swap >= 0) snapshot.totalSwapKb = swap;
        }

        return snapshot;
    }

    private static void applyRow(@NonNull Snapshot snapshot, @NonNull String label,
                                 long pss, long rss) {
        // Skip any TOTAL row - the dedicated total-line regex handles those.
        if (label.startsWith("TOTAL")) return;
        switch (label) {
            case "Java Heap":
                snapshot.javaHeapPssKb = pss;
                snapshot.javaHeapRssKb = rss;
                break;
            case "Native Heap":
                snapshot.nativeHeapPssKb = pss;
                snapshot.nativeHeapRssKb = rss;
                break;
            case "Code":
                snapshot.codePssKb = pss;
                snapshot.codeRssKb = rss;
                break;
            case "Stack":
                snapshot.stackPssKb = pss;
                snapshot.stackRssKb = rss;
                break;
            case "Graphics":
                snapshot.graphicsPssKb = pss;
                snapshot.graphicsRssKb = rss;
                break;
            case "Private Other":
                snapshot.privateOtherPssKb = pss;
                break;
            case "System":
                snapshot.systemPssKb = pss;
                break;
            case "Unknown":
                // On modern Android the "Unknown" row only has an RSS column,
                // so the leading number lands in the RSS slot. Older Android
                // dumps put it in PSS. Accept either and prefer the larger
                // since one will be the missing-field sentinel.
                long known = Math.max(pss, rss);
                snapshot.unknownRssKb = known;
                break;
            default:
                // Unrecognised label - leave fields untouched. This guarantees
                // forward-compatibility if Google adds a new App Summary row
                // in a future Android release.
                break;
        }
    }

    private static long parseLongSafe(@Nullable String s) {
        if (s == null) return -1L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    /** Per-app memory snapshot, all values in kilobytes. {@code -1} = missing. */
    public static final class Snapshot {
        public long javaHeapPssKb = -1L;
        public long javaHeapRssKb = -1L;
        public long nativeHeapPssKb = -1L;
        public long nativeHeapRssKb = -1L;
        public long codePssKb = -1L;
        public long codeRssKb = -1L;
        public long stackPssKb = -1L;
        public long stackRssKb = -1L;
        public long graphicsPssKb = -1L;
        public long graphicsRssKb = -1L;
        public long privateOtherPssKb = -1L;
        public long systemPssKb = -1L;
        public long unknownRssKb = -1L;
        public long totalPssKb = -1L;
        public long totalRssKb = -1L;
        public long totalSwapKb = -1L;

        /**
         * Sum of every present per-region PSS value, capped at 0 when every
         * entry is missing. Equivalent to the dumpsys TOTAL PSS row when
         * dumpsys actually emits one, but works as a fallback when the totals
         * line is absent (some OEM stripped-down dumpsys variants).
         */
        public long sumPresentPssKb() {
            long sum = 0L;
            sum += positiveOrZero(javaHeapPssKb);
            sum += positiveOrZero(nativeHeapPssKb);
            sum += positiveOrZero(codePssKb);
            sum += positiveOrZero(stackPssKb);
            sum += positiveOrZero(graphicsPssKb);
            sum += positiveOrZero(privateOtherPssKb);
            sum += positiveOrZero(systemPssKb);
            return sum;
        }

        private static long positiveOrZero(long v) {
            return v > 0 ? v : 0L;
        }
    }
}
