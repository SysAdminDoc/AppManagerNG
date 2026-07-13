// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class SnapshotCryptoTest {
    private static final byte[] PLAINTEXT =
            "the quick brown fox jumps over the lazy dog · snapshot payload".getBytes(StandardCharsets.UTF_8);

    @Test
    public void roundTripsWithCorrectPassphrase() throws Exception {
        byte[] envelope = SnapshotCrypto.encrypt(PLAINTEXT, "correct horse battery".toCharArray());
        assertTrue(SnapshotCrypto.looksEncrypted(envelope));
        assertArrayEquals(PLAINTEXT, SnapshotCrypto.decrypt(envelope, "correct horse battery".toCharArray()));
    }

    @Test
    public void distinctEnvelopesForSamePlaintext() throws Exception {
        // Random salt + nonce per call → ciphertext must differ.
        byte[] a = SnapshotCrypto.encrypt(PLAINTEXT, "pw".toCharArray());
        byte[] b = SnapshotCrypto.encrypt(PLAINTEXT, "pw".toCharArray());
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    public void wrongPassphraseFailsBeforeReturningPlaintext() throws Exception {
        byte[] envelope = SnapshotCrypto.encrypt(PLAINTEXT, "right".toCharArray());
        assertThrows(SnapshotImportException.class,
                () -> SnapshotCrypto.decrypt(envelope, "wrong".toCharArray()));
    }

    @Test
    public void tamperedCiphertextFails() throws Exception {
        byte[] envelope = SnapshotCrypto.encrypt(PLAINTEXT, "pw".toCharArray());
        envelope[envelope.length - 1] ^= 0x01; // flip a bit in the last ciphertext/tag byte
        assertThrows(SnapshotImportException.class,
                () -> SnapshotCrypto.decrypt(envelope, "pw".toCharArray()));
    }

    @Test
    public void tamperedHeaderFails() throws Exception {
        byte[] envelope = SnapshotCrypto.encrypt(PLAINTEXT, "pw".toCharArray());
        // Flip a byte inside the salt (part of the authenticated header) → key + AAD mismatch.
        envelope[SnapshotCrypto.MAGIC.length + 2] ^= 0x01;
        assertThrows(SnapshotImportException.class,
                () -> SnapshotCrypto.decrypt(envelope, "pw".toCharArray()));
    }

    @Test
    public void looksEncryptedDistinguishesEnvelopeFromZip() throws Exception {
        assertTrue(SnapshotCrypto.looksEncrypted(SnapshotCrypto.encrypt(PLAINTEXT, "pw".toCharArray())));
        assertFalse(SnapshotCrypto.looksEncrypted("PKplain zip".getBytes(StandardCharsets.UTF_8)));
        assertFalse(SnapshotCrypto.looksEncrypted(new byte[]{1, 2, 3}));
        assertFalse(SnapshotCrypto.looksEncrypted(null));
    }

    @Test
    public void decryptRejectsNonEnvelopeInput() {
        assertThrows(SnapshotImportException.class,
                () -> SnapshotCrypto.decrypt("not an envelope".getBytes(StandardCharsets.UTF_8),
                        "pw".toCharArray()));
    }
}
