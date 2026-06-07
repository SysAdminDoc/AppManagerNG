// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.BackupFlags;

@RunWith(RobolectricTestRunner.class)
public class AppsBaseProfileBackupInfoTest {
    @Test
    public void backupDataTrimsNameAndSanitizesExclusionGlobs() throws Exception {
        AppsProfile profile = AppsProfile.DESERIALIZER.deserialize(profileJson(new JSONObject()
                .put("name", " nightly ")
                .put("flags", BackupFlags.BACKUP_INT_DATA)
                .put("exclusion_globs", new JSONArray()
                        .put(" /cache//tmp ")
                        .put(" #comment ")
                        .put(JSONObject.NULL))));

        assertNotNull(profile.backupData);
        assertEquals("nightly", profile.backupData.name);
        assertArrayEquals(new String[]{"cache/tmp"}, profile.backupData.exclusionGlobs);

        JSONObject backupData = profile.serializeToJson().getJSONObject("backup_data");
        assertEquals("nightly", backupData.getString("name"));
        assertEquals("cache/tmp", backupData.getJSONArray("exclusion_globs").getString(0));
        assertEquals(1, backupData.getJSONArray("exclusion_globs").length());
    }

    @Test
    public void backupDataBlankNameSerializesAsNull() throws Exception {
        AppsProfile profile = AppsProfile.DESERIALIZER.deserialize(profileJson(new JSONObject()
                .put("name", "   ")
                .put("flags", BackupFlags.BACKUP_INT_DATA)));

        assertNotNull(profile.backupData);
        assertNull(profile.backupData.name);
        assertTrue(profile.serializeToJson().getJSONObject("backup_data").isNull("name"));
    }

    @Test
    public void backupDataMalformedFieldsAreIgnored() throws Exception {
        AppsProfile negativeFlags = AppsProfile.DESERIALIZER.deserialize(profileJson(new JSONObject()
                .put("name", "nightly")
                .put("flags", -1)));
        AppsProfile scalarGlobs = AppsProfile.DESERIALIZER.deserialize(profileJson(new JSONObject()
                .put("name", "nightly")
                .put("flags", BackupFlags.BACKUP_INT_DATA)
                .put("exclusion_globs", "cache")));
        AppsProfile nonStringGlobs = AppsProfile.DESERIALIZER.deserialize(profileJson(new JSONObject()
                .put("name", "nightly")
                .put("flags", BackupFlags.BACKUP_INT_DATA)
                .put("exclusion_globs", new JSONArray().put(false))));

        assertNull(negativeFlags.backupData);
        assertNull(scalarGlobs.backupData);
        assertNull(nonStringGlobs.backupData);
    }

    private static JSONObject profileJson(JSONObject backupData) throws Exception {
        return new JSONObject()
                .put("id", "profile")
                .put("name", "Profile")
                .put("type", BaseProfile.PROFILE_TYPE_APPS)
                .put("state", BaseProfile.STATE_ON)
                .put("version", 1)
                .put("packages", new JSONArray().put("dnsfilter.android"))
                .put("backup_data", backupData);
    }
}
