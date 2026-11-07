// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stands in for WorkManager, so no test ever runs a real check against the system. */
final class RecordingWorkSink implements PermissionWatchScheduler.WorkSink {
    final List<PeriodicWorkRequest> scheduled = Collections.synchronizedList(new ArrayList<>());
    final List<String> enqueuedNames = Collections.synchronizedList(new ArrayList<>());
    final List<OneTimeWorkRequest> enqueued = Collections.synchronizedList(new ArrayList<>());
    volatile int cancels;

    @NonNull
    static RecordingWorkSink install() {
        RecordingWorkSink sink = new RecordingWorkSink();
        PermissionWatchScheduler.setWorkSinkForTesting(sink);
        return sink;
    }

    static void uninstall() {
        PermissionWatchScheduler.setWorkSinkForTesting(null);
    }

    @Override
    public void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request) {
        scheduled.add(request);
    }

    @Override
    public void cancelSchedule(@NonNull Context context) {
        ++cancels;
    }

    @Override
    public void enqueue(@NonNull Context context, @NonNull String uniqueName, @NonNull OneTimeWorkRequest request) {
        enqueuedNames.add(uniqueName);
        enqueued.add(request);
    }
}
