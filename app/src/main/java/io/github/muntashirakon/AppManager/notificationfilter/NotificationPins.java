// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;

import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Pinned copies: ongoing, with an Unpin button, for a while or until unpinned. Android 14 and later let the user swipe
 * an ongoing notification away, so a pinned one comes back once for each swipe until it's unpinned or runs out.
 */
public final class NotificationPins {
    private static final String TAG = "NotificationPins";
    /** When the pin runs out, or {@link #FOREVER}. Only pinned notifications carry it. */
    static final String EXTRA_PIN_UNTIL = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_PIN_UNTIL";
    static final String EXTRA_TAG = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_PIN_TAG";
    static final String ACTION_UNPIN = BuildConfig.APPLICATION_ID + ".action.NOTIFICATION_UNPIN";
    static final String ACTION_DISMISSED = BuildConfig.APPLICATION_ID + ".action.NOTIFICATION_PIN_DISMISSED";
    /** Pins made from the history. A pin made by a rule is a copy and keeps the copy's tag. */
    static final String PIN_TAG_PREFIX = "notification_filter_pin:";
    static final long FOREVER = Long.MAX_VALUE;
    /** Gives every pin's buttons their own PendingIntent: extras don't make two intents different. */
    private static final String URI_SCHEME = "amng-pin";

    /** The pins this process posted, so a swiped one can be put back while the listener is off. */
    private static final Map<String, Notification> sPosted = new ConcurrentHashMap<>();

    private NotificationPins() {
    }

    @NonNull
    static String historyTag(long entryId) {
        return PIN_TAG_PREFIX + entryId;
    }

    static long untilMillis(@NonNull NotificationFilterRule.PinLength length, long nowMillis,
                            @NonNull TimeZone zone) {
        switch (length) {
            case ONE_HOUR:
                return nowMillis + TimeUnit.HOURS.toMillis(1);
            case TODAY: {
                // The next local midnight, which isn't always 24 hours after the last one.
                Calendar calendar = Calendar.getInstance(zone);
                calendar.setTimeInMillis(nowMillis);
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                return calendar.getTimeInMillis();
            }
            case UNTIL_UNPINNED:
            default:
                return FOREVER;
        }
    }

    /** @return When the notification's pin runs out, or 0 when it isn't pinned. */
    static long pinnedUntil(@Nullable Notification notification) {
        if (notification == null || notification.extras == null) {
            return 0;
        }
        return notification.extras.getLong(EXTRA_PIN_UNTIL, 0);
    }

    private static boolean isLive(long untilMillis, long nowMillis) {
        return untilMillis == FOREVER || (untilMillis > 0 && nowMillis < untilMillis);
    }

    /**
     * @param reason Why it left the shade, as the listener was told. Only the user's own swipe or Clear all brings it
     *               back: the app or the Unpin button cancel it, and the system times it out.
     */
    static boolean shouldRepost(@NonNull Notification notification, int reason, long nowMillis) {
        if (reason != NotificationListenerService.REASON_CANCEL
                && reason != NotificationListenerService.REASON_CANCEL_ALL) {
            return false;
        }
        return isLive(pinnedUntil(notification), nowMillis);
    }

