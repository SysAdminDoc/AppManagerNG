// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AppsBaseProfilePrivilegeTest {
    @Test
    public void getUnsupportedOperationsNamesEveryUnavailablePrivilegedAction() throws Exception {
        AppsProfile profile = AppsProfile.DESERIALIZER.deserialize(profileJson()
                .put("components", new JSONArray().put("activity:com.example/.MainActivity"))
                .put("app_ops", new JSONArray().put(24))
                .put("permissions", new JSONArray().put("android.permission.CAMERA"))
                .put("misc", new JSONArray()
                        .put("freeze")
                        .put("force_stop")
                        .put("clear_cache")
                        .put("clear_data")
                        .put("block_trackers")));

        List<Integer> unsupported = profile.getUnsupportedOperations(noPrivileges());

        assertEquals(Arrays.asList(
                AppsBaseProfile.PROFILE_OP_COMPONENTS,
                AppsBaseProfile.PROFILE_OP_APP_OPS,
                AppsBaseProfile.PROFILE_OP_PERMISSIONS,
                AppsBaseProfile.PROFILE_OP_FREEZE,
                AppsBaseProfile.PROFILE_OP_FORCE_STOP,
                AppsBaseProfile.PROFILE_OP_CLEAR_CACHE,
                AppsBaseProfile.PROFILE_OP_CLEAR_DATA,
                AppsBaseProfile.PROFILE_OP_TRACKERS
        ), unsupported);
    }

    @Test
    public void getUnsupportedOperationsAllowsSupportedActions() throws Exception {
        AppsProfile profile = AppsProfile.DESERIALIZER.deserialize(profileJson()
                .put("permissions", new JSONArray().put("android.permission.CAMERA"))
                .put("misc", new JSONArray().put("freeze").put("force_stop")));

        List<Integer> unsupported = profile.getUnsupportedOperations(new AppsBaseProfile.PrivilegeCapabilities(
                true,
                true,
                false,
                false,
                true,
                true,
                true));

        assertEquals(Arrays.asList(
                AppsBaseProfile.PROFILE_OP_PERMISSIONS,
                AppsBaseProfile.PROFILE_OP_FREEZE
        ), unsupported);
    }

    @Test
    public void getUnsupportedOperationsReturnsEmptyWhenAllRequestedActionsAreAvailable() throws Exception {
        AppsProfile profile = AppsProfile.DESERIALIZER.deserialize(profileJson()
                .put("components", new JSONArray().put("receiver:com.example/.BootReceiver"))
                .put("app_ops", new JSONArray().put(24))
                .put("permissions", new JSONArray().put("android.permission.POST_NOTIFICATIONS"))
                .put("misc", new JSONArray().put("block_trackers")));

        assertTrue(profile.getUnsupportedOperations(allPrivileges()).isEmpty());
    }

    private static AppsBaseProfile.PrivilegeCapabilities noPrivileges() {
        return new AppsBaseProfile.PrivilegeCapabilities(false, false, false, false, false, false, false);
    }

    private static AppsBaseProfile.PrivilegeCapabilities allPrivileges() {
        return new AppsBaseProfile.PrivilegeCapabilities(true, true, true, true, true, true, true);
    }

    private static JSONObject profileJson() throws Exception {
        return new JSONObject()
                .put("id", "profile")
                .put("name", "Profile")
                .put("type", BaseProfile.PROFILE_TYPE_APPS)
                .put("state", BaseProfile.STATE_ON)
                .put("version", 1)
                .put("packages", new JSONArray().put("com.example"));
    }
}
