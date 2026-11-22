// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Issue 50: the Permissions tab showed only the raw name and the system's long description. Settings calls
 * READ_MEDIA_AUDIO "Music and audio", and a few special permissions came with no description at all.
 */
@RunWith(RobolectricTestRunner.class)
public class PermissionTextsTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void aMediaPermissionGetsTheNameOfItsGroupInSettings() {
        addGroup("android.permission-group.READ_MEDIA_AURAL", "Music and audio");

        assertEquals("Music and audio", text(PermissionTexts.shortLabel(mContext,
                permission("android.permission.READ_MEDIA_AUDIO", null, null))));
    }

    @Test
    public void aPermissionThatNamesItsGroupUsesThatGroup() {
        addGroup("com.example.permission-group.SYNC", "Sync your data");

        assertEquals("Sync your data", text(PermissionTexts.shortLabel(mContext,
                permission("com.example.permission.SYNC", null, "com.example.permission-group.SYNC"))));
    }

    @Test
    public void aSpecialPermissionGetsTheNameOfItsSettingsPage() {
        assertEquals("Display over other apps", text(PermissionTexts.shortLabel(mContext,
                permission("android.permission.SYSTEM_ALERT_WINDOW", null, null))));
        assertEquals("All files access", text(PermissionTexts.shortLabel(mContext,
                permission("android.permission.MANAGE_EXTERNAL_STORAGE", null, null))));
        assertEquals("Alarms and reminders", text(PermissionTexts.shortLabel(mContext,
                permission("android.permission.SCHEDULE_EXACT_ALARM", null, null))));
    }

    @Test
    public void aPermissionWithOnlyItsOwnLabelShowsThatLabel() {
        PermissionInfo info = permission("com.example.permission.C2D", null, null);
        info.nonLocalizedLabel = "Receive cloud messages";

        assertEquals("Receive cloud messages", text(PermissionTexts.shortLabel(mContext, info)));
    }

    @Test
    public void aPermissionWithNothingToShowHasNoLabel() {
        PermissionInfo info = permission("com.example.permission.PRIVATE", null, null);
        info.nonLocalizedLabel = "com.example.permission.PRIVATE";

        assertNull(PermissionTexts.shortLabel(mContext, info));
    }

    @Test
    public void theSystemDescriptionWinsWhenThereIsOne() {
        PermissionInfo info = permission("android.permission.MANAGE_EXTERNAL_STORAGE",
                "Allows the app to read, modify and delete all files.", null);

        assertEquals("Allows the app to read, modify and delete all files.",
                text(PermissionTexts.description(mContext, info)));
    }

    @Test
    public void everySpecialPermissionHasADescriptionOfItsOwn() {
        String[] special = {
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.WRITE_SETTINGS",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.USE_EXACT_ALARM",
        };
        for (String name : special) {
            CharSequence description = PermissionTexts.description(mContext, permission(name, null, null));
            assertNotNull(name, description);
            assertNotNull(name, PermissionTexts.shortLabel(mContext, permission(name, null, null)));
        }
        assertEquals(mContext.getString(io.github.muntashirakon.AppManager.R.string.perm_desc_request_install_packages),
                text(PermissionTexts.description(mContext, permission(special[2], "  ", null))));
    }

    @Test
    public void anUnknownPermissionWithoutADescriptionHasNone() {
        assertNull(PermissionTexts.description(mContext, permission("com.example.permission.X", null, null)));
    }

    private static void addGroup(@NonNull String name, @NonNull String label) {
        PermissionGroupInfo group = new PermissionGroupInfo();
        group.name = name;
        group.packageName = "android";
        group.nonLocalizedLabel = label;
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addPermissionGroupInfo(group);
    }

    @NonNull
    private static PermissionInfo permission(@NonNull String name, @Nullable String description,
                                             @Nullable String group) {
        PermissionInfo info = new PermissionInfo();
        info.name = name;
        info.packageName = "android";
        info.nonLocalizedDescription = description;
        info.group = group;
        return info;
    }

    @Nullable
    private static String text(@Nullable CharSequence text) {
        return text == null ? null : text.toString();
    }
}
