// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class OpHistoryPruneScheduler {
    private static final String TAG = OpHistoryPruneScheduler.class.getSimpleName();

    public static final String UNIQUE_WORK_NAME = "op_history_retention_prune";
    public static final String WORK_TAG = "op_history_retention";
    public static final long DAILY_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1);

    private OpHistoryPruneScheduler() {
    }

    public static void scheduleOrCancel(@NonNull Context context) {
        scheduleOrCancel(context, Prefs.Privacy.getOpHistoryRetentionDays());
    }

    public static void scheduleOrCancel(@NonNull Context context, int retentionDays) {
        if (shouldSchedule(retentionDays)) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    public static void schedule(@NonNull Context context) {
        WorkManager workManager = getWorkManager(context);
        if (workManager == null) {
            return;
        }
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, buildPeriodicRequest());
    }

    public static void cancel(@NonNull Context context) {
        WorkManager workManager = getWorkManager(context);
        if (workManager == null) {
            return;
        }
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    @Nullable
    private static WorkManager getWorkManager(@NonNull Context context) {
        try {
            return WorkManager.getInstance(context.getApplicationContext());
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager is unavailable for operation history pruning.", e);
            return null;
        }
    }

    @NonNull
    @VisibleForTesting
    static PeriodicWorkRequest buildPeriodicRequest() {
        return new PeriodicWorkRequest.Builder(
                OpHistoryPruneWorker.class, DAILY_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build();
    }

    @VisibleForTesting
    static boolean shouldSchedule(int retentionDays) {
        return retentionDays > 0;
    }
}
