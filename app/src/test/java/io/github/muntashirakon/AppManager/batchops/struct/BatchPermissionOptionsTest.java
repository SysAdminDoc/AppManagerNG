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
public class BatchPermissionOptionsTest {
    @Test
    public void constructorTrimsPermissionNames() {
        BatchPermissionOptions options = new BatchPermissionOptions(
                new String[]{" android.permission.POST_NOTIFICATIONS "});

        assertArrayEquals(new String[]{"android.permission.POST_NOTIFICATIONS"}, options.getPermissions());
    }

    @Test
    public void constructorAcceptsSingleWildcard() {
        BatchPermissionOptions options = new BatchPermissionOptions(new String[]{" * "});

        assertArrayEquals(new String[]{"*"}, options.getPermissions());
    }

    @Test
    public void constructorRejectsEmptyBlankNullAndMixedWildcard() {
        assertThrows(IllegalArgumentException.class, () -> new BatchPermissionOptions(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> new BatchPermissionOptions(new String[]{""}));
        assertThrows(IllegalArgumentException.class, () -> new BatchPermissionOptions(new String[]{"   "}));
        assertThrows(IllegalArgumentException.class, () -> new BatchPermissionOptions(new String[]{null}));
        assertThrows(IllegalArgumentException.class, () -> new BatchPermissionOptions(
                new String[]{"*", "android.permission.POST_NOTIFICATIONS"}));
    }

    @Test
    public void jsonRestorationTrimsPermissionNames() throws Exception {
        BatchPermissionOptions options = BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(" android.permission.POST_NOTIFICATIONS ")));

        assertArrayEquals(new String[]{"android.permission.POST_NOTIFICATIONS"}, options.getPermissions());
    }

    @Test
    public void jsonRestorationRejectsMalformedPermissions() {
        assertThrows(JSONException.class, () -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray())));
        assertThrows(JSONException.class, () -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(""))));
        assertThrows(JSONException.class, () -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(JSONObject.NULL))));
        assertThrows(JSONException.class, () -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put(7))));
        assertThrows(JSONException.class, () -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonOptions(
                new JSONArray().put("*").put("android.permission.POST_NOTIFICATIONS"))));
    }

    @Test
    public void parcelRestorationRejectsMixedWildcard() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeStringArray(new String[]{"*", "android.permission.POST_NOTIFICATIONS"});
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class, () -> BatchPermissionOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject jsonOptions(JSONArray permissions) throws JSONException {
        return new JSONObject()
                .put("tag", BatchPermissionOptions.TAG)
                .put("permissions", permissions);
    }
}
