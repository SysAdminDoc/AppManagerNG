// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.PendingIntentCompat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class AppArchiveManager {
    public static final String ACTION_ARCHIVE_RESULT = BuildConfig.APPLICATION_ID + ".action.APP_ARCHIVE_RESULT";
    public static final String EXTRA_OPERATION = BuildConfig.APPLICATION_ID + ".extra.ARCHIVE_OPERATION";
    public static final String EXTRA_APP_LABEL = BuildConfig.APPLICATION_ID + ".extra.APP_LABEL";
    @VisibleForTesting
    static final String EXTRA_PACKAGE_INSTALLER_STATUS = "android.content.pm.extra.STATUS";
    @VisibleForTesting
    static final String EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS = "android.content.pm.extra.UNARCHIVE_STATUS";
    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_FAILURE = 1;
    private static final int STATUS_PENDING_USER_ACTION = -1;
    private static final int UNARCHIVAL_OK = 0;
    private static final int UNARCHIVAL_ERROR_USER_ACTION_NEEDED = 1;
    private static final int UNARCHIVAL_GENERIC_ERROR = 100;

    public static final int OP_ARCHIVE = 1;
    public static final int OP_UNARCHIVE = 2;

    private AppArchiveManager() {
    }

    public static boolean isSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    public static boolean canShowArchiveAction(int sdkInt, int targetUserId, int currentUserId,
                                               boolean isExternalApk, boolean isStaticSharedLibrary,
                                               boolean isSystemApp, @NonNull String packageName) {
        return isSupported(sdkInt)
                && targetUserId == currentUserId
                && !isExternalApk
                && !isStaticSharedLibrary
                && !isSystemApp
                && !BuildConfig.APPLICATION_ID.equals(packageName);
    }

    @SuppressLint("NewApi")
    public static boolean isArchived(@NonNull PackageInfo packageInfo) {
        return isSupported(Build.VERSION.SDK_INT) && isArchiveTimeArchived(packageInfo.getArchiveTimeMillis());
    }

    @VisibleForTesting
    static boolean isArchiveTimeArchived(long archiveTimeMillis) {
        return archiveTimeMillis > 0;
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static void request(@NonNull Context context, @NonNull String packageName,
                               @Nullable CharSequence appLabel, @Operation int operation)
            throws PackageManager.NameNotFoundException, IOException {
        ThreadUtils.ensureWorkerThread();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        IntentSender sender = buildResultSender(context, packageName, appLabel, operation);
        if (operation == OP_ARCHIVE) {
            packageInstaller.requestArchive(packageName, sender);
        } else {
            packageInstaller.requestUnarchive(packageName, sender);
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @NonNull
    private static IntentSender buildResultSender(@NonNull Context context, @NonNull String packageName,
                                                  @Nullable CharSequence appLabel, @Operation int operation) {
        Intent callbackIntent = new Intent(context, AppArchiveResultReceiver.class)
                .setAction(ACTION_ARCHIVE_RESULT)
                .setPackage(context.getPackageName())
                .putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_APP_LABEL, appLabel != null ? appLabel.toString() : packageName)
                .putExtra(EXTRA_OPERATION, operation);
        int requestCode = Objects.hash(packageName, operation);
        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(context, requestCode, callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, true);
        return pendingIntent.getIntentSender();
    }

    public static boolean isPendingUserAction(@NonNull Intent intent) {
        return intent.getIntExtra(EXTRA_PACKAGE_INSTALLER_STATUS, STATUS_FAILURE)
                == STATUS_PENDING_USER_ACTION
                || intent.getIntExtra(EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS,
                UNARCHIVAL_GENERIC_ERROR)
                == UNARCHIVAL_ERROR_USER_ACTION_NEEDED;
    }

    public static boolean isSuccess(@NonNull Intent intent, @Operation int operation) {
        if (operation == OP_UNARCHIVE && intent.hasExtra(EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS)) {
            return intent.getIntExtra(EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS,
                    UNARCHIVAL_GENERIC_ERROR) == UNARCHIVAL_OK;
        }
        return intent.getIntExtra(EXTRA_PACKAGE_INSTALLER_STATUS, STATUS_FAILURE) == STATUS_SUCCESS;
    }

    @IntDef({OP_ARCHIVE, OP_UNARCHIVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Operation {
    }
}
