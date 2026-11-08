// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.ipc.HeldBroadcasts;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Lets an automation app start or stop the privileged server and choose the mode. It's off until
 * the user turns it on, every call has to carry the authorization key, and the work goes through
 * the same {@link Ops} transition lock as the settings screen. The caller reads the result code of
 * an ordered broadcast or, if it asked for one, a result broadcast. The schema is
 * {@link ServerControlRequest}.
 * <p>
 * Nobody is there to answer a dialog, so a status that wants a person ends as a failure with a
 * toast saying so, and the settings screen's dialog tracking stays out of it.
 */
public class ServerControlReceiver extends BroadcastReceiver {
    private static final String TAG = ServerControlReceiver.class.getSimpleName();

    /** Android gives a foreground broadcast 10 seconds and a background one 60. */
    private static final long FOREGROUND_DEADLINE_MILLIS = 8_000;
    private static final long BACKGROUND_DEADLINE_MILLIS = 30_000;
    /** Any app can send these, so a stream of bad keys gets one row and one toast per interval. */
    private static final long REJECTION_REPORT_INTERVAL_MILLIS = 5_000;
    private static final long NEVER_REPORTED = Long.MIN_VALUE;

    @VisibleForTesting
    interface Operations {
        @Ops.Status
        int start(@NonNull Context context);

        @Ops.Status
        int switchMode(@NonNull Context context, @NonNull String mode);

        @Ops.Status
        int stop(@NonNull Context context);

        boolean isModeApplyInProgress();

        boolean isServerRunning();
    }

    private static final Operations OPS_OPERATIONS = new Operations() {
        @Override
        public int start(@NonNull Context context) {
            return Ops.startUnattended(context);
        }

        @Override
        public int switchMode(@NonNull Context context, @NonNull String mode) {
            return Ops.switchModeUnattended(context, mode);
        }

        @Override
        public int stop(@NonNull Context context) {
            return Ops.stopServerUnattended(context);
        }

        @Override
        public boolean isModeApplyInProgress() {
            return Ops.isModeApplyInProgress();
        }

        @Override
        public boolean isServerRunning() {
            return LocalServices.alive();
        }
    };

    private static volatile Operations sOperations = OPS_OPERATIONS;
    private static final AtomicBoolean sRunning = new AtomicBoolean();
    private static final AtomicLong sLastRejectionReport = new AtomicLong(NEVER_REPORTED);

    @VisibleForTesting
    static void setOperationsForTest(@Nullable Operations operations) {
        sOperations = operations != null ? operations : OPS_OPERATIONS;
    }

    @VisibleForTesting
    static void resetRejectionLimitForTest() {
        sLastRejectionReport.set(NEVER_REPORTED);
    }

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (!Prefs.AppActions.isServerControlBroadcastsEnabled()) {
            // Off means off: no toast, no history row, no work for a caller to set off
            answer(ServerControlRequest.RESULT_DISABLED);
            return;
        }
        Context appContext = context.getApplicationContext();
        if (intent == null) {
            answer(ServerControlRequest.RESULT_BAD_REQUEST);
            return;
        }
        String suppliedKey;
        ServerControlRequest request;
        try {
            suppliedKey = intent.getStringExtra(BaseActivity.EXTRA_AUTH);
            request = ServerControlRequest.from(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Refused a server control broadcast whose extras can't be read.");
            answer(ServerControlRequest.RESULT_BAD_REQUEST);
            reportRejection(appContext, ServerControlRequest.unreadable(intent.getAction()),
                    ServerControlRequest.RESULT_BAD_REQUEST);
            return;
        }
        if (!ServerControlRequest.isAuthorized(AuthManager.getKey(), suppliedKey)) {
            Log.w(TAG, "Refused a server control broadcast with a wrong or missing key.");
            answer(ServerControlRequest.RESULT_BAD_AUTH);
            reportRejection(appContext, request, ServerControlRequest.RESULT_BAD_AUTH);
            return;
        }
        if (!request.valid || !isModeAvailableHere(appContext, request.mode)) {
            answer(ServerControlRequest.RESULT_BAD_REQUEST);
            reportInBackground(appContext, request, ServerControlRequest.RESULT_BAD_REQUEST,
                    ServerControlRequest.OPS_STATUS_NONE);
            return;
        }
        Operations operations = sOperations;
        if (operations.isModeApplyInProgress() || !sRunning.compareAndSet(false, true)) {
            answer(ServerControlRequest.RESULT_BUSY);
            reportInBackground(appContext, request, ServerControlRequest.RESULT_BUSY,
                    ServerControlRequest.OPS_STATUS_NONE);
            return;
        }
        run(appContext, intent, request, operations);
    }

