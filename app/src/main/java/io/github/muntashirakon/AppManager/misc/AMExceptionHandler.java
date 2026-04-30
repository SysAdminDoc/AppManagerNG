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
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.io.Paths;

public class AMExceptionHandler implements Thread.UncaughtExceptionHandler {
    static final String CRASHES_DIR = "crashes";
    private static final int MAX_CRASH_FILES = 10;

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

        // Write crash to file for persistence (survives notification dismissal)
        Uri crashUri = writeCrashToFile(report.toString());

        // Send notification
        Intent i = new Intent(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            i.setIdentifier(String.valueOf(System.currentTimeMillis()));
        }
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "AppManager NG: Crash Report");
        String body = report.toString();
        i.putExtra(Intent.EXTRA_TEXT, body);
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

    private Uri writeCrashToFile(@NonNull String report) {
        try {
            File crashDir = new File(mContext.getFilesDir(), CRASHES_DIR);
            if (!crashDir.exists() && !crashDir.mkdirs()) {
                return null;
            }
            pruneOldCrashes(crashDir);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    .format(new Date());
            File crashFile = new File(crashDir, "crash_" + timestamp + ".log");
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(crashFile), StandardCharsets.UTF_8)) {
                writer.write(report);
            }
            return FmProvider.getContentUri(Paths.get(crashFile));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void pruneOldCrashes(@NonNull File crashDir) {
        File[] files = crashDir.listFiles();
        if (files == null || files.length < MAX_CRASH_FILES) return;
        // Delete oldest files to stay under MAX_CRASH_FILES
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i <= files.length - MAX_CRASH_FILES; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
