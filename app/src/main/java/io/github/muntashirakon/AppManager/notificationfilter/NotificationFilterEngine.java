// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Carries out what {@link NotificationFilterDecision} decides: posts the copy, takes the original out of the shade,
 * tidies the group the original came from and writes the history. It runs on the listener's thread only.
 */
final class NotificationFilterEngine {
    /** The shade, as the listener sees it. */
    interface Shade {
        /** Everything in the shade right now, this app's own notifications included. */
        @NonNull
        StatusBarNotification[] active();

        /** Removes another app's notification. */
        void cancelSource(@NonNull String key);

        /** Whether a copy on this channel would show. */
        boolean canPost(@NonNull String channelId);

        /** @return Whether it was posted. */
        boolean post(@NonNull String tag, @NonNull Notification notification);

        void cancelOwn(@NonNull String tag);

        /** The Android version, so a test can play any of them. */
        int sdkInt();

        /** Whether the root server is up, for a removal a listener isn't allowed. */
        boolean isRoot();

        /** Whether a snoozed notification can be brought back early (root or ADB, Android 11 and later). */
        boolean canUnsnooze();

        /** Hides another app's notification for a while (Android 8 and later). @return Whether it was asked. */
        boolean snooze(@NonNull String key, long durationMillis);

        /** Cancels another app's notification through the root server. @return Whether it was asked. */
        boolean cancelAsRoot(@NonNull StatusBarNotification sbn);

        /** Brings a snoozed notification back now. */
        void unsnooze(@NonNull String key);

        /** What's snoozed right now, with each app's latest update. Empty before Android 8. */
        @NonNull
        StatusBarNotification[] snoozed();

        /** The notification's importance in the current ranking, or a negative number when it's unknown. */
        int importanceOf(@NonNull String key);
    }

    interface Apps {
        @NonNull
        CharSequence label(@NonNull String packageName);

        @Nullable
        Bitmap icon(@NonNull String packageName);
    }

    /**
     * Android drops new notifications past 50 per app, and losing a copy after the original is gone would lose the
     * notification. The oldest copies make room first. They're still in the history.
     */
    static final int MAX_OWN_NOTIFICATIONS = 40;
    private static final int REMEMBERED = 256;
    /** How long a snooze may take to show in the snoozed list before a copy is taken for gone. */
    static final long SNOOZE_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(30);
    /** How often what's snoozed is looked in on, since Android doesn't report an update to it. */
    static final long CHECK_EVERY_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final int UNTOUCHABLE_SUMMARY_FLAGS = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;

    @NonNull
    private final Context mContext;
    @NonNull
    private final String mOwnPackage;
    @NonNull
    private final Shade mShade;
    @NonNull
    private final Apps mApps;
    @NonNull
    private final NotificationFilterSettings mSettings;
    @NonNull
    private final Consumer<NotificationHistoryEntry> mHistory;
    @NonNull
    private final LongSupplier mClock;

    /** Original key to its app, for each copy in the shade, oldest first. */
    private final LinkedHashMap<String, String> mCopies = new LinkedHashMap<>();
    /**
     * What each copy last said, so an update that says the same thing doesn't vibrate again. Kept for a snoozed
     * original whose copy the user dismissed, so only a real update brings the copy back.
     */
    private final Map<String, String> mCopySignatures = new LinkedHashMap<>();
    /** How each copy was made, in step with {@link #mCopySignatures}. */
    private final Map<String, CopyInfo> mCopyInfo = new HashMap<>();
    /** Originals this listener snoozed, which it checks for updates since the shade no longer reports them. */
    private final LinkedHashMap<String, Snoozed> mSnoozed = new LinkedHashMap<>();
    /** Groups a child was taken out of, for a summary that turns up after its children. */
    private final Set<String> mFilteredGroups = boundedSet();
    /** Originals asked to go that the shade may still list, since a cancel lands a moment later. */
    private final Set<String> mCancelling = boundedSet();
    /** What was last written down for each key, so an app that reposts the same thing isn't recorded twice. */
    private final Map<String, String> mRecorded = boundedMap();

    /** The rule a copy was made for, so it can be kept up to date the same way. */
    private static final class CopyInfo {
        @NonNull
        final NotificationFilterRule.Action action;
        @NonNull
        final NotificationFilterRule.Priority priority;
        /** When a pinned copy runs out, or 0 for one that isn't pinned. */
        final long pinUntilMillis;

