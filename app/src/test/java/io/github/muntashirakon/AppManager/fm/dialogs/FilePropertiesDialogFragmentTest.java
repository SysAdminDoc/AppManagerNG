// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class FilePropertiesDialogFragmentTest {
    @Test
    public void describeSharedUidLabels_joinsDistinctLabelsInOrder() {
        CharSequence labels = FilePropertiesDialogFragment.FilePropertiesViewModel.describeSharedUidLabels(
                Arrays.asList("Calendar", "Contacts", "Sync"));

        assertEquals("Calendar, Contacts, Sync", labels.toString());
    }

    @Test
    public void describeSharedUidLabels_removesDuplicateLabels() {
        CharSequence labels = FilePropertiesDialogFragment.FilePropertiesViewModel.describeSharedUidLabels(
                Arrays.asList("Calendar", "Contacts", "Calendar"));

        assertEquals("Calendar, Contacts", labels.toString());
    }

    @Test
    public void describeSharedUidLabels_skipsEmptyLabels() {
        CharSequence labels = FilePropertiesDialogFragment.FilePropertiesViewModel.describeSharedUidLabels(
                Arrays.asList("", "Calendar", null));

        assertEquals("Calendar", labels.toString());
    }

    @Test
    public void describeSharedUidLabels_handlesEmptyInput() {
        CharSequence labels = FilePropertiesDialogFragment.FilePropertiesViewModel.describeSharedUidLabels(
                Collections.emptyList());

        assertEquals("", labels.toString());
    }

    @Test
    public void formatPropertyDisplayNameFlattensControlsDefusesFormulaAndFallsBack() {
        assertEquals("' =payload name.txt",
                FilePropertiesDialogFragment.formatPropertyDisplayName("\t=payload\nname.txt", "/sdcard/fallback"));
        assertEquals("/sdcard/fallback",
                FilePropertiesDialogFragment.formatPropertyDisplayName("\r\n\t", "/sdcard/fallback"));
    }
}
