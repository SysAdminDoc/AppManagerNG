// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.tags;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * NF-08 first-slice: SharedPreferences-backed multi-tag store for installed apps.
 *
 * <p>Each (packageName) maps to a sorted set of lower-cased free-form tags. The
 * data layer is deliberately not in Room yet — Room schema migrations need
 * runtime validation we cannot run from this host, and the working set
 * (~50–200 tagged apps for power users) is well below the threshold where
 * SharedPreferences storage starts to hurt. The shape is API-compatible with a
 * later Room migration: callers receive {@code Set<String>} or query through a
 * predicate, never the underlying JSON.</p>
 *
 * <p>Tags are normalised on write: trimmed, lower-cased, and constrained to
 * the {@code [a-z0-9_-]} character class with an alphanumeric first character.
 * Empty results after normalisation are silently ignored.
 * {@link #isValidTag(CharSequence)} is exposed so UI rows can validate
 * input before calling {@link #addTag}.</p>
 */
public final class AppTagStore {
    private static final String PREFS_NAME = "app_tags";
    private static final String KEY_VERSION = "_schema";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TAG_LEN = 32;
    private static final Pattern VALID_TAG = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    private final SharedPreferences mPrefs;

    public AppTagStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!mPrefs.contains(KEY_VERSION)) {
            mPrefs.edit().putInt(KEY_VERSION, SCHEMA_VERSION).apply();
        }
    }

    /**
     * Return the sorted, immutable set of tags attached to {@code packageName}.
     * Unknown packages return an empty set, never null.
     */
    @AnyThread
    @NonNull
    public Set<String> getTags(@NonNull String packageName) {
        String raw = mPrefs.getString(packageName, null);
        if (raw == null) return Collections.emptySet();
        return parseTags(raw);
    }

    /** True if {@code packageName} currently carries any tag. */
    @AnyThread
    public boolean hasAnyTag(@NonNull String packageName) {
        return mPrefs.contains(packageName);
    }

    /** True if {@code packageName} carries the given normalised tag. */
    @AnyThread
    public boolean hasTag(@NonNull String packageName, @NonNull String tag) {
        String norm = normaliseTag(tag);
        return norm != null && getTags(packageName).contains(norm);
    }

    /**
     * Attach {@code tag} (after normalisation) to {@code packageName}. Returns
     * true when the tag was newly added; false when it was already present,
     * or when {@code tag} fails normalisation.
     */
    @AnyThread
    public boolean addTag(@NonNull String packageName, @NonNull String tag) {
        String norm = normaliseTag(tag);
        if (norm == null) return false;
        Set<String> tags = new TreeSet<>(getTags(packageName));
        if (!tags.add(norm)) return false;
        persist(packageName, tags);
        return true;
    }

    /** Detach a tag. No-op (returns false) if the tag isn't attached. */
    @AnyThread
    public boolean removeTag(@NonNull String packageName, @NonNull String tag) {
        String norm = normaliseTag(tag);
        if (norm == null) return false;
        Set<String> tags = new TreeSet<>(getTags(packageName));
        if (!tags.remove(norm)) return false;
        if (tags.isEmpty()) {
            mPrefs.edit().remove(packageName).apply();
        } else {
            persist(packageName, tags);
        }
        return true;
    }

    /** Remove every tag from {@code packageName}. */
    @AnyThread
    public void clear(@NonNull String packageName) {
        mPrefs.edit().remove(packageName).apply();
    }

    /**
     * Snapshot of every tag in use, sorted alphabetically. Used by the picker
     * UI for auto-complete and by the Finder predicate for chip rendering.
     */
    @AnyThread
    @NonNull
    public Set<String> getAllKnownTags() {
        Set<String> out = new TreeSet<>();
        for (Map.Entry<String, ?> e : mPrefs.getAll().entrySet()) {
            if (KEY_VERSION.equals(e.getKey())) continue;
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            out.addAll(parseTags((String) v));
        }
        return out;
    }

    /** Snapshot of every (package, tags) pair. Defensive copy. */
    @AnyThread
    @NonNull
    public Map<String, Set<String>> snapshot() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : mPrefs.getAll().entrySet()) {
            if (KEY_VERSION.equals(e.getKey())) continue;
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            out.put(e.getKey(), parseTags((String) v));
        }
        return out;
    }

    /** True when {@code candidate} would normalise to a non-empty tag. */
    @AnyThread
    public static boolean isValidTag(@NonNull CharSequence candidate) {
        return normaliseTag(candidate.toString()) != null;
    }

    /**
     * Convenience filter: true when {@code packageName} carries every tag in
     * {@code required}. Empty required-set matches every package.
     */
    @AnyThread
    public boolean hasAllTags(@NonNull String packageName, @NonNull List<String> required) {
        if (required.isEmpty()) return true;
        Set<String> tags = getTags(packageName);
        for (String tag : required) {
            String norm = normaliseTag(tag);
            if (norm == null || !tags.contains(norm)) return false;
        }
        return true;
    }

    /**
     * Convenience filter: true when {@code packageName} carries any tag in
     * {@code anyOf}. Empty list matches no package.
     */
    @AnyThread
    public boolean hasAnyTagIn(@NonNull String packageName, @NonNull List<String> anyOf) {
        Set<String> tags = getTags(packageName);
        for (String tag : anyOf) {
            String norm = normaliseTag(tag);
            if (norm != null && tags.contains(norm)) return true;
        }
        return false;
    }

    /**
     * Normalise a free-form tag: trim, lower-case, reject if empty or longer
     * than {@value #MAX_TAG_LEN} chars, reject if any character falls outside
     * the allowed pattern (alphanumeric, underscore, hyphen). Leading character
     * must be alphanumeric.
     */
    @VisibleForTesting
    @Nullable
    static String normaliseTag(@NonNull String tag) {
        String trimmed = tag.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_TAG_LEN) return null;
        if (!VALID_TAG.matcher(trimmed).matches()) return null;
        return trimmed;
    }

    private void persist(@NonNull String packageName, @NonNull Set<String> tags) {
        JSONArray array = new JSONArray();
        for (String tag : tags) array.put(tag);
        mPrefs.edit().putString(packageName, array.toString()).apply();
    }

    @NonNull
    private static Set<String> parseTags(@NonNull String raw) {
        Set<String> out = new LinkedHashSet<>();
        try {
            if (raw.startsWith("[")) {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); ++i) {
                    String norm = normaliseTag(array.optString(i, ""));
                    if (norm != null) out.add(norm);
                }
            } else if (raw.startsWith("{")) {
                // Defensive: tolerate a future dev-build that stored a JSONObject.
                JSONObject obj = new JSONObject(raw);
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String norm = normaliseTag(it.next());
                    if (norm != null) out.add(norm);
                }
            } else {
                // Comma-joined fallback for any legacy artefact.
                for (String piece : raw.split(",")) {
                    String norm = normaliseTag(piece);
                    if (norm != null) out.add(norm);
                }
            }
        } catch (JSONException ignored) {
        }
        return Collections.unmodifiableSet(new TreeSet<>(out));
    }
}
