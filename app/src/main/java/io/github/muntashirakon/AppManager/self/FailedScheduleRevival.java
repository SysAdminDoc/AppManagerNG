// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupScheduler;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneScheduler;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchScheduler;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineScheduler;

/**
 * Brings back the schedules that ended FAILED. On WorkManager 2.10.5 periodic work whose worker threw ended that way
 * and never ran again, and enqueuing it again with UPDATE, as every schedule here does, leaves a FAILED row as it is.
 * {@link io.github.muntashirakon.AppManager.utils.ScheduledWorker} keeps it from happening now, so this repairs the
 * schedules that failed before. The app change sync needs nothing: its KEEP replaces finished work on every start.
 */
public final class FailedScheduleRevival {
    private static final String TAG = FailedScheduleRevival.class.getSimpleName();

    private FailedScheduleRevival() {
    }

    @WorkerThread
    public static void reviveFailedSchedules(@NonNull Context context) {
        List<WorkInfo> failed;
        try {
            failed = WorkManager.getInstance(context.getApplicationContext())
                    .getWorkInfos(WorkQuery.fromStates(WorkInfo.State.FAILED))
                    .get();
        } catch (IllegalStateException | ExecutionException e) {
            Log.w(TAG, "Could not look for schedules that stopped.", e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        revive(context, failed);
    }

    /** Puts back each schedule that's on and has periodic work among {@code failed}. */
    @VisibleForTesting
    public static void revive(@NonNull Context context, @NonNull List<WorkInfo> failed) {
        Set<String> tags = new HashSet<>();
        for (WorkInfo info : failed) {
            // One-time work, a "Run now" backup for one, can end FAILED and stays that way
            if (info.getState() == WorkInfo.State.FAILED && info.getPeriodicityInfo() != null) {
                tags.addAll(info.getTags());
            }
        }
        if (tags.isEmpty()) {
            return;
        }
        Log.i(TAG, "Putting back schedules that stopped: %s", tags);
        if (tags.contains(AutoBackupScheduler.WORK_TAG)) {
            AutoBackupScheduler.revive(context);
        }
        if (tags.contains(OpHistoryPruneScheduler.WORK_TAG)) {
            OpHistoryPruneScheduler.revive(context);
        }
        if (tags.contains(PermissionWatchScheduler.WORK_TAG)) {
            PermissionWatchScheduler.revive(context);
        }
        if (tags.contains(RoutineScheduler.WORK_TAG)) {
            RoutineScheduler.revive(context, tags);
        }
    }
}
