// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class StartupInitUiStateTest {
    @Test
    public void migrationStateShowsProgressWithoutRecoveryActions() {
        StartupInitState state = StartupInitState.startAttempt(1, Ops.MODE_ADB_WIFI, 100, 600);

        StartupInitUiState uiState = StartupInitUiState.from(state);

        assertEquals(R.string.startup_init_migration, uiState.getMessageRes());
        assertTrue(uiState.isProgressVisible());
        assertTrue(uiState.getActions().isEmpty());
    }

    @Test
    public void localNetworkPermissionActionIsPrioritized() {
        StartupInitState state = StartupInitState.startAttempt(2, Ops.MODE_ADB_WIFI, 100, 600)
                .statusReceived(2, Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED, "missing permission");

        StartupInitUiState uiState = StartupInitUiState.from(state);

        assertEquals(R.string.startup_init_local_network_permission_required, uiState.getMessageRes());
        assertTrue(uiState.isProgressVisible());
        assertEquals(StartupInitState.RecoveryAction.REQUEST_LOCAL_NETWORK_PERMISSION,
                uiState.getActions().get(0).getAction());
        assertEquals(R.string.startup_recovery_request_local_network_permission,
                uiState.getActions().get(0).getLabelRes());
        assertEquals(R.drawable.ic_wifi, uiState.getActions().get(0).getIconRes());
        assertEquals(StartupInitState.RecoveryAction.RETRY, uiState.getActions().get(1).getAction());
    }

    @Test
    public void pairingStateShowsCancelBeforeGenericRecovery() {
        StartupInitState state = StartupInitState.startAttempt(3, Ops.MODE_ADB_WIFI, 100, 600)
                .statusReceived(3, Ops.STATUS_ADB_PAIRING_REQUIRED, "pairing");

        StartupInitUiState uiState = StartupInitUiState.from(state);

        assertEquals(R.string.startup_init_adb_pairing_wait, uiState.getMessageRes());
        assertEquals(StartupInitState.RecoveryAction.CANCEL_PAIRING,
                uiState.getActions().get(0).getAction());
        assertEquals(R.string.startup_recovery_cancel_pairing,
                uiState.getActions().get(0).getLabelRes());
    }

    @Test
    public void timeoutUsesTerminalMessageAndGenericRecoveryOrder() {
        StartupInitState state = StartupInitState.startAttempt(4, Ops.MODE_SHIZUKU, 100, 600)
                .stage(4, StartupInitState.Stage.SHIZUKU_SERVICE_BIND, "binding")
                .timeout(4, 700, "timeout");

        StartupInitUiState uiState = StartupInitUiState.from(state);

        assertEquals(R.string.startup_init_timed_out, uiState.getMessageRes());
        assertFalse(uiState.isProgressVisible());
        assertEquals(StartupInitState.RecoveryAction.RETRY, uiState.getActions().get(0).getAction());
        assertEquals(StartupInitState.RecoveryAction.CHOOSE_MODE, uiState.getActions().get(1).getAction());
        assertEquals(StartupInitState.RecoveryAction.MODE_DOCTOR, uiState.getActions().get(2).getAction());
        assertEquals(StartupInitState.RecoveryAction.SUPPORT_BUNDLE, uiState.getActions().get(3).getAction());
    }

    @Test
    public void failedIncompleteUsbDebuggingKeepsSpecificMessage() {
        StartupInitState state = StartupInitState.startAttempt(5, Ops.MODE_ADB_OVER_TCP, 100, 600)
                .statusReceived(5, Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS, "need perms");

        StartupInitUiState uiState = StartupInitUiState.from(state);

        assertEquals(R.string.startup_init_incomplete_usb_debugging, uiState.getMessageRes());
        assertFalse(uiState.isProgressVisible());
    }
}
