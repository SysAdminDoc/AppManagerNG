// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupArchiveStateGuardTest {
    private static final String PACKAGE_NAME = "com.example.archived";

    @Test
    public void backupRejectsArchivedApkPayload() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES);

        BackupException exception = assertThrows(BackupException.class,
                () -> BackupArchiveStateGuard.requireBackupAllowed(PACKAGE_NAME, flags, true));

        assertTrue(exception.getMessage().startsWith(BackupArchiveStateGuard.BACKUP_REJECTION_PREFIX));
    }

    @Test
    public void backupRejectsArchivedDataPayload() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_INT_DATA);

        BackupException exception = assertThrows(BackupException.class,
                () -> BackupArchiveStateGuard.requireBackupAllowed(PACKAGE_NAME, flags, true));

        assertTrue(exception.getMessage().startsWith(BackupArchiveStateGuard.BACKUP_REJECTION_PREFIX));
    }

    @Test
    public void backupAllowsArchivedExtrasOnly() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_EXTRAS | BackupFlags.BACKUP_RULES);

        BackupArchiveStateGuard.requireBackupAllowed(PACKAGE_NAME, flags, true);
    }

    @Test
    public void backupAllowsArchivedRulesOnly() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_RULES);

        BackupArchiveStateGuard.requireBackupAllowed(PACKAGE_NAME, flags, true);
    }

    @Test
    public void backupAllowsUnarchivedApkAndDataPayload() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_EXT_DATA);

        BackupArchiveStateGuard.requireBackupAllowed(PACKAGE_NAME, flags, false);
    }

    @Test
    public void restoreRejectsArchivedInstalledPackage() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA);

        BackupException exception = assertThrows(BackupException.class,
                () -> BackupArchiveStateGuard.requireRestoreAllowed(PACKAGE_NAME, flags, true));

        assertTrue(exception.getMessage().startsWith(BackupArchiveStateGuard.RESTORE_REJECTION_PREFIX));
    }

    @Test
    public void restoreRejectsArchivedExtrasOnly() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_EXTRAS);

        BackupException exception = assertThrows(BackupException.class,
                () -> BackupArchiveStateGuard.requireRestoreAllowed(PACKAGE_NAME, flags, true));

        assertTrue(exception.getMessage().startsWith(BackupArchiveStateGuard.RESTORE_REJECTION_PREFIX));
    }

    @Test
    public void restoreAllowsArchivedInstalledPackageWhenNothingSelected() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_NOTHING);

        BackupArchiveStateGuard.requireRestoreAllowed(PACKAGE_NAME, flags, true);
    }

    @Test
    public void restoreAllowsUnarchivedInstalledPackage() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA);

        BackupArchiveStateGuard.requireRestoreAllowed(PACKAGE_NAME, flags, false);
    }

    @Test
    public void restoreAllowsUnavailablePackage() throws BackupException {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA);

        BackupArchiveStateGuard.requireRestoreAllowed(PACKAGE_NAME, flags, null);
    }
}
