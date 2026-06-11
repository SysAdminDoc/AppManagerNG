// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.utils.FreezeUtils;

@RunWith(RobolectricTestRunner.class)
public class BatchFreezeOptionsTest {
    @Test
    public void constructorAcceptsSupportedFreezeTypes() {
        assertEquals(FreezeUtils.FREEZE_DISABLE,
                new BatchFreezeOptions(FreezeUtils.FREEZE_DISABLE, false).getType());
        assertEquals(FreezeUtils.FREEZE_SUSPEND,
                new BatchFreezeOptions(FreezeUtils.FREEZE_SUSPEND, false).getType());
        assertEquals(FreezeUtils.FREEZE_HIDE,
                new BatchFreezeOptions(FreezeUtils.FREEZE_HIDE, false).getType());
        assertEquals(FreezeUtils.FREEZE_ADV_SUSPEND,
                new BatchFreezeOptions(FreezeUtils.FREEZE_ADV_SUSPEND, false).getType());
    }

    @Test
    public void constructorRejectsUnsupportedFreezeTypes() {
        assertThrows(IllegalArgumentException.class, () -> new BatchFreezeOptions(0, false));
        assertThrows(IllegalArgumentException.class, () -> new BatchFreezeOptions(-1, false));
        assertThrows(IllegalArgumentException.class, () -> new BatchFreezeOptions(16, false));
    }

    @Test
    public void jsonRestorationAcceptsSupportedFreezeType() throws Exception {
        BatchFreezeOptions options = BatchFreezeOptions.DESERIALIZER.deserialize(jsonOptions(
                FreezeUtils.FREEZE_ADV_SUSPEND, true));

        assertEquals(FreezeUtils.FREEZE_ADV_SUSPEND, options.getType());
        assertTrue(options.isPreferCustom());
        assertFalse(options.isAllowCriticalPackages());
    }

    @Test
    public void jsonRestorationAcceptsCriticalPackageOverride() throws Exception {
        BatchFreezeOptions options = BatchFreezeOptions.DESERIALIZER.deserialize(jsonOptions(
                FreezeUtils.FREEZE_ADV_SUSPEND, true, true));

        assertEquals(FreezeUtils.FREEZE_ADV_SUSPEND, options.getType());
        assertTrue(options.isPreferCustom());
        assertTrue(options.isAllowCriticalPackages());
    }

    @Test
    public void jsonRestorationRejectsUnsupportedFreezeType() {
        assertThrows(JSONException.class, () -> BatchFreezeOptions.DESERIALIZER.deserialize(jsonOptions(0, false)));
    }

    @Test
    public void parcelRestorationRejectsUnsupportedFreezeType() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(16);
            parcel.writeByte((byte) 1);
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class, () -> BatchFreezeOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRoundTripPreservesCriticalPackageOverride() {
        BatchFreezeOptions options = new BatchFreezeOptions(FreezeUtils.FREEZE_HIDE, true, true);
        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BatchFreezeOptions restored = BatchFreezeOptions.CREATOR.createFromParcel(parcel);

            assertEquals(FreezeUtils.FREEZE_HIDE, restored.getType());
            assertTrue(restored.isPreferCustom());
            assertTrue(restored.isAllowCriticalPackages());
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject jsonOptions(int type, boolean preferCustom) throws JSONException {
        return new JSONObject()
                .put("tag", BatchFreezeOptions.TAG)
                .put("type", type)
                .put("prefer_custom", preferCustom);
    }

    private static JSONObject jsonOptions(int type, boolean preferCustom,
                                          boolean allowCriticalPackages) throws JSONException {
        return jsonOptions(type, preferCustom)
                .put("allow_critical_packages", allowCriticalPackages);
    }
}
