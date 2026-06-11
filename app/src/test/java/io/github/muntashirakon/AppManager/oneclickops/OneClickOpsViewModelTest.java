// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class OneClickOpsViewModelTest {
    @Test
    public void setCurrentTaskCancelsPreviousTask() {
        OneClickOpsViewModel viewModel = new OneClickOpsViewModel(ApplicationProvider.getApplicationContext());
        TestFuture first = new TestFuture();
        TestFuture second = new TestFuture();

        viewModel.setCurrentTask(first);
        viewModel.setCurrentTask(second);

        assertTrue(first.cancelled);
        assertFalse(second.cancelled);
        assertTrue(viewModel.hasCurrentTask());
    }

    @Test
    public void cancelCurrentTaskCancelsAndClearsTask() {
        OneClickOpsViewModel viewModel = new OneClickOpsViewModel(ApplicationProvider.getApplicationContext());
        TestFuture future = new TestFuture();

        viewModel.setCurrentTask(future);
        viewModel.cancelCurrentTask();

        assertTrue(future.cancelled);
        assertFalse(viewModel.hasCurrentTask());
    }

    private static class TestFuture implements Future<Object> {
        private boolean cancelled;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
