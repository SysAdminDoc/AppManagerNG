// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class OemBloatRiskTableTest {
    @Test
    public void samsungSmartSuggestionsFallbackRequiresOneUi85() {
        OemBloatRiskTable.UninstallFallback fallback = OemBloatRiskTable.getUninstallFallback(
                "com.samsung.android.smartsuggestions", "Samsung", "UP1A", "80500");

        assertNotNull(fallback);
        assertEquals(R.string.oem_bloat_action_samsung_smartsuggestions_oneui85, fallback.messageRes);
        assertNull(OemBloatRiskTable.getUninstallFallback(
                "com.samsung.android.smartsuggestions", "Samsung", "UP1A", "80000"));
    }

    @Test
    public void miuiCoreFallsBackToDisableOnXiaomiFamily() {
        assertNotNull(OemBloatRiskTable.getUninstallFallback("com.miui.core",
                "Xiaomi", "OS1.0.24", ""));
        assertNotNull(OemBloatRiskTable.getUninstallFallback("com.miui.core",
                "POCO", "OS1.0.24", ""));
        assertNull(OemBloatRiskTable.getUninstallFallback("com.miui.core",
                "Google", "AP4A", ""));
    }

    @Test
    public void oplusGuardedPackagesFallbackByManufacturerOrBuildFingerprint() {
        assertNotNull(OemBloatRiskTable.getUninstallFallback("com.oplus.apprecover",
                "OnePlus", "CPH2581_15.0.0", ""));
        assertNotNull(OemBloatRiskTable.getUninstallFallback("com.coloros.sauhelper",
                "Unknown", "ColorOS_15_CPH2581", ""));
        assertNull(OemBloatRiskTable.getUninstallFallback("com.oplus.games",
                "OnePlus", "CPH2581_15.0.0", ""));
    }
}
