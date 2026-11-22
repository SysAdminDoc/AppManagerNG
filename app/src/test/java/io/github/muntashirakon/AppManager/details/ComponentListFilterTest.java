// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Issue 51: an app with hundreds of activities buries the few the row can launch. The "Launchable only" filter keeps
 * exactly the rows that show a Launch button.
 */
@RunWith(RobolectricTestRunner.class)
public class ComponentListFilterTest {
    @After
    public void restorePreference() {
        Prefs.AppDetailsPage.setShowLaunchableActivitiesOnly(false);
    }

    @Test
    public void aRowIsLaunchableWhenItOffersEitherLaunchButton() {
        assertTrue(activity("a.Direct", true, false).isLaunchable());
        assertTrue(activity("a.Assist", false, true).isLaunchable());
        assertFalse(activity("a.Hidden", false, false).isLaunchable());
    }

    @Test
    public void theFilterKeepsOnlyTheRowsWithALaunchButton() {
        List<AppDetailsItem<ComponentInfo>> items = new ArrayList<>(Arrays.asList(
                activity("a.Direct", true, false),
                activity("a.Hidden", false, false),
                activity("a.Assist", false, true),
                activity("a.Disabled", false, false)));

        List<AppDetailsItem<ComponentInfo>> kept = ComponentListFilter.launchableOnly(items, true);

        assertEquals(Arrays.asList("a.Direct", "a.Assist"), names(kept));
        // The full list is left alone, so turning the filter off brings every row back
        assertEquals(4, items.size());
    }

    @Test
    public void withTheFilterOffEveryRowStays() {
        List<AppDetailsItem<ComponentInfo>> items = new ArrayList<>(Arrays.asList(
                activity("a.Direct", true, false),
                activity("a.Hidden", false, false)));

        assertSame(items, ComponentListFilter.launchableOnly(items, false));
    }

    @Test
    public void theChoiceIsRemembered() {
        assertFalse(Prefs.AppDetailsPage.showLaunchableActivitiesOnly());

        Prefs.AppDetailsPage.setShowLaunchableActivitiesOnly(true);

        assertTrue(Prefs.AppDetailsPage.showLaunchableActivitiesOnly());
    }

    @NonNull
    private static List<String> names(@NonNull List<AppDetailsItem<ComponentInfo>> items) {
        List<String> names = new ArrayList<>();
        for (AppDetailsItem<ComponentInfo> item : items) {
            names.add(item.name);
        }
        return names;
    }

    @NonNull
    private static AppDetailsActivityItem activity(@NonNull String name, boolean canLaunch, boolean canLaunchAssist) {
        ActivityInfo info = new ActivityInfo();
        info.name = name;
        info.packageName = "a";
        info.applicationInfo = new ApplicationInfo();
        AppDetailsActivityItem item = new AppDetailsActivityItem(info);
        item.canLaunch = canLaunch;
        item.canLaunchAssist = canLaunchAssist;
        return item;
    }
}
