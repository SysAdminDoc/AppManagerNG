// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

final class BackupChecksumPolicy {
    private BackupChecksumPolicy() {
    }

    static void requireCryptographicChecksum(@NonNull BackupMetadataV5.Info backupInfo)
            throws BackupException {
        if (DigestUtils.CRC32.equals(backupInfo.checksumAlgo)) {
            throw new BackupException("Backup uses non-cryptographic CRC32 checksums; "
                    + "verification and restore are refused.");
        }
    }
}
