// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BatchSafetyOptionsTest {
    @Test
    public void jsonRoundTripPreservesCriticalPackageOverride() throws Exception {
        BatchSafetyOptions options = new BatchSafetyOptions(true);

        BatchSafetyOptions restored = BatchSafetyOptions.DESERIALIZER.deserialize(options.serializeToJson());

        assertTrue(restored.isAllowCriticalPackages());
    }

    @Test
    public void missingJsonOverrideDefaultsToBlocked() throws Exception {
        BatchSafetyOptions restored = BatchSafetyOptions.DESERIALIZER.deserialize(new JSONObject()
                .put("tag", BatchSafetyOptions.TAG));

        assertFalse(restored.isAllowCriticalPackages());
    }

    @Test
    public void parcelRoundTripPreservesCriticalPackageOverride() {
        BatchSafetyOptions options = new BatchSafetyOptions(true);
        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BatchSafetyOptions restored = BatchSafetyOptions.CREATOR.createFromParcel(parcel);

            assertTrue(restored.isAllowCriticalPackages());
        } finally {
            parcel.recycle();
        }
    }
}
