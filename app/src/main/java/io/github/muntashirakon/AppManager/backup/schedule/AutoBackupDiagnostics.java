// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.PendingJobReasonsInfo;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.SelfBatteryOptimization;
import io.github.muntashirakon.AppManager.utils.AndroidUtils;

final class AutoBackupDiagnostics {
    private static final String EXTRA_WORK_SPEC_ID = "EXTRA_WORK_SPEC_ID";

    private AutoBackupDiagnostics() {
    }

    @NonNull
    static String collect(@NonNull Context context) throws ExecutionException, InterruptedException {
        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);
        WorkInfo scheduled = latest(workManager.getWorkInfosForUniqueWork(
                AutoBackupScheduler.UNIQUE_WORK_NAME).get());
        WorkInfo manual = latest(workManager.getWorkInfosForUniqueWork(
                AutoBackupScheduler.MANUAL_WORK_NAME).get());
        if (scheduled == null && manual == null) {
            return appendBatteryDiagnostics(appContext,
                    appContext.getString(R.string.auto_backup_diagnostics_no_work));
        }
        StringBuilder sb = new StringBuilder();
        if (scheduled != null) {
            sb.append(formatWork(appContext, appContext.getString(R.string.auto_backup_diagnostics_scheduled),
                    scheduled, findJob(appContext, scheduled)));
        }
        if (manual != null) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(formatWork(appContext, appContext.getString(R.string.auto_backup_diagnostics_manual),
                    manual, findJob(appContext, manual)));
        }
        return appendBatteryDiagnostics(appContext, sb.toString());
    }

    @Nullable
    private static WorkInfo latest(@NonNull List<WorkInfo> workInfos) {
        if (workInfos.isEmpty()) {
            return null;
        }
        return Collections.max(workInfos, Comparator
                .comparingInt(WorkInfo::getGeneration)
                .thenComparingLong(WorkInfo::getNextScheduleTimeMillis)
                .thenComparing(workInfo -> workInfo.getId().toString()));
    }

    @NonNull
    private static String formatWork(@NonNull Context context,
                                     @NonNull String label,
                                     @NonNull WorkInfo workInfo,
                                     @Nullable JobInfo jobInfo) {
        StringBuilder detail = new StringBuilder(context.getString(R.string.auto_backup_diagnostics_work,
                label,
                workInfo.getState().name().toLowerCase(Locale.ROOT),
                workInfo.getRunAttemptCount(),
                describeWorkStopReason(workInfo.getStopReason())));
        long nextSchedule = workInfo.getNextScheduleTimeMillis();
        if (nextSchedule > 0 && workInfo.getState() == WorkInfo.State.ENQUEUED) {
            detail.append(context.getString(R.string.auto_backup_diagnostics_next,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(new java.util.Date(nextSchedule))));
        }
        if (AndroidUtils.sdkAtLeast(Build.VERSION_CODES.BAKLAVA, 0) && jobInfo != null) {
            appendJobSchedulerDiagnostics(context, detail, jobInfo);
        }
        return detail.toString();
    }

    @Nullable
    private static JobInfo findJob(@NonNull Context context, @NonNull WorkInfo workInfo) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            return null;
        }
        List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
        String workSpecId = workInfo.getId().toString();
        for (JobInfo jobInfo : jobs) {
            PersistableBundle extras = jobInfo.getExtras();
            if (extras != null && workSpecId.equals(extras.getString(EXTRA_WORK_SPEC_ID))) {
                return jobInfo;
            }
        }
        return null;
    }

    private static void appendJobSchedulerDiagnostics(@NonNull Context context,
                                                      @NonNull StringBuilder detail,
                                                      @NonNull JobInfo jobInfo) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            return;
        }
        int[] pendingReasons = jobScheduler.getPendingJobReasons(jobInfo.getId());
        if (pendingReasons != null && pendingReasons.length > 0) {
            detail.append(context.getString(R.string.auto_backup_diagnostics_pending,
                    describePendingReasons(pendingReasons)));
        }
        List<PendingJobReasonsInfo> history = jobScheduler.getPendingJobReasonsHistory(jobInfo.getId());
        if (history != null && !history.isEmpty()) {
            PendingJobReasonsInfo recent = Collections.max(history,
                    Comparator.comparingLong(PendingJobReasonsInfo::getTimestampMillis));
            detail.append(context.getString(R.string.auto_backup_diagnostics_recent,
                    describePendingReasons(recent.getPendingJobReasons())));
        }
    }

    @NonNull
    private static String appendBatteryDiagnostics(@NonNull Context context, @NonNull String detail) {
        return detail + "; " + SelfBatteryOptimization.formatDiagnostics(context);
    }

    @NonNull
    @VisibleForTesting
    static String describeWorkStopReason(int reason) {
        switch (reason) {
            case WorkInfo.STOP_REASON_NOT_STOPPED:
                return "not stopped";
            case WorkInfo.STOP_REASON_CANCELLED_BY_APP:
                return "cancelled by app";
            case WorkInfo.STOP_REASON_PREEMPT:
                return "preempted";
            case WorkInfo.STOP_REASON_TIMEOUT:
                return "timeout";
            case WorkInfo.STOP_REASON_DEVICE_STATE:
                return "device state";
            case WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW:
                return "battery-not-low constraint";
            case WorkInfo.STOP_REASON_CONSTRAINT_CHARGING:
                return "charging constraint";
            case WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY:
                return "network constraint";
            case WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE:
                return "device-idle constraint";
            case WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW:
                return "storage-not-low constraint";
            case WorkInfo.STOP_REASON_QUOTA:
                return "quota";
            case WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION:
                return "background restriction";
            case WorkInfo.STOP_REASON_APP_STANDBY:
                return "app standby";
            case WorkInfo.STOP_REASON_USER:
                return "user";
            case WorkInfo.STOP_REASON_SYSTEM_PROCESSING:
                return "system processing";
            case WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED:
                return "estimated launch time changed";
            case WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT:
                return "foreground service timeout";
            case WorkInfo.STOP_REASON_UNKNOWN:
            default:
                return "unknown";
        }
    }

    @NonNull
    @VisibleForTesting
    static String describePendingReason(int reason) {
        switch (reason) {
            case JobScheduler.PENDING_JOB_REASON_UNDEFINED:
                return "undefined";
            case JobScheduler.PENDING_JOB_REASON_APP:
                return "app";
            case JobScheduler.PENDING_JOB_REASON_APP_STANDBY:
                return "app standby";
            case JobScheduler.PENDING_JOB_REASON_BACKGROUND_RESTRICTION:
                return "background restriction";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW:
                return "battery-not-low constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING:
                return "charging constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY:
                return "network constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER:
                return "content trigger";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_DEADLINE:
                return "deadline constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_DEVICE_IDLE:
                return "device-idle constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY:
                return "minimum-latency constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_PREFETCH:
                return "prefetch constraint";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW:
                return "storage-not-low constraint";
            case JobScheduler.PENDING_JOB_REASON_DEVICE_STATE:
                return "device state";
            case JobScheduler.PENDING_JOB_REASON_EXECUTING:
                return "executing";
            case JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID:
                return "invalid job id";
            case JobScheduler.PENDING_JOB_REASON_JOB_SCHEDULER_OPTIMIZATION:
                return "scheduler optimization";
            case JobScheduler.PENDING_JOB_REASON_QUOTA:
                return "quota";
            case JobScheduler.PENDING_JOB_REASON_USER:
                return "user";
            default:
                return "reason " + reason;
        }
    }

    @NonNull
    private static String describePendingReasons(@Nullable int[] reasons) {
        if (reasons == null || reasons.length == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int reason : reasons) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(describePendingReason(reason));
        }
        return sb.toString();
    }
}
