// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.filters.FilterablePermissionInfo;

public class PermissionsOptionTest {
    @Test
    public void matchesGrantState() {
        FilterablePermissionInfo granted = permission(true, 0, "android");
        FilterablePermissionInfo denied = permission(false, 0, "android");

        assertTrue(PermissionsOption.matchesPermissionDetails(granted, "granted", 0));
        assertFalse(PermissionsOption.matchesPermissionDetails(granted, "denied", 0));
        assertTrue(PermissionsOption.matchesPermissionDetails(denied, "denied", 0));
        assertFalse(PermissionsOption.matchesPermissionDetails(denied, "granted", 0));
    }

    @Test
    public void matchesCustomPermissionSource() {
        assertFalse(PermissionsOption.matchesPermissionDetails(
                permission(false, 0, "android"), "custom", 0));
        assertTrue(PermissionsOption.matchesPermissionDetails(
                permission(false, 0, "com.example.provider"), "custom", 0));
        assertTrue(PermissionsOption.matchesPermissionDetails(
                permission(false, 0, null), "custom", 0));
    }

    @Test
    public void matchesFixedAndRawFlags() {
        FilterablePermissionInfo permission = permission(false,
                FLAG_PERMISSION_USER_SET | FLAG_PERMISSION_USER_FIXED, "android");

        assertTrue(PermissionsOption.matchesPermissionDetails(permission, "fixed", 0));
        assertTrue(PermissionsOption.matchesPermissionDetails(permission,
                "with_flags", FLAG_PERMISSION_USER_SET | FLAG_PERMISSION_USER_FIXED));
        assertFalse(PermissionsOption.matchesPermissionDetails(permission,
                "with_flags", FLAG_PERMISSION_SYSTEM_FIXED));
        assertTrue(PermissionsOption.matchesPermissionDetails(permission,
                "without_flags", FLAG_PERMISSION_SYSTEM_FIXED));
        assertFalse(PermissionsOption.matchesPermissionDetails(permission,
                "without_flags", FLAG_PERMISSION_USER_FIXED));
    }

    private static FilterablePermissionInfo permission(boolean granted, int permissionFlags, String sourcePackageName) {
        return new FilterablePermissionInfo("android.permission.CAMERA",
                granted, permissionFlags, true, sourcePackageName, 0, 0, 0);
    }
}
