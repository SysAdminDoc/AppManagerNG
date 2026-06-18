// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test
    public void getManifestFromApkStreamReturnsManifestEntry() throws IOException, ApkFile.ApkFileException {
        byte[] manifest = "manifest-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] apk = zipWithEntries(
                new TestZipEntry("classes.dex", "dex".getBytes(StandardCharsets.UTF_8)),
                new TestZipEntry("AndroidManifest.xml", manifest));

        ByteBuffer buffer = ApkUtils.getManifestFromApk(new ByteArrayInputStream(apk));

        byte[] extracted = new byte[buffer.remaining()];
        buffer.get(extracted);
        assertEquals("manifest-bytes", new String(extracted, StandardCharsets.UTF_8));
    }

    @Test
    public void getManifestFromApkStreamRejectsTooManyEntries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i <= ApkUtils.MAX_APK_STREAM_ENTRIES; ++i) {
                zip.putNextEntry(new ZipEntry("ignored-" + i + ".txt"));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("manifest".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ApkFile.ApkFileException exception = assertThrows(ApkFile.ApkFileException.class,
                () -> ApkUtils.getManifestFromApk(new ByteArrayInputStream(out.toByteArray())));

        assertNotNull(exception.getCause());
        assertEquals("APK stream has too many entries.", exception.getCause().getMessage());
    }

    @Test
    public void getManifestFromApkStreamRejectsOversizedManifest() throws Exception {
        byte[] oversizedManifest = new byte[ApkUtils.MAX_MANIFEST_SIZE_BYTES + 1];
        byte[] apk = zipWithEntries(new TestZipEntry("AndroidManifest.xml", oversizedManifest));

        ApkFile.ApkFileException exception = assertThrows(ApkFile.ApkFileException.class,
                () -> ApkUtils.getManifestFromApk(new ByteArrayInputStream(apk)));

        assertNotNull(exception.getCause());
        assertEquals("AndroidManifest.xml is too large.", exception.getCause().getMessage());
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

    private static byte[] zipWithEntries(TestZipEntry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (TestZipEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.content);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static final class TestZipEntry {
        final String name;
        final byte[] content;

        TestZipEntry(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
