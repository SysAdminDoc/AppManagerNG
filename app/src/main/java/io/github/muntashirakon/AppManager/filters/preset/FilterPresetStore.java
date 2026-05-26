// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.preset;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.github.muntashirakon.AppManager.filters.FilterItem;

/**
 * Saved Filter Presets — NF-08 / T8 follow-up.
 *
 * <p>A SharedPreferences-backed JSON-array store of named Finder filter
 * chains. The shape mirrors {@link io.github.muntashirakon.AppManager.tags.AppTagStore}
 * and {@link io.github.muntashirakon.AppManager.profiles.trigger.ProfileTriggerStore}
 * — write-time validation, defensive parse on read, schema-versioning header
 * — so a future Finder UI iteration can ship a "Save filter" action without
 * any further data work.</p>
 *
 * <p>Names are normalised on write (trim, lower-case, restrict to
 * {@code [a-z0-9 _-]} with an alphanumeric first character, 64-char cap) so
 * the resulting JSON is shell-safe and predictable to match. Empty names
 * after normalisation are rejected. {@link #isValidName(CharSequence)} is
 * exposed for UI input validation.</p>
 */
public final class FilterPresetStore {
    private static final String PREFS_NAME = "filter_presets";
    private static final String KEY_VERSION = "_schema";
    private static final String KEY_ALL = "presets";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_NAME_LEN = 64;
    private static final java.util.regex.Pattern VALID_NAME = java.util.regex.Pattern.compile(
            "[a-z0-9][a-z0-9 _-]{0,63}");

    private final SharedPreferences mPrefs;

    public FilterPresetStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!mPrefs.contains(KEY_VERSION)) {
            mPrefs.edit().putInt(KEY_VERSION, SCHEMA_VERSION).apply();
        }
    }

    /** Immutable persisted preset. */
    public static final class Preset {
        @NonNull public final String id;
        @NonNull public final String name;
        @NonNull public final FilterItem filter;
        public final long createdAt;

        @VisibleForTesting
        public Preset(@NonNull String id, @NonNull String name, @NonNull FilterItem filter, long createdAt) {
            this.id = id;
            this.name = name;
            this.filter = filter;
            this.createdAt = createdAt;
        }
    }

    /** Snapshot sorted by name. */
    @AnyThread
    @NonNull
    public List<Preset> all() {
        Map<String, Preset> map = readMap();
        List<Preset> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<Preset>() {
            @Override
            public int compare(Preset a, Preset b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return Collections.unmodifiableList(out);
    }

    @AnyThread
    @Nullable
    public Preset find(@NonNull String presetId) {
        return readMap().get(presetId);
    }

    /**
     * Insert with auto-generated id. The {@code name} is normalised first and
     * collision against existing presets (case-insensitive) drops the call
     * with {@code null}. Returns the persisted preset.
     */
    @AnyThread
    @Nullable
    public Preset save(@NonNull CharSequence name, @NonNull FilterItem filter) {
        String norm = normaliseName(name.toString());
        if (norm == null) return null;
        Map<String, Preset> map = readMap();
        for (Preset existing : map.values()) {
            if (existing.name.equalsIgnoreCase(norm)) return null;
        }
        Preset preset = new Preset(UUID.randomUUID().toString(), norm, filter,
                System.currentTimeMillis());
        map.put(preset.id, preset);
        write(map);
        return preset;
    }

    /** Rename an existing preset; returns the updated preset or null on conflict / missing. */
    @AnyThread
    @Nullable
    public Preset rename(@NonNull String presetId, @NonNull CharSequence newName) {
        String norm = normaliseName(newName.toString());
        if (norm == null) return null;
        Map<String, Preset> map = readMap();
        Preset existing = map.get(presetId);
        if (existing == null) return null;
        for (Preset other : map.values()) {
            if (!other.id.equals(presetId) && other.name.equalsIgnoreCase(norm)) {
                return null;
            }
        }
        Preset renamed = new Preset(existing.id, norm, existing.filter, existing.createdAt);
        map.put(renamed.id, renamed);
        write(map);
        return renamed;
    }

    @AnyThread
    public boolean remove(@NonNull String presetId) {
        Map<String, Preset> map = readMap();
        if (map.remove(presetId) == null) return false;
        write(map);
        return true;
    }

    /** True when at least one preset is persisted. */
    @AnyThread
    public boolean hasAny() {
        return !readMap().isEmpty();
    }

    /** True when {@code candidate} would normalise to a valid preset name. */
    @AnyThread
    public static boolean isValidName(@NonNull CharSequence candidate) {
        return normaliseName(candidate.toString()) != null;
    }

    @VisibleForTesting
    @Nullable
    static String normaliseName(@NonNull String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_NAME_LEN) return null;
        if (!VALID_NAME.matcher(trimmed).matches()) return null;
        return trimmed;
    }

    @VisibleForTesting
    @NonNull
    Map<String, Preset> readMap() {
        String raw = mPrefs.getString(KEY_ALL, null);
        if (raw == null || raw.isEmpty()) return new LinkedHashMap<>();
        LinkedHashMap<String, Preset> out = new LinkedHashMap<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); ++i) {
                JSONObject element = array.optJSONObject(i);
                if (element == null) continue;
                try {
                    String id = element.optString("id", "");
                    String name = normaliseName(element.optString("name", ""));
                    long createdAt = element.optLong("createdAt", System.currentTimeMillis());
                    JSONObject filterJson = element.optJSONObject("filter");
                    if (id.isEmpty() || name == null || filterJson == null) continue;
                    FilterItem filter = new FilterItem(filterJson);
                    out.put(id, new Preset(id, name, filter, createdAt));
                } catch (JSONException ignored) {
                    // Skip malformed individual entries; corrupted prefs do not crash startup.
                }
            }
        } catch (JSONException ignored) {
            // Whole-file corruption: silently reset to empty on next write.
        }
        return out;
    }

    @VisibleForTesting
    void write(@NonNull Map<String, Preset> map) {
        JSONArray array = new JSONArray();
        for (Preset preset : map.values()) {
            try {
                JSONObject element = new JSONObject();
                element.put("id", preset.id);
                element.put("name", preset.name);
                element.put("createdAt", preset.createdAt);
                element.put("filter", preset.filter.serializeToJson());
                array.put(element);
            } catch (JSONException ignored) {
            }
        }
        mPrefs.edit().putString(KEY_ALL, array.toString()).apply();
    }
}
