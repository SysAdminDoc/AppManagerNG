// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.permissions.PermissionInspectorFilter.Filter;

public class PermissionInspectorFilterTest {

    @Test
    public void allKeepsEverything() {
        assertTrue(PermissionInspectorFilter.matches(Filter.ALL, 0, 0));
        assertTrue(PermissionInspectorFilter.matches(Filter.ALL, 5, 2));
    }

    @Test
    public void requestedKeepsOnlyRequestedGroups() {
        assertTrue(PermissionInspectorFilter.matches(Filter.REQUESTED, 1, 0));
        assertTrue(PermissionInspectorFilter.matches(Filter.REQUESTED, 3, 3));
        assertFalse(PermissionInspectorFilter.matches(Filter.REQUESTED, 0, 0));
    }

    @Test
    public void grantedKeepsOnlyGroupsWithAGrant() {
        assertTrue(PermissionInspectorFilter.matches(Filter.GRANTED, 4, 1));
        assertFalse(PermissionInspectorFilter.matches(Filter.GRANTED, 4, 0));
        assertFalse(PermissionInspectorFilter.matches(Filter.GRANTED, 0, 0));
    }

    @Test
    public void needsAttentionKeepsOnlyPartlyUngranted() {
        // Some app requests it but not all have granted -> actionable.
        assertTrue(PermissionInspectorFilter.matches(Filter.NEEDS_ATTENTION, 5, 2));
        assertTrue(PermissionInspectorFilter.matches(Filter.NEEDS_ATTENTION, 1, 0));
        // Fully granted or never requested -> nothing to review.
        assertFalse(PermissionInspectorFilter.matches(Filter.NEEDS_ATTENTION, 3, 3));
        assertFalse(PermissionInspectorFilter.matches(Filter.NEEDS_ATTENTION, 0, 0));
    }
}
