// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.smoke;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AppLaunchSmokeTest {
    private static final String ONE_CLICK_OPS_ACTIVITY =
            "io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity";

    @Test
    public void mainAppListLaunches() {
        UiDevice device = SmokeTestUtils.device();
        SmokeTestUtils.launchMain(device);

        SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "item_list"),
                "main app list RecyclerView");
    }

    @Test
    public void mainBatchSelectionSurfaceLaunches() {
        UiDevice device = SmokeTestUtils.device();
        SmokeTestUtils.launchMain(device);
        UiObject2 appList = SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "item_list"),
                "main app list RecyclerView");
        UiObject2 firstRow = SmokeTestUtils.waitForFirstChild(appList, "first app-list row");

        firstRow.longClick();

        SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "selection_view"),
                "batch selection action surface");
    }

    @Test
    public void oneClickOpsRuleAndBackupSurfaceLaunches() {
        UiDevice device = SmokeTestUtils.device();
        SmokeTestUtils.launchTargetActivity(device, ONE_CLICK_OPS_ACTIVITY);

        SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "scrollView"),
                "one-click operations scroll surface");
        SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "one_click_ops_notice"),
                "one-click operations notice");
        SmokeTestUtils.waitForObject(device,
                By.res(SmokeTestUtils.TARGET_PACKAGE, "container"),
                "one-click operations action container");
    }
}
