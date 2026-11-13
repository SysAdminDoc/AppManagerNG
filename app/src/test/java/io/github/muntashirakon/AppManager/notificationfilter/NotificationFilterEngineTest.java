// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Process;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;

@RunWith(RobolectricTestRunner.class)
public class NotificationFilterEngineTest {
    private static final String CHAT = "com.example.chat";
    private static final String SHOP = "com.example.shop";
    private static final int UID = 10_100;
    private static final long NOW = 1_800_000_000_000L;

    /** Records what the engine asked of the shade, in order. */
    private static final class FakeShade implements NotificationFilterEngine.Shade {
        final List<StatusBarNotification> active = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final Map<String, Notification> posted = new LinkedHashMap<>();
        boolean canPost = true;
        boolean postWorks = true;

        @NonNull
        @Override
        public StatusBarNotification[] active() {
            return active.toArray(new StatusBarNotification[0]);
        }

        @Override
        public void cancelSource(@NonNull String key) {
            log.add("cancel " + key);
        }

        @Override
        public boolean canPost(@NonNull Action action) {
            return canPost;
        }

        @Override
        public boolean post(@NonNull String tag, @NonNull Notification notification) {
            if (!postWorks) {
                return false;
            }
            log.add("post " + tag);
            posted.put(tag, notification);
            return true;
        }

        @Override
        public void cancelOwn(@NonNull String tag) {
            log.add("cancelOwn " + tag);
            posted.remove(tag);
        }
    }

    private static final NotificationFilterEngine.Apps APPS = new NotificationFilterEngine.Apps() {
        @NonNull
        @Override
        public CharSequence label(@NonNull String packageName) {
            return "Label " + packageName;
        }

        @Nullable
        @Override
        public Bitmap icon(@NonNull String packageName) {
            return null;
        }
    };

