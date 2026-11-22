// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * One change made in the editor: what the key held before ({@code null} if it wasn't set) and what it holds after
 * ({@code null} if it was deleted).
 */
public final class SettingsChange {
    private static final String TIME = "time";
    private static final String TABLE = "table";
    private static final String KEY = "key";
    private static final String OLD = "old";
    private static final String NEW = "new";

    public final long timeMillis;
    @NonNull
    public final SettingsTable table;
    @NonNull
    public final String key;
    @Nullable
    public final String oldValue;
    @Nullable
    public final String newValue;

    public SettingsChange(long timeMillis, @NonNull SettingsTable table, @NonNull String key,
                          @Nullable String oldValue, @Nullable String newValue) {
        this.timeMillis = timeMillis;
        this.table = table;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public boolean isDeletion() {
        return newValue == null;
    }

    /**
     * The change that puts this one back: the key returns to its old value, or is deleted if it wasn't set. It starts
     * from what the key holds now, which may no longer be this change's new value.
     */
    @NonNull
    public SettingsChange revert(@Nullable String currentValue, long timeMillis) {
        return new SettingsChange(timeMillis, table, key, currentValue, oldValue);
    }

    /** Whether applying this would change nothing. */
    public boolean isNoOp() {
        return Objects.equals(oldValue, newValue);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put(TIME, timeMillis);
        object.put(TABLE, table.shellName);
        object.put(KEY, key);
        // A missing field is "not set" or "deleted"; JSON null would read back as the text "null".
        if (oldValue != null) {
            object.put(OLD, oldValue);
        }
        if (newValue != null) {
            object.put(NEW, newValue);
        }
        return object;
    }

    @NonNull
    public static SettingsChange fromJson(@NonNull JSONObject object) throws JSONException {
        SettingsTable table = SettingsTable.fromShellName(object.getString(TABLE));
        if (table == null) {
            throw new JSONException("Unknown table " + object.getString(TABLE));
        }
        return new SettingsChange(object.getLong(TIME), table, object.getString(KEY),
                object.has(OLD) ? object.getString(OLD) : null, object.has(NEW) ? object.getString(NEW) : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingsChange)) return false;
        SettingsChange that = (SettingsChange) o;
        return timeMillis == that.timeMillis && table == that.table && key.equals(that.key)
                && Objects.equals(oldValue, that.oldValue) && Objects.equals(newValue, that.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMillis, table, key, oldValue, newValue);
    }
}
