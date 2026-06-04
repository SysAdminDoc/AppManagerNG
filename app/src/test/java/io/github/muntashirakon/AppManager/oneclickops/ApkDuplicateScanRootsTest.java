// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ApkDuplicateScanRootsTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void deduplicateRootsSkipsConfiguredRootAlreadyCoveredByExternalStorage() throws IOException {
        File externalRoot = tmp.newFolder("external");
        File configuredRoot = new File(externalRoot, "AppManager");
        configuredRoot.mkdirs();

        List<File> roots = ApkDuplicateScanRoots.deduplicateRoots(
                Arrays.asList(externalRoot, configuredRoot));

        assertEquals(1, roots.size());
        assertEquals(externalRoot.getCanonicalPath(), roots.get(0).getCanonicalPath());
    }

    @Test
    public void deduplicateRootsKeepsConfiguredRootOutsideExternalStorage() throws IOException {
        File externalRoot = tmp.newFolder("external");
        File configuredRoot = tmp.newFolder("configured-backup");

        List<File> roots = ApkDuplicateScanRoots.deduplicateRoots(
                Arrays.asList(externalRoot, configuredRoot));

        assertEquals(2, roots.size());
        assertEquals(externalRoot.getCanonicalPath(), roots.get(0).getCanonicalPath());
        assertEquals(configuredRoot.getCanonicalPath(), roots.get(1).getCanonicalPath());
    }

    @Test
    public void scanRootsFindsApksInConfiguredRootOutsideExternalStorage() throws IOException {
        File externalRoot = tmp.newFolder("external");
        File configuredRoot = tmp.newFolder("configured-backup");
        writeFile(configuredRoot, "saved.apk", 16);

        List<File> files = ApkDuplicateScanRoots.scanRoots(
                Arrays.asList(externalRoot, configuredRoot), null);

        assertEquals(1, files.size());
        assertEquals("saved.apk", files.get(0).getName());
    }

    @Test
    public void scanRootsDeduplicatesOverlappingExternalAndConfiguredRoots() throws IOException {
        File externalRoot = tmp.newFolder("external");
        File configuredRoot = new File(externalRoot, "AppManager");
        configuredRoot.mkdirs();
        writeFile(configuredRoot, "saved.apk", 16);

        List<File> files = ApkDuplicateScanRoots.scanRoots(
                Arrays.asList(externalRoot, configuredRoot), null);

        assertEquals(1, files.size());
        assertEquals("saved.apk", files.get(0).getName());
    }

    private File writeFile(File parent, String name, int bytes) throws IOException {
        File file = new File(parent, name);
        File fileParent = file.getParentFile();
        if (fileParent != null) {
            fileParent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
        return file;
    }
}
