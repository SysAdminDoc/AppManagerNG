// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Process;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Priority;

@RunWith(RobolectricTestRunner.class)
public class NotificationReposterTest {
    private static final String CHAT = "com.example.chat";

    private Application mApp;
    private PendingIntent mOpen;
    private PendingIntent mDismissed;
    private PendingIntent mReply;
    private PendingIntent mMarkRead;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mOpen = PendingIntent.getActivity(mApp, 1, new Intent("open"), PendingIntent.FLAG_IMMUTABLE);
        mDismissed = PendingIntent.getBroadcast(mApp, 2, new Intent("dismissed"), PendingIntent.FLAG_IMMUTABLE);
        mReply = PendingIntent.getBroadcast(mApp, 3, new Intent("reply"), PendingIntent.FLAG_MUTABLE);
        mMarkRead = PendingIntent.getBroadcast(mApp, 4, new Intent("read"), PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private static StatusBarNotification sbn(@NonNull Notification notification) {
        return new StatusBarNotification(CHAT, CHAT, 7, "tag", 10_123, 0, 0, notification, Process.myUserHandle(),
                1_000L);
    }

    @NonNull
    private Notification original() {
        RemoteInput input = new RemoteInput.Builder("reply_text").setLabel("Reply").build();
        return new Notification.Builder(mApp, "their_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Carol")
                .setContentText("Are you coming?")
                .setSubText("Work")
                .setStyle(new Notification.BigTextStyle().bigText("Are you coming? We start at six."))
                .setContentIntent(mOpen)
                .setDeleteIntent(mDismissed)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Reply", mReply).addRemoteInput(input).build())
                .addAction(new Notification.Action.Builder(null, "Mark read", mMarkRead).build())
                .build();
    }

    @Test
    public void copyKeepsWhereItLeadsAndItsButtons() {
        Notification copy = NotificationReposter.buildCopy(mApp, sbn(original()), Action.SILENT, "Chat",
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), true);

        assertSame(mOpen, copy.contentIntent);
        assertSame(mDismissed, copy.deleteIntent);
        assertNotNull(copy.actions);
        assertEquals(2, copy.actions.length);
        assertEquals("Reply", copy.actions[0].title.toString());
        assertSame(mReply, copy.actions[0].actionIntent);
        RemoteInput[] inputs = copy.actions[0].getRemoteInputs();
        assertNotNull(inputs);
        assertEquals(1, inputs.length);
        assertEquals("reply_text", inputs[0].getResultKey());
        assertSame(mMarkRead, copy.actions[1].actionIntent);
        assertNull(copy.actions[1].getRemoteInputs());
        assertTrue((copy.flags & Notification.FLAG_AUTO_CANCEL) != 0);
    }

    @Test
    public void copyShowsTheSameWordsAndSaysWhichAppItCameFrom() {
        Notification copy = NotificationReposter.buildCopy(mApp, sbn(original()), Action.SILENT, "Chat", null, true);

        assertEquals("Carol", copy.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Are you coming?", copy.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals("Are you coming? We start at six.",
                copy.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString());
        assertEquals("Chat, Work", copy.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString());
        assertEquals(CHAT, copy.extras.getString(NotificationReposter.EXTRA_SOURCE_PACKAGE));
        assertEquals(NotificationReposter.groupOf(CHAT), copy.getGroup());
        assertNotNull(copy.publicVersion);
    }

