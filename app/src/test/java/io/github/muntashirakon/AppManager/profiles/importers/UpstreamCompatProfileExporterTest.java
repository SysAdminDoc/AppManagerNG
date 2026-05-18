// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.importers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Iterator;

public class UpstreamCompatProfileExporterTest {

    @Test
    public void filterDropsNgOnlyKeys() throws JSONException {
        JSONObject src = new JSONObject()
                .put("id", "uuid")
                .put("name", "Profile")
                .put("type", 0)
                .put("state", "on")
                .put("packages", new org.json.JSONArray().put("com.foo.bar"))
                // NG-only synthetic fields that must not leak upstream
                .put("ng_routine_quota_hint", 9000)
                .put("_ng_synthetic_marker", "yes");
        JSONObject filtered = UpstreamCompatProfileExporter.filter(src);
        assertTrue(filtered.has("id"));
        assertTrue(filtered.has("name"));
        assertTrue(filtered.has("type"));
        assertTrue(filtered.has("state"));
        assertTrue(filtered.has("packages"));
        assertFalse("NG-only key must be dropped from upstream-compat export",
                filtered.has("ng_routine_quota_hint"));
        assertFalse(filtered.has("_ng_synthetic_marker"));
    }

    @Test
    public void filterDropsNullsButPreservesEmptyArrays() throws JSONException {
        JSONObject src = new JSONObject()
                .put("id", "uuid")
                .put("name", "Profile")
                .put("type", 0)
                .put("state", "on")
                .put("packages", new org.json.JSONArray())
                .put("comment", JSONObject.NULL)
                .put("components", JSONObject.NULL);
        JSONObject filtered = UpstreamCompatProfileExporter.filter(src);
        assertFalse(filtered.has("comment"));
        assertFalse(filtered.has("components"));
        assertTrue(filtered.has("packages"));
        assertEquals(0, filtered.getJSONArray("packages").length());
    }

    @Test
    public void allowlistEnumeratesEveryExpectedKey() {
        // Locks the allowlist down so an unintended addition needs an explicit
        // upstream-compat-verification step.
        String[] expected = {
                "id", "name", "type", "state",
                "version", "allow_routine", "comment", "users",
                "components", "app_ops", "permissions",
                "backup_data", "export_rules", "misc",
                "packages"
        };
        assertEquals(expected.length, UpstreamCompatProfileExporter.UPSTREAM_KNOWN_KEYS.size());
        for (String k : expected) {
            assertTrue("Expected " + k + " in upstream allowlist",
                    UpstreamCompatProfileExporter.UPSTREAM_KNOWN_KEYS.contains(k));
        }
    }

    @Test
    public void unknownKeysIteratorDoesNotEscape() throws JSONException {
        JSONObject src = new JSONObject()
                .put("id", "uuid")
                .put("name", "Profile")
                .put("type", 0);
        JSONObject filtered = UpstreamCompatProfileExporter.filter(src);
        // Belt-and-braces: walk the keys, ensure nothing outside the allowlist appears.
        for (Iterator<String> it = filtered.keys(); it.hasNext(); ) {
            String k = it.next();
            assertTrue("Unexpected key leaked: " + k,
                    UpstreamCompatProfileExporter.UPSTREAM_KNOWN_KEYS.contains(k));
        }
    }

    @Test
    public void unsupportedProfileTypeProducesTypedFailure() {
        FakeNgOnlyProfile p = new FakeNgOnlyProfile();
        try {
            UpstreamCompatProfileExporter.toFilteredJson(p);
            fail("Expected UpstreamCompatExportException for non-AppsProfile");
        } catch (UpstreamCompatProfileExporter.UpstreamCompatExportException expected) {
            assertTrue(expected.getMessage().contains("AppManagerNG-only")
                    || expected.getMessage().contains("Unknown profile type"));
        } catch (JSONException unexpected) {
            fail("Should not have thrown JSONException: " + unexpected);
        }
    }

    /** Fake stand-in to exercise the rejection path without dragging in Android-only deps. */
    private static final class FakeNgOnlyProfile
            extends io.github.muntashirakon.AppManager.profiles.struct.BaseProfile {
        FakeNgOnlyProfile() {
            super("fake-uuid", "Fake", PROFILE_TYPE_APPS_FILTER);
        }

        @Override
        public io.github.muntashirakon.AppManager.profiles.struct.ProfileApplierResult apply(
                @androidx.annotation.NonNull String state,
                @androidx.annotation.Nullable io.github.muntashirakon.AppManager.profiles.ProfileLogger logger,
                @androidx.annotation.Nullable io.github.muntashirakon.AppManager.progress.ProgressHandler progressHandler) {
            throw new UnsupportedOperationException();
        }

        @androidx.annotation.NonNull
        @Override
        public CharSequence toLocalizedString(@androidx.annotation.NonNull android.content.Context context) {
            return "";
        }
    }
}
