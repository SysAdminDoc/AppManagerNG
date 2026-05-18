// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager.BatchOpsInfo;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.AndroidUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class AutoBackupWorker extends Worker {
    static final String KEY_MANUAL = "manual";
    private static final String TAG = AutoBackupWorker.class.getSimpleName();
    private static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.AUTO_BACKUP";
    private static final int FOREGROUND_NOTIFICATION_ID = 0x4a11;
    private static final int RESULT_NOTIFICATION_ID = 0x4a12;

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    static Data manualInputData() {
        return new Data.Builder().putBoolean(KEY_MANUAL, true).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        boolean manual = getInputData().getBoolean(KEY_MANUAL, false);
        if (!manual && !Prefs.BackupRestore.isScheduledAutoBackupEnabled()) {
            AutoBackupScheduler.recordRunResult(context.getString(R.string.auto_backup_result_disabled));
            AutoBackupScheduler.refreshDiagnostics(context);
            return Result.success();
        }
        try {
            String runningMessage = context.getString(R.string.auto_backup_running);
            setForegroundAsync(createForegroundInfo(context.getString(R.string.auto_backup_notification_title),
                    runningMessage, 0, 0, true)).get();
            AutoBackupScheduler.recordRunStarted(runningMessage);
            List<UserPackagePair> pairs = AutoBackupScheduler.collectInstalledPackages(context);
            if (pairs.isEmpty()) {
                String message = context.getString(R.string.auto_backup_result_no_apps);
                AutoBackupScheduler.recordRunResult(message);
                AutoBackupScheduler.refreshDiagnostics(context);
                postResultNotification(context, message, false);
                return Result.success();
            }
            int flags = Prefs.BackupRestore.getBackupFlags() | BackupFlags.BACKUP_MULTIPLE;
            BatchBackupOptions options = new BatchBackupOptions(flags, null, null);
            AutoBackupProgressHandler progressHandler = new AutoBackupProgressHandler(context);
            progressHandler.onProgressStart(pairs.size(), 0,
                    context.getString(R.string.auto_backup_progress_preparing));
            BatchOpsManager.Result result = new BatchOpsManager().performOp(
                    BatchOpsInfo.fromUserPackagePair(BatchOpsManager.OP_BACKUP, pairs, options), progressHandler);
            progressHandler.onResult(null);
            int failed = result.getFailedPackages().size();
            int success = Math.max(0, pairs.size() - failed);
            String message = failed == 0
                    ? context.getResources().getQuantityString(R.plurals.auto_backup_result_success,
                            success, success)
                    : context.getString(R.string.auto_backup_result_partial, success, pairs.size(), failed);
            AutoBackupScheduler.recordRunResult(message);
            AutoBackupScheduler.refreshDiagnostics(context);
            postResultNotification(context, message, failed > 0);
            return Result.success(new Data.Builder()
                    .putInt("packages", pairs.size())
                    .putInt("failed", failed)
                    .build());
        } catch (Throwable th) {
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
    private ForegroundInfo createForegroundInfo(@NonNull String title,
                                                @NonNull String body,
                                                int progressMax,
                                                int progress,
                                                boolean progressIndeterminate) {
        Notification notification = buildForegroundNotification(getApplicationContext(),
                title, body, progressMax, progress, progressIndeterminate);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification);
    }

    @NonNull
    private Notification buildForegroundNotification(@NonNull Context context,
                                                     @NonNull String title,
                                                     @NonNull String body,
                                                     int progressMax,
                                                     int progress,
                                                     boolean progressIndeterminate) {
        if (AndroidUtils.sdkAtLeast(Build.VERSION_CODES.BAKLAVA, 0)) {
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
        int max = progressIndeterminate ? 0 : Math.max(progressMax, 0);
        int current = progressIndeterminate ? 0 : Math.max(0, Math.min(progressMax, progress));
        return buildNotification(context, title, body, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(max, current, progressIndeterminate)
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
