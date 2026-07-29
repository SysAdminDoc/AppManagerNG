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
    /** The last document that parsed as a whole, kept so a corrupt one can never be the only copy. */
    @VisibleForTesting
    static final String KEY_LAST_GOOD = "triggers_last_good";
    /** Verbatim copy of a document that failed to parse, retained for export/inspection. */
    @VisibleForTesting
    static final String KEY_QUARANTINE = "triggers_corrupt";
    @VisibleForTesting
    static final String KEY_QUARANTINE_AT = "triggers_corrupt_at";
    private static final int SCHEMA_VERSION = 1;

    /**
     * Guards the read-modify-write sequence in every mutator. The class is
     * {@code @AnyThread} and is invoked concurrently — the {@link RoutineWorker}
     * background thread calls {@link #setEnabled} while the UI thread adds /
     * removes / toggles triggers. Each instance wraps the same backing
     * {@code SharedPreferences}, so the lock must be process-wide (static) for
     * the whole-list serialise-back in {@link #write} not to clobber a
     * concurrent writer's change (lost update).
     */
    private static final Object LOCK = new Object();

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
        synchronized (LOCK) {
            Map<String, ProfileTrigger> map = readMap();
            map.put(trigger.id, trigger);
            write(map);
        }
    }

    /** Remove by id; false when the id wasn't present. */
    @AnyThread
    public boolean remove(@NonNull String triggerId) {
        synchronized (LOCK) {
            Map<String, ProfileTrigger> map = readMap();
            if (map.remove(triggerId) == null) return false;
            write(map);
            return true;
        }
    }

    /** Remove every trigger attached to {@code profileId}. */
    @AnyThread
    public int removeForProfile(@NonNull String profileId) {
        synchronized (LOCK) {
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
    }

    /** Flip the enabled bit on a stored trigger; returns the new state or null when missing. */
    @AnyThread
    @Nullable
    public Boolean toggleEnabled(@NonNull String triggerId) {
        synchronized (LOCK) {
            Map<String, ProfileTrigger> map = readMap();
            ProfileTrigger existing = map.get(triggerId);
            if (existing == null) return null;
            ProfileTrigger flipped = existing.withEnabled(!existing.enabled);
            map.put(flipped.id, flipped);
            write(map);
            return flipped.enabled;
        }
    }

    /** Set the enabled bit on a stored trigger; returns the updated trigger or null when missing. */
    @AnyThread
    @Nullable
    public ProfileTrigger setEnabled(@NonNull String triggerId, boolean enabled) {
        synchronized (LOCK) {
            Map<String, ProfileTrigger> map = readMap();
            ProfileTrigger existing = map.get(triggerId);
            if (existing == null) return null;
            ProfileTrigger updated = existing.withEnabled(enabled);
            map.put(updated.id, updated);
            write(map);
            return updated;
        }
    }

    /** True when at least one stored trigger is enabled. */
    @AnyThread
    public boolean hasAnyEnabled() {
        for (ProfileTrigger trigger : all()) {
            if (trigger.enabled) return true;
        }
        return false;
    }

    /**
     * Reports whether stored automation state needed recovery, so the UI can warn instead of
     * silently presenting an empty trigger list.
     */
    @AnyThread
    @NonNull
    public Health inspect() {
        synchronized (LOCK) {
            String raw = mPrefs.getString(KEY_ALL, null);
            ParseResult result = parse(raw);
            boolean restored = false;
            if (result.documentCorrupt) {
                ParseResult lastGood = parse(mPrefs.getString(KEY_LAST_GOOD, null));
                restored = !lastGood.documentCorrupt && !lastGood.map.isEmpty();
            }
            return new Health(result.documentCorrupt, result.droppedEntries, restored,
                    mPrefs.getString(KEY_QUARANTINE, null),
                    mPrefs.getLong(KEY_QUARANTINE_AT, 0L));
        }
    }

    /**
     * @return the retained copy of the document that failed to parse, for export.
     */
    @AnyThread
    @Nullable
    public String getQuarantinedDocument() {
        return mPrefs.getString(KEY_QUARANTINE, null);
    }

    /**
     * Drops the quarantined copy once the user has exported or dismissed it. Automation state is
     * untouched.
     */
    @AnyThread
    public void clearQuarantine() {
        synchronized (LOCK) {
            mPrefs.edit().remove(KEY_QUARANTINE).remove(KEY_QUARANTINE_AT).apply();
        }
    }

    /**
     * Discards every stored trigger along with the recovery copies. Only for the explicit
     * "reset automation" action — nothing calls this as a side effect of a parse failure.
     */
    @AnyThread
    public void reset() {
        synchronized (LOCK) {
            mPrefs.edit()
                    .remove(KEY_ALL)
                    .remove(KEY_LAST_GOOD)
                    .remove(KEY_QUARANTINE)
                    .remove(KEY_QUARANTINE_AT)
                    .apply();
        }
    }

    @VisibleForTesting
    @NonNull
    Map<String, ProfileTrigger> readMap() {
        synchronized (LOCK) {
            String raw = mPrefs.getString(KEY_ALL, null);
            ParseResult result = parse(raw);
            if (!result.documentCorrupt) {
                // Individually malformed entries are dropped; the valid siblings survive.
                return result.map;
            }
            // A document that does not parse at all must never be silently replaced by an empty
            // list: keep a verbatim copy and fall back to the last document that did parse.
            quarantine(raw);
            ParseResult lastGood = parse(mPrefs.getString(KEY_LAST_GOOD, null));
            return lastGood.documentCorrupt ? new LinkedHashMap<>() : lastGood.map;
        }
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
        synchronized (LOCK) {
            String previous = mPrefs.getString(KEY_ALL, null);
            SharedPreferences.Editor editor = mPrefs.edit();
            if (previous != null && !previous.isEmpty() && !parse(previous).documentCorrupt) {
                // Promote the document being replaced: it is the last one known to parse.
                editor.putString(KEY_LAST_GOOD, previous);
            }
            editor.putString(KEY_ALL, array.toString()).apply();
        }
    }

    private void quarantine(@Nullable String raw) {
        if (raw == null || raw.isEmpty() || raw.equals(mPrefs.getString(KEY_QUARANTINE, null))) {
            return;
        }
        mPrefs.edit()
                .putString(KEY_QUARANTINE, raw)
                .putLong(KEY_QUARANTINE_AT, System.currentTimeMillis())
                .apply();
    }

    @NonNull
    private static ParseResult parse(@Nullable String raw) {
        LinkedHashMap<String, ProfileTrigger> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return new ParseResult(out, false, 0);
        }
        int dropped = 0;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); ++i) {
                JSONObject element = array.optJSONObject(i);
                if (element == null) {
                    ++dropped;
                    continue;
                }
                try {
                    ProfileTrigger trigger = ProfileTrigger.fromJson(element);
                    out.put(trigger.id, trigger);
                } catch (JSONException ignored) {
                    // Malformed individual entries are skipped, not fatal.
                    ++dropped;
                }
            }
        } catch (JSONException e) {
            return new ParseResult(new LinkedHashMap<>(), true, 0);
        }
        return new ParseResult(out, false, dropped);
    }

    private static final class ParseResult {
        @NonNull
        final LinkedHashMap<String, ProfileTrigger> map;
        final boolean documentCorrupt;
        final int droppedEntries;

        ParseResult(@NonNull LinkedHashMap<String, ProfileTrigger> map, boolean documentCorrupt,
                    int droppedEntries) {
            this.map = map;
            this.documentCorrupt = documentCorrupt;
            this.droppedEntries = droppedEntries;
        }
    }

    /** Recovery state of the on-disk automation document. */
    public static final class Health {
        public final boolean documentCorrupt;
        public final int droppedEntries;
        public final boolean restoredFromLastGood;
        @Nullable
        public final String quarantinedDocument;
        public final long quarantinedAt;

        Health(boolean documentCorrupt, int droppedEntries, boolean restoredFromLastGood,
               @Nullable String quarantinedDocument, long quarantinedAt) {
            this.documentCorrupt = documentCorrupt;
            this.droppedEntries = droppedEntries;
            this.restoredFromLastGood = restoredFromLastGood;
            this.quarantinedDocument = quarantinedDocument;
            this.quarantinedAt = quarantinedAt;
        }

        public boolean needsAttention() {
            return documentCorrupt || droppedEntries > 0 || quarantinedDocument != null;
        }
    }
}
