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

    @Test
    public void assertReasonableZipEntryCountRejectsOverLimit() {
        try {
            FmArchiveUtils.assertReasonableZipEntryCount(FmArchiveUtils.MAX_ZIP_ENTRIES + 1);
            fail("Expected over-limit archive entry count to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Archive bomb detected"));
        }
    }

    private static void writeText(Path path, String text) throws IOException {
        try (OutputStream outputStream = path.openOutputStream()) {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ── normalizeZipEntryName: hostile entry name validation ──

    @Test
    public void normalizeZipEntryName_rejectsSimpleTraversal() {
        assertUnsafeEntry("../escape.txt");
    }

    @Test
    public void normalizeZipEntryName_rejectsDeepTraversal() {
        assertUnsafeEntry("../../../etc/passwd");
    }

    @Test
    public void normalizeZipEntryName_rejectsMiddleTraversal() {
        assertUnsafeEntry("dir/../../../escape.txt");
    }

    @Test
    public void normalizeZipEntryName_rejectsAbsoluteUnixPath() {
        assertUnsafeEntry("/etc/passwd");
    }

    @Test
    public void normalizeZipEntryName_rejectsAbsoluteWindowsPath() {
        assertUnsafeEntry("C:\\Windows\\System32\\evil.dll");
    }

    @Test
    public void normalizeZipEntryName_rejectsWindowsTraversal() {
        assertUnsafeEntry("..\\..\\escape.txt");
    }

    @Test
    public void normalizeZipEntryName_rejectsDotDotOnly() {
        assertUnsafeEntry("..");
    }

    @Test
    public void normalizeZipEntryName_normalizesTrailingDotDot() throws IOException {
        // foo/bar/.. normalizes to foo — still within root, so it's safe
        assertEquals("foo", FmArchiveUtils.normalizeZipEntryName("foo/bar/.."));
    }

    @Test
    public void normalizeZipEntryName_rejectsMixedSeparatorTraversal() {
        assertUnsafeEntry("foo\\..\\..\\escape.txt");
    }

    @Test
    public void normalizeZipEntryName_acceptsSimpleName() throws IOException {
        assertEquals("file.txt", FmArchiveUtils.normalizeZipEntryName("file.txt"));
    }

    @Test
    public void normalizeZipEntryName_acceptsNestedPath() throws IOException {
        assertEquals("dir/subdir/file.txt", FmArchiveUtils.normalizeZipEntryName("dir/subdir/file.txt"));
    }

    @Test
    public void normalizeZipEntryName_acceptsDotInName() throws IOException {
        assertEquals("dir/.hidden/file.txt", FmArchiveUtils.normalizeZipEntryName("dir/.hidden/file.txt"));
    }

    @Test
    public void normalizeZipEntryName_acceptsSafeInternalDotDot() throws IOException {
        assertEquals("dir/file.txt", FmArchiveUtils.normalizeZipEntryName("dir/sub/../file.txt"));
    }

    @Test
    public void extractZipArchive_rejectsDeepTraversal() throws Exception {
        Path archive = root.createNewFile("deep-traversal.zip", null);
        try (ZipOutputStream zos = new ZipOutputStream(archive.openOutputStream())) {
            zos.putNextEntry(new ZipEntry("../../../tmp/evil.txt"));
            zos.write("payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path destination = root.createNewDirectory("extract-deep");
        try {
            FmArchiveUtils.extractZipArchive(archive, destination,
                    name -> FmArchiveUtils.ConflictAction.REPLACE, null);
            fail("Expected deep-traversal entry to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe archive entry path"));
        }
    }

    @Test
    public void extractZipArchive_rejectsAbsolutePath() throws Exception {
        Path archive = root.createNewFile("absolute-path.zip", null);
        try (ZipOutputStream zos = new ZipOutputStream(archive.openOutputStream())) {
            zos.putNextEntry(new ZipEntry("/etc/passwd"));
            zos.write("root:x:0:0".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path destination = root.createNewDirectory("extract-abs");
        try {
            FmArchiveUtils.extractZipArchive(archive, destination,
                    name -> FmArchiveUtils.ConflictAction.REPLACE, null);
            fail("Expected absolute-path entry to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe archive entry path"));
        }
    }

    @Test
    public void extractZipArchive_rejectsWindowsPathTraversal() throws Exception {
        Path archive = root.createNewFile("windows-traversal.zip", null);
        try (ZipOutputStream zos = new ZipOutputStream(archive.openOutputStream())) {
            zos.putNextEntry(new ZipEntry("..\\..\\evil.txt"));
            zos.write("payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path destination = root.createNewDirectory("extract-win");
        try {
            FmArchiveUtils.extractZipArchive(archive, destination,
                    name -> FmArchiveUtils.ConflictAction.REPLACE, null);
            fail("Expected Windows traversal entry to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe archive entry path"));
        }
    }

    @Test
    public void extractZipArchive_extractsSafeEntriesSkipsUnsafe() throws Exception {
        Path archive = root.createNewFile("mixed.zip", null);
        try (ZipOutputStream zos = new ZipOutputStream(archive.openOutputStream())) {
            zos.putNextEntry(new ZipEntry("safe.txt"));
            zos.write("safe content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested/also-safe.txt"));
            zos.write("nested safe".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path destination = root.createNewDirectory("extract-safe");
        FmArchiveUtils.extractZipArchive(archive, destination,
                name -> FmArchiveUtils.ConflictAction.REPLACE, null);
        assertEquals("safe content", readText(destination.findFile("safe.txt")));
        assertEquals("nested safe", readText(destination.findFile("nested/also-safe.txt")));
    }

    // ── Helpers ──

    private static void assertUnsafeEntry(String entryName) {
        try {
            FmArchiveUtils.normalizeZipEntryName(entryName);
            fail("Expected IOException for unsafe entry: " + entryName);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe archive entry path"));
        }
    }

    private static String readText(Path path) throws IOException {
        try (InputStream inputStream = path.openInputStream()) {
            return new String(IoUtils.readFully(inputStream, -1, true), StandardCharsets.UTF_8);
        }
    }
}
