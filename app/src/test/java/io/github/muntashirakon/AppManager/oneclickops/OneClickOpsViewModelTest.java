// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class OneClickOpsViewModelTest {
    @Test
    public void getTrimCacheVolumeUuids_alwaysStartsWithInternalVolume() {
        List<String> volumeUuids = OneClickOpsViewModel.getTrimCacheVolumeUuids(Collections.emptyList());

        assertEquals(1, volumeUuids.size());
        assertNull(volumeUuids.get(0));
    }

    @Test
    public void getTrimCacheVolumeUuids_deduplicatesWritableVolumesInOrder() {
        List<String> volumeUuids = OneClickOpsViewModel.getTrimCacheVolumeUuids(
                Arrays.asList("ABCD-1234", "ABCD-1234", null, "", "EFGH-5678"));

        assertEquals(3, volumeUuids.size());
        assertNull(volumeUuids.get(0));
        assertEquals("ABCD-1234", volumeUuids.get(1));
        assertEquals("EFGH-5678", volumeUuids.get(2));
    }
}
