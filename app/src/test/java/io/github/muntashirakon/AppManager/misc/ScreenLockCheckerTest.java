// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The freeze and session services created a checker whose timer they could not stop, so a check
 * queued before onDestroy could still call into a destroyed service. The clock-driven tests use a
 * scheduler the test advances by hand, so nothing depends on how long a real timer takes.
 */
@RunWith(RobolectricTestRunner.class)
public class ScreenLockCheckerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AtomicInteger mCallbacks = new AtomicInteger();

    @Before
    public void setUp() {
        mCallbacks.set(0);
    }

    @Test
    public void aClosedCheckerNeverRunsItsCallback() {
        setDevice(true, true, false);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet, new ManualClock());

        checker.checkLock();
        assertEquals("a locked secure device runs the callback while open", 1, mCallbacks.get());

        checker.close();
        checker.close();
        checker.checkLock();

        assertEquals(1, mCallbacks.get());
        assertTrue(checker.isClosed());
    }

    @Test
    public void aQueuedCheckRunsWhenItsDelayHasPassed() {
        // Positive control for the tests below: secure, unlocked and screen off queues a check one
        // second out, and that check runs the callback once the device has locked.
        ManualClock clock = new ManualClock();
        setDevice(true, false, false);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet, clock);

        checker.checkLock();
        setDevice(true, true, false);
        clock.advance(999);
        assertEquals(0, mCallbacks.get());
        clock.advance(1);

        assertEquals(1, mCallbacks.get());
    }

    @Test
    public void advancingTheClockAfterCloseRunsNoCallback() {
        ManualClock clock = new ManualClock();
        setDevice(true, false, false);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet, clock);
        checker.checkLock();

        checker.close();
        setDevice(true, true, false);
        clock.advance(TimeUnit.HOURS.toMillis(1));

        assertEquals(0, mCallbacks.get());
        assertTrue(clock.shutDown);
        assertEquals(0, clock.pending());
    }

    @Test
    public void aCheckRunningWhenCloseIsCalledStopsBeforeItsCallback() {
        // The queued check starts, then close() lands before it looks at the device: it must not
        // reach the callback even though the device is locked by then.
        ManualClock clock = new ManualClock();
        setDevice(true, false, false);
        ScreenLockChecker[] holder = new ScreenLockChecker[1];
        holder[0] = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet, clock);
        holder[0].checkLock();
        setDevice(true, true, false);

        clock.beforeEachTask = () -> holder[0].close();
        clock.advance(1_000);

        assertEquals(0, mCallbacks.get());
    }

    @Test(timeout = 10_000)
    public void closeWaitsForACallbackThatHasBegunAndNoneStartsAfterIt() throws InterruptedException {
        setDevice(true, true, false);
        CountDownLatch inCallback = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, () -> {
            mCallbacks.incrementAndGet();
            inCallback.countDown();
            awaitQuietly(release);
        }, new ManualClock());
        Thread screenEvent = new Thread(checker::checkLock);
        screenEvent.start();
        assertTrue(inCallback.await(5, TimeUnit.SECONDS));

        AtomicBoolean closeReturned = new AtomicBoolean();
        Thread closer = new Thread(() -> {
            checker.close();
            closeReturned.set(true);
        });
        closer.start();
        while (closer.getState() != Thread.State.BLOCKED && closer.isAlive()) {
            Thread.sleep(5);
        }
        assertFalse("close() must wait for the running callback", closeReturned.get());

        release.countDown();
        closer.join();
        screenEvent.join();
        checker.checkLock();

        assertTrue(closeReturned.get());
        assertEquals(1, mCallbacks.get());
    }

    @Test(timeout = 20_000)
    public void closeAndScreenEventsMayRaceWithoutACallbackAfterClose() throws InterruptedException {
        // Locked and secure, so every check that gets through calls back; none may do so once
        // close() has returned.
        setDevice(true, true, false);
        AtomicBoolean closeReturned = new AtomicBoolean();
        AtomicInteger lateCallbacks = new AtomicInteger();
        ScreenLockChecker checker = new ScreenLockChecker(mContext, () -> {
            mCallbacks.incrementAndGet();
            if (closeReturned.get()) {
                lateCallbacks.incrementAndGet();
            }
        }, new ManualClock());
        List<Throwable> failures = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            boolean closer = i == 0;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < 200; ++j) {
                        if (closer && j == 100) {
                            checker.close();
                            closeReturned.set(true);
                        } else {
                            checker.checkLock();
                        }
                    }
                } catch (Throwable th) {
                    synchronized (failures) {
                        failures.add(th);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(new ArrayList<Throwable>(), failures);
        assertTrue(checker.isClosed());
        assertTrue("the race should have let some checks call back first", mCallbacks.get() > 0);
        assertEquals(0, lateCallbacks.get());
    }

    @Test(timeout = 10_000)
    public void repeatedStartAndStopLeavesNoTimerThreads() throws InterruptedException {
        // This one needs the real timer: the leak it guards against is the timer's thread.
        setDevice(true, false, false);
        for (int i = 0; i < 50; ++i) {
            ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet);
            checker.checkLock();
            checker.close();
        }

        long deadline = System.currentTimeMillis() + 5_000;
        while (timerThreads() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, timerThreads());
    }

    private void setDevice(boolean secure, boolean locked, boolean interactive) {
        KeyguardManager keyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        shadowOf(keyguard).setIsKeyguardSecure(secure);
        shadowOf(keyguard).setKeyguardLocked(locked);
        shadowOf((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).setIsInteractive(interactive);
    }

    static int timerThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (ScreenLockChecker.TIMER_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
                ++count;
            }
        }
        return count;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A scheduler whose time only moves when the test calls {@link #advance}. */
    private static final class ManualClock implements ScreenLockChecker.Scheduler {
        private final List<long[]> mDue = new ArrayList<>();
        private final List<Runnable> mTasks = new ArrayList<>();
        private long mNow;
        boolean shutDown;
        Runnable beforeEachTask;

        @NonNull
        @Override
        public synchronized Runnable schedule(@NonNull Runnable task, long delayMillis) {
            long[] due = {mNow + delayMillis};
            mDue.add(due);
            mTasks.add(task);
            return () -> {
                synchronized (ManualClock.this) {
                    int index = mDue.indexOf(due);
                    if (index >= 0) {
                        mDue.remove(index);
                        mTasks.remove(index);
                    }
                }
            };
        }

        @Override
        public synchronized void shutdown() {
            shutDown = true;
            mDue.clear();
            mTasks.clear();
        }

        synchronized int pending() {
            return mTasks.size();
        }

        void advance(long millis) {
            long target;
            synchronized (this) {
                target = mNow + millis;
            }
            while (true) {
                Runnable task = null;
                synchronized (this) {
                    int next = -1;
                    for (int i = 0; i < mDue.size(); ++i) {
                        if (mDue.get(i)[0] <= target && (next < 0 || mDue.get(i)[0] < mDue.get(next)[0])) {
                            next = i;
                        }
                    }
                    if (next < 0) {
                        mNow = target;
                        return;
                    }
                    mNow = mDue.remove(next)[0];
                    task = mTasks.remove(next);
                }
                if (beforeEachTask != null) {
                    beforeEachTask.run();
                }
                task.run();
            }
        }
    }
}
