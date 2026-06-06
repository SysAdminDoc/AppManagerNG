// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SecurityAndOpsViewModelTest {
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
    public void stageUpdatePublishesObservableState() {
        SecurityAndOpsViewModel viewModel = newViewModel();
        long attemptId = viewModel.beginStartupInitAttempt(Ops.MODE_ADB_OVER_TCP, 100, 600);

        viewModel.postStartupInitStage(attemptId, StartupInitState.Stage.ADB_SERVER_RESTART, "restart");

        assertEquals(StartupInitState.Stage.ADB_SERVER_RESTART,
                viewModel.getStartupInitStateSnapshot().getStage());
        assertEquals(StartupInitState.Stage.ADB_SERVER_RESTART,
                viewModel.startupInitState().getValue().getStage());
    }

    private static SecurityAndOpsViewModel newViewModel() {
        Application application = RuntimeEnvironment.getApplication();
        return new SecurityAndOpsViewModel(application);
    }
}
