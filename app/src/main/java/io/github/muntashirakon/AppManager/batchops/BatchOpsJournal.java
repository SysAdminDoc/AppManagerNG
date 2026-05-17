// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Ops;

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

    private static final String STATE_INTENT_RECORDED = "intent_recorded";
    private static final String STATE_EXECUTING = "executing";
    private static final String STATE_INTERRUPTED = "interrupted";

    private BatchOpsJournal() {
    }

    public static synchronized void recordIntent(@NonNull Context context, @NonNull BatchQueueItem item) {
        writeEntry(context, item, STATE_INTENT_RECORDED, null);
    }

    public static synchronized void recordExecuting(@NonNull Context context, @NonNull BatchQueueItem item) {
        writeEntry(context, item, STATE_EXECUTING, null);
    }

    public static synchronized void markInterrupted(@NonNull Context context, @Nullable Throwable throwable) {
        Entry entry = readEntry(context);
        if (entry == null) {
            return;
        }
        String reason = throwable != null ? summarizeThrowable(throwable) : entry.getReason();
        writeEntry(context, entry.getQueueItem(), STATE_INTERRUPTED, reason);
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
        try {
            long now = System.currentTimeMillis();
            JSONObject existing = readEntryJson(context);
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
            getPreferences(context).edit().putString(KEY_ENTRY, entry.toString()).apply();
        } catch (JSONException ignore) {
        }
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
    }
}
