// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppInfoViewModelCleartextTest {
    @Test
    public void shouldWarnCleartextDeprecationWhenCleartextHasNoNetworkConfig() {
        assertTrue(AppInfoViewModel.shouldWarnCleartextDeprecation(true, 0));
    }

    @Test
    public void shouldNotWarnWhenCleartextDisabledOrNetworkConfigPresent() {
        assertFalse(AppInfoViewModel.shouldWarnCleartextDeprecation(false, 0));
        assertFalse(AppInfoViewModel.shouldWarnCleartextDeprecation(true, 42));
    }
}
