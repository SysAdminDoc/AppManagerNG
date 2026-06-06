// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StartupInitStateTest {
    @Test
    public void startAttemptCreatesRunningMigrationState() {
        StartupInitState state = StartupInitState.startAttempt(1, Ops.MODE_ADB_WIFI, 100, 600);

        assertEquals(1, state.getAttemptId());
        assertEquals(Ops.MODE_ADB_WIFI, state.getConfiguredMode());
        assertEquals(StartupInitState.Status.RUNNING, state.getStatus());
        assertEquals(StartupInitState.Stage.MIGRATION, state.getStage());
        assertEquals(100, state.getStartedAtMillis());
        assertEquals(600, state.getDeadlineAtMillis());
        assertFalse(state.isTerminal());
    }

    @Test
    public void staleStageEventIsIgnored() {
        StartupInitState state = StartupInitState.startAttempt(2, Ops.MODE_ROOT, 100, 600);

        StartupInitState afterStaleEvent = state.stage(1, StartupInitState.Stage.ROOT_SERVICE_BIND, "old");

        assertSame(state, afterStaleEvent);
        assertEquals(StartupInitState.Stage.MIGRATION, afterStaleEvent.getStage());
    }

    @Test
    public void timeoutIsTerminalAndKeepsRecoveryActions() {
        StartupInitState state = StartupInitState.startAttempt(3, Ops.MODE_SHIZUKU, 100, 600)
                .stage(3, StartupInitState.Stage.SHIZUKU_SERVICE_BIND, "binding");

        StartupInitState timedOut = state.timeout(3, 700, "Shizuku bind timed out.");

        assertEquals(StartupInitState.Status.TIMED_OUT, timedOut.getStatus());
        assertEquals(StartupInitState.Stage.SHIZUKU_SERVICE_BIND, timedOut.getStage());
        assertTrue(timedOut.isTerminal());
        assertTrue(timedOut.getRecoveryActions().contains(StartupInitState.RecoveryAction.RETRY));
        assertTrue(timedOut.getRecoveryActions().contains(StartupInitState.RecoveryAction.CHOOSE_MODE));
        assertTrue(timedOut.getRecoveryActions().contains(StartupInitState.RecoveryAction.MODE_DOCTOR));
        assertTrue(timedOut.getRecoveryActions().contains(StartupInitState.RecoveryAction.SUPPORT_BUNDLE));
    }

    @Test
    public void retryCreatesNewAttemptAndStaleFailureIsIgnored() {
        StartupInitState timedOut = StartupInitState.startAttempt(4, Ops.MODE_ADB_OVER_TCP, 100, 600)
                .timeout(4, 700, "ADB timed out.");

        StartupInitState retry = timedOut.retry(5, 800, 1400);
        StartupInitState afterStaleFailure = retry.statusReceived(4, Ops.STATUS_FAILURE, "old failure");

        assertEquals(5, retry.getAttemptId());
        assertEquals(StartupInitState.Status.RUNNING, retry.getStatus());
        assertEquals(StartupInitState.Stage.MIGRATION, retry.getStage());
        assertSame(retry, afterStaleFailure);
    }

    @Test
    public void localNetworkPermissionStatusHasPermissionRecovery() {
        StartupInitState state = StartupInitState.startAttempt(6, Ops.MODE_ADB_WIFI, 100, 600);

        StartupInitState blocked = state.statusReceived(6, Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED,
                "permission missing");

        assertEquals(StartupInitState.Status.RUNNING, blocked.getStatus());
        assertEquals(StartupInitState.Stage.LOCAL_NETWORK_PERMISSION_REQUIRED, blocked.getStage());
        assertEquals(Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED, blocked.getOpsStatus());
        assertTrue(blocked.getRecoveryActions()
                .contains(StartupInitState.RecoveryAction.REQUEST_LOCAL_NETWORK_PERMISSION));
        assertTrue(blocked.getRecoveryActions().contains(StartupInitState.RecoveryAction.MODE_DOCTOR));
    }

    @Test
    public void shizukuPermissionStatusHasShizukuRecovery() {
        StartupInitState state = StartupInitState.startAttempt(7, Ops.MODE_SHIZUKU, 100, 600);

        StartupInitState blocked = state.statusReceived(7, Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED,
                "permission missing");

        assertEquals(StartupInitState.Status.RUNNING, blocked.getStatus());
        assertEquals(StartupInitState.Stage.SHIZUKU_PERMISSION_REQUIRED, blocked.getStage());
        assertTrue(blocked.getRecoveryActions()
                .contains(StartupInitState.RecoveryAction.REQUEST_SHIZUKU_PERMISSION));
        assertTrue(blocked.getRecoveryActions().contains(StartupInitState.RecoveryAction.RETRY));
    }

    @Test
    public void pairingRequiredCanBeCancelled() {
        StartupInitState state = StartupInitState.startAttempt(8, Ops.MODE_ADB_WIFI, 100, 600);

        StartupInitState pairing = state.statusReceived(8, Ops.STATUS_ADB_PAIRING_REQUIRED, "pairing");
        StartupInitState cancelled = pairing.cancel(8, "user cancelled");

        assertEquals(StartupInitState.Stage.ADB_PAIRING_WAIT, pairing.getStage());
        assertTrue(pairing.getRecoveryActions().contains(StartupInitState.RecoveryAction.CANCEL_PAIRING));
        assertEquals(StartupInitState.Status.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.isTerminal());
    }

    @Test
    public void successIsTerminalAndHasNoRecoveryActions() {
        StartupInitState state = StartupInitState.startAttempt(9, Ops.MODE_NO_ROOT, 100, 600)
                .stage(9, StartupInitState.Stage.OPS_INIT, "ops");

        StartupInitState success = state.statusReceived(9, Ops.STATUS_SUCCESS, "ok");

        assertEquals(StartupInitState.Status.SUCCEEDED, success.getStatus());
        assertEquals(StartupInitState.Stage.SUCCESS, success.getStage());
        assertTrue(success.isTerminal());
        assertTrue(success.getRecoveryActions().isEmpty());
    }
}