    /** The notification as it goes back up: on Android 8 and later the time left starts again from now. */
    @NonNull
    static Notification forRepost(@NonNull Context context, @NonNull Notification notification, long nowMillis) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // No timeout before Android 8, and the alarm set with the first post still runs.
            return notification;
        }
        long until = pinnedUntil(notification);
        if (until == FOREVER || until <= nowMillis) {
            return notification;
        }
        Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification);
        builder.setTimeoutAfter(until - nowMillis);
        return builder.build();
    }

    /** Makes the notification being built a pin under this tag: ongoing, with Unpin, running out at the given time. */
    @SuppressWarnings("deprecation")
    static void decorate(@NonNull Context context, @NonNull Notification.Builder builder, @NonNull String tag,
                         long untilMillis, long nowMillis) {
        PendingIntent unpin = broadcast(context, ACTION_UNPIN, tag);
        Notification.Action.Builder action;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            action = new Notification.Action.Builder(Icon.createWithResource(context,
                    R.drawable.ic_default_notification), context.getString(R.string.notification_filter_unpin),
                    unpin);
        } else {
            action = new Notification.Action.Builder(R.drawable.ic_default_notification,
                    context.getString(R.string.notification_filter_unpin), unpin);
        }
        // A swipe puts it back, so the app that posted the original isn't told it was dismissed.
        builder.setOngoing(true)
                .setAutoCancel(false)
                .addAction(action.build())
                .setDeleteIntent(broadcast(context, ACTION_DISMISSED, tag));
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_PIN_UNTIL, untilMillis);
        builder.addExtras(extras);
        if (untilMillis == FOREVER) {
            return;
        }
        long left = Math.max(1, untilMillis - nowMillis);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(left);
        } else {
            // No timeout before Android 8, so the Unpin button is pressed by an alarm.
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.set(AlarmManager.RTC, untilMillis, unpin);
            }
        }
    }

    /** A pin of a notification from the history, opening its app when tapped. */
    @NonNull
    @SuppressWarnings("deprecation")
    static Notification buildFromHistory(@NonNull Context context, @NonNull NotificationHistoryEntry entry,
                                         @Nullable Bitmap appIcon, @NonNull NotificationFilterRule.PinLength length,
                                         long nowMillis) {
        String tag = historyTag(entry.id);
        Notification.Builder builder = NotificationReposter.newBuilder(context, NotificationReposter.CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(entry.title.isEmpty() ? entry.appLabel : entry.title)
                .setContentText(entry.text)
                .setSubText(entry.appLabel)
                .setWhen(entry.timestampMillis)
                .setShowWhen(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent(context, entry.packageName));
        if (!TextUtils.isEmpty(entry.text)) {
            builder.setStyle(new Notification.BigTextStyle().bigText(entry.text));
        }
        if (appIcon != null) {
            builder.setLargeIcon(appIcon);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(0).setSound(null).setVibrate(null).setPriority(Notification.PRIORITY_LOW);
        }
        decorate(context, builder, tag, untilMillis(length, nowMillis, TimeZone.getDefault()), nowMillis);
        return builder.build();
    }

    /** @return Whether it was posted. Notifications may be turned off. */
    public static boolean pinFromHistory(@NonNull Context context, @NonNull NotificationHistoryEntry entry,
                                         @Nullable Bitmap appIcon, @NonNull NotificationFilterRule.PinLength length,
                                         long nowMillis) {
        NotificationReposter.ensureChannels(context);
        if (!NotificationReposter.canPostOn(context, NotificationReposter.CHANNEL_SILENT)) {
            return false;
        }
        String tag = historyTag(entry.id);
        Notification pin = buildFromHistory(context, entry, appIcon, length, nowMillis);
        if (!post(context, tag, pin)) {
            return false;
        }
        remember(tag, pin);
        return true;
    }

    /** Takes a pin down for good. */
    static void unpin(@NonNull Context context, @NonNull String tag) {
        // Forgotten first, so the delete intent that may follow doesn't put it back.
        sPosted.remove(tag);
        NotificationManagerCompat.from(context).cancel(tag, NotificationReposter.NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.cancel(broadcast(context, ACTION_UNPIN, tag));
            }
        }
    }

    /** Keeps the posted pin, so its delete intent can put it back while the listener is off. */
    static void remember(@NonNull String tag, @NonNull Notification notification) {
        if (pinnedUntil(notification) != 0) {
            sPosted.put(tag, notification);
        }
    }

    /** Puts a swiped-away pin back from what {@link #remember} kept, unless it's showing or ran out. */
    static void repostRemembered(@NonNull Context context, @NonNull String tag, long nowMillis) {
        Notification pin = sPosted.get(tag);
        if (pin == null) {
            // Unpinned, or this process started after it was posted: the listener puts it back if it's on.
            return;
        }
        if (!isLive(pinnedUntil(pin), nowMillis)) {
            // Its time ran out, which sends the delete intent too.
            sPosted.remove(tag);
            return;
        }
        if (isShowing(context, tag)) {
            // The listener put it back first.
            return;
        }
        Notification again = forRepost(context, pin, nowMillis);
        if (post(context, tag, again)) {
            sPosted.put(tag, again);
        }
    }

    /** For a pin that left the shade for good, or a copy that's no longer pinned. */
    static void forget(@NonNull String tag) {
        sPosted.remove(tag);
    }

    static void forgetAllForTesting() {
        sPosted.clear();
    }

    private static boolean isShowing(@NonNull Context context, @NonNull String tag) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return false;
        }
        for (StatusBarNotification sbn : manager.getActiveNotifications()) {
            if (tag.equals(sbn.getTag()) && sbn.getId() == NotificationReposter.NOTIFICATION_ID) {
                return true;
            }
        }
        return false;
    }

    private static boolean post(@NonNull Context context, @NonNull String tag, @NonNull Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(tag, NotificationReposter.NOTIFICATION_ID, notification);
            return true;
        } catch (SecurityException e) {
            // Notifications turned off between the check and the post.
            Log.w(TAG, "Could not post a pinned notification.", e);
            return false;
        }
    }

    @NonNull
    private static PendingIntent broadcast(@NonNull Context context, @NonNull String action, @NonNull String tag) {
        Intent intent = new Intent(context, NotificationPinReceiver.class)
                .setAction(action)
                .setData(Uri.fromParts(URI_SCHEME, tag, null))
                .putExtra(EXTRA_TAG, tag);
        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        if (pendingIntent == null) {
            // Only FLAG_NO_CREATE returns null.
            throw new IllegalStateException("No PendingIntent for " + action);
        }
        return pendingIntent;
    }

    @Nullable
    private static PendingIntent openIntent(@NonNull Context context, @NonNull String packageName) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            // Nothing to open, so the history it came from opens instead.
            launch = new Intent(context, NotificationHistoryActivity.class);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntentCompat.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT, false);
    }
}
