// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SystemDataBackupTest {
    @Test
    public void detectsSystemDataTokens() {
        assertTrue(SystemDataBackup.isSystemDataToken(SystemDataBackup.TOKEN_WIFI_MISC));
        assertFalse(SystemDataBackup.isSystemDataToken("/data/misc/wifi"));
        assertFalse(SystemDataBackup.hasSystemDataToken(new String[]{"/data/user/0/example"}));
        assertTrue(SystemDataBackup.hasSystemDataToken(new String[]{SystemDataBackup.TOKEN_BLUETOOTH_APEX}));
    }

    @Test
    public void resolvesUserScopedAccountSources() throws Throwable {
        assertEquals("/data/system_ce/10",
                SystemDataBackup.getSourcePath(SystemDataBackup.TOKEN_ACCOUNTS_CE, 10));
        assertEquals("/data/system_de/11",
                SystemDataBackup.getSourcePath(SystemDataBackup.TOKEN_ACCOUNTS_DE, 11));
    }

    @Test
    public void retainOnlySystemDataRemovesAppScopedContentFlags() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_SYSTEM_DATA
                | BackupFlags.BACKUP_APK_FILES
                | BackupFlags.BACKUP_INT_DATA
                | BackupFlags.BACKUP_NO_SIGNATURE_CHECK);

        SystemDataBackup.retainOnlySystemData(flags);

        assertTrue(flags.backupSystemData());
        assertFalse(flags.backupApkFiles());
        assertFalse(flags.backupInternalData());
        assertTrue(flags.skipSignatureCheck());
    }
}
