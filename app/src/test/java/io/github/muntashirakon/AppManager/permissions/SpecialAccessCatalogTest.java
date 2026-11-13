// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SpecialAccessCatalogTest {
    private static final String PKG = "com.example.app";

    @Test
    public void everyCategoryFromTheIssueIsListedOnce() {
        List<String> ids = ids(SpecialAccessCatalog.all());
        assertEquals(Arrays.asList("overlay", "write_settings", "pip", "full_screen_intent", "accessibility",
                "notification_listener", "battery_optimization", "background_activity", "exact_alarms",
                "unrestricted_data", "background_data", "install_unknown", "all_files", "manage_media",
                "usage_access", "dnd_access", "device_admin", "vpn"), ids);
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    public void entriesNewerThanTheDeviceAreLeftOut() {
        Set<String> marshmallow = new HashSet<>(ids(SpecialAccessCatalog.available(Build.VERSION_CODES.M)));
        assertTrue(marshmallow.contains("overlay"));
        assertTrue(marshmallow.contains("battery_optimization"));
        assertTrue(marshmallow.contains("dnd_access"));
        assertFalse(marshmallow.contains("pip"));
        assertFalse(marshmallow.contains("install_unknown"));
        assertFalse(marshmallow.contains("unrestricted_data"));
        assertFalse(marshmallow.contains("all_files"));
        assertFalse(marshmallow.contains("exact_alarms"));
        assertFalse(marshmallow.contains("full_screen_intent"));

        Set<String> lollipop = new HashSet<>(ids(SpecialAccessCatalog.available(Build.VERSION_CODES.LOLLIPOP)));
        assertEquals(new HashSet<>(Arrays.asList("accessibility", "notification_listener", "usage_access",
                "device_admin", "vpn")), lollipop);

        assertEquals(18, SpecialAccessCatalog.available(Build.VERSION_CODES.UPSIDE_DOWN_CAKE).size());
    }

    @Test
    public void findByIdOnlyReturnsEntriesThisDeviceHas() {
        assertEquals("overlay", SpecialAccessCatalog.findById("overlay").id);
        assertNull(SpecialAccessCatalog.findById("nope"));
        assertNull(SpecialAccessCatalog.findById(null));
    }

    @Test
    @Config(sdk = 30)
    public void findByIdSkipsEntriesTooNewForTheDevice() {
        assertNull(SpecialAccessCatalog.findById("exact_alarms"));
        assertEquals("all_files", SpecialAccessCatalog.findById("all_files").id);
    }

    @Test
    public void pictureInPictureNeedsActivitiesAndAnActivityThatSupportsIt() {
        SpecialAccessCatalog.Entry pip = SpecialAccessCatalog.findById("pip");
        assertTrue((pip.packageFlags() & android.content.pm.PackageManager.GET_ACTIVITIES) != 0);
        PackageInfo pi = app();
        assertFalse(pip.appliesTo(pi, null));
        ActivityInfo plain = new ActivityInfo();
        ActivityInfo player = new ActivityInfo();
        player.flags = SpecialAccessCatalog.FLAG_SUPPORTS_PICTURE_IN_PICTURE;
        pi.activities = new ActivityInfo[]{plain};
        assertFalse(pip.appliesTo(pi, null));
        pi.activities = new ActivityInfo[]{plain, player};
        assertTrue(pip.appliesTo(pi, null));
    }

    @Test
    public void useExactAlarmIsAlwaysAllowedAndCantBeSwitched() throws Exception {
        SpecialAccessCatalog.Entry alarms = SpecialAccessCatalog.findById("exact_alarms");
        PackageInfo pi = app("android.permission.USE_EXACT_ALARM");
        pi.requestedPermissionsFlags = new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED};
        assertTrue(alarms.appliesTo(pi, null));
        // Read without touching app ops, which a test can't reach
        assertEquals(SpecialAccess.GRANTED, alarms.probe.status(pi, null));
        assertFalse(alarms.canChange(pi, null));
        assertFalse(alarms.appliesTo(app(Manifest.permission.INTERNET), null));
    }

    @Test
    public void appOpEntriesApplyToAppsThatAskForThePermission() {
        SpecialAccessCatalog.Entry overlay = SpecialAccessCatalog.findById("overlay");
        assertTrue(overlay.appliesTo(app(Manifest.permission.SYSTEM_ALERT_WINDOW), null));
        assertFalse(overlay.appliesTo(app(Manifest.permission.CAMERA), null));
        PackageInfo noApplication = app(Manifest.permission.SYSTEM_ALERT_WINDOW);
        noApplication.applicationInfo = null;
        assertFalse(overlay.appliesTo(noApplication, null));
    }

    @Test
    public void settingsTryTheAppsOwnPageThenTheListThenAppInfo() {
        List<Intent> intents = SpecialAccessCatalog.findById("overlay").settings(app(), null);
        assertEquals(3, intents.size());
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intents.get(0).getAction());
        assertEquals("package:" + PKG, String.valueOf(intents.get(0).getData()));
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intents.get(1).getAction());
        assertNull(intents.get(1).getData());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents.get(2).getAction());
    }

    @Test
    public void everyEntryEndsWithAppInfo() {
        for (SpecialAccessCatalog.Entry entry : SpecialAccessCatalog.all()) {
            if (entry.id.equals("notification_listener")) {
                continue; // Reads the listener components from the snapshot
            }
            List<Intent> intents = entry.settings(app(), null);
            assertEquals(entry.id, Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    intents.get(intents.size() - 1).getAction());
        }
    }

    @Test
    public void packagesOfReadsFlattenedComponentLists() {
        assertEquals(new HashSet<>(Arrays.asList("com.a", "com.b")), SpecialAccessSnapshot.packagesOf(
                "com.a/.Svc:com.b/com.b.Other:com.a/.Second"));
        assertTrue(SpecialAccessSnapshot.packagesOf(null).isEmpty());
        assertTrue(SpecialAccessSnapshot.packagesOf("").isEmpty());
        // The Do Not Disturb list stores bare package names
        assertEquals(new HashSet<>(Arrays.asList("com.a", "com.b")), SpecialAccessSnapshot.packagesOf(
                "com.a::com.b"));
    }

    @Test
    public void theDoNotDisturbSettingIsOnlyTrustedWhereAndroidStillWritesIt() {
        assertEquals(new HashSet<>(Arrays.asList("com.a", "com.b")),
                SpecialAccessSnapshot.dndAccessFromSetting(Build.VERSION_CODES.R, "com.a::com.b"));
        assertTrue(SpecialAccessSnapshot.dndAccessFromSetting(Build.VERSION_CODES.R, null).isEmpty());
        // Android 12 stopped updating it, so whatever is there is left over from an upgrade
        assertNull(SpecialAccessSnapshot.dndAccessFromSetting(Build.VERSION_CODES.S, "com.a"));
        assertNull(SpecialAccessSnapshot.dndAccessFromSetting(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, null));
    }

    private static List<String> ids(List<SpecialAccessCatalog.Entry> entries) {
        String[] ids = new String[entries.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = entries.get(i).id;
        return Arrays.asList(ids);
    }

    private static PackageInfo app(String... permissions) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = PKG;
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = PKG;
        pi.applicationInfo.uid = 10123;
        pi.requestedPermissions = permissions;
        pi.requestedPermissionsFlags = new int[permissions.length];
        return pi;
    }
}
