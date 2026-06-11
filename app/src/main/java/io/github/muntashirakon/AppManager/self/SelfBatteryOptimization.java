// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.logs.Log;

public final class SelfBatteryOptimization {
    private static final String TAG = "SelfBatteryOptimization";

    @IntDef({
            RESULT_UNSUPPORTED,
            RESULT_ALREADY_EXEMPT,
            RESULT_FIXED,
            RESULT_NO_PRIVILEGE,
            RESULT_FAILED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutoFixResult {
    }

    public static final int RESULT_UNSUPPORTED = 0;
    public static final int RESULT_ALREADY_EXEMPT = 1;
    public static final int RESULT_FIXED = 2;
    public static final int RESULT_NO_PRIVILEGE = 3;
    public static final int RESULT_FAILED = 4;

    private SelfBatteryOptimization() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean isExempt(@NonNull Context context) {
        if (!isSupported()) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(appContext.getPackageName());
    }

    public static boolean canAutoFix() {
        return isSupported()
                && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER);
    }

    @NonNull
    public static String formatDiagnostics(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        boolean supported = isSupported();
        boolean exempt = supported && isExempt(appContext);
        boolean canAutoFix = false;
        if (supported && !exempt) {
            try {
                canAutoFix = canAutoFix();
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not determine battery optimization auto-fix state.", e);
            }
        }
        return appContext.getString(getDiagnosticsLabelRes(supported, exempt, canAutoFix));
    }

    @StringRes
    @VisibleForTesting
    static int getDiagnosticsLabelRes(boolean supported, boolean exempt, boolean canAutoFix) {
        if (!supported) {
            return R.string.self_battery_optimization_diagnostics_unsupported;
        }
        if (exempt) {
            return R.string.self_battery_optimization_diagnostics_exempt;
        }
        return canAutoFix
                ? R.string.self_battery_optimization_diagnostics_restricted_autofix
                : R.string.self_battery_optimization_diagnostics_restricted_manual;
    }

    @WorkerThread
    @AutoFixResult
    public static int autoFixIfPossible(@NonNull Context context) {
        if (!isSupported()) {
            return RESULT_UNSUPPORTED;
        }
        Context appContext = context.getApplicationContext();
        if (isExempt(appContext)) {
            return RESULT_ALREADY_EXEMPT;
        }
        if (!canAutoFix()) {
            return RESULT_NO_PRIVILEGE;
        }
        try {
            boolean ok = DeviceIdleManagerCompat.disableBatteryOptimization(appContext.getPackageName());
            return ok && isExempt(appContext) ? RESULT_FIXED : RESULT_FAILED;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not auto-fix battery optimization for %s.", e, appContext.getPackageName());
            return RESULT_FAILED;
        }
    }
}
