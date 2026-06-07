// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FmPathListAdapterTest {
    @Test
    public void getDisplayPathPartFormatsBreadcrumbLabels() {
        assertEquals("' =payload folder",
                FmPathListAdapter.getDisplayPathPart("\t=payload\nfolder"));
    }
}
