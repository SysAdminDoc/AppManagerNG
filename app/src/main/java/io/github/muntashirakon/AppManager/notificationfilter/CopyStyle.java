// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;

import androidx.annotation.NonNull;

/**
 * Where a copy goes and how it sorts, from its rule's action and priority. A higher priority is a more important
 * channel (the shade sorts by that first), then a newer time and an earlier sort key inside the app's group.
 */
final class CopyStyle {
    @NonNull
    final String channel;
    /** The priority before Android 8, which has no channels. */
    final int legacyPriority;
    /** Orders the copies inside one app's group. */
    @NonNull
    final String sortKey;
    /** Whether the copy takes the time it's posted, so it sorts above older ones. */
    final boolean whenIsNow;

    private CopyStyle(@NonNull String channel, int legacyPriority, @NonNull String sortKey, boolean whenIsNow) {
        this.channel = channel;
        this.legacyPriority = legacyPriority;
        this.sortKey = sortKey;
        this.whenIsNow = whenIsNow;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    static CopyStyle of(@NonNull NotificationFilterRule.Action action,
                        @NonNull NotificationFilterRule.Priority priority) {
        boolean high = priority == NotificationFilterRule.Priority.HIGH;
        boolean low = priority == NotificationFilterRule.Priority.LOW;
        String sortKey = high ? "0" : low ? "2" : "1";
        switch (action) {
            case VIBRATE:
                // A high priority that vibrates would pop up before Android 8, so it stops at default.
                return new CopyStyle(NotificationReposter.CHANNEL_VIBRATE,
                        low ? Notification.PRIORITY_LOW : Notification.PRIORITY_DEFAULT, sortKey, high);
            case KEEP_ICON:
                // Before Android 8 the minimum priority hides the status bar icon, so low stops at low.
                return new CopyStyle(NotificationReposter.CHANNEL_ICON, high ? Notification.PRIORITY_HIGH
                        : low ? Notification.PRIORITY_LOW : Notification.PRIORITY_DEFAULT, sortKey, high);
            case SILENT:
            case PIN:
            default:
                if (high) {
                    // Still silent, one importance up, so it sorts with the notifications that show an icon.
                    return new CopyStyle(NotificationReposter.CHANNEL_ICON, Notification.PRIORITY_HIGH, sortKey, true);
                }
                if (low) {
                    return new CopyStyle(NotificationReposter.CHANNEL_SILENT_LOW, Notification.PRIORITY_MIN, sortKey,
                            false);
                }
                return new CopyStyle(NotificationReposter.CHANNEL_SILENT, Notification.PRIORITY_LOW, sortKey, false);
        }
    }
}
