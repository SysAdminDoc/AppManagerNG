// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class BackupTest {
    private Path testDir;
    private String previousVolumePath;

    @Before
    public void setUp() throws IOException {
        previousVolumePath = Prefs.Storage.getVolumePath().toString();
        testDir = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("backup-entity-test");
        Prefs.Storage.setVolumePath(testDir.getUri().toString());
    }

    @After
    public void tearDown() {
        Prefs.Storage.setVolumePath(previousVolumePath);
        testDir.delete();
    }

    @Test
    public void legacyMetadataProjectionTrimsBackupName() throws IOException {
        Backup backup = Backup.fromBackupMetadata(metadataV2(" nightly "));

        assertEquals("nightly", backup.backupName);
    }

    @Test
    public void legacyMetadataProjectionTreatsBlankBackupNameAsBaseBackup() throws IOException {
        Backup backup = Backup.fromBackupMetadata(metadataV2("   "));

        assertEquals("", backup.backupName);
    }

    private static BackupMetadataV2 metadataV2(String backupName) throws IOException {
        BackupMetadataV2 metadata = new BackupMetadataV2();
        metadata.packageName = "dnsfilter.android";
        metadata.backupName = backupName;
        metadata.label = "DNSFilter";
        metadata.versionName = "1";
        metadata.versionCode = 1L;
        metadata.isSystem = false;
        metadata.isSplitApk = false;
        metadata.hasRules = false;
        metadata.backupTime = 1L;
        metadata.crypto = CryptoUtils.MODE_NO_ENCRYPTION;
        metadata.version = MetadataManager.getCurrentBackupMetaVersion();
        metadata.flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES);
        metadata.userId = 0;
        metadata.tarType = TarUtils.TAR_GZIP;
        metadata.keyStore = false;
        metadata.installer = null;
        metadata.backupItem = backupItem();
        return metadata;
    }

    private static BackupItems.BackupItem backupItem() throws IOException {
        Prefs.Storage.getAppManagerDirectory()
                .findOrCreateDirectory("dnsfilter.android")
                .findOrCreateDirectory(BackupUtils.getV4BackupName(0, "nightly"));
        return BackupItems.findBackupItem(BackupUtils.getV4RelativeDir(0, "nightly", "dnsfilter.android"));
    }
}