        CopyInfo(@NonNull NotificationFilterRule.Action action, @NonNull NotificationFilterRule.Priority priority,
                 long pinUntilMillis) {
            this.action = action;
            this.priority = priority;
            this.pinUntilMillis = pinUntilMillis;
        }
    }

    private static final class Snoozed {
        final long sinceMillis;
        @NonNull
        final String packageName;
        final int uid;
        /** Whether the shade listed it as snoozed. Until then it may be missing because the snooze hasn't landed. */
        boolean seen;
        /**
         * Its app was force stopped, which ended it, though Android lists it as snoozed until the snooze runs out.
         * Only a post made after that, with a later post time, brings its copy back.
         */
        boolean ended;
        long endedPostTime;

        Snoozed(long sinceMillis, @NonNull StatusBarNotification sbn) {
            this.sinceMillis = sinceMillis;
            this.packageName = sbn.getPackageName();
            this.uid = sbn.getUid();
        }
    }

    NotificationFilterEngine(@NonNull Context context, @NonNull Shade shade, @NonNull Apps apps,
                             @NonNull NotificationFilterSettings settings,
                             @NonNull Consumer<NotificationHistoryEntry> history, @NonNull LongSupplier clock) {
        mContext = context;
        mOwnPackage = context.getPackageName();
        mShade = shade;
        mApps = apps;
        mSettings = settings;
        mHistory = history;
        mClock = clock;
    }

    /**
     * Finds the copies and pins still in the shade after the listener was restarted, and the originals it had snoozed,
     * then applies the rules to what's in the shade.
     */
    @MainThread
    void onConnected() {
        mCopies.clear();
        mCopySignatures.clear();
        mCopyInfo.clear();
        mSnoozed.clear();
        List<StatusBarNotification> own = new ArrayList<>();
        for (StatusBarNotification sbn : mShade.active()) {
            if (!mOwnPackage.equals(sbn.getPackageName())) {
                continue;
            }
            String tag = sbn.getTag();
            if (tag != null && NotificationPins.pinnedUntil(sbn.getNotification()) != 0) {
                // So a pin swiped while the listener is off still comes back.
                NotificationPins.remember(tag, sbn.getNotification());
            }
            if (NotificationReposter.sourceKeyOf(tag) != null) {
                own.add(sbn);
            }
        }
        Collections.sort(own, (a, b) -> Long.compare(a.getPostTime(), b.getPostTime()));
        for (StatusBarNotification sbn : own) {
            Bundle extras = sbn.getNotification().extras;
            String sourcePackage = extras != null ? extras.getString(NotificationReposter.EXTRA_SOURCE_PACKAGE) : null;
            String sourceKey = NotificationReposter.sourceKeyOf(sbn.getTag());
            if (sourcePackage != null && sourceKey != null) {
                mCopies.put(sourceKey, sourcePackage);
                mCopyInfo.put(sourceKey, new CopyInfo(
                        savedEnum(extras, NotificationReposter.EXTRA_COPY_ACTION, NotificationFilterRule.Action.SILENT),
                        savedEnum(extras, NotificationReposter.EXTRA_COPY_PRIORITY,
                                NotificationFilterRule.Priority.NORMAL),
                        NotificationPins.pinnedUntil(sbn.getNotification())));
            }
        }
        if (!mCopies.isEmpty()) {
            long now = mClock.getAsLong();
            for (StatusBarNotification sbn : mShade.snoozed()) {
                if (mCopies.containsKey(sbn.getKey())) {
                    Snoozed snoozed = new Snoozed(now, sbn);
                    snoozed.seen = true;
                    mSnoozed.put(sbn.getKey(), snoozed);
                }
            }
        }
        sweep();
    }

    @MainThread
    void onPosted(@NonNull StatusBarNotification sbn) {
        if (mOwnPackage.equals(sbn.getPackageName())) {
            return;
        }
        // Back in the shade: its snooze ran out or it was brought back. It's handled like any new one.
        mSnoozed.remove(sbn.getKey());
        refreshSnoozed();
        handle(sbn, false, false);
    }

