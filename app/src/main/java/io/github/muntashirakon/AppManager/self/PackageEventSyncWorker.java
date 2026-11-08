// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** The 15 minute look for app changes while the app isn't running. */
public class PackageEventSyncWorker extends Worker {
    public PackageEventSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        PackageEventSync.syncNow(getApplicationContext());
        return Result.success();
    }
}
