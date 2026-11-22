// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.utils.DigestUtils;

/**
 * A file named with the start of its SHA-256 can be checked against it before installing (#44). The
 * check only says whether the name and the file agree, never that the file is genuine.
 */
public class FileNameChecksumTest {
    private static final String FULL = "0f3a9c7be41d26580f3a9c7be41d26580f3a9c7be41d26580f3a9c7be41d2658";

    @Test
    public void aHexRunBeforeTheExtensionIsTheChecksum() {
        assertEquals("b32438a1", FileNameChecksum.parse("AppManagerNG-v0.8.0-full-universal-b32438a1.apk"));
        assertEquals("0a1b2c3d", FileNameChecksum.parse("Walkie-Talkie_2.0.3-0A1B2C3D.apks"));
        assertEquals(FULL, FileNameChecksum.parse("app-" + FULL + ".xapk"));
        assertEquals("deadbeef12", FileNameChecksum.parse("Some App_1.2_deadbeef12.apkm"));
        assertEquals("abcdef0123", FileNameChecksum.parse("APP-ABCDEF0123.APK"));
    }

    @Test
    public void versionsDatesAndOtherNamesHaveNoChecksum() {
        String[] names = {
                null, "", "app.apk", "app-1.2.3.apk", "app-v1.2.3.apk", "app-20261121.apk", "app_12345678.apk",
                "app-1234567890123.apks", "app-abc1234.apk", "app-" + FULL + "0.apk", "app-deadbeefg.apk",
                "app-v1b2c3d4e.apk", "app-b32438a1.zip", "app-b32438a1.apk.part", "b32438a1.apk",
                "app-b32438a1 .apk", "app-b32438a1", "app.b32438a1.apk"
        };
        for (String name : names) {
            assertNull(name, FileNameChecksum.parse(name));
        }
    }

    @Test
    public void theNameMatchesWhenTheFilesChecksumStartsWithIt() {
        assertTrue(FileNameChecksum.matches("0f3a9c7b", FULL));
        assertTrue(FileNameChecksum.matches("0F3A9C7BE41D", FULL));
        assertTrue(FileNameChecksum.matches(FULL, FULL));
        assertFalse(FileNameChecksum.matches("0f3a9c7c", FULL));
        assertFalse(FileNameChecksum.matches("e41d2658", FULL));
        assertFalse(FileNameChecksum.matches(FULL.substring(0, 63) + "9", FULL));
        assertFalse(FileNameChecksum.matches(FULL + "00", FULL));
    }

    @Test
    public void theFileItselfIsHashedForTheVerdict() throws IOException {
        byte[] bytes = "not really an apk".getBytes(StandardCharsets.UTF_8);
        String sha256 = DigestUtils.getHexDigest(DigestUtils.SHA_256, bytes);

        assertEquals(FileNameChecksum.Verdict.MATCHES,
                FileNameChecksum.check("app-" + sha256.substring(0, 8) + ".apk", stream(bytes)));
        assertEquals(FileNameChecksum.Verdict.DIFFERS,
                FileNameChecksum.check("app-" + flip(sha256.substring(0, 8)) + ".apk", stream(bytes)));
        assertNull(FileNameChecksum.check("app-1.0.apk", stream(bytes)));
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static String flip(String hex) {
        char last = hex.charAt(hex.length() - 1);
        return hex.substring(0, hex.length() - 1) + (last == '0' ? '1' : '0');
    }
}
