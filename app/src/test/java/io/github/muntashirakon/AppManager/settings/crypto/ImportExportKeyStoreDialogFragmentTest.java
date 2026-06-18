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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Test
    public void importFlowConfirmsReplacementAndUsesSpecificFailureCopy() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/"
                + "ImportExportKeyStoreDialogFragment.java");

        assertTrue("Keystore import should confirm before replacing live keys",
                source.contains("R.string.confirm_import_keystore"));
        assertTrue("Canceled confirmation should clear the typed import password",
                source.contains("setNegativeButton(R.string.cancel, (dialog, which) -> Utils.clearChars(importPassword))"));
        assertTrue("Canceled confirmation via back/outside should clear the typed import password",
                source.contains("setOnCancelListener(dialog -> Utils.clearChars(importPassword))"));
        assertTrue("Keystore export failures should use specific copy",
                source.contains("R.string.keystore_export_failed"));
        assertTrue("Keystore import failures should use specific copy",
                source.contains("R.string.keystore_import_failed"));
        assertTrue("Keystore import failures should be logged for diagnostics",
                source.contains("Log.e(TAG, \"Could not import AppManagerNG keystore.\", e);"));
        assertTrue("Keystore rollback should only run after the live file is backed up or determined absent",
                source.contains("boolean backupPrepared = false")
                        && source.contains("backupPrepared = true")
                        && source.contains("if (backupPrepared)"));
        assertFalse("Keystore import/export should not fall back to a vague failure toast",
                source.contains("UIUtils.displayShortToast(R.string.failed)"));
    }

    private static String readRepoFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/res"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
