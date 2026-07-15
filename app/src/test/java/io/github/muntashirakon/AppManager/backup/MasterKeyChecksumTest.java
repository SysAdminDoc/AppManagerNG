// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.utils.DigestUtils;

public class MasterKeyChecksumTest {
    private static final byte[] NON_UTF8_MASTER_KEY = {
            0x00, 0x41, (byte) 0x80, (byte) 0xC3, 0x28, (byte) 0xFF
    };

    @Test
    public void currentMetadataHashesRawBinaryKeyBytes() {
        String checksum = MasterKeyChecksum.calculate(DigestUtils.SHA_256,
                MasterKeyChecksum.RAW_BYTES_VERSION, NON_UTF8_MASTER_KEY);

        assertEquals(DigestUtils.getHexDigest(DigestUtils.SHA_256, NON_UTF8_MASTER_KEY), checksum);
    }

    @Test
    public void legacyMetadataReproducesLossyStringRoundTrip() {
        String legacyChecksum = MasterKeyChecksum.calculateLegacy(DigestUtils.SHA_256,
                NON_UTF8_MASTER_KEY, StandardCharsets.UTF_8);
        String expected = DigestUtils.getHexDigest(DigestUtils.SHA_256,
                new String(NON_UTF8_MASTER_KEY, StandardCharsets.UTF_8)
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, legacyChecksum);
        assertNotEquals(DigestUtils.getHexDigest(DigestUtils.SHA_256, NON_UTF8_MASTER_KEY),
                legacyChecksum);
    }

    @Test
    public void metadataVersionSelectsBackwardCompatibleChecksumScheme() {
        String legacyChecksum = MasterKeyChecksum.calculate(DigestUtils.SHA_256,
                MasterKeyChecksum.RAW_BYTES_VERSION - 1, NON_UTF8_MASTER_KEY);
        String rawChecksum = MasterKeyChecksum.calculate(DigestUtils.SHA_256,
                MasterKeyChecksum.RAW_BYTES_VERSION, NON_UTF8_MASTER_KEY);

        assertEquals(MasterKeyChecksum.calculateLegacy(DigestUtils.SHA_256,
                NON_UTF8_MASTER_KEY, StandardCharsets.UTF_8), legacyChecksum);
        assertNotEquals(legacyChecksum, rawChecksum);
        assertEquals(MasterKeyChecksum.RAW_BYTES_VERSION,
                MetadataManager.getCurrentBackupMetaVersion());
    }
}