    @Test
    public void actionGoesOnItsOwnChannelAndAnUnchangedCopyStaysQuiet() {
        Notification silent = NotificationReposter.buildCopy(mApp, sbn(original()), Action.SILENT, "Chat", null,
                true);
        Notification vibrate = NotificationReposter.buildCopy(mApp, sbn(original()), Action.VIBRATE, "Chat", null,
                true);
        Notification repeat = NotificationReposter.buildCopy(mApp, sbn(original()), Action.VIBRATE, "Chat", null,
                false);

        assertEquals(NotificationReposter.CHANNEL_SILENT, silent.getChannelId());
        assertEquals(NotificationReposter.CHANNEL_VIBRATE, vibrate.getChannelId());
        assertEquals(0, vibrate.flags & Notification.FLAG_ONLY_ALERT_ONCE);
        assertTrue((repeat.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
    }

    @Test
    public void summaryHoldsTheGroupWithoutAlerting() {
        Notification summary = NotificationReposter.buildSummary(mApp, CHAT, "Chat", null);

        assertTrue((summary.flags & Notification.FLAG_GROUP_SUMMARY) != 0);
        assertEquals(NotificationReposter.groupOf(CHAT), summary.getGroup());
        assertEquals(Notification.GROUP_ALERT_CHILDREN, summary.getGroupAlertBehavior());
        assertEquals(NotificationReposter.CHANNEL_SILENT, summary.getChannelId());
        assertEquals(CHAT, summary.extras.getString(NotificationReposter.EXTRA_SOURCE_PACKAGE));
    }

    @Test
    public void copyTagsLeadBackToTheOriginal() {
        String key = "0|com.example.chat|7|tag|10123";

        assertEquals(key, NotificationReposter.sourceKeyOf(NotificationReposter.copyTag(key)));
        assertNull(NotificationReposter.sourceKeyOf(NotificationReposter.summaryTag(CHAT)));
        assertNull(NotificationReposter.sourceKeyOf(null));
        assertNull(NotificationReposter.sourceKeyOf("toast_history"));
    }

    @Test
    public void channelsAreQuietOrVibrateOnly() {
        NotificationReposter.ensureChannels(mApp);
        NotificationManager manager = mApp.getSystemService(NotificationManager.class);

        NotificationChannel silent = manager.getNotificationChannel(NotificationReposter.CHANNEL_SILENT);
        NotificationChannel vibrate = manager.getNotificationChannel(NotificationReposter.CHANNEL_VIBRATE);
        assertEquals(NotificationManager.IMPORTANCE_LOW, silent.getImportance());
        assertNull(silent.getSound());
        assertFalse(silent.shouldVibrate());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, vibrate.getImportance());
        assertNull(vibrate.getSound());
        assertTrue(vibrate.shouldVibrate());
    }

    /** #32: a status bar channel with no sound, and a lower one for low priority rules. */
    @Test
    public void theIconAndLowChannelsMakeNoSound() {
        NotificationReposter.ensureChannels(mApp);
        NotificationManager manager = mApp.getSystemService(NotificationManager.class);

        NotificationChannel icon = manager.getNotificationChannel(NotificationReposter.CHANNEL_ICON);
        NotificationChannel low = manager.getNotificationChannel(NotificationReposter.CHANNEL_SILENT_LOW);
        assertNotNull(icon);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, icon.getImportance());
        assertNull(icon.getSound());
        assertFalse(icon.shouldVibrate());
        assertNotNull(low);
        assertEquals(NotificationManager.IMPORTANCE_MIN, low.getImportance());
        assertNull(low.getSound());
        assertFalse(low.shouldVibrate());
    }

    @Test
    public void keepIconCopyCarriesTheOriginalsSmallIcon() {
        Notification original = new Notification.Builder(mApp, "their_channel")
                .setSmallIcon(Icon.createWithResource(CHAT, 0x7f080042))
                .setContentTitle("Carol")
                .setContentText("Are you coming?")
                .build();

        Notification keep = NotificationReposter.buildCopy(mApp, sbn(original), Action.KEEP_ICON, Priority.NORMAL,
                "Chat", null, true, false, 5_000L, 0);
        Notification silent = NotificationReposter.buildCopy(mApp, sbn(original), Action.SILENT, Priority.NORMAL,
                "Chat", null, true, false, 5_000L, 0);

        assertEquals(NotificationReposter.CHANNEL_ICON, keep.getChannelId());
        assertEquals(CHAT, keep.getSmallIcon().getResPackage());
        assertEquals(0x7f080042, keep.getSmallIcon().getResId());
        assertEquals(mApp.getPackageName(), silent.getSmallIcon().getResPackage());
    }

