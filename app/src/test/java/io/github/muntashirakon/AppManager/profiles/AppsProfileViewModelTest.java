// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class AppsProfileViewModelTest {
    @Test
    public void mergeSelectablePackagesAddsBackupOnlyPackages() {
        ApplicationInfo installedInfo = new ApplicationInfo();
        installedInfo.packageName = "com.example.installed";
        installedInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        AppsProfileViewModel.SelectablePackageItem installedItem =
                AppsProfileViewModel.SelectablePackageItem.fromApplicationInfo("Installed", installedInfo);

        Backup installedBackup = new Backup();
        installedBackup.packageName = "com.example.installed";
        installedBackup.label = "Installed backup";

        Backup backupOnly = new Backup();
        backupOnly.packageName = "com.example.backuponly";
        backupOnly.label = "Backup Only";

        HashMap<String, Backup> backups = new HashMap<>();
        backups.put(installedBackup.packageName, installedBackup);
        backups.put(backupOnly.packageName, backupOnly);

        List<AppsProfileViewModel.SelectablePackageItem> merged =
                AppsProfileViewModel.mergeSelectablePackages(Collections.singletonList(installedItem), backups,
                        Collections.emptyList());

        assertEquals(2, merged.size());
        assertPackage(merged, "com.example.installed", false);
        assertPackage(merged, "com.example.backuponly", true);
    }

    @Test
    public void mergeSelectablePackagesKeepsMissingSelectedPackageVisible() {
        List<AppsProfileViewModel.SelectablePackageItem> merged =
                AppsProfileViewModel.mergeSelectablePackages(Collections.emptyList(), Collections.emptyMap(),
                        Arrays.asList("com.example.missing"));

        assertEquals(1, merged.size());
        assertEquals("com.example.missing", merged.get(0).packageName);
        assertTrue(merged.get(0).backupOnly);
    }

    @Test
    public void sortingAProfilesAppsLeavesTheMainListsSortAlone() {
        Prefs.MainPage.setSortOrder(MainListOptions.SORT_BY_LAST_UPDATE);
        Prefs.MainPage.setReverseSort(false);
        AppsProfileViewModel model = new AppsProfileViewModel(ApplicationProvider.getApplicationContext());

        // A profile's list starts alphabetical whatever the main list uses
        assertEquals(MainListOptions.SORT_BY_APP_LABEL, model.getSortBy());
        model.setSortBy(MainListOptions.SORT_BY_PACKAGE_NAME);
        model.setReverseSort(true);

        assertEquals(MainListOptions.SORT_BY_PACKAGE_NAME, model.getSortBy());
        assertTrue(model.isReverseSort());
        assertEquals(MainListOptions.SORT_BY_LAST_UPDATE, Prefs.MainPage.getSortOrder());
        assertFalse(Prefs.MainPage.isReverseSort());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void theShownAppsFollowTheSearchAndTheSort() {
        AppsProfileViewModel model = new AppsProfileViewModel(ApplicationProvider.getApplicationContext());
        List<List<AppsFragment.AppsFragmentItem>> shown = new ArrayList<>();
        model.getDisplayedPackages().observeForever(shown::add);
        ((MutableLiveData<ArrayList<AppsFragment.AppsFragmentItem>>) model.getPackages()).setValue(new ArrayList<>(
                Arrays.asList(ProfileAppsListFilterTest.item("com.b", "Bravo", false, 0, 0),
                        ProfileAppsListFilterTest.item("org.a", "Alpha", false, 0, 0))));

        assertEquals(Arrays.asList("org.a", "com.b"), ProfileAppsListFilterTest.names(last(shown)));
        model.setSortBy(MainListOptions.SORT_BY_PACKAGE_NAME);
        assertEquals(Arrays.asList("com.b", "org.a"), ProfileAppsListFilterTest.names(last(shown)));
        model.setAppsQuery("alp", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        assertEquals(Collections.singletonList("org.a"), ProfileAppsListFilterTest.names(last(shown)));
        model.setAppsQuery(null, AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        assertEquals(Arrays.asList("com.b", "org.a"), ProfileAppsListFilterTest.names(last(shown)));
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    private static void assertPackage(List<AppsProfileViewModel.SelectablePackageItem> items,
                                      String packageName, boolean backupOnly) {
        for (AppsProfileViewModel.SelectablePackageItem item : items) {
            if (packageName.equals(item.packageName)) {
                assertEquals(backupOnly, item.backupOnly);
                if (backupOnly) {
                    assertEquals("Backup Only", item.label);
                } else {
                    assertFalse(item.backupOnly);
                }
                return;
            }
        }
        throw new AssertionError("Missing package " + packageName);
    }
}
