// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreinstalledOemResolverTest {
    @Test
    public void explicitOemsTakePrecedenceOverInference() {
        assertArrayEquals(new String[]{"Samsung", "Xiaomi"},
                PreinstalledOemResolver.resolve(new String[]{" samsung ", "Xiaomi"},
                        "com.oppo.sauhelper", "oem", "OPPO system updater"));
    }

    @Test
    public void packagePrefixesInferKnownPreinstallOems() {
        assertArrayEquals(new String[]{"Samsung"},
                PreinstalledOemResolver.resolve(null, "com.samsung.android.app.spage",
                        "oem", null));
        assertArrayEquals(new String[]{"Xiaomi"},
                PreinstalledOemResolver.resolve(null, "com.miui.analytics",
                        "oem", null));
    }

    @Test
    public void descriptionInferenceRequiresProvenanceContext() {
        assertArrayEquals(new String[]{"Xiaomi", "Huawei"},
                PreinstalledOemResolver.resolve(null, "com.android.providers.partnerbookmarks",
                        "aosp", "Some OEMs such as Huawei and Xiaomi preinstall this provider."));
        assertArrayEquals(new String[0],
                PreinstalledOemResolver.resolve(null, "com.example.viewer",
                        "misc", "Samsung is mentioned in user-facing help text only."));
    }

    @Test
    public void descriptionContextGateRecognizesVendorLanguage() {
        assertTrue(PreinstalledOemResolver.shouldInspectDescription("aosp",
                "Used by several OEM firmware images."));
        assertTrue(PreinstalledOemResolver.shouldInspectDescription("oem",
                "Samsung launcher helper."));
        assertFalse(PreinstalledOemResolver.shouldInspectDescription("misc",
                "Plain helper with no provenance signal."));
    }
}
