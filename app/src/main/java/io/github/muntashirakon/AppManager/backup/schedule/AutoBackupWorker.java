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

import androidx.annotation.NonNull;
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
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
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
            return Result.success();
        }
        try {
            String runningMessage = context.getString(R.string.auto_backup_running);
            setForegroundAsync(createForegroundInfo(runningMessage)).get();
            AutoBackupScheduler.recordRunStarted(runningMessage);
            List<UserPackagePair> pairs = AutoBackupScheduler.collectInstalledPackages(context);
            if (pairs.isEmpty()) {
                String message = context.getString(R.string.auto_backup_result_no_apps);
                AutoBackupScheduler.recordRunResult(message);
                postResultNotification(context, message, false);
                return Result.success();
            }
            int flags = Prefs.BackupRestore.getBackupFlags() | BackupFlags.BACKUP_MULTIPLE;
            BatchBackupOptions options = new BatchBackupOptions(flags, null, null);
            BatchOpsManager.Result result = new BatchOpsManager().performOp(
                    BatchOpsInfo.fromUserPackagePair(BatchOpsManager.OP_BACKUP, pairs, options), null);
            int failed = result.getFailedPackages().size();
            int success = Math.max(0, pairs.size() - failed);
            String message = failed == 0
                    ? context.getResources().getQuantityString(R.plurals.auto_backup_result_success,
                            success, success)
                    : context.getString(R.string.auto_backup_result_partial, success, pairs.size(), failed);
            AutoBackupScheduler.recordRunResult(message);
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
            postResultNotification(context, message, true);
            return Result.failure();
        }
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String body) {
        Notification notification = buildNotification(getApplicationContext(),
                getApplicationContext().getString(R.string.auto_backup_notification_title), body, false)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification);
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
    private NotificationCompat.Builder buildNotification(@NonNull Context context,
                                                        @NonNull String title,
                                                        @NonNull String body,
                                                        boolean alert) {
        NotificationUtils.getNewNotificationManager(context, CHANNEL_ID,
                context.getString(R.string.auto_backup_notification_channel),
                alert ? NotificationManagerCompat.IMPORTANCE_DEFAULT : NotificationManagerCompat.IMPORTANCE_LOW);
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setSmallIcon(R.drawable.ic_backup_restore)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(alert ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);
    }
}