    private Application mApp;
    private NotificationFilterSettings mSettings;
    private FakeShade mShade;
    private final List<NotificationHistoryEntry> mHistory = new ArrayList<>();
    private NotificationFilterEngine mEngine;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mSettings = new NotificationFilterSettings(
                mApp.getSharedPreferences("notification_filter_engine_test", Context.MODE_PRIVATE));
        mShade = new FakeShade();
        mEngine = newEngine();
    }

    @NonNull
    private NotificationFilterEngine newEngine() {
        return new NotificationFilterEngine(mApp, mShade, APPS, mSettings, mHistory::add, () -> NOW);
    }

    private void rules(@NonNull NotificationFilterRule... rules) {
        mSettings.setRules(Arrays.asList(rules));
    }

    @NonNull
    private static NotificationFilterRule rule(@NonNull String pattern, @NonNull Action action) {
        return new NotificationFilterRule("id-" + pattern, "Rule " + pattern, null, Field.ANY, pattern, false,
                action);
    }

    @NonNull
    private Notification.Builder builder(@NonNull String title, @NonNull String text) {
        return new Notification.Builder(mApp, "their_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private static StatusBarNotification sbn(@NonNull String packageName, int id, @Nullable String tag,
                                             @NonNull Notification notification, long postTime) {
        return new StatusBarNotification(packageName, packageName, id, tag, UID, 0, 0, notification,
                Process.myUserHandle(), postTime);
    }

    @NonNull
    private StatusBarNotification notification(@NonNull String packageName, int id, @NonNull String text) {
        return sbn(packageName, id, null, builder("Title", text).build(), NOW);
    }

    @NonNull
    private StatusBarNotification child(int id, @NonNull String text) {
        return sbn(CHAT, id, null, builder("Title", text).setGroup("g").build(), NOW);
    }

    @NonNull
    private StatusBarNotification summary(int id, int extraFlags) {
        Notification notification = builder("Chat", "").setGroup("g").setGroupSummary(true).build();
        notification.flags |= extraFlags;
        return sbn(CHAT, id, null, notification, NOW);
    }

    /** The copy as the shade would hand it back to the listener. */
    @NonNull
    private StatusBarNotification ownCopy(@NonNull String sourceKey, long postTime) {
        String tag = NotificationReposter.copyTag(sourceKey);
        Notification copy = mShade.posted.get(tag);
        assertNotNull("no copy of " + sourceKey, copy);
        return sbn(mApp.getPackageName(), NotificationReposter.NOTIFICATION_ID, tag, copy, postTime);
    }

    private int count(@NonNull String line) {
        return Collections.frequency(mShade.log, line);
    }

    @Test
    public void silentRulePostsTheCopyBeforeTakingTheOriginal() {
        rules(rule("sale", Action.SILENT));
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");

        mEngine.onPosted(sale);

        String copyTag = NotificationReposter.copyTag(sale.getKey());
        assertEquals(Arrays.asList("post " + copyTag, "post " + NotificationReposter.summaryTag(SHOP),
                "cancel " + sale.getKey()), mShade.log);
        assertEquals(NotificationReposter.CHANNEL_SILENT, mShade.posted.get(copyTag).getChannelId());
        assertEquals(Collections.singletonList(sale.getKey()), mEngine.copiedKeys());
        assertEquals(1, mHistory.size());
        NotificationHistoryEntry entry = mHistory.get(0);
        assertEquals("SILENT", entry.action);
        assertEquals("Rule sale", entry.ruleName);
        assertFalse(entry.byQuietMode);
        assertEquals(SHOP, entry.packageName);
        assertEquals("Label " + SHOP, entry.appLabel);
        assertEquals("Big sale today", entry.text);
        assertEquals(NOW, entry.timestampMillis);
    }

    @Test
    public void blockTakesTheOriginalWithoutACopy() {
        rules(rule("sale", Action.BLOCK));
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");

        mEngine.onPosted(sale);

        assertEquals(Collections.singletonList("cancel " + sale.getKey()), mShade.log);
        assertTrue(mShade.posted.isEmpty());
        assertEquals("BLOCK", mHistory.get(0).action);
    }

    @Test
    public void allowedUpdateTakesTheEarlierCopyAway() {
        rules(rule("mom", Action.ALLOW), rule("", Action.SILENT));
        StatusBarNotification first = notification(CHAT, 1, "Group chat is busy");
        StatusBarNotification update = notification(CHAT, 1, "Mom: call me");

        mEngine.onPosted(first);
        mShade.log.clear();
        mEngine.onPosted(update);

        assertEquals(Arrays.asList("cancelOwn " + NotificationReposter.copyTag(first.getKey()),
                "cancelOwn " + NotificationReposter.summaryTag(CHAT)), mShade.log);
        assertTrue(mEngine.copiedKeys().isEmpty());
        assertEquals(2, mHistory.size());
        assertEquals("ALLOW", mHistory.get(1).action);
        assertEquals("Rule mom", mHistory.get(1).ruleName);
    }

    @Test
    public void ownNotificationsAreLeftAlone() {
        rules(rule("", Action.BLOCK));
        mSettings.setRecordAll(true);

        mEngine.onPosted(notification(mApp.getPackageName(), 5, "Toast history"));

        assertTrue(mShade.log.isEmpty());
        assertTrue(mHistory.isEmpty());
    }

    @Test
    public void originalStaysWhenNoCopyCanBeShown() {
        rules(rule("sale", Action.SILENT));
        mShade.canPost = false;

        mEngine.onPosted(notification(SHOP, 1, "Big sale today"));

        assertTrue(mShade.log.isEmpty());
        assertTrue(mHistory.isEmpty());

        // With every notification recorded it shows up, as left alone.
        mSettings.setRecordAll(true);
        mEngine.onPosted(notification(SHOP, 2, "Another sale"));
        assertTrue(mShade.log.isEmpty());
        assertEquals(1, mHistory.size());
        assertEquals(NotificationHistoryEntry.ACTION_NONE, mHistory.get(0).action);
        assertNull(mHistory.get(0).ruleName);
    }

    @Test
    public void originalStaysWhenPostingTheCopyFails() {
        rules(rule("sale", Action.VIBRATE));
        mShade.postWorks = false;
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");

        mEngine.onPosted(sale);

        assertFalse(mShade.log.contains("cancel " + sale.getKey()));
        assertTrue(mEngine.copiedKeys().isEmpty());
    }

    @Test
    public void emptyGroupSummaryGoesWithItsLastChild() {
        rules(rule("sale", Action.SILENT));
        StatusBarNotification summary = summary(100, 0);
        StatusBarNotification child = child(1, "Big sale today");
        // The shade still lists the child for a moment after it was asked to go.
        mShade.active.addAll(Arrays.asList(summary, child));

        mEngine.onPosted(child);

        assertEquals(1, count("cancel " + child.getKey()));
        assertEquals(1, count("cancel " + summary.getKey()));
        assertTrue(mShade.log.indexOf("cancel " + child.getKey()) < mShade.log.indexOf("cancel " + summary.getKey()));
    }

    @Test
    public void summaryStaysWhileAnotherChildShows() {
        rules(rule("sale", Action.SILENT));
        StatusBarNotification summary = summary(100, 0);
        StatusBarNotification sale = child(1, "Big sale today");
        StatusBarNotification friend = child(2, "Lunch tomorrow?");
        mShade.active.addAll(Arrays.asList(summary, sale, friend));

        mEngine.onPosted(sale);

        assertEquals(0, count("cancel " + summary.getKey()));
        assertEquals(0, count("cancel " + friend.getKey()));
    }

    @Test
    public void summaryThatTurnsUpAfterItsFilteredChildGoes() {
        rules(rule("sale", Action.BLOCK));
        StatusBarNotification child = child(1, "Big sale today");
        mShade.active.add(child);
        mEngine.onPosted(child);

        StatusBarNotification summary = summary(100, 0);
        mShade.active.add(summary);
        mEngine.onPosted(summary);

        assertEquals(1, count("cancel " + summary.getKey()));
        // A summary is never recorded on its own.
        assertEquals(1, mHistory.size());
    }

    @Test
    public void summaryThatCanNotBeSwipedAwayStays() {
        rules(rule("sale", Action.SILENT));
        StatusBarNotification summary = summary(100, Notification.FLAG_ONGOING_EVENT);
        StatusBarNotification child = child(1, "Big sale today");
        mShade.active.addAll(Arrays.asList(summary, child));

        mEngine.onPosted(child);

        assertEquals(1, count("cancel " + child.getKey()));
        assertEquals(0, count("cancel " + summary.getKey()));
    }

    @Test
    public void sameUpdateAlertsOnceAndIsRecordedOnce() {
        rules(rule("score", Action.VIBRATE));
        StatusBarNotification first = notification(SHOP, 1, "Live score 1-0");

        mEngine.onPosted(first);
        mEngine.onPosted(notification(SHOP, 1, "Live score 1-0"));
        String copyTag = NotificationReposter.copyTag(first.getKey());
        Notification repeat = mShade.posted.get(copyTag);

        assertTrue((repeat.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
        assertEquals(NotificationReposter.CHANNEL_VIBRATE, repeat.getChannelId());
        assertEquals(1, mHistory.size());

        mEngine.onPosted(notification(SHOP, 1, "Live score 2-0"));
        Notification changed = mShade.posted.get(copyTag);

        assertEquals(0, changed.flags & Notification.FLAG_ONLY_ALERT_ONCE);
        assertEquals(2, mHistory.size());
        assertEquals(1, count("post " + NotificationReposter.summaryTag(SHOP)));
    }

    @Test
    public void recordAllKeepsWhatWasLeftAloneButNeverWhatIsExempt() {
        mSettings.setRecordAll(true);

        mEngine.onPosted(notification(CHAT, 1, "Lunch tomorrow?"));
        Notification ongoing = builder("Upload", "50%").setOngoing(true).build();
        mEngine.onPosted(sbn(CHAT, 2, null, ongoing, NOW));

        assertTrue(mShade.log.isEmpty());
        assertEquals(1, mHistory.size());
        assertEquals(NotificationHistoryEntry.ACTION_NONE, mHistory.get(0).action);
        assertEquals("Lunch tomorrow?", mHistory.get(0).text);
    }

    @Test
    public void leftAloneIsNotRecordedByDefault() {
        mEngine.onPosted(notification(CHAT, 1, "Lunch tomorrow?"));

        assertTrue(mHistory.isEmpty());
    }

    @Test
    public void oldestCopiesMakeRoomPastTheCap() {
        rules(rule("", Action.SILENT));
        List<String> keys = new ArrayList<>();
        for (int id = 0; id < NotificationFilterEngine.MAX_OWN_NOTIFICATIONS + 5; ++id) {
            StatusBarNotification sbn = notification(SHOP, id, "Item " + id);
            keys.add(sbn.getKey());
            mEngine.onPosted(sbn);
        }

        List<String> copied = mEngine.copiedKeys();
        // The group summary takes one place.
        assertEquals(NotificationFilterEngine.MAX_OWN_NOTIFICATIONS - 1, copied.size());
        assertEquals(keys.get(6), copied.get(0));
        assertEquals(keys.get(keys.size() - 1), copied.get(copied.size() - 1));
        for (int id = 0; id < 6; ++id) {
            assertEquals(1, count("cancelOwn " + NotificationReposter.copyTag(keys.get(id))));
        }
        assertEquals(0, count("cancelOwn " + NotificationReposter.summaryTag(SHOP)));
        // Every original still left the shade, and each one is in the history.
        assertEquals(keys.size(), mHistory.size());
    }

    @Test
    public void dismissingTheLastCopyTakesItsSummary() {
        rules(rule("sale", Action.SILENT));
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");
        mEngine.onPosted(sale);
        mShade.log.clear();

        mEngine.onRemoved(ownCopy(sale.getKey(), NOW));

        assertEquals(Collections.singletonList("cancelOwn " + NotificationReposter.summaryTag(SHOP)), mShade.log);
        assertTrue(mEngine.copiedKeys().isEmpty());
    }

    @Test
    public void copiesAreFoundAgainAfterARestart() {
        rules(rule("", Action.SILENT));
        StatusBarNotification older = notification(SHOP, 1, "First");
        StatusBarNotification newer = notification(CHAT, 2, "Second");
        mEngine.onPosted(older);
        mEngine.onPosted(newer);
        mShade.active.add(ownCopy(newer.getKey(), NOW + 10));
        mShade.active.add(ownCopy(older.getKey(), NOW));

        NotificationFilterEngine restarted = newEngine();
        restarted.onConnected();

        assertEquals(Arrays.asList(older.getKey(), newer.getKey()), restarted.copiedKeys());

        rules(rule("", Action.ALLOW));
        mShade.log.clear();
        restarted.onPosted(notification(SHOP, 1, "First, edited"));
        assertEquals(Arrays.asList("cancelOwn " + NotificationReposter.copyTag(older.getKey()),
                "cancelOwn " + NotificationReposter.summaryTag(SHOP)), mShade.log);
    }

    @Test
    public void quietModeQuietensWhatNoRuleAllows() {
        rules(rule("mom", Action.ALLOW));
        mSettings.setQuietMode(new QuietMode(true, false, 0, 0, 0));
        mSettings.setQuietAction(Action.VIBRATE);
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");
        StatusBarNotification mom = notification(CHAT, 2, "Mom: call me");

        mEngine.onPosted(sale);
        mEngine.onPosted(mom);

        assertEquals(NotificationReposter.CHANNEL_VIBRATE,
                mShade.posted.get(NotificationReposter.copyTag(sale.getKey())).getChannelId());
        assertEquals(0, count("cancel " + mom.getKey()));
        assertEquals(2, mHistory.size());
        assertEquals("VIBRATE", mHistory.get(0).action);
        assertTrue(mHistory.get(0).byQuietMode);
        assertNull(mHistory.get(0).ruleName);
        assertEquals("ALLOW", mHistory.get(1).action);
        assertFalse(mHistory.get(1).byQuietMode);
    }
}
