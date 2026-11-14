// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ArchivedActivityInfo;
import android.content.pm.ArchivedPackageInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.PendingIntentCompat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class AppArchiveManager {
    private static final String TAG = AppArchiveManager.class.getSimpleName();
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

    /** When the package was archived, or 0 when it isn't archived or Android can't archive apps. */
    @SuppressLint("NewApi")
    public static long getArchiveTimeMillis(@NonNull PackageInfo packageInfo) {
        return isArchived(packageInfo) ? packageInfo.getArchiveTimeMillis() : 0;
    }

    @VisibleForTesting
    static boolean isArchiveTimeArchived(long archiveTimeMillis) {
        return archiveTimeMillis > 0;
    }

    /**
     * The name the launcher shows for an app archived for the calling user, or null when Android kept
     * none. Android drops the APK, so the package's own label is gone and only this one is left. Check
     * {@link #isArchived(PackageInfo)} first: for an installed app Android answers with its live entry.
     */
    @WorkerThread
    @Nullable
    public static CharSequence getArchivedLabel(@NonNull PackageManager pm, @NonNull String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return null;
        }
        ArchivedActivityInfo entry = pickLauncherEntry(readArchivedPackage(pm, packageName));
        return entry != null && !TextUtils.isEmpty(entry.getLabel()) ? entry.getLabel() : null;
    }

    /** The icon the launcher shows for an app archived for the calling user, or null when Android kept none. */
    @WorkerThread
    @Nullable
    public static Drawable getArchivedIcon(@NonNull PackageManager pm, @NonNull String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return null;
        }
        ArchivedPackageInfo info = readArchivedPackage(pm, packageName);
        ArchivedActivityInfo entry = pickLauncherEntry(info);
        Drawable icon = entry != null ? entry.getIcon() : null;
        if (icon != null || info == null) {
            return icon;
        }
        // The entry the name comes from kept no icon, so another entry's stands in
        for (ArchivedActivityInfo activity : info.getLauncherActivities()) {
            icon = activity.getIcon();
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Nullable
    private static ArchivedActivityInfo pickLauncherEntry(@Nullable ArchivedPackageInfo info) {
        if (info == null) {
            return null;
        }
        List<ArchivedActivityInfo> activities = info.getLauncherActivities();
        for (ArchivedActivityInfo activity : activities) {
            if (!TextUtils.isEmpty(activity.getLabel())) {
                return activity;
            }
        }
        return activities.isEmpty() ? null : activities.get(0);
    }

    /**
     * What Android has for the calling user's copy of the app, or null when it has nothing. An installed
     * app gets an answer too, so callers check that the app is archived first.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Nullable
    private static ArchivedPackageInfo readArchivedPackage(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            return pm.getArchivedPackage(packageName);
        } catch (IllegalArgumentException e) {
            // Android throws this when it kept no launcher entry for the app
            Log.w(TAG, "No archived launcher entry for %s", e, packageName);
            return null;
        }
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
                .setClass(context, AppArchiveResultReceiver.class)
                .putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_APP_LABEL, appLabel != null ? appLabel.toString() : packageName)
                .putExtra(EXTRA_OPERATION, operation);
        int requestCode = Objects.hash(packageName, operation);
        // Mutable is required — the platform installer fills in the status extras — so the base
        // intent names an explicit component and cannot be redirected by whoever holds it.
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
