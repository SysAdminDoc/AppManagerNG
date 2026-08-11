// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.PackageStateVerifier;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AppArchiveResultReceiver extends BroadcastReceiver {
    public static final String TAG = AppArchiveResultReceiver.class.getSimpleName();

    /**
     * Surface the platform's confirmation dialog through a notification when it cannot be
     * launched directly. Tapping it starts the activity with a foreground trigger the system
     * accepts, so the operation is recoverable rather than silently lost.
     */
    private static void postConfirmNotification(@NonNull Context context,
                                                @NonNull Intent confirmIntent,
                                                @NonNull String label,
                                                @AppArchiveManager.Operation int operation) {
        int title = operation == AppArchiveManager.OP_ARCHIVE
                ? R.string.confirm_archival
                : R.string.confirm_unarchival;
        NotificationUtils.displayInstallConfirmNotification(context, builder -> builder
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_default_notification)
                .setTicker(label)
                .setContentTitle(label)
                .setSubText(context.getString(R.string.package_installer))
                .setContentText(context.getString(title))
                .setContentIntent(PendingIntentCompat.getActivity(context, 0, confirmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT, false))
                .build());
    }

    @Override
    public void onReceive(Context nullableContext, @NonNull Intent intent) {
        Context context = nullableContext != null ? nullableContext : ContextUtils.getContext();
        @AppArchiveManager.Operation int operation = intent.getIntExtra(AppArchiveManager.EXTRA_OPERATION,
                AppArchiveManager.OP_ARCHIVE);
        String label = intent.getStringExtra(AppArchiveManager.EXTRA_APP_LABEL);
        if (label == null) {
            label = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        }
        if (label == null) {
            label = context.getString(R.string.app_name);
        }
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        if (AppArchiveManager.isPendingUserAction(intent)) {
            Intent confirmIntent = InstallerConfirmIntentGuard.sanitize(
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class),
                    context.getPackageName());
            if (confirmIntent == null) {
                UIUtils.displayLongToast(operation == AppArchiveManager.OP_ARCHIVE
                        ? R.string.failed_to_archive_app
                        : R.string.failed_to_unarchive_app, label);
                return;
            }
            // Starting an activity straight from a receiver only works while the app is
            // foreground; a background-activity-launch block does not throw, it is dropped with a
            // log line, so the catch below would report success on a confirmation the user never
            // saw. Fall back to a notification whose tap supplies the foreground trigger, the same
            // way the installer does.
            if (!Utils.isAppInForeground()) {
                postConfirmNotification(context, confirmIntent, label, operation);
                return;
            }
            try {
                context.startActivity(confirmIntent);
            } catch (Exception th) {
                Log.e(TAG, "Could not launch archive confirmation.", th);
                postConfirmNotification(context, confirmIntent, label, operation);
                return;
            }
            return;
        }
        boolean success = AppArchiveManager.isSuccess(intent, operation);
        PendingResult pending = goAsync();
        String finalLabel = label;
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean verifiedSuccess = verifyArchiveResult(operation, packageName, success);
            ThreadUtils.postOnMainThread(() -> {
                try {
                    displayArchiveResult(operation, finalLabel, verifiedSuccess);
                } finally {
                    pending.finish();
                }
            });
        });
    }

    private static boolean verifyArchiveResult(@AppArchiveManager.Operation int operation, String packageName,
                                               boolean success) {
        if (success && packageName != null) {
            @BatchOpsManager.OpType int op = operation == AppArchiveManager.OP_ARCHIVE
                    ? BatchOpsManager.OP_ARCHIVE
                    : BatchOpsManager.OP_UNARCHIVE;
            UserPackagePair pair = new UserPackagePair(packageName, UserHandleHidden.myUserId());
            if (PackageStateVerifier.matchesExpectedAndroidState(op, pair)) {
                return true;
            }
            Log.e(TAG, "Archive result contradicted package state. package=" + pair
                    + ", expected=" + PackageStateVerifier.getExpectedStateLabel(op));
            return false;
        }
        if (success) {
            Log.e(TAG, "Archive result did not include a package name.");
        }
        return false;
    }

    private static void displayArchiveResult(@AppArchiveManager.Operation int operation, @NonNull String label,
                                             boolean success) {
        if (success) {
            UIUtils.displayLongToast(operation == AppArchiveManager.OP_ARCHIVE
                    ? R.string.archived_successfully
                    : R.string.unarchived_successfully, label);
        } else {
            UIUtils.displayLongToast(operation == AppArchiveManager.OP_ARCHIVE
                    ? R.string.failed_to_archive_app
                    : R.string.failed_to_unarchive_app, label);
        }
    }
}
