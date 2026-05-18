// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;

public class AESCryptoTest {
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
