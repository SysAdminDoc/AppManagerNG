// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** JSON-backed view overrides for individual file-manager folders. */
public final class FmFolderViewPreferences {
    private static final int MAX_ENTRIES = 128;
    private static final int PERSISTED_OPTIONS = FmListOptions.OPTIONS_DISPLAY_DOT_FILES
            | FmListOptions.OPTIONS_FOLDERS_FIRST;

    public static final class Value {
        @FmListOptions.SortOrder
        final int sortBy;
        final boolean reverseSort;
        @FmListOptions.Options
        final int options;

        Value(@FmListOptions.SortOrder int sortBy, boolean reverseSort,
              @FmListOptions.Options int options) {
            this.sortBy = sortBy;
            this.reverseSort = reverseSort;
            this.options = options & PERSISTED_OPTIONS;
        }
    }

    private FmFolderViewPreferences() {
    }

    @Nullable
    public static Value get(@Nullable String serialized, @NonNull Uri uri) {
        if (TextUtils.isEmpty(serialized)) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(serialized);
            JSONObject value = root.optJSONObject(key(uri));
            if (value == null || !value.has("sort") || !value.has("reverse")
                    || !value.has("options")) {
                return null;
            }
            int sortBy = value.getInt("sort");
            if (sortBy < FmListOptions.SORT_BY_NAME || sortBy > FmListOptions.SORT_BY_TYPE) {
                return null;
            }
            return new Value(sortBy, value.getBoolean("reverse"), value.getInt("options"));
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    @NonNull
    public static String put(@Nullable String serialized, @NonNull Uri uri,
                             @FmListOptions.SortOrder int sortBy, boolean reverseSort,
                             @FmListOptions.Options int options) {
        JSONObject root = parse(serialized);
        try {
            root.put(key(uri), new JSONObject()
                    .put("sort", sortBy)
                    .put("reverse", reverseSort)
                    .put("options", options & PERSISTED_OPTIONS));
            trim(root);
            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    @NonNull
    public static String remove(@Nullable String serialized, @NonNull Uri uri) {
        JSONObject root = parse(serialized);
        root.remove(key(uri));
        return root.toString();
    }

    @NonNull
    private static JSONObject parse(@Nullable String serialized) {
        if (TextUtils.isEmpty(serialized)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(serialized);
        } catch (JSONException | RuntimeException e) {
            return new JSONObject();
        }
    }

    private static void trim(@NonNull JSONObject root) {
        if (root.length() <= MAX_ENTRIES) {
            return;
        }
        JSONArray names = root.names();
        if (names == null) {
            return;
        }
        int removeCount = root.length() - MAX_ENTRIES;
        for (int i = 0; i < removeCount && i < names.length(); ++i) {
            root.remove(names.optString(i));
        }
    }

    @NonNull
    private static String key(@NonNull Uri uri) {
        return uri.normalizeScheme().toString();
    }
}
