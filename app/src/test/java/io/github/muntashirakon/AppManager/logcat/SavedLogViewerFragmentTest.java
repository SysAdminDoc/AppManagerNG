// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SavedLogViewerFragmentTest {
    @Test
    public void formatSubtitleFlattensControlsDefusesFormulaAndAllowsBlank() {
        assertEquals("' =events log", SavedLogViewerFragment.formatSubtitle("\t=events\nlog"));
        assertEquals("", SavedLogViewerFragment.formatSubtitle("\n\t"));
        assertEquals("", SavedLogViewerFragment.formatSubtitle(null));
    }
}
