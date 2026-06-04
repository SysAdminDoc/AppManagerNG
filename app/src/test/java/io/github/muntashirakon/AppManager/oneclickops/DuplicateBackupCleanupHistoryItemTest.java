// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import io.github.muntashirakon.AppManager.backup.BackupRetentionPolicy;
import io.github.muntashirakon.AppManager.db.entity.Backup;

public class DuplicateBackupCleanupHistoryItemTest {
    @Test
    public void serializeToJsonStoresStrategyRowsAndSummaryCounts() throws Exception {
        Backup backup = new Backup();
        backup.packageName = "com.example.saved";
        backup.userId = 10;
        backup.versionCode = 42L;
        backup.backupName = "manual";
        backup.relativeDir = "backups/example";
        OneClickOpsViewModel.DuplicateBackupEntry entry =
                new OneClickOpsViewModel.DuplicateBackupEntry(backup, 4096L);
        DuplicateBackupCleanupHistoryItem item = new DuplicateBackupCleanupHistoryItem(
                "Delete duplicate backups",
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST_THEN_NEWEST,
                Collections.singletonList(entry),
                1,
                4096L);

        JSONObject json = item.serializeToJson();

        assertEquals(1, json.getInt("schema_version"));
        assertEquals("duplicate_backups", json.getString("cleanup_type"));
        assertEquals("Delete duplicate backups", json.getString("label"));
        assertEquals("LARGEST_THEN_NEWEST", json.getString("keep_strategy"));
        assertEquals(1, json.getInt("selected_count"));
        assertEquals(1, json.getInt("deleted_count"));
        assertEquals(4096L, json.getLong("reclaimed_bytes"));
        JSONObject row = json.getJSONArray("entries").getJSONObject(0);
        assertEquals("com.example.saved", row.getString("package_name"));
        assertEquals(10, row.getInt("user_id"));
        assertEquals(42L, row.getLong("version_code"));
        assertEquals("manual", row.getString("backup_name"));
        assertEquals("backups/example", row.getString("relative_dir"));
        assertEquals(4096L, row.getLong("size_bytes"));
        assertEquals("com.example.saved v42 u10", item.getTargetPreview().get(0));
    }
}
