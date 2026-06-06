// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class StartupInitState {
    public enum Status {
        IDLE,
        RUNNING,
        TIMED_OUT,
        CANCELLED,
        SUCCEEDED,
        FAILED
    }

    public enum Stage {
        IDLE,
        MIGRATION,
        OPS_INIT,
        AUTO_DETECT,
        ROOT_SERVICE_BIND,
        SHIZUKU_PERMISSION_REQUIRED,
        SHIZUKU_SERVICE_BIND,
        WIRELESS_ADB_PORT_DISCOVERY,
        WIRELESS_DEBUGGING_CHOOSER_REQUIRED,
        ADB_CONNECT_REQUIRED,
        ADB_SERVER_RESTART,
        ADB_SERVICE_BIND,
        ADB_PAIRING_WAIT,
        LOCAL_NETWORK_PERMISSION_REQUIRED,
        INCOMPLETE_USB_DEBUGGING,
        NO_ROOT,
        SUCCESS,
        FAILURE
    }

    public enum RecoveryAction {
        RETRY,
        CHOOSE_MODE,
        MODE_DOCTOR,
        SUPPORT_BUNDLE,
        REQUEST_LOCAL_NETWORK_PERMISSION,
        REQUEST_SHIZUKU_PERMISSION,
        CANCEL_PAIRING
    }

    private static final int NO_OPS_STATUS = Integer.MIN_VALUE;

    private final long mAttemptId;
    @Nullable
    @Ops.Mode
    private final String mConfiguredMode;
    @NonNull
    private final Status mStatus;
    @NonNull
    private final Stage mStage;
    private final long mStartedAtMillis;
    private final long mDeadlineAtMillis;
    private final int mOpsStatus;
    @Nullable
    private final String mDetail;
    @NonNull
    private final EnumSet<RecoveryAction> mRecoveryActions;

    private StartupInitState(long attemptId, @Nullable @Ops.Mode String configuredMode,
                             @NonNull Status status, @NonNull Stage stage,
                             long startedAtMillis, long deadlineAtMillis, int opsStatus,
                             @Nullable String detail,
                             @NonNull EnumSet<RecoveryAction> recoveryActions) {
        mAttemptId = attemptId;
        mConfiguredMode = configuredMode;
        mStatus = Objects.requireNonNull(status);
        mStage = Objects.requireNonNull(stage);
        mStartedAtMillis = startedAtMillis;
        mDeadlineAtMillis = deadlineAtMillis;
        mOpsStatus = opsStatus;
        mDetail = detail;
        mRecoveryActions = copyActions(recoveryActions);
    }

    @NonNull
    static StartupInitState idle() {
        return new StartupInitState(0, null, Status.IDLE, Stage.IDLE, 0, 0,
                NO_OPS_STATUS, null, noActions());
    }

    @NonNull
    static StartupInitState startAttempt(long attemptId, @NonNull @Ops.Mode String configuredMode,
                                         long startedAtMillis, long deadlineAtMillis) {
        if (attemptId <= 0) {
            throw new IllegalArgumentException("attemptId must be positive.");
        }
        if (deadlineAtMillis < startedAtMillis) {
            throw new IllegalArgumentException("deadline must not be before start.");
        }
        return new StartupInitState(attemptId, configuredMode, Status.RUNNING, Stage.MIGRATION,
                startedAtMillis, deadlineAtMillis, NO_OPS_STATUS, null, noActions());
    }

    @NonNull
    StartupInitState stage(long attemptId, @NonNull Stage stage, @Nullable String detail) {
        if (!accepts(attemptId)) {
            return this;
        }
        return new StartupInitState(mAttemptId, mConfiguredMode, Status.RUNNING, stage,
                mStartedAtMillis, mDeadlineAtMillis, NO_OPS_STATUS, detail, noActions());
    }

    @NonNull
    StartupInitState statusReceived(long attemptId, int opsStatus, @Nullable String detail) {
        if (!accepts(attemptId)) {
            return this;
        }
        switch (opsStatus) {
            case Ops.STATUS_SUCCESS:
                return terminal(Status.SUCCEEDED, Stage.SUCCESS, opsStatus, detail, noActions());
            case Ops.STATUS_FAILURE:
                return terminal(Status.FAILED, Stage.FAILURE, opsStatus, detail, genericRecoveryActions());
            case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                return running(Stage.WIRELESS_ADB_PORT_DISCOVERY, opsStatus, detail, noActions());
            case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                return running(Stage.WIRELESS_DEBUGGING_CHOOSER_REQUIRED, opsStatus, detail,
                        genericRecoveryActions());
            case Ops.STATUS_ADB_CONNECT_REQUIRED:
                return running(Stage.ADB_CONNECT_REQUIRED, opsStatus, detail, genericRecoveryActions());
            case Ops.STATUS_ADB_PAIRING_REQUIRED:
                return running(Stage.ADB_PAIRING_WAIT, opsStatus, detail,
                        withGenericRecovery(RecoveryAction.CANCEL_PAIRING));
            case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                return terminal(Status.FAILED, Stage.INCOMPLETE_USB_DEBUGGING, opsStatus, detail,
                        genericRecoveryActions());
            case Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED:
                return running(Stage.SHIZUKU_PERMISSION_REQUIRED, opsStatus, detail,
                        withGenericRecovery(RecoveryAction.REQUEST_SHIZUKU_PERMISSION));
            case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                return running(Stage.LOCAL_NETWORK_PERMISSION_REQUIRED, opsStatus, detail,
                        withGenericRecovery(RecoveryAction.REQUEST_LOCAL_NETWORK_PERMISSION));
            default:
                return terminal(Status.FAILED, Stage.FAILURE, opsStatus, detail, genericRecoveryActions());
        }
    }

    @NonNull
    StartupInitState timeout(long attemptId, long nowMillis, @Nullable String detail) {
        if (!accepts(attemptId)) {
            return this;
        }
        String timeoutDetail = detail != null ? detail : "Timed out at " + nowMillis + ".";
        return new StartupInitState(mAttemptId, mConfiguredMode, Status.TIMED_OUT, mStage,
                mStartedAtMillis, mDeadlineAtMillis, mOpsStatus, timeoutDetail, genericRecoveryActions());
    }

    @NonNull
    StartupInitState cancel(long attemptId, @Nullable String detail) {
        if (!accepts(attemptId)) {
            return this;
        }
        return new StartupInitState(mAttemptId, mConfiguredMode, Status.CANCELLED, mStage,
                mStartedAtMillis, mDeadlineAtMillis, mOpsStatus, detail, withGenericRecovery(RecoveryAction.RETRY));
    }

    @NonNull
    StartupInitState retry(long newAttemptId, long startedAtMillis, long deadlineAtMillis) {
        if (mConfiguredMode == null) {
            throw new IllegalStateException("Cannot retry without a configured mode.");
        }
        return startAttempt(newAttemptId, mConfiguredMode, startedAtMillis, deadlineAtMillis);
    }

    boolean accepts(long attemptId) {
        return mStatus == Status.RUNNING && mAttemptId == attemptId;
    }

    public boolean isTerminal() {
        return mStatus == Status.SUCCEEDED || mStatus == Status.FAILED
                || mStatus == Status.TIMED_OUT || mStatus == Status.CANCELLED;
    }

    public long getAttemptId() {
        return mAttemptId;
    }

    @Nullable
    @Ops.Mode
    public String getConfiguredMode() {
        return mConfiguredMode;
    }

    @NonNull
    public Status getStatus() {
        return mStatus;
    }

    @NonNull
    public Stage getStage() {
        return mStage;
    }

    public long getStartedAtMillis() {
        return mStartedAtMillis;
    }

    public long getDeadlineAtMillis() {
        return mDeadlineAtMillis;
    }

    public int getOpsStatus() {
        return mOpsStatus;
    }

    @Nullable
    public String getDetail() {
        return mDetail;
    }

    @NonNull
    public Set<RecoveryAction> getRecoveryActions() {
        return Collections.unmodifiableSet(mRecoveryActions);
    }

    @NonNull
    private StartupInitState running(@NonNull Stage stage, int opsStatus, @Nullable String detail,
                                     @NonNull EnumSet<RecoveryAction> recoveryActions) {
        return new StartupInitState(mAttemptId, mConfiguredMode, Status.RUNNING, stage,
                mStartedAtMillis, mDeadlineAtMillis, opsStatus, detail, recoveryActions);
    }

    @NonNull
    private StartupInitState terminal(@NonNull Status status, @NonNull Stage stage, int opsStatus,
                                      @Nullable String detail,
                                      @NonNull EnumSet<RecoveryAction> recoveryActions) {
        return new StartupInitState(mAttemptId, mConfiguredMode, status, stage,
                mStartedAtMillis, mDeadlineAtMillis, opsStatus, detail, recoveryActions);
    }

    @NonNull
    private static EnumSet<RecoveryAction> genericRecoveryActions() {
        return EnumSet.of(RecoveryAction.RETRY, RecoveryAction.CHOOSE_MODE,
                RecoveryAction.MODE_DOCTOR, RecoveryAction.SUPPORT_BUNDLE);
    }

    @NonNull
    private static EnumSet<RecoveryAction> withGenericRecovery(@NonNull RecoveryAction action) {
        EnumSet<RecoveryAction> actions = genericRecoveryActions();
        actions.add(action);
        return actions;
    }

    @NonNull
    private static EnumSet<RecoveryAction> noActions() {
        return EnumSet.noneOf(RecoveryAction.class);
    }

    @NonNull
    private static EnumSet<RecoveryAction> copyActions(@NonNull EnumSet<RecoveryAction> actions) {
        return actions.isEmpty() ? noActions() : EnumSet.copyOf(actions);
    }
}
