// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class AutoBackupScheduler {
    public static final String UNIQUE_WORK_NAME = "scheduled_auto_backup";
    public static final String MANUAL_WORK_NAME = "manual_scheduled_auto_backup";
    public static final String WORK_TAG = "auto_backup";
    public static final int DEFAULT_HOUR = 2;
    public static final int DEFAULT_MINUTE = 0;

    public static final int NETWORK_NOT_REQUIRED = 0;
    public static final int NETWORK_CONNECTED = 1;
    public static final int NETWORK_UNMETERED = 2;

    private static final long DAILY_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1);

    private AutoBackupScheduler() {
    }

    public static void scheduleOrCancel(@NonNull Context context) {
        if (Prefs.BackupRestore.isScheduledAutoBackupEnabled()) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    public static void schedule(@NonNull Context context) {
        int hour = Prefs.BackupRestore.getScheduledBackupHour();
        int minute = Prefs.BackupRestore.getScheduledBackupMinute();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class, DAILY_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setInitialDelay(computeInitialDelayMillis(hour, minute, System.currentTimeMillis()),
                        TimeUnit.MILLISECONDS)
                .setConstraints(buildConstraints(
                        Prefs.BackupRestore.isScheduledBackupChargingRequired(),
                        Prefs.BackupRestore.getScheduledBackupNetworkType()))
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    public static void enqueueManualRun(@NonNull Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AutoBackupWorker.class)
                .setInputData(AutoBackupWorker.manualInputData())
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    public static List<UserPackagePair> collectInstalledPackages(@NonNull Context context) {
        List<ApplicationItem> applicationItems = PackageUtils.getInstalledOrBackedUpApplicationsFromDb(
                context.getApplicationContext(), false, true);
        ArrayList<UserPackagePair> pairs = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (!item.isInstalled || item.userIds == null) {
                continue;
            }
            for (int userId : item.userIds) {
                pairs.add(new UserPackagePair(item.packageName, userId));
            }
        }
        return pairs;
    }

    @NonNull
    public static Constraints buildConstraints(boolean requireCharging, int networkType) {
        return new Constraints.Builder()
                .setRequiresCharging(requireCharging)
                .setRequiredNetworkType(toWorkNetworkType(networkType))
                .build();
    }

    @NonNull
    public static NetworkType toWorkNetworkType(int networkType) {
        switch (sanitizeNetworkType(networkType)) {
            case NETWORK_CONNECTED:
                return NetworkType.CONNECTED;
            case NETWORK_UNMETERED:
                return NetworkType.UNMETERED;
            case NETWORK_NOT_REQUIRED:
            default:
                return NetworkType.NOT_REQUIRED;
        }
    }

    public static int sanitizeHour(int hour) {
        return Math.max(0, Math.min(23, hour));
    }

    public static int sanitizeMinute(int minute) {
        return Math.max(0, Math.min(59, minute));
    }

    public static int sanitizeNetworkType(int networkType) {
        switch (networkType) {
            case NETWORK_CONNECTED:
            case NETWORK_UNMETERED:
                return networkType;
            case NETWORK_NOT_REQUIRED:
            default:
                return NETWORK_NOT_REQUIRED;
        }
    }

    public static void recordRunStarted(@NonNull String result) {
        Prefs.BackupRestore.setScheduledBackupLastRun(System.currentTimeMillis());
        Prefs.BackupRestore.setScheduledBackupLastResult(result);
    }

    public static void recordRunResult(@NonNull String result) {
        Prefs.BackupRestore.setScheduledBackupLastRun(System.currentTimeMillis());
        Prefs.BackupRestore.setScheduledBackupLastResult(result);
    }

    @NonNull
    public static String refreshDiagnostics(@NonNull Context context) {
        try {
            String diagnostics = AutoBackupDiagnostics.collect(context);
            Prefs.BackupRestore.setScheduledBackupLastDiagnostics(diagnostics);
            return diagnostics;
        } catch (Throwable th) {
            if (th instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String diagnostics = context.getString(R.string.auto_backup_diagnostics_unavailable);
            Prefs.BackupRestore.setScheduledBackupLastDiagnostics(diagnostics);
            return diagnostics;
        }
    }

    @NonNull
    public static String formatScheduleTime(@NonNull Context context) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Prefs.BackupRestore.getScheduledBackupHour());
        calendar.set(Calendar.MINUTE, Prefs.BackupRestore.getScheduledBackupMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.getTime());
    }

    @VisibleForTesting
    public static long computeInitialDelayMillis(int hour, int minute, long nowMillis) {
        return computeInitialDelayMillis(hour, minute, nowMillis, TimeZone.getDefault());
    }

    @VisibleForTesting
    public static long computeInitialDelayMillis(int hour, int minute, long nowMillis,
                                                 @NonNull TimeZone timeZone) {
        Calendar next = Calendar.getInstance(timeZone);
        next.setTimeInMillis(nowMillis);
        next.set(Calendar.HOUR_OF_DAY, sanitizeHour(hour));
        next.set(Calendar.MINUTE, sanitizeMinute(minute));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= nowMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - nowMillis;
    }
}
