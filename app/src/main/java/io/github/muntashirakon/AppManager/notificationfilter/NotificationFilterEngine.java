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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        boolean canPost(@NonNull NotificationFilterRule.Action action);

        /** @return Whether it was posted. */
        boolean post(@NonNull String tag, @NonNull Notification notification);

        void cancelOwn(@NonNull String tag);
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
    /** What each copy last said, so an update that says the same thing doesn't vibrate again. */
    private final Map<String, String> mCopySignatures = new LinkedHashMap<>();
    /** Groups a child was taken out of, for a summary that turns up after its children. */
    private final Set<String> mFilteredGroups = boundedSet();
    /** Originals asked to go that the shade may still list, since a cancel lands a moment later. */
    private final Set<String> mCancelling = boundedSet();
    /** What was last written down for each key, so an app that reposts the same thing isn't recorded twice. */
    private final Map<String, String> mRecorded = boundedMap();

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

    /** Finds the copies still in the shade after the listener was restarted. */
    @MainThread
    void onConnected() {
        mCopies.clear();
        mCopySignatures.clear();
        List<StatusBarNotification> own = new ArrayList<>();
        for (StatusBarNotification sbn : mShade.active()) {
            if (mOwnPackage.equals(sbn.getPackageName()) && NotificationReposter.sourceKeyOf(sbn.getTag()) != null) {
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
            }
        }
    }

    @MainThread
    void onPosted(@NonNull StatusBarNotification sbn) {
        if (mOwnPackage.equals(sbn.getPackageName())) {
            return;
        }
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
        NotificationFilterRule.Action quietAction = mSettings.isQuietModeActive(now) ? mSettings.getQuietAction()
                : null;
        NotificationFilterDecision.Result result = NotificationFilterDecision.decide(facts, mOwnPackage,
                mSettings.getRules(), quietAction);
        if (result.exempt) {
            dropCopy(key);
            return;
        }
        NotificationFilterRule.Action action = result.action;
        if (action == null || action == NotificationFilterRule.Action.ALLOW) {
            // The original shows again as it is, so an earlier copy would be a duplicate.
            dropCopy(key);
            record(sbn, facts, action, result);
            return;
        }
        if (action == NotificationFilterRule.Action.BLOCK) {
            dropCopy(key);
            cancelSource(key);
        } else if (!repost(sbn, facts, action)) {
            // Without a copy the notification would be lost, so it stays as the app posted it.
            record(sbn, facts, null, result);
            return;
        }
        if (notification.getGroup() != null) {
            mFilteredGroups.add(sbn.getGroupKey());
            cancelEmptySummary(sbn.getGroupKey());
        }
        record(sbn, facts, action, result);
    }

    @MainThread
    void onRemoved(@NonNull StatusBarNotification sbn) {
        mCancelling.remove(sbn.getKey());
        if (!mOwnPackage.equals(sbn.getPackageName())) {
            return;
        }
        String sourceKey = NotificationReposter.sourceKeyOf(sbn.getTag());
        if (sourceKey == null) {
            return;
        }
        // The user dismissed a copy, or tapped one that cancels itself.
        String sourcePackage = mCopies.remove(sourceKey);
        mCopySignatures.remove(sourceKey);
        if (sourcePackage != null) {
            cancelSummaryIfEmpty(sourcePackage);
        }
    }

    private boolean repost(@NonNull StatusBarNotification sbn, @NonNull NotificationFilterDecision.Facts facts,
                           @NonNull NotificationFilterRule.Action action) {
        if (!mShade.canPost(action)) {
            return false;
        }
        String key = sbn.getKey();
        String sourcePackage = sbn.getPackageName();
        String signature = action.name() + '\n' + facts.fields.title + '\n' + facts.fields.text;
        boolean alertAgain = !signature.equals(mCopySignatures.get(key));
        makeRoom(key, sourcePackage);
        CharSequence label = mApps.label(sourcePackage);
        Bitmap icon = mApps.icon(sourcePackage);
        Notification copy = NotificationReposter.buildCopy(mContext, sbn, action, label, icon, alertAgain);
        if (!mShade.post(NotificationReposter.copyTag(key), copy)) {
            return false;
        }
        boolean hadCopies = hasCopiesFor(sourcePackage);
        mCopies.remove(key);
        mCopies.put(key, sourcePackage);
        mCopySignatures.put(key, signature);
        if (NotificationReposter.groupsCopies() && !hadCopies) {
            mShade.post(NotificationReposter.summaryTag(sourcePackage),
                    NotificationReposter.buildSummary(mContext, sourcePackage, label, icon));
        }
        // Only now that the copy is up, so nothing is lost if posting it failed.
        cancelSource(key);
        return true;
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
        String sourcePackage = mCopies.remove(key);
        if (sourcePackage == null) {
            return;
        }
        mCopySignatures.remove(key);
        mShade.cancelOwn(NotificationReposter.copyTag(key));
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

    /** For tests: the keys of the originals that have a copy, oldest first. */
    @NonNull
    List<String> copiedKeys() {
        return new ArrayList<>(mCopies.keySet());
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
