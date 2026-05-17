// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;

public class QuickFreezeTileControllerTest {
    @Test
    public void isProfileEligibleRequiresFreezeConfiguration() throws Exception {
        assertFalse(QuickFreezeTileController.isProfileEligible(null));
        assertFalse(QuickFreezeTileController.isProfileEligible(appsProfile(false)));
        assertTrue(QuickFreezeTileController.isProfileEligible(appsProfile(true)));
    }

    private static BaseProfile appsProfile(boolean freeze) throws Exception {
        JSONObject profile = new JSONObject()
                .put("id", freeze ? "freeze-profile" : "regular-profile")
                .put("name", freeze ? "Freeze profile" : "Regular profile")
                .put("type", BaseProfile.PROFILE_TYPE_APPS)
                .put("state", BaseProfile.STATE_ON)
                .put("version", 1)
                .put("packages", new JSONArray().put("com.example.app"));
        if (freeze) {
            profile.put("misc", new JSONArray().put("freeze"));
        }
        return BaseProfile.DESERIALIZER.deserialize(profile);
    }
}
