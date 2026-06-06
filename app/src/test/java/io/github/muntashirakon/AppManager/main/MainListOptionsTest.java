// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.options.AppTypeOption;

@RunWith(RobolectricTestRunner.class)
public class MainListOptionsTest {
    @Test
    public void appsWithSplitsFlagMatchesOnlySplitApps() {
        FilterItem filterItem = MainListOptions.getFilterItemFromFlags(MainListOptions.FILTER_APPS_WITH_SPLITS);

        assertEquals("app_type_1", filterItem.getExpr());
        assertEquals("with_flags", filterItem.getFilterOptionAt(0).getKey());
        assertEquals(String.valueOf(AppTypeOption.APP_TYPE_HAS_SPLITS), filterItem.getFilterOptionAt(0).getValue());
        assertTrue(AppTypeOption.withFlagsCheck(app(true, false), AppTypeOption.APP_TYPE_HAS_SPLITS));
        assertTrue(filterItem.matches(app(true, false)));
        assertFalse(filterItem.matches(app(false, false)));
    }

    @Test
    public void appsWithSafFlagMatchesOnlySafApps() {
        FilterItem filterItem = MainListOptions.getFilterItemFromFlags(MainListOptions.FILTER_APPS_WITH_SAF);

        assertTrue(filterItem.matches(app(false, true)));
        assertFalse(filterItem.matches(app(false, false)));
    }

    @Test
    public void splitAndSafFlagsRequireBothProperties() {
        FilterItem filterItem = MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_APPS_WITH_SPLITS | MainListOptions.FILTER_APPS_WITH_SAF);

        assertTrue(filterItem.matches(app(true, true)));
        assertFalse(filterItem.matches(app(true, false)));
        assertFalse(filterItem.matches(app(false, true)));
    }

    private static ApplicationItem app(boolean hasSplits, boolean usesSaf) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.app";
        item.hasSplits = hasSplits;
        item.usesSaf = usesSaf;
        return item;
    }
}
