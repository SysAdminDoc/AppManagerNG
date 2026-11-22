// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;

/**
 * How another app's notification can be taken out of the shade, by Android version and mode. A listener's cancel
 * skips anything marked ongoing (and, before Android 13, anything from a foreground service), so those are snoozed
 * from Android 8, removed as root before that, or left alone.
 */
final class OngoingPolicy {
    enum Removal {
        /** The listener's own cancel. */
        CANCEL,
        /** Hidden until the snooze runs out, then checked again (Android 8 and later). */
        SNOOZE,
        /** Cancelled by the root server as if by the system (Android 5 to 7, not for a foreground service). */
        AS_ROOT,
        /** Can't be removed here, so it's left as it is. */
        NONE,
    }

    /** A quiet one comes back silently, so it's checked again often and an update shows soon. */
    static final long SHORT_SNOOZE_MILLIS = TimeUnit.MINUTES.toMillis(30);
    /** One that may sound or vibrate when it comes back is kept away longer, so it doesn't keep alerting. */
    static final long LONG_SNOOZE_MILLIS = TimeUnit.HOURS.toMillis(12);

    private OngoingPolicy() {
    }

    /**
     * @param flags The notification's flags.
     * @param root  Whether the root server is up. ADB and Shizuku run as the shell, which may not cancel another
     *              app's notification either.
     */
    @NonNull
    static Removal of(int flags, int sdkInt, boolean root) {
        boolean service = (flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
        // What NotificationManagerService refuses a listener's cancel for. "No clear" was never part of it.
        boolean refused = (flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (service && sdkInt < Build.VERSION_CODES.TIRAMISU);
        if (!refused) {
            return Removal.CANCEL;
        }
        if (sdkInt >= Build.VERSION_CODES.O) {
            return Removal.SNOOZE;
        }
        // Before Android 8 only the system itself may cancel a foreground service's notification.
        return root && !service ? Removal.AS_ROOT : Removal.NONE;
    }

    /** @param importance The notification's importance in the ranking, or a negative number when it's unknown. */
    static long snoozeMillis(int importance) {
        if (importance >= NotificationManagerCompat.IMPORTANCE_NONE
                && importance <= NotificationManagerCompat.IMPORTANCE_LOW) {
            return SHORT_SNOOZE_MILLIS;
        }
        return LONG_SNOOZE_MILLIS;
    }

    /** What the rule editor says happens to an ongoing notification on this phone. */
    @StringRes
    static int describe(int sdkInt, boolean root) {
        if (sdkInt >= Build.VERSION_CODES.O) {
            return R.string.notification_filter_ongoing_snooze;
        }
        return root ? R.string.notification_filter_ongoing_root : R.string.notification_filter_ongoing_none;
    }
}
