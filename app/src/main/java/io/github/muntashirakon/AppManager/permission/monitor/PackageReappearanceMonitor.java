// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Records a warning when a package comes back after AppManagerNG recorded a
 * successful uninstall for it. This catches OEM restore jobs and store-managed
 * reinstalls that otherwise make debloat operations look trustworthy while the
 * package silently reappears later.
 */
public final class PackageReappearanceMonitor {
    public static final String TAG = "PackageReappearanceMonitor";

    @VisibleForTesting
    static final long RECENT_UNINSTALL_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private static final AtomicInteger sRequestCode = new AtomicInteger(4800);

    private PackageReappearanceMonitor() {
    }

    @WorkerThread
    public static void onPackageAdded(@NonNull Context appContext, @NonNull String packageName) {
        long now = System.currentTimeMillis();
        if (findRecentSuccessfulUninstall(OpHistoryManager.getAllHistoryItems(), packageName, now) == null) {
            return;
        }
        String label = resolveLabel(appContext, packageName);
        String title = appContext.getString(R.string.app_change_reappearance_title, label);
        String body = appContext.getString(R.string.app_change_reappearance_body, packageName);
        new AppChangeFeedStore(appContext).append(AppChangeFeedEntry.now("operation_reappearance",
                packageName, title, body));
        try {
            postNotification(appContext, packageName, title, body);
        } catch (Throwable t) {
            Log.w(TAG, "Could not post reappearance notification for " + packageName, t);
        }
    }

    @VisibleForTesting
    @Nullable
    static OpHistory findRecentSuccessfulUninstall(@NonNull List<OpHistory> rows,
                                                   @NonNull String packageName,
                                                   long nowMillis) {
        OpHistory best = null;
        for (OpHistory row : rows) {
            if (!isRecentSuccessfulUninstall(row, packageName, nowMillis)) {
                continue;
            }
            if (best == null || row.execTime > best.execTime) {
                best = row;
            }
        }
        return best;
    }

    private static boolean isRecentSuccessfulUninstall(@NonNull OpHistory row,
                                                       @NonNull String packageName,
                                                       long nowMillis) {
        if (!OpHistoryManager.HISTORY_TYPE_BATCH_OPS.equals(OpHistoryManager.normalizeHistoryType(row.type))
                || !OpHistoryManager.STATUS_SUCCESS.equals(OpHistoryManager.normalizeStatus(row.status))) {
            return false;
        }
        long age = nowMillis - row.execTime;
        if (age < 0 || age > RECENT_UNINSTALL_WINDOW_MILLIS) {
            return false;
        }
        try {
            JSONObject data = new JSONObject(row.serializedData);
            if (data.optInt("op", BatchOpsManager.OP_NONE) != BatchOpsManager.OP_UNINSTALL) {
                return false;
            }
            JSONArray packages = data.optJSONArray("packages");
            if (packages == null) {
                return false;
            }
            for (int i = 0; i < packages.length(); ++i) {
                if (packageName.equals(packages.optString(i))) {
                    return true;
                }
            }
        } catch (JSONException ignore) {
        }
        return false;
    }

    @WorkerThread
    private static void postNotification(@NonNull Context appContext, @NonNull String packageName,
                                         @NonNull String title, @NonNull String body) {
        Intent contentIntent = AppDetailsActivity.getIntent(appContext, packageName, 0, true);
        PendingIntent pi = PendingIntentCompat.getActivity(appContext, sRequestCode.incrementAndGet(),
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(appContext)
                .setSmallIcon(R.drawable.ic_security)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi);
        NotificationUtils.displayHighPriorityNotification(appContext, builder.build());
    }

    @NonNull
    private static String resolveLabel(@NonNull Context appContext, @NonNull String packageName) {
        try {
            ApplicationInfo info = appContext.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = info.loadLabel(appContext.getPackageManager());
            return label != null && label.length() > 0 ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
