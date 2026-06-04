// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

public class LeftoverCleanupHistoryItemTest {
    @Test
    public void serializeToJsonStoresSelectedRowsAndSummaryCounts() throws Exception {
        OneClickOpsViewModel.LeftoverEntry entry = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(new File("Android/data/com.example.gone"),
                        "com.example.gone", LeftoverScanner.KIND_DATA),
                128L);
        LeftoverCleanupHistoryItem item = new LeftoverCleanupHistoryItem(
                "Detect leftover folders", Collections.singletonList(entry), 1, 128L);

        JSONObject json = item.serializeToJson();

        assertEquals(1, json.getInt("schema_version"));
        assertEquals("leftover_folders", json.getString("cleanup_type"));
        assertEquals("Detect leftover folders", json.getString("label"));
        assertEquals(1, json.getInt("selected_count"));
        assertEquals(1, json.getInt("deleted_count"));
        assertEquals(128L, json.getLong("reclaimed_bytes"));
        JSONObject row = json.getJSONArray("entries").getJSONObject(0);
        assertEquals("com.example.gone", row.getString("package_name"));
        assertEquals("data", row.getString("kind"));
        assertEquals(128L, row.getLong("size_bytes"));
        assertEquals(entry.leftover.path.getPath(), row.getString("path"));
    }
}
