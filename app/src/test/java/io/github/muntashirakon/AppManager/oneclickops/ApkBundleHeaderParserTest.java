// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ApkBundleHeaderParserTest {

    private static byte[] makeZip(List<String> entryNames) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String name : entryNames) {
                ZipEntry e = new ZipEntry(name);
                zos.putNextEntry(e);
                // Each entry gets one filler byte so the ZIP is well-formed.
                zos.write(new byte[]{1});
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @Test
    public void parseAcceptsBundletoolApksWithBaseAndSplits() throws IOException {
        byte[] zip = makeZip(Arrays.asList(
                "base.apk",
                "split_config.arm64_v8a.apk",
                "split_config.xxhdpi.apk",
                "BundleConfig.pb"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APKS, h.format);
        assertTrue(h.hasBaseApk);
        assertEquals(2, h.splitApkCount);
        assertTrue(h.hasBundleConfig);
        assertFalse(h.hasManifestJson);
        assertFalse(h.hasInfoJson);
        assertFalse(h.hasObbData);
    }

    @Test
    public void parseAcceptsApkMirrorBundleWithInfoJson() throws IOException {
        byte[] zip = makeZip(Arrays.asList(
                "base.apk",
                "split_config.arm64_v8a.apk",
                "info.json"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APKM, h.format);
        assertTrue(h.hasBaseApk);
        assertEquals(1, h.splitApkCount);
        assertTrue(h.hasInfoJson);
        assertFalse(h.hasManifestJson);
    }

    @Test
    public void parseAcceptsApkpureXapkWithManifestJsonAndObb() throws IOException {
        byte[] zip = makeZip(Arrays.asList(
                "base.apk",
                "split_config.arm64_v8a.apk",
                "manifest.json",
                "Android/obb/com.example.app/main.123.com.example.app.obb"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.XAPK, h.format);
        assertTrue(h.hasManifestJson);
        assertTrue(h.hasObbData);
    }

    @Test
    public void parseTreatsSingleApkAsApk() throws IOException {
        byte[] zip = makeZip(Arrays.asList(
                "AndroidManifest.xml",
                "classes.dex",
                "resources.arsc"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APK, h.format);
        assertFalse(h.hasBaseApk);
        assertEquals(0, h.splitApkCount);
    }

    @Test
    public void parseHandlesMultidexClassesEntries() throws IOException {
        byte[] zip = makeZip(Arrays.asList(
                "AndroidManifest.xml",
                "classes.dex",
                "classes2.dex",
                "classes3.dex",
                "resources.arsc"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APK, h.format);
    }

    @Test
    public void parseRejectsNonZipInput() throws IOException {
        byte[] notZip = "this is not a zip archive".getBytes();
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(notZip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.UNKNOWN, h.format);
        assertFalse(h.hasBaseApk);
        assertEquals(0, h.splitApkCount);
    }

    @Test
    public void parseEmptyInputReturnsUnknown() throws IOException {
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(new byte[0]);
        assertEquals(ApkBundleHeaderParser.BundleFormat.UNKNOWN, h.format);
    }

    @Test
    public void parseShortInputReturnsUnknownWithoutThrowing() throws IOException {
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(new byte[]{0x50, 0x4B});
        assertEquals(ApkBundleHeaderParser.BundleFormat.UNKNOWN, h.format);
    }

    @Test
    public void parseAcceptsApksWithOnlyConfigSplitsAndNoBase() throws IOException {
        // Bundletool also produces packs that ship only config splits when
        // the base.apk lives elsewhere (e.g. universal-base extraction).
        byte[] zip = makeZip(Arrays.asList(
                "config.arm64_v8a.apk",
                "config.xxhdpi.apk"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.parse(zip);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APKS, h.format);
        assertFalse(h.hasBaseApk);
        assertEquals(2, h.splitApkCount);
    }

    @Test
    public void zipMagicCheckRejectsNullAndShortBytes() {
        assertFalse(ApkBundleHeaderParser.hasZipMagic(null));
        assertFalse(ApkBundleHeaderParser.hasZipMagic(new byte[0]));
        assertFalse(ApkBundleHeaderParser.hasZipMagic(new byte[]{0x50, 0x4B, 0x03}));
        assertTrue(ApkBundleHeaderParser.hasZipMagic(
                new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00}));
    }

    @Test
    public void classifyEmptyEntrySetIsUnknown() {
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.classify(
                Collections.<String>emptySet());
        assertEquals(ApkBundleHeaderParser.BundleFormat.UNKNOWN, h.format);
        assertEquals(0, h.splitApkCount);
    }

    @Test
    public void classifyIsCaseInsensitiveOnEntryNames() {
        Set<String> upper = new HashSet<>(Arrays.asList(
                "BASE.APK",
                "SPLIT_CONFIG.ARM64_V8A.APK",
                "INFO.JSON"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.classify(upper);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APKM, h.format);
        assertEquals(1, h.splitApkCount);
    }

    @Test
    public void classifyDetectsTocPbAsApksFormat() {
        // Apks Helper writes toc.pb instead of BundleConfig.pb.
        Set<String> entries = new HashSet<>(Arrays.asList(
                "base.apk",
                "toc.pb"));
        ApkBundleHeaderParser.Header h = ApkBundleHeaderParser.classify(entries);
        assertEquals(ApkBundleHeaderParser.BundleFormat.APKS, h.format);
        assertTrue(h.hasBundleConfig);
    }
}
