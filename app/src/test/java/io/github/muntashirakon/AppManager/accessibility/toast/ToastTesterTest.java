// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class ToastTesterTest {
    private Application mApp;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void oneToastWithTheChosenLength() {
        ToastTester.show(mApp, "Short one", false);
        ShadowLooper.idleMainLooper();
        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("Short one", ShadowToast.getTextOfLatestToast());
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().getDuration());

        ToastTester.show(mApp, "Long one", true);
        ShadowLooper.idleMainLooper();
        assertEquals(2, ShadowToast.shownToastCount());
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
    }

    @Test
    public void aBackgroundThreadHandsTheToastToTheMainLooper() throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Thread> callers = new ArrayList<>();
        Executor background = command -> {
            Thread thread = new Thread(() -> {
                callers.add(Thread.currentThread());
                command.run();
            });
            threads.add(thread);
            thread.start();
        };

        ToastTester.showFromBackground(mApp, "From the background", false, background);
        for (Thread thread : threads) {
            thread.join(5_000L);
        }

        assertEquals(1, callers.size());
        assertNotSame(Looper.getMainLooper().getThread(), callers.get(0));
        // Nothing shows until the main looper runs what was posted to it.
        assertEquals(0, ShadowToast.shownToastCount());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("From the background", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aBurstIsThreeToastsWithin300Ms() {
        ToastTester.showBurst(mApp, "Burst", true, new Handler(Looper.getMainLooper()));

        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals(mApp.getString(R.string.toast_history_test_burst_item, "Burst", 1, 3),
                ShadowToast.getTextOfLatestToast());

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ToastTester.BURST_GAP_MILLIS));
        assertEquals(2, ShadowToast.shownToastCount());

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ToastTester.BURST_GAP_MILLIS));
        assertEquals(3, ShadowToast.shownToastCount());
        assertEquals(mApp.getString(R.string.toast_history_test_burst_item, "Burst", 3, 3),
                ShadowToast.getTextOfLatestToast());
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
        assertEquals(300L, ToastTester.BURST_GAP_MILLIS * (ToastTester.BURST_COUNT - 1));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(3, ShadowToast.shownToastCount());
    }
}
