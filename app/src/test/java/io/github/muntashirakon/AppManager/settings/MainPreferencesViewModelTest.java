// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class MainPreferencesViewModelTest {
    private MainPreferencesViewModel mViewModel;
    private TestLifecycleOwner mOwner;
    private final List<Integer> mStatuses = new ArrayList<>();

    @Before
    public void setUp() {
        mViewModel = new MainPreferencesViewModel(RuntimeEnvironment.getApplication());
        mOwner = new TestLifecycleOwner();
        mOwner.start();
        mViewModel.getModeOfOpsStatus().observe(mOwner, mStatuses::add);
    }

    @After
    public void tearDown() {
        mOwner.destroy();
        mViewModel.onCleared();
    }

    @Test
    public void aSecondModeRequestWhileOneRunsIsDropped() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();

        mViewModel.submitModeOperation(() -> {
            runs.incrementAndGet();
            started.countDown();
            await(release);
            return Ops.STATUS_SUCCESS;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(mViewModel.isModeOperationPending());

        // Interleaved, two requests tear down each other's server and services
        mViewModel.submitModeOperation(() -> {
            runs.incrementAndGet();
            return Ops.STATUS_FAILURE;
        });
        release.countDown();
        waitForStatuses(1);

        assertEquals(1, runs.get());
        assertEquals(Collections.singletonList(Ops.STATUS_SUCCESS), mStatuses);
        assertFalse(mViewModel.isModeOperationPending());
    }

    @Test
    public void theGuardIsClearedWhenTheStatusIsDelivered() throws Exception {
        List<Boolean> pendingWhenDelivered = new ArrayList<>();
        mViewModel.getModeOfOpsStatus().removeObservers(mOwner);
        mViewModel.getModeOfOpsStatus().observe(mOwner, status -> {
            mStatuses.add(status);
            pendingWhenDelivered.add(mViewModel.isModeOperationPending());
        });

        mViewModel.submitModeOperation(() -> Ops.STATUS_ADB_CONNECT_REQUIRED);
        waitForStatuses(1);

        // The screen sends its follow-up request (connect, pair) straight from the observer
        assertEquals(Collections.singletonList(false), pendingWhenDelivered);
    }

    @Test
    public void aThrowingRequestReportsFailureAndFreesTheGuard() throws Exception {
        mViewModel.submitModeOperation(() -> {
            throw new IllegalStateException("server went away");
        });
        waitForStatuses(1);

        assertEquals(Collections.singletonList(Ops.STATUS_FAILURE), mStatuses);
        assertFalse(mViewModel.isModeOperationPending());
    }

    @Test
    public void aRejectedRequestReportsFailureAndFreesTheGuard() throws Exception {
        mViewModel.onCleared();

        mViewModel.submitModeOperation(() -> Ops.STATUS_SUCCESS);
        waitForStatuses(1);

        assertEquals(Collections.singletonList(Ops.STATUS_FAILURE), mStatuses);
        assertFalse(mViewModel.isModeOperationPending());
    }

    @Test
    public void aDialogIsLostOnlyAfterTheScreenShowedIt() {
        mViewModel.onStatusReceived(Ops.STATUS_ADB_CONNECT_REQUIRED);
        idleMainLooper();
        // Not shown yet: the event itself still reaches the screen
        assertNull(mViewModel.getLostDialogStatus());

        mViewModel.onModeStatusDialogShown();
        assertEquals(Integer.valueOf(Ops.STATUS_ADB_CONNECT_REQUIRED), mViewModel.getLostDialogStatus());

        // Cancelling a dialog reports a failure, which answers it
        mViewModel.onStatusReceived(Ops.STATUS_FAILURE);
        idleMainLooper();
        assertNull(mViewModel.getLostDialogStatus());
    }

    @Test
    public void answeringADialogForgetsIt() throws Exception {
        mViewModel.onStatusReceived(Ops.STATUS_ADB_CONNECT_REQUIRED);
        idleMainLooper();
        mViewModel.onModeStatusDialogShown();

        // The user typed a port: a screen recreated during the connect must not ask again
        CountDownLatch release = new CountDownLatch(1);
        mViewModel.submitModeOperation(() -> {
            await(release);
            return Ops.STATUS_SUCCESS;
        });
        assertNull(mViewModel.getLostDialogStatus());
        release.countDown();
        waitForStatuses(2);
    }

    @Test
    public void aShizukuGrantConnectsThroughTheGuard() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        mViewModel.submitModeOperation(() -> {
            await(release);
            return Ops.STATUS_SUCCESS;
        });

        // Dropped: a second connect would tear down the first one's services
        mViewModel.connectShizuku(RuntimeEnvironment.getApplication());
        release.countDown();
        waitForStatuses(1);
        idleMainLooper();
        Thread.sleep(50);
        idleMainLooper();

        assertEquals(Collections.singletonList(Ops.STATUS_SUCCESS), mStatuses);
    }

    @Test
    public void aRecreatedScreenGetsNoReplayedStatus() {
        mViewModel.onStatusReceived(Ops.STATUS_SUCCESS);
        idleMainLooper();
        assertEquals(Collections.singletonList(Ops.STATUS_SUCCESS), mStatuses);

        List<Integer> recreated = new ArrayList<>();
        mOwner.destroy();
        mOwner = new TestLifecycleOwner();
        mOwner.start();
        mViewModel.getModeOfOpsStatus().observe(mOwner, recreated::add);
        idleMainLooper();

        assertTrue(recreated.isEmpty());
    }

    @Test
    public void theApplyStateOutlivesTheScreen() {
        // Rotating mid-switch must neither lose the switch nor roll the saved mode back
        ModeOfOpsApplyState state = mViewModel.getModeApplyState();
        assertTrue(state.begin(Ops.MODE_NO_ROOT, Ops.MODE_ADB_OVER_TCP));

        assertTrue(mViewModel.getModeApplyState().isApplying());
        assertEquals(Ops.MODE_ADB_OVER_TCP, mViewModel.getModeApplyState().getPendingMode());
    }

    private void waitForStatuses(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (mStatuses.size() < count && System.nanoTime() < deadline) {
            idleMainLooper();
            Thread.sleep(10);
        }
        assertEquals(count, mStatuses.size());
    }

    private static void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void await(@NonNull CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static class TestLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry mLifecycle = new LifecycleRegistry(this);

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return mLifecycle;
        }

        void start() {
            mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        }

        void destroy() {
            mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        }
    }
}
