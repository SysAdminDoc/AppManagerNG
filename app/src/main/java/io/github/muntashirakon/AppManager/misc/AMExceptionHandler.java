// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class AMExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "AMExceptionHandler";
    static final String CRASHES_DIR = "crashes";
    /**
     * How long to wait after posting the notification. The default handler kills the process at
     * once, and on Android 14 and later the notification can be lost if the binder call to the
     * notification manager hasn't gone out yet (upstream edfae0b04).
     */
    @VisibleForTesting
    static final long NOTIFICATION_GRACE_MILLIS = 250;

    @Nullable
    private final Thread.UncaughtExceptionHandler mDefaultExceptionHandler;
    private final Context mContext;

    public AMExceptionHandler(Context context) {
        this(context, Thread.getDefaultUncaughtExceptionHandler());
    }

    @VisibleForTesting
    AMExceptionHandler(Context context, @Nullable Thread.UncaughtExceptionHandler defaultExceptionHandler) {
        mDefaultExceptionHandler = defaultExceptionHandler;
        mContext = context;
    }

    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        try {
            report(t, e);
        } catch (Throwable reportError) {
            Log.e(TAG, "Unable to report the crash", reportError);
        } finally {
            // Manage the rests via the default handler
            if (mDefaultExceptionHandler != null) {
                mDefaultExceptionHandler.uncaughtException(t, e);
            }
        }
    }

    private void report(@NonNull Thread t, @NonNull Throwable e) {
        String report = buildReport(e);

        // The local crash sink is opt-in. The notification share remains
        // user-initiated, but private on-disk crash JSON is written only when
        // the user explicitly enables it from Privacy settings.
        Uri crashUri = null;
        try {
            crashUri = writeCrashSink(t, e, report);
        } catch (Throwable sinkError) {
            Log.e(TAG, "Unable to save the crash", sinkError);
        }

        // Send notification
        Intent i = buildCrashShareIntent(report, crashUri, System.currentTimeMillis());
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(mContext, 0,
                Intent.createChooser(i, mContext.getText(R.string.send_crash_report)),
                PendingIntent.FLAG_ONE_SHOT, false);
        NotificationCompat.Builder builder = NotificationUtils.getCrashNotificationBuilder(mContext)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setTicker(mContext.getText(R.string.app_name))
                .setContentTitle(mContext.getText(R.string.am_crashed))
                .setContentText(mContext.getText(R.string.tap_to_submit_crash_report))
                .setContentIntent(pendingIntent);
        if (displayNotification(builder.build())) {
            pause(NOTIFICATION_GRACE_MILLIS);
        }
    }

    @NonNull
    private String buildReport(@NonNull Throwable e) {
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
        try {
            report.append(describeDevice());
        } catch (Throwable deviceInfoError) {
            // The stack trace is what matters, so send it without the device details
            Log.e(TAG, "Unable to collect device info", deviceInfoError);
            report.append("Unavailable (").append(deviceInfoError.getClass().getName()).append(")\n");
        }
        return report.toString();
    }

    @VisibleForTesting
    @NonNull
    String describeDevice() {
        return new DeviceInfo(mContext).toString();
    }

    @VisibleForTesting
    @Nullable
    Uri writeCrashSink(@NonNull Thread t, @NonNull Throwable e, @NonNull String report) {
        return Prefs.Privacy.isLocalCrashSinkEnabled()
                ? LocalCrashSink.writeCrash(mContext, t, e, report)
                : null;
    }

    @VisibleForTesting
    boolean displayNotification(@NonNull Notification notification) {
        return NotificationUtils.displayCrashNotification(mContext, notification);
    }

    @VisibleForTesting
    void pause(long millis) {
        SystemClock.sleep(millis);
    }

    @VisibleForTesting
    @NonNull
    static String formatCrashReportForShare(@NonNull CharSequence report) {
        return ExportTextUtils.toPlainTextReport(SupportInfoBundle.scrubForPublicIssue(report.toString()));
    }

    @VisibleForTesting
    @NonNull
    static Intent buildCrashShareIntent(@NonNull CharSequence report, @Nullable Uri crashUri,
                                        long identifierMillis) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.setIdentifier(String.valueOf(identifierMillis));
        }
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "AppManager NG: Crash Report");
        intent.putExtra(Intent.EXTRA_TEXT, formatCrashReportForShare(report));
        if (crashUri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, crashUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // ClipData is required for FLAG_GRANT_READ_URI_PERMISSION to reach the chooser.
            intent.setClipData(ClipData.newRawUri("", crashUri));
        }
        return intent;
    }
}
