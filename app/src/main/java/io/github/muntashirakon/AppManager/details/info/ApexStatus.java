// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;

/**
 * Whether a package is an APEX, a Mainline module, or both. Android 10 brought both, so older versions have neither.
 */
public final class ApexStatus {
    @IntDef({REGULAR, APEX, MAINLINE_APEX, MAINLINE_APK})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    public static final int REGULAR = 0;
    public static final int APEX = 1;
    public static final int MAINLINE_APEX = 2;
    public static final int MAINLINE_APK = 3;

    private ApexStatus() {
    }

    /**
     * @param isApex           {@link PackageInfo#isApex}
     * @param sourceDir        Where the package lives. Code inside an APEX is mounted under /apex/.
     * @param isMainlineModule Whether the package manager knows the package as a module
     */
    @Status
    public static int classify(boolean isApex, @Nullable String sourceDir, boolean isMainlineModule) {
        boolean apex = isApex || (sourceDir != null && sourceDir.startsWith("/apex/"));
        if (isMainlineModule) {
            return apex ? MAINLINE_APEX : MAINLINE_APK;
        }
        return apex ? APEX : REGULAR;
    }

    /**
     * Asks the package manager: the package as an APEX (MATCH_APEX finds the APEX itself, which an ordinary query
     * leaves out), and the module list.
     */
    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.Q)
    @Status
    public static int of(@NonNull Context context, @NonNull PackageInfo packageInfo) {
        PackageManager pm = context.getPackageManager();
        boolean isApex = packageInfo.isApex;
        if (!isApex) {
            try {
                isApex = pm.getPackageInfo(packageInfo.packageName, PackageManager.MATCH_APEX).isApex;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignore) {
            }
        }
        boolean isModule;
        try {
            isModule = pm.getModuleInfo(packageInfo.packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            isModule = false;
        }
        String sourceDir = packageInfo.applicationInfo != null ? packageInfo.applicationInfo.sourceDir : null;
        return classify(isApex, sourceDir, isModule);
    }

    @StringRes
    public static int labelRes(@Status int status) {
        switch (status) {
            case APEX:
                return R.string.apex_status_apex;
            case MAINLINE_APEX:
                return R.string.apex_status_mainline_apex;
            case MAINLINE_APK:
                return R.string.apex_status_mainline_apk;
            case REGULAR:
            default:
                return R.string.apex_status_regular;
        }
    }
}
