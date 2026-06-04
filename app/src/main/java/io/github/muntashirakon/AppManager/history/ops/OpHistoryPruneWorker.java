// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;

public class OpHistoryPruneWorker extends Worker {
    private static final String TAG = OpHistoryPruneWorker.class.getSimpleName();

    public static final String OUTPUT_DELETED_COUNT = "deleted_count";

    public OpHistoryPruneWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        int retentionDays = Prefs.Privacy.getOpHistoryRetentionDays();
        if (!OpHistoryPruneScheduler.shouldSchedule(retentionDays)) {
            OpHistoryPruneScheduler.cancel(context);
            return success(0);
        }
        try {
            return success(OpHistoryManager.pruneHistoryOlderThan(retentionDays));
        } catch (Throwable th) {
            Log.e(TAG, "Failed to prune operation history.", th);
            return Result.failure();
        }
    }

    @NonNull
    private static Result success(int deletedCount) {
        return Result.success(new Data.Builder()
                .putInt(OUTPUT_DELETED_COUNT, Math.max(0, deletedCount))
                .build());
    }
}
