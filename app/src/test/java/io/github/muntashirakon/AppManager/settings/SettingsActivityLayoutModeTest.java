// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.main.WindowWidthSizeClass;

public class SettingsActivityLayoutModeTest {
    @Test
    public void compactAndMediumWidthsUseSinglePane() {
        assertFalse(SettingsActivity.shouldUseDualPane(0));
        assertFalse(SettingsActivity.shouldUseDualPane(WindowWidthSizeClass.MEDIUM_MIN_DP));
        assertFalse(SettingsActivity.shouldUseDualPane(WindowWidthSizeClass.EXPANDED_MIN_DP - 1));
    }

    @Test
    public void expandedWidthsUseDualPane() {
        assertTrue(SettingsActivity.shouldUseDualPane(WindowWidthSizeClass.EXPANDED_MIN_DP));
        assertTrue(SettingsActivity.shouldUseDualPane(1280));
    }
}
