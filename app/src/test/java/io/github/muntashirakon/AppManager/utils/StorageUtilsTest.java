// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

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
}
