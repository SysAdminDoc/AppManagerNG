// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.importers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import io.github.muntashirakon.AppManager.profiles.struct.AppsFilterProfile;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;

/**
 * Emits an upstream-AppManager-compatible subset of a NG {@link BaseProfile} JSON.
 *
 * <p>Anti-lock-in stance: users who try NG and later want to fall back to the
 * upstream <a href="https://github.com/MuntashirAkon/AppManager">App Manager</a>
 * should be able to take their profiles with them.
 *
 * <p>Strategy: NG was bootstrapped from upstream commit
 * <code>3d11bcb</code> (post-v4.0.5) and inherited the full upstream profile
 * schema verbatim. Two cases diverge:
 *
 * <ul>
 *   <li><b>NG-only profile types</b>: {@link BaseProfile#PROFILE_TYPE_APPS_FILTER}
 *       is unknown upstream. The exporter refuses these and surfaces a typed
 *       failure so the caller can show an explainer rather than silently
 *       writing a half-broken file.</li>
 *   <li><b>NG-only top-level fields</b>: if any new field is ever added to
 *       NG's profile JSON, this exporter filters it out by walking the
 *       serialised JSON against an explicit allowlist of upstream-known keys.
 *       Future additions therefore <em>opt in</em> to compat export by being
 *       added to {@link #UPSTREAM_KNOWN_KEYS} after upstream-compat verification,
 *       rather than leaking out automatically.</li>
 * </ul>
 *
 * <p>The output is pure JSON — no marker fields, no schema-version header —
 * because upstream AM would treat them as unknown and either ignore or reject
 * (depending on its release line).
 */
public final class UpstreamCompatProfileExporter {

    /**
     * Allowlist of profile JSON keys that exist in upstream App Manager as of
     * the NG bootstrap point. Anything outside this set is dropped on
     * compat-export. Sourced from upstream's
     * {@code BaseProfile} / {@code AppsBaseProfile} / {@code AppsProfile}
     * serialise paths at the NG bootstrap commit.
     *
     * <p>If NG ever introduces a new field that <em>is</em> upstream-compatible
     * (e.g. because upstream later adopted it), add the key here so the
     * compat-export propagates it.
     */
    @VisibleForTesting
    static final Set<String> UPSTREAM_KNOWN_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // BaseProfile
            "id", "name", "type", "state",
            // AppsBaseProfile
            "version", "allow_routine", "comment", "users",
            "components", "app_ops", "permissions",
            "backup_data", "export_rules", "misc",
            // AppsProfile
            "packages")));

    private UpstreamCompatProfileExporter() {
    }

    /**
     * Build an upstream-AM-compatible JSON string for {@code profile}. Throws
     * if the profile uses an NG-only type.
     */
    @NonNull
    public static String toJsonString(@NonNull BaseProfile profile) throws JSONException, UpstreamCompatExportException {
        return toFilteredJson(profile).toString(2);
    }

    @VisibleForTesting
    @NonNull
    static JSONObject toFilteredJson(@NonNull BaseProfile profile)
            throws JSONException, UpstreamCompatExportException {
        if (profile instanceof AppsFilterProfile) {
            throw new UpstreamCompatExportException(
                    "Filter-based profiles are AppManagerNG-only and cannot be exported in upstream-compatible form. "
                            + "Export a regular Apps profile instead, or use the standard Export action.");
        }
        if (!(profile instanceof AppsProfile)) {
            throw new UpstreamCompatExportException(
                    "Unknown profile type for upstream-compat export: " + profile.getClass().getSimpleName());
        }
        return filter(profile.serializeToJson());
    }

    @VisibleForTesting
    @NonNull
    static JSONObject filter(@NonNull JSONObject src) throws JSONException {
        JSONObject out = new JSONObject();
        Iterator<String> keys = src.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!UPSTREAM_KNOWN_KEYS.contains(key)) {
                // Drop NG-only fields silently — the compat receiver would not know
                // what to do with them and they would either be ignored or rejected
                // depending on the upstream release line.
                continue;
            }
            Object value = src.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            out.put(key, value);
        }
        return out;
    }

    @Nullable
    public static String describeIfUnsupported(@NonNull BaseProfile profile) {
        if (profile instanceof AppsFilterProfile) {
            return "Filter profile";
        }
        if (!(profile instanceof AppsProfile)) {
            return profile.getClass().getSimpleName();
        }
        return null;
    }

    /** Thrown when a profile cannot be expressed in upstream-AM's schema. */
    public static class UpstreamCompatExportException extends Exception {
        public UpstreamCompatExportException(@NonNull String message) {
            super(message);
        }
    }
}
