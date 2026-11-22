// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

/** Issue 48: keys that can lock someone out or cut the ADB link get a warning chip and a note. */
public class RiskyKeysTest {
    @Test
    public void everyListedKeyIsMarkedWithANote() {
        String[] risky = {"device_provisioned", "user_setup_complete", "adb_enabled", "development_settings_enabled",
                "airplane_mode_on", "package_verifier_enable", "hidden_api_policy", "hidden_api_policy_p_apps",
                "hidden_api_policy_pre_p_apps", "lock_screen_owner_info", "lock_screen_lock_after_timeout",
                "bluetooth_on", "wifi_on"};
        for (String key : risky) {
            assertTrue(key, RiskyKeys.isRisky(key));
            assertNotEquals(key, 0, RiskyKeys.getNote(key));
        }
    }

    @Test
    public void eachKindOfRiskHasItsOwnNote() {
        assertEquals(R.string.settings_editor_risk_setup, RiskyKeys.getNote("user_setup_complete"));
        assertEquals(R.string.settings_editor_risk_adb, RiskyKeys.getNote("adb_enabled"));
        assertEquals(R.string.settings_editor_risk_radio, RiskyKeys.getNote("wifi_on"));
        assertEquals(R.string.settings_editor_risk_verifier, RiskyKeys.getNote("package_verifier_enable"));
        assertEquals(R.string.settings_editor_risk_hidden_api, RiskyKeys.getNote("hidden_api_policy_p_apps"));
        assertEquals(R.string.settings_editor_risk_lock_screen, RiskyKeys.getNote("lock_screen_show_notifications"));
    }

    @Test
    public void ordinaryKeysAreNotMarked() {
        for (String key : new String[]{"screen_brightness", "adb_enabled_extra", "wifi_on_off", "my_lock_screen",
                "Adb_enabled", "bluetooth_name", "hidden_api", ""}) {
            assertFalse(key, RiskyKeys.isRisky(key));
        }
    }
}
