// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import io.github.muntashirakon.AppManager.snapshot.SnapshotBundle;
import io.github.muntashirakon.AppManager.snapshot.SnapshotCrypto;

/**
 * Choosing Encrypt with a blank passphrase used to write the snapshot in plaintext, because only a
 * non-empty passphrase selected the encrypted writer. Encrypt now needs a passphrase with at least
 * one non-whitespace character; plaintext is only ever the explicit Save unencrypted choice.
 */
@RunWith(RobolectricTestRunner.class)
public class SnapshotExportPassphraseTest {
    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void blankOrWhitespaceOnlyPassphrasesCannotEncrypt() {
        assertFalse(PrivacyPreferences.isUsablePassphrase(null));
        assertFalse(PrivacyPreferences.isUsablePassphrase(new char[0]));
        assertFalse(PrivacyPreferences.isUsablePassphrase(" \t\n ".toCharArray()));
    }

    @Test
    public void anyVisibleCharacterMakesAUsablePassphrase() {
        assertTrue(PrivacyPreferences.isUsablePassphrase("x".toCharArray()));
        assertTrue(PrivacyPreferences.isUsablePassphrase("  correct horse  ".toCharArray()));
    }

    @Test
    public void aBlankEncryptPassphraseWritesNothing() throws Exception {
        File target = new File(mTemp.getRoot(), "blank.amsnap");

        try {
            PrivacyPreferences.writeSnapshot(ApplicationProvider.getApplicationContext(), Uri.fromFile(target),
                    "   ".toCharArray());
            fail("A blank passphrase must not produce a snapshot");
        } catch (IOException expected) {
            // Refused before the target was opened.
        }
        assertFalse(target.exists());
    }

    @Test
    public void anEncryptExportCannotBeReadAsPlaintext() throws Exception {
        File target = new File(mTemp.getRoot(), "encrypted.amsnap");

        PrivacyPreferences.writeSnapshot(ApplicationProvider.getApplicationContext(), Uri.fromFile(target),
                "correct horse".toCharArray());

        byte[] written = Files.readAllBytes(target.toPath());
        assertTrue("the export must carry the encrypted envelope header", SnapshotCrypto.looksEncrypted(written));
        // Without the passphrase the envelope is recognised as encrypted, never parsed as a plain bundle.
        try (InputStream in = new FileInputStream(target)) {
            assertThrows(SnapshotBundle.PassphraseRequiredException.class,
                    () -> SnapshotBundle.readManifestOnly(in, null));
        }
    }
}
