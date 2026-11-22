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
    /** Silent copies of low priority rules, below the other silent ones. */
    public static final String CHANNEL_SILENT_LOW = BuildConfig.APPLICATION_ID
            + ".channel.NOTIFICATION_FILTER_SILENT_LOW";
    /** Default importance with no sound or vibration, so the copy keeps its status bar icon. */
    public static final String CHANNEL_ICON = BuildConfig.APPLICATION_ID + ".channel.NOTIFICATION_FILTER_ICON";
    /** Every copy and summary uses this id. The tag tells them apart. */
    static final int NOTIFICATION_ID = 1;
    static final String COPY_TAG_PREFIX = "notification_filter:";
    static final String SUMMARY_TAG_PREFIX = "notification_filter_summary:";
    /** The app that posted the original, so the listener can find its copies again after a restart. */
    static final String EXTRA_SOURCE_PACKAGE = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_FILTER_PACKAGE";
    /** The rule action and priority a copy was made with, so the listener knows its style again after a restart. */
    static final String EXTRA_COPY_ACTION = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_FILTER_ACTION";
    static final String EXTRA_COPY_PRIORITY = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_FILTER_PRIORITY";
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
        return CopyStyle.of(action, NotificationFilterRule.Priority.NORMAL).channel;
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
        // Default importance keeps the status bar icon. With no sound and no vibration it stays quiet.
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ICON,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_filter_channel_icon))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(true)
                .build());
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_SILENT_LOW,
                NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(context.getString(R.string.notification_filter_channel_silent_low))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build());
    }

    /**
     * @return Whether a copy on the action's channel would be shown. If not, the original is left where it is, since
     * removing it would lose it.
     */
    static boolean canPost(@NonNull Context context, @NonNull NotificationFilterRule.Action action) {
        return canPostOn(context, channelOf(action));
    }

    /** Whether a notification on this channel would be shown. A channel not made yet will be, with its default. */
    static boolean canPostOn(@NonNull Context context, @NonNull String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannelCompat channel = manager.getNotificationChannelCompat(channelId);
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
        return buildCopy(context, sbn, action, NotificationFilterRule.Priority.NORMAL, appLabel, appIcon, alertAgain,
                false, System.currentTimeMillis(), 0);
    }

    /**
     * @param quiet          Never alerts, for a notification that was already in the shade when a rule took it.
     * @param pinUntilMillis For {@link NotificationFilterRule.Action#PIN}: when the pin runs out.
     */
    @NonNull
    static Notification buildCopy(@NonNull Context context, @NonNull StatusBarNotification sbn,
                                  @NonNull NotificationFilterRule.Action action,
                                  @NonNull NotificationFilterRule.Priority priority, @NonNull CharSequence appLabel,
                                  @Nullable Bitmap appIcon, boolean alertAgain, boolean quiet, long nowMillis,
                                  long pinUntilMillis) {
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
        CopyStyle style = CopyStyle.of(action, priority);
        String channel = style.channel;
        Notification.Builder builder = newBuilder(context, channel)
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
                .setOnlyAlertOnce(quiet || !alertAgain || (original.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0)
                .setSortKey(style.sortKey);
        setSmallIcon(context, builder, action, original);
        if (style.whenIsNow) {
            // A newer time sorts it above the app's older notifications.
            builder.setWhen(nowMillis).setShowWhen(true);
        } else if (original.when != 0) {
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
        ours.putString(EXTRA_COPY_ACTION, action.name());
        ours.putString(EXTRA_COPY_PRIORITY, priority.name());
        builder.addExtras(ours);
        if (groupsCopies()) {
            builder.setGroup(groupOf(sbn.getPackageName()));
        }
        if (action == NotificationFilterRule.Action.PIN) {
            // After the app's own buttons, so Unpin comes last.
            NotificationPins.decorate(context, builder, copyTag(sbn.getKey()),
                    pinUntilMillis > 0 ? pinUntilMillis : NotificationPins.FOREVER, nowMillis);
        }
        applyChannelBehavior(builder, action, style, quiet);
        return builder.build();
    }

    /**
     * Ours, which is what the shade shows for a filtered copy. A copy that keeps its status bar icon shows the app's
     * own icon there instead, from Android 6, where a notification can carry another app's icon.
     */
    private static void setSmallIcon(@NonNull Context context, @NonNull Notification.Builder builder,
                                     @NonNull NotificationFilterRule.Action action, @NonNull Notification original) {
        if (action == NotificationFilterRule.Action.KEEP_ICON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon icon = original.getSmallIcon();
            if (icon != null) {
                builder.setSmallIcon(icon);
                return;
            }
        }
        builder.setSmallIcon(R.drawable.ic_default_notification);
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
    static Notification.Builder newBuilder(@NonNull Context context, @NonNull String channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, channel);
        }
        return new Notification.Builder(context);
    }

    /**
     * Before channels, the copy carries its own sound, vibration and priority. A quiet copy is a group child that
     * leaves alerting to its summary, which never alerts, so even the vibrating channel stays still.
     */
    @SuppressWarnings("deprecation")
    private static void applyChannelBehavior(@NonNull Notification.Builder builder,
                                             @NonNull NotificationFilterRule.Action action, @NonNull CopyStyle style,
                                             boolean quiet) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setGroupAlertBehavior(quiet ? Notification.GROUP_ALERT_SUMMARY
                    : Notification.GROUP_ALERT_CHILDREN);
            return;
        }
        builder.setDefaults(0).setSound(null).setPriority(style.legacyPriority);
        if (action == NotificationFilterRule.Action.VIBRATE && !quiet) {
            builder.setVibrate(VIBRATION);
        } else {
            builder.setVibrate(null);
        }
    }
}
