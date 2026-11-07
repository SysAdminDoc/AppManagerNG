// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * The whole schema of the server control broadcasts: three commands and six modes, each a fixed
 * value. Nothing a caller sends reaches a shell or becomes an intent.
 */
final class ServerControlRequest {
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_RESULT_PACKAGE = "result_package";

    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_COMMAND = "command";
    static final String EXTRA_UID = "uid";
    static final String EXTRA_OPS_STATUS = "ops_status";

    static final String COMMAND_START = "SERVER_START";
    static final String COMMAND_STOP = "SERVER_STOP";
    static final String COMMAND_SET_MODE = "SERVER_SET_MODE";
    static final String COMMAND_UNKNOWN = "unknown";

    private static final String ACTION_PREFIX = BuildConfig.APPLICATION_ID + ".action.";
    static final String ACTION_RESULT = ACTION_PREFIX + "SERVER_CONTROL_RESULT";

    /** {@link android.app.Activity#RESULT_OK}, so an ordered broadcast's caller reads it as usual. */
    static final int RESULT_SUCCESS = -1;
    /** The command outlived the broadcast. The result broadcast carries the answer. */
    static final int RESULT_STILL_RUNNING = 1;
    static final int RESULT_DISABLED = 2;
    static final int RESULT_BAD_AUTH = 3;
    static final int RESULT_BAD_REQUEST = 4;
    static final int RESULT_BUSY = 5;
    /** The start finished, but the saved mode (no-root, or auto finding nothing) runs no server. */
    static final int RESULT_NO_SERVER = 6;
    /** Added to the {@link Ops.Status} of a command that ran and failed. */
    static final int RESULT_OPS_FAILURE_BASE = 100;

    /** For {@link #EXTRA_OPS_STATUS} when the command never ran. */
    static final int OPS_STATUS_NONE = -1;

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final int PACKAGE_NAME_MAX_LENGTH = 255;

    @NonNull
    final String command;
    @Nullable
    @Ops.Mode
    final String mode;
    @Nullable
    final String requestId;
    @Nullable
    final String resultPackage;
    /** Whether every extra follows the schema. A request that doesn't still gets its answer sent. */
    final boolean valid;

    private ServerControlRequest(@NonNull String command, @Nullable String mode, @Nullable String requestId,
                                 @Nullable String resultPackage, boolean valid) {
        this.command = command;
        this.mode = mode;
        this.requestId = requestId;
        this.resultPackage = resultPackage;
        this.valid = valid;
    }

    /**
     * @throws RuntimeException if the extras can't be read: a sender can put a class in them that
     *                          this app can't unparcel.
     */
    @NonNull
    static ServerControlRequest from(@NonNull Intent intent) {
        String command = commandOf(intent.getAction());
        boolean hasMode = intent.hasExtra(EXTRA_MODE);
        String mode = allowedMode(intent.getStringExtra(EXTRA_MODE));
        boolean hasRequestId = intent.hasExtra(EXTRA_REQUEST_ID);
        String requestId = allowedRequestId(intent.getStringExtra(EXTRA_REQUEST_ID));
        boolean hasResultPackage = intent.hasExtra(EXTRA_RESULT_PACKAGE);
        String resultPackage = allowedPackage(intent.getStringExtra(EXTRA_RESULT_PACKAGE));

        boolean valid = !COMMAND_UNKNOWN.equals(command)
                && (COMMAND_SET_MODE.equals(command) ? mode != null : !hasMode)
                && (!hasRequestId || requestId != null)
                && (!hasResultPackage || resultPackage != null);
        return new ServerControlRequest(command, mode, requestId, resultPackage, valid);
    }

    /**
     * What's left of a request whose extras couldn't be read, for the history row.
     */
    @NonNull
    static ServerControlRequest unreadable(@Nullable String action) {
        return new ServerControlRequest(commandOf(action), null, null, null, false);
    }

    /**
     * The key check every call goes through first. A blank stored key opens nothing, and the
     * comparison takes the same time wherever the first wrong character is.
     */
    static boolean isAuthorized(@Nullable String storedKey, @Nullable String suppliedKey) {
        if (storedKey == null || storedKey.trim().isEmpty() || suppliedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(storedKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8));
    }

    static int resultCodeFor(@Ops.Status int opsStatus) {
        return opsStatus == Ops.STATUS_SUCCESS ? RESULT_SUCCESS : RESULT_OPS_FAILURE_BASE + opsStatus;
    }

    @NonNull
    private static String commandOf(@Nullable String action) {
        if (action != null && action.startsWith(ACTION_PREFIX)) {
            String command = action.substring(ACTION_PREFIX.length());
            if (COMMAND_START.equals(command) || COMMAND_STOP.equals(command) || COMMAND_SET_MODE.equals(command)) {
                return command;
            }
        }
        return COMMAND_UNKNOWN;
    }

    @Nullable
    private static String allowedMode(@Nullable String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode) {
            case Ops.MODE_AUTO:
            case Ops.MODE_ROOT:
            case Ops.MODE_SHIZUKU:
            case Ops.MODE_ADB_OVER_TCP:
            case Ops.MODE_ADB_WIFI:
            case Ops.MODE_NO_ROOT:
                return mode;
            default:
                return null;
        }
    }

    @Nullable
    private static String allowedRequestId(@Nullable String requestId) {
        return requestId != null && REQUEST_ID.matcher(requestId).matches() ? requestId : null;
    }

    @Nullable
    private static String allowedPackage(@Nullable String packageName) {
        return packageName != null && packageName.length() <= PACKAGE_NAME_MAX_LENGTH
                && PACKAGE_NAME.matcher(packageName).matches() ? packageName : null;
    }
}
