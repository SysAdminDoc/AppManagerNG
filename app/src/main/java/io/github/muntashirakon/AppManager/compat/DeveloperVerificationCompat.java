// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;

public final class DeveloperVerificationCompat {
    private static final String TAG = DeveloperVerificationCompat.class.getSimpleName();

    public static final String SERVICE_NAME = "developer_verifier";
    public static final String EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON =
            "android.content.pm.extra.DEVELOPER_VERIFICATION_FAILURE_REASON";

    public static final int FAILURE_REASON_NOT_PRESENT = -1;
    public static final int DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN = 0;
    public static final int DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE = 1;
    public static final int DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED = 2;

    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_UNKNOWN = 1;
    public static final int STATUS_VERIFIED = 2;
    public static final int STATUS_UNVERIFIED = 3;

    @IntDef({
            FAILURE_REASON_NOT_PRESENT,
            DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN,
            DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE,
            DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FailureReason {
    }

    @IntDef({
            STATUS_UNAVAILABLE,
            STATUS_UNKNOWN,
            STATUS_VERIFIED,
            STATUS_UNVERIFIED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VerificationStatus {
    }

    private DeveloperVerificationCompat() {
    }

    public static boolean isVerifierServiceAvailable(@NonNull Context context) {
        try {
            Object service = context.getSystemService(SERVICE_NAME);
            if (service != null) {
                return true;
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not query developer verifier system service.", th);
        }
        try {
            IBinder binder = ProxyBinder.getUnprivilegedService(SERVICE_NAME);
            return binder.pingBinder();
        } catch (Throwable ignore) {
            return false;
        }
    }

    @VerificationStatus
    public static int getVerificationStatus(@NonNull Context context, @Nullable String packageName) {
        if (!isVerifierServiceAvailable(context)) {
            return STATUS_UNAVAILABLE;
        }
        // Android 36.1 exposes final install failure reasons through PackageInstaller result
        // intents, but no stable public per-package preflight query is available to apps here.
        return STATUS_UNKNOWN;
    }

    @FailureReason
    public static int getFailureReason(@Nullable Intent intent) {
        if (intent == null) {
            return FAILURE_REASON_NOT_PRESENT;
        }
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON)) {
            return FAILURE_REASON_NOT_PRESENT;
        }
        Object value = extras.get(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON);
        if (value instanceof Integer) {
            return normalizeFailureReason((Integer) value);
        }
        if (value instanceof Number) {
            return normalizeFailureReason(((Number) value).intValue());
        }
        if (value instanceof String) {
            try {
                return normalizeFailureReason(Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignore) {
                return FAILURE_REASON_NOT_PRESENT;
            }
        }
        return FAILURE_REASON_NOT_PRESENT;
    }

    @FailureReason
    private static int normalizeFailureReason(int reason) {
        switch (reason) {
            case DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN:
            case DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE:
            case DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED:
                return reason;
            default:
                return FAILURE_REASON_NOT_PRESENT;
        }
    }

    @NonNull
    public static String getFailureReasonName(@FailureReason int reason) {
        switch (reason) {
            case DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN:
                return "UNKNOWN";
            case DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE:
                return "NETWORK_UNAVAILABLE";
            case DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED:
                return "DEVELOPER_BLOCKED";
            default:
                return "NOT_PRESENT";
        }
    }

    @NonNull
    public static String getFailureReasonLabel(@NonNull Context context, @FailureReason int reason) {
        switch (reason) {
            case DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE:
                return context.getString(R.string.developer_verification_failure_network);
            case DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED:
                return context.getString(R.string.developer_verification_failure_blocked);
            case DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN:
                return context.getString(R.string.developer_verification_failure_unknown);
            default:
                return getFailureReasonName(reason).toLowerCase(Locale.ROOT);
        }
    }

    @Nullable
    public static String appendFailureReason(@NonNull Context context, @Nullable String statusMessage,
                                             @FailureReason int reason) {
        if (reason == FAILURE_REASON_NOT_PRESENT) {
            return statusMessage;
        }
        String line = context.getString(R.string.installer_developer_verification_failure_reason,
                getFailureReasonLabel(context, reason), getFailureReasonName(reason));
        if (statusMessage == null || statusMessage.trim().isEmpty()) {
            return line;
        }
        if (statusMessage.contains(line)
                || statusMessage.contains(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON)
                || statusMessage.contains("Developer verification:")) {
            return statusMessage;
        }
        return statusMessage + "\n" + line;
    }
}
