// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UndoableActionQueueTest {

    private static final class FixedClock implements UndoableActionQueue.Clock {
        long now;

        @Override
        public long currentTimeMillis() {
            return now;
        }
    }

    @Test
    public void deferStoresAction() {
        FixedClock clock = new FixedClock();
        clock.now = 100;
        UndoableActionQueue q = new UndoableActionQueue(clock);
        int handle = q.defer("Freeze com.foo", () -> { /* no-op */ }, 5_000);
        assertEquals(1, q.size());
        UndoableActionQueue.Entry e = q.peek(handle);
        assertEquals(5_100L, e.expiresAtMillis);
        assertEquals("Freeze com.foo", e.label);
    }

    @Test
    public void cancelRemovesPendingAndPreventsCommit() {
        UndoableActionQueue q = new UndoableActionQueue();
        AtomicInteger commits = new AtomicInteger();
        int h = q.defer("op", commits::incrementAndGet, 0, 5_000);
        assertTrue(q.cancel(h));
        // Polling past the deadline must not surface a cancelled entry.
        List<UndoableActionQueue.Entry> expired = q.pollExpired(10_000);
        assertTrue(expired.isEmpty());
        // Re-cancelling a vanished handle is a no-op false.
        assertFalse(q.cancel(h));
        // The commit Runnable never ran.
        assertEquals(0, commits.get());
    }

    @Test
    public void pollExpiredDrainsOnlyElapsedEntries() {
        UndoableActionQueue q = new UndoableActionQueue();
        int a = q.defer("op-a", () -> {}, 100, 5_000);   // expires at 5100
        int b = q.defer("op-b", () -> {}, 100, 1_000);   // expires at 1100
        int c = q.defer("op-c", () -> {}, 100, 10_000);  // expires at 10100
        assertEquals(3, q.size());

        List<UndoableActionQueue.Entry> firstDrain = q.pollExpired(2_000);
        assertEquals(1, firstDrain.size());
        assertEquals(b, firstDrain.get(0).handle);
        assertEquals(2, q.size());

        List<UndoableActionQueue.Entry> secondDrain = q.pollExpired(6_000);
        assertEquals(1, secondDrain.size());
        assertEquals(a, secondDrain.get(0).handle);
        assertEquals(1, q.size());

        List<UndoableActionQueue.Entry> thirdDrain = q.pollExpired(20_000);
        assertEquals(1, thirdDrain.size());
        assertEquals(c, thirdDrain.get(0).handle);
        assertEquals(0, q.size());
    }

    @Test
    public void pollExpiredReturnsInsertionOrder() {
        // When multiple entries share the same deadline the iteration order
        // must be deterministic so a heartbeat that drains them commits in
        // the order the user kicked them off.
        UndoableActionQueue q = new UndoableActionQueue();
        int first = q.defer("first", () -> {}, 0, 1_000);
        int second = q.defer("second", () -> {}, 0, 1_000);
        int third = q.defer("third", () -> {}, 0, 1_000);
        List<UndoableActionQueue.Entry> drained = q.pollExpired(5_000);
        assertEquals(3, drained.size());
        assertEquals(first, drained.get(0).handle);
        assertEquals(second, drained.get(1).handle);
        assertEquals(third, drained.get(2).handle);
    }

    @Test
    public void drainAllReturnsEveryEntryRegardlessOfDeadline() {
        UndoableActionQueue q = new UndoableActionQueue();
        q.defer("op-a", () -> {}, 100, 5_000);
        q.defer("op-b", () -> {}, 100, 10_000);
        List<UndoableActionQueue.Entry> all = q.drainAll();
        assertEquals(2, all.size());
        assertEquals(0, q.size());
    }

    @Test
    public void negativeDelayClampsToImmediateExpiration() {
        UndoableActionQueue q = new UndoableActionQueue();
        int h = q.defer("op", () -> {}, 100, -42);
        // Negative input is clamped to 0; expiresAt == nowMillis, so polling
        // at the same instant must drain the entry.
        List<UndoableActionQueue.Entry> drained = q.pollExpired(100);
        assertEquals(1, drained.size());
        assertEquals(h, drained.get(0).handle);
    }

    @Test
    public void cancelOnDrainedEntryReturnsFalse() {
        UndoableActionQueue q = new UndoableActionQueue();
        int h = q.defer("op", () -> {}, 100, 0);
        q.pollExpired(200);  // drains the entry
        assertFalse(q.cancel(h));
    }

    @Test
    public void peekReturnsNullForUnknownHandle() {
        UndoableActionQueue q = new UndoableActionQueue();
        assertNull(q.peek(42));
    }

    @Test
    public void handlesAreUnique() {
        UndoableActionQueue q = new UndoableActionQueue();
        int a = q.defer("a", () -> {}, 0, 1_000);
        int b = q.defer("b", () -> {}, 0, 1_000);
        int c = q.defer("c", () -> {}, 0, 1_000);
        assertTrue(a != b);
        assertTrue(b != c);
        assertTrue(a != c);
    }

    @Test
    public void emptyQueueDrainsCleanly() {
        UndoableActionQueue q = new UndoableActionQueue();
        assertTrue(q.pollExpired(System.currentTimeMillis()).isEmpty());
        assertTrue(q.drainAll().isEmpty());
        assertEquals(0, q.size());
    }
}
