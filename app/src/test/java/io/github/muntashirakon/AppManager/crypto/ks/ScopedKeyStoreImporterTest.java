// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class ScopedKeyStoreImporterTest {

    @Test
    public void isAmOwnedAliasRecognizesAllKnownAliases() {
        assertTrue(ScopedKeyStoreImporter.isAmOwnedAlias("adb_rsa"));
        assertTrue(ScopedKeyStoreImporter.isAmOwnedAlias("signing_key"));
        assertTrue(ScopedKeyStoreImporter.isAmOwnedAlias("backup_rsa"));
        assertTrue(ScopedKeyStoreImporter.isAmOwnedAlias("backup_aes"));
        assertTrue(ScopedKeyStoreImporter.isAmOwnedAlias("backup_ecc"));
    }

    @Test
    public void isAmOwnedAliasRejectsForeignAliases() {
        assertFalse(ScopedKeyStoreImporter.isAmOwnedAlias("my_custom_key"));
        assertFalse(ScopedKeyStoreImporter.isAmOwnedAlias(""));
        assertFalse(ScopedKeyStoreImporter.isAmOwnedAlias("adb_rsa_2"));
        assertFalse(ScopedKeyStoreImporter.isAmOwnedAlias("ADB_RSA"));
    }

    @Test
    public void amOwnedAliasSetContainsExactlyFiveEntries() {
        assertEquals(5, ScopedKeyStoreImporter.AM_OWNED_ALIASES.size());
        assertEquals(new HashSet<>(Arrays.asList(
                "adb_rsa", "signing_key", "backup_rsa", "backup_aes", "backup_ecc"
        )), ScopedKeyStoreImporter.AM_OWNED_ALIASES);
    }

    @Test
    public void importPreviewHasImportableAliases() {
        ScopedKeyStoreImporter.ImportPreview preview = new ScopedKeyStoreImporter.ImportPreview(
                Arrays.asList("backup_aes", "backup_rsa"),
                Collections.singletonList("backup_aes"),
                Collections.singletonList("foreign_key"));
        assertTrue(preview.hasImportableAliases());
    }

    @Test
    public void importPreviewNoImportableAliases() {
        ScopedKeyStoreImporter.ImportPreview preview = new ScopedKeyStoreImporter.ImportPreview(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("foreign_key"));
        assertFalse(preview.hasImportableAliases());
    }

    @Test
    public void importResultAllFailed() {
        ScopedKeyStoreImporter.ImportResult result = new ScopedKeyStoreImporter.ImportResult(
                Collections.emptyList(),
                Arrays.asList("backup_aes", "backup_rsa"));
        assertTrue(result.allFailed());
        assertTrue(result.hasFailures());
    }

    @Test
    public void importResultPartialSuccess() {
        ScopedKeyStoreImporter.ImportResult result = new ScopedKeyStoreImporter.ImportResult(
                Collections.singletonList("backup_aes"),
                Collections.singletonList("backup_rsa"));
        assertFalse(result.allFailed());
        assertTrue(result.hasFailures());
    }

    @Test
    public void importResultFullSuccess() {
        ScopedKeyStoreImporter.ImportResult result = new ScopedKeyStoreImporter.ImportResult(
                Arrays.asList("backup_aes", "backup_rsa"),
                Collections.emptyList());
        assertFalse(result.allFailed());
        assertFalse(result.hasFailures());
    }
}
