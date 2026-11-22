// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.notificationfilter.OngoingPolicy.Removal;

/** #32: the best way to take an ongoing notification out, per Android version and mode. */
public class OngoingPolicyTest {
    private static final int ONGOING = Notification.FLAG_ONGOING_EVENT;
    private static final int SERVICE = Notification.FLAG_FOREGROUND_SERVICE;
    private static final int NO_CLEAR = Notification.FLAG_NO_CLEAR;

    @Test
    public void aListenerCancelsWhatIsNotMarkedOngoing() {
        for (int sdk = 21; sdk <= 37; ++sdk) {
            assertEquals("sdk " + sdk, Removal.CANCEL, OngoingPolicy.of(0, sdk, false));
            // The listener's cancel never checked "no clear".
            assertEquals("sdk " + sdk, Removal.CANCEL, OngoingPolicy.of(NO_CLEAR, sdk, false));
        }
        // From Android 13 a service's notification counts as ongoing only when it's marked so.
        assertEquals(Removal.CANCEL, OngoingPolicy.of(SERVICE, 33, false));
        assertEquals(Removal.CANCEL, OngoingPolicy.of(SERVICE | NO_CLEAR, 36, false));
    }

    @Test
    public void ongoingOnesAreSnoozedFromAndroid8() {
        for (int sdk = 26; sdk <= 37; ++sdk) {
            assertEquals("sdk " + sdk, Removal.SNOOZE, OngoingPolicy.of(ONGOING, sdk, false));
            assertEquals("sdk " + sdk, Removal.SNOOZE, OngoingPolicy.of(ONGOING | SERVICE, sdk, true));
        }
        for (int sdk = 26; sdk <= 32; ++sdk) {
            assertEquals("sdk " + sdk, Removal.SNOOZE, OngoingPolicy.of(SERVICE, sdk, false));
        }
    }

    @Test
    public void beforeAndroid8OnlyRootRemovesThemAndNeverAServices() {
        for (int sdk = 21; sdk <= 25; ++sdk) {
            assertEquals("sdk " + sdk, Removal.AS_ROOT, OngoingPolicy.of(ONGOING, sdk, true));
            assertEquals("sdk " + sdk, Removal.AS_ROOT, OngoingPolicy.of(ONGOING | NO_CLEAR, sdk, true));
            assertEquals("sdk " + sdk, Removal.NONE, OngoingPolicy.of(ONGOING, sdk, false));
            assertEquals("sdk " + sdk, Removal.NONE, OngoingPolicy.of(SERVICE, sdk, true));
            assertEquals("sdk " + sdk, Removal.NONE, OngoingPolicy.of(ONGOING | SERVICE, sdk, true));
        }
    }

    @Test
    public void aQuietOneIsCheckedOftenAndOneThatMayAlertIsKeptAwayLonger() {
        assertEquals(OngoingPolicy.SHORT_SNOOZE_MILLIS, OngoingPolicy.snoozeMillis(NotificationManager.IMPORTANCE_MIN));
        assertEquals(OngoingPolicy.SHORT_SNOOZE_MILLIS, OngoingPolicy.snoozeMillis(NotificationManager.IMPORTANCE_LOW));
        assertEquals(OngoingPolicy.LONG_SNOOZE_MILLIS,
                OngoingPolicy.snoozeMillis(NotificationManager.IMPORTANCE_DEFAULT));
        assertEquals(OngoingPolicy.LONG_SNOOZE_MILLIS, OngoingPolicy.snoozeMillis(NotificationManager.IMPORTANCE_HIGH));
        assertEquals("unknown", OngoingPolicy.LONG_SNOOZE_MILLIS,
                OngoingPolicy.snoozeMillis(NotificationManager.IMPORTANCE_UNSPECIFIED));
        assertTrue(OngoingPolicy.SHORT_SNOOZE_MILLIS < OngoingPolicy.LONG_SNOOZE_MILLIS);
    }

    @Test
    public void theEditorSaysWhatHappensOnThisPhone() {
        assertEquals(R.string.notification_filter_ongoing_snooze, OngoingPolicy.describe(26, false));
        assertEquals(R.string.notification_filter_ongoing_snooze, OngoingPolicy.describe(36, true));
        assertEquals(R.string.notification_filter_ongoing_root, OngoingPolicy.describe(25, true));
        assertEquals(R.string.notification_filter_ongoing_root, OngoingPolicy.describe(21, true));
        assertEquals(R.string.notification_filter_ongoing_none, OngoingPolicy.describe(25, false));
        assertEquals(R.string.notification_filter_ongoing_none, OngoingPolicy.describe(21, false));
    }
}
