// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.nio.ByteBuffer;

public class ApkUtilsTest {
    @Test
    public void formatApkFilenameUsesLiteralPlaceholderValues() {
        assertEquals("Price $5\\Beta-com.example-1$2\\3-42-35-23-2026-06-06",
                ApkUtils.formatApkFilename("%label%-%package_name%-%version%-%version_code%-%target_sdk%-%min_sdk%-%datetime%",
                        "Price $5\\Beta",
                        "com.example",
                        "1$2\\3",
                        42,
                        35,
                        23,
                        "2026-06-06"));
    }

    @Test
    public void formatApkFilenameHandlesMissingVersion() {
        assertEquals("Example--1",
                ApkUtils.formatApkFilename("%label%-%version%-%version_code%",
                        "Example",
                        "com.example",
                        null,
                        1,
                        35,
                        23,
                        "2026-06-06"));
    }

    @Test
    public void getManifestAttributesWrapsHostileBinaryXmlAsApkFileException() {
        ApkFile.ApkFileException exception = assertThrows(ApkFile.ApkFileException.class,
                () -> ApkUtils.getManifestAttributes(ByteBuffer.wrap(hostileBinaryXml())));

        assertNotNull(exception.getCause());
    }

    private static byte[] hostileBinaryXml() {
        return new byte[]{
                0x03, 0x00, 0x08, 0x00,
                0x28, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x1c, 0x00,
                0x10, 0x00, 0x00, 0x00,
                0x10, 0x00, 0x00, 0x00
        };
    }
}
