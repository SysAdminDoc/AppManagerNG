// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

public class ImportExportKeyStoreDialogFragmentTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void backupExistingKeyStoreMovesLiveFileBeforeImportOverwrite() throws Exception {
        File keyStore = temporaryFolder.newFile("am_keystore.bks");
        byte[] originalBytes = new byte[]{1, 2, 3, 4};
        Files.write(keyStore.toPath(), originalBytes);

        File backup = ImportExportKeyStoreDialogFragment.backupExistingKeyStore(keyStore);

        assertNotNull(backup);
        assertFalse(keyStore.exists());
        assertTrue(backup.exists());
        assertArrayEquals(originalBytes, Files.readAllBytes(backup.toPath()));
    }

    @Test
    public void restoreKeyStoreBackupReplacesPartialImportWithOriginal() throws Exception {
        File keyStore = temporaryFolder.newFile("am_keystore.bks");
        byte[] originalBytes = new byte[]{9, 8, 7, 6};
        Files.write(keyStore.toPath(), originalBytes);
        File backup = ImportExportKeyStoreDialogFragment.backupExistingKeyStore(keyStore);
        Files.write(keyStore.toPath(), new byte[]{0, 1});

        ImportExportKeyStoreDialogFragment.restoreKeyStoreBackup(keyStore, backup);

        assertTrue(keyStore.exists());
        assertFalse(backup.exists());
        assertArrayEquals(originalBytes, Files.readAllBytes(keyStore.toPath()));
    }

    @Test
    public void restoreKeyStoreBackupDeletesPartialImportWhenNoOriginalExists() throws Exception {
        File keyStore = new File(temporaryFolder.getRoot(), "am_keystore.bks");
        Files.write(keyStore.toPath(), new byte[]{0, 1});

        ImportExportKeyStoreDialogFragment.restoreKeyStoreBackup(keyStore, null);

        assertFalse(keyStore.exists());
    }
}
