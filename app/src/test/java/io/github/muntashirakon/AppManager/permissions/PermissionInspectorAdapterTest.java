// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PermissionInspectorAdapterTest {
    private static final int SECTION = 0;
    private static final int ROW = 1;

    @Test
    public void eachSectionGetsOneHeaderBeforeItsRows() {
        List<PermissionInspectorViewModel.Row> rows = new ArrayList<>();
        rows.add(new PermissionInspectorViewModel.Row(PermissionGroupCatalog.all().get(0), 4, 1));
        rows.add(new PermissionInspectorViewModel.Row(PermissionGroupCatalog.all().get(1), 3, 3));
        rows.add(special("overlay", 5, 2, true));
        rows.add(special("write_settings", 2, 0, true));
        rows.add(special("unrestricted_data", 9, 0, false));
        PermissionInspectorAdapter adapter = new PermissionInspectorAdapter(row -> {
        });
        adapter.submit(rows);

        assertEquals(8, adapter.getItemCount());
        int[] expected = {SECTION, ROW, ROW, SECTION, ROW, ROW, SECTION, ROW};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("item " + i, expected[i], adapter.getItemViewType(i));
        }
    }

    @Test
    public void aFilteredOutSectionLeavesNoEmptyHeader() {
        List<PermissionInspectorViewModel.Row> rows = new ArrayList<>();
        rows.add(special("overlay", 5, 2, true));
        PermissionInspectorAdapter adapter = new PermissionInspectorAdapter(row -> {
        });
        adapter.submit(rows);
        assertEquals(2, adapter.getItemCount());
        adapter.submit(new ArrayList<>());
        assertEquals(0, adapter.getItemCount());
    }

    private static PermissionInspectorViewModel.Row special(String id, int requested, int granted, boolean known) {
        return new PermissionInspectorViewModel.Row(SpecialAccessCatalog.findById(id), requested, granted, known);
    }
}
