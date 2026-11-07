// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.app.Notification;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;

/**
 * Tells a toast from the other things that arrive as {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED}, so
 * that nothing but toasts is ever written down. A notification carries its {@link Notification} as the parcel, a
 * toast carries none.
 *
 * <p>What reaches us depends on the Android version:
 * <ul>
 *     <li>Up to Android 10 every toast is drawn in the sending app, which sends the event itself.</li>
 *     <li>From Android 11, a plain text toast from an app targeting 30 or later is drawn by SystemUI. SystemUI still
 *     names the app as the event's package, so the source stays right, but a vendor build that drops that could
 *     report SystemUI instead, and we record whatever package the event names.</li>
 *     <li>From Android 11, a custom view toast from an app in the background is blocked and never shown, so there is
 *     no event and nothing to record. One shown from the foreground is drawn by the app and arrives as before,
 *     possibly with no text at all.</li>
 * </ul>
 */
public final class ToastEventFilter {
    /** Far more than a toast can show, and it stops one entry from crowding out the others in the store. */
    public static final int MAX_TEXT_LENGTH = 2000;
    @VisibleForTesting
    static final long MAX_DELIVERY_DELAY_MILLIS = 60_000L;
    private static final String NOTIFICATION_CLASS = "android.app.Notification";
    /** Android 11 and later name the toast itself, older versions its nested {@code TN} class. */
    private static final String TOAST_CLASS_PREFIX = "android.widget.Toast";

    private ToastEventFilter() {
    }

    public static final class CapturedToast {
        @NonNull
        public final String packageName;
        /** Empty for a toast that only shows a custom view with no readable text. */
        @NonNull
        public final String text;

        CapturedToast(@NonNull String packageName, @NonNull String text) {
            this.packageName = packageName;
            this.text = text;
        }
    }

    /**
     * Must be called while the event is being delivered, as it gets recycled afterwards.
     *
     * @param ownPackage this app's package. Its own toasts are skipped since a toast about a failure to save a toast
     *                   would otherwise feed itself.
     * @return The toast, or {@code null} if the event isn't one.
     */
    @Nullable
    public static CapturedToast read(@NonNull AccessibilityEvent event, @NonNull String ownPackage) {
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                || event.getParcelableData() instanceof Notification
                || event.isPassword()) {
            return null;
        }
        CharSequence className = event.getClassName();
        // A missing parcel is not enough. When the phone is locked, a notification hiding its content carries its
        // public version as the parcel, which may be null, with the private ticker text still in the event.
        if (className != null && NOTIFICATION_CLASS.contentEquals(className)) {
            return null;
        }
        CharSequence packageName = event.getPackageName();
        if (TextUtils.isEmpty(packageName) || ownPackage.contentEquals(packageName)) {
            return null;
        }
        String text = readText(event);
        if (text.isEmpty() && !(className != null && className.toString().startsWith(TOAST_CLASS_PREFIX))) {
            return null;
        }
        return new CapturedToast(packageName.toString(), text);
    }

    /**
     * Works out when the toast was shown, since delivery is held back by the service's notification timeout.
     *
     * @param eventUptimeMillis {@link AccessibilityEvent#getEventTime()}
     * @param uptimeNowMillis   {@link android.os.SystemClock#uptimeMillis()}
     * @param wallNowMillis     {@link System#currentTimeMillis()}
     */
    public static long wallClockMillis(long eventUptimeMillis, long uptimeNowMillis, long wallNowMillis) {
        long delay = uptimeNowMillis - eventUptimeMillis;
        if (eventUptimeMillis <= 0 || delay < 0 || delay > MAX_DELIVERY_DELAY_MILLIS) {
            // The sender didn't set a usable time.
            return wallNowMillis;
        }
        return wallNowMillis - delay;
    }

    @NonNull
    private static String readText(@NonNull AccessibilityEvent event) {
        List<CharSequence> lines = event.getText();
        StringBuilder text = new StringBuilder();
        for (CharSequence line : lines) {
            if (!TextUtils.isEmpty(line)) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(line);
            }
        }
        if (text.length() == 0 && !TextUtils.isEmpty(event.getContentDescription())) {
            text.append(event.getContentDescription());
        }
        return cap(text.toString().trim());
    }

    @NonNull
    private static String cap(@NonNull String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        // Never leave half of a surrogate pair behind.
        int end = Character.isHighSurrogate(text.charAt(MAX_TEXT_LENGTH - 1)) ? MAX_TEXT_LENGTH - 1 : MAX_TEXT_LENGTH;
        return text.substring(0, end);
    }
}
