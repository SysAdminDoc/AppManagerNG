// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function aggregator for the T19-A "backup archive size" sibling row
 * in the App Details Storage panel.
 *
 * <p>App Details already shows the package's on-device storage breakdown via
 * {@code PackageSizeInfo} (code / data / cache / obb / media / total).
 * Sibling to that, the storage panel should surface the backup-archive
 * footprint - the total bytes consumed by every saved backup of this
 * package, broken down per version code when there is more than one.
 *
 * <p>This aggregator takes a flat list of {@link Archive} records (one per
 * backup) and produces a {@link Summary} with:
 * <ul>
 *   <li>{@code totalBytes} - sum across every archive.</li>
 *   <li>{@code archiveCount} - non-negative size count, helpful for the UI
 *       to show "12 backups · 1.2 GB".</li>
 *   <li>{@code byVersionCode} - per-version-code breakdown in insertion
 *       order, with each version code's archives sorted newest first so
 *       the panel can collapse stale rows cleanly.</li>
 *   <li>{@code newestArchive} - shortcut for the panel header.</li>
 * </ul>
 *
 * <p>The aggregator never throws; archives with non-positive size or
 * unknown version code are still counted in the totals so the panel does
 * not under-report. Pure JVM; no Android API dependency.
 */
public final class BackupArchiveSizeAggregator {

    /** Single backup archive record. */
    public static final class Archive {
        public final long versionCode;
        public final long sizeBytes;
        public final long backupTimeMillis;
        @Nullable
        public final String label;

        public Archive(long versionCode, long sizeBytes, long backupTimeMillis,
                       @Nullable String label) {
            this.versionCode = versionCode;
            this.sizeBytes = sizeBytes;
            this.backupTimeMillis = backupTimeMillis;
            this.label = label;
        }
    }

    public static final class Summary {
        public final long totalBytes;
        public final int archiveCount;
        @NonNull
        public final Map<Long, List<Archive>> byVersionCode;
        @Nullable
        public final Archive newestArchive;

        Summary(long totalBytes, int archiveCount,
                @NonNull Map<Long, List<Archive>> byVersionCode,
                @Nullable Archive newestArchive) {
            this.totalBytes = totalBytes;
            this.archiveCount = archiveCount;
            this.byVersionCode = Collections.unmodifiableMap(byVersionCode);
            this.newestArchive = newestArchive;
        }

        public boolean isEmpty() {
            return archiveCount == 0;
        }
    }

    private BackupArchiveSizeAggregator() {
    }

    @NonNull
    public static Summary aggregate(@Nullable List<Archive> archives) {
        if (archives == null || archives.isEmpty()) {
            return new Summary(0L, 0, new LinkedHashMap<>(), null);
        }
        long total = 0L;
        int count = 0;
        Archive newest = null;
        Map<Long, List<Archive>> buckets = new LinkedHashMap<>();
        for (Archive a : archives) {
            if (a == null) continue;
            ++count;
            if (a.sizeBytes > 0L) total += a.sizeBytes;
            List<Archive> bucket = buckets.get(a.versionCode);
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets.put(a.versionCode, bucket);
            }
            bucket.add(a);
            if (newest == null || a.backupTimeMillis > newest.backupTimeMillis) {
                newest = a;
            }
        }
        // Sort each bucket newest-first for the UI; ties broken by deterministic
        // descending size so the result is stable across runs.
        Comparator<Archive> newestFirst = (x, y) -> {
            int byTime = Long.compare(y.backupTimeMillis, x.backupTimeMillis);
            if (byTime != 0) return byTime;
            return Long.compare(y.sizeBytes, x.sizeBytes);
        };
        for (List<Archive> bucket : buckets.values()) {
            Collections.sort(bucket, newestFirst);
        }
        return new Summary(total, count, buckets, newest);
    }

    /**
     * Format a byte count for the sibling row. Uses base-1024 SI-style
     * units ({@code KB / MB / GB / TB}) so the value matches what the
     * Android Storage panel renders elsewhere. Returns {@code "0 B"} for
     * zero or negative inputs.
     */
    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes <= 0L) return "0 B";
        if (bytes < 1024L) return bytes + " B";
        final String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unitIndex = -1;
        do {
            value /= 1024.0;
            ++unitIndex;
        } while (value >= 1024.0 && unitIndex < units.length - 1);
        // One decimal place is enough for a storage hint.
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }
}
