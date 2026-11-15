// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import io.github.muntashirakon.AppManager.utils.ScheduledWorker;

/** The 15 minute look for app changes while the app isn't running. */
public class PackageEventSyncWorker extends ScheduledWorker {
    public PackageEventSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    protected Result doScheduledWork() {
        PackageEventSync.syncNow(getApplicationContext());
        return Result.success();
    }
}
