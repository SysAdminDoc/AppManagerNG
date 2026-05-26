// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * EI-04 — chip-row filter logic for {@link PermissionAppsViewModel}. Pure-JVM
 * so it runs without Robolectric.
 */
public class PermissionAppsViewModelFilterTest {

    private static PermissionAppsViewModel.AppRow row(String pkg, boolean isSystem, boolean granted) {
        return new PermissionAppsViewModel.AppRow(pkg, pkg, null, isSystem, granted, true);
    }

    @Test
    public void allFilterReturnsEverything() {
        List<PermissionAppsViewModel.AppRow> input = Arrays.asList(
                row("a.user.granted", false, true),
                row("b.user.denied", false, false),
                row("c.system.granted", true, true));
        List<PermissionAppsViewModel.AppRow> out =
                PermissionAppsViewModel.applyFilter(input, PermissionAppsViewModel.Filter.ALL);
        assertEquals(3, out.size());
    }

    @Test
    public void allFilterReturnsADefensiveCopy() {
        List<PermissionAppsViewModel.AppRow> input = Collections.singletonList(
                row("only", false, true));
        List<PermissionAppsViewModel.AppRow> out =
                PermissionAppsViewModel.applyFilter(input, PermissionAppsViewModel.Filter.ALL);
        assertNotSame("filter result must be a fresh list to avoid downstream aliasing",
                input, out);
    }

    @Test
    public void userAppsFilterDropsSystemRows() {
        List<PermissionAppsViewModel.AppRow> input = Arrays.asList(
                row("user.a", false, true),
                row("user.b", false, false),
                row("system.c", true, true));
        List<PermissionAppsViewModel.AppRow> out =
                PermissionAppsViewModel.applyFilter(input, PermissionAppsViewModel.Filter.USER_APPS);
        assertEquals(2, out.size());
        for (PermissionAppsViewModel.AppRow row : out) {
            assertTrue(!row.isSystem);
        }
    }

    @Test
    public void grantedFilterKeepsOnlyGrantedRows() {
        List<PermissionAppsViewModel.AppRow> input = Arrays.asList(
                row("user.granted", false, true),
                row("user.denied", false, false),
                row("system.granted", true, true),
                row("system.denied", true, false));
        List<PermissionAppsViewModel.AppRow> out =
                PermissionAppsViewModel.applyFilter(input, PermissionAppsViewModel.Filter.GRANTED);
        assertEquals(2, out.size());
        for (PermissionAppsViewModel.AppRow row : out) {
            assertTrue(row.anyGranted);
        }
    }

    @Test
    public void emptySourceStaysEmpty() {
        List<PermissionAppsViewModel.AppRow> out = PermissionAppsViewModel.applyFilter(
                Collections.emptyList(), PermissionAppsViewModel.Filter.USER_APPS);
        assertTrue(out.isEmpty());
    }
}
