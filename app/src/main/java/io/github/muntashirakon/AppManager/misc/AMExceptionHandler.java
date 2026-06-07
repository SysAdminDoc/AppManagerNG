// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class AMExceptionHandler implements Thread.UncaughtExceptionHandler {
    static final String CRASHES_DIR = "crashes";

    private final Thread.UncaughtExceptionHandler mDefaultExceptionHandler;
    private final Context mContext;

    public AMExceptionHandler(Context context) {
        mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        mContext = context;
    }

    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        // Collect info
        StackTraceElement[] arr = e.getStackTrace();
        StringBuilder report = new StringBuilder(e + "\n");
        for (StackTraceElement traceElement : arr) {
            report.append("    at ").append(traceElement.toString()).append("\n");
        }
        Throwable cause = e;
        while((cause = cause.getCause()) != null) {
            report.append(" Caused by: ").append(cause).append("\n");
            arr = cause.getStackTrace();
            for (StackTraceElement stackTraceElement : arr) {
                report.append("   at ").append(stackTraceElement.toString()).append("\n");
            }
        }
        report.append("\nDevice Info:\n");
        report.append(new DeviceInfo(mContext));

        // The local crash sink is opt-in. The notification share remains
        // user-initiated, but private on-disk crash JSON is written only when
        // the user explicitly enables it from Privacy settings.
        Uri crashUri = Prefs.Privacy.isLocalCrashSinkEnabled()
                ? LocalCrashSink.writeCrash(mContext, t, e, report.toString())
                : null;

        // Send notification
        Intent i = new Intent(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            i.setIdentifier(String.valueOf(System.currentTimeMillis()));
        }
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "AppManager NG: Crash Report");
        i.putExtra(Intent.EXTRA_TEXT, formatCrashReportForShare(report));
        if (crashUri != null) {
            i.putExtra(Intent.EXTRA_STREAM, crashUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // ClipData is required for FLAG_GRANT_READ_URI_PERMISSION to reach the chooser
            i.setClipData(ClipData.newRawUri("", crashUri));
        }
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(mContext, 0,
                Intent.createChooser(i, mContext.getText(R.string.send_crash_report)),
                PendingIntent.FLAG_ONE_SHOT, true);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(mContext)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(mContext.getText(R.string.app_name))
                .setContentTitle(mContext.getText(R.string.am_crashed))
                .setContentText(mContext.getText(R.string.tap_to_submit_crash_report))
                .setContentIntent(pendingIntent);
        NotificationUtils.displayHighPriorityNotification(mContext, builder.build());
        // Manage the rests via the default handler
        mDefaultExceptionHandler.uncaughtException(t, e);
    }

    @VisibleForTesting
    @NonNull
    static String formatCrashReportForShare(@NonNull CharSequence report) {
        return ExportTextUtils.toPlainTextReport(SupportInfoBundle.scrubForPublicIssue(report.toString()));
    }
}
