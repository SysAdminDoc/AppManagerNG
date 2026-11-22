// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.PinLength;

/** #32: pinned copies, from a rule or from the history. */
@RunWith(RobolectricTestRunner.class)
public class NotificationPinsTest {
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final String CHAT = "com.example.chat";

    private Application mApp;
    private NotificationManager mManager;
    private long mNow;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mManager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
        mNow = System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        NotificationPins.forgetAllForTesting();
    }

    @NonNull
    private static NotificationHistoryEntry entry(long id) {
        NotificationHistoryEntry entry = new NotificationHistoryEntry(1_000L, CHAT, "Chat",
                new NotificationFields("Carol", "Gate 12, boarding at 9:40", "Carol"), "SILENT", null, false);
        entry.id = id;
        return entry;
    }

    @Nullable
    private Notification shown(@NonNull String tag) {
        for (StatusBarNotification sbn : mManager.getActiveNotifications()) {
            if (tag.equals(sbn.getTag())) {
                return sbn.getNotification();
            }
        }
        return null;
    }

    @NonNull
    private static Intent sent(@NonNull PendingIntent pendingIntent) {
        return shadowOf(pendingIntent).getSavedIntent();
    }

    @Test
    public void pinLengths() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 21, 15, 30, 0);
        Calendar midnight = Calendar.getInstance(zone);
        midnight.clear();
        midnight.set(2026, Calendar.NOVEMBER, 22, 0, 0, 0);
        long at = now.getTimeInMillis();

        assertEquals(at + HOUR, NotificationPins.untilMillis(PinLength.ONE_HOUR, at, zone));
        assertEquals(NotificationPins.FOREVER, NotificationPins.untilMillis(PinLength.UNTIL_UNPINNED, at, zone));
        assertEquals(midnight.getTimeInMillis(), NotificationPins.untilMillis(PinLength.TODAY, at, zone));
        // A second before midnight still means the end of that day.
        long late = midnight.getTimeInMillis() - 1_000L;
        assertEquals(midnight.getTimeInMillis(), NotificationPins.untilMillis(PinLength.TODAY, late, zone));

        // The day the clocks go back is 25 hours long.
        Calendar fallBack = Calendar.getInstance(zone);
        fallBack.clear();
        fallBack.set(2026, Calendar.NOVEMBER, 1, 0, 30, 0);
        Calendar nextDay = Calendar.getInstance(zone);
        nextDay.clear();
        nextDay.set(2026, Calendar.NOVEMBER, 2, 0, 0, 0);
        assertEquals(nextDay.getTimeInMillis(),
                NotificationPins.untilMillis(PinLength.TODAY, fallBack.getTimeInMillis(), zone));
    }

    @Test
    public void onlyTheUsersOwnSwipeBringsAPinBack() {
        Notification pin = NotificationPins.buildFromHistory(mApp, entry(1), null, PinLength.ONE_HOUR, mNow);
        Notification forever = NotificationPins.buildFromHistory(mApp, entry(2), null, PinLength.UNTIL_UNPINNED,
                mNow);
        Notification plain = new Notification.Builder(mApp, NotificationReposter.CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_default_notification).build();

        assertEquals(mNow + HOUR, NotificationPins.pinnedUntil(pin));
        assertEquals(0, NotificationPins.pinnedUntil(plain));
        assertTrue(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_CANCEL, mNow));
        assertTrue(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_CANCEL_ALL, mNow));
        assertFalse(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_APP_CANCEL, mNow));
        assertFalse(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_TIMEOUT, mNow));
        assertFalse(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_CLICK, mNow));
        assertFalse(NotificationPins.shouldRepost(pin, NotificationListenerService.REASON_CANCEL, mNow + HOUR));
        assertFalse(NotificationPins.shouldRepost(plain, NotificationListenerService.REASON_CANCEL, mNow));
        assertTrue(NotificationPins.shouldRepost(forever, NotificationListenerService.REASON_CANCEL,
                mNow + 1_000 * HOUR));
    }

    @Test
    public void aPinFromTheHistoryIsOngoingWithUnpin() {
        Notification pin = NotificationPins.buildFromHistory(mApp, entry(7), null, PinLength.ONE_HOUR, mNow);

        assertTrue((pin.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertEquals("Carol", pin.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Gate 12, boarding at 9:40", pin.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals("Chat", pin.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString());
        assertEquals(NotificationReposter.CHANNEL_SILENT, pin.getChannelId());
        assertEquals(HOUR, pin.getTimeoutAfter());
        assertNotNull(pin.contentIntent);
        assertNotNull(pin.actions);
        Notification.Action unpin = pin.actions[pin.actions.length - 1];
        assertEquals(mApp.getString(R.string.notification_filter_unpin), unpin.title.toString());
        Intent unpinIntent = sent(unpin.actionIntent);
        assertEquals(NotificationPinReceiver.class.getName(), unpinIntent.getComponent().getClassName());
        assertEquals(NotificationPins.ACTION_UNPIN, unpinIntent.getAction());
        assertEquals(NotificationPins.historyTag(7), unpinIntent.getStringExtra(NotificationPins.EXTRA_TAG));
        Intent dismissed = sent(pin.deleteIntent);
        assertEquals(NotificationPinReceiver.class.getName(), dismissed.getComponent().getClassName());
        assertEquals(NotificationPins.ACTION_DISMISSED, dismissed.getAction());

        Notification forever = NotificationPins.buildFromHistory(mApp, entry(8), null, PinLength.UNTIL_UNPINNED,
                mNow);
        assertEquals(0, forever.getTimeoutAfter());
        // Each pin has its own buttons.
        assertEquals(NotificationPins.historyTag(8),
                sent(forever.actions[forever.actions.length - 1].actionIntent)
                        .getStringExtra(NotificationPins.EXTRA_TAG));
    }

    @Test
    public void aRepostedPinRunsOutWhenItWasMeantTo() {
        Notification pin = NotificationPins.buildFromHistory(mApp, entry(7), null, PinLength.ONE_HOUR, mNow);

        Notification again = NotificationPins.forRepost(mApp, pin, mNow + TimeUnit.MINUTES.toMillis(20));

        assertEquals(TimeUnit.MINUTES.toMillis(40), again.getTimeoutAfter());
        assertEquals(mNow + HOUR, NotificationPins.pinnedUntil(again));
        assertTrue((again.flags & Notification.FLAG_ONGOING_EVENT) != 0);
    }

    @Test
    public void pinningFromTheHistoryPostsItAndUnpinTakesItDown() {
        assertTrue(NotificationPins.pinFromHistory(mApp, entry(7), null, PinLength.ONE_HOUR, mNow));
        String tag = NotificationPins.historyTag(7);
        Notification pin = shown(tag);
        assertNotNull(pin);

        // Swiped away with the listener off: the delete intent puts it back.
        mManager.cancel(tag, NotificationReposter.NOTIFICATION_ID);
        new NotificationPinReceiver().onReceive(mApp, sent(pin.deleteIntent));
        assertNotNull(shown(tag));

        new NotificationPinReceiver().onReceive(mApp, sent(pin.actions[pin.actions.length - 1].actionIntent));
        assertNull(shown(tag));
        // A late delete intent after Unpin changes nothing.
        new NotificationPinReceiver().onReceive(mApp, sent(pin.deleteIntent));
        assertNull(shown(tag));
    }

    @Test
    public void nothingIsPinnedWhenNotificationsAreOff() {
        shadowOf(mApp).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        assertFalse(NotificationPins.pinFromHistory(mApp, entry(7), null, PinLength.ONE_HOUR, mNow));
        assertNull(shown(NotificationPins.historyTag(7)));
    }

    /** Before Android 8 there's no timeout, so an alarm unpins it. */
    @Config(sdk = Build.VERSION_CODES.N)
    @Test
    public void beforeAndroid8AnAlarmUnpinsIt() {
        assertTrue(NotificationPins.pinFromHistory(mApp, entry(7), null, PinLength.ONE_HOUR, mNow));

        AlarmManager alarms = (AlarmManager) mApp.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarms).getNextScheduledAlarm();
        assertNotNull(alarm);
        assertEquals(mNow + HOUR, alarm.getTriggerAtMs());
        Intent unpin = sent(alarm.operation);
        assertEquals(NotificationPins.ACTION_UNPIN, unpin.getAction());
        assertEquals(NotificationPins.historyTag(7), unpin.getStringExtra(NotificationPins.EXTRA_TAG));
    }
}
