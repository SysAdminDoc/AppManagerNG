// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The freeze and session services created a checker whose timer they could not stop, so a check
 * queued before onDestroy could still call into a destroyed service.
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
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet);

        checker.checkLock();
        assertEquals("a locked secure device runs the callback while open", 1, mCallbacks.get());

        checker.close();
        checker.close();
        checker.checkLock();

        assertEquals(1, mCallbacks.get());
        assertTrue(checker.isClosed());
    }

    @Test(timeout = 10_000)
    public void closingCancelsTheQueuedCheck() throws InterruptedException {
        // Secure, unlocked and screen off: the first check is queued one second out.
        setDevice(true, false, false);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet);
        checker.checkLock();

        checker.close();
        // Had the queued check survived, it would find the device locked and run the callback.
        setDevice(true, true, false);
        Thread.sleep(1_500);

        assertEquals(0, mCallbacks.get());
    }

    @Test(timeout = 10_000)
    public void repeatedStartAndStopLeavesNoTimerThreads() throws InterruptedException {
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

    @Test(timeout = 10_000)
    public void closeAndScreenEventsMayRace() throws InterruptedException {
        setDevice(true, false, false);
        ScreenLockChecker checker = new ScreenLockChecker(mContext, mCallbacks::incrementAndGet);
        List<Throwable> failures = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            boolean closer = i % 2 == 0;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; ++j) {
                        if (closer && j == 50) {
                            checker.close();
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
    }

    private void setDevice(boolean secure, boolean locked, boolean interactive) {
        KeyguardManager keyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        shadowOf(keyguard).setIsKeyguardSecure(secure);
        shadowOf(keyguard).setKeyguardLocked(locked);
        shadowOf((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).setIsInteractive(interactive);
    }

    private static int timerThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (ScreenLockChecker.TIMER_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
                ++count;
            }
        }
        return count;
    }
}
