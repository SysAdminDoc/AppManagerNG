// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * One quiet notification that keeps the newest toasts in the shade, for a toast that was gone before it could be
 * read. It never makes a sound, never vibrates and never pops up. It's there only while both Record toasts and the
 * shade switch are on.
 */
public final class ToastShadeNotification {
    private static final String TAG = "ToastShadeNotification";
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.TOAST_HISTORY";
    @VisibleForTesting
    static final String NOTIFICATION_TAG = "toast_history";
    @VisibleForTesting
    static final int NOTIFICATION_ID = 1;
    /** How many toasts the expanded notification lists. */
    @VisibleForTesting
    static final int SHOWN = 8;
    /** A long toast is cut in the list, the screen has the rest. */
    private static final int MAX_LINE_LENGTH = 160;
    private static final char ELLIPSIS = (char) 0x2026;

    private ToastShadeNotification() {
    }

    public static boolean isWanted() {
        return Prefs.Privacy.isToastHistoryEnabled() && Prefs.Privacy.isToastHistoryShadeEnabled();
    }

    /** Shows the newest toasts, or takes the notification away when it isn't wanted or there's nothing to show. */
    @WorkerThread
    static void update(@NonNull Context context, @NonNull ToastHistory history) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!isWanted()) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
            return;
        }
        List<ToastEntry> newest;
        try {
            newest = history.readNewest(SHOWN);
        } catch (SQLException e) {
            Log.w(TAG, "Could not read the newest toasts.", e);
            return;
        }
        if (newest.isEmpty()) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
            return;
        }
        if (!canPost(context)) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.toast_history_title))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setLightsEnabled(false)
                .setShowBadge(false)
                .build());
        try {
            manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, build(context, newest));
        } catch (SecurityException e) {
            // Permission taken away between the check and the post.
            Log.w(TAG, "Could not show the recent toasts.", e);
        }
    }

    public static boolean canPost(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** @param newest Newest first, not empty. */
    @VisibleForTesting
    @NonNull
    static Notification build(@NonNull Context context, @NonNull List<ToastEntry> newest) {
        ToastEntry latest = newest.get(0);
        DateFormat time = android.text.format.DateFormat.getTimeFormat(context);
        StringBuilder lines = new StringBuilder();
        for (ToastEntry entry : newest) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append(context.getString(R.string.toast_history_shade_line,
                    time.format(new Date(entry.timestampMillis)), entry.appLabel, oneLine(context, entry)));
        }
        Intent open = new Intent(context, ToastHistoryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openHistory = PendingIntentCompat.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        String title = context.getString(R.string.toast_history_title);
        // Toasts can carry codes and messages, so a locked screen shows only that there's something here.
        Notification hidden = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(title)
                .build();
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(latest.appLabel)
                .setContentText(oneLine(context, latest))
                .setSubText(title)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(lines))
                .setWhen(latest.timestampMillis)
                .setShowWhen(true)
                .setContentIntent(openHistory)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(hidden)
                .build();
    }

    @NonNull
    private static String oneLine(@NonNull Context context, @NonNull ToastEntry entry) {
        if (entry.text.isEmpty()) {
            return context.getString(R.string.toast_history_no_text);
        }
        String text = entry.text.replace('\n', ' ').trim();
        return text.length() > MAX_LINE_LENGTH ? text.substring(0, MAX_LINE_LENGTH - 1) + ELLIPSIS : text;
    }
}
