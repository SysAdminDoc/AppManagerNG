// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.format.DateUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupStorageCheck;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager.BatchOpsInfo;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ScheduledWorker;

public class AutoBackupWorker extends ScheduledWorker {
    static final String KEY_MANUAL = "manual";
    static final String KEY_ORIGIN = "origin";
    static final String KEY_CATCH_UP = "catch_up";
    // Where a run was started from. The worker records it with the run, so the status screen can
    // say what started the last backup.
    public static final String ORIGIN_SCHEDULE = "schedule";
    public static final String ORIGIN_SETTINGS = "settings";
    public static final String ORIGIN_HOME_SCREEN_SHORTCUT = "home_screen_shortcut";
    private static final String TAG = AutoBackupWorker.class.getSimpleName();
    private static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.AUTO_BACKUP";
    private static final int FOREGROUND_NOTIFICATION_ID_NAMESPACE = 0x4a11_0000;
    private static final int RESULT_NOTIFICATION_ID = 0x4a12;
    // Periodic and "Run now" runs use different unique work names, so WorkManager can run them
    // concurrently. Guard against that here: two simultaneous backups over the same due-package
    // set produce duplicate archives and race on the backup dir / Room rows.
    private static final AtomicBoolean sRunning = new AtomicBoolean(false);

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    static Data manualInputData(@NonNull String origin) {
        return new Data.Builder().putBoolean(KEY_MANUAL, true).putString(KEY_ORIGIN, origin).build();
    }

    /** A one-time run in place of a scheduled run that couldn't use the network. It counts as the schedule. */
    @NonNull
    static Data catchUpInputData() {
        return new Data.Builder().putBoolean(KEY_CATCH_UP, true).build();
    }

    /** Whether a scheduled or "Run now" backup is running in this process. */
    public static boolean isRunning() {
        return sRunning.get();
    }

    /**
     * Takes the single run slot and records who started the run. A run that finds the slot taken
     * records nothing, so it can't relabel the run that holds it.
     */
    @VisibleForTesting
    static boolean claimRun(@NonNull String origin) {
        if (!sRunning.compareAndSet(false, true)) {
            return false;
        }
        Log.i(TAG, "Auto-backup run started from " + (origin.isEmpty() ? "an unknown origin" : origin));
        AutoBackupScheduler.recordRunOrigin(origin);
        return true;
    }

    @VisibleForTesting
    static void releaseRun() {
        sRunning.set(false);
    }

    /**
     * The origin a run records: the schedule for periodic work, otherwise the origin its request
     * named. A manual run queued before origins were recorded, or with an origin this version
     * doesn't know, records none rather than a guess.
     */
    @NonNull
    static String originOf(@NonNull Data inputData) {
        if (!inputData.getBoolean(KEY_MANUAL, false)) {
            return ORIGIN_SCHEDULE;
        }
        String origin = inputData.getString(KEY_ORIGIN);
        if (ORIGIN_SETTINGS.equals(origin) || ORIGIN_HOME_SCREEN_SHORTCUT.equals(origin)) {
            return origin;
        }
        return "";
    }

    @NonNull
    @Override
    protected Result doScheduledWork() {
        Context context = getApplicationContext();
        boolean manual = getInputData().getBoolean(KEY_MANUAL, false);
        String origin = originOf(getInputData());
        if (!manual && !Prefs.BackupRestore.isScheduledAutoBackupEnabled()) {
            AutoBackupScheduler.recordRunOrigin(origin);
            AutoBackupScheduler.recordRunResult(context.getString(R.string.auto_backup_result_disabled));
            AutoBackupScheduler.refreshDiagnostics(context);
            return Result.success();
        }
        boolean catchUp = getInputData().getBoolean(KEY_CATCH_UP, false);
        if (!manual && !hasUsableNetwork(context, Prefs.BackupRestore.getScheduledBackupNetworkType())) {
            Log.i(TAG, "Scheduled auto-backup is waiting for a network it can use");
            if (!isRunning()) {
                // A backup that's running keeps its own status. Settings refreshes the diagnostics
                // when it opens.
                AutoBackupScheduler.recordRunOrigin(origin);
                AutoBackupScheduler.recordRunResult(context.getString(
                        R.string.auto_backup_result_waiting_for_network));
            }
            return holdScheduledRun(context, catchUp);
        }
        if (!claimRun(origin)) {
            // Periodic and "Run now" runs use different unique work names, so WorkManager can start
            // one while another runs. The run in flight backs up the same due packages.
            Log.w(TAG, "Auto-backup already running; skipping this run (origin=" + origin + ")");
            AutoBackupScheduler.recordRunResult(context.getString(R.string.auto_backup_result_already_running));
            AutoBackupScheduler.refreshDiagnostics(context);
            return manual ? Result.success() : holdScheduledRun(context, catchUp);
        }
        try {
            Result result = runBackup(context, manual, origin);
            if (isSameKind(result, Result.success())) {
                if (!catchUp) {
                    // This run backed up, so a catch-up still waiting from an earlier one isn't needed
                    AutoBackupScheduler.cancelCatchUpRun(context);
                }
                return result;
            }
            if (isSameKind(result, Result.retry()) && !manual) {
                return holdScheduledRun(context, catchUp);
            }
            return result;
        } finally {
            releaseRun();
        }
    }

