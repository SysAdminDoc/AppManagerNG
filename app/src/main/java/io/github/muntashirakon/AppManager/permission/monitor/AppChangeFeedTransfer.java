// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Versioned, bounded JSON transfer and query helpers for the local change feed. */
public final class AppChangeFeedTransfer {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = AppChangeFeedStore.MAX_ENTRIES;
    public static final long MAX_IMPORT_BYTES = AppChangeFeedStore.MAX_STORE_BYTES;

    private AppChangeFeedTransfer() {
    }

    public static final class ParseResult {
        @NonNull
        public final List<AppChangeFeedEntry> entries;
        @Nullable
        public final String error;

        private ParseResult(@NonNull List<AppChangeFeedEntry> entries, @Nullable String error) {
            this.entries = entries;
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }
    }

    @NonNull
    public static String serialize(@NonNull List<AppChangeFeedEntry> entries) {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        int count = Math.min(entries.size(), MAX_ENTRIES);
        for (int i = 0; i < count; ++i) {
            AppChangeFeedEntry entry = entries.get(i);
            if (entry == null) continue;
            JSONObject object = new JSONObject();
            try {
                object.put("kind", entry.kind);
                object.put("package_name", entry.packageName);
                object.put("timestamp_millis", entry.timestampMillis);
                object.put("title", entry.title);
                object.put("body", entry.body);
                if (entry.hasVersionContext()) {
                    object.put("before_version_code", entry.beforeVersionCode);
                    object.put("after_version_code", entry.afterVersionCode);
                }
                array.put(object);
            } catch (JSONException impossible) {
                // JSONObject only rejects unsupported values, and all entry fields are strings or longs.
            }
        }
        try {
            root.put("schema_version", SCHEMA_VERSION);
            root.put("entries", array);
            return root.toString(2);
        } catch (JSONException impossible) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}";
        }
    }

    @NonNull
    public static ParseResult parse(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return invalid("The file is empty.");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) {
            return invalid("The file is too large.");
        }
        try {
            JSONObject root = new JSONObject(json);
            Object version = root.has("schema_version") ? root.get("schema_version") : null;
            if (!(version instanceof Number) || ((Number) version).intValue() != SCHEMA_VERSION) {
                return invalid("This change-feed version is not supported.");
            }
            JSONArray array = root.optJSONArray("entries");
            if (array == null) return invalid("The entries field is missing.");
            if (array.length() > MAX_ENTRIES) {
                return invalid("The file contains too many entries.");
            }
            List<AppChangeFeedEntry> entries = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); ++i) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) return invalid("An entry is not an object.");
                String kind = requiredString(object, "kind");
                String packageName = requiredString(object, "package_name");
                String title = requiredString(object, "title");
                String body = requiredString(object, "body");
                Long timestamp = requiredLong(object, "timestamp_millis");
                if (kind == null || packageName == null || title == null || body == null
                        || timestamp == null || timestamp < 0) {
                    return invalid("An entry is missing a valid field.");
                }
                Long before = optionalLong(object, "before_version_code");
                Long after = optionalLong(object, "after_version_code");
                if ((object.has("before_version_code") && before == null)
                        || (object.has("after_version_code") && after == null)
                        || (before != null && before < AppChangeFeedEntry.UNKNOWN_VERSION_CODE)
                        || (after != null && after < AppChangeFeedEntry.UNKNOWN_VERSION_CODE)) {
                    return invalid("An entry has an invalid version code.");
                }
                entries.add(new AppChangeFeedEntry(kind, packageName, timestamp, title, body,
                        before != null ? before : AppChangeFeedEntry.UNKNOWN_VERSION_CODE,
                        after != null ? after : AppChangeFeedEntry.UNKNOWN_VERSION_CODE));
            }
            entries.sort(Comparator.comparingLong((AppChangeFeedEntry entry) -> entry.timestampMillis)
                    .reversed());
            return new ParseResult(entries, null);
        } catch (JSONException e) {
            return invalid("The file is not valid JSON.");
        }
    }

    @NonNull
    public static List<AppChangeFeedEntry> filter(@NonNull List<AppChangeFeedEntry> source,
                                                  @Nullable String packageQuery,
                                                  @Nullable String kindQuery,
                                                  long fromMillis,
                                                  long untilMillis) {
        String packageNeedle = normalize(packageQuery);
        String kindNeedle = normalize(kindQuery);
        if (fromMillis > untilMillis) return Collections.emptyList();
        List<AppChangeFeedEntry> filtered = new ArrayList<>();
        for (AppChangeFeedEntry entry : source) {
            if (entry == null || entry.timestampMillis < fromMillis || entry.timestampMillis > untilMillis) {
                continue;
            }
            if (!packageNeedle.isEmpty()
                    && !entry.packageName.toLowerCase(Locale.ROOT).contains(packageNeedle)) {
                continue;
            }
            if (!kindNeedle.isEmpty()
                    && !entry.kind.toLowerCase(Locale.ROOT).contains(kindNeedle)) {
                continue;
            }
            filtered.add(entry);
            if (filtered.size() == MAX_ENTRIES) break;
        }
        filtered.sort(Comparator.comparingLong((AppChangeFeedEntry entry) -> entry.timestampMillis)
                .reversed());
        return filtered;
    }

    @Nullable
    private static String requiredString(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        try {
            Object value = object.get(key);
            if (!(value instanceof String)) return null;
            String string = (String) value;
            return string.isEmpty() && !"body".equals(key) ? null : string;
        } catch (JSONException e) {
            return null;
        }
    }

    @Nullable
    private static Long requiredLong(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        return optionalLong(object, key);
    }

    @Nullable
    private static Long optionalLong(@NonNull JSONObject object, @NonNull String key) {
        try {
            Object value = object.get(key);
            if (!(value instanceof Number)) return null;
            return ((Number) value).longValue();
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static ParseResult invalid(@NonNull String error) {
        return new ParseResult(Collections.emptyList(), error);
    }
}
