// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/** API 36 JobScheduler diagnostics with a privileged dumpsys fallback. */
public final class JobSchedulerCompat {
    private static final String TAG = "JobSchedulerCompat";
    private static final int MIN_PENDING_REASONS_API = 36;
    private static final int CONSTRAINT_CHARGING = 0x1;
    private static final int CONSTRAINT_TIMING_DELAY = 0x2;
    private static final int CONSTRAINT_DEADLINE = 0x4;
    private static final int CONSTRAINT_IDLE = 0x8;
    private static final int CONSTRAINT_UNMETERED = 0x10;
    private static final int CONSTRAINT_CONNECTIVITY = 0x20;
    private static final int CONSTRAINT_APP_NOT_IDLE = 0x40;
    private static final int CONSTRAINT_CONTENT_TRIGGER = 0x80;

    private JobSchedulerCompat() {
    }

    @NonNull
    @WorkerThread
    public static List<PendingJob> getPendingJobs(@NonNull Context context,
                                                  @NonNull String packageName,
                                                  @UserIdInt int userId) {
        if (Build.VERSION.SDK_INT < MIN_PENDING_REASONS_API) {
            return Collections.emptyList();
        }
        if (context.getPackageName().equals(packageName)) {
            try {
                return getOwnPendingJobs(context);
            } catch (Throwable th) {
                Log.w(TAG, "Could not inspect this app's pending jobs.", th);
                return Collections.emptyList();
            }
        }
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.DUMP)) {
            return Collections.emptyList();
        }
        try {
            Runner.Result result = Runner.runCommand(new String[]{
                    "dumpsys", "jobscheduler", packageName
            });
            if (!result.isSuccessful()) {
                return Collections.emptyList();
            }
            return parseDumpsys(result.getOutputAsList(), packageName, userId);
        } catch (Throwable th) {
            Log.w(TAG, "Could not inspect pending jobs for %s.", th, packageName);
            return Collections.emptyList();
        }
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private static List<PendingJob> getOwnPendingJobs(@NonNull Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return Collections.emptyList();
        }
        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        if (jobs == null || jobs.isEmpty()) {
            return Collections.emptyList();
        }
        List<PendingJob> pendingJobs = new ArrayList<>();
        for (JobInfo job : jobs) {
            int[] reasons = scheduler.getPendingJobReasons(job.getId());
            List<String> labels = new ArrayList<>();
            if (reasons != null) {
                for (int reason : reasons) {
                    labels.add(describePendingReason(reason));
                }
            }
            if (labels.isEmpty()) {
                labels.add(describePendingReason(JobScheduler.PENDING_JOB_REASON_UNDEFINED));
            }
            String service = job.getService() == null
                    ? ""
                    : job.getService().flattenToShortString();
            pendingJobs.add(new PendingJob(job.getId(), service, labels));
        }
        return pendingJobs;
    }

    @NonNull
    @VisibleForTesting
    static List<PendingJob> parseDumpsys(@NonNull List<String> lines,
                                         @NonNull String packageName,
                                         @UserIdInt int userId) {
        List<PendingJob> pendingJobs = new ArrayList<>();
        for (String line : lines) {
            PendingJob pendingJob = parseJobStatusLine(line, packageName, userId);
            if (pendingJob != null) {
                pendingJobs.add(pendingJob);
            }
        }
        return pendingJobs;
    }

    @Nullable
    @VisibleForTesting
    static PendingJob parseJobStatusLine(@NonNull String line,
                                         @NonNull String packageName,
                                         @UserIdInt int userId) {
        int marker = line.indexOf("JobStatus{");
        int hash = line.indexOf('#', marker);
        if (marker < 0 || hash < 0) {
            return null;
        }
        int slash = line.indexOf('/', hash);
        int idEnd = slash < 0 ? -1 : line.indexOf(' ', slash);
        if (slash < 0 || idEnd < 0) {
            return null;
        }
        String userToken = line.substring(hash + 1, slash);
        int parsedUserId = parseUserId(userToken);
        if (parsedUserId != userId) {
            return null;
        }
        int jobId;
        try {
            jobId = Integer.parseInt(line.substring(slash + 1, idEnd));
        } catch (NumberFormatException ignored) {
            return null;
        }
        int componentStart = idEnd + 1;
        int componentEnd = line.indexOf(' ', componentStart);
        if (componentEnd < 0) {
            componentEnd = line.indexOf('}', componentStart);
        }
        if (componentEnd <= componentStart) {
            return null;
        }
        String component = line.substring(componentStart, componentEnd);
        int componentSeparator = component.indexOf('/');
        if (componentSeparator <= 0) {
            return null;
        }
        String componentPackage = component.substring(0, componentSeparator);
        int namespaceSeparator = componentPackage.lastIndexOf('@');
        if (namespaceSeparator >= 0) {
            componentPackage = componentPackage.substring(namespaceSeparator + 1);
        }
        if (!packageName.equals(componentPackage)) {
            return null;
        }
        int unsatisfiedStart = line.indexOf("unsatisfied:0x", componentEnd);
        if (unsatisfiedStart < 0) {
            return null;
        }
        int reasonStart = unsatisfiedStart + "unsatisfied:0x".length();
        int reasonEnd = reasonStart;
        while (reasonEnd < line.length()
                && Character.digit(line.charAt(reasonEnd), 16) >= 0) {
            ++reasonEnd;
        }
        if (reasonStart == reasonEnd) {
            return null;
        }
        long unsatisfied;
        try {
            unsatisfied = Long.parseLong(line.substring(reasonStart, reasonEnd), 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (unsatisfied == 0) {
            return null;
        }
        return new PendingJob(jobId, component, describeDumpReasons(unsatisfied));
    }

    private static int parseUserId(@NonNull String userToken) {
        int start = userToken.startsWith("u") ? 1 : 0;
        int end = start;
        while (end < userToken.length() && Character.isDigit(userToken.charAt(end))) {
            ++end;
        }
        if (start == end) {
            return -1;
        }
        try {
            return Integer.parseInt(userToken.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @NonNull
    @VisibleForTesting
    static List<String> describeDumpReasons(long unsatisfied) {
        List<String> labels = new ArrayList<>();
        addMaskReason(labels, unsatisfied, CONSTRAINT_CHARGING, "charging constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_TIMING_DELAY, "minimum-latency constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_DEADLINE, "deadline constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_IDLE, "device-idle constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_UNMETERED, "unmetered-network constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_CONNECTIVITY, "network constraint");
        addMaskReason(labels, unsatisfied, CONSTRAINT_APP_NOT_IDLE, "app standby");
        addMaskReason(labels, unsatisfied, CONSTRAINT_CONTENT_TRIGGER, "content trigger");
        long knownMask = CONSTRAINT_CHARGING | CONSTRAINT_TIMING_DELAY | CONSTRAINT_DEADLINE
                | CONSTRAINT_IDLE | CONSTRAINT_UNMETERED | CONSTRAINT_CONNECTIVITY
                | CONSTRAINT_APP_NOT_IDLE | CONSTRAINT_CONTENT_TRIGGER;
        if ((unsatisfied & ~knownMask) != 0) {
            labels.add("system state");
        }
        if (labels.isEmpty()) {
            labels.add(String.format(Locale.ROOT, "constraint mask 0x%x", unsatisfied));
        }
        return labels;
    }

    private static void addMaskReason(@NonNull List<String> labels, long mask,
                                      long bit, @NonNull String label) {
        if ((mask & bit) != 0) {
            labels.add(label);
        }
    }

    @NonNull
    @VisibleForTesting
    static String describePendingReason(int reason) {
        if (Build.VERSION.SDK_INT < MIN_PENDING_REASONS_API) {
            return String.valueOf(reason);
        }
        switch (reason) {
            case JobScheduler.PENDING_JOB_REASON_UNDEFINED:
                return "ready or unknown";
            case JobScheduler.PENDING_JOB_REASON_APP:
                return "app state";
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
                return "user action";
            default:
                return String.format(Locale.ROOT, "reason %d", reason);
        }
    }

    public static final class PendingJob {
        private final int jobId;
        @NonNull
        private final String service;
        @NonNull
        private final List<String> reasons;

        private PendingJob(int jobId, @NonNull String service, @NonNull List<String> reasons) {
            this.jobId = jobId;
            this.service = service;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }

        public int getJobId() {
            return jobId;
        }

        @NonNull
        public String getService() {
            return service;
        }

        @NonNull
        public List<String> getReasons() {
            return reasons;
        }
    }
}
