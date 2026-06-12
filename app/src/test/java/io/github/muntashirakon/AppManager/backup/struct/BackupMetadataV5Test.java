// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

@RunWith(RobolectricTestRunner.class)
public class BackupMetadataV5Test {
    @Test
    public void constructorTrimsBackupName() {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(" nightly ");

        assertEquals("nightly", metadata.backupName);
    }

    @Test
    public void jsonCopyAndSerializationUseNormalizedBackupName() throws JSONException {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(metadataJson(" nightly "));

        assertEquals("nightly", metadata.backupName);
        assertEquals("nightly", new BackupMetadataV5.Metadata(metadata).backupName);
        assertEquals("nightly", metadata.serializeToJson().getString("backup_name"));
    }

    @Test
    public void blankBackupNameBecomesBaseBackup() throws JSONException {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(metadataJson("   "));
        BackupMetadataV5 backupMetadata = new BackupMetadataV5(info(), metadata);

        assertNull(metadata.backupName);
        assertTrue(metadata.serializeToJson().isNull("backup_name"));
        assertTrue(backupMetadata.isBaseBackup());
        assertEquals("", Backup.fromBackupMetadataV5(backupMetadata).backupName);
    }

    @Test
    public void namedBackupProjectsTrimmedNameToDatabaseRow() throws JSONException {
        BackupMetadataV5 backupMetadata = new BackupMetadataV5(info(),
                new BackupMetadataV5.Metadata(metadataJson(" nightly ")));

        assertFalse(backupMetadata.isBaseBackup());
        assertEquals("nightly", Backup.fromBackupMetadataV5(backupMetadata).backupName);
    }

    @Test
    public void metadataParsesAndSerializesProtectionAndNote() throws JSONException {
        JSONObject json = metadataJson("nightly")
                .put("protected_from_prune", true)
                .put("note", "  Before upgrade\r\nKeep  ");

        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(json);
        BackupMetadataV5.Metadata copy = new BackupMetadataV5.Metadata(metadata);
        JSONObject serialized = copy.serializeToJson();

        assertTrue(copy.protectedFromPrune);
        assertEquals("Before upgrade\nKeep", copy.note);
        assertTrue(serialized.getBoolean("protected_from_prune"));
        assertEquals("Before upgrade\nKeep", serialized.getString("note"));
    }

    @Test
    public void metadataDefaultsProtectionAndNoteForOldBackups() throws JSONException {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(metadataJson("nightly"));

        assertFalse(metadata.protectedFromPrune);
        assertNull(metadata.note);
    }

    private static BackupMetadataV5.Info info() {
        return new BackupMetadataV5.Info(1L, new BackupFlags(BackupFlags.BACKUP_APK_FILES), 0, TarUtils.TAR_GZIP,
                DigestUtils.SHA_256, CryptoUtils.MODE_NO_ENCRYPTION, null, null, null);
    }

    private static JSONObject metadataJson(String backupName) throws JSONException {
        return new JSONObject()
                .put("version", 7)
                .put("backup_name", backupName)
                .put("label", "DNSFilter")
                .put("package_name", "dnsfilter.android")
                .put("version_name", "1")
                .put("version_code", 1L)
                .put("data_dirs", new JSONArray())
                .put("is_system", false)
                .put("is_split_apk", false)
                .put("split_configs", new JSONArray())
                .put("has_rules", false)
                .put("apk_name", "base.apk")
                .put("instruction_set", "arm64")
                .put("key_store", false)
                .put("installer", JSONObject.NULL)
                .put("default_roles", new JSONArray());
    }
}