    @Test
    public void priorityPicksTheChannelSortKeyAndTime() {
        Notification original = new Notification.Builder(mApp, "their_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Carol")
                .setWhen(1_234L)
                .build();

        Notification high = NotificationReposter.buildCopy(mApp, sbn(original), Action.SILENT, Priority.HIGH, "Chat",
                null, true, false, 5_000L, 0);
        Notification low = NotificationReposter.buildCopy(mApp, sbn(original), Action.SILENT, Priority.LOW, "Chat",
                null, true, false, 5_000L, 0);

        assertEquals(NotificationReposter.CHANNEL_ICON, high.getChannelId());
        assertEquals("0", high.getSortKey());
        assertEquals(5_000L, high.when);
        assertEquals(NotificationReposter.CHANNEL_SILENT_LOW, low.getChannelId());
        assertEquals("2", low.getSortKey());
        assertEquals(1_234L, low.when);
    }

    @Test
    public void aQuietCopyNeverAlerts() {
        Notification quiet = NotificationReposter.buildCopy(mApp, sbn(original()), Action.VIBRATE, Priority.NORMAL,
                "Chat", null, true, true, 5_000L, 0);
        Notification loud = NotificationReposter.buildCopy(mApp, sbn(original()), Action.VIBRATE, Priority.NORMAL,
                "Chat", null, true, false, 5_000L, 0);

        assertEquals(Notification.GROUP_ALERT_SUMMARY, quiet.getGroupAlertBehavior());
        assertEquals(Notification.GROUP_ALERT_CHILDREN, loud.getGroupAlertBehavior());
    }

    @Test
    public void pinnedCopyIsOngoingWithUnpinAndKeepsTheAppsDismissalToItself() {
        long now = 5_000L;
        Notification pin = NotificationReposter.buildCopy(mApp, sbn(original()), Action.PIN, Priority.NORMAL, "Chat",
                null, true, false, now, now + 3_600_000L);

        assertTrue((pin.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertEquals(now + 3_600_000L, NotificationPins.pinnedUntil(pin));
        assertEquals(3_600_000L, pin.getTimeoutAfter());
        assertEquals(NotificationReposter.CHANNEL_SILENT, pin.getChannelId());
        // The app's own buttons stay, Unpin comes last.
        assertEquals(3, pin.actions.length);
        assertSame(mReply, pin.actions[0].actionIntent);
        assertEquals(mApp.getString(R.string.notification_filter_unpin), pin.actions[2].title.toString());
        // A swipe puts it back, so the app isn't told it was dismissed.
        assertEquals(NotificationPins.ACTION_DISMISSED, shadowOf(pin.deleteIntent).getSavedIntent().getAction());
        assertFalse((pin.flags & Notification.FLAG_AUTO_CANCEL) != 0);
    }

    @Test
    public void canPostOnFollowsEachChannel() {
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationReposter.ensureChannels(mApp);
        NotificationManager manager = mApp.getSystemService(NotificationManager.class);
        NotificationChannel icon = manager.getNotificationChannel(NotificationReposter.CHANNEL_ICON);
        icon.setImportance(NotificationManager.IMPORTANCE_NONE);
        manager.createNotificationChannel(icon);

        assertFalse(NotificationReposter.canPostOn(mApp, NotificationReposter.CHANNEL_ICON));
        assertTrue(NotificationReposter.canPostOn(mApp, NotificationReposter.CHANNEL_SILENT));
    }

    @Test
    public void canPostOnlyWhereTheCopyWouldShow() {
        NotificationReposter.ensureChannels(mApp);
        NotificationManager manager = mApp.getSystemService(NotificationManager.class);

        shadowOf(mApp).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertFalse(NotificationReposter.canPost(mApp, Action.SILENT));

        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertTrue(NotificationReposter.canPost(mApp, Action.SILENT));
        assertTrue(NotificationReposter.canPost(mApp, Action.VIBRATE));

        NotificationChannel vibrate = manager.getNotificationChannel(NotificationReposter.CHANNEL_VIBRATE);
        vibrate.setImportance(NotificationManager.IMPORTANCE_NONE);
        manager.createNotificationChannel(vibrate);
        assertFalse(NotificationReposter.canPost(mApp, Action.VIBRATE));
        assertTrue(NotificationReposter.canPost(mApp, Action.SILENT));

        shadowOf(manager).setNotificationsEnabled(false);
        assertFalse(NotificationReposter.canPost(mApp, Action.SILENT));
    }
}
