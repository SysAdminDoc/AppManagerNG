// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.PackageStateVerifier;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AppArchiveResultReceiver extends BroadcastReceiver {
    public static final String TAG = AppArchiveResultReceiver.class.getSimpleName();

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
            Intent confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            if (confirmIntent == null) {
                UIUtils.displayLongToast(operation == AppArchiveManager.OP_ARCHIVE
                        ? R.string.failed_to_archive_app
                        : R.string.failed_to_unarchive_app, label);
                return;
            }
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirmIntent);
            } catch (Throwable th) {
                Log.e(TAG, "Could not launch archive confirmation.", th);
                UIUtils.displayLongToast(operation == AppArchiveManager.OP_ARCHIVE
                        ? R.string.failed_to_archive_app
                        : R.string.failed_to_unarchive_app, label);
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
