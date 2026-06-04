// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FreezeOptionTest {
    @Test
    public void activeStateExcludesFrozenAndArchivedApps() {
        assertTrue(FreezeOption.matches(0, false, "active", 0));
        assertFalse(FreezeOption.matches(FreezeOption.FREEZE_TYPE_DISABLED, false, "active", 0));
        assertFalse(FreezeOption.matches(0, true, "active", 0));
    }

    @Test
    public void archivedStateIsIndependentFromFreezeFlags() {
        assertTrue(FreezeOption.matches(0, true, "archived", 0));
        assertTrue(FreezeOption.matches(FreezeOption.FREEZE_TYPE_HIDDEN, true, "archived", 0));
        assertFalse(FreezeOption.matches(FreezeOption.FREEZE_TYPE_HIDDEN, false, "archived", 0));
        assertTrue(FreezeOption.matches(0, true, "unfrozen", 0));
        assertTrue(FreezeOption.matches(FreezeOption.FREEZE_TYPE_SUSPENDED, true,
                "with_flags", FreezeOption.FREEZE_TYPE_SUSPENDED));
    }
}
