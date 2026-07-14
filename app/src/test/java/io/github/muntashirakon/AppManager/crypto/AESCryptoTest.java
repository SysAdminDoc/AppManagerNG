// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AESCryptoTest {
    private static SecretKey testKey() {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; ++i) {
            keyBytes[i] = (byte) (i * 7 + 1);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] testIv() {
        byte[] iv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        for (int i = 0; i < iv.length; ++i) {
            iv[i] = (byte) (i + 3);
        }
        return iv;
    }

    @Test
    public void streamingRoundTripHandlesLargeMultiBlockPayload() throws Exception {
        // A multi-megabyte payload spans far more than a single Cipher.doFinal buffer, standing in
        // for the large-app backups that make the JCE Cipher throw "GCM cipher cannot be reused".
        // AESCrypto's BouncyCastle GCMBlockCipher + CipherOutputStream streaming design must
        // round-trip it exactly; this test fails if the crypto path regresses to a buffered
        // single-shot cipher that cannot stream arbitrary sizes.
        byte[] plaintext = new byte[5 * 1024 * 1024 + 17];
        new Random(42).nextBytes(plaintext);

        ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();
        new AESCrypto(testIv(), testKey()).encrypt(new ByteArrayInputStream(plaintext), ciphertext);

        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        new AESCrypto(testIv(), testKey()).decrypt(new ByteArrayInputStream(ciphertext.toByteArray()), decrypted);

        assertArrayEquals(plaintext, decrypted.toByteArray());
    }

    @Test
    public void sameInstanceEncryptsSequentiallyWithoutCipherReuse() throws Exception {
        // Each encrypt()/decrypt() call must build a fresh GCM cipher. If the design regressed to a
        // cached/reused cipher instance, the second call would fail with a "cipher cannot be reused"
        // error — this exercises two sequential operations on one AESCrypto instance.
        AESCrypto crypto = new AESCrypto(testIv(), testKey());
        byte[] first = "first payload".getBytes();
        byte[] second = "second, different payload".getBytes();

        ByteArrayOutputStream ct1 = new ByteArrayOutputStream();
        crypto.encrypt(new ByteArrayInputStream(first), ct1);
        ByteArrayOutputStream ct2 = new ByteArrayOutputStream();
        crypto.encrypt(new ByteArrayInputStream(second), ct2);

        ByteArrayOutputStream pt1 = new ByteArrayOutputStream();
        crypto.decrypt(new ByteArrayInputStream(ct1.toByteArray()), pt1);
        ByteArrayOutputStream pt2 = new ByteArrayOutputStream();
        crypto.decrypt(new ByteArrayInputStream(ct2.toByteArray()), pt2);

        assertArrayEquals(first, pt1.toByteArray());
        assertArrayEquals(second, pt2.toByteArray());
    }

    @Test
    public void deriveIvForFileReturnsStableDistinctIvPerFile() {
        byte[] baseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        for (int i = 0; i < baseIv.length; ++i) {
            baseIv[i] = (byte) i;
        }

        byte[] first = AESCrypto.deriveIvForFile(baseIv, "base.apk");
        byte[] firstAgain = AESCrypto.deriveIvForFile(baseIv, "base.apk");
        byte[] second = AESCrypto.deriveIvForFile(baseIv, "split_config.arm64_v8a.apk");

        assertEquals(AESCrypto.GCM_IV_SIZE_BYTES, first.length);
        assertEquals(AESCrypto.GCM_IV_SIZE_BYTES, second.length);
        assertArrayEquals(first, firstAgain);
        assertFalse(Arrays.equals(first, second));
        assertFalse(Arrays.equals(baseIv, first));
    }

    @Test
    public void deriveIvForFileChangesWhenBackupIvChanges() {
        byte[] baseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        byte[] otherBaseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        otherBaseIv[0] = 1;

        byte[] first = AESCrypto.deriveIvForFile(baseIv, "meta_v5.am.json");
        byte[] second = AESCrypto.deriveIvForFile(otherBaseIv, "meta_v5.am.json");

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void deriveArchiveKeyReturnsStableDistinctKeyPerBackupIv() throws CryptoException {
        byte[] masterKey = new byte[32];
        byte[] baseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        byte[] otherBaseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        for (int i = 0; i < masterKey.length; ++i) {
            masterKey[i] = (byte) (i + 1);
        }
        otherBaseIv[0] = 1;

        byte[] first = AESCrypto.deriveArchiveKey(masterKey, baseIv, masterKey.length);
        byte[] firstAgain = AESCrypto.deriveArchiveKey(masterKey, baseIv, masterKey.length);
        byte[] second = AESCrypto.deriveArchiveKey(masterKey, otherBaseIv, masterKey.length);

        assertEquals(masterKey.length, first.length);
        assertArrayEquals(first, firstAgain);
        assertFalse(Arrays.equals(first, second));
        assertFalse(Arrays.equals(masterKey, first));
    }

    @Test
    public void deriveArchiveKeyPreservesMasterKeyLength() throws CryptoException {
        byte[] masterKey = new byte[16];
        byte[] baseIv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];

        byte[] derived = AESCrypto.deriveArchiveKey(masterKey, baseIv, masterKey.length);

        assertEquals(masterKey.length, derived.length);
    }

    @Test
    public void metadataUsesLegacyAuthTagOnlyForPreV4Versions() {
        assertTrue(AESCrypto.metadataUsesLegacyAuthTag(1));
        assertTrue(AESCrypto.metadataUsesLegacyAuthTag(3));
        assertFalse(AESCrypto.metadataUsesLegacyAuthTag(AESCrypto.FIRST_STRONG_AUTH_TAG_VERSION));
        assertFalse(AESCrypto.metadataUsesLegacyAuthTag(5));
        assertFalse(AESCrypto.metadataUsesLegacyAuthTag(AESCrypto.ARCHIVE_KEY_DERIVATION_VERSION));
    }

    @Test
    public void getCanonicalFileNameForIvUsesPlainBackupNameOnBothPaths() {
        assertEquals("data0.tar.gz.0", AESCrypto.getCanonicalFileNameForIv(true,
                "data0.tar.gz.0", "data0.tar.gz.0.aes", AESCrypto.AES_EXT));
        assertEquals("data0.tar.gz.0", AESCrypto.getCanonicalFileNameForIv(false,
                "data0.tar.gz.0.aes", "data0.tar.gz.0", AESCrypto.AES_EXT));
        assertEquals("meta_v5.am.json", AESCrypto.getCanonicalFileNameForIv(true,
                "meta_v5.am.json", "meta_v5.am.json.rsa", ".rsa"));
        assertEquals("meta_v5.am.json", AESCrypto.getCanonicalFileNameForIv(false,
                "meta_v5.am.json.rsa", "meta_v5.am.json", ".rsa"));
    }
}
