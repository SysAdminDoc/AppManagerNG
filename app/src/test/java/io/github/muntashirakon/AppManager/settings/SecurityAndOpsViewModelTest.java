// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class SecurityAndOpsViewModelTest {
    private SecurityAndOpsViewModel mViewModel;

    @After
    public void tearDown() {
        if (mViewModel != null) {
            mViewModel.onCleared();
        }
    }

    @Test
    public void currentAttemptStatusUpdatesStartupStateAndLegacyStatus() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long attemptId = viewModel.beginStartupInitAttempt(Ops.MODE_ADB_WIFI, 100, 600);

        viewModel.postStartupInitStatus(attemptId, Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED,
                "permission missing");

        StartupInitState state = viewModel.getStartupInitStateSnapshot();
        assertEquals(StartupInitState.Stage.LOCAL_NETWORK_PERMISSION_REQUIRED, state.getStage());
        assertEquals(Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED, state.getOpsStatus());
        assertEquals(Integer.valueOf(Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED),
                viewModel.authenticationStatus().getValue());
        assertTrue(state.getRecoveryActions()
                .contains(StartupInitState.RecoveryAction.REQUEST_LOCAL_NETWORK_PERMISSION));
    }

    @Test
    public void staleStatusDoesNotUpdateStartupStateOrLegacyStatus() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        viewModel.beginStartupInitAttempt(Ops.MODE_ROOT, 100, 600);

        viewModel.postStartupInitStatus(42, Ops.STATUS_SUCCESS, "old success");

        assertEquals(StartupInitState.Stage.MIGRATION, viewModel.getStartupInitStateSnapshot().getStage());
        assertNull(viewModel.authenticationStatus().getValue());
    }

    @Test
    public void timeoutThenRetryRejectsOldSuccess() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long firstAttempt = viewModel.beginStartupInitAttempt(Ops.MODE_SHIZUKU, 100, 600);
        viewModel.timeoutStartupInitAttempt(firstAttempt, 700, "timeout");

        StartupInitState retry = viewModel.retryStartupInitAttempt(800, 1400);
        viewModel.postStartupInitStatus(firstAttempt, Ops.STATUS_SUCCESS, "old success");

        assertEquals(StartupInitState.Status.RUNNING, viewModel.getStartupInitStateSnapshot().getStatus());
        assertEquals(retry.getAttemptId(), viewModel.getStartupInitStateSnapshot().getAttemptId());
        assertSame(retry, viewModel.getStartupInitStateSnapshot());
        assertNull(viewModel.authenticationStatus().getValue());
    }

    @Test
    public void publicTimeoutMarksCurrentAttemptTimedOut() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long attemptId = viewModel.beginStartupInitAttempt(Ops.MODE_SHIZUKU, 100, 600);

        viewModel.timeoutStartupInitAttempt(attemptId, "timeout");

        StartupInitState state = viewModel.getStartupInitStateSnapshot();
        assertEquals(StartupInitState.Status.TIMED_OUT, state.getStatus());
        assertEquals("timeout", state.getDetail());
        assertTrue(state.getRecoveryActions().contains(StartupInitState.RecoveryAction.RETRY));
    }

    @Test
    public void publicCancelMarksCurrentAttemptCancelled() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long attemptId = viewModel.beginStartupInitAttempt(Ops.MODE_ADB_WIFI, 100, 600);

        viewModel.cancelStartupInitAttempt(attemptId, "cancelled");

        StartupInitState state = viewModel.getStartupInitStateSnapshot();
        assertEquals(StartupInitState.Status.CANCELLED, state.getStatus());
        assertEquals("cancelled", state.getDetail());
        assertTrue(state.getRecoveryActions().contains(StartupInitState.RecoveryAction.RETRY));
    }

    @Test
    public void stageUpdatePublishesObservableState() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long attemptId = viewModel.beginStartupInitAttempt(Ops.MODE_ADB_OVER_TCP, 100, 600);

        viewModel.postStartupInitStage(attemptId, StartupInitState.Stage.ADB_SERVER_RESTART, "restart");

        assertEquals(StartupInitState.Stage.ADB_SERVER_RESTART,
                viewModel.getStartupInitStateSnapshot().getStage());
        assertEquals(StartupInitState.Stage.ADB_SERVER_RESTART,
                viewModel.startupInitState().getValue().getStage());
    }

    @Test
    public void keyStorePasswordProbeSurvivesObserverReplacementAndRunsOnce() throws Exception {
        Application application = RuntimeEnvironment.getApplication();
        CountDownLatch probeCompleted = new CountDownLatch(1);
        AtomicInteger probeCount = new AtomicInteger();
        mViewModel = new SecurityAndOpsViewModel(application, () -> {
            probeCount.incrementAndGet();
            probeCompleted.countDown();
            return true;
        });

        mViewModel.checkKeyStorePassword();
        mViewModel.checkKeyStorePassword();
        assertTrue(probeCompleted.await(5, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (mViewModel.keyStorePasswordStatus().getValue() == null
                && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }

        assertEquals(Boolean.TRUE, mViewModel.keyStorePasswordStatus().getValue());
        assertEquals(Boolean.TRUE, mViewModel.claimKeyStorePasswordStatus());
        assertNull(mViewModel.claimKeyStorePasswordStatus());
        assertEquals(1, probeCount.get());
    }

    @Test
    public void statusesAreNotReplayedToARecreatedActivity() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        List<Integer> statuses = Arrays.asList(
                Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING,
                Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED,
                Ops.STATUS_ADB_PAIRING_REQUIRED,
                Ops.STATUS_ADB_CONNECT_REQUIRED,
                Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS,
                Ops.STATUS_FAILURE,
                Ops.STATUS_SUCCESS);
        List<Integer> first = new ArrayList<>();
        TestLifecycleOwner firstOwner = new TestLifecycleOwner();
        firstOwner.start();
        viewModel.authenticationStatus().observe(firstOwner, first::add);
        for (int status : statuses) {
            viewModel.onStatusReceived(status);
            idleMainLooper();
        }
        firstOwner.destroy();

        // A replay would reopen a dialog, retry ADB or run the terminal step twice
        List<Integer> recreated = new ArrayList<>();
        TestLifecycleOwner recreatedOwner = new TestLifecycleOwner();
        recreatedOwner.start();
        viewModel.authenticationStatus().observe(recreatedOwner, recreated::add);
        idleMainLooper();

        assertEquals(statuses, first);
        assertTrue(recreated.isEmpty());
        recreatedOwner.destroy();
    }

    @Test
    public void aStatusPublishedWithNoActivityIsDeliveredOnce() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        viewModel.onStatusReceived(Ops.STATUS_SUCCESS);
        idleMainLooper();

        List<Integer> recreated = new ArrayList<>();
        TestLifecycleOwner owner = new TestLifecycleOwner();
        owner.start();
        viewModel.authenticationStatus().observe(owner, recreated::add);
        owner.destroy();
        TestLifecycleOwner again = new TestLifecycleOwner();
        again.start();
        viewModel.authenticationStatus().observe(again, recreated::add);
        again.destroy();

        assertEquals(Arrays.asList(Ops.STATUS_SUCCESS), recreated);
    }

    @Test
    public void aShownDialogStatusIsKeptForARecreatedActivityUntilAnswered() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        viewModel.onStatusReceived(Ops.STATUS_ADB_PAIRING_REQUIRED);
        idleMainLooper();
        assertNull(viewModel.getLostDialogStatus());

        viewModel.onStatusDialogShown();
        assertEquals(Integer.valueOf(Ops.STATUS_ADB_PAIRING_REQUIRED), viewModel.getLostDialogStatus());

        viewModel.onStatusReceived(Ops.STATUS_SUCCESS);
        idleMainLooper();
        assertNull(viewModel.getLostDialogStatus());
    }

    @Test
    public void aStatusFromAWorkerThreadArrivesOnTheMainThread() throws Exception {
        SecurityAndOpsViewModel viewModel = newViewModel();
        List<Integer> received = new ArrayList<>();
        TestLifecycleOwner owner = new TestLifecycleOwner();
        owner.start();
        viewModel.authenticationStatus().observe(owner, received::add);

        Thread worker = new Thread(() -> {
            viewModel.onStatusReceived(Ops.STATUS_ADB_CONNECT_REQUIRED);
            viewModel.onStatusReceived(Ops.STATUS_FAILURE);
        });
        worker.start();
        worker.join(5_000);
        idleMainLooper();

        // Posting both keeps the dialog request; postValue() used to drop all but the last one
        assertEquals(Arrays.asList(Ops.STATUS_ADB_CONNECT_REQUIRED, Ops.STATUS_FAILURE), received);
        owner.destroy();
    }

    private static void idleMainLooper() {
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
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

    private SecurityAndOpsViewModel newViewModel() {
        Application application = RuntimeEnvironment.getApplication();
        mViewModel = new SecurityAndOpsViewModel(application);
        return mViewModel;
    }
}
