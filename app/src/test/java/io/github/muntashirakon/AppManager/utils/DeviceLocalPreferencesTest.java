// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DeviceLocalPreferencesTest {
    private Context context;
    private SharedPreferences portable;
    private SharedPreferences deviceLocal;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        portable = context.getSharedPreferences(AppPref.getSharedPreferencesName(), Context.MODE_PRIVATE);
        deviceLocal = context.getSharedPreferences(AppPref.DEVICE_LOCAL_PREF_NAME, Context.MODE_PRIVATE);
        clear();
    }

    @After
    public void tearDown() {
        clear();
    }

    @Test
    public void legacySecretsAreDiscardedAndStoredOnlyDeviceLocally() {
        portable.edit()
                .putString("authorization_key", "legacy-authorization")
                .putString("tasker_plugin_signing_secret", "legacy-tasker")
                .putString("virus_total_api_key", "legacy-virus-total")
                .commit();

        AppPref appPref = AppPref.getNewInstance(context);

        assertFalse(portable.contains("authorization_key"));
        assertFalse(portable.contains("tasker_plugin_signing_secret"));
        assertFalse(portable.contains("virus_total_api_key"));
        assertNotEquals("legacy-authorization",
                appPref.getValue(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR));
        assertEquals("", appPref.getValue(AppPref.PrefKey.PREF_TASKER_PLUGIN_SIGNING_SECRET_STR));
        assertEquals("", appPref.getValue(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR));
        assertTrue(deviceLocal.contains("authorization_key"));
        assertTrue(portable.getBoolean("device_local_secrets_reset_notice", false));
    }

    @Test
    public void restoredPortableBindingRotatesDeviceLocalSecrets() {
        portable.edit().putString("device_local_binding_id", "restored-device").commit();
        deviceLocal.edit()
                .putString("device_local_binding_id", "old-device")
                .putString("authorization_key", "old-device-secret")
                .commit();

        AppPref appPref = AppPref.getNewInstance(context);

        assertNotEquals("old-device-secret",
                appPref.getValue(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR));
        assertEquals(portable.getString("device_local_binding_id", null),
                deviceLocal.getString("device_local_binding_id", null));
        assertTrue(portable.getBoolean("device_local_secrets_reset_notice", false));
    }

    @Test
    public void freshInstallCreatesBindingWithoutWarning() {
        AppPref.getNewInstance(context);

        assertEquals(portable.getString("device_local_binding_id", null),
                deviceLocal.getString("device_local_binding_id", null));
        assertFalse(portable.getBoolean("device_local_secrets_reset_notice", false));
    }

    private void clear() {
        portable.edit().clear().commit();
        deviceLocal.edit().clear().commit();
    }

}
