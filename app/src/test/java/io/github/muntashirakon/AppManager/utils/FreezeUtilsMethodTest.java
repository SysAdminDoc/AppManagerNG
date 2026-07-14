// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FreezeUtilsMethodTest {
    @Test
    public void acceptsEachDefinedFreezeMethod() {
        assertTrue(FreezeUtils.isValidFreezeMethod(FreezeUtils.FREEZE_DISABLE));
        assertTrue(FreezeUtils.isValidFreezeMethod(FreezeUtils.FREEZE_SUSPEND));
        assertTrue(FreezeUtils.isValidFreezeMethod(FreezeUtils.FREEZE_HIDE));
        assertTrue(FreezeUtils.isValidFreezeMethod(FreezeUtils.FREEZE_ADV_SUSPEND));
    }

    @Test
    public void rejectsOutOfRangeOrCombinedValues() {
        assertFalse(FreezeUtils.isValidFreezeMethod(0));
        assertFalse(FreezeUtils.isValidFreezeMethod(3)); // combined, not a single method
        assertFalse(FreezeUtils.isValidFreezeMethod(999));
        assertFalse(FreezeUtils.isValidFreezeMethod(-1));
    }
}
