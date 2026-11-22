// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DhizukuBridgeTest {
    @Test
    public void platformSupportMatchesDhizukuDeclaredRange() {
        // Dhizuku-API 2.7.0 declares minSdk 23; Dhizuku 2.13.0 lists Android 6.0 through 17
        assertTrue(DhizukuBridge.isBelowMinimumSupportedAndroidVersion(22));
        assertFalse(DhizukuBridge.isBelowMinimumSupportedAndroidVersion(23));
        assertFalse(DhizukuBridge.isAboveDeclaredSupportedAndroidVersion(37));
        assertTrue(DhizukuBridge.isAboveDeclaredSupportedAndroidVersion(38));
    }

    @Test
    public void officialOwnerComponentMatchesDhizukuDeviceAdminReceiver() {
        assertTrue(DhizukuBridge.isOfficialOwnerComponent(
                DhizukuBridge.PACKAGE_NAME,
                DhizukuBridge.DEVICE_ADMIN_CLASS_NAME));
        assertTrue(DhizukuBridge.isOfficialOwnerComponent(
                DhizukuBridge.PACKAGE_NAME,
                ".server.DhizukuDAReceiver"));
        assertFalse(DhizukuBridge.isOfficialOwnerComponent(
                "com.example.owner",
                DhizukuBridge.DEVICE_ADMIN_CLASS_NAME));
        assertFalse(DhizukuBridge.isOfficialOwnerComponent(
                DhizukuBridge.PACKAGE_NAME,
                "com.rosan.dhizuku.server.OtherReceiver"));
    }

    @Test
    public void providerAuthorityMatchesOfficialAndForkedContracts() {
        assertEquals("com.rosan.dhizuku.server.provider",
                DhizukuBridge.getProviderAuthorityName(DhizukuBridge.PACKAGE_NAME));
        assertEquals("org.example.owner.dhizuku_server.provider",
                DhizukuBridge.getProviderAuthorityName("org.example.owner"));
    }

    @Test
    public void permissionRequestActionMatchesOfficialAndForkedContracts() {
        assertEquals("com.rosan.dhizuku.action.request.permission",
                DhizukuBridge.getActionRequestPermission(DhizukuBridge.PACKAGE_NAME));
        assertEquals("org.example.owner.action.REQUEST_DHIZUKU_PERMISSION",
                DhizukuBridge.getActionRequestPermission("org.example.owner"));
    }
}
