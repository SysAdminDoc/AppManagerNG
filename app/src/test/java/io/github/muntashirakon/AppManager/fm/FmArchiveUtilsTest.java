// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmArchiveUtilsTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-fm-archive");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void createAndExtractZipArchive_preservesTree() throws Exception {
        Path source = root.createNewDirectory("source");
        writeText(source.createNewFile("plain.txt", null), "plain");
        Path nested = source.createNewDirectory("nested");
        writeText(nested.createNewFile("data.txt", null), "nested");
        source.createNewDirectory("empty");
        Path archive = root.createNewFile("bundle.zip", null);
        Path destination = root.createNewDirectory("extract");

        FmArchiveUtils.createZipArchive(Collections.singletonList(source), archive, null);
        FmArchiveUtils.extractZipArchive(archive, destination, name -> FmArchiveUtils.ConflictAction.REPLACE, null);

        assertEquals("plain", readText(destination.findFile("source/plain.txt")));
        assertEquals("nested", readText(destination.findFile("source/nested/data.txt")));
        assertTrue(destination.findFile("source/empty").isDirectory());
    }

    @Test
    public void extractZipArchive_keepBothConflict_createsNumberedCopy() throws Exception {
        Path sourceFile = root.createNewFile("file.txt", null);
        writeText(sourceFile, "new");
        Path archive = root.createNewFile("single.zip", null);
        Path destination = root.createNewDirectory("extract");
        writeText(destination.createNewFile("file.txt", null), "old");

        FmArchiveUtils.createZipArchive(Collections.singletonList(sourceFile), archive, null);
        FmArchiveUtils.extractZipArchive(archive, destination,
                name -> FmArchiveUtils.ConflictAction.KEEP_BOTH, null);

        assertEquals("old", readText(destination.findFile("file.txt")));
        assertEquals("new", readText(destination.findFile("file (1).txt")));
    }

    @Test
    public void extractZipArchive_rejectsZipSlipEntry() throws Exception {
        Path archive = root.createNewFile("bad.zip", null);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archive.openOutputStream())) {
            zipOutputStream.putNextEntry(new ZipEntry("../escape.txt"));
            zipOutputStream.write("unsafe".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        Path destination = root.createNewDirectory("extract");

        try {
            FmArchiveUtils.extractZipArchive(archive, destination,
                    name -> FmArchiveUtils.ConflictAction.REPLACE, null);
            fail("Expected zip-slip entry to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe archive entry path"));
        }
        assertFalse(root.hasFile("escape.txt"));
    }

    private static void writeText(Path path, String text) throws IOException {
        try (OutputStream outputStream = path.openOutputStream()) {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(Path path) throws IOException {
        try (InputStream inputStream = path.openInputStream()) {
            return new String(IoUtils.readFully(inputStream, -1, true), StandardCharsets.UTF_8);
        }
    }
}
