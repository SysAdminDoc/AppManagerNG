// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class InstallerOptionsTest {
    @Test
    public void parcelRoundTripKeepsForcedAdbInstallFlag() throws Exception {
        InstallerOptions options = new InstallerOptions(optionsJson(true));

        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            InstallerOptions restored = InstallerOptions.CREATOR.createFromParcel(parcel);

            assertTrue(restored.isForceAdbInstall());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void jsonRoundTripKeepsForcedAdbInstallFlag() throws Exception {
        InstallerOptions options = new InstallerOptions(optionsJson(true));

        InstallerOptions restored = InstallerOptions.DESERIALIZER.deserialize(options.serializeToJson());

        assertTrue(restored.isForceAdbInstall());
    }

    @Test
    public void legacyJsonDefaultsForcedAdbInstallToFalse() throws Exception {
        JSONObject jsonObject = optionsJson(false);
        jsonObject.remove("force_adb_install");

        InstallerOptions restored = InstallerOptions.DESERIALIZER.deserialize(jsonObject);

        assertEquals(false, restored.isForceAdbInstall());
    }

    private static JSONObject optionsJson(boolean forceAdbInstall) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user_id", 0);
        jsonObject.put("install_location", 0);
        jsonObject.put("installer_name", "io.github.sysadmindoc.AppManagerNG");
        jsonObject.put("originating_package", JSONObject.NULL);
        jsonObject.put("originating_uri", JSONObject.NULL);
        jsonObject.put("set_originating_package", false);
        jsonObject.put("package_source", 0);
        jsonObject.put("install_scenario", 0);
        jsonObject.put("request_update_ownership", false);
        jsonObject.put("disable_apk_verification", false);
        jsonObject.put("sign_apk_files", false);
        jsonObject.put("force_dex_opt", false);
        jsonObject.put("block_trackers", false);
        jsonObject.put("force_adb_install", forceAdbInstall);
        return jsonObject;
    }
}
