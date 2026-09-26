// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;

import io.github.muntashirakon.AppManager.misc.ScreenLockChecker;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

/** Stopping the freeze service must close its lock checker, or a queued check outlives it. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowCpuUtils.class)
public class FreezeUnfreezeServiceLifecycleTest {
    @Test
    public void destroyClosesTheCheckerAndHandsOutNoNewOne() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class,
                new Intent()).create().startCommand(0, 1);
        FreezeUnfreezeService service = controller.get();
        ScreenLockChecker checker = service.getScreenLockChecker();
        assertNotNull(checker);
        assertFalse(checker.isClosed());

        controller.destroy();

        assertTrue(checker.isClosed());
        assertNull("a screen event during teardown must not start a new checker", service.getScreenLockChecker());
    }

    @Test(timeout = 20_000)
    public void repeatedStartAndStopLeavesNoTimerThreads() throws InterruptedException {
        for (int i = 0; i < 20; ++i) {
            ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(
                    FreezeUnfreezeService.class, new Intent()).create().startCommand(0, i);
            assertNotNull(controller.get().getScreenLockChecker());
            controller.destroy();
        }

        assertEquals(0, remainingTimerThreads());
    }

    static int remainingTimerThreads() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        int count;
        do {
            count = 0;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (ScreenLockChecker.TIMER_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
                    ++count;
                }
            }
            if (count > 0) {
                Thread.sleep(20);
            }
        } while (count > 0 && System.currentTimeMillis() < deadline);
        return count;
    }
}
