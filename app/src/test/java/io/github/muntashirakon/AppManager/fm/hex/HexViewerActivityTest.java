// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.hex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class HexViewerActivityTest {
    @Test
    public void formatExternalMetadataTextFlattensControlsDefusesFormulaAndFallsBack() {
        assertEquals("' =Binary Name",
                HexViewerActivity.formatExternalMetadataText("\t=Binary\nName", "Fallback"));
        assertEquals("Fallback", HexViewerActivity.formatExternalMetadataText("\n\t", "Fallback"));
        assertEquals("Fallback", HexViewerActivity.formatExternalMetadataText(null, "Fallback"));
    }
}
