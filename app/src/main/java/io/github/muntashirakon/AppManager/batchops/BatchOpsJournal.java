// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class BatchOpsJournal {
    private static final String PREF_NAME = BuildConfig.APPLICATION_ID + ".batch_ops_journal";
    private static final String KEY_ENTRY = "entry";

    private static final String KEY_ID = "id";
    private static final String KEY_STATE = "state";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_MODE = "mode";
    private static final String KEY_QUEUE_ITEM = "queue_item";
    private static final String KEY_REASON = "reason";
    private static final String KEY_COMPLETED_TARGETS = "completed_targets";
    private static final String KEY_FAILED_TARGETS = "failed_targets";
    private static final String KEY_PACKAGE = "package";
    private static final String KEY_USER = "user";

    private static final String STATE_INTENT_RECORDED = "intent_recorded";
    private static final String STATE_EXECUTING = "executing";
    private static final String STATE_INTERRUPTED = "interrupted";

    private BatchOpsJournal() {
    }

    public static synchronized void recordIntent(@NonNull Context context, @NonNull BatchQueueItem item) {
        // A new batch op begins here. Start a fresh record (new id/created_at, empty
        // target sets) instead of inheriting them from a prior entry that may still be
        // sitting in the journal (e.g. an interrupted op the user chose "Not now" on).
        // Inheriting stale completed/failed targets would make a later interrupted-op
        // retry skip packages that this op never actually processed.
        writeEntry(context, item, STATE_INTENT_RECORDED, null,
                new ArrayList<>(0), new ArrayList<>(0), true);
    }

    public static synchronized void recordExecuting(@NonNull Context context, @NonNull BatchQueueItem item) {
        writeEntry(context, item, STATE_EXECUTING, null);
    }

    public static synchronized void markInterrupted(@NonNull Context context, @Nullable Throwable throwable) {
        markInterrupted(context, throwable, null);
    }

    public static synchronized void markInterrupted(@NonNull Context context,
                                                    @Nullable Throwable throwable,
                                                    @Nullable BatchOpsManager.Result result) {
        Entry entry = readEntry(context);
        if (entry == null) {
            return;
        }
        String reason = throwable != null ? summarizeThrowable(throwable) : entry.getReason();
        List<UserPackagePair> completedTargets = result != null
                ? completedTargets(entry.getQueueItem(), result)
                : null;
        writeEntry(context, entry.getQueueItem(), STATE_INTERRUPTED, reason,
                completedTargets, failedTargets(result));
    }

    public static synchronized void recordTargetFinished(@NonNull Context context,
                                                         @NonNull UserPackagePair target,
                                                         boolean failed) {
        Entry entry = readEntry(context);
        if (entry == null) {
            return;
        }
        List<UserPackagePair> completedTargets = deserializeTargets(entry.mEntry.optJSONArray(KEY_COMPLETED_TARGETS));
        List<UserPackagePair> failedTargets = deserializeTargets(entry.mEntry.optJSONArray(KEY_FAILED_TARGETS));
        removeTarget(completedTargets, target);
        removeTarget(failedTargets, target);
        if (failed) {
            failedTargets.add(target);
        } else {
            completedTargets.add(target);
        }
        writeEntry(context, entry.getQueueItem(), entry.getState(), entry.getReason(),
                completedTargets, failedTargets);
    }

    public static synchronized void markCompleted(@NonNull Context context) {
        clear(context);
    }

    public static synchronized void dismissInterrupted(@NonNull Context context) {
        clear(context);
    }

    @Nullable
    public static synchronized Entry getInterruptedOperation(@NonNull Context context, boolean batchServiceRunning) {
        if (batchServiceRunning) {
            return null;
        }
        Entry entry = readEntry(context);
        if (entry == null) {
            return null;
        }
        String state = entry.getState();
        if (STATE_INTENT_RECORDED.equals(state)
                || STATE_EXECUTING.equals(state)
                || STATE_INTERRUPTED.equals(state)) {
            return entry;
        }
        clear(context);
        return null;
    }

    private static void writeEntry(@NonNull Context context,
                                   @NonNull BatchQueueItem item,
                                   @NonNull String state,
                                   @Nullable String reason) {
        writeEntry(context, item, state, reason, null, null);
    }

    private static void writeEntry(@NonNull Context context,
                                   @NonNull BatchQueueItem item,
                                   @NonNull String state,
                                   @Nullable String reason,
                                   @Nullable List<UserPackagePair> completedTargets,
                                   @Nullable List<UserPackagePair> failedTargets) {
        writeEntry(context, item, state, reason, completedTargets, failedTargets, false);
    }

    private static void writeEntry(@NonNull Context context,
                                   @NonNull BatchQueueItem item,
                                   @NonNull String state,
                                   @Nullable String reason,
                                   @Nullable List<UserPackagePair> completedTargets,
                                   @Nullable List<UserPackagePair> failedTargets,
                                   boolean freshEntry) {
        try {
            long now = System.currentTimeMillis();
            JSONObject existing = freshEntry ? null : readEntryJson(context);
            long id = existing != null ? existing.optLong(KEY_ID, now) : now;
            long createdAt = existing != null ? existing.optLong(KEY_CREATED_AT, now) : now;
            item.getUsers();
            JSONObject entry = new JSONObject()
                    .put(KEY_ID, id)
                    .put(KEY_STATE, state)
                    .put(KEY_CREATED_AT, createdAt)
                    .put(KEY_UPDATED_AT, now)
                    .put(KEY_MODE, getModeLabel(context))
                    .put(KEY_QUEUE_ITEM, item.serializeToJson());
            if (reason != null && !reason.isEmpty()) {
                entry.put(KEY_REASON, reason);
            }
            JSONArray completedJson = completedTargets != null
                    ? serializeTargets(completedTargets)
                    : existing != null ? existing.optJSONArray(KEY_COMPLETED_TARGETS) : null;
            JSONArray failedJson = failedTargets != null
                    ? serializeTargets(failedTargets)
                    : existing != null ? existing.optJSONArray(KEY_FAILED_TARGETS) : null;
            if (completedJson != null) {
                entry.put(KEY_COMPLETED_TARGETS, completedJson);
            }
            if (failedJson != null) {
                entry.put(KEY_FAILED_TARGETS, failedJson);
            }
            getPreferences(context).edit().putString(KEY_ENTRY, entry.toString()).apply();
        } catch (JSONException ignore) {
        }
    }

    @NonNull
    private static List<UserPackagePair> completedTargets(@NonNull BatchQueueItem item,
                                                          @Nullable BatchOpsManager.Result result) {
        if (result == null) {
            return new ArrayList<>(0);
        }
        Set<String> failedTargetKeys = new HashSet<>();
        for (UserPackagePair pair : result.getFailedUserPackagePairs()) {
            failedTargetKeys.add(targetKey(pair));
        }
        ArrayList<UserPackagePair> completedTargets = new ArrayList<>();
        ArrayList<String> packages = item.getPackages();
        ArrayList<Integer> users = item.getUsers();
        for (int i = 0; i < packages.size(); ++i) {
            UserPackagePair pair = new UserPackagePair(packages.get(i), users.get(i));
            if (!failedTargetKeys.contains(targetKey(pair))) {
                completedTargets.add(pair);
            }
        }
        return completedTargets;
    }

    @Nullable
    private static List<UserPackagePair> failedTargets(@Nullable BatchOpsManager.Result result) {
        return result != null ? result.getFailedUserPackagePairs() : null;
    }

    private static void removeTarget(@NonNull List<UserPackagePair> targets,
                                     @NonNull UserPackagePair target) {
        String key = targetKey(target);
        for (int i = targets.size() - 1; i >= 0; --i) {
            if (targetKey(targets.get(i)).equals(key)) {
                targets.remove(i);
            }
        }
    }

    @NonNull
    private static JSONArray serializeTargets(@NonNull List<UserPackagePair> targets) throws JSONException {
        JSONArray array = new JSONArray();
        for (UserPackagePair target : targets) {
            if (!isValidTarget(target.getPackageName(), target.getUserId())) {
                continue;
            }
            array.put(new JSONObject()
                    .put(KEY_PACKAGE, target.getPackageName())
                    .put(KEY_USER, target.getUserId()));
        }
        return array;
    }

    @NonNull
    private static List<UserPackagePair> deserializeTargets(@Nullable JSONArray array) {
        ArrayList<UserPackagePair> targets = new ArrayList<>();
        if (array == null) {
            return targets;
        }
        for (int i = 0; i < array.length(); ++i) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String packageName = item.optString(KEY_PACKAGE, "");
            int userId = item.optInt(KEY_USER, 0);
            if (!isValidTarget(packageName, userId)) {
                continue;
            }
            targets.add(new UserPackagePair(packageName, userId));
        }
        return targets;
    }

    private static boolean isValidTarget(@NonNull String packageName, int userId) {
        return userId >= 0 && PackageUtils.validateName(packageName);
    }

    @NonNull
    private static String targetKey(@NonNull UserPackagePair target) {
        return target.getPackageName() + '\u0000' + target.getUserId();
    }

    @Nullable
    private static Entry readEntry(@NonNull Context context) {
        JSONObject entry = readEntryJson(context);
        if (entry == null) {
            return null;
        }
        try {
            return new Entry(entry);
        } catch (JSONException e) {
            clear(context);
            return null;
        }
    }

    @Nullable
    private static JSONObject readEntryJson(@NonNull Context context) {
        String serialized = getPreferences(context).getString(KEY_ENTRY, null);
        if (serialized == null) {
            return null;
        }
        try {
            return new JSONObject(serialized);
        } catch (JSONException e) {
            clear(context);
            return null;
        }
    }

    private static void clear(@NonNull Context context) {
        getPreferences(context).edit().remove(KEY_ENTRY).apply();
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String getModeLabel(@NonNull Context context) {
        try {
            return Ops.getInferredMode(context).toString();
        } catch (Throwable ignore) {
            return Ops.getMode();
        }
    }

    @Nullable
    private static String summarizeThrowable(@Nullable Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder message = new StringBuilder(throwable.getClass().getSimpleName());
        if (throwable.getMessage() != null) {
            message.append(": ").append(throwable.getMessage().replaceAll("\\s+", " ").trim());
        }
        if (message.length() > 240) {
            return message.substring(0, 237) + "...";
        }
        return message.toString();
    }

    public static final class Entry {
        @NonNull
        private final JSONObject mEntry;
        @NonNull
        private final BatchQueueItem mQueueItem;

        private Entry(@NonNull JSONObject entry) throws JSONException {
            mEntry = entry;
            mQueueItem = BatchQueueItem.DESERIALIZER.deserialize(entry.getJSONObject(KEY_QUEUE_ITEM));
        }

        @NonNull
        public String getState() {
            return mEntry.optString(KEY_STATE, STATE_INTERRUPTED);
        }

        public long getCreatedAt() {
            return mEntry.optLong(KEY_CREATED_AT, 0);
        }

        @NonNull
        public String getModeLabel() {
            return mEntry.optString(KEY_MODE, "");
        }

        @Nullable
        public String getReason() {
            String reason = mEntry.optString(KEY_REASON, "");
            return reason.isEmpty() ? null : reason;
        }

        @NonNull
        public BatchQueueItem getQueueItem() {
            return mQueueItem;
        }

        public int getTargetCount() {
            return mQueueItem.getPackages().size();
        }

        public int getCompletedTargetCount() {
            return deserializeTargets(mEntry.optJSONArray(KEY_COMPLETED_TARGETS)).size();
        }

        public int getFailedTargetCount() {
            return deserializeTargets(mEntry.optJSONArray(KEY_FAILED_TARGETS)).size();
        }

        public boolean hasPartialOutcome() {
            return mEntry.has(KEY_COMPLETED_TARGETS) || mEntry.has(KEY_FAILED_TARGETS);
        }

        public int getRetryTargetCount() {
            return getRetryQueueItem().getPackages().size();
        }

        @NonNull
        public BatchQueueItem getRetryQueueItem() {
            List<UserPackagePair> completedTargets = deserializeTargets(mEntry.optJSONArray(KEY_COMPLETED_TARGETS));
            if (completedTargets.isEmpty()) {
                return mQueueItem;
            }
            Set<String> completedTargetKeys = new HashSet<>();
            for (UserPackagePair pair : completedTargets) {
                completedTargetKeys.add(targetKey(pair));
            }
            ArrayList<UserPackagePair> retryTargets = new ArrayList<>();
            ArrayList<String> packages = mQueueItem.getPackages();
            ArrayList<Integer> users = mQueueItem.getUsers();
            for (int i = 0; i < packages.size(); ++i) {
                UserPackagePair pair = new UserPackagePair(packages.get(i), users.get(i));
                if (!completedTargetKeys.contains(targetKey(pair))) {
                    retryTargets.add(pair);
                }
            }
            return mQueueItem.withTargets(retryTargets);
        }
    }
}
