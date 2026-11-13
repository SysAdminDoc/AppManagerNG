// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Hears from {@link PermissionWatchRelay} that a watched app's permission or app op changed and checks that app's
 * rules right away. The manifest only lets senders holding {@code UPDATE_APP_OPS_STATS} in, which here means the
 * privileged process (shell or root) or the system.
 */
public class WatchedAppChangedReceiver extends BroadcastReceiver {
    private static final String TAG = "WatchedAppChangedReceiver";

    public static final String ACTION_WATCHED_APP_CHANGED =
            "io.github.muntashirakon.AppManager.action.WATCHED_APP_CHANGED";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_UID = "uid";

    /** {@code Intent.FLAG_RECEIVER_FROM_SHELL}, hidden: the sender is the shell or root. */
    private static final int FLAG_RECEIVER_FROM_SHELL = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 0x00400000 : 0;

    @SuppressLint("WrongConstant") // FLAG_RECEIVER_FROM_SHELL is a hidden flag lint doesn't list
    @NonNull
    static Intent intentFor(@NonNull String packageName, int uid) {
        return new Intent(ACTION_WATCHED_APP_CHANGED)
                .setClassName(BuildConfig.APPLICATION_ID, WatchedAppChangedReceiver.class.getName())
                // Foreground: Android 14 and later can hold a background broadcast for minutes
                .addFlags(FLAG_RECEIVER_FROM_SHELL | Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_UID, uid);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String packageName = packageOf(intent);
        if (packageName == null) {
            return;
        }
        int userId = userOf(intent);
        Context appContext = context.getApplicationContext();
        PendingResult pending = goAsync();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                handle(appContext, packageName, userId);
            } finally {
                if (pending != null) {
                    pending.finish();
                }
            }
        });
    }

    @VisibleForTesting
    @Nullable
    static String packageOf(@NonNull Intent intent) {
        if (!ACTION_WATCHED_APP_CHANGED.equals(intent.getAction()) || intent.getIntExtra(EXTRA_UID, -1) < 0) {
            return null;
        }
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        return packageName == null || packageName.isEmpty() ? null : packageName;
    }

    /** The user the changed app belongs to, so a work profile's copy of an app gets its own rules checked. */
    @VisibleForTesting
    static int userOf(@NonNull Intent intent) {
        return UserHandleHidden.getUserId(intent.getIntExtra(EXTRA_UID, -1));
    }

    /**
     * Checks on this thread while the privileged services are up, since this process may be about to go back to
     * sleep and a queued check could wait; hands it to WorkManager otherwise.
     */
    @VisibleForTesting
    @WorkerThread
    static void handle(@NonNull Context context, @NonNull String packageName, int userId) {
        if (!PermissionWatchDb.mightHaveRules(context)) {
            return;
        }
        if (LocalServices.alive()) {
            try {
                PermissionWatchWorker.checkAndNotify(context, packageName, userId);
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "Checking %s right away failed, queueing it.", e, packageName);
            }
        }
        PermissionWatchScheduler.checkSoon(context, packageName, userId);
    }
}
