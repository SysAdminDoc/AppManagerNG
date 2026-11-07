// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * The scheduled check, plus one-off checks after a package update, a rule change or a restart.
 */
public final class PermissionWatchScheduler {
    private static final String TAG = "PermissionWatchScheduler";

    public static final String UNIQUE_WORK_NAME = "permission_watch_scheduled";
    public static final String WORK_TAG = "permission_watch";
    @VisibleForTesting
    static final String ALL_RULES_WORK_NAME = "permission_watch_all";
    @VisibleForTesting
    static final String PACKAGE_WORK_PREFIX = "permission_watch_package:";

    /** What the setting offers, in minutes. 0 is off. WorkManager won't run periodic work more often than 15. */
    public static final int[] INTERVAL_CHOICES_MINUTES = {0, 15, 60, 24 * 60};

    /** Where the requests go. Tests swap it so nothing reaches a real WorkManager. */
    @VisibleForTesting
    interface WorkSink {
        void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request);

        void cancelSchedule(@NonNull Context context);

        void enqueue(@NonNull Context context, @NonNull String uniqueName, @NonNull OneTimeWorkRequest request);
    }

    private static final WorkSink WORK_MANAGER_SINK = new WorkSink() {
        @Override
        public void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request) {
            WorkManager workManager = getWorkManager(context);
            if (workManager != null) {
                workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
            }
        }

        @Override
        public void cancelSchedule(@NonNull Context context) {
            WorkManager workManager = getWorkManager(context);
            if (workManager != null) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
            }
        }

        @Override
        public void enqueue(@NonNull Context context, @NonNull String uniqueName,
                            @NonNull OneTimeWorkRequest request) {
            WorkManager workManager = getWorkManager(context);
            if (workManager != null) {
                workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request);
            }
        }
    };

    @NonNull
    private static volatile WorkSink sWorkSink = WORK_MANAGER_SINK;

    private PermissionWatchScheduler() {
    }

    @VisibleForTesting
    static void setWorkSinkForTesting(@Nullable WorkSink sink) {
        sWorkSink = sink != null ? sink : WORK_MANAGER_SINK;
    }

    public static void scheduleOrCancel(@NonNull Context context) {
        scheduleOrCancel(context, Prefs.Privacy.getPermissionWatcherIntervalMinutes());
    }

    public static void scheduleOrCancel(@NonNull Context context, int intervalMinutes) {
        if (shouldSchedule(intervalMinutes)) {
            sWorkSink.schedule(context, buildPeriodicRequest(intervalMinutes));
        } else {
            sWorkSink.cancelSchedule(context);
        }
    }

    /** After a restart: the schedule is put back, and with it on, every rule is checked once right away. */
    public static void onBoot(@NonNull Context context) {
        int intervalMinutes = Prefs.Privacy.getPermissionWatcherIntervalMinutes();
        scheduleOrCancel(context, intervalMinutes);
        if (shouldSchedule(intervalMinutes) && PermissionWatchDb.mightHaveRules(context)) {
            checkSoon(context, null, 0);
        }
    }

    /**
     * Checks one app's rules, or every rule when {@code packageName} is {@code null}. A newer request for the same
     * app replaces one still waiting.
     */
    public static void checkSoon(@NonNull Context context, @Nullable String packageName, int userId) {
        sWorkSink.enqueue(context, uniqueCheckName(packageName, userId), buildOneTimeRequest(packageName, userId));
    }

    @VisibleForTesting
    @NonNull
    static String uniqueCheckName(@Nullable String packageName, int userId) {
        return packageName == null ? ALL_RULES_WORK_NAME : PACKAGE_WORK_PREFIX + packageName + ":" + userId;
    }

    @Nullable
    private static WorkManager getWorkManager(@NonNull Context context) {
        try {
            return WorkManager.getInstance(context.getApplicationContext());
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager is unavailable for the permission watcher.", e);
            return null;
        }
    }

    @VisibleForTesting
    static boolean shouldSchedule(int intervalMinutes) {
        return intervalMinutes > 0;
    }

    @VisibleForTesting
    @NonNull
    static PeriodicWorkRequest buildPeriodicRequest(int intervalMinutes) {
        long minutes = Math.max(intervalMinutes, PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60_000L);
        return new PeriodicWorkRequest.Builder(PermissionWatchWorker.class, minutes, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build();
    }

    @VisibleForTesting
    @NonNull
    static OneTimeWorkRequest buildOneTimeRequest(@Nullable String packageName, int userId) {
        Data.Builder input = new Data.Builder().putInt(PermissionWatchWorker.INPUT_USER, userId);
        if (packageName != null) {
            input.putString(PermissionWatchWorker.INPUT_PACKAGE, packageName);
        }
        return new OneTimeWorkRequest.Builder(PermissionWatchWorker.class)
                .setInputData(input.build())
                .addTag(WORK_TAG)
                .build();
    }
}
