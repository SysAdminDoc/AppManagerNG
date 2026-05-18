// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import android.content.pm.PermissionInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class ShizukuBridgeTest {
    @Test
    public void android17RiskIsDisabledBelowApi37() {
        assertFalse(ShizukuBridge.hasAndroid17CompatibilityRisk(36, "13.6.0", null));
        assertFalse(ShizukuBridge.hasAndroid17CompatibilityRisk(36, null, "13.7.0"));
    }

    @Test
    public void android17RiskStaysEnabledUntilKnownFixedVersionShips() {
        assertTrue(ShizukuBridge.hasAndroid17CompatibilityRisk(37, "13.6.0", null));
        assertTrue(ShizukuBridge.hasAndroid17CompatibilityRisk(37, null, null));
    }

    @Test
    public void android17RiskComparesInstalledVersionAgainstKnownFixedVersion() {
        assertTrue(ShizukuBridge.hasAndroid17CompatibilityRisk(37, "13.6.0", "13.7.0"));
        assertFalse(ShizukuBridge.hasAndroid17CompatibilityRisk(37, "13.7.0", "13.7.0"));
        assertFalse(ShizukuBridge.hasAndroid17CompatibilityRisk(37, "v13.7.1.r1200", "13.7.0"));
    }

    @Test
    public void compareVersionNormalizesPrefixesAndSuffixes() {
        assertEquals(0, ShizukuBridge.compareVersion("v13.6.0.r1086.2650830c", "13.6.0"));
        assertTrue(ShizukuBridge.compareVersion("13.6.1", "13.6.0") > 0);
        assertTrue(ShizukuBridge.compareVersion("13.5.4", "13.6.0") < 0);
    }

    @Test
    public void trustedWlanAutoStartRequiresAndroid13AndRecommendedManager() {
        assertFalse(ShizukuBridge.supportsTrustedWlanAutoStart(32, "13.6.0"));
        assertFalse(ShizukuBridge.supportsTrustedWlanAutoStart(33, null));
        assertFalse(ShizukuBridge.supportsTrustedWlanAutoStart(33, "13.5.4"));
        assertTrue(ShizukuBridge.supportsTrustedWlanAutoStart(33, "13.6.0"));
        assertTrue(ShizukuBridge.supportsTrustedWlanAutoStart(37, "v13.6.1.r1200"));
    }

    @Test
    public void trustedWlanAutoStartOfferIsOnlyForStoppedBinder() {
        assertTrue(ShizukuBridge.shouldOfferTrustedWlanAutoStart(33, "13.6.0", false));
        assertFalse(ShizukuBridge.shouldOfferTrustedWlanAutoStart(33, "13.6.0", true));
        assertFalse(ShizukuBridge.shouldOfferTrustedWlanAutoStart(33, "13.5.4", false));
    }

    @Test
    public void rootBackedDetectionOnlyTreatsUidZeroAsRoot() {
        assertTrue(ShizukuBridge.isRootBackedUid(0));
        assertFalse(ShizukuBridge.isRootBackedUid(2000));
        assertFalse(ShizukuBridge.isRootBackedUid(1000));
    }

    @Test
    public void autoModeAvoidsRootBackedShizukuOnlyWhenAdbIsAvailable() {
        assertTrue(ShizukuBridge.shouldAvoidRootBackedShizukuInAuto(true, 0, true));
        assertFalse(ShizukuBridge.shouldAvoidRootBackedShizukuInAuto(true, 0, false));
        assertFalse(ShizukuBridge.shouldAvoidRootBackedShizukuInAuto(true, 2000, true));
        assertFalse(ShizukuBridge.shouldAvoidRootBackedShizukuInAuto(false, 0, true));
    }

    @Test
    public void oemCompatibilityWarningRequiresShizuku136Runtime() {
        assertNull(ShizukuBridge.getOemCompatibilityWarning("Infinix", "GT 20 Pro", "x6871",
                "x6871", 35, "13.5.4", 13, "XOS", "mt6895",
                "", "", "", ""));
        assertNull(ShizukuBridge.getOemCompatibilityWarning("Infinix", "GT 20 Pro", "x6871",
                "x6871", 35, null, ShizukuBridge.MIN_USER_SERVICE_VERSION, "XOS", "mt6895",
                "", "", "", ""));
    }

    @Test
    public void oemCompatibilityWarningDetectsTranssionAndroid15() {
        ShizukuBridge.OemCompatibilityWarning warning = ShizukuBridge.getOemCompatibilityWarning("Infinix",
                "GT 20 Pro", "x6871", "x6871", 35, "13.6.0", 13,
                "XOS-15", "mt6895", "", "", "", "");
        assertNotNull(warning);
        assertEquals("transsion", warning.reasonCode);
        assertEquals(R.string.shizuku_oem_compat_banner_transsion, warning.bannerTextRes);
        assertEquals(ShizukuBridge.PINNED_SAFE_MANAGER_VERSION, warning.fallbackVersion);
    }

    @Test
    public void oemCompatibilityWarningDetectsMediatekAndroid15() {
        ShizukuBridge.OemCompatibilityWarning warning = ShizukuBridge.getOemCompatibilityWarning("Xiaomi",
                "Example", "device", "product", 35, "v13.6.0.r1086", 13,
                "", "mt6789", "", "", "", "Dimensity 1080");
        assertNotNull(warning);
        assertEquals("mediatek", warning.reasonCode);
        assertEquals(R.string.shizuku_oem_compat_banner_mediatek, warning.bannerTextRes);
    }

    @Test
    public void oemCompatibilityWarningDetectsPixel9Android16() {
        ShizukuBridge.OemCompatibilityWarning warning = ShizukuBridge.getOemCompatibilityWarning("Google",
                "Pixel 9 Pro", "caiman", "caiman", 36, null, 13,
                "", "zuma", "", "", "", "");
        assertNotNull(warning);
        assertEquals("pixel9", warning.reasonCode);
        assertEquals(R.string.shizuku_oem_compat_banner_pixel9, warning.bannerTextRes);
    }

    @Test
    public void oemCompatibilityWarningIgnoresOldPlatforms() {
        assertNull(ShizukuBridge.getOemCompatibilityWarning("Infinix", "GT 20 Pro", "x6871",
                "x6871", 34, "13.6.0", 13, "XOS", "mt6895",
                "", "", "", ""));
        assertNull(ShizukuBridge.getOemCompatibilityWarning("Google", "Pixel 9 Pro", "caiman",
                "caiman", 35, "13.6.0", 13, "", "zuma",
                "", "", "", ""));
    }

    @Test
    public void clearDataWarningClassifiesAuthorizationSensitivePackages() {
        assertEquals(R.string.shizuku_clear_data_self_warning,
                ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                        "io.github.sysadmindoc.AppManagerNG", false));
        assertEquals(R.string.shizuku_clear_data_manager_warning,
                ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                        ShizukuBridge.PACKAGE_NAME, false));
        assertEquals(R.string.shizuku_clear_data_client_warning,
                ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                        "com.example.shizuku.client", true));
        assertEquals(0, ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                "com.example.regular", false));
    }

    @Test
    public void clearDataWarningUsesDetectedShizukuManagerPackage() {
        assertEquals(R.string.shizuku_clear_data_manager_warning,
                ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                        "dev.hidden.shizuku", "dev.hidden.shizuku", false));
        assertEquals(R.string.shizuku_clear_data_manager_warning,
                ShizukuBridge.getClearDataAuthorizationWarning("io.github.sysadmindoc.AppManagerNG",
                        ShizukuBridge.PACKAGE_NAME, "dev.hidden.shizuku", false));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void managerPackageDetectionPrefersPermissionOwner() {
        PermissionInfo apiPermission = new PermissionInfo();
        apiPermission.packageName = "dev.hidden.shizuku";
        PermissionInfo legacyPermission = new PermissionInfo();
        legacyPermission.packageName = "moe.legacy.shizuku";

        assertEquals("dev.hidden.shizuku", ShizukuBridge.getManagerPackageName(apiPermission, legacyPermission));
        assertEquals("moe.legacy.shizuku", ShizukuBridge.getManagerPackageName(null, legacyPermission));
        assertEquals(ShizukuBridge.PACKAGE_NAME, ShizukuBridge.getManagerPackageName(null, null));
    }

    @Test
    public void shizukuProviderDetectionRequiresProviderClassName() {
        assertTrue(ShizukuBridge.isShizukuProviderName(ShizukuBridge.PROVIDER_CLASS_NAME));
        assertFalse(ShizukuBridge.isShizukuProviderName("com.example.Provider"));
        assertFalse(ShizukuBridge.isShizukuProviderName(null));
    }

    @Test
    public void permissionRevokedRequiresPreviousGrantAndMissingGrantAfterClearData() {
        assertTrue(ShizukuBridge.wasPermissionRevokedAfterClearData(true, false));
        assertFalse(ShizukuBridge.wasPermissionRevokedAfterClearData(true, true));
        assertFalse(ShizukuBridge.wasPermissionRevokedAfterClearData(false, false));
    }

    @Test
    public void displayVersionPrefersInstalledVersionWhenPresent() {
        assertEquals("13.6.0", ShizukuBridge.getDisplayVersion("13.6.0", 14, true));
    }

    @Test
    public void displayVersionFallsBackToBinderApiVersionWhenHidden() {
        // "Hide Shizuku" mode: package query returned null but binder is alive
        // and reports an API version. Display should surface the API version
        // with a (hidden) suffix so the user knows the install is healthy and
        // the missing package label isn't a real missing-install signal.
        assertEquals("api 14 (hidden)", ShizukuBridge.getDisplayVersion(null, 14, true));
        assertEquals("api 14 (hidden)", ShizukuBridge.getDisplayVersion("", 14, true));
    }

    @Test
    public void displayVersionReturnsNullWhenNoPackageAndNoBinder() {
        assertNull(ShizukuBridge.getDisplayVersion(null, 0, false));
        assertNull(ShizukuBridge.getDisplayVersion(null, 14, false));
        assertNull(ShizukuBridge.getDisplayVersion(null, 0, true));
    }
}