    /**
     * Applies the rules to what's already in the shade, quietly, after the rules changed or the listener connected.
     * A notification that was snoozed by a rule that no longer takes it is brought back where that's possible.
     */
    @MainThread
    void sweep() {
        refreshSnoozed();
        for (StatusBarNotification sbn : mShade.active()) {
            if (mOwnPackage.equals(sbn.getPackageName()) || mCancelling.contains(sbn.getKey())
                    || (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                // Summaries go with their children.
                continue;
            }
            handle(sbn, true, true);
        }
    }

    /**
     * @param quiet    Never alerts, for one that was already in the shade.
     * @param sweeping Whether it was already in the shade. What's left alone was recorded when it was posted, if at
     *                 all, so it isn't recorded again.
     */
    private void handle(@NonNull StatusBarNotification sbn, boolean quiet, boolean sweeping) {
        Notification notification = sbn.getNotification();
        String key = sbn.getKey();
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            if (notification.getGroup() != null && mFilteredGroups.contains(sbn.getGroupKey())) {
                cancelEmptySummary(sbn.getGroupKey());
            }
            return;
        }
        long now = mClock.getAsLong();
        NotificationFilterDecision.Facts facts = NotificationFilterDecision.Facts.of(sbn);
        NotificationFilterDecision.Result result = decide(facts, now);
        if (result.exempt) {
            dropCopy(key);
            return;
        }
        NotificationFilterRule.Action action = result.action;
        if (action == null || action == NotificationFilterRule.Action.ALLOW) {
            // The original shows again as it is, so an earlier copy would be a duplicate.
            dropCopy(key);
            if (!sweeping) {
                record(sbn, facts, action, result);
            }
            return;
        }
        OngoingPolicy.Removal removal = OngoingPolicy.of(notification.flags, mShade.sdkInt(), mShade.isRoot());
        if (removal == OngoingPolicy.Removal.NONE) {
            // It can't be taken out of the shade here, so it stays as the app posted it, and a copy would be a
            // duplicate.
            dropCopy(key);
            if (!sweeping) {
                record(sbn, facts, null, result);
            }
            return;
        }
        if (action == NotificationFilterRule.Action.BLOCK) {
            dropCopy(key);
            remove(sbn, removal, now);
        } else if (!repost(sbn, facts, action, result.priority, pinUntil(key, result.pinFor, now), quiet, removal,
                now)) {
            // Without a copy the notification would be lost, so it stays as the app posted it.
            if (!sweeping) {
                record(sbn, facts, null, result);
            }
            return;
        }
        if (notification.getGroup() != null) {
            mFilteredGroups.add(sbn.getGroupKey());
            cancelEmptySummary(sbn.getGroupKey());
        }
        record(sbn, facts, action, result);
    }

    @NonNull
    private NotificationFilterDecision.Result decide(@NonNull NotificationFilterDecision.Facts facts, long now) {
        NotificationFilterRule.Action quietAction = mSettings.isQuietModeActive(now) ? mSettings.getQuietAction()
                : null;
        return NotificationFilterDecision.decide(facts, mOwnPackage, mSettings.getRules(), quietAction);
    }

    /**
     * An app can update or end a notification while it's snoozed, and the listener isn't told. So at each event the
     * snoozed ones are checked: an update reaches its copy, one no rule takes any more comes back where Android lets
     * it, and a copy whose original is gone goes too.
     */
    private void refreshSnoozed() {
        if (mSnoozed.isEmpty()) {
            return;
        }
        long now = mClock.getAsLong();
        Map<String, StatusBarNotification> snoozed = snoozedByKey();
        Set<String> active = null;
        for (String key : new ArrayList<>(mSnoozed.keySet())) {
            Snoozed state = mSnoozed.get(key);
            StatusBarNotification sbn = snoozed.get(key);
            if (sbn != null) {
                state.seen = true;
                if (state.ended) {
                    if (sbn.getPostTime() == state.endedPostTime) {
                        // What its app had up when it was force stopped, listed until the snooze runs out.
                        continue;
                    }
                    // Posted again since, and Android kept that snoozed too.
                    state.ended = false;
                }
                refreshSnoozed(sbn, now);
                continue;
            }
            if (active == null) {
                active = new HashSet<>();
                for (StatusBarNotification shown : mShade.active()) {
                    active.add(shown.getKey());
                }
            }
            if (active.contains(key)) {
                // Back in the shade. Its own post handles it.
                continue;
            }
            if (state.seen || now - state.sinceMillis >= SNOOZE_GRACE_MILLIS) {
                // The app ended it while it was snoozed.
                mSnoozed.remove(key);
                dropCopy(key);
            }
        }
    }

