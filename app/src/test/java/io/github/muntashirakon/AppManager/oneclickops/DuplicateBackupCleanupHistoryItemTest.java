// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.backup.BackupRetentionPolicy;
import io.github.muntashirakon.AppManager.db.entity.Backup;

public class DuplicateBackupCleanupHistoryItemTest {
    @Test
    public void serializeToJsonStoresStrategyRowsSummaryCountsAndDisplayLabels() throws Exception {
        OneClickOpsViewModel.DuplicateBackupEntry namedEntry =
                new OneClickOpsViewModel.DuplicateBackupEntry(backup("com.example.saved", 10, 42L,
                        " manual ", "backups/example"), 4096L);
        OneClickOpsViewModel.DuplicateBackupEntry baseEntry =
                new OneClickOpsViewModel.DuplicateBackupEntry(backup("com.example.base", 0, 7L,
                        "", "backups/base"), 2048L);
        DuplicateBackupCleanupHistoryItem item = new DuplicateBackupCleanupHistoryItem(
                "Delete duplicate backups",
                "Base backup",
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST_THEN_NEWEST,
                Arrays.asList(namedEntry, baseEntry),
                2,
                6144L);

        JSONObject json = item.serializeToJson();

        assertEquals(1, json.getInt("schema_version"));
        assertEquals("duplicate_backups", json.getString("cleanup_type"));
        assertEquals("Delete duplicate backups", json.getString("label"));
        assertEquals("LARGEST_THEN_NEWEST", json.getString("keep_strategy"));
        assertEquals(2, json.getInt("selected_count"));
        assertEquals(2, json.getInt("deleted_count"));
        assertEquals(6144L, json.getLong("reclaimed_bytes"));
        JSONObject row = json.getJSONArray("entries").getJSONObject(0);
        assertEquals("com.example.saved", row.getString("package_name"));
        assertEquals(10, row.getInt("user_id"));
        assertEquals(42L, row.getLong("version_code"));
        assertEquals("manual", row.getString("backup_name"));
        assertEquals("manual", row.getString("backup_label"));
        assertEquals("backups/example", row.getString("relative_dir"));
        assertEquals(4096L, row.getLong("size_bytes"));
        JSONObject baseRow = json.getJSONArray("entries").getJSONObject(1);
        assertEquals("com.example.base", baseRow.getString("package_name"));
        assertEquals("", baseRow.getString("backup_name"));
        assertEquals("Base backup", baseRow.getString("backup_label"));
        assertEquals("com.example.saved (manual) v42 u10", item.getTargetPreview().get(0));
        assertEquals("com.example.base (Base backup) v7 u0", item.getTargetPreview().get(1));
    }

    private static Backup backup(String packageName, int userId, long versionCode, String backupName,
                                 String relativeDir) {
        Backup backup = new Backup();
        backup.packageName = packageName;
        backup.userId = userId;
        backup.versionCode = versionCode;
        backup.backupName = backupName;
        backup.relativeDir = relativeDir;
        return backup;
    }
}
