// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.Manifest;
import android.app.Notification;
import android.app.RemoteInput;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;

/**
 * Builds the copy that stands in for a notification the filter silenced or turned into a vibration. The copy goes on
 * this app's own channels, which is what keeps it from popping up, and it hands every tap, reply and dismissal to the
 * app that posted the original.
 */
public final class NotificationReposter {
    public static final String CHANNEL_SILENT = BuildConfig.APPLICATION_ID + ".channel.NOTIFICATION_FILTER_SILENT";
    public static final String CHANNEL_VIBRATE = BuildConfig.APPLICATION_ID + ".channel.NOTIFICATION_FILTER_VIBRATE";
    /** Every copy and summary uses this id. The tag tells them apart. */
    static final int NOTIFICATION_ID = 1;
    static final String COPY_TAG_PREFIX = "notification_filter:";
    static final String SUMMARY_TAG_PREFIX = "notification_filter_summary:";
    /** The app that posted the original, so the listener can find its copies again after a restart. */
    static final String EXTRA_SOURCE_PACKAGE = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_FILTER_PACKAGE";
    private static final String GROUP_PREFIX = "notification_filter:";
    private static final long[] VIBRATION = {0, 250, 150, 250};

    private NotificationReposter() {
    }

    /** Copies are grouped per app only where the shade bundles groups. Older versions would hide the children. */
    static boolean groupsCopies() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    @NonNull
    static String copyTag(@NonNull String sourceKey) {
        return COPY_TAG_PREFIX + sourceKey;
    }

    @Nullable
    static String sourceKeyOf(@Nullable String tag) {
        return tag != null && tag.startsWith(COPY_TAG_PREFIX) ? tag.substring(COPY_TAG_PREFIX.length()) : null;
    }

    @NonNull
    static String summaryTag(@NonNull String packageName) {
        return SUMMARY_TAG_PREFIX + packageName;
    }

    @NonNull
    static String groupOf(@NonNull String packageName) {
        return GROUP_PREFIX + packageName;
    }

    @NonNull
    static String channelOf(@NonNull NotificationFilterRule.Action action) {
        return action == NotificationFilterRule.Action.VIBRATE ? CHANNEL_VIBRATE : CHANNEL_SILENT;
    }

