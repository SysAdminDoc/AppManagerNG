// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowSystemProperties;
import org.robolectric.util.ReflectionHelpers;

import io.github.muntashirakon.AppManager.miui.MiuiVersionInfo;

/**
 * HyperOS keeps the MIUI version name, so it reads as MIUI, but its build number isn't a MIUI one.
 * Reading it as one threw, and an install Android aborted on such a phone then crashed instead of
 * reporting how it ended.
 */
@RunWith(RobolectricTestRunner.class)
public class MiuiUtilsTest {
    @After
    public void forgetTheRom() {
        ReflectionHelpers.setStaticField(MiuiUtils.class, "sMiuiVersionInfo", null);
    }

    @Test
    public void aHyperOsBuildHasNoMiuiVersion() {
        rom("V816", "OS2.0.1.0.VNAMIXM");

        assertNull(MiuiUtils.getMiuiVersionInfo());
        assertFalse(MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0"));
    }

    @Test
    public void aStableMiuiBuildStillReadsItsVersion() {
        rom("V140", "V14.0.3.0.TKFMIXM");

        MiuiVersionInfo info = MiuiUtils.getMiuiVersionInfo();

        assertEquals("14.0", info.getMiuiVersion());
        assertEquals("TKFMIXM", info.letters);
        assertTrue(MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0"));
    }

    @Test
    public void aStableBuildNumberWithoutItsRomLettersIsStillStable() {
        rom("V125", "V12.5.1.0");

        assertEquals("12.5", MiuiUtils.getMiuiVersionInfo().getMiuiVersion());
        assertTrue(MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0"));
    }

    @Test
    public void aOnePartStableBuildNumberReadsAsThatVersion() {
        rom("V14", "V14");

        assertEquals("14", MiuiUtils.getMiuiVersionInfo().getMiuiVersion());
        assertTrue(MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0"));
    }

    @Test
    public void aMiuiLookingBuildNumberIsNotMiuiWithoutTheVersionName() {
        ShadowBuild.setVersionIncremental("V14.0.3.0.TKFMIXM");

        assertNull(MiuiUtils.getMiuiVersionInfo());
        assertFalse(MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0"));
    }

    private static void rom(@NonNull String miuiVersionName, @NonNull String buildNumber) {
        ShadowSystemProperties.override("ro.miui.ui.version.name", miuiVersionName);
        ShadowBuild.setVersionIncremental(buildNumber);
    }
}
