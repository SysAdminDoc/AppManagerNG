// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;

public final class StartupInitUiState {
    private static final StartupInitState.RecoveryAction[] ACTION_ORDER = new StartupInitState.RecoveryAction[]{
            StartupInitState.RecoveryAction.REQUEST_LOCAL_NETWORK_PERMISSION,
            StartupInitState.RecoveryAction.REQUEST_SHIZUKU_PERMISSION,
            StartupInitState.RecoveryAction.CANCEL_PAIRING,
            StartupInitState.RecoveryAction.RETRY,
            StartupInitState.RecoveryAction.CHOOSE_MODE,
            StartupInitState.RecoveryAction.MODE_DOCTOR,
            StartupInitState.RecoveryAction.SUPPORT_BUNDLE,
    };

    @StringRes
    private final int mMessageRes;
    private final boolean mProgressVisible;
    @NonNull
    private final List<Action> mActions;

    private StartupInitUiState(@StringRes int messageRes, boolean progressVisible,
                               @NonNull List<Action> actions) {
        mMessageRes = messageRes;
        mProgressVisible = progressVisible;
        mActions = Collections.unmodifiableList(new ArrayList<>(actions));
    }

    @NonNull
    public static StartupInitUiState from(@NonNull StartupInitState state) {
        List<Action> actions = new ArrayList<>();
        Set<StartupInitState.RecoveryAction> recoveryActions = state.getRecoveryActions();
        for (StartupInitState.RecoveryAction action : ACTION_ORDER) {
            if (recoveryActions.contains(action)) {
                actions.add(Action.from(action));
            }
        }
        boolean progressVisible = state.getStatus() == StartupInitState.Status.IDLE
                || state.getStatus() == StartupInitState.Status.RUNNING;
        return new StartupInitUiState(getMessageRes(state), progressVisible, actions);
    }

    @StringRes
    public int getMessageRes() {
        return mMessageRes;
    }

    public boolean isProgressVisible() {
        return mProgressVisible;
    }

    @NonNull
    public List<Action> getActions() {
        return mActions;
    }

    @StringRes
    private static int getMessageRes(@NonNull StartupInitState state) {
        switch (state.getStatus()) {
            case TIMED_OUT:
                return R.string.startup_init_timed_out;
            case CANCELLED:
                return R.string.startup_init_cancelled;
            case SUCCEEDED:
                return R.string.startup_init_succeeded;
            case FAILED:
                if (state.getStage() == StartupInitState.Stage.INCOMPLETE_USB_DEBUGGING) {
                    return R.string.startup_init_incomplete_usb_debugging;
                }
                if (state.getStage() == StartupInitState.Stage.NO_ROOT) {
                    return R.string.startup_init_no_root;
                }
                return R.string.startup_init_failed;
            case RUNNING:
            case IDLE:
            default:
                return getStageMessageRes(state.getStage());
        }
    }

    @StringRes
    private static int getStageMessageRes(@NonNull StartupInitState.Stage stage) {
        switch (stage) {
            case MIGRATION:
                return R.string.startup_init_migration;
            case OPS_INIT:
                return R.string.startup_init_ops_init;
            case AUTO_DETECT:
                return R.string.startup_init_auto_detect;
            case ROOT_SERVICE_BIND:
                return R.string.startup_init_root_service_bind;
            case SHIZUKU_PERMISSION_REQUIRED:
                return R.string.startup_init_shizuku_permission_required;
            case SHIZUKU_SERVICE_BIND:
                return R.string.startup_init_shizuku_service_bind;
            case WIRELESS_ADB_PORT_DISCOVERY:
                return R.string.startup_init_wireless_port_discovery;
            case WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                return R.string.startup_init_wireless_debugging_chooser;
            case ADB_CONNECT_REQUIRED:
                return R.string.startup_init_adb_connect_required;
            case ADB_SERVER_RESTART:
                return R.string.startup_init_adb_server_restart;
            case ADB_SERVICE_BIND:
                return R.string.startup_init_adb_service_bind;
            case ADB_PAIRING_WAIT:
                return R.string.startup_init_adb_pairing_wait;
            case LOCAL_NETWORK_PERMISSION_REQUIRED:
                return R.string.startup_init_local_network_permission_required;
            case INCOMPLETE_USB_DEBUGGING:
                return R.string.startup_init_incomplete_usb_debugging;
            case NO_ROOT:
                return R.string.startup_init_no_root;
            case SUCCESS:
                return R.string.startup_init_succeeded;
            case FAILURE:
                return R.string.startup_init_failed;
            case IDLE:
            default:
                return R.string.authenticating;
        }
    }

    public static final class Action {
        @NonNull
        private final StartupInitState.RecoveryAction mAction;
        @StringRes
        private final int mLabelRes;
        @DrawableRes
        private final int mIconRes;

        private Action(@NonNull StartupInitState.RecoveryAction action, @StringRes int labelRes,
                       @DrawableRes int iconRes) {
            mAction = action;
            mLabelRes = labelRes;
            mIconRes = iconRes;
        }

        @NonNull
        private static Action from(@NonNull StartupInitState.RecoveryAction action) {
            switch (action) {
                case REQUEST_LOCAL_NETWORK_PERMISSION:
                    return new Action(action, R.string.startup_recovery_request_local_network_permission,
                            R.drawable.ic_wifi);
                case REQUEST_SHIZUKU_PERMISSION:
                    return new Action(action, R.string.startup_recovery_request_shizuku_permission,
                            R.drawable.ic_security);
                case CANCEL_PAIRING:
                    return new Action(action, R.string.startup_recovery_cancel_pairing,
                            R.drawable.ic_close);
                case CHOOSE_MODE:
                    return new Action(action, R.string.pref_mode_of_operations, R.drawable.ic_tune);
                case MODE_DOCTOR:
                    return new Action(action, R.string.privilege_health_mode_doctor_title,
                            R.drawable.ic_security_network);
                case SUPPORT_BUNDLE:
                    return new Action(action, R.string.pref_support_info_bundle,
                            R.drawable.ic_hammer_wrench);
                case RETRY:
                default:
                    return new Action(action, R.string.startup_recovery_retry, R.drawable.ic_refresh);
            }
        }

        @NonNull
        public StartupInitState.RecoveryAction getAction() {
            return mAction;
        }

        @StringRes
        public int getLabelRes() {
            return mLabelRes;
        }

        @DrawableRes
        public int getIconRes() {
            return mIconRes;
        }
    }
}
