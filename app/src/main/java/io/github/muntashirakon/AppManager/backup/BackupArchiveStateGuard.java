// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.apk.installer.AppArchiveManager;

final class BackupArchiveStateGuard {
    @VisibleForTesting
    static final String BACKUP_REJECTION_PREFIX = "Archived package backup requires unarchive first: ";
    @VisibleForTesting
    static final String RESTORE_REJECTION_PREFIX = "Archived package restore requires unarchive first: ";

    private BackupArchiveStateGuard() {
    }

    static void requireBackupAllowed(@NonNull String packageName, @NonNull BackupFlags flags,
                                     @NonNull PackageInfo packageInfo) throws BackupException {
        requireBackupAllowed(packageName, flags, AppArchiveManager.isArchived(packageInfo));
    }

    static void requireRestoreAllowed(@NonNull String packageName, @NonNull BackupFlags requestedFlags,
                                      @Nullable PackageInfo installedPackageInfo) throws BackupException {
        requireRestoreAllowed(packageName, requestedFlags,
                installedPackageInfo != null && AppArchiveManager.isArchived(installedPackageInfo));
    }

    @VisibleForTesting
    static void requireBackupAllowed(@NonNull String packageName, @NonNull BackupFlags flags,
                                     boolean isArchived) throws BackupException {
        if (isArchived && touchesUnavailableArchivedPayload(flags)) {
            throw new BackupException(BACKUP_REJECTION_PREFIX + packageName
                    + ". Unarchive the app first, then run backup again.");
        }
    }

    @VisibleForTesting
    static void requireRestoreAllowed(@NonNull String packageName, @NonNull BackupFlags requestedFlags,
                                      boolean isArchived) throws BackupException {
        if (isArchived && touchesInstalledPackageState(requestedFlags)) {
            throw new BackupException(RESTORE_REJECTION_PREFIX + packageName
                    + " while the installed app is archived. Unarchive the app first, then restore again.");
        }
    }

    private static boolean touchesUnavailableArchivedPayload(@NonNull BackupFlags flags) {
        return flags.backupApkFiles() || flags.backupData();
    }

    private static boolean touchesInstalledPackageState(@NonNull BackupFlags flags) {
        return flags.backupApkFiles() || flags.backupData() || flags.backupExtras() || flags.backupRules();
    }
}
