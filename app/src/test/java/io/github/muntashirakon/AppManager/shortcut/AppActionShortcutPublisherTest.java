// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.main.ApplicationItem;

@RunWith(RobolectricTestRunner.class)
public class AppActionShortcutPublisherTest {
    @Test
    public void selectTargetActionsPrioritizesRecentAppsAndStableActionOrder() {
        List<AppActionShortcutPublisher.TargetAction> actions =
                AppActionShortcutPublisher.selectTargetActions(Arrays.asList(
                        item("com.example.old", "Old", 100, 10, false, false),
                        item("com.example.new", "New", 300, 1, false, false)
                ), 4, true, true, true);

        assertEquals(4, actions.size());
        assertEquals("com.example.new", actions.get(0).packageName);
        assertEquals(AppActionShortcutInfo.ACTION_FREEZE, actions.get(0).action);
        assertEquals(AppActionShortcutInfo.ACTION_FORCE_STOP, actions.get(1).action);
        assertEquals(AppActionShortcutInfo.ACTION_CLEAR_CACHE, actions.get(2).action);
        assertEquals("com.example.old", actions.get(3).packageName);
        assertEquals(AppActionShortcutInfo.ACTION_FREEZE, actions.get(3).action);
    }

    @Test
    public void selectTargetActionsHonorsCapabilityGates() {
        List<AppActionShortcutPublisher.TargetAction> actions =
                AppActionShortcutPublisher.selectTargetActions(Arrays.asList(
                        item("com.example.app", "App", 100, 0, false, false)
                ), 3, false, true, false);

        assertEquals(1, actions.size());
        assertEquals(AppActionShortcutInfo.ACTION_FORCE_STOP, actions.get(0).action);
    }

    @Test
    public void selectTargetActionsSkipsInvalidSelfUninstalledAndAlreadyStoppedFreezeCases() {
        List<AppActionShortcutPublisher.TargetAction> actions =
                AppActionShortcutPublisher.selectTargetActions(Arrays.asList(
                        item("not a package", "Invalid", 600, 0, false, false),
                        item(BuildConfig.APPLICATION_ID, "Self", 500, 0, false, false),
                        uninstalledItem("com.example.backup"),
                        item("com.example.frozen", "Frozen", 400, 0, true, true)
                ), 3, true, true, true);

        assertEquals(1, actions.size());
        assertEquals("com.example.frozen", actions.get(0).packageName);
        assertEquals(AppActionShortcutInfo.ACTION_CLEAR_CACHE, actions.get(0).action);
    }

    private static ApplicationItem item(String packageName,
                                        String label,
                                        long lastUsageTime,
                                        int openCount,
                                        boolean disabled,
                                        boolean stopped) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.label = label;
        item.userIds = new int[]{0};
        item.isInstalled = true;
        item.lastUsageTime = lastUsageTime;
        item.openCount = openCount;
        item.isDisabled = disabled;
        item.isStopped = stopped;
        return item;
    }

    private static ApplicationItem uninstalledItem(String packageName) {
        ApplicationItem item = item(packageName, packageName, 200, 0, false, false);
        item.isInstalled = false;
        return item;
    }
}
