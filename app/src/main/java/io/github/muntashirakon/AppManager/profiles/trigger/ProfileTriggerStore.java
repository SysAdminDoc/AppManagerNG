// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

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
import java.util.Map;

/**
 * NF-09 data layer: SharedPreferences-backed JSON-list store of
 * {@link ProfileTrigger} records.
 *
 * <p>The shape mirrors {@code AppTagStore} from NF-08 — write-time
 * validation, defensive parse on read, schema versioning header — so a future
 * Room migration has a stable shape to copy from. The data layer is fully
 * usable today: callers can persist, list, and toggle triggers without the
 * executor existing yet.</p>
 */
public final class ProfileTriggerStore {
    private static final String PREFS_NAME = "profile_triggers";
    private static final String KEY_VERSION = "_schema";
    private static final String KEY_ALL = "triggers";
    private static final int SCHEMA_VERSION = 1;

    private final SharedPreferences mPrefs;

    public ProfileTriggerStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!mPrefs.contains(KEY_VERSION)) {
            mPrefs.edit().putInt(KEY_VERSION, SCHEMA_VERSION).apply();
        }
    }

    /** Persisted snapshot. Order: oldest first. */
    @AnyThread
    @NonNull
    public List<ProfileTrigger> all() {
        Map<String, ProfileTrigger> map = readMap();
        List<ProfileTrigger> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<ProfileTrigger>() {
            @Override
            public int compare(ProfileTrigger a, ProfileTrigger b) {
                int byCreated = Long.compare(a.createdAt, b.createdAt);
                return byCreated != 0 ? byCreated : a.id.compareTo(b.id);
            }
        });
        return Collections.unmodifiableList(out);
    }

    /** Look up a trigger by id. {@code null} when not present. */
    @AnyThread
    @Nullable
    public ProfileTrigger find(@NonNull String triggerId) {
        return readMap().get(triggerId);
    }

    /** Snapshot of triggers attached to a single profile id. */
    @AnyThread
    @NonNull
    public List<ProfileTrigger> forProfile(@NonNull String profileId) {
        List<ProfileTrigger> out = new ArrayList<>();
        for (ProfileTrigger trigger : all()) {
            if (profileId.equals(trigger.profileId)) out.add(trigger);
        }
        return out;
    }

    /** Insert or replace. Triggers are looked up by their id. */
    @AnyThread
    public void put(@NonNull ProfileTrigger trigger) {
        Map<String, ProfileTrigger> map = readMap();
        map.put(trigger.id, trigger);
        write(map);
    }

    /** Remove by id; false when the id wasn't present. */
    @AnyThread
    public boolean remove(@NonNull String triggerId) {
        Map<String, ProfileTrigger> map = readMap();
        if (map.remove(triggerId) == null) return false;
        write(map);
        return true;
    }

    /** Remove every trigger attached to {@code profileId}. */
    @AnyThread
    public int removeForProfile(@NonNull String profileId) {
        Map<String, ProfileTrigger> map = readMap();
        int removed = 0;
        java.util.Iterator<Map.Entry<String, ProfileTrigger>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ProfileTrigger> entry = it.next();
            if (profileId.equals(entry.getValue().profileId)) {
                it.remove();
                ++removed;
            }
        }
        if (removed > 0) write(map);
        return removed;
    }

    /** Flip the enabled bit on a stored trigger; returns the new state or null when missing. */
    @AnyThread
    @Nullable
    public Boolean toggleEnabled(@NonNull String triggerId) {
        Map<String, ProfileTrigger> map = readMap();
        ProfileTrigger existing = map.get(triggerId);
        if (existing == null) return null;
        ProfileTrigger flipped = existing.withEnabled(!existing.enabled);
        map.put(flipped.id, flipped);
        write(map);
        return flipped.enabled;
    }

    /** True when at least one stored trigger is enabled. */
    @AnyThread
    public boolean hasAnyEnabled() {
        for (ProfileTrigger trigger : all()) {
            if (trigger.enabled) return true;
        }
        return false;
    }

    @VisibleForTesting
    @NonNull
    Map<String, ProfileTrigger> readMap() {
        String raw = mPrefs.getString(KEY_ALL, null);
        if (raw == null || raw.isEmpty()) return new LinkedHashMap<>();
        LinkedHashMap<String, ProfileTrigger> out = new LinkedHashMap<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); ++i) {
                JSONObject element = array.optJSONObject(i);
                if (element == null) continue;
                try {
                    ProfileTrigger trigger = ProfileTrigger.fromJson(element);
                    out.put(trigger.id, trigger);
                } catch (JSONException ignored) {
                    // Malformed individual entries are skipped, not fatal.
                }
            }
        } catch (JSONException ignored) {
            // Corrupted JSON wipes back to an empty list rather than throwing.
        }
        return out;
    }

    @VisibleForTesting
    void write(@NonNull Map<String, ProfileTrigger> map) {
        JSONArray array = new JSONArray();
        for (ProfileTrigger trigger : map.values()) {
            try {
                array.put(trigger.toJson());
            } catch (JSONException ignored) {
                // Should not happen with builder-validated data; skip on the off chance.
            }
        }
        mPrefs.edit().putString(KEY_ALL, array.toString()).apply();
    }
}
