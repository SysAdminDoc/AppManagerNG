// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.tags;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppNoteStore {
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
        }
    }

    @AnyThread
    public void clear(@NonNull String packageName) {
        mPrefs.edit().remove(packageName).apply();
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
