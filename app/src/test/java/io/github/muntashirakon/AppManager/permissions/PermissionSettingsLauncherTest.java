// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.permission.PermUtils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PermissionSettingsLauncherTest {
    private static final String PKG = "com.example.app";

    @Test
    public void picksTheFirstPermissionWithItsOwnPage() {
        Map<String, PermUtils.SettingItem> items = new HashMap<>();
        items.put("b", new PermUtils.SettingItem("action.B"));
        items.put("c", new PermUtils.SettingItem("action.C"));
        assertEquals("b", PermissionSettingsLauncher.firstWithSettingsPage(Arrays.asList("a", "b", "c"), items));
        assertNull(PermissionSettingsLauncher.firstWithSettingsPage(Collections.singletonList("a"), items));
    }

    @Test
    public void notificationsOpenTheAppsNotificationPageThenAppInfo() {
        List<Intent> intents = PermissionSettingsLauncher.candidates(PKG,
                Collections.singletonList(Manifest.permission.POST_NOTIFICATIONS));
        assertEquals(2, intents.size());
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intents.get(0).getAction());
        assertEquals(PKG, intents.get(0).getStringExtra(Settings.EXTRA_APP_PACKAGE));
        assertAppInfo(intents.get(1));
    }

    @Test
    public void aRuntimePermissionWithoutItsOwnPageOpensAppInfo() {
        List<Intent> intents = PermissionSettingsLauncher.candidates(PKG,
                Arrays.asList(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO));
        assertEquals(1, intents.size());
        assertAppInfo(intents.get(0));
    }

    @Test
    public void fallsBackWhenTheFirstPageIsMissing() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).checkActivities(true);
        List<Intent> intents = PermissionSettingsLauncher.candidates(PKG,
                Collections.singletonList(Manifest.permission.POST_NOTIFICATIONS));
        // Only app info exists on this device, like a ROM that dropped the notification page
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(intents.get(1), settingsActivity());

        assertTrue(PermissionSettingsLauncher.launch(app, intents));
        Intent started = shadowOf(app).getNextStartedActivity();
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.getAction());
        // Started from outside an activity, so it needs its own task or Android refuses it
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void reportsFailureWhenNoPageOpens() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).checkActivities(true);
        assertFalse(PermissionSettingsLauncher.launch(app, PermissionSettingsLauncher.candidates(PKG,
                Collections.singletonList(Manifest.permission.CAMERA))));
        assertNull(shadowOf(app).getNextStartedActivity());
    }

    private static void assertAppInfo(Intent intent) {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
        assertEquals("package:" + PKG, String.valueOf(intent.getData()));
    }

    private static ResolveInfo settingsActivity() {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.android.settings";
        info.activityInfo.name = "com.android.settings.AppInfo";
        return info;
    }
}
