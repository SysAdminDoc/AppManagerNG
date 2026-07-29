// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;
import android.os.Build;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Whether an app is currently blocked by Android's "restricted settings" gate.
 *
 * <p>From Android 13 the platform refuses to let a sideloaded app be granted accessibility or
 * notification-listener access — and from Android 14, health access — until the user explicitly
 * opens the app's info screen and chooses "Allow restricted settings". Until then those toggles
 * appear greyed out with no explanation of why, which is the single most confusing state an
 * inspector can leave a user in.
 *
 * <p>The gate is tracked by the hidden {@code android:access_restricted_settings} app-op, whose
 * numeric value has moved between platform releases. It is therefore resolved by name at runtime,
 * and an unresolvable op yields {@link Status#UNKNOWN} rather than a reassuring answer: reporting
 * "not restricted" for an app we could not query would be worse than reporting nothing.
 */
public final class RestrictedSettingsDetector {
    public static final String TAG = "RestrictedSettings";

    /** Hidden app-op backing the gate. Present from Android 13; the numeric code is not stable. */
    public static final String OPSTR_ACCESS_RESTRICTED_SETTINGS = "android:access_restricted_settings";

    /** Resolved at class load; {@link AppOpsManagerCompat#OP_NONE} when this platform has no such op. */
    public static final int OP_ACCESS_RESTRICTED_SETTINGS = resolveOp();

    public enum Status {
        /** This Android release has no restricted-settings gate. */
        UNSUPPORTED,
        /** The gate exists and the user has lifted it for this app. */
        ALLOWED,
        /** The gate exists and is currently blocking this app. */
        RESTRICTED,
        /** The gate could not be queried; nothing is claimed either way. */
        UNKNOWN
    }

    private RestrictedSettingsDetector() {
    }

    private static int resolveOp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return AppOpsManagerCompat.OP_NONE;
        }
        try {
            return AppOpsManagerHidden.strOpToOp(OPSTR_ACCESS_RESTRICTED_SETTINGS);
        } catch (Throwable th) {
            Log.w(TAG, "Could not resolve the restricted-settings app-op on this platform.", th);
            return AppOpsManagerCompat.OP_NONE;
        }
    }

    /** Whether this platform gates restricted settings at all. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /** Whether the gate can actually be queried on this platform. */
    public static boolean isQueryable() {
        return isSupported() && OP_ACCESS_RESTRICTED_SETTINGS > AppOpsManagerCompat.OP_NONE;
    }

    /**
     * Maps an app-op mode onto the gate's state.
     *
     * <p>Only {@link AppOpsManager#MODE_ALLOWED} means the user lifted the gate. {@code MODE_ERRORED}
     * is the platform default for a sideloaded app and means "still blocked"; {@code MODE_IGNORED}
     * and {@code MODE_DEFAULT} likewise leave the restricted toggles unavailable. Any mode this
     * mapping does not recognise is reported as unknown rather than folded into one of the two
     * definite answers.
     *
     * @param sdkInt Platform level, so a device without the gate is never described as restricted.
     * @param opMode Mode returned for {@link #OP_ACCESS_RESTRICTED_SETTINGS}.
     */
    @NonNull
    public static Status classify(int sdkInt, int opMode) {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return Status.UNSUPPORTED;
        }
        switch (opMode) {
            case AppOpsManager.MODE_ALLOWED:
                return Status.ALLOWED;
            case AppOpsManager.MODE_ERRORED:
            case AppOpsManager.MODE_IGNORED:
            case AppOpsManager.MODE_DEFAULT:
                return Status.RESTRICTED;
            default:
                return Status.UNKNOWN;
        }
    }

    /**
     * Queries the gate for one app.
     *
     * @return {@link Status#UNKNOWN} whenever the op is unavailable or the query fails.
     */
    @NonNull
    public static Status getStatus(int uid, @NonNull String packageName) {
        if (!isSupported()) {
            return Status.UNSUPPORTED;
        }
        if (!isQueryable()) {
            return Status.UNKNOWN;
        }
        try {
            int mode = new AppOpsManagerCompat().checkOperation(OP_ACCESS_RESTRICTED_SETTINGS, uid, packageName);
            return classify(Build.VERSION.SDK_INT, mode);
        } catch (Throwable th) {
            Log.w(TAG, "Could not read the restricted-settings gate for %s", th, packageName);
            return Status.UNKNOWN;
        }
    }
}
