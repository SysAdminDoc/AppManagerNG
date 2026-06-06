// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class StorageUtilsTest {
    @Test
    public void getFixedTreeUriAddsDocumentSegmentForTreeUri() {
        Uri treeUri = Uri.parse("content://com.example.documents/tree/primary%3AAppManager");
        Uri fixedUri = StorageUtils.getFixedTreeUri(treeUri);

        assertEquals("content://com.example.documents/tree/primary%3AAppManager/document/primary%3AAppManager",
                fixedUri.toString());
    }

    @Test
    public void getFixedTreeUriKeepsDocumentTreeUri() {
        Uri treeUri = Uri.parse("content://com.example.documents/tree/primary%3AAppManager/document/primary%3AAppManager%2Fbackups");
        Uri fixedUri = StorageUtils.getFixedTreeUri(treeUri);

        assertEquals(treeUri, fixedUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getFixedTreeUriRejectsSingleDocumentUri() {
        StorageUtils.getFixedTreeUri(Uri.parse("content://com.example.documents/document/primary%3AAppManager"));
    }

    @Test
    public void getTrimCacheVolumeUuids_alwaysStartsWithInternalVolume() {
        List<String> volumeUuids = StorageUtils.getTrimCacheVolumeUuids(Collections.emptyList());

        assertEquals(1, volumeUuids.size());
        assertNull(volumeUuids.get(0));
    }

    @Test
    public void getTrimCacheVolumeUuids_deduplicatesWritableVolumesInOrder() {
        List<String> volumeUuids = StorageUtils.getTrimCacheVolumeUuids(
                Arrays.asList("ABCD-1234", "ABCD-1234", null, "", "EFGH-5678"));

        assertEquals(3, volumeUuids.size());
        assertNull(volumeUuids.get(0));
        assertEquals("ABCD-1234", volumeUuids.get(1));
        assertEquals("EFGH-5678", volumeUuids.get(2));
    }
}
