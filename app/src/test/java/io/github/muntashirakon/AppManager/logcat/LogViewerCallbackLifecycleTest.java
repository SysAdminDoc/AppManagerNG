// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logcat.struct.LogLine;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 36})
@LooperMode(LooperMode.Mode.PAUSED)
public class LogViewerCallbackLifecycleTest {
    @Test
    public void queuedDeliveryRechecksListenerAfterDetach() {
        TestListener listener = new TestListener();
        WeakReference<LogViewerViewModel.LogLinesAvailableInterface> reference = new WeakReference<>(listener);

        LogViewerViewModel.sendNewLogs(Collections.emptyList(), reference);
        reference.clear();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, listener.mDeliveryCount);
    }

    @Test
    public void queuedDeliveryRechecksViewLifecycle() {
        TestListener listener = new TestListener();
        WeakReference<LogViewerViewModel.LogLinesAvailableInterface> reference = new WeakReference<>(listener);

        LogViewerViewModel.sendNewLogs(Collections.emptyList(), reference);
        listener.mActive = false;
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, listener.mDeliveryCount);
    }

    @Test
    public void activeViewReceivesQueuedDelivery() {
        TestListener listener = new TestListener();

        LogViewerViewModel.sendNewLogs(Collections.emptyList(), new WeakReference<>(listener));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, listener.mDeliveryCount);
    }

    private static final class TestListener implements LogViewerViewModel.LogLinesAvailableInterface {
        private boolean mActive = true;
        private int mDeliveryCount;

        @Override
        public void onNewLogsAvailable(@NonNull List<LogLine> logLines) {
            ++mDeliveryCount;
        }

        @Override
        public boolean isLogViewActive() {
            return mActive;
        }
    }
}
