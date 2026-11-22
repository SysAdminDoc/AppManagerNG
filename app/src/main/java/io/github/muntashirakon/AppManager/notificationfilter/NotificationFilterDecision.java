// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;

/** Decides what happens to one notification. Has no state, so every case can be tested on its own. */
public final class NotificationFilterDecision {
    /** What the decision needs to know about a notification. */
    public static final class Facts {
        @NonNull
        public final String packageName;
        public final int flags;
        public final boolean clearable;
        @Nullable
        public final String category;
        public final boolean fullScreen;
        public final boolean media;
        @NonNull
        public final NotificationFields fields;

        @VisibleForTesting
        public Facts(@NonNull String packageName, int flags, boolean clearable, @Nullable String category,
                     boolean fullScreen, boolean media, @NonNull NotificationFields fields) {
            this.packageName = packageName;
            this.flags = flags;
            this.clearable = clearable;
            this.category = category;
            this.fullScreen = fullScreen;
            this.media = media;
            this.fields = fields;
        }

        @NonNull
        public static Facts of(@NonNull StatusBarNotification sbn) {
            Notification notification = sbn.getNotification();
            return new Facts(sbn.getPackageName(), notification.flags, sbn.isClearable(), notification.category,
                    notification.fullScreenIntent != null, isMedia(notification),
                    NotificationFields.from(notification));
        }

        private static boolean isMedia(@NonNull Notification notification) {
            if (notification.extras == null) {
                return false;
            }
            if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                return true;
            }
            // Notification.EXTRA_TEMPLATE, which isn't public on every version.
            String template = notification.extras.getString("android.template");
            return template != null && template.contains("MediaStyle");
        }
    }

    /** The outcome. A {@code null} action means the notification is left alone. */
    public static final class Result {
        static final Result EXEMPT = new Result(null, null, false, true);
        static final Result UNTOUCHED = new Result(null, null, false, false);

        @Nullable
        public final NotificationFilterRule.Action action;
        /** The rule that matched, if one did. */
        @Nullable
        public final String ruleName;
        public final boolean byQuietMode;
        /** Never filtered at all, whatever the rules say, so it isn't recorded either. */
        public final boolean exempt;
        /** The matching rule's priority for its copy. Quiet mode's copies are normal. */
        @NonNull
        public final NotificationFilterRule.Priority priority;
        @NonNull
        public final NotificationFilterRule.PinLength pinFor;

        Result(@Nullable NotificationFilterRule.Action action, @Nullable String ruleName, boolean byQuietMode,
               boolean exempt) {
            this(action, ruleName, byQuietMode, exempt, NotificationFilterRule.Priority.NORMAL,
                    NotificationFilterRule.PinLength.UNTIL_UNPINNED);
        }

        Result(@Nullable NotificationFilterRule.Action action, @Nullable String ruleName, boolean byQuietMode,
               boolean exempt, @NonNull NotificationFilterRule.Priority priority,
               @NonNull NotificationFilterRule.PinLength pinFor) {
            this.action = action;
            this.ruleName = ruleName;
            this.byQuietMode = byQuietMode;
            this.exempt = exempt;
            this.priority = priority;
            this.pinFor = pinFor;
        }
    }

    private static final int ONGOING_FLAGS = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_FOREGROUND_SERVICE
            | Notification.FLAG_NO_CLEAR;

    private NotificationFilterDecision() {
    }

    /**
     * @param ownPackage  This app. Its own notifications, the copies included, are never touched.
     * @param quietAction What a notification no rule allows becomes while quiet mode is on, or {@code null} if it's
     *                    off.
     */
    @NonNull
    public static Result decide(@NonNull Facts facts, @NonNull String ownPackage,
                                @NonNull List<NotificationFilterRule> rules,
                                @Nullable NotificationFilterRule.Action quietAction) {
        if (isExempt(facts, ownPackage)) {
            return Result.EXEMPT;
        }
        boolean ongoing = isOngoing(facts);
        for (NotificationFilterRule rule : rules) {
            if (ongoing && !rule.ongoing) {
                continue;
            }
            if (rule.matches(facts.packageName, facts.fields)) {
                return new Result(rule.action, rule.name, false, false, rule.priority, rule.pinFor);
            }
        }
        if (ongoing) {
            // Nothing asked for it, so it stays out of the filter and the history, as it always did.
            return Result.EXEMPT;
        }
        if (quietAction != null && !isPlatform(facts.packageName)) {
            return new Result(quietAction, null, true, false);
        }
        return Result.UNTOUCHED;
    }

    /**
     * Whether the app keeps the notification up while something runs: ongoing, from a foreground service, or one the
     * user can't clear. Only a rule that asks for ongoing ones takes them.
     */
    public static boolean isOngoing(@NonNull Facts facts) {
        return (facts.flags & ONGOING_FLAGS) != 0 || !facts.clearable;
    }

    /** The system's own warnings, like low battery, aren't silenced by quiet mode. A rule can still name them. */
    private static boolean isPlatform(@NonNull String packageName) {
        return "android".equals(packageName) || "com.android.systemui".equals(packageName);
    }

    /**
     * Calls, alarms and media keep working, whatever the rules say. Group summaries are handled with their children.
     * Ongoing notifications aren't exempt here: {@link #decide} gives them only to a rule that asks for them.
     */
    public static boolean isExempt(@NonNull Facts facts, @NonNull String ownPackage) {
        if (ownPackage.equals(facts.packageName)) {
            return true;
        }
        if ((facts.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true;
        }
        if (Notification.CATEGORY_CALL.equals(facts.category) || Notification.CATEGORY_ALARM.equals(facts.category)) {
            return true;
        }
        return facts.fullScreen || facts.media;
    }
}
