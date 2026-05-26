// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BackupArchiveSizeAggregatorTest {

    private static BackupArchiveSizeAggregator.Archive a(long ver, long size, long ts,
                                                         String label) {
        return new BackupArchiveSizeAggregator.Archive(ver, size, ts, label);
    }

    @Test
    public void emptyOrNullInputsProduceEmptySummary() {
        BackupArchiveSizeAggregator.Summary none =
                BackupArchiveSizeAggregator.aggregate(null);
        assertEquals(0L, none.totalBytes);
        assertEquals(0, none.archiveCount);
        assertTrue(none.byVersionCode.isEmpty());
        assertNull(none.newestArchive);
        assertTrue(none.isEmpty());

        BackupArchiveSizeAggregator.Summary empty =
                BackupArchiveSizeAggregator.aggregate(new ArrayList<>());
        assertEquals(0L, empty.totalBytes);
        assertTrue(empty.isEmpty());
    }

    @Test
    public void aggregateSumsAllArchives() {
        List<BackupArchiveSizeAggregator.Archive> archives = Arrays.asList(
                a(100, 1_000L, 1L, "preinstall"),
                a(100, 2_000L, 2L, "auto"),
                a(101, 3_000L, 3L, "auto"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        assertEquals(6_000L, s.totalBytes);
        assertEquals(3, s.archiveCount);
        assertEquals(2, s.byVersionCode.size());
        assertNotNull(s.newestArchive);
        assertEquals(3L, s.newestArchive.backupTimeMillis);
        assertEquals(101L, s.newestArchive.versionCode);
    }

    @Test
    public void aggregateBucketsByVersionCode() {
        List<BackupArchiveSizeAggregator.Archive> archives = Arrays.asList(
                a(100, 1_000L, 1L, "a"),
                a(101, 2_000L, 3L, "b"),
                a(100, 1_500L, 5L, "c"),
                a(102, 9_000L, 4L, "d"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        Map<Long, List<BackupArchiveSizeAggregator.Archive>> map = s.byVersionCode;
        assertEquals(3, map.size());
        assertEquals(2, map.get(100L).size());
        assertEquals(1, map.get(101L).size());
        assertEquals(1, map.get(102L).size());
        // Each bucket newest-first: vc 100 has ts 1, 5 -> 5 then 1.
        assertEquals(5L, map.get(100L).get(0).backupTimeMillis);
        assertEquals(1L, map.get(100L).get(1).backupTimeMillis);
    }

    @Test
    public void tieOnTimestampIsBrokenByDescendingSize() {
        // Two archives with the same backupTime: deterministic newest-first
        // tie-break is descending size.
        List<BackupArchiveSizeAggregator.Archive> archives = Arrays.asList(
                a(100, 1_000L, 5L, "small"),
                a(100, 9_000L, 5L, "big"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        assertEquals(9_000L, s.byVersionCode.get(100L).get(0).sizeBytes);
        assertEquals(1_000L, s.byVersionCode.get(100L).get(1).sizeBytes);
    }

    @Test
    public void newestPicksHighestTimestampAcrossAllVersions() {
        List<BackupArchiveSizeAggregator.Archive> archives = Arrays.asList(
                a(100, 1_000L, 100L, "first"),
                a(101, 2_000L, 50L, "second"),
                a(102, 3_000L, 200L, "third"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        assertNotNull(s.newestArchive);
        assertEquals(200L, s.newestArchive.backupTimeMillis);
        assertEquals(102L, s.newestArchive.versionCode);
    }

    @Test
    public void zeroOrNegativeSizesDoNotContributeToTotalButStillCountInArchiveCount() {
        // An archive with unknown size (-1) still counts as a backup the
        // user owns; we just can't sum it. archiveCount must include it so
        // the panel header reads accurately.
        List<BackupArchiveSizeAggregator.Archive> archives = Arrays.asList(
                a(100, 1_000L, 1L, "a"),
                a(100, -1L, 2L, "b"),
                a(100, 0L, 3L, "c"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        assertEquals(1_000L, s.totalBytes);
        assertEquals(3, s.archiveCount);
    }

    @Test
    public void aggregateTolerantOfNullArchiveEntries() {
        List<BackupArchiveSizeAggregator.Archive> archives = new ArrayList<>();
        archives.add(null);
        archives.add(a(100, 5_000L, 1L, "a"));
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(archives);
        assertEquals(5_000L, s.totalBytes);
        assertEquals(1, s.archiveCount);
    }

    @Test
    public void byVersionCodeIsImmutable() {
        BackupArchiveSizeAggregator.Summary s =
                BackupArchiveSizeAggregator.aggregate(Collections.singletonList(
                        a(100, 1L, 1L, null)));
        try {
            s.byVersionCode.clear();
            // Some JDKs / hash impls may not throw immediately; just assert
            // mutation didn't happen by checking the map still has the data.
            assertEquals(1, s.byVersionCode.size());
        } catch (UnsupportedOperationException expected) {
            // Acceptable - map is unmodifiable.
        }
    }

    @Test
    public void formatBytesRendersSiUnits() {
        assertEquals("0 B", BackupArchiveSizeAggregator.formatBytes(0L));
        assertEquals("0 B", BackupArchiveSizeAggregator.formatBytes(-100L));
        assertEquals("512 B", BackupArchiveSizeAggregator.formatBytes(512L));
        assertEquals("1.0 KB", BackupArchiveSizeAggregator.formatBytes(1024L));
        assertEquals("1.5 KB", BackupArchiveSizeAggregator.formatBytes(1536L));
        assertEquals("1.0 MB", BackupArchiveSizeAggregator.formatBytes(1024L * 1024L));
        assertEquals("2.0 GB", BackupArchiveSizeAggregator.formatBytes(2L * 1024L * 1024L * 1024L));
    }
}