    /**
     * The settings screen doesn't offer wireless debugging below Android 11 or on a TV, so neither
     * does this.
     */
    private static boolean isModeAvailableHere(@NonNull Context context, @Nullable String mode) {
        return !Ops.MODE_ADB_WIFI.equals(mode)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Utils.isTv(context));
    }

    private void answer(int result) {
        if (isOrderedBroadcast()) {
            setResultCode(result);
        }
    }

    private void run(@NonNull Context context, @NonNull Intent intent, @NonNull ServerControlRequest request,
                     @NonNull Operations operations) {
        boolean ordered = isOrderedBroadcast();
        PendingResult pendingResult = goAsync();
        AtomicBoolean finished = new AtomicBoolean();
        Handler handler = ThreadUtils.getUiThreadHandler();
        // Starting a server can take longer than Android waits for a receiver. The caller then hears
        // "still running" in time and the result broadcast, the toast and the history have the rest.
        Runnable deadline = () -> finish(pendingResult, ordered, finished, ServerControlRequest.RESULT_STILL_RUNNING);
        handler.postDelayed(deadline, (intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0
                ? FOREGROUND_DEADLINE_MILLIS : BACKGROUND_DEADLINE_MILLIS);
        // Starting the privileged services waits for their binder, which comes by broadcast and
        // can't reach this process while this one is held, so that wait answers early too
        HeldBroadcasts.hold(deadline);
        try {
            ThreadUtils.postOnBackgroundThread(() -> {
                int opsStatus = Ops.STATUS_FAILURE;
                int result;
                try {
                    opsStatus = perform(context, request, operations);
                    result = resultFor(request, opsStatus, operations);
                } catch (RuntimeException e) {
                    Log.e(TAG, "A server control command failed.", e);
                    result = ServerControlRequest.resultCodeFor(Ops.STATUS_FAILURE);
                } finally {
                    sRunning.set(false);
                }
                HeldBroadcasts.done(deadline);
                handler.removeCallbacks(deadline);
                // The history row goes first: finishing is what lets Android freeze this process
                report(context, request, result, opsStatus);
                finish(pendingResult, ordered, finished, result);
            });
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Could not queue a server control command.", e);
            sRunning.set(false);
            HeldBroadcasts.done(deadline);
            handler.removeCallbacks(deadline);
            int result = ServerControlRequest.resultCodeFor(Ops.STATUS_FAILURE);
            reportInBackground(context, request, result, Ops.STATUS_FAILURE);
            finish(pendingResult, ordered, finished, result);
        }
    }

    private static void finish(@NonNull PendingResult pendingResult, boolean ordered,
                               @NonNull AtomicBoolean finished, int result) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        if (ordered) {
            pendingResult.setResultCode(result);
        }
        pendingResult.finish();
    }

    @WorkerThread
    @Ops.Status
    private static int perform(@NonNull Context context, @NonNull ServerControlRequest request,
                               @NonNull Operations operations) {
        switch (request.command) {
            case ServerControlRequest.COMMAND_START:
                return operations.start(context);
            case ServerControlRequest.COMMAND_STOP:
                return operations.stop(context);
            case ServerControlRequest.COMMAND_SET_MODE:
                // A valid SET_MODE request always has one
                return operations.switchMode(context, request.mode != null ? request.mode : Ops.getMode());
            default:
                return Ops.STATUS_FAILURE;
        }
    }

    private static int resultFor(@NonNull ServerControlRequest request, @Ops.Status int opsStatus,
                                 @NonNull Operations operations) {
        if (opsStatus == Ops.STATUS_SUCCESS && ServerControlRequest.COMMAND_START.equals(request.command)
                && !operations.isServerRunning()) {
            return ServerControlRequest.RESULT_NO_SERVER;
        }
        return ServerControlRequest.resultCodeFor(opsStatus);
    }

    /**
     * A call without the right key gets no result broadcast, that's for callers who proved who they
     * are. Its row and toast are rate limited, and what the row says comes from the fixed schema.
     */
    private static void reportRejection(@NonNull Context context, @NonNull ServerControlRequest request, int result) {
        long now = SystemClock.elapsedRealtime();
        long last = sLastRejectionReport.get();
        if (last != NEVER_REPORTED && now - last < REJECTION_REPORT_INTERVAL_MILLIS) {
            return;
        }
        if (!sLastRejectionReport.compareAndSet(last, now)) {
            return;
        }
        toast(context, request, result, ServerControlRequest.OPS_STATUS_NONE);
        postHistory(context, request, result, ServerControlRequest.OPS_STATUS_NONE);
    }

    private static void reportInBackground(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                                           int opsStatus) {
        sendResultBroadcast(context, request, result, opsStatus);
        toast(context, request, result, opsStatus);
        postHistory(context, request, result, opsStatus);
    }

    @WorkerThread
    private static void report(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                               int opsStatus) {
        sendResultBroadcast(context, request, result, opsStatus);
        toast(context, request, result, opsStatus);
        recordHistory(context, request, result, opsStatus);
    }

    private static void sendResultBroadcast(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                                            int opsStatus) {
        if (request.resultPackage == null) {
            return;
        }
        // Only the validated package name comes from the caller. The action and every extra are ours.
        Intent answer = new Intent(ServerControlRequest.ACTION_RESULT)
                .setPackage(request.resultPackage)
                .putExtra(ServerControlRequest.EXTRA_RESULT_CODE, result)
                .putExtra(ServerControlRequest.EXTRA_COMMAND, request.command)
                .putExtra(ServerControlRequest.EXTRA_MODE, Ops.getMode())
                .putExtra(ServerControlRequest.EXTRA_UID, Ops.getWorkingUid())
                .putExtra(ServerControlRequest.EXTRA_OPS_STATUS, opsStatus);
        if (request.requestId != null) {
            answer.putExtra(ServerControlRequest.EXTRA_REQUEST_ID, request.requestId);
        }
        context.sendBroadcast(answer);
    }

    private static void toast(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                              int opsStatus) {
        String message = messageFor(context, request, result, opsStatus);
        if (message != null) {
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(message));
        }
    }

    @Nullable
    private static String messageFor(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                                     int opsStatus) {
        switch (result) {
            case ServerControlRequest.RESULT_SUCCESS:
                switch (request.command) {
                    case ServerControlRequest.COMMAND_START:
                        return context.getString(R.string.server_control_started);
                    case ServerControlRequest.COMMAND_STOP:
                        return context.getString(R.string.server_control_stopped);
                    default:
                        return context.getString(R.string.server_control_mode_set, request.mode);
                }
            case ServerControlRequest.RESULT_NO_SERVER:
                return context.getString(R.string.server_control_no_server);
            case ServerControlRequest.RESULT_BAD_AUTH:
                return context.getString(R.string.server_control_bad_key);
            case ServerControlRequest.RESULT_BAD_REQUEST:
                return context.getString(R.string.server_control_bad_request);
            case ServerControlRequest.RESULT_BUSY:
                return context.getString(R.string.server_control_busy);
            default:
                break;
        }
        switch (opsStatus) {
            case Ops.STATUS_FAILURE_SERVER_START:
            case Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE:
            case Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED:
                // Ops has already said which of these it was
                return null;
            case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
            case Ops.STATUS_ADB_PAIRING_REQUIRED:
            case Ops.STATUS_ADB_CONNECT_REQUIRED:
            case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
            case Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED:
            case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                return context.getString(R.string.server_control_needs_you);
            default:
                return context.getString(R.string.server_control_failed);
        }
    }

    private static void postHistory(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                                    int opsStatus) {
        try {
            ThreadUtils.postOnBackgroundThread(() -> recordHistory(context, request, result, opsStatus));
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Could not record a server control broadcast.", e);
        }
    }

    /**
     * Holds the command, the mode and the outcome. The key and the other extras never get here.
     */
    @WorkerThread
    private static void recordHistory(@NonNull Context context, @NonNull ServerControlRequest request, int result,
                                      int opsStatus) {
        try {
            boolean success = result == ServerControlRequest.RESULT_SUCCESS;
            String detail = "command=" + request.command + " mode=" + request.mode + " result=" + result
                    + " ops_status=" + opsStatus;
            SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(
                    SingleAppActionHistoryItem.ACTION_SERVER_CONTROL,
                    context.getString(R.string.server_control_history_label),
                    BuildConfig.APPLICATION_ID,
                    UserHandleHidden.myUserId(),
                    request.mode != null ? request.mode : request.command,
                    detail);
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, item, success,
                    OperationJournalMetadata.forSingleAppAction(context, item, success,
                            OperationJournalMetadata.RISK_MEDIUM, false, success ? null : new Exception(detail)));
        } catch (Exception e) {
            Log.e(TAG, "Could not record a server control broadcast.", e);
        }
    }
}
