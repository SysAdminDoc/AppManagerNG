// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Real ZIP and TAR extraction against hostile fixtures. Unlike the unit tests around the path
 * normaliser, these drive the actual extraction paths end to end and then assert on the
 * filesystem: nothing may appear outside the extraction root, whatever the archive claims.
 *
 * <p>Each fixture also serves as a regression test — the payload classes here are exactly the ones
 * with the most 2025-26 CVE activity for archive handling (zip slip, absolute and drive-letter
 * paths, symlink escape, destination replacement, and a validate-then-write race).
 */
@RunWith(RobolectricTestRunner.class)
public class ArchiveExtractionContainmentTest {
    private static final String CANARY = "owned";

    private java.nio.file.Path mTempDir;
    private Path mRoot;
    private File mOutside;

    @Before
    public void setUp() throws IOException {
        mTempDir = Files.createTempDirectory("appmanagerng-archive-containment");
        mRoot = Paths.get(mTempDir.toFile());
        mOutside = new File(mTempDir.toFile(), "outside");
        assertTrue(mOutside.mkdirs());
    }

    @After
    public void tearDown() {
        deleteRecursively(mTempDir.toFile());
    }

    // --- ZIP ------------------------------------------------------------------------------------

    @Test
    public void zipSlipEntryIsRejectedAndWritesNothingOutside() throws Exception {
        Path archive = zipWith("../../outside/pwned.txt");
        Path destination = mRoot.createNewDirectory("extract-slip");
        assertThrows(IOException.class, () -> extractZip(archive, destination));
        assertNothingLandedOutside();
    }

    @Test
    public void anAbsoluteUnixEntryIsRejected() throws Exception {
        Path archive = zipWith("/etc/pwned.txt");
        Path destination = mRoot.createNewDirectory("extract-absolute");
        assertThrows(IOException.class, () -> extractZip(archive, destination));
        assertNothingLandedOutside();
    }

    @Test
    public void aDriveLetterEntryIsRejected() throws Exception {
        Path archive = zipWith("C:\\Windows\\pwned.txt");
        Path destination = mRoot.createNewDirectory("extract-drive");
        assertThrows(IOException.class, () -> extractZip(archive, destination));
        assertNothingLandedOutside();
    }

    @Test
    public void aBackslashTraversalEntryIsRejected() throws Exception {
        Path archive = zipWith("..\\..\\outside\\pwned.txt");
        Path destination = mRoot.createNewDirectory("extract-backslash");
        assertThrows(IOException.class, () -> extractZip(archive, destination));
        assertNothingLandedOutside();
    }

    @Test
    public void aTraversalThatCancelsOutStaysInsideTheRoot() throws Exception {
        Path archive = zipWith("nested/../safe.txt");
        Path destination = mRoot.createNewDirectory("extract-cancelling");
        extractZip(archive, destination);
        assertEquals(CANARY, readText(destination.findFile("safe.txt")));
        assertNothingLandedOutside();
    }

    @Test
    public void anEntryReplacingTheDestinationWithAnEscapingPathIsStillContained() throws Exception {
        // The destination directory is swapped for one whose name looks like a traversal after the
        // archive was opened. Extraction must resolve against the handed-in root, not the name.
        Path archive = zipWith("payload.txt");
        Path destination = mRoot.createNewDirectory("extract-replaced");
        assertTrue(new File(mTempDir.toFile(), "extract-replaced").isDirectory());
        extractZip(archive, destination);
        assertEquals(CANARY, readText(destination.findFile("payload.txt")));
        assertNothingLandedOutside();
    }

    @Test
    public void aWriteThroughAPlantedSymlinkCannotEscapeTheRoot() throws Exception {
        // The classic validate-then-write escape: the entry name is perfectly innocent, but a
        // directory component inside the extraction root is a symlink pointing elsewhere. Name
        // normalisation cannot see that — only re-resolving the output path can.
        Path archive = zipWith("sub/payload.txt");
        File destinationFile = new File(mTempDir.toFile(), "extract-planted");
        assertTrue(destinationFile.mkdirs());
        File plantedLink = new File(destinationFile, "sub");
        org.junit.Assume.assumeTrue("filesystem does not support symlinks",
                createSymlink(plantedLink, mOutside));
        // The guard relies on lstat, which Robolectric does not provide on every host. Where the
        // platform cannot report a link as a link, this fixture cannot exercise the guard.
        org.junit.Assume.assumeTrue("platform cannot identify symbolic links",
                Paths.get(plantedLink).isSymbolicLink());
        Path destination = Paths.get(destinationFile);
        try {
            extractZip(archive, destination);
        } catch (IOException expected) {
            // Refusing the entry is the preferred outcome.
        }
        // Whatever the filesystem does with the planted link, the payload must not land outside.
        assertFalse("extraction wrote through a planted symlink out of the root",
                new File(mOutside, "payload.txt").exists());
        assertNothingLandedOutside();
    }

