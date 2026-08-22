// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.app.Application;

import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class FmFolderViewPreferencesTest {
    private static final Uri DOWNLOADS = Uri.parse("file:///storage/emulated/0/Download");
    private static final Uri PICTURES = Uri.parse("file:///storage/emulated/0/Pictures");

    @Test
    public void storesIndependentOverridesForEachFolder() {
        String serialized = FmFolderViewPreferences.put(null, DOWNLOADS,
                FmListOptions.SORT_BY_SIZE, true,
                FmListOptions.OPTIONS_DISPLAY_DOT_FILES | FmListOptions.OPTIONS_ONLY_FOR_THIS_FOLDER);
        serialized = FmFolderViewPreferences.put(serialized, PICTURES,
                FmListOptions.SORT_BY_TYPE, false, FmListOptions.OPTIONS_FOLDERS_FIRST);

        FmFolderViewPreferences.Value downloads = FmFolderViewPreferences.get(serialized, DOWNLOADS);
        assertEquals(FmListOptions.SORT_BY_SIZE, downloads.sortBy);
        assertTrue(downloads.reverseSort);
        assertEquals(FmListOptions.OPTIONS_DISPLAY_DOT_FILES, downloads.options);

        FmFolderViewPreferences.Value pictures = FmFolderViewPreferences.get(serialized, PICTURES);
        assertEquals(FmListOptions.SORT_BY_TYPE, pictures.sortBy);
        assertFalse(pictures.reverseSort);
        assertEquals(FmListOptions.OPTIONS_FOLDERS_FIRST, pictures.options);
    }

    @Test
    public void missingAndMalformedOverridesFallBackToGlobalSettings() {
        assertNull(FmFolderViewPreferences.get(null, DOWNLOADS));
        assertNull(FmFolderViewPreferences.get("not-json", DOWNLOADS));
        assertNull(FmFolderViewPreferences.get(
                "{\"file:///storage/emulated/0/Download\":{\"sort\":\"bad\",\"reverse\":true,\"options\":1}}",
                DOWNLOADS));
        String serialized = FmFolderViewPreferences.put(null, DOWNLOADS,
                FmListOptions.SORT_BY_NAME, false, 0);
        assertNull(FmFolderViewPreferences.get(serialized, PICTURES));
        assertNull(FmFolderViewPreferences.get(
                FmFolderViewPreferences.remove(serialized, DOWNLOADS), DOWNLOADS));
    }

    @Test
    public void viewModelRestoresFolderOverrideWithoutChangingGlobalDefaults() {
        Application application = ApplicationProvider.getApplicationContext();
        Uri folder = Uri.fromFile(application.getFilesDir());
        int previousSort = Prefs.FileManager.getSortOrder();
        boolean previousReverse = Prefs.FileManager.isReverseSort();
        int previousOptions = Prefs.FileManager.getOptions();
        try {
            Prefs.FileManager.setSortOrder(FmListOptions.SORT_BY_NAME);
            Prefs.FileManager.setReverseSort(false);
            Prefs.FileManager.setOptions(FmListOptions.OPTIONS_DISPLAY_DOT_FILES);
            Prefs.FileManager.setFolderViewPreferences(folder, FmListOptions.SORT_BY_SIZE,
                    true, FmListOptions.OPTIONS_FOLDERS_FIRST);

            FmViewModel viewModel = new FmViewModel(application);
            viewModel.loadFiles(folder);

            assertEquals(FmListOptions.SORT_BY_SIZE, viewModel.getSortBy());
            assertTrue(viewModel.isReverseSort());
            assertTrue(viewModel.isOptionSelected(FmListOptions.OPTIONS_FOLDERS_FIRST));
            assertEquals(FmListOptions.SORT_BY_NAME, Prefs.FileManager.getSortOrder());
            assertFalse(Prefs.FileManager.isReverseSort());
            assertEquals(FmListOptions.OPTIONS_DISPLAY_DOT_FILES, Prefs.FileManager.getOptions());
            viewModel.onCleared();
        } finally {
            Prefs.FileManager.clearFolderViewPreferences(folder);
            Prefs.FileManager.setSortOrder(previousSort);
            Prefs.FileManager.setReverseSort(previousReverse);
            Prefs.FileManager.setOptions(previousOptions);
        }
    }
}
