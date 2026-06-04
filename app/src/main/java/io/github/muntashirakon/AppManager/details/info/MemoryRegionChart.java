// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class MemoryRegionChart {
    private static final int MIN_WIDTH = 8;
    private static final int MAX_WIDTH = 48;

    public static final class Segment {
        @NonNull
        public final String label;
        public final long bytes;

        public Segment(@NonNull String label, long bytes) {
            this.label = label;
            this.bytes = bytes;
        }
    }

    private MemoryRegionChart() {
    }

    @NonNull
    public static String render(@NonNull List<Segment> segments, int width) {
        List<Segment> positive = new ArrayList<>();
        long total = 0L;
        for (Segment segment : segments) {
            if (segment.bytes <= 0L) {
                continue;
            }
            positive.add(segment);
            total += segment.bytes;
        }
        if (positive.isEmpty() || total <= 0L) {
            return "";
        }
        int chartWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
        StringBuilder out = new StringBuilder();
        for (Segment segment : positive) {
            int bars = Math.max(1, (int) Math.round((double) chartWidth * segment.bytes / total));
            out.append(segment.label)
                    .append(": ")
                    .append(repeat('#', bars))
                    .append(' ')
                    .append(MemoryFormat.formatBytes(segment.bytes))
                    .append('\n');
        }
        return out.toString().trim();
    }

    @NonNull
    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }
}
