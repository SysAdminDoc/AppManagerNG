// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class CodeEditorDiff {
    @VisibleForTesting
    static final int DEFAULT_MAX_TOTAL_LINES = 20_000;
    @VisibleForTesting
    static final int DEFAULT_MAX_DISPLAY_LINES = 500;
    private static final int MAX_MYERS_EDIT_DISTANCE = 1_024;
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\r\\n|\\n|\\r");

    enum Kind {
        ADDED,
        REMOVED
    }

    static final class LineChange {
        @NonNull
        final Kind kind;
        @NonNull
        final String text;

        private LineChange(@NonNull Kind kind, @NonNull String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    static final class Result {
        private final boolean mTooLarge;
        private final boolean mNoChanges;
        final int added;
        final int removed;
        final int omitted;
        @NonNull
        final List<LineChange> displayLines;

        private Result(boolean tooLarge, boolean noChanges, int added, int removed, int omitted,
                       @NonNull List<LineChange> displayLines) {
            mTooLarge = tooLarge;
            mNoChanges = noChanges;
            this.added = added;
            this.removed = removed;
            this.omitted = omitted;
            this.displayLines = Collections.unmodifiableList(displayLines);
        }

        boolean isTooLarge() {
            return mTooLarge;
        }

        boolean isNoChanges() {
            return mNoChanges;
        }
    }

    @NonNull
    static Result compute(@NonNull String original, @NonNull String current) {
        return compute(original, current, DEFAULT_MAX_TOTAL_LINES, DEFAULT_MAX_DISPLAY_LINES);
    }

    @VisibleForTesting
    @NonNull
    static Result compute(@NonNull String original, @NonNull String current,
                          int maxTotalLines, int maxDisplayLines) {
        if (original.equals(current)) {
            return new Result(false, true, 0, 0, 0, Collections.emptyList());
        }
        String[] originalLines = splitLines(original);
        String[] currentLines = splitLines(current);
        if (originalLines.length + currentLines.length > maxTotalLines) {
            return new Result(true, false, 0, 0, 0, Collections.emptyList());
        }
        List<LineChange> changes = calculateChanges(originalLines, currentLines);
        int added = 0;
        int removed = 0;
        List<LineChange> displayLines = new ArrayList<>(Math.min(changes.size(), maxDisplayLines));
        for (LineChange change : changes) {
            if (change.kind == Kind.ADDED) {
                ++added;
            } else {
                ++removed;
            }
            if (displayLines.size() < maxDisplayLines) {
                displayLines.add(change);
            }
        }
        return new Result(false, false, added, removed,
                Math.max(0, added + removed - displayLines.size()), displayLines);
    }

    @NonNull
    private static String[] splitLines(@NonNull String text) {
        if (text.isEmpty()) {
            return new String[0];
        }
        return LINE_SEPARATOR.split(text, -1);
    }

    @NonNull
    private static List<LineChange> calculateChanges(@NonNull String[] originalLines,
                                                     @NonNull String[] currentLines) {
        int prefix = 0;
        int prefixLimit = Math.min(originalLines.length, currentLines.length);
        while (prefix < prefixLimit && originalLines[prefix].equals(currentLines[prefix])) {
            ++prefix;
        }
        int originalEnd = originalLines.length;
        int currentEnd = currentLines.length;
        while (originalEnd > prefix && currentEnd > prefix
                && originalLines[originalEnd - 1].equals(currentLines[currentEnd - 1])) {
            --originalEnd;
            --currentEnd;
        }
        List<LineChange> changes = myersChanges(originalLines, prefix, originalEnd,
                currentLines, prefix, currentEnd);
        if (changes != null) {
            return changes;
        }
        return fallbackChanges(originalLines, prefix, originalEnd, currentLines, prefix, currentEnd);
    }

    @NonNull
    private static List<LineChange> fallbackChanges(@NonNull String[] originalLines, int originalStart,
                                                    int originalEnd, @NonNull String[] currentLines,
                                                    int currentStart, int currentEnd) {
        List<LineChange> changes = new ArrayList<>((originalEnd - originalStart) + (currentEnd - currentStart));
        for (int i = originalStart; i < originalEnd; ++i) {
            changes.add(new LineChange(Kind.REMOVED, originalLines[i]));
        }
        for (int i = currentStart; i < currentEnd; ++i) {
            changes.add(new LineChange(Kind.ADDED, currentLines[i]));
        }
        return changes;
    }

    @Nullable
    private static List<LineChange> myersChanges(@NonNull String[] originalLines, int originalStart,
                                                 int originalEnd, @NonNull String[] currentLines,
                                                 int currentStart, int currentEnd) {
        int originalSize = originalEnd - originalStart;
        int currentSize = currentEnd - currentStart;
        int max = originalSize + currentSize;
        int maxDistance = Math.min(max, MAX_MYERS_EDIT_DISTANCE);
        int offset = maxDistance + 1;
        int[] frontier = new int[(2 * maxDistance) + 3];
        Arrays.fill(frontier, -1);
        frontier[offset + 1] = 0;
        List<int[]> trace = new ArrayList<>(maxDistance + 1);
        for (int distance = 0; distance <= maxDistance; ++distance) {
            trace.add(Arrays.copyOf(frontier, frontier.length));
            for (int diagonal = -distance; diagonal <= distance; diagonal += 2) {
                int right = frontier[offset + diagonal - 1];
                int down = frontier[offset + diagonal + 1];
                int x;
                if (diagonal == -distance || (diagonal != distance && right < down)) {
                    x = down;
                } else {
                    x = right + 1;
                }
                int y = x - diagonal;
                while (x < originalSize && y < currentSize
                        && originalLines[originalStart + x].equals(currentLines[currentStart + y])) {
                    ++x;
                    ++y;
                }
                frontier[offset + diagonal] = x;
                if (x >= originalSize && y >= currentSize) {
                    return backtrack(trace, originalLines, originalStart, currentLines, currentStart,
                            originalSize, currentSize, offset, distance);
                }
            }
        }
        return null;
    }

    @NonNull
    private static List<LineChange> backtrack(@NonNull List<int[]> trace,
                                              @NonNull String[] originalLines, int originalStart,
                                              @NonNull String[] currentLines, int currentStart,
                                              int x, int y, int offset, int distance) {
        List<LineChange> reversed = new ArrayList<>(distance);
        for (int d = distance; d > 0; --d) {
            int[] frontier = trace.get(d);
            int diagonal = x - y;
            int prevDiagonal;
            if (diagonal == -d || (diagonal != d
                    && frontier[offset + diagonal - 1] < frontier[offset + diagonal + 1])) {
                prevDiagonal = diagonal + 1;
            } else {
                prevDiagonal = diagonal - 1;
            }
            int prevX = frontier[offset + prevDiagonal];
            int prevY = prevX - prevDiagonal;
            while (x > prevX && y > prevY) {
                --x;
                --y;
            }
            if (x == prevX) {
                --y;
                reversed.add(new LineChange(Kind.ADDED, currentLines[currentStart + y]));
            } else {
                --x;
                reversed.add(new LineChange(Kind.REMOVED, originalLines[originalStart + x]));
            }
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private CodeEditorDiff() {
    }
}
