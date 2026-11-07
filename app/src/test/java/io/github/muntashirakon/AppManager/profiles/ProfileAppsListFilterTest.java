// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

@RunWith(RobolectricTestRunner.class)
public class ProfileAppsListFilterTest {
    private static final int CONTAINS = AdvancedSearchView.SEARCH_TYPE_CONTAINS;

    @Test
    public void aPlainSearchMatchesTheLabelOrThePackageNameInAnyCase() {
        List<AppsFragment.AppsFragmentItem> items = Arrays.asList(
                item("org.mozilla.firefox", "Firefox", false, 0, 0),
                item("com.android.chrome", "Chrome", true, 0, 0),
                item("com.example.nolabel", null, false, 0, 0));

        assertEquals(names("org.mozilla.firefox"), names(ProfileAppsListFilter.apply(items, "FIRE", CONTAINS,
                MainListOptions.SORT_BY_APP_LABEL, false)));
        assertEquals(names("com.android.chrome"), names(ProfileAppsListFilter.apply(items, "android", CONTAINS,
                MainListOptions.SORT_BY_APP_LABEL, false)));
        assertEquals(names("com.example.nolabel"), names(ProfileAppsListFilter.apply(items, "nolabel",
                AdvancedSearchView.SEARCH_TYPE_SUFFIX, MainListOptions.SORT_BY_APP_LABEL, false)));
        assertEquals(names("com.android.chrome", "com.example.nolabel"), names(ProfileAppsListFilter.apply(items,
                "com.", AdvancedSearchView.SEARCH_TYPE_PREFIX, MainListOptions.SORT_BY_PACKAGE_NAME, false)));
    }

    @Test
    public void aRegexSearchMatchesAndABrokenPatternMatchesNothingInsteadOfCrashing() {
        List<AppsFragment.AppsFragmentItem> items = Arrays.asList(
                item("org.mozilla.firefox", "Firefox", false, 0, 0),
                item("com.android.chrome", "Chrome", true, 0, 0));

        assertEquals(names("com.android.chrome"), names(ProfileAppsListFilter.apply(items, "^com\\.",
                AdvancedSearchView.SEARCH_TYPE_REGEX, MainListOptions.SORT_BY_APP_LABEL, false)));
        assertTrue(ProfileAppsListFilter.apply(items, "([", AdvancedSearchView.SEARCH_TYPE_REGEX,
                MainListOptions.SORT_BY_APP_LABEL, false).isEmpty());
    }

    @Test
    public void installAndUpdateDatesSortNewestFirstAndReverseFlipsThem() {
        List<AppsFragment.AppsFragmentItem> items = Arrays.asList(
                item("a.old", "Old", false, 100, 900),
                item("b.new", "New", false, 300, 100),
                item("c.mid", "Mid", false, 200, 500));

        assertEquals(names("b.new", "c.mid", "a.old"), names(ProfileAppsListFilter.apply(items, "", CONTAINS,
                MainListOptions.SORT_BY_INSTALLATION_DATE, false)));
        assertEquals(names("a.old", "c.mid", "b.new"), names(ProfileAppsListFilter.apply(items, "", CONTAINS,
                MainListOptions.SORT_BY_INSTALLATION_DATE, true)));
        assertEquals(names("a.old", "c.mid", "b.new"), names(ProfileAppsListFilter.apply(items, "", CONTAINS,
                MainListOptions.SORT_BY_LAST_UPDATE, false)));
    }

    @Test
    public void labelSortIsAlphabeticalAndTiesInOtherSortsStayAlphabetical() {
        List<AppsFragment.AppsFragmentItem> items = Arrays.asList(
                item("z.system.b", "Bravo", true, 0, 0),
                item("y.user.c", "Charlie", false, 0, 0),
                item("x.system.a", "Alpha", true, 0, 0),
                item("w.user.d", "Delta", false, 0, 0));

        assertEquals(names("x.system.a", "z.system.b", "y.user.c", "w.user.d"), names(ProfileAppsListFilter.apply(
                items, null, CONTAINS, MainListOptions.SORT_BY_APP_LABEL, false)));
        assertEquals(names("w.user.d", "y.user.c", "z.system.b", "x.system.a"), names(ProfileAppsListFilter.apply(
                items, null, CONTAINS, MainListOptions.SORT_BY_APP_LABEL, true)));
        // User apps first, each group alphabetical by label
        assertEquals(names("y.user.c", "w.user.d", "x.system.a", "z.system.b"), names(ProfileAppsListFilter.apply(
                items, null, CONTAINS, MainListOptions.SORT_BY_DOMAIN, false)));
    }

    @Test
    public void theInputListIsNotReordered() {
        List<AppsFragment.AppsFragmentItem> items = new ArrayList<>(Arrays.asList(
                item("b", "B", false, 0, 0), item("a", "A", false, 0, 0)));

        ProfileAppsListFilter.apply(items, "", CONTAINS, MainListOptions.SORT_BY_APP_LABEL, false);

        assertEquals(names("b", "a"), names(items));
    }

    static AppsFragment.AppsFragmentItem item(String packageName, String label, boolean system,
                                              long installed, long updated) {
        AppsFragment.AppsFragmentItem item = new AppsFragment.AppsFragmentItem(packageName);
        item.label = label;
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        item.applicationInfo = info;
        item.firstInstallTime = installed;
        item.lastUpdateTime = updated;
        return item;
    }

    static List<String> names(String... packageNames) {
        return Arrays.asList(packageNames);
    }

    static List<String> names(List<AppsFragment.AppsFragmentItem> items) {
        List<String> names = new ArrayList<>(items.size());
        for (AppsFragment.AppsFragmentItem item : items) {
            names.add(item.packageName);
        }
        return names;
    }
}
