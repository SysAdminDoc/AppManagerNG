// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ApkBundleBaseExtractorTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void extractBaseApkPrefersRootBaseEntry() throws IOException {
        byte[] base = new byte[]{1, 2, 3, 4};
        File bundle = makeZip("app.apks",
                entry("split_config.arm64_v8a.apk", new byte[]{9}),
                entry("base.apk", base));

        File extracted = ApkBundleBaseExtractor.extractBaseApk(bundle, tmp.newFolder("cache"));

        assertTrue(extracted != null && extracted.exists());
        assertArrayEquals(base, Files.readAllBytes(extracted.toPath()));
    }

    @Test
    public void extractBaseApkFallsBackToRootPackageApkForXapk() throws IOException {
        byte[] base = new byte[]{5, 6, 7};
        File bundle = makeZip("app.xapk",
                entry("manifest.json", "{}".getBytes()),
                entry("com.example.app.apk", base),
                entry("Android/obb/com.example.app/main.1.com.example.app.obb", new byte[]{8}));

        File extracted = ApkBundleBaseExtractor.extractBaseApk(bundle, tmp.newFolder("cache"));

        assertTrue(extracted != null && extracted.exists());
        assertArrayEquals(base, Files.readAllBytes(extracted.toPath()));
    }

    @Test
    public void extractBaseApkRejectsSplitOnlyBundle() throws IOException {
        File bundle = makeZip("split-only.apks",
                entry("split_config.arm64_v8a.apk", new byte[]{1}),
                entry("config.xxhdpi.apk", new byte[]{2}));

        assertNull(ApkBundleBaseExtractor.extractBaseApk(bundle, tmp.newFolder("cache")));
    }

    @Test
    public void selectBaseApkEntryPrefersRootBaseOverNestedBaseAndFallback() {
        assertEquals("base.apk", ApkBundleBaseExtractor.selectBaseApkEntryName(new HashSet<>(Arrays.asList(
                "payload/base.apk",
                "base.apk",
                "com.example.app.apk"))));
    }

    @Test
    public void selectBaseApkEntryRejectsUnsafeTraversalEntries() {
        assertNull(ApkBundleBaseExtractor.selectBaseApkEntryName(
                new HashSet<>(Collections.singletonList("../base.apk"))));
    }

    private File makeZip(String name, Entry... entries) throws IOException {
        File bundle = new File(tmp.getRoot(), name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(bundle))) {
            for (Entry entry : entries) {
                out.putNextEntry(new ZipEntry(entry.name));
                out.write(entry.bytes);
                out.closeEntry();
            }
        }
        return bundle;
    }

    private static Entry entry(String name, byte[] bytes) {
        return new Entry(name, bytes);
    }

    private static final class Entry {
        final String name;
        final byte[] bytes;

        Entry(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
