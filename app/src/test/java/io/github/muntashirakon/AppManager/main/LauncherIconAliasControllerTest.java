// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.main.LauncherIconAliasPlan.LauncherIconStyle;

public class LauncherIconAliasControllerTest {

    @Test
    public void classNameFor_mapsEachStyleToItsManifestComponent() {
        assertEquals(LauncherIconAliasController.CLASS_DEFAULT,
                LauncherIconAliasController.classNameFor(LauncherIconStyle.DEFAULT));
        assertEquals(LauncherIconAliasController.CLASS_NG_MARK,
                LauncherIconAliasController.classNameFor(LauncherIconStyle.NG_MARK));
        assertEquals(LauncherIconAliasController.CLASS_NEUTRAL,
                LauncherIconAliasController.classNameFor(LauncherIconStyle.NEUTRAL_SQUARE));
        assertEquals(LauncherIconAliasController.CLASS_MONOCHROME,
                LauncherIconAliasController.classNameFor(LauncherIconStyle.MONOCHROME));
    }

    @Test
    public void classNameFor_everyStyleHasADistinctComponent() {
        Set<String> seen = new HashSet<>();
        for (LauncherIconStyle style : LauncherIconStyle.values()) {
            String name = LauncherIconAliasController.classNameFor(style);
            assertEquals("Component must be fully qualified", true,
                    name.startsWith("io.github.muntashirakon.AppManager.main."));
            assertEquals("Each style maps to a unique component", true, seen.add(name));
        }
        assertEquals(4, seen.size());
    }

    @Test
    public void classNames_defaultTargetsSplashActivity() {
        // The DEFAULT style is the real SplashActivity launcher entry, not an alias.
        assertEquals("io.github.muntashirakon.AppManager.main.SplashActivity",
                LauncherIconAliasController.CLASS_DEFAULT);
        assertNotEquals(LauncherIconAliasController.CLASS_DEFAULT,
                LauncherIconAliasController.CLASS_NG_MARK);
    }
}
