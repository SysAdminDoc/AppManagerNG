// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
