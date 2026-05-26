// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ApkFileScannerTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static File writeFile(File parent, String name, int bytes) throws IOException {
        File f = new File(parent, name);
        File pf = f.getParentFile();
        if (pf != null) pf.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (bytes > 0) out.write(new byte[bytes]);
        }
        return f;
    }

    @Test
    public void scanFindsAllRecognisedExtensions() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "chrome.apk", 1024);
        writeFile(root, "splits/chrome.apks", 4096);
        writeFile(root, "splits/chrome.apkm", 4096);
        writeFile(root, "splits/chrome.xapk", 4096);
        writeFile(root, "notes.txt", 64); // unrelated

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(4, hits.size());
    }

    @Test
    public void scanIgnoresZeroByteFiles() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "valid.apk", 256);
        writeFile(root, "placeholder.apk", 0);

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(1, hits.size());
        assertEquals("valid.apk", hits.get(0).getName());
    }

    @Test
    public void scanIgnoresPartialDownloads() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "chrome.apk.crdownload", 1024);
        writeFile(root, "chrome.apk.part", 1024);
        writeFile(root, "chrome.apk.download", 1024);
        writeFile(root, "chrome.apk.opdownload", 1024);
        writeFile(root, "chrome.apk.tmp", 1024);
        writeFile(root, "chrome.apk", 1024); // the keeper

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(1, hits.size());
        assertEquals("chrome.apk", hits.get(0).getName());
    }

    @Test
    public void scanIgnoresHiddenFiles() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, ".tmp.apk", 1024);  // hidden / partial - reject
        writeFile(root, "real.apk", 1024);

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(1, hits.size());
        assertEquals("real.apk", hits.get(0).getName());
    }

    @Test
    public void scanIsCaseInsensitiveOnExtension() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "weird.APK", 1024);
        writeFile(root, "more.ApKs", 1024);
        writeFile(root, "third.Xapk", 1024);

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(3, hits.size());
    }

    @Test
    public void scanWalksNestedDirectories() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "top.apk", 1024);
        writeFile(root, "a/b/c/deep.apk", 1024);
        writeFile(root, "a/b/d/also.apk", 1024);
        writeFile(root, "a/sibling.apk", 1024);

        List<File> hits = ApkFileScanner.scan(root, null);
        assertEquals(4, hits.size());
    }

    @Test
    public void scanReturnsEmptyWhenRootMissing() {
        File missing = new File(tmp.getRoot(), "absent");
        assertTrue(ApkFileScanner.scan(missing, null).isEmpty());
    }

    @Test
    public void scanReturnsEmptyWhenRootIsNotDirectory() throws IOException {
        File file = tmp.newFile("something.apk");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[16]);
        }
        // root must be a directory; passing a file returns empty.
        assertTrue(ApkFileScanner.scan(file, null).isEmpty());
    }

    @Test
    public void scanHonorsCustomExtensionSet() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "real.apk", 1024);
        writeFile(root, "alt.zip", 1024);
        writeFile(root, "deep/alt2.zip", 1024);

        List<File> hits = ApkFileScanner.scan(root,
                new HashSet<>(Arrays.asList(".zip")), null);
        assertEquals(2, hits.size());
        for (File f : hits) {
            assertTrue(f.getName().toLowerCase().endsWith(".zip"));
        }
    }

    @Test
    public void scanShortCircuitsOnCancellation() throws IOException {
        File root = tmp.newFolder("apks");
        writeFile(root, "a.apk", 1024);
        writeFile(root, "deep/b.apk", 1024);

        ApkFileScanner.CancellationSignal signal = new ApkFileScanner.CancellationSignal();
        signal.cancel();
        List<File> hits = ApkFileScanner.scan(root, signal);
        assertTrue("scan with pre-cancelled signal must short-circuit",
                hits.isEmpty());
    }

    @Test
    public void isAcceptableApkRejectsEmptySet() throws IOException {
        File root = tmp.newFolder("apks");
        File f = writeFile(root, "real.apk", 1024);
        assertFalse(ApkFileScanner.isAcceptableApk(f, Collections.<String>emptySet()));
    }

    @Test
    public void isAcceptableApkRejectsDirectories() throws IOException {
        File root = tmp.newFolder("apks");
        File dir = new File(root, "weird.apk");
        dir.mkdirs();
        assertFalse(ApkFileScanner.isAcceptableApk(dir, ApkFileScanner.APK_EXTENSIONS));
    }

    @Test
    public void hasAcceptedExtensionRejectsMismatch() {
        assertFalse(ApkFileScanner.hasAcceptedExtension("foo.zip", ApkFileScanner.APK_EXTENSIONS));
        assertFalse(ApkFileScanner.hasAcceptedExtension("foo.apk.zip", ApkFileScanner.APK_EXTENSIONS));
        assertTrue(ApkFileScanner.hasAcceptedExtension("foo.apk", ApkFileScanner.APK_EXTENSIONS));
        assertTrue(ApkFileScanner.hasAcceptedExtension("foo.apks", ApkFileScanner.APK_EXTENSIONS));
    }

    @Test
    public void matchesPartialDownloadSuffixSpotsAllKnownVariants() {
        assertTrue(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk.crdownload"));
        assertTrue(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk.part"));
        assertTrue(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk.download"));
        assertTrue(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk.opdownload"));
        assertTrue(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk.tmp"));
        assertFalse(ApkFileScanner.matchesPartialDownloadSuffix("foo.apk"));
    }
}
