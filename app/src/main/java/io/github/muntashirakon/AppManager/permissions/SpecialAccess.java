// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.AppOpsManager;
import android.net.NetworkPolicyManager;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * State rules shared by the special app access entries of the Permission Inspector. Kept free
 * of Android services so the rules can be tested on the JVM.
 */
public final class SpecialAccess {
    @IntDef({UNKNOWN, DENIED, GRANTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    /** App Manager can't read this state with its current access. */
    public static final int UNKNOWN = -1;
    public static final int DENIED = 0;
    public static final int GRANTED = 1;

    /** {@code AppOpsManager.MODE_FOREGROUND}, which only exists from Android 10. */
    private static final int MODE_FOREGROUND = 4;

    private SpecialAccess() {
    }

    /**
     * The state of an app-op guarded access, read the way Android's PermissionChecker reads it:
     * the default mode defers to whether the permission itself is granted.
     */
    @Status
    public static int fromAppOpMode(int mode, boolean permissionGranted) {
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
            case MODE_FOREGROUND:
                return GRANTED;
            case AppOpsManager.MODE_IGNORED:
            case AppOpsManager.MODE_ERRORED:
                return DENIED;
            case AppOpsManager.MODE_DEFAULT:
                return permissionGranted ? GRANTED : DENIED;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Background activity is allowed unless the op is set to ignore or error. The default mode
     * means Android hasn't restricted the app.
     */
    @Status
    public static int fromBackgroundMode(int mode) {
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED ? DENIED : GRANTED;
    }

    /** Whether the app may use mobile data freely while Data Saver is on. */
    @Status
    public static int unrestrictedDataStatus(int policy) {
        return (policy & NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND) != 0 ? GRANTED : DENIED;
    }

    /** Whether the app may use mobile data in the background. */
    @Status
    public static int backgroundDataStatus(int policy) {
        return (policy & NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND) != 0 ? DENIED : GRANTED;
    }

    /**
     * The new uid policy after turning unrestricted data on or off. The two metered flags exclude
     * each other the way Settings sets them, and any other flag (an OEM's, say) is kept.
     */
    public static int withUnrestrictedData(int policy, boolean allow) {
        if (allow) {
            return (policy & ~NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND)
                    | NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND;
        }
        return policy & ~NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND;
    }

    /** The new uid policy after allowing or blocking background data. */
    public static int withBackgroundData(int policy, boolean allow) {
        if (allow) {
            return policy & ~NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND;
        }
        return (policy & ~NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND)
                | NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND;
    }
}
