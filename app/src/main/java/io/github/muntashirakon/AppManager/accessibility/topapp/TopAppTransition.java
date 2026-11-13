// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One change the top-app bubble saw: a different app came up underneath, an overlay appeared over it, or an overlay
 * went away. Saved one per line, so an ad that flashed up and vanished can be looked up later.
 */
public final class TopAppTransition {
    public enum Kind {
        /** {@link #packageName} came up underneath, replacing {@link #other}. */
        APP,
        /** {@link #packageName} drew an overlay over {@link #other}. */
        OVERLAY_SHOWN,
        /** The overlay from {@link #packageName}, drawn over {@link #other}, went away. */
        OVERLAY_GONE,
    }

    /** What the bubble showed at one moment. */
    public static final class Layers {
        public static final Layers EMPTY = new Layers(null, null, false);

        @Nullable
        public final String underlying;
        @Nullable
        public final String overlay;
        public final boolean overlayOnScreen;

        public Layers(@Nullable String underlying, @Nullable String overlay, boolean overlayOnScreen) {
            this.underlying = underlying;
            this.overlay = overlay;
            this.overlayOnScreen = overlayOnScreen;
        }

        @Nullable
        String liveOverlay() {
            return overlayOnScreen ? overlay : null;
        }
    }

    private static final char SEPARATOR = '\t';

    /**
     * The changes from {@code before} to {@code after}, in the order they happened: an old overlay leaves before the
     * next one is drawn, and the app underneath comes up before an overlay is drawn over it.
     */
    @NonNull
    public static List<TopAppTransition> between(@NonNull Layers before, @NonNull Layers after, long nowMillis) {
        String beforeOverlay = before.liveOverlay();
        String afterOverlay = after.liveOverlay();
        List<TopAppTransition> transitions = new ArrayList<>(3);
        if (beforeOverlay != null && !beforeOverlay.equals(afterOverlay)) {
            transitions.add(new TopAppTransition(nowMillis, Kind.OVERLAY_GONE, beforeOverlay, before.underlying));
        }
        if (after.underlying != null && !after.underlying.equals(before.underlying)) {
            transitions.add(new TopAppTransition(nowMillis, Kind.APP, after.underlying, before.underlying));
        }
        if (afterOverlay != null && !afterOverlay.equals(beforeOverlay)) {
            transitions.add(new TopAppTransition(nowMillis, Kind.OVERLAY_SHOWN, afterOverlay, after.underlying));
        }
        return transitions.isEmpty() ? Collections.emptyList() : transitions;
    }

    /** A saved line, or {@code null} if it isn't one, so a damaged file loses that line and nothing else. */
    @Nullable
    public static TopAppTransition parse(@NonNull String line) {
        String[] fields = line.split(String.valueOf(SEPARATOR), -1);
        if (fields.length != 4 || fields[2].isEmpty()) {
            return null;
        }
        try {
            long timestampMillis = Long.parseLong(fields[0]);
            Kind kind = Kind.valueOf(fields[1]);
            return new TopAppTransition(timestampMillis, kind, fields[2], fields[3].isEmpty() ? null : fields[3]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public final long timestampMillis;
    @NonNull
    public final Kind kind;
    @NonNull
    public final String packageName;
    /** The app it replaced or was drawn over, if one was known. */
    @Nullable
    public final String other;

    public TopAppTransition(long timestampMillis, @NonNull Kind kind, @NonNull String packageName,
                            @Nullable String other) {
        this.timestampMillis = timestampMillis;
        this.kind = kind;
        this.packageName = packageName;
        this.other = other;
    }

    public boolean isOverlay() {
        return kind != Kind.APP;
    }

    /** One line with no line break. Package names can't hold a tab or a line break, so they're kept as they are. */
    @NonNull
    public String toLine() {
        return String.valueOf(timestampMillis) + SEPARATOR + kind.name() + SEPARATOR + packageName + SEPARATOR
                + (other != null ? other : "");
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof TopAppTransition)) return false;
        TopAppTransition that = (TopAppTransition) o;
        return timestampMillis == that.timestampMillis && kind == that.kind
                && packageName.equals(that.packageName) && Objects.equals(other, that.other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampMillis, kind, packageName, other);
    }

    @NonNull
    @Override
    public String toString() {
        return toLine();
    }
}
