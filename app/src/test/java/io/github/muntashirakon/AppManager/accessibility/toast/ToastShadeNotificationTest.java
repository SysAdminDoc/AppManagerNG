// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class ToastShadeNotificationTest {
    private static final String ONE = "com.example.one";
    private static final long T0 = System.currentTimeMillis() - 60_000L;

    private Application mApp;
    private ToastHistory mHistory;
    private NotificationManager mManager;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mHistory = ToastHistoryTestAccess.useInMemory(mApp);
        mManager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @After
    public void tearDown() {
        Prefs.Privacy.setToastHistoryEnabled(false);
        Prefs.Privacy.setToastHistoryShadeEnabled(false);
        ToastHistoryTestAccess.reset();
    }

    @Nullable
    private Notification shown() {
        for (StatusBarNotification sbn : mManager.getActiveNotifications()) {
            if (ToastShadeNotification.NOTIFICATION_TAG.equals(sbn.getTag())
                    && sbn.getId() == ToastShadeNotification.NOTIFICATION_ID) {
                return sbn.getNotification();
            }
        }
        return null;
    }

    private static void bothOn() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        Prefs.Privacy.setToastHistoryShadeEnabled(true);
    }

    @Test
    public void itIsOffUntilTheUserTurnsItOn() {
        Prefs.Privacy.setToastHistoryEnabled(true);

        mHistory.record(T0, ONE, "Saved");

        assertFalse(Prefs.Privacy.isToastHistoryShadeEnabled());
        assertNull(shown());
    }

    @Test
    public void collapsedItShowsTheNewestAndExpandedTheLastFew() {
        bothOn();
        for (int i = 0; i < 10; ++i) {
            mHistory.record(T0 + i, ONE, "Toast " + i);
        }

        Notification notification = shown();

        assertNotNull(notification);
        assertEquals("Label of " + ONE, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Toast 9", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        String list = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        assertTrue(list.contains("Label of " + ONE + ": Toast 9"));
        assertTrue(list.contains("Toast 2"));
        assertFalse("only the newest " + ToastShadeNotification.SHOWN + " are listed", list.contains("Toast 1"));
        assertEquals(ToastShadeNotification.SHOWN, list.split("\n").length);
        assertEquals(T0 + 9, notification.when);
    }

    @Test
    public void itIsQuietAndTapOpensTheHistory() {
        bothOn();
        mHistory.record(T0, ONE, "Saved");

        Notification notification = shown();

        assertNotNull(notification);
        assertEquals(ToastShadeNotification.CHANNEL_ID, notification.getChannelId());
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
                .contains("Saved"));
        NotificationChannel channel = mManager.getNotificationChannel(ToastShadeNotification.CHANNEL_ID);
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.getImportance());
        assertNull(channel.getSound());
        assertFalse(channel.shouldVibrate());
        assertEquals(ToastHistoryActivity.class.getName(),
                shadowOf(notification.contentIntent).getSavedIntent().getComponent().getClassName());
    }

    @Test
    public void itFollowsNewToastsClearAndUndo() {
        bothOn();
        mHistory.record(T0, ONE, "First");
        mHistory.record(T0 + 1, ONE, "Second");
        assertEquals("Second", shown().extras.getCharSequence(Notification.EXTRA_TEXT).toString());

        AtomicReference<List<ToastEntry>> removed = new AtomicReference<>();
        mHistory.clear(removed::set);
        assertNull(shown());

        mHistory.restore(removed.get());
        assertEquals("Second", shown().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    }

    @Test
    public void turningEitherSwitchOffTakesItAway() {
        bothOn();
        mHistory.record(T0, ONE, "Saved");
        assertNotNull(shown());

        Prefs.Privacy.setToastHistoryShadeEnabled(false);
        mHistory.refreshShade();
        assertNull(shown());

        Prefs.Privacy.setToastHistoryShadeEnabled(true);
        mHistory.refreshShade();
        assertNotNull(shown());

        Prefs.Privacy.setToastHistoryEnabled(false);
        mHistory.refreshShade();
        assertNull(shown());
    }

    @Test
    public void withoutNotificationPermissionNothingIsPosted() {
        shadowOf(mApp).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        bothOn();

        mHistory.record(T0, ONE, "Saved");

        assertFalse(ToastShadeNotification.canPost(mApp));
        assertNull(shown());
    }

    @Test
    public void aToastIsOneLineAndOneWithNoTextSaysSo() {
        Notification notification = ToastShadeNotification.build(mApp, Arrays.asList(
                new ToastEntry(T0 + 1, ONE, "App", "Line one\nLine two"),
                new ToastEntry(T0, ONE, "App", "")));

        String list = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        assertTrue(list.contains("App: Line one Line two"));
        assertTrue(list.contains("App: " + mApp.getString(R.string.toast_history_no_text)));
        assertEquals(2, list.split("\n").length);
        assertEquals("Line one Line two", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    }
}
