// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Process;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.PinLength;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Priority;

@RunWith(RobolectricTestRunner.class)
public class NotificationFilterEngineTest {
    private static final String CHAT = "com.example.chat";
    private static final String SHOP = "com.example.shop";
    private static final int UID = 10_100;
    private static final long NOW = 1_800_000_000_000L;

    /** Records what the engine asked of the shade, in order. */
    private static final class FakeShade implements NotificationFilterEngine.Shade {
        final List<StatusBarNotification> active = new ArrayList<>();
        final List<StatusBarNotification> snoozed = new ArrayList<>();
        final Map<String, Integer> importance = new HashMap<>();
        final List<String> log = new ArrayList<>();
        final Map<String, Notification> posted = new LinkedHashMap<>();
        boolean canPost = true;
        boolean postWorks = true;
        int sdk = Build.VERSION_CODES.BAKLAVA;
        boolean root;
        boolean canUnsnooze;

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
        public boolean canPost(@NonNull String channelId) {
            return canPost;
        }

        @Override
        public int sdkInt() {
            return sdk;
        }

        @Override
        public boolean isRoot() {
            return root;
        }

        @Override
        public boolean canUnsnooze() {
            return canUnsnooze;
        }

        @Override
        public boolean snooze(@NonNull String key, long durationMillis) {
            log.add("snooze " + key + " " + durationMillis);
            return true;
        }

        @Override
        public boolean cancelAsRoot(@NonNull StatusBarNotification sbn) {
            log.add("rootCancel " + sbn.getKey());
            return true;
        }

        @Override
        public void unsnooze(@NonNull String key) {
            log.add("unsnooze " + key);
        }

        @NonNull
        @Override
        public StatusBarNotification[] snoozed() {
            return snoozed.toArray(new StatusBarNotification[0]);
        }

        @Override
        public int importanceOf(@NonNull String key) {
            Integer value = importance.get(key);
            return value != null ? value : -1000;
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
    private long mNow = NOW;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mSettings = new NotificationFilterSettings(
                mApp.getSharedPreferences("notification_filter_engine_test", Context.MODE_PRIVATE));
        mShade = new FakeShade();
        mEngine = newEngine();
    }

    @After
    public void tearDown() {
        NotificationPins.forgetAllForTesting();
    }

