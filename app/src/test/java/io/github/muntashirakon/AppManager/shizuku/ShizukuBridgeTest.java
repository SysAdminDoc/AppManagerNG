// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}
