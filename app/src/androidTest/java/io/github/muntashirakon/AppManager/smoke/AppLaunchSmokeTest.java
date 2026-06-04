// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.smoke;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AppLaunchSmokeTest {
    private static final String TARGET_PACKAGE = "io.github.sysadmindoc.AppManagerNG.debug";
    private static final long LAUNCH_TIMEOUT_MILLIS = 15_000L;

    @Test
    public void mainAppListLaunches() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        Assert.assertNotNull("Launch intent must resolve for AppManagerNG debug.", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        context.startActivity(launchIntent);

        Assert.assertTrue("AppManagerNG did not open its main window.",
                device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MILLIS));
        Assert.assertNotNull("Main app list RecyclerView was not present.",
                device.wait(Until.findObject(By.res(TARGET_PACKAGE, "item_list")), LAUNCH_TIMEOUT_MILLIS));
    }
}