    private void refreshSnoozed(@NonNull StatusBarNotification sbn, long now) {
        String key = sbn.getKey();
        NotificationFilterDecision.Facts facts = NotificationFilterDecision.Facts.of(sbn);
        NotificationFilterDecision.Result result = decide(facts, now);
        NotificationFilterRule.Action action = result.exempt ? null : result.action;
        if (action == null || action == NotificationFilterRule.Action.ALLOW) {
            // No rule takes it any more.
            if (mShade.canUnsnooze()) {
                mSnoozed.remove(key);
                mShade.unsnooze(key);
                dropCopy(key);
                return;
            }
            // It stays hidden until the snooze runs out, so its copy keeps showing what it says.
            CopyInfo info = mCopyInfo.get(key);
            if (info != null && mCopies.containsKey(key)) {
                repost(sbn, facts, info.action, info.priority, info.pinUntilMillis, true, null, now);
            }
            return;
        }
        if (action == NotificationFilterRule.Action.BLOCK) {
            dropCopy(key);
            record(sbn, facts, action, result);
            return;
        }
        if (repost(sbn, facts, action, result.priority, pinUntil(key, result.pinFor, now), true, null, now)) {
            record(sbn, facts, action, result);
        }
    }

    /** An update to a pinned copy keeps the time the pin runs out. */
    private long pinUntil(@NonNull String key, @NonNull NotificationFilterRule.PinLength length, long now) {
        CopyInfo info = mCopyInfo.get(key);
        if (info != null && info.action == NotificationFilterRule.Action.PIN
                && (info.pinUntilMillis == NotificationPins.FOREVER || now < info.pinUntilMillis)) {
            return info.pinUntilMillis;
        }
        return NotificationPins.untilMillis(length, now, TimeZone.getDefault());
    }

    /** @param reason Why it left, as the listener was told on Android 8 and later, or 0 before. */
    @MainThread
    void onRemoved(@NonNull StatusBarNotification sbn, int reason) {
        mCancelling.remove(sbn.getKey());
        if (!mOwnPackage.equals(sbn.getPackageName())) {
            return;
        }
        String tag = sbn.getTag();
        Notification notification = sbn.getNotification();
        if (tag != null && NotificationPins.pinnedUntil(notification) != 0) {
            long now = mClock.getAsLong();
            if (NotificationPins.shouldRepost(notification, reason, now)) {
                // Swiped away, which Android 14 and later allow even for an ongoing notification.
                Notification again = NotificationPins.forRepost(mContext, notification, now);
                if (mShade.post(tag, again)) {
                    NotificationPins.remember(tag, again);
                    return;
                }
            }
            NotificationPins.forget(tag);
        }
        String sourceKey = NotificationReposter.sourceKeyOf(tag);
        if (sourceKey == null) {
            return;
        }
        // The user dismissed a copy, or tapped one that cancels itself.
        String sourcePackage = mCopies.remove(sourceKey);
        if (!mSnoozed.containsKey(sourceKey)) {
            mCopySignatures.remove(sourceKey);
            mCopyInfo.remove(sourceKey);
        }
        if (sourcePackage != null) {
            cancelSummaryIfEmpty(sourcePackage);
        }
    }

    @MainThread
    void onRemoved(@NonNull StatusBarNotification sbn) {
        onRemoved(sbn, 0);
    }