    /**
     * Whether two results are both success, both retry or both failure. Result's subclasses are restricted API, and a
     * success can carry output data, so neither instanceof nor equals will do.
     */
    private static boolean isSameKind(@NonNull Result result, @NonNull Result kind) {
        return result.getClass() == kind.getClass();
    }

    /**
     * Ends a scheduled run that can't back up yet. A retry of periodic work would move every later
     * run to whenever this one finally ends, so the scheduled run ends here and keeps its time of
     * day, and a one-time run backs up in its place. That one-time run tries again itself.
     */
    @NonNull
    private static Result holdScheduledRun(@NonNull Context context, boolean catchUp) {
        if (catchUp) {
            return Result.retry();
        }
        AutoBackupScheduler.enqueueCatchUpRun(context);
        return Result.success();
    }

    /**
     * Whether a scheduled run may start under its network setting. WorkManager 2.10.5 reads the
     * network from its capabilities from Android 9 on, and those don't show that Android blocks
     * this app's network, so the constraint can pass while the network is blocked (b/465016918,
     * fixed in 2.11.1, and b/445324855, fixed in 2.11.2; both need minSdk 23). The run checks for
     * itself on every version, through the network info that does show the block. A run set to
     * unmetered networks also checks that the network it would use isn't metered, since the setting
     * can change after a catch-up run is queued.
     */
    @VisibleForTesting
    @SuppressWarnings("deprecation") // NetworkInfo is what reports the block for this app
    static boolean hasUsableNetwork(@NonNull Context context, int networkType) {
        int type = AutoBackupScheduler.sanitizeNetworkType(networkType);
        if (type == AutoBackupScheduler.NETWORK_NOT_REQUIRED) {
            return true;
        }
        ConnectivityManager cm = ContextCompat.getSystemService(context, ConnectivityManager.class);
        if (cm == null) {
            return true;
        }
        NetworkInfo info;
        boolean metered;
        try {
            info = cm.getActiveNetworkInfo();
            metered = type == AutoBackupScheduler.NETWORK_UNMETERED && cm.isActiveNetworkMetered();
        } catch (SecurityException e) {
            // Some builds refuse the call (b/480123154). The constraint decides, as it did before.
            Log.w(TAG, "Couldn't read the network state", e);
            return true;
        }
        return info != null && info.isConnected() && !metered;
    }