    static void ensureChannels(@NonNull Context context) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_SILENT,
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_filter_channel_silent))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(true)
                .build());
        // Default importance is the lowest that may vibrate, and it never pops up.
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_VIBRATE,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_filter_channel_vibrate))
                .setSound(null, null)
                .setVibrationEnabled(true)
                .setVibrationPattern(VIBRATION)
                .setShowBadge(true)
                .build());
    }

    /**
     * @return Whether a copy on the action's channel would be shown. If not, the original is left where it is, since
     * removing it would lose it.
     */
    static boolean canPost(@NonNull Context context, @NonNull NotificationFilterRule.Action action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannelCompat channel = manager.getNotificationChannelCompat(channelOf(action));
        return channel == null || channel.getImportance() != NotificationManagerCompat.IMPORTANCE_NONE;
    }

    /**
     * @param appIcon    Shown as the large icon, so the copy says which app it came from.
     * @param alertAgain Whether the copy may vibrate again when it replaces an earlier copy of the same notification.
     */
    @NonNull
    static Notification buildCopy(@NonNull Context context, @NonNull StatusBarNotification sbn,
                                  @NonNull NotificationFilterRule.Action action, @NonNull CharSequence appLabel,
                                  @Nullable Bitmap appIcon, boolean alertAgain) {
        Notification original = sbn.getNotification();
        Bundle extras = original.extras != null ? original.extras : Bundle.EMPTY;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null) {
            title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        }
        CharSequence expanded = NotificationFields.expandedText(extras);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text == null) {
            text = expanded;
        }
        CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        String channel = channelOf(action);
        Notification.Builder builder = newBuilder(context, channel)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(title != null ? title : appLabel)
                .setContentText(text)
                .setSubText(TextUtils.isEmpty(subText) ? appLabel
                        : context.getString(R.string.notification_filter_copy_sub_text, appLabel, subText))
                .setContentIntent(original.contentIntent)
                .setDeleteIntent(original.deleteIntent)
                .setColor(original.color)
                .setCategory(original.category)
                .setVisibility(original.visibility)
                .setPublicVersion(newBuilder(context, channel)
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setContentTitle(appLabel)
                        .build())
                .setAutoCancel((original.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                .setOnlyAlertOnce(!alertAgain || (original.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
        if (original.when != 0) {
            builder.setWhen(original.when).setShowWhen(true);
        }
        if (appIcon != null) {
            builder.setLargeIcon(appIcon);
        }
        if (expanded != null && !TextUtils.equals(expanded, text)) {
            builder.setStyle(new Notification.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(expanded));
        }
        if (original.actions != null) {
            for (Notification.Action sourceAction : original.actions) {
                if (sourceAction != null && sourceAction.actionIntent != null) {
                    builder.addAction(copyAction(context, sourceAction));
                }
            }
        }
        Bundle ours = new Bundle();
        ours.putString(EXTRA_SOURCE_PACKAGE, sbn.getPackageName());
        builder.addExtras(ours);
        if (groupsCopies()) {
            builder.setGroup(groupOf(sbn.getPackageName()));
        }
        applyChannelBehavior(builder, action);
        return builder.build();
    }

    /** Holds the copies of one app together, and never alerts on its own. */
    @NonNull
    static Notification buildSummary(@NonNull Context context, @NonNull String packageName,
                                     @NonNull CharSequence appLabel, @Nullable Bitmap appIcon) {
        Notification.Builder builder = newBuilder(context, CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(appLabel)
                .setSubText(appLabel)
                .setGroup(groupOf(packageName))
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        if (appIcon != null) {
            builder.setLargeIcon(appIcon);
        }
        Bundle ours = new Bundle();
        ours.putString(EXTRA_SOURCE_PACKAGE, packageName);
        builder.addExtras(ours);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
        }
        return builder.build();
    }

    /**
     * Keeps the button's label, what it sends and any reply box. The icon is ours: the original names a resource in
     * the other app, which this app can't load, and the shade doesn't draw action icons anyway.
     */
    @VisibleForTesting
    @NonNull
    @SuppressWarnings("deprecation")
    static Notification.Action copyAction(@NonNull Context context, @NonNull Notification.Action source) {
        Notification.Action.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder = new Notification.Action.Builder(Icon.createWithResource(context,
                    R.drawable.ic_default_notification), source.title, source.actionIntent);
        } else {
            builder = new Notification.Action.Builder(R.drawable.ic_default_notification, source.title,
                    source.actionIntent);
        }
        builder.addExtras(source.getExtras());
        RemoteInput[] inputs = source.getRemoteInputs();
        if (inputs != null) {
            for (RemoteInput input : inputs) {
                builder.addRemoteInput(input);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setAllowGeneratedReplies(source.getAllowGeneratedReplies());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setSemanticAction(source.getSemanticAction());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAuthenticationRequired(source.isAuthenticationRequired());
        }
        return builder.build();
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private static Notification.Builder newBuilder(@NonNull Context context, @NonNull String channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, channel);
        }
        return new Notification.Builder(context);
    }

    /** Before channels, the copy carries its own sound, vibration and priority. */
    @SuppressWarnings("deprecation")
    private static void applyChannelBehavior(@NonNull Notification.Builder builder,
                                             @NonNull NotificationFilterRule.Action action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
            return;
        }
        builder.setDefaults(0).setSound(null);
        if (action == NotificationFilterRule.Action.VIBRATE) {
            builder.setPriority(Notification.PRIORITY_DEFAULT).setVibrate(VIBRATION);
        } else {
            builder.setPriority(Notification.PRIORITY_LOW).setVibrate(null);
        }
    }
}
