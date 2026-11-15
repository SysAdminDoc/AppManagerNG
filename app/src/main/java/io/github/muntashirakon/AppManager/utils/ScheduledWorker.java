// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * A worker that runs on a schedule. On WorkManager 2.10.5 periodic work whose worker throws ends FAILED and never
 * runs again (b/443879071, fixed in 2.11.2, which needs minSdk 23), while periodic work that returns a failure waits
 * for its next period. So anything thrown here, an {@link Error} included, is logged and returned as a failure.
 */
public abstract class ScheduledWorker extends Worker {
    public ScheduledWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public final Result doWork() {
        try {
            return doScheduledWork();
        } catch (Throwable th) {
            Log.e(getClass().getSimpleName(), "This run stopped on an uncaught error. The schedule goes on.", th);
            return Result.failure();
        }
    }

    /** The run itself, on the worker's background thread. */
    @NonNull
    protected abstract Result doScheduledWork();
}