    /**
     * Posts or updates the copy, then takes the original out of the shade.
     *
     * @param removal How the original goes, or {@code null} when it's snoozed already.
     */
    private boolean repost(@NonNull StatusBarNotification sbn, @NonNull NotificationFilterDecision.Facts facts,
                           @NonNull NotificationFilterRule.Action action,
                           @NonNull NotificationFilterRule.Priority priority, long pinUntilMillis, boolean quiet,
                           @Nullable OngoingPolicy.Removal removal, long now) {
        if (!mShade.canPost(CopyStyle.of(action, priority).channel)) {
            return false;
        }
        String key = sbn.getKey();
        String sourcePackage = sbn.getPackageName();
        String signature = action.name() + '\n' + facts.fields.title + '\n' + facts.fields.text;
        boolean unchanged = signature.equals(mCopySignatures.get(key));
        CopyInfo info = mCopyInfo.get(key);
        if (removal == null && unchanged && info != null && info.action == action && info.priority == priority) {
            // A snoozed original that didn't change. Its copy is up to date, or the user dismissed it.
            return true;
        }
        long pin = action == NotificationFilterRule.Action.PIN ? pinUntilMillis : 0;
        makeRoom(key, sourcePackage);
        CharSequence label = mApps.label(sourcePackage);
        Bitmap icon = mApps.icon(sourcePackage);
        Notification copy = NotificationReposter.buildCopy(mContext, sbn, action, priority, label, icon, !unchanged,
                quiet, now, pin);
        String tag = NotificationReposter.copyTag(key);
        if (!mShade.post(tag, copy)) {
            return false;
        }
        if (pin != 0) {
            NotificationPins.remember(tag, copy);
        } else {
            NotificationPins.forget(tag);
        }
        boolean hadCopies = hasCopiesFor(sourcePackage);
        mCopies.remove(key);
        mCopies.put(key, sourcePackage);
        mCopySignatures.put(key, signature);
        mCopyInfo.put(key, new CopyInfo(action, priority, pin));
        if (NotificationReposter.groupsCopies() && !hadCopies) {
            mShade.post(NotificationReposter.summaryTag(sourcePackage),
                    NotificationReposter.buildSummary(mContext, sourcePackage, label, icon));
        }
        // Only now that the copy is up, so nothing is lost if posting it failed.
        if (removal != null) {
            remove(sbn, removal, now);
        }
        return true;
    }

