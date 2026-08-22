// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AccessibilityActionSequencerTest {
    @Test
    public void enabledImmediatelyRunsWithoutDelay() {
        FakeScheduler scheduler = new FakeScheduler();
        AccessibilityActionSequencer sequencer = new AccessibilityActionSequencer(Runnable::run, scheduler);
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean timedOut = new AtomicBoolean();

        sequencer.runWhenReady(() -> checks.incrementAndGet() == 1, 10, 500,
                () -> timedOut.set(true));

        assertEquals(1, checks.get());
        assertEquals(0, scheduler.elapsedMillis());
        assertFalse(timedOut.get());
        assertTrue(scheduler.isEmpty());
    }

    @Test
    public void enabledLateRetriesUntilReady() {
        FakeScheduler scheduler = new FakeScheduler();
        AccessibilityActionSequencer sequencer = new AccessibilityActionSequencer(Runnable::run, scheduler);
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean timedOut = new AtomicBoolean();

        sequencer.runWhenReady(() -> checks.incrementAndGet() == 3, 10, 500,
                () -> timedOut.set(true));
        scheduler.runNext();
        scheduler.runNext();

        assertEquals(3, checks.get());
        assertEquals(1_000, scheduler.elapsedMillis());
        assertFalse(timedOut.get());
        assertTrue(scheduler.isEmpty());
    }

    @Test
    public void neverEnabledStopsAtBoundedAttemptCount() {
        FakeScheduler scheduler = new FakeScheduler();
        AccessibilityActionSequencer sequencer = new AccessibilityActionSequencer(Runnable::run, scheduler);
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean timedOut = new AtomicBoolean();

        sequencer.runWhenReady(() -> {
            checks.incrementAndGet();
            return false;
        }, 18, 500, () -> timedOut.set(true));
        scheduler.runAll();

        assertEquals(18, checks.get());
        assertEquals(8_500, scheduler.elapsedMillis());
        assertTrue(timedOut.get());
        assertTrue(scheduler.isEmpty());
    }

    @Test
    public void cancellationDropsQueuedRetries() {
        FakeScheduler scheduler = new FakeScheduler();
        AccessibilityActionSequencer sequencer = new AccessibilityActionSequencer(Runnable::run, scheduler);
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean timedOut = new AtomicBoolean();

        sequencer.runWhenReady(() -> {
            checks.incrementAndGet();
            return false;
        }, 18, 500, () -> timedOut.set(true));
        sequencer.cancelPending();
        scheduler.runAll();

        assertEquals(1, checks.get());
        assertFalse(timedOut.get());
        assertTrue(scheduler.isEmpty());
    }

    private static final class FakeScheduler implements AccessibilityActionSequencer.Scheduler {
        private final Queue<Runnable> mActions = new ArrayDeque<>();
        private long mElapsedMillis;

        @Override
        public void schedule(Runnable action, long delayMillis) {
            mActions.add(() -> {
                mElapsedMillis += delayMillis;
                action.run();
            });
        }

        @Override
        public void shutdownNow() {
            mActions.clear();
        }

        void runNext() {
            mActions.remove().run();
        }

        void runAll() {
            while (!mActions.isEmpty()) {
                runNext();
            }
        }

        boolean isEmpty() {
            return mActions.isEmpty();
        }

        long elapsedMillis() {
            return mElapsedMillis;
        }
    }
}
