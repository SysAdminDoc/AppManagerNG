// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the output of {@code settings list <table>}: one {@code key=value} per line, split at the first {@code =}
 * so a value may hold more of them. The command prints a missing value as {@code null}. A value with line breaks
 * goes on over the lines after it, which have no {@code =} of their own.
 */
public final class SettingsListParser {
    private SettingsListParser() {
    }

    /** Every row, sorted by key, the last one winning if a key comes twice. */
    @NonNull
    public static List<SettingEntry> parse(@NonNull List<String> lines) {
        Map<String, String> rows = new LinkedHashMap<>();
        String key = null;
        StringBuilder value = null;
        int pendingBreaks = 0;
        for (String line : lines) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                if (key != null) {
                    rows.put(key, valueOf(value));
                }
                key = line.substring(0, equals);
                value = new StringBuilder(line.substring(equals + 1));
                pendingBreaks = 0;
            } else if (key != null) {
                // An empty line only counts once more of the value follows it.
                if (line.isEmpty()) {
                    ++pendingBreaks;
                } else {
                    for (int i = 0; i <= pendingBreaks; ++i) {
                        value.append('\n');
                    }
                    value.append(line);
                    pendingBreaks = 0;
                }
            }
            // Anything before the first row is the shell talking, not a setting.
        }
        if (key != null) {
            rows.put(key, valueOf(value));
        }
        List<SettingEntry> entries = new ArrayList<>(rows.size());
        for (Map.Entry<String, String> row : rows.entrySet()) {
            entries.add(new SettingEntry(row.getKey(), row.getValue()));
        }
        Collections.sort(entries, (a, b) -> {
            int order = a.key.compareToIgnoreCase(b.key);
            return order != 0 ? order : a.key.compareTo(b.key);
        });
        return entries;
    }

    @Nullable
    private static String valueOf(@NonNull StringBuilder value) {
        String text = value.toString();
        return "null".equals(text) ? null : text;
    }
}
