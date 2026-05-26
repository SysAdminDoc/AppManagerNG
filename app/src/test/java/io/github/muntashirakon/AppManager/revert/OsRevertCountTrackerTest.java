// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OsRevertCountTrackerTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long ONE_DAY = 24L * 60L * 60L * 1000L;

    @Test
    public void emptyTrackerCountsZero() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        assertEquals(0, t.countRecent("com.example", T0,
                OsRevertCountTracker.DEFAULT_TTL_MILLIS));
        assertEquals(0, t.trackedPackageCount());
    }

    @Test
    public void recordedRevertCountsInsideTtl() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.example", T0 - ONE_DAY);
        t.recordRevert("com.example", T0 - 2L * ONE_DAY);
        assertEquals(2, t.countRecent("com.example", T0,
                OsRevertCountTracker.DEFAULT_TTL_MILLIS));
    }

    @Test
    public void recordedRevertOutsideTtlIsIgnored() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.example", T0 - 30L * ONE_DAY);   // 30d old
        t.recordRevert("com.example", T0 - 2L * ONE_DAY);    // 2d old
        // TTL = 7 days
        assertEquals(1, t.countRecent("com.example", T0, 7L * ONE_DAY));
    }

    @Test
    public void zeroOrNegativeTtlYieldsZero() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.example", T0);
        assertEquals(0, t.countRecent("com.example", T0, 0L));
        assertEquals(0, t.countRecent("com.example", T0, -1L));
    }

    @Test
    public void evictExpiredDropsStaleEventsAndPackages() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.a", T0 - 30L * ONE_DAY);
        t.recordRevert("com.a", T0 - 2L * ONE_DAY);
        t.recordRevert("com.b", T0 - 30L * ONE_DAY);

        int removed = t.evictExpired(T0, 7L * ONE_DAY);
        // Two stale events (com.a stale + com.b stale) are dropped; com.b
        // empties out entirely so the package row is removed too.
        assertEquals(2, removed);
        assertEquals(1, t.trackedPackageCount());
        assertEquals(1, t.totalEventCount());
        assertEquals(1, t.countRecent("com.a", T0, 7L * ONE_DAY));
        assertEquals(0, t.countRecent("com.b", T0, 7L * ONE_DAY));
    }

    @Test
    public void perPackageCountIsIsolated() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.a", T0);
        t.recordRevert("com.a", T0);
        t.recordRevert("com.b", T0);

        assertEquals(2, t.countRecent("com.a", T0, ONE_DAY));
        assertEquals(1, t.countRecent("com.b", T0, ONE_DAY));
        assertEquals(0, t.countRecent("com.unrelated", T0, ONE_DAY));
    }

    @Test
    public void perPackageEventCapTrimsOldest() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        long ts = T0;
        for (int i = 0; i < OsRevertCountTracker.MAX_EVENTS_PER_PACKAGE + 50; ++i) {
            t.recordRevert("com.flood", ts++);
        }
        // Per-package list is capped to MAX_EVENTS_PER_PACKAGE; oldest are
        // dropped first so the most recent events remain.
        int recent = t.countRecent("com.flood", ts, Long.MAX_VALUE / 2);
        assertEquals(OsRevertCountTracker.MAX_EVENTS_PER_PACKAGE, recent);
    }

    @Test
    public void emptyPackageNameIsIgnored() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("", T0);
        assertEquals(0, t.trackedPackageCount());
    }

    @Test
    public void trackedPackagesSnapshotIsImmutable() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.a", T0);
        t.recordRevert("com.b", T0);
        try {
            t.trackedPackages().add("intruder");
            assertEquals(2, t.trackedPackageCount());
        } catch (UnsupportedOperationException ignored) {
            // Acceptable: returned list is unmodifiable.
        }
    }

    @Test
    public void clearResetsEverything() {
        OsRevertCountTracker t = new OsRevertCountTracker();
        t.recordRevert("com.a", T0);
        t.recordRevert("com.b", T0);
        t.clear();
        assertEquals(0, t.trackedPackageCount());
        assertEquals(0, t.totalEventCount());
    }

    @Test
    public void concurrentRecordsAreSerialisedSafely() throws InterruptedException {
        OsRevertCountTracker t = new OsRevertCountTracker();
        int threadCount = 8;
        int eventsPerThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        try {
            for (int i = 0; i < threadCount; ++i) {
                final int threadIdx = i;
                pool.submit(() -> {
                    long ts = T0;
                    for (int j = 0; j < eventsPerThread; ++j) {
                        t.recordRevert("com.t" + threadIdx, ts + j);
                    }
                    latch.countDown();
                });
            }
            assertTrue(latch.await(5L, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2L, TimeUnit.SECONDS);
        }
        // Each thread should have exactly MAX_EVENTS_PER_PACKAGE entries after
        // the cap kicks in (eventsPerThread > MAX). Sum across all threads is
        // deterministic.
        int expectedPerThread = Math.min(eventsPerThread, OsRevertCountTracker.MAX_EVENTS_PER_PACKAGE);
        assertEquals(threadCount, t.trackedPackageCount());
        assertEquals(threadCount * expectedPerThread, t.totalEventCount());
    }
}
