// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ToastEventFilterTest {
    private static final String OWN = "com.example.self";
    private static final String SENDER = "com.example.sender";
    private static final String TOAST_CLASS = "android.widget.Toast";
    private static final String NOTIFICATION_CLASS = "android.app.Notification";

    @NonNull
    private static AccessibilityEvent event(@Nullable String className, @Nullable String text) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setPackageName(SENDER);
        event.setClassName(className);
        if (text != null) {
            event.getText().add(text);
        }
        return event;
    }

    @Test
    public void aNullParcelIsAToastWithItsSenderAndText() {
        ToastEventFilter.CapturedToast toast = ToastEventFilter.read(event(TOAST_CLASS, "Saved"), OWN);

        assertNotNull(toast);
        assertEquals(SENDER, toast.packageName);
        assertEquals("Saved", toast.text);
    }

    @Test
    public void aNotificationParcelIsNotLogged() {
        AccessibilityEvent event = event(TOAST_CLASS, "A new message");
        event.setParcelableData(new Notification());

        assertNull(ToastEventFilter.read(event, OWN));
    }

    /** What the system sends for a notification hiding its content on the lock screen: no parcel, private text. */
    @Test
    public void aNotificationWithNoParcelIsNotLogged() {
        assertNull(ToastEventFilter.read(event(NOTIFICATION_CLASS, "The private message"), OWN));
    }

    @Test
    public void theOlderToastClassNameCounts() {
        assertNotNull(ToastEventFilter.read(event("android.widget.Toast$TN", "Saved"), OWN));
    }

    @Test
    public void anEventWithNoClassNameButSomeTextCounts() {
        assertNotNull(ToastEventFilter.read(event(null, "Saved"), OWN));
    }

    @Test
    public void otherEventTypesAreNotToasts() {
        AccessibilityEvent event = event(TOAST_CLASS, "A button");
        event.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);

        assertNull(ToastEventFilter.read(event, OWN));
    }

    @Test
    public void thisAppsOwnToastsAreSkippedSoAFailureToastCantLoop() {
        AccessibilityEvent event = event(TOAST_CLASS, "Couldn't save");
        event.setPackageName(OWN);

        assertNull(ToastEventFilter.read(event, OWN));
    }

    @Test
    public void anEventWithNoSenderIsSkipped() {
        AccessibilityEvent event = event(TOAST_CLASS, "Saved");
        event.setPackageName(null);

        assertNull(ToastEventFilter.read(event, OWN));
    }

    @Test
    public void passwordTextIsNeverRead() {
        AccessibilityEvent event = event(TOAST_CLASS, "hunter2");
        event.setPassword(true);

        assertNull(ToastEventFilter.read(event, OWN));
    }

    @Test
    public void aToastWithOnlyACustomViewIsKeptWithNoText() {
        ToastEventFilter.CapturedToast toast = ToastEventFilter.read(event(TOAST_CLASS, null), OWN);

        assertNotNull(toast);
        assertEquals("", toast.text);
    }

    @Test
    public void anEmptyEventThatDoesNotNameAToastIsSkipped() {
        assertNull(ToastEventFilter.read(event(null, null), OWN));
        assertNull(ToastEventFilter.read(event("com.example.Thing", null), OWN));
    }

    @Test
    public void everyLineIsKept() {
        AccessibilityEvent event = event(TOAST_CLASS, "First");
        event.getText().add("");
        event.getText().add("Second");

        ToastEventFilter.CapturedToast toast = ToastEventFilter.read(event, OWN);

        assertNotNull(toast);
        assertEquals("First\nSecond", toast.text);
    }

    @Test
    public void theContentDescriptionStandsInForMissingText() {
        AccessibilityEvent event = event(TOAST_CLASS, null);
        event.setContentDescription("An image of a tick");

        ToastEventFilter.CapturedToast toast = ToastEventFilter.read(event, OWN);

        assertNotNull(toast);
        assertEquals("An image of a tick", toast.text);
    }

    @Test
    public void longTextIsCappedWithoutSplittingACharacter() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < ToastEventFilter.MAX_TEXT_LENGTH - 1; ++i) {
            text.append('a');
        }
        // One character that takes two UTF-16 units, straddling the limit.
        text.appendCodePoint(0x1F600).append("tail");

        ToastEventFilter.CapturedToast toast = ToastEventFilter.read(event(TOAST_CLASS, text.toString()), OWN);

        assertNotNull(toast);
        assertEquals(ToastEventFilter.MAX_TEXT_LENGTH - 1, toast.text.length());
        assertTrue(toast.text.endsWith("a"));
    }

    @Test
    public void theTimeIsWhenTheToastWasShownNotWhenItWasDelivered() {
        assertEquals(9_900L, ToastEventFilter.wallClockMillis(1_000L, 1_100L, 10_000L));
    }

    @Test
    public void anUnusableEventTimeFallsBackToNow() {
        assertEquals(10_000L, ToastEventFilter.wallClockMillis(0L, 1_100L, 10_000L));
        assertEquals(10_000L, ToastEventFilter.wallClockMillis(2_000L, 1_100L, 10_000L));
        long tooOld = ToastEventFilter.MAX_DELIVERY_DELAY_MILLIS + 1;
        assertEquals(10_000L, ToastEventFilter.wallClockMillis(1L, 1L + tooOld, 10_000L));
    }
}