    /** Takes another app's notification out of the shade the way this Android version and mode allow. */
    private void remove(@NonNull StatusBarNotification sbn, @NonNull OngoingPolicy.Removal removal, long now) {
        String key = sbn.getKey();
        switch (removal) {
            case CANCEL:
                cancelSource(key);
                break;
            case SNOOZE:
                mCancelling.add(key);
                if (mShade.snooze(key, OngoingPolicy.snoozeMillis(mShade.importanceOf(key)))) {
                    mSnoozed.put(key, new Snoozed(now, sbn));
                }
                break;
            case AS_ROOT:
                mCancelling.add(key);
                mShade.cancelAsRoot(sbn);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void makeRoom(@NonNull String key, @NonNull String sourcePackage) {
        while (true) {
            Set<String> packages = new HashSet<>();
            packages.add(sourcePackage);
            String oldest = null;
            int copies = 1;
            for (Map.Entry<String, String> copy : mCopies.entrySet()) {
                if (copy.getKey().equals(key)) {
                    continue;
                }
                if (oldest == null) {
                    oldest = copy.getKey();
                }
                ++copies;
                packages.add(copy.getValue());
            }
            int summaries = NotificationReposter.groupsCopies() ? packages.size() : 0;
            if (oldest == null || copies + summaries <= MAX_OWN_NOTIFICATIONS) {
                return;
            }
            dropCopy(oldest);
        }
    }

    private void dropCopy(@NonNull String key) {
        mCopySignatures.remove(key);
        mCopyInfo.remove(key);
        String sourcePackage = mCopies.remove(key);
        if (sourcePackage == null) {
            return;
        }
        String tag = NotificationReposter.copyTag(key);
        NotificationPins.forget(tag);
        mShade.cancelOwn(tag);
        cancelSummaryIfEmpty(sourcePackage);
    }

    private void cancelSummaryIfEmpty(@NonNull String sourcePackage) {
        if (NotificationReposter.groupsCopies() && !hasCopiesFor(sourcePackage)) {
            mShade.cancelOwn(NotificationReposter.summaryTag(sourcePackage));
        }
    }

    private boolean hasCopiesFor(@NonNull String sourcePackage) {
        return mCopies.containsValue(sourcePackage);
    }

    private void cancelSource(@NonNull String key) {
        mCancelling.add(key);
        mShade.cancelSource(key);
    }

    /**
     * An app's group summary stays in the shade after its last child is taken out, as an empty header. It goes too,
     * unless something in the group is still showing.
     */
    private void cancelEmptySummary(@NonNull String groupKey) {
        StatusBarNotification summary = null;
        for (StatusBarNotification sbn : mShade.active()) {
            if (!groupKey.equals(sbn.getGroupKey()) || mOwnPackage.equals(sbn.getPackageName())) {
                continue;
            }
            if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                summary = sbn;
            } else if (!mCancelling.contains(sbn.getKey())) {
                return;
            }
        }
        if (summary != null && summary.isClearable()
                && (summary.getNotification().flags & UNTOUCHABLE_SUMMARY_FLAGS) == 0) {
            cancelSource(summary.getKey());
        }
    }

    private void record(@NonNull StatusBarNotification sbn, @NonNull NotificationFilterDecision.Facts facts,
                        @Nullable NotificationFilterRule.Action action,
                        @NonNull NotificationFilterDecision.Result result) {
        if (action == null && !mSettings.isRecordAll()) {
            return;
        }
        String actionName = action != null ? action.name() : NotificationHistoryEntry.ACTION_NONE;
        String signature = actionName + '\n' + facts.fields.title + '\n' + facts.fields.text;
        if (signature.equals(mRecorded.get(sbn.getKey()))) {
            return;
        }
        mRecorded.put(sbn.getKey(), signature);
        String packageName = sbn.getPackageName();
        mHistory.accept(new NotificationHistoryEntry(mClock.getAsLong(), packageName,
                mApps.label(packageName).toString(), facts.fields, actionName,
                action != null ? result.ruleName : null, action != null && result.byQuietMode));
    }

    /**
     * A force stop ends every notification the app had, snoozed ones too. Android keeps listing those as snoozed
     * until their snooze runs out and doesn't tell the listener, so this is the one sign, and their copies go now.
     *
     * @param uid The app's uid, so another user's copy of it is left alone, or a negative number when it's unknown.
     */
    @MainThread
    void onPackageStopped(@NonNull String packageName, int uid) {
        if (mSnoozed.isEmpty()) {
            return;
        }
        Map<String, StatusBarNotification> snoozed = null;
        Iterator<Map.Entry<String, Snoozed>> it = mSnoozed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Snoozed> entry = it.next();
            Snoozed state = entry.getValue();
            if (!state.packageName.equals(packageName) || (uid >= 0 && state.uid != uid)) {
                continue;
            }
            if (snoozed == null) {
                snoozed = snoozedByKey();
            }
            StatusBarNotification sbn = snoozed.get(entry.getKey());
            if (sbn != null) {
                state.ended = true;
                state.endedPostTime = sbn.getPostTime();
            } else {
                it.remove();
            }
            dropCopy(entry.getKey());
        }
    }

    /** Whether anything this listener snoozed is out of sight, so the service keeps looking in on it. */
    boolean hasSnoozed() {
        return !mSnoozed.isEmpty();
    }

    /**
     * The regular look at what's snoozed, for what Android doesn't report: an update, and a snooze that ran out on
     * one its app had ended.
     */
    @MainThread
    void check() {
        refreshSnoozed();
    }

    @NonNull
    private Map<String, StatusBarNotification> snoozedByKey() {
        Map<String, StatusBarNotification> snoozed = new HashMap<>();
        for (StatusBarNotification sbn : mShade.snoozed()) {
            snoozed.put(sbn.getKey(), sbn);
        }
        return snoozed;
    }

    /** For tests: the keys of the originals that have a copy, oldest first. */
    @NonNull
    List<String> copiedKeys() {
        return new ArrayList<>(mCopies.keySet());
    }

    /** A name saved in a copy's extras, or the fallback for a copy made before it was saved. */
    @NonNull
    private static <E extends Enum<E>> E savedEnum(@Nullable Bundle extras, @NonNull String key,
                                                   @NonNull E fallback) {
        String name = extras != null ? extras.getString(key) : null;
        if (name != null) {
            for (E value : fallback.getDeclaringClass().getEnumConstants()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
        }
        return fallback;
    }

    @NonNull
    private static <K, V> Map<K, V> boundedMap() {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > REMEMBERED;
            }
        };
    }

    @NonNull
    private static Set<String> boundedSet() {
        return Collections.newSetFromMap(boundedMap());
    }
}
