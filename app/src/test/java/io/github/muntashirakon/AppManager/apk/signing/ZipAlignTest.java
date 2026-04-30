// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ZipAlignTest {
    private File tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("zipalign-test").toFile();
    }

    @After
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    @Test
    public void replaceFile_replacesExistingTargetAndDeletesBackup() throws Exception {
        File source = new File(tempDir, "app.apk.align.tmp");
        File target = new File(tempDir, "app.apk");
        File staleBackup = new File(tempDir, "app.apk.align.bak");
        write(source, "aligned");
        write(target, "original");
        write(staleBackup, "stale");

        ZipAlign.replaceFile(source, target);

        assertFalse(source.exists());
        assertFalse(staleBackup.exists());
        assertTrue(target.exists());
        assertEquals("aligned", read(target));
    }

    @Test
    public void replaceFile_rejectsMissingSourceWithoutDeletingTarget() throws Exception {
        File source = new File(tempDir, "missing.apk.align.tmp");
        File target = new File(tempDir, "app.apk");
        write(target, "original");

        assertThrows(IOException.class, () -> ZipAlign.replaceFile(source, target));

        assertTrue(target.exists());
        assertEquals("original", read(target));
    }

    private static void write(File file, String contents) throws IOException {
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
