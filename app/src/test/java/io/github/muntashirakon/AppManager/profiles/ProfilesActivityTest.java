// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;

public class ProfilesActivityTest {
    @Test
    public void formatProfileMetadataLabelNormalizesControlTextAndDefusesFormula() {
        assertEquals("' =Profile Name", ProfilesActivity.formatProfileMetadataLabel("\t=Profile\nName"));
        assertEquals("profile", ProfilesActivity.formatProfileMetadataLabel("\n\t"));
    }

    @Test
    public void buildProfileShareFilenameUsesSanitizedProfileName() {
        String filename = ProfilesActivity.buildProfileShareFilename("\t=Profile/Name\n",
                ProfileManager.PROFILE_EXT);

        assertTrue(filename.endsWith(ProfileManager.PROFILE_EXT));
        assertFalse(filename.contains("\t"));
        assertFalse(filename.contains("\n"));
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("null"));
    }

    @Test
    public void writeProfileExportWritesStandardProfileJson() throws Exception {
        BaseProfile profile = createAppsProfileWithNgOnlyField();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        ProfilesActivity.writeProfileExport(os, profile, false);

        JSONObject exported = new JSONObject(os.toString(StandardCharsets.UTF_8.name()));
        assertEquals("profile-id", exported.getString("id"));
        assertEquals("Profile", exported.getString("name"));
        assertEquals(1, exported.getJSONArray("packages").length());
        assertTrue(exported.has("ng_only"));
    }

    @Test
    public void writeProfileExportFiltersUpstreamCompatibleJson() throws Exception {
        BaseProfile profile = createAppsProfileWithNgOnlyField();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        ProfilesActivity.writeProfileExport(os, profile, true);

        JSONObject exported = new JSONObject(os.toString(StandardCharsets.UTF_8.name()));
        assertEquals("profile-id", exported.getString("id"));
        assertEquals("Profile", exported.getString("name"));
        assertEquals(1, exported.getJSONArray("packages").length());
        assertFalse(exported.has("ng_only"));
    }

    private static BaseProfile createAppsProfileWithNgOnlyField() throws Exception {
        return new NgOnlyAppsProfile();
    }

    private static final class NgOnlyAppsProfile extends AppsProfile {
        NgOnlyAppsProfile() {
            super("profile-id", "Profile");
            packages = new String[]{"com.example.app"};
        }

        @androidx.annotation.NonNull
        @Override
        public JSONObject serializeToJson() throws org.json.JSONException {
            return super.serializeToJson().put("ng_only", true);
        }
    }
}
