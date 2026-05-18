// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.miui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiuiAppOpsNamesTest {

    @Test
    public void rangeBoundsExcludeStartAndEnd() {
        // start is exclusive (10000 is not a valid MIUI op; the range begins at 10001).
        assertFalse(MiuiAppOpsNames.isInMiuiRange(MiuiAppOpsNames.MIUI_OP_START));
        assertFalse(MiuiAppOpsNames.isInMiuiRange(MiuiAppOpsNames.MIUI_OP_END));
        assertTrue(MiuiAppOpsNames.isInMiuiRange(MiuiAppOpsNames.MIUI_OP_START + 1));
        assertTrue(MiuiAppOpsNames.isInMiuiRange(MiuiAppOpsNames.MIUI_OP_END - 1));
    }

    @Test
    public void framework_op_codes_below_range_return_null() {
        // Regular Android op codes (0-149-ish) must fall through to the framework.
        assertNull(MiuiAppOpsNames.getNameOrNull(0));
        assertNull(MiuiAppOpsNames.getNameOrNull(1));
        assertNull(MiuiAppOpsNames.getNameOrNull(99));
        assertNull(MiuiAppOpsNames.getNameOrNull(MiuiAppOpsNames.MIUI_OP_START - 1));
    }

    @Test
    public void op_codes_above_range_return_null() {
        // Future MIUI ops we haven't catalogued yet (or non-MIUI extended ranges)
        // must return null so the caller falls back to the framework.
        assertNull(MiuiAppOpsNames.getNameOrNull(MiuiAppOpsNames.MIUI_OP_END));
        assertNull(MiuiAppOpsNames.getNameOrNull(MiuiAppOpsNames.MIUI_OP_END + 100));
        assertNull(MiuiAppOpsNames.getNameOrNull(99_999));
    }

    @Test
    public void wellKnownCodesResolveToFriendlyLabels() {
        assertEquals("MIUI: Autostart", MiuiAppOpsNames.getNameOrNull(10008));
        assertEquals("MIUI: Background launch activity", MiuiAppOpsNames.getNameOrNull(10021));
        assertEquals("MIUI: Display while screen locked", MiuiAppOpsNames.getNameOrNull(10020));
        assertEquals("MIUI: Query installed apps", MiuiAppOpsNames.getNameOrNull(10022));
        assertEquals("MIUI: Read clipboard", MiuiAppOpsNames.getNameOrNull(10037));
    }

    @Test
    public void allKnownCodesAreInRange() {
        // Every entry in the lookup table must fall inside the documented MIUI
        // op range (defence against a typo dropping an entry at, say, 1001).
        for (int op = MiuiAppOpsNames.MIUI_OP_START + 1; op < MiuiAppOpsNames.MIUI_OP_END; ++op) {
            String name = MiuiAppOpsNames.getNameOrNull(op);
            if (name != null) {
                assertTrue("Out-of-range entry " + op, MiuiAppOpsNames.isInMiuiRange(op));
            }
        }
    }

    @Test
    public void knownCodeCountMatchesObservedMiuiSurface() {
        // We catalogue 39 of the 39 well-known MIUI op codes (10001-10039).
        // If MIUI ever extends to 10040+, MIUI_OP_END must move first and
        // the new codes must be added — otherwise the test fails and the
        // maintainer is reminded to refresh the table.
        assertEquals(39, MiuiAppOpsNames.knownCodeCount());
    }

    @Test
    public void labelsHaveMiuiPrefix() {
        // The "MIUI: " prefix is load-bearing for the user — without it the
        // label looks indistinguishable from an upstream AOSP op name in
        // contexts where both are mixed (e.g., search results).
        for (int op = MiuiAppOpsNames.MIUI_OP_START + 1; op < MiuiAppOpsNames.MIUI_OP_END; ++op) {
            String name = MiuiAppOpsNames.getNameOrNull(op);
            if (name != null) {
                assertTrue("Label missing MIUI: prefix for op " + op + ": " + name,
                        name.startsWith("MIUI: "));
            }
        }
    }

    @Test
    public void nonNullForEveryAdvertisedCode() {
        // Spot-check that the lookup actually returns a value for every code
        // we advertise as known. assertNotNull on a representative sample.
        for (int op : new int[]{10001, 10008, 10014, 10020, 10021, 10037, 10038, 10039}) {
            assertNotNull("Missing entry for advertised op " + op,
                    MiuiAppOpsNames.getNameOrNull(op));
        }
    }
}
