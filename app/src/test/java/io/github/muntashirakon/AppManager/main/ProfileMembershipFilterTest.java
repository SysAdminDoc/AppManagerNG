// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.options.PackageNameOption;

@RunWith(RobolectricTestRunner.class)
public class ProfileMembershipFilterTest {
    @Test
    public void packageProfileIncludesListedPackages() {
        ProfileMembershipFilter filter = ProfileMembershipFilter.fromPackageNames(
                new String[]{"com.example.keep", "com.example.audit"}, false);

        assertTrue(filter.matches(app("com.example.keep")));
        assertTrue(filter.matches(app("com.example.audit")));
        assertFalse(filter.matches(app("com.example.other")));
    }

    @Test
    public void packageProfileExcludesListedPackagesWhenInverted() {
        ProfileMembershipFilter filter = ProfileMembershipFilter.fromPackageNames(
                new String[]{"com.example.keep", "com.example.audit"}, true);

        assertFalse(filter.matches(app("com.example.keep")));
        assertFalse(filter.matches(app("com.example.audit")));
        assertTrue(filter.matches(app("com.example.other")));
    }

    @Test
    public void emptyPackageProfileIncludesNoneAndExcludesNone() {
        ProfileMembershipFilter include = ProfileMembershipFilter.fromPackageNames(new String[0], false);
        ProfileMembershipFilter exclude = ProfileMembershipFilter.fromPackageNames(new String[0], true);

        assertFalse(include.matches(app("com.example.any")));
        assertTrue(exclude.matches(app("com.example.any")));
    }

    @Test
    public void filterProfileCanBeInvertedWithoutChangingOtherPredicates() {
        FilterItem profileFilter = new FilterItem();
        PackageNameOption option = new PackageNameOption();
        option.setKeyValue("starts_with", "com.example.");
        profileFilter.addFilterOption(option);

        ProfileMembershipFilter include = ProfileMembershipFilter.fromFilterItem(profileFilter, false);
        ProfileMembershipFilter exclude = ProfileMembershipFilter.fromFilterItem(profileFilter, true);

        assertTrue(include.matches(app("com.example.member")));
        assertFalse(include.matches(app("org.example.other")));
        assertFalse(exclude.matches(app("com.example.member")));
        assertTrue(exclude.matches(app("org.example.other")));
    }

    @Test
    public void disabledProfileFilterMatchesEverything() {
        ProfileMembershipFilter filter = ProfileMembershipFilter.none();

        assertTrue(filter.matches(app("com.example.any")));
        assertFalse(filter.isFiltering());
    }

    private static ApplicationItem app(String packageName) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        return item;
    }
}