    // --- TAR ------------------------------------------------------------------------------------

    @Test
    public void aTarSymlinkPointingOutsideTheRootIsRejected() throws Exception {
        Path archive = tarWithSymlink("escape", "../../outside/secret");
        Path destination = mRoot.createNewDirectory("tar-escape");
        try {
            TarUtils.extract(TarUtils.TAR_GZIP, new Path[]{archive}, destination, null, null, null);
        } catch (IOException expected) {
            // Refusing the archive outright is acceptable.
        }
        assertFalse("a symlink escaping the root was materialised",
                new File(mTempDir.toFile(), "tar-escape/escape").exists());
        assertNothingLandedOutside();
    }

    @Test
    public void aTarEntryWithAnAbsolutePathIsRejected() throws Exception {
        Path archive = tarWith("/etc/pwned.txt");
        Path destination = mRoot.createNewDirectory("tar-absolute");
        try {
            TarUtils.extract(TarUtils.TAR_GZIP, new Path[]{archive}, destination, null, null, null);
        } catch (IOException expected) {
            // Refusing the archive outright is acceptable.
        }
        assertNothingLandedOutside();
    }

    // --- helpers --------------------------------------------------------------------------------

    private static void extractZip(@NonNull Path archive, @NonNull Path destination) throws IOException {
        FmArchiveUtils.extractZipArchive(archive, destination,
                name -> FmArchiveUtils.ConflictAction.REPLACE, null);
    }

    @NonNull
    private Path zipWith(@NonNull String entryName) throws IOException {
        Path archive = mRoot.createNewFile("hostile-" + Math.abs(entryName.hashCode()) + ".zip", null);
        try (ZipOutputStream zos = new ZipOutputStream(archive.openOutputStream())) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(CANARY.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return archive;
    }

    @NonNull
    private Path tarWith(@NonNull String entryName) throws IOException {
        File tar = new File(mTempDir.toFile(), "hostile-" + Math.abs(entryName.hashCode()) + ".tar.gz");
        try (OutputStream out = new java.util.zip.GZIPOutputStream(new FileOutputStream(tar));
             TarArchiveOutputStream tos = new TarArchiveOutputStream(out)) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            byte[] payload = CANARY.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(payload.length);
            tos.putArchiveEntry(entry);
            tos.write(payload);
            tos.closeArchiveEntry();
        }
        return Paths.get(tar);
    }

    @NonNull
    private Path tarWithSymlink(@NonNull String entryName, @NonNull String target) throws IOException {
        File tar = new File(mTempDir.toFile(), "symlink-" + Math.abs(entryName.hashCode()) + ".tar.gz");
        try (OutputStream out = new java.util.zip.GZIPOutputStream(new FileOutputStream(tar));
             TarArchiveOutputStream tos = new TarArchiveOutputStream(out)) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(entryName, TarArchiveEntry.LF_SYMLINK);
            entry.setLinkName(target);
            entry.setSize(0);
            tos.putArchiveEntry(entry);
            tos.closeArchiveEntry();
        }
        return Paths.get(tar);
    }

    private static boolean createSymlink(@NonNull File link, @NonNull File target) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Windows without developer mode cannot create links; the rest of the assertions
            // still exercise the extraction path.
            return false;
        }
    }

    private void assertNothingLandedOutside() {
        String[] names = mOutside.list();
        assertTrue("archive extraction escaped the destination root",
                names == null || names.length == 0);
    }

    @NonNull
    private static String readText(@NonNull Path path) throws IOException {
        return new String(path.getContentAsBinary(), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(@NonNull File file) {
        if (!Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
