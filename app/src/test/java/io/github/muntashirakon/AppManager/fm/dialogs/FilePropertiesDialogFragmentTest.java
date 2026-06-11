// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void formatPropertyDisplayPathFlattensControlsAndDefusesFormula() {
        assertEquals("' =payload sdcard/file.txt",
                FilePropertiesDialogFragment.formatPropertyDisplayPath("\t=payload\nsdcard/file.txt"));
    }

    @Test
    public void formatPropertyDisplayTextFlattensControlsAndDefusesFormula() {
        assertEquals("' =payload context",
                FilePropertiesDialogFragment.formatPropertyDisplayText("\t=payload\ncontext"));
    }

    @Test
    public void shouldShowOpenWithOnlyForReadableFiles() {
        FilePropertiesDialogFragment.FileProperties file = new FilePropertiesDialogFragment.FileProperties();
        file.canRead = true;
        file.isDirectory = false;
        assertTrue(FilePropertiesDialogFragment.shouldShowOpenWith(file));

        file.isDirectory = true;
        assertFalse(FilePropertiesDialogFragment.shouldShowOpenWith(file));

        file.isDirectory = false;
        file.canRead = false;
        assertFalse(FilePropertiesDialogFragment.shouldShowOpenWith(file));
    }
}
