// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.tags;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.AppNote;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class AppNoteStore {
    private static final String TAG = AppNoteStore.class.getSimpleName();
    private static final AtomicBoolean ROOM_RECONCILE_SCHEDULED = new AtomicBoolean();
    @VisibleForTesting
    public static final String PREFS_NAME = "app_notes";
    private static final String KEY_VERSION = "_schema";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_NOTE_LEN = 4000;

    private final SharedPreferences mPrefs;

    public AppNoteStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!mPrefs.contains(KEY_VERSION)) {
            mPrefs.edit().putInt(KEY_VERSION, SCHEMA_VERSION).apply();
        }
        // Keep the Room table populated for database-backed consumers while the
        // preference file remains the immediate, UI-safe compatibility cache.
        if (ROOM_RECONCILE_SCHEDULED.compareAndSet(false, true)) {
            ThreadUtils.postOnBackgroundThread(() -> reconcileRoom(mPrefs));
        }
    }

    @AnyThread
    @Nullable
    public String getNote(@NonNull String packageName) {
        return normalizeNote(mPrefs.getString(packageName, null));
    }

    @AnyThread
    public boolean hasNote(@NonNull String packageName) {
        return getNote(packageName) != null;
    }

    @AnyThread
    public void setNote(@NonNull String packageName, @Nullable CharSequence note) {
        String normalized = normalizeNote(note);
        if (normalized == null) {
            clear(packageName);
        } else {
            mPrefs.edit().putString(packageName, normalized).apply();
            syncRoomAsync(packageName);
        }
    }

    @AnyThread
    public void clear(@NonNull String packageName) {
        mPrefs.edit().remove(packageName).apply();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                AppsDb.getInstance().appNoteDao().delete(packageName);
            } catch (Exception e) {
                Log.w(TAG, "Could not clear the Room note row for " + packageName, e);
            }
        });
    }

    @AnyThread
    @NonNull
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : mPrefs.getAll().entrySet()) {
            if (KEY_VERSION.equals(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String note = normalizeNote((String) value);
            if (note != null) {
                out.put(entry.getKey(), note);
            }
        }
        return out;
    }

    /** Reconcile the legacy preference cache into the Room note table. */
    @WorkerThread
    public static void reconcileRoom(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        reconcileRoom(prefs);
    }

    @WorkerThread
    private static void reconcileRoom(@NonNull SharedPreferences prefs) {
        try {
            Map<String, String> notes = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (KEY_VERSION.equals(entry.getKey()) || !(entry.getValue() instanceof String)) {
                    continue;
                }
                String note = normalizeNote((String) entry.getValue());
                if (note != null) {
                    notes.put(entry.getKey(), note);
                }
            }
            AppsDb db = AppsDb.getInstance();
            for (Map.Entry<String, String> entry : notes.entrySet()) {
                AppNote row = new AppNote();
                row.packageName = entry.getKey();
                row.note = entry.getValue();
                row.updatedAt = System.currentTimeMillis();
                db.appNoteDao().insert(row);
            }
            for (AppNote row : db.appNoteDao().getAll()) {
                if (!notes.containsKey(row.packageName)) {
                    db.appNoteDao().delete(row.packageName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not reconcile app notes into Room.", e);
        }
    }

    private void syncRoomAsync(@NonNull String packageName) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                String note = normalizeNote(mPrefs.getString(packageName, null));
                if (note == null) {
                    AppsDb.getInstance().appNoteDao().delete(packageName);
                    return;
                }
                AppNote row = new AppNote();
                row.packageName = packageName;
                row.note = note;
                row.updatedAt = System.currentTimeMillis();
                AppsDb.getInstance().appNoteDao().insert(row);
            } catch (Exception e) {
                Log.w(TAG, "Could not write the Room note row for " + packageName, e);
            }
        });
    }

    @AnyThread
    @Nullable
    public static String normalizeNote(@Nullable CharSequence note) {
        if (note == null) {
            return null;
        }
        String normalized = note.toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= MAX_NOTE_LEN ? normalized : normalized.substring(0, MAX_NOTE_LEN).trim();
    }
}
