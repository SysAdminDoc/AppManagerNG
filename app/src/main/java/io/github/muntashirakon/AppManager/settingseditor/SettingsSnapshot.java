// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Every readable key of the three tables as one JSON file, to keep before a round of changes or to compare two
 * phones. A table that couldn't be read is named under {@code unreadable} with the reason.
 */
public final class SettingsSnapshot {
    public static final int FORMAT_VERSION = 1;

    private SettingsSnapshot() {
    }

    @NonNull
    public static JSONObject toJson(@NonNull Map<SettingsTable, List<SettingEntry>> tables,
                                    @NonNull Map<SettingsTable, String> unreadable, long timeMillis,
                                    int androidApi, int userId) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_VERSION);
        root.put("created", isoUtc(timeMillis));
        root.put("android_api", androidApi);
        root.put("user", userId);
        JSONObject tablesJson = new JSONObject();
        for (SettingsTable table : SettingsTable.values()) {
            List<SettingEntry> entries = tables.get(table);
            if (entries == null) {
                continue;
            }
            JSONObject rows = new JSONObject();
            for (SettingEntry entry : entries) {
                rows.put(entry.key, entry.value != null ? entry.value : JSONObject.NULL);
            }
            tablesJson.put(table.shellName, rows);
        }
        root.put("tables", tablesJson);
        if (!unreadable.isEmpty()) {
            JSONObject reasons = new JSONObject();
            for (SettingsTable table : SettingsTable.values()) {
                String reason = unreadable.get(table);
                if (reason != null) {
                    reasons.put(table.shellName, reason);
                }
            }
            root.put("unreadable", reasons);
        }
        return root;
    }

    /** A file name with the date and time, so snapshots sort and don't overwrite each other. */
    @NonNull
    public static String fileName(long timeMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        return "settings-snapshot-" + format.format(new Date(timeMillis)) + ".json";
    }

    @NonNull
    private static String isoUtc(long timeMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timeMillis));
    }
}