    /**
     * Runs one backup. {@link #doScheduledWork()} holds the single run slot around it. Split out so a test
     * can count the runs that get past the checks before it.
     *
     * @param manual whether someone started the run by hand
     * @param origin what started the run, as recorded with it
     */
    @VisibleForTesting
    @NonNull
    Result runBackup(@NonNull Context context, boolean manual, @NonNull String origin) {
        try {
            String runningMessage = context.getString(R.string.auto_backup_running);
            try {
                setForegroundAsync(createForegroundInfo(context.getString(R.string.auto_backup_notification_title),
                        runningMessage, 0, 0, true)).get();
            } catch (Exception fgErr) {
                // Promoting to a foreground service can fail (background-restricted
                // app, OEM FGS limits). Surface that distinctly and retry rather than
                // logging it as a generic "backup failed". doScheduledWork turns a scheduled
                // run's retry into a catch-up run, so the schedule keeps its time.
                if (fgErr instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Log.w(TAG, "Auto-backup could not start foreground service", fgErr);
                String message = context.getString(R.string.auto_backup_result_could_not_start);
                AutoBackupScheduler.recordRunResult(message);
                AutoBackupScheduler.refreshDiagnostics(context);
                postResultNotification(context, message, true);
                return Result.retry();
            }
            AutoBackupScheduler.recordRunStarted();
            List<UserPackagePair> pairs = AutoBackupScheduler.collectInstalledPackages(context);
            if (pairs.isEmpty()) {
                String message = context.getString(R.string.auto_backup_result_no_apps);
                AutoBackupScheduler.recordRunResult(message);
                AutoBackupScheduler.refreshDiagnostics(context);
                postResultNotification(context, message, false);
                return Result.success();
            }
            AutoBackupScheduler.BackupSelection selection = AutoBackupScheduler.selectPackagesDueForBackup(
                    pairs,
                    AutoBackupScheduler.collectExistingBackups(pairs),
                    Prefs.BackupRestore.getScheduledBackupMinimumAgeDays(),
                    System.currentTimeMillis());
            pairs = selection.getDuePackages();
            int skipped = selection.getSkippedPackages();
            for (AutoBackupScheduler.SkippedPackage sp : selection.getSkippedDetails()) {
                Log.i(TAG, "Auto-backup skipped " + sp.packageName + " (u" + sp.userId + "): "
                        + sp.reason + "; last backup at " + sp.lastBackupMillis);
            }
            if (pairs.isEmpty()) {
                String message = context.getResources().getQuantityString(
                        R.plurals.auto_backup_result_all_recent, skipped, skipped);
                AutoBackupScheduler.recordRunResult(message, selection.getSkippedDetails());
                AutoBackupScheduler.refreshDiagnostics(context);
                postResultNotification(context, message, false);
                return Result.success(new Data.Builder()
                        .putInt("packages", 0)
                        .putInt("failed", 0)
                        .putInt("skipped_recent", skipped)
                        .build());
            }
            List<String> packageNames = new ArrayList<>(pairs.size());
            for (UserPackagePair pair : pairs) {
                packageNames.add(pair.getPackageName());
            }
            int flags = Prefs.BackupRestore.getBackupFlags() | BackupFlags.BACKUP_MULTIPLE;
            BackupStorageCheck.Result storageResult = BackupStorageCheck.evaluateAggregateWithTagPolicies(
                    packageNames, flags);
            if (storageResult.status == BackupStorageCheck.Status.INSUFFICIENT) {
                String message = context.getString(R.string.auto_backup_result_insufficient_storage,
                        Formatter.formatFileSize(context, storageResult.estimatedBytes),
                        Formatter.formatFileSize(context, storageResult.freeBytes));
                AutoBackupScheduler.recordRunResult(message, selection.getSkippedDetails());
                AutoBackupScheduler.refreshDiagnostics(context);
                postResultNotification(context, message, true);
                return Result.success(new Data.Builder()
                        .putInt("packages", 0)
                        .putInt("failed", 0)
                        .putInt("skipped_storage", pairs.size())
                        .build());
            }
            if (storageResult.status != BackupStorageCheck.Status.OK) {
                Log.w(TAG, "Storage preflight warning: " + storageResult.status
                        + " (estimated=" + storageResult.estimatedBytes
                        + ", free=" + storageResult.freeBytes + ")");
            }
            BatchBackupOptions options = BatchBackupOptions.forTagPolicies(flags, null, null,
                    null, false, null);
            AutoBackupProgressHandler progressHandler = new AutoBackupProgressHandler(context);
            progressHandler.onProgressStart(pairs.size(), 0,
                    context.getString(R.string.auto_backup_progress_preparing));
            BatchOpsManager batchOpsManager = new BatchOpsManager();
            BatchOpsManager.Result result;
            try {
                result = batchOpsManager.performOp(
                        BatchOpsInfo.fromUserPackagePair(BatchOpsManager.OP_BACKUP, pairs, options), progressHandler);
            } finally {
                // Flush and close the batch-ops log writer; otherwise per-package failure
                // diagnostics from the unattended run are dropped and the file handle leaks.
                batchOpsManager.conclude();
            }
            progressHandler.onResult(null);
            int failed = result.getFailedPackages().size();
            int success = Math.max(0, pairs.size() - failed);
            String message = buildResultMessage(context, success, pairs.size(), failed, skipped);
            AutoBackupScheduler.recordRunResult(message, selection.getSkippedDetails());
            AutoBackupScheduler.refreshDiagnostics(context);
            postResultNotification(context, message, failed > 0);
            return Result.success(new Data.Builder()
                    .putInt("packages", pairs.size())
                    .putInt("failed", failed)
                    .putInt("skipped_recent", skipped)
                    .build());
        } catch (Exception th) {
            if (th instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.w(TAG, "Scheduled auto-backup failed", th);
            String message = context.getString(R.string.auto_backup_result_failed,
                    th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName());
            AutoBackupScheduler.recordRunResult(message);
            AutoBackupScheduler.refreshDiagnostics(context);
            postResultNotification(context, message, true);
            return Result.failure();
        }
    }

    @NonNull
    private String buildResultMessage(@NonNull Context context, int success, int total, int failed, int skipped) {
        if (failed == 0) {
            String successMessage = context.getResources().getQuantityString(R.plurals.auto_backup_result_success,
                    success, success);
            return skipped > 0
                    ? context.getResources().getQuantityString(
                            R.plurals.auto_backup_result_success_with_skipped, skipped, successMessage, skipped)
                    : successMessage;
        }
        return skipped > 0
                ? context.getString(R.string.auto_backup_result_partial_with_skipped, success, total, failed, skipped)
                : context.getString(R.string.auto_backup_result_partial, success, total, failed);
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title,
                                                @NonNull String body,
                                                int progressMax,
                                                int progress,
                                                boolean progressIndeterminate) {
        Notification notification = buildForegroundNotification(getApplicationContext(),
                title, body, progressMax, progress, progressIndeterminate);
        int notificationId = foregroundNotificationIdFor(getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(notificationId, notification);
    }

    static int foregroundNotificationIdFor(@NonNull UUID workerId) {
        long most = workerId.getMostSignificantBits();
        long least = workerId.getLeastSignificantBits();
        int mixed = (int) (most ^ (most >>> 32) ^ least ^ (least >>> 32));
        return FOREGROUND_NOTIFICATION_ID_NAMESPACE | (mixed & 0x0000ffff);
    }

    @NonNull
    private Notification buildForegroundNotification(@NonNull Context context,
                                                     @NonNull String title,
                                                     @NonNull String body,
                                                     int progressMax,
                                                     int progress,
                                                     boolean progressIndeterminate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            return buildProgressStyleNotification(context, title, body, progressMax, progress,
                    progressIndeterminate);
        }
        int max = progressIndeterminate ? 0 : Math.max(progressMax, 0);
        int current = progressIndeterminate ? 0 : Math.max(0, Math.min(progressMax, progress));
        return buildNotification(context, title, body, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(max, current, progressIndeterminate)
                .build();
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @NonNull
    private Notification buildProgressStyleNotification(@NonNull Context context,
                                                         @NonNull String title,
                                                         @NonNull String body,
                                                         int progressMax,
                                                         int progress,
                                                         boolean progressIndeterminate) {
        NotificationUtils.getNewNotificationManager(context, CHANNEL_ID,
                context.getString(R.string.auto_backup_notification_channel),
                NotificationManagerCompat.IMPORTANCE_LOW);
        Notification.ProgressStyle style = new Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressIndeterminate(progressIndeterminate);
        if (!progressIndeterminate && progressMax > 0) {
            style.addProgressSegment(new Notification.ProgressStyle.Segment(progressMax));
            int safeProgress = Math.max(0, Math.min(progressMax, progress));
            if (safeProgress > 0 && safeProgress < progressMax) {
                style.addProgressPoint(new Notification.ProgressStyle.Point(safeProgress));
            }
            style.setProgress(safeProgress);
        }
        return new Notification.Builder(context, CHANNEL_ID)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setSmallIcon(R.drawable.ic_backup_restore)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(style)
                .setContentIntent(createContentIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void postResultNotification(@NonNull Context context, @NonNull String body, boolean alert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat manager = NotificationUtils.getNewNotificationManager(context, CHANNEL_ID,
                context.getString(R.string.auto_backup_notification_channel),
                alert ? NotificationManagerCompat.IMPORTANCE_DEFAULT : NotificationManagerCompat.IMPORTANCE_LOW);
        manager.notify(RESULT_NOTIFICATION_ID, buildNotification(context,
                context.getString(R.string.auto_backup_notification_title), body, alert).build());
    }

    @NonNull
    private static PendingIntent createContentIntent(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntentCompat.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
    }

    @NonNull
    private NotificationCompat.Builder buildNotification(@NonNull Context context,
                                                        @NonNull String title,
                                                        @NonNull String body,
                                                        boolean alert) {
        NotificationUtils.getNewNotificationManager(context, CHANNEL_ID,
                context.getString(R.string.auto_backup_notification_channel),
                alert ? NotificationManagerCompat.IMPORTANCE_DEFAULT : NotificationManagerCompat.IMPORTANCE_LOW);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setSmallIcon(R.drawable.ic_backup_restore)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(createContentIntent(context))
                .setAutoCancel(true)
                .setPriority(alert ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);
    }

    private final class AutoBackupProgressHandler extends ProgressHandler {
        @NonNull
        private final Context mContext;
        private final long mStartedAtMillis = System.currentTimeMillis();
        private volatile int mLastMax = MAX_INDETERMINATE;
        private volatile float mLastProgress;
        @Nullable
        private volatile Object mLastMessage;
        @Nullable
        private volatile Object mActiveStageMessage;

        private AutoBackupProgressHandler(@NonNull Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public void onAttach(@Nullable android.app.Service service, @NonNull Object message) {
            onProgressStart(MAX_INDETERMINATE, 0, message);
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
            mLastMax = max;
            mLastProgress = current;
            if (message != null) {
                mLastMessage = message;
            }
            updateForeground(mActiveStageMessage != null ? mActiveStageMessage : mLastMessage);
        }

        @Override
        public void onResult(@Nullable Object message) {
            // Final success/failure is posted by AutoBackupWorker.postResultNotification().
        }

        @Override
        public void onDetach(@Nullable android.app.Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return new AutoBackupSubProgressHandler(this);
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return mLastMessage;
        }

        @Override
        public int getLastMax() {
            return mLastMax;
        }

        @Override
        public float getLastProgress() {
            return mLastProgress;
        }

        @Override
        public void postUpdate(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }

        private void onSubProgressUpdate(@Nullable Object message) {
            if (message != null) {
                mActiveStageMessage = message;
            }
            updateForeground(mActiveStageMessage);
        }

        private void updateForeground(@Nullable Object message) {
            int max = mLastMax;
            int current = max > 0 ? Math.max(0, Math.min(max, (int) mLastProgress)) : 0;
            String body = buildProgressBody(max, current, message);
            setForegroundAsync(createForegroundInfo(mContext.getString(R.string.auto_backup_notification_title),
                    body, max, current, max <= 0));
        }

        @NonNull
        private String buildProgressBody(int max, int current, @Nullable Object message) {
            CharSequence stage = message instanceof CharSequence ? (CharSequence) message : null;
            String eta = buildEta(max, current);
            if (stage != null && stage.length() > 0 && max > 0) {
                return eta != null
                        ? mContext.getString(R.string.auto_backup_progress_stage_eta, stage, current, max, eta)
                        : mContext.getString(R.string.auto_backup_progress_stage, stage, current, max);
            }
            if (max > 0) {
                return eta != null
                        ? mContext.getString(R.string.auto_backup_progress_apps_eta, current, max, eta)
                        : mContext.getString(R.string.auto_backup_progress_apps, current, max);
            }
            return stage != null && stage.length() > 0
                    ? stage.toString()
                    : mContext.getString(R.string.auto_backup_running);
        }

        @Nullable
        private String buildEta(int max, int current) {
            if (max <= 0 || current <= 0 || current >= max) {
                return null;
            }
            long elapsedMillis = Math.max(0, System.currentTimeMillis() - mStartedAtMillis);
            long remainingMillis = (elapsedMillis * (max - current)) / current;
            if (remainingMillis < 15_000L) {
                return null;
            }
            return DateUtils.formatElapsedTime(remainingMillis / 1000L);
        }
    }

    private static final class AutoBackupSubProgressHandler extends ProgressHandler {
        @NonNull
        private final AutoBackupProgressHandler mParent;
        private volatile int mLastMax = MAX_INDETERMINATE;
        private volatile float mLastProgress;
        @Nullable
        private volatile Object mLastMessage;

        private AutoBackupSubProgressHandler(@NonNull AutoBackupProgressHandler parent) {
            mParent = parent;
        }

        @Override
        public void onAttach(@Nullable android.app.Service service, @NonNull Object message) {
            onProgressStart(MAX_INDETERMINATE, 0, message);
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
            mLastMax = max;
            mLastProgress = current;
            if (message != null) {
                mLastMessage = message;
            }
            mParent.onSubProgressUpdate(mLastMessage);
        }

        @Override
        public void onResult(@Nullable Object message) {
        }

        @Override
        public void onDetach(@Nullable android.app.Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return this;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return mLastMessage;
        }

        @Override
        public int getLastMax() {
            return mLastMax;
        }

        @Override
        public float getLastProgress() {
            return mLastProgress;
        }

        @Override
        public void postUpdate(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }
    }
}
