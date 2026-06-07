// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.app.AppOpsManager;
import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

@RunWith(RobolectricTestRunner.class)
public class BatchAppOpsOptionsTest {
    @Test
    public void testParcelable() {
        int[] array = new int[]{AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME};
        BatchAppOpsOptions options = new BatchAppOpsOptions(array, AppOpsManager.MODE_ALLOWED);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchAppOpsOptions options2 = BatchAppOpsOptions.CREATOR.createFromParcel(parcel);
        assertArrayEquals(array, options2.getAppOps());
        assertEquals(AppOpsManager.MODE_ALLOWED, options2.getMode());
    }

    @Test
    public void jsonAcceptsSingleWildcardAppOp() throws Exception {
        BatchAppOpsOptions options = BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[]{AppOpsManagerCompat.OP_NONE}, AppOpsManager.MODE_DEFAULT));

        assertArrayEquals(new int[]{AppOpsManagerCompat.OP_NONE}, options.getAppOps());
        assertEquals(AppOpsManager.MODE_DEFAULT, options.getMode());
    }

    @Test
    public void jsonRejectsInvalidAppOpsAndModes() {
        assertThrows(JSONException.class, () -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[0], AppOpsManager.MODE_ALLOWED)));
        assertThrows(JSONException.class, () -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[]{AppOpsManagerCompat.OP_NONE, AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME},
                AppOpsManager.MODE_ALLOWED)));
        assertThrows(JSONException.class, () -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[]{-2}, AppOpsManager.MODE_ALLOWED)));
        assertThrows(JSONException.class, () -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[]{AppOpsManagerCompat._NUM_OP}, AppOpsManager.MODE_ALLOWED)));
        assertThrows(JSONException.class, () -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonOptions(
                new int[]{AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME}, 9999)));
    }

    @Test
    public void constructorRejectsInvalidAppOpsAndModes() {
        assertThrows(IllegalArgumentException.class, () -> new BatchAppOpsOptions(
                new int[0], AppOpsManager.MODE_ALLOWED));
        assertThrows(IllegalArgumentException.class, () -> new BatchAppOpsOptions(
                new int[]{AppOpsManagerCompat.OP_NONE, AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME},
                AppOpsManager.MODE_ALLOWED));
        assertThrows(IllegalArgumentException.class, () -> new BatchAppOpsOptions(
                new int[]{AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME}, 9999));
    }

    private static JSONObject jsonOptions(int[] appOps, int mode) throws JSONException {
        JSONArray appOpsJson = new JSONArray();
        for (int appOp : appOps) {
            appOpsJson.put(appOp);
        }
        return new JSONObject()
                .put("tag", BatchAppOpsOptions.TAG)
                .put("app_ops", appOpsJson)
                .put("mode", mode);
    }
}
