// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Issue 51: Expand all and Collapse all act on the rows the tab shows right now. A row a search hides keeps the
 * state it had.
 */
public class ComponentExpansionStateTest {
    private final List<String> mVisible = Arrays.asList("a.One", "a.Two", "a.Three");

    @Test
    public void expandAllOpensEveryVisibleRow() {
        ComponentExpansionState state = new ComponentExpansionState();

        state.expandAll(mVisible);

        for (String name : mVisible) {
            assertTrue(name, state.isExpanded(name));
        }
        assertTrue(state.areAllExpanded(mVisible));
        assertFalse("a hidden row stays closed", state.isExpanded("a.Hidden"));
    }

    @Test
    public void collapseAllClosesOnlyTheVisibleRows() {
        ComponentExpansionState state = new ComponentExpansionState();
        state.expandAll(Arrays.asList("a.One", "a.Two", "a.Three", "a.Hidden"));

        state.collapseAll(mVisible);

        for (String name : mVisible) {
            assertFalse(name, state.isExpanded(name));
        }
        assertTrue("a row the search hides keeps its state", state.isExpanded("a.Hidden"));
        assertFalse(state.areAllExpanded(mVisible));
    }

    @Test
    public void oneOpenRowIsNotAllOfThem() {
        ComponentExpansionState state = new ComponentExpansionState();

        state.toggle("a.Two");

        assertTrue(state.isExpanded("a.Two"));
        assertFalse(state.areAllExpanded(mVisible));
        state.toggle("a.Two");
        assertFalse(state.isExpanded("a.Two"));
    }

    @Test
    public void anEmptyTabHasNothingToCollapse() {
        assertFalse(new ComponentExpansionState().areAllExpanded(Collections.emptyList()));
    }
}
