// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BatchComponentOptionsTest {
    @Test
    public void constructorTrimsComponentSignatures() {
        BatchComponentOptions options = new BatchComponentOptions(new String[]{" io.example.app. "});

        assertArrayEquals(new String[]{"io.example.app."}, options.getSignatures());
    }

    @Test
    public void constructorRejectsEmptyBlankAndNullSignatures() {
        assertThrows(IllegalArgumentException.class, () -> new BatchComponentOptions(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> new BatchComponentOptions(new String[]{""}));
        assertThrows(IllegalArgumentException.class, () -> new BatchComponentOptions(new String[]{"   "}));
        assertThrows(IllegalArgumentException.class, () -> new BatchComponentOptions(new String[]{null}));
    }

    @Test
    public void jsonRestorationTrimsComponentSignatures() throws Exception {
        BatchComponentOptions options = BatchComponentOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(" io.example.app. ")));

        assertArrayEquals(new String[]{"io.example.app."}, options.getSignatures());
    }

    @Test
    public void jsonRestorationRejectsMalformedSignatures() {
        assertThrows(JSONException.class, () -> BatchComponentOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray())));
        assertThrows(JSONException.class, () -> BatchComponentOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(""))));
        assertThrows(JSONException.class, () -> BatchComponentOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(JSONObject.NULL))));
        assertThrows(JSONException.class, () -> BatchComponentOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(7))));
    }

    @Test
    public void parcelRestorationRejectsBlankSignatures() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeStringArray(new String[]{""});
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class, () -> BatchComponentOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject jsonOptions(JSONArray signatures) throws JSONException {
        return new JSONObject()
                .put("tag", BatchComponentOptions.TAG)
                .put("signatures", signatures);
    }
}
