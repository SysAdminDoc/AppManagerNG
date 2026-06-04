// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ProfileApplierReceiverTest {
    @Test
    public void automationIntentTargetsProfileApplierReceiver() {
        Context context = ApplicationProvider.getApplicationContext();

        Intent intent = ProfileApplierReceiver.getAutomationIntent(context, "Nightly Profile", "on");

        assertNotNull(intent.getComponent());
        assertEquals(ProfileApplierReceiver.class.getName(), intent.getComponent().getClassName());
        assertEquals("Nightly_Profile", intent.getStringExtra(ProfileApplierActivity.EXTRA_PROFILE_ID));
        assertEquals("on", intent.getStringExtra(ProfileApplierActivity.EXTRA_STATE));
    }

    @Test
    public void runtimePackageOverridePreservesOtherProfileOverrides() throws Exception {
        JSONObject existing = new JSONObject()
                .put("backup_data", new JSONObject().put("name", "nightly"));

        JSONObject overrides = ProfileApplierReceiver.mergeRuntimePackageOverride(
                existing, "com.example.dynamic");

        assertNotNull(overrides);
        assertEquals("com.example.dynamic", overrides.getJSONArray("packages").getString(0));
        assertEquals("nightly", overrides.getJSONObject("backup_data").getString("name"));
    }

    @Test
    public void runtimePackageOverrideRejectsInvalidPackageName() {
        assertThrows(IllegalArgumentException.class, () ->
                ProfileApplierReceiver.mergeRuntimePackageOverride(null, "not a package"));
    }
}
