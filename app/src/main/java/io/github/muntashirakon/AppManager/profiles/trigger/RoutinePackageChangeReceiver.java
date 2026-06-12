// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.monitor.PackageReappearanceMonitor;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class RoutinePackageChangeReceiver extends BroadcastReceiver {
    private static final String TAG = RoutinePackageChangeReceiver.class.getSimpleName();

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        Integer triggerType = resolveTriggerType(intent);
        if (triggerType == null) {
            return;
        }
        String packageName = extractPackageName(intent);
        if (packageName == null || packageName.isEmpty() || context.getPackageName().equals(packageName)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        boolean isFreshInstall = Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        String finalPackageName = packageName;
        PendingResult pending = goAsync();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                if (isFreshInstall) {
                    try {
                        PackageReappearanceMonitor.onPackageAdded(appContext, finalPackageName);
                    } catch (Throwable th) {
                        Log.w(TAG, "Could not check package reappearance for " + finalPackageName, th);
                    }
                }
                RoutineScheduler.enqueuePackageEventTriggers(appContext, triggerType);
            } catch (Throwable th) {
                Log.w(TAG, "Could not dispatch package routine trigger for " + finalPackageName, th);
            } finally {
                pending.finish();
            }
        });
    }

    @Nullable
    @ProfileTrigger.Type
    @VisibleForTesting
    static Integer resolveTriggerType(@NonNull Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    ? ProfileTrigger.TYPE_ON_APP_UPDATE
                    : ProfileTrigger.TYPE_ON_APP_INSTALL;
        }
        if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            return ProfileTrigger.TYPE_ON_APP_UPDATE;
        }
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return ProfileTrigger.TYPE_ON_APP_UNINSTALL;
        }
        return null;
    }

    @Nullable
    @VisibleForTesting
    static String extractPackageName(@NonNull Intent intent) {
        Uri data = intent.getData();
        if (data == null) return null;
        String ssp = data.getSchemeSpecificPart();
        if (ssp != null && !ssp.isEmpty()) return ssp;
        return data.getEncodedSchemeSpecificPart();
    }
}
