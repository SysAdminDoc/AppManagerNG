// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CodeEditorActivityTest {
    @Test
    public void formatExternalMetadataTextFlattensControlsDefusesFormulaAndFallsBack() {
        assertEquals("' =Title Name",
                CodeEditorActivity.formatExternalMetadataText("\t=Title\nName", "Fallback"));
        assertEquals("Fallback", CodeEditorActivity.formatExternalMetadataText("\n\t", "Fallback"));
        assertEquals("Fallback", CodeEditorActivity.formatExternalMetadataText(null, "Fallback"));
    }
}
