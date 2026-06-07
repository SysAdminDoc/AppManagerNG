// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BatchNetPolicyOptionsTest {
    @Test
    public void constructorAcceptsZeroAndPositivePolicies() {
        assertEquals(0, new BatchNetPolicyOptions(0).getPolicies());
        assertEquals(1001, new BatchNetPolicyOptions(1001).getPolicies());
    }

    @Test
    public void constructorRejectsNegativePolicies() {
        assertThrows(IllegalArgumentException.class, () -> new BatchNetPolicyOptions(-1));
    }

    @Test
    public void jsonRestorationAcceptsPositivePolicies() throws Exception {
        BatchNetPolicyOptions options = BatchNetPolicyOptions.DESERIALIZER.deserialize(jsonOptions(1002));

        assertEquals(1002, options.getPolicies());
    }

    @Test
    public void jsonRestorationRejectsNegativePolicies() {
        assertThrows(JSONException.class, () -> BatchNetPolicyOptions.DESERIALIZER.deserialize(jsonOptions(-1)));
    }

    @Test
    public void parcelRestorationRejectsNegativePolicies() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(-1);
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class, () -> BatchNetPolicyOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject jsonOptions(int policies) throws JSONException {
        return new JSONObject()
                .put("tag", BatchNetPolicyOptions.TAG)
                .put("policies", policies);
    }
}