    @NonNull
    private NotificationFilterEngine newEngine() {
        return new NotificationFilterEngine(mApp, mShade, APPS, mSettings, mHistory::add, () -> mNow);
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
    private static NotificationFilterRule ongoingRule(@NonNull String pattern, @NonNull Action action) {
        return rule(pattern, action).withOptions(true, Priority.NORMAL, PinLength.UNTIL_UNPINNED);
    }

    @NonNull
    private StatusBarNotification withFlags(@NonNull String packageName, int id, @NonNull String text, int flags) {
        Notification notification = builder("Upload", text).build();
        notification.flags |= flags;
        return sbn(packageName, id, null, notification, NOW);
    }

    @NonNull
    private static String text(@NonNull Notification notification) {
        return notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
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

    private int countStartingWith(@NonNull String prefix) {
        int count = 0;
        for (String line : mShade.log) {
            if (line.startsWith(prefix)) {
                ++count;
            }
        }
        return count;
    }

    /** #32: a rule saved now applies to what's already in the shade, without buzzing for each one. */
    @Test
    public void sweepAppliesTheRulesToWhatIsAlreadyInTheShadeQuietly() {
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");
        StatusBarNotification lunch = notification(CHAT, 2, "Lunch tomorrow?");
        StatusBarNotification own = notification(mApp.getPackageName(), 3, "Big sale in the toast history");
        StatusBarNotification summary = summary(100, 0);
        mShade.active.addAll(Arrays.asList(sale, lunch, own, summary));
        rules(rule("sale", Action.VIBRATE));

        mEngine.sweep();

        assertEquals(1, count("cancel " + sale.getKey()));
        assertEquals(0, count("cancel " + lunch.getKey()));
        assertEquals(0, count("cancel " + own.getKey()));
        assertEquals(0, count("cancel " + summary.getKey()));
        Notification copy = mShade.posted.get(NotificationReposter.copyTag(sale.getKey()));
        assertNotNull(copy);
        assertEquals(Notification.GROUP_ALERT_SUMMARY, copy.getGroupAlertBehavior());
        assertEquals(1, mHistory.size());
        assertEquals("VIBRATE", mHistory.get(0).action);

        // One that arrives after still alerts as its rule says.
        StatusBarNotification another = notification(SHOP, 4, "Another sale");
        mEngine.onPosted(another);
        assertEquals(Notification.GROUP_ALERT_CHILDREN,
                mShade.posted.get(NotificationReposter.copyTag(another.getKey())).getGroupAlertBehavior());
    }

    @Test
    public void connectingSweepsTheShade() {
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");
        mShade.active.add(sale);
        rules(rule("sale", Action.BLOCK));

        mEngine.onConnected();

        assertEquals(1, count("cancel " + sale.getKey()));
        assertEquals(1, mHistory.size());
        assertEquals("BLOCK", mHistory.get(0).action);
    }

    @Test
    public void ongoingIsLeftAloneUnlessTheRuleAsksForIt() {
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        rules(rule("upload", Action.SILENT));
        mSettings.setRecordAll(true);

        mEngine.onPosted(upload);
        assertTrue(mShade.log.isEmpty());
        assertTrue(mHistory.isEmpty());

        rules(ongoingRule("upload", Action.SILENT));
        mShade.importance.put(upload.getKey(), NotificationManager.IMPORTANCE_LOW);
        mEngine.onPosted(upload);

        String copyTag = NotificationReposter.copyTag(upload.getKey());
        assertEquals(Arrays.asList("post " + copyTag, "post " + NotificationReposter.summaryTag(CHAT),
                "snooze " + upload.getKey() + " " + OngoingPolicy.SHORT_SNOOZE_MILLIS), mShade.log);
        assertEquals("Upload 10%", text(mShade.posted.get(copyTag)));
        assertEquals(1, mHistory.size());
        assertEquals("SILENT", mHistory.get(0).action);
    }

    @Test
    public void anOngoingOneThatMayAlertIsKeptAwayLonger() {
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        rules(ongoingRule("upload", Action.BLOCK));
        mShade.importance.put(upload.getKey(), NotificationManager.IMPORTANCE_DEFAULT);

        mEngine.onPosted(upload);

        assertEquals(Collections.singletonList("snooze " + upload.getKey() + " " + OngoingPolicy.LONG_SNOOZE_MILLIS),
                mShade.log);
    }

    @Test
    public void ongoingRemovalFollowsTheAndroidVersionAndMode() {
        rules(ongoingRule("upload", Action.BLOCK));
        // From Android 13 a listener may cancel a foreground service's notification that isn't marked ongoing.
        StatusBarNotification service = withFlags(CHAT, 1, "Upload 1", Notification.FLAG_FOREGROUND_SERVICE);
        mEngine.onPosted(service);
        assertEquals(Collections.singletonList("cancel " + service.getKey()), mShade.log);

        mShade.log.clear();
        mShade.sdk = Build.VERSION_CODES.S;
        mEngine.onPosted(withFlags(CHAT, 2, "Upload 2", Notification.FLAG_FOREGROUND_SERVICE));
        assertEquals(1, countStartingWith("snooze "));
        assertEquals(0, countStartingWith("cancel "));

        mShade.log.clear();
        mShade.sdk = Build.VERSION_CODES.N_MR1;
        mShade.root = true;
        StatusBarNotification ongoing = withFlags(CHAT, 3, "Upload 3", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(ongoing);
        assertEquals(Collections.singletonList("rootCancel " + ongoing.getKey()), mShade.log);

        // Not even root removes a running service's notification before Android 8.
        mShade.log.clear();
        mEngine.onPosted(withFlags(CHAT, 4, "Upload 4",
                Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE));
        assertTrue(mShade.log.isEmpty());

        // And without root nothing ongoing can be removed there, so no copy goes up either.
        mShade.root = false;
        rules(ongoingRule("upload", Action.SILENT));
        mEngine.onPosted(withFlags(CHAT, 5, "Upload 5", Notification.FLAG_ONGOING_EVENT));
        assertTrue(mShade.log.isEmpty());
        assertEquals(3, mHistory.size());
    }

    /** #32: an app updates its snoozed notification out of sight, and the copy follows. */
    @Test
    public void aSnoozedOriginalsUpdatesReachItsCopy() {
        rules(ongoingRule("upload", Action.SILENT));
        StatusBarNotification first = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(first);
        String copyTag = NotificationReposter.copyTag(first.getKey());
        mShade.log.clear();

        mShade.snoozed.add(withFlags(CHAT, 3, "Upload 60%", Notification.FLAG_ONGOING_EVENT));
        mEngine.onPosted(notification(SHOP, 9, "Something else"));

        assertEquals("Upload 60%", text(mShade.posted.get(copyTag)));
        assertEquals("it's already snoozed", 0, countStartingWith("snooze "));
        assertEquals(Collections.singletonList(first.getKey()), mEngine.copiedKeys());
        assertEquals(2, mHistory.size());
        assertEquals("Upload 60%", mHistory.get(1).text);
    }

    @Test
    public void aSnoozedUpdateNoRuleTakesComesBackWhereItCan() {
        rules(ongoingRule("upload 10", Action.SILENT));
        StatusBarNotification first = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(first);
        String copyTag = NotificationReposter.copyTag(first.getKey());

        // Without root or ADB it stays snoozed, and its copy shows the update, so nothing is lost.
        mShade.snoozed.add(withFlags(CHAT, 3, "Upload done", Notification.FLAG_ONGOING_EVENT));
        mEngine.onPosted(notification(SHOP, 9, "Something else"));
        assertEquals("Upload done", text(mShade.posted.get(copyTag)));
        assertEquals(0, count("unsnooze " + first.getKey()));

        mShade.canUnsnooze = true;
        mEngine.onPosted(notification(SHOP, 10, "Something else again"));
        assertEquals(1, count("unsnooze " + first.getKey()));
        assertNull(mShade.posted.get(copyTag));
        assertTrue(mEngine.copiedKeys().isEmpty());
    }

    @Test
    public void aCopyGoesWhenItsSnoozedOriginalIsGone() {
        rules(ongoingRule("upload", Action.SILENT), rule("sale", Action.SILENT));
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        StatusBarNotification download = withFlags(CHAT, 4, "Upload 5%", Notification.FLAG_ONGOING_EVENT);
        StatusBarNotification sale = notification(SHOP, 1, "Big sale today");
        mEngine.onPosted(upload);
        mEngine.onPosted(download);
        mEngine.onPosted(sale);
        mShade.snoozed.add(upload);
        mEngine.onPosted(notification(CHAT, 9, "Lunch?"));
        assertEquals(Arrays.asList(upload.getKey(), download.getKey(), sale.getKey()), mEngine.copiedKeys());

        // The app ended the upload while it was snoozed, so it's in neither list now. A plain copy, like the
        // sale's, always stands for an original that's gone, and stays.
        mShade.snoozed.clear();
        mEngine.onPosted(notification(CHAT, 10, "Lunch, then?"));
        assertNull(mShade.posted.get(NotificationReposter.copyTag(upload.getKey())));
        assertNotNull(mShade.posted.get(NotificationReposter.copyTag(sale.getKey())));
        // The download was never seen snoozed, so it gets a moment for the snooze to land.
        assertNotNull(mShade.posted.get(NotificationReposter.copyTag(download.getKey())));

        mNow = NOW + TimeUnit.MINUTES.toMillis(1);
        mEngine.onPosted(notification(CHAT, 11, "Lunch, really"));
        assertNull(mShade.posted.get(NotificationReposter.copyTag(download.getKey())));
        assertEquals(Collections.singletonList(sale.getKey()), mEngine.copiedKeys());
    }

    /**
     * #32: an app that's force stopped ends its snoozed notifications too, but Android keeps listing them as snoozed
     * until the snooze runs out and doesn't tell the listener. The stop is the one sign, so their copies go then.
     */
    @Test
    public void aForceStoppedAppsCopiesGoAtOnce() {
        rules(ongoingRule("upload", Action.SILENT));
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        StatusBarNotification other = withFlags(SHOP, 4, "Upload 5%", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(upload);
        mEngine.onPosted(other);
        mShade.snoozed.add(upload);
        mShade.snoozed.add(other);
        String uploadCopy = NotificationReposter.copyTag(upload.getKey());

        mEngine.onPackageStopped(CHAT, UID);
        assertNull(mShade.posted.get(uploadCopy));
        assertNotNull("another app's copy stays", mShade.posted.get(NotificationReposter.copyTag(other.getKey())));

        // Still listed, but it's the one that ended, so its copy stays down.
        mEngine.onPosted(notification(SHOP, 9, "Something else"));
        mEngine.check();
        assertNull(mShade.posted.get(uploadCopy));

        // The app starts again and posts it once more. Android keeps that one snoozed, so the copy comes back.
        mShade.snoozed.set(0, sbn(CHAT, 3, null, upload.getNotification(), NOW + 5000));
        mEngine.check();
        assertNotNull(mShade.posted.get(uploadCopy));
        assertEquals(Arrays.asList(other.getKey(), upload.getKey()), mEngine.copiedKeys());
    }

    /** A stop for another user's copy of the app, or before anything was snoozed, changes nothing. */
    @Test
    public void aStopOnlyTouchesThatAppsOwnSnoozedOnes() {
        mEngine.onPackageStopped(CHAT, UID);
        rules(ongoingRule("upload", Action.SILENT));
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(upload);
        mShade.snoozed.add(upload);

        mEngine.onPackageStopped(CHAT, UID + 100000);
        assertNotNull(mShade.posted.get(NotificationReposter.copyTag(upload.getKey())));
        assertEquals(Collections.singletonList(upload.getKey()), mEngine.copiedKeys());
    }

    /**
     * #32: Android doesn't say when a snoozed notification changes or its snooze runs out on one that ended, so the
     * service looks in on them every so often for as long as something is snoozed.
     */
    @Test
    public void theRegularCheckCatchesWhatHappensWhileSnoozed() {
        assertFalse(mEngine.hasSnoozed());
        rules(ongoingRule("upload", Action.SILENT));
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        mEngine.onPosted(upload);
        assertTrue(mEngine.hasSnoozed());
        String copyTag = NotificationReposter.copyTag(upload.getKey());

        mShade.snoozed.add(withFlags(CHAT, 3, "Upload 60%", Notification.FLAG_ONGOING_EVENT));
        mEngine.check();
        assertEquals("Upload 60%", text(mShade.posted.get(copyTag)));

        // Its app ended it, and once the snooze ran out Android stopped listing it.
        mShade.snoozed.clear();
        mEngine.check();
        assertNull(mShade.posted.get(copyTag));
        assertFalse(mEngine.hasSnoozed());
    }

    /**
     * Android 8 and 9 snooze notifications too, but have no way to ask a notification whose it is. Asking threw, so
     * a rule that took an ongoing notification there crashed the filter.
     */
    @Test
    @Config(sdk = Build.VERSION_CODES.P)
    public void anOngoingNotificationIsSnoozedOnAndroidNine() {
        rules(ongoingRule("upload", Action.SILENT));
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);

        mEngine.onPosted(upload);

        assertTrue(mEngine.hasSnoozed());
        String copyTag = NotificationReposter.copyTag(upload.getKey());
        assertNotNull(mShade.posted.get(copyTag));
        // A force stop still ends the copy, whichever user's app it came from
        mShade.snoozed.add(upload);
        mEngine.onPackageStopped(CHAT, UID);
        assertNull(mShade.posted.get(copyTag));
    }

    @Test
    public void editingTheRulesBringsBackWhatTheyNoLongerTake() {
        StatusBarNotification upload = withFlags(CHAT, 3, "Upload 10%", Notification.FLAG_ONGOING_EVENT);
        rules(ongoingRule("upload", Action.SILENT));
        mEngine.onPosted(upload);
        mShade.snoozed.add(upload);
        String copyTag = NotificationReposter.copyTag(upload.getKey());

        rules(rule("upload", Action.SILENT));
        mEngine.sweep();
        assertEquals("no root or ADB: it stays hidden and the copy stays", 0, count("unsnooze " + upload.getKey()));
        assertNotNull(mShade.posted.get(copyTag));

        mShade.canUnsnooze = true;
        mEngine.sweep();
        assertEquals(1, count("unsnooze " + upload.getKey()));
        assertNull(mShade.posted.get(copyTag));
    }

    /** #32: a pinned copy that's swiped away comes back, once each time, until it runs out or is unpinned. */
    @Test
    public void aPinComesBackWhenSwipedAwayUntilItRunsOut() {
        rules(rule("boarding", Action.PIN).withOptions(false, Priority.NORMAL, PinLength.ONE_HOUR));
        StatusBarNotification pass = notification(CHAT, 1, "Your boarding pass");
        mEngine.onPosted(pass);
        String copyTag = NotificationReposter.copyTag(pass.getKey());
        Notification pin = mShade.posted.get(copyTag);
        assertNotNull(pin);
        assertTrue((pin.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertEquals(NOW + TimeUnit.HOURS.toMillis(1), NotificationPins.pinnedUntil(pin));
        assertEquals(1, count("cancel " + pass.getKey()));
        assertEquals("PIN", mHistory.get(0).action);

        mEngine.onRemoved(ownCopy(pass.getKey(), NOW), NotificationListenerService.REASON_CANCEL);
        assertEquals(2, count("post " + copyTag));
        mEngine.onRemoved(ownCopy(pass.getKey(), NOW), NotificationListenerService.REASON_CANCEL_ALL);
        assertEquals(3, count("post " + copyTag));
        assertEquals(Collections.singletonList(pass.getKey()), mEngine.copiedKeys());

        mNow = NOW + TimeUnit.HOURS.toMillis(1);
        mEngine.onRemoved(ownCopy(pass.getKey(), NOW), NotificationListenerService.REASON_CANCEL);
        assertEquals(3, count("post " + copyTag));
        assertTrue(mEngine.copiedKeys().isEmpty());
    }

    @Test
    public void unpinningEndsThePin() {
        rules(rule("boarding", Action.PIN));
        StatusBarNotification pass = notification(CHAT, 1, "Your boarding pass");
        mEngine.onPosted(pass);
        String copyTag = NotificationReposter.copyTag(pass.getKey());

        // Unpin cancels it from this app, which the listener hears as the app's own cancel.
        mEngine.onRemoved(ownCopy(pass.getKey(), NOW), NotificationListenerService.REASON_APP_CANCEL);

        assertEquals(1, count("post " + copyTag));
        assertTrue(mEngine.copiedKeys().isEmpty());
    }

    @Test
    public void aPinFromTheHistoryComesBackToo() {
        NotificationHistoryEntry entry = new NotificationHistoryEntry(NOW, CHAT, "Chat",
                new NotificationFields("Carol", "Gate 12", "Carol"), "SILENT", null, false);
        entry.id = 5;
        Notification pin = NotificationPins.buildFromHistory(mApp, entry, null, PinLength.UNTIL_UNPINNED, NOW);
        String tag = NotificationPins.historyTag(5);
        StatusBarNotification shown = sbn(mApp.getPackageName(), NotificationReposter.NOTIFICATION_ID, tag, pin, NOW);

        mEngine.onRemoved(shown, NotificationListenerService.REASON_CANCEL);

        assertEquals(Collections.singletonList("post " + tag), mShade.log);
    }

    /** #32: a copy that keeps the original app's icon in the status bar, still with no sound. */
    @Test
    public void keepIconCopyShowsTheAppsOwnIcon() {
        rules(rule("sale", Action.KEEP_ICON));
        Icon theirs = Icon.createWithResource(SHOP, 0x7f080042);
        StatusBarNotification sale = sbn(SHOP, 1, null, builder("Shop", "Big sale today").setSmallIcon(theirs)
                .build(), NOW);

        mEngine.onPosted(sale);

        Notification copy = mShade.posted.get(NotificationReposter.copyTag(sale.getKey()));
        assertNotNull(copy);
        assertEquals(NotificationReposter.CHANNEL_ICON, copy.getChannelId());
        assertEquals(SHOP, copy.getSmallIcon().getResPackage());
        assertEquals(0x7f080042, copy.getSmallIcon().getResId());
        assertEquals(1, count("cancel " + sale.getKey()));
        assertEquals("KEEP_ICON", mHistory.get(0).action);
    }

    /** #32: a high priority rule's copies sort above the rest, a low one's below. */
    @Test
    public void priorityDecidesWhereCopiesSort() {
        rules(rule("mom", Action.SILENT).withOptions(false, Priority.HIGH, PinLength.UNTIL_UNPINNED),
                rule("sale", Action.SILENT).withOptions(false, Priority.LOW, PinLength.UNTIL_UNPINNED));
        long earlier = NOW - 60_000L;
        StatusBarNotification mom = sbn(CHAT, 1, null, builder("Mom", "Mom: call me").setWhen(earlier).build(), NOW);
        StatusBarNotification sale = sbn(SHOP, 2, null, builder("Shop", "Big sale").setWhen(earlier).build(), NOW);
        mNow = NOW + 5_000L;

        mEngine.onPosted(sale);
        mEngine.onPosted(mom);

        Notification high = mShade.posted.get(NotificationReposter.copyTag(mom.getKey()));
        Notification low = mShade.posted.get(NotificationReposter.copyTag(sale.getKey()));
        assertEquals(NotificationReposter.CHANNEL_ICON, high.getChannelId());
        assertEquals("0", high.getSortKey());
        assertEquals(NOW + 5_000L, high.when);
        assertEquals(NotificationReposter.CHANNEL_SILENT_LOW, low.getChannelId());
        assertEquals("2", low.getSortKey());
        assertEquals(earlier, low.when);
    }
}
