// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/** The words the screens use for actions and fields. */
final class FilterLabels {
    private FilterLabels() {
    }

    @StringRes
    static int of(@NonNull NotificationFilterRule.Action action) {
        switch (action) {
            case ALLOW:
                return R.string.notification_filter_action_allow;
            case SILENT:
                return R.string.notification_filter_action_silent;
            case VIBRATE:
                return R.string.notification_filter_action_vibrate;
            case KEEP_ICON:
                return R.string.notification_filter_action_keep_icon;
            case PIN:
                return R.string.signer_pin_action_pin;
            case BLOCK:
            default:
                return R.string.block;
        }
    }

    /** What the action does, in a sentence for the rule editor. */
    @StringRes
    static int summaryOf(@NonNull NotificationFilterRule.Action action) {
        switch (action) {
            case ALLOW:
                return R.string.notification_filter_action_allow_summary;
            case SILENT:
                return R.string.notification_filter_action_silent_summary;
            case VIBRATE:
                return R.string.notification_filter_action_vibrate_summary;
            case KEEP_ICON:
                return R.string.notification_filter_action_keep_icon_summary;
            case PIN:
                return R.string.notification_filter_action_pin_summary;
            case BLOCK:
            default:
                return R.string.notification_filter_action_block_summary;
        }
    }

    /** @param action An action name as the history saves it. */
    @StringRes
    static int ofSaved(@NonNull String action) {
        try {
            return of(NotificationFilterRule.Action.valueOf(action));
        } catch (IllegalArgumentException e) {
            return R.string.notification_history_action_none;
        }
    }

    @StringRes
    static int of(@NonNull NotificationFilterRule.Field field) {
        switch (field) {
            case TITLE:
                return R.string.notification_filter_field_title;
            case TEXT:
                return R.string.notification_filter_field_text;
            case SENDER:
                return R.string.notification_filter_field_sender;
            case ANY:
            default:
                return R.string.notification_filter_field_any;
        }
    }

    @StringRes
    static int of(@NonNull NotificationFilterRule.Priority priority) {
        switch (priority) {
            case HIGH:
                return R.string.notification_filter_priority_high;
            case LOW:
                return R.string.notification_filter_priority_low;
            case NORMAL:
            default:
                return R.string.notification_filter_priority_normal;
        }
    }

    @StringRes
    static int of(@NonNull NotificationFilterRule.PinLength length) {
        switch (length) {
            case ONE_HOUR:
                return R.string.notification_filter_pin_one_hour;
            case TODAY:
                return R.string.notification_filter_pin_today;
            case UNTIL_UNPINNED:
            default:
                return R.string.notification_filter_pin_until_unpinned;
        }
    }
}
