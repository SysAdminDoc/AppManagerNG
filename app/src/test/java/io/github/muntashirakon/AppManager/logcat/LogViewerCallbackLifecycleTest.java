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

import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
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

    @Test
    public void endedReaderCannotTearDownItsInstalledReplacement() {
        TestListener listener = new TestListener();
        LogViewerViewModel.LogcatSession session = new LogViewerViewModel.LogcatSession(listener);
        TestReader oldReader = new TestReader();
        TestReader replacementReader = new TestReader();

        LogViewerViewModel.setSessionReader(session, oldReader);
        LogViewerViewModel.setSessionReader(session, replacementReader);

        assertEquals(true, LogViewerViewModel.continueWithReplacementReader(session, oldReader));
        assertEquals(true, LogViewerViewModel.isSessionActive(session));
        assertEquals(false, replacementReader.mKilled);
    }

    @Test
    public void currentReaderOwnsSessionShutdownAtomically() {
        TestListener listener = new TestListener();
        LogViewerViewModel.LogcatSession session = new LogViewerViewModel.LogcatSession(listener);
        TestReader reader = new TestReader();
        LogViewerViewModel.setSessionReader(session, reader);

        assertEquals(false, LogViewerViewModel.continueWithReplacementReader(session, reader));
        assertEquals(false, LogViewerViewModel.isSessionActive(session));
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

    private static final class TestReader implements LogcatReader {
        private boolean mKilled;

        @Override
        public String readLine() {
            return null;
        }

        @Override
        public void killQuietly() {
            mKilled = true;
        }

        @Override
        public boolean readyToRecord() {
            return true;
        }

        @Override
        public List<Process> getProcesses() {
            return Collections.emptyList();
        }
    }
}
