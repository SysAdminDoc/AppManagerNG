// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class BackupItemsTest {
    private Path testDir;
    private String previousVolumePath;

    @Before
    public void setUp() throws IOException {
        previousVolumePath = Prefs.Storage.getVolumePath().toString();
        testDir = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("backup-items-test");
        Prefs.Storage.setVolumePath(testDir.getUri().toString());
    }

    @After
    public void tearDown() {
        Prefs.Storage.setVolumePath(previousVolumePath);
        testDir.delete();
    }

    @Test
    public void backupArchiveSelectionIgnoresPrefixSidecars() throws IOException {
        String backupUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        Path backupPath = Prefs.Storage.getAppManagerDirectory()
                .findOrCreateDirectory(BackupItems.BACKUP_DIRECTORY)
                .findOrCreateDirectory(backupUuid);

        createFiles(backupPath,
                "source.tar.gz.0",
                "source.tar.zst.12",
                "source.tar.gz",
                "source.tar.gz.",
                "source.tar.gz.next",
                "source.tar.gz.0.bak",
                "source-not-an-archive",
                "data0.tar.gz.0",
                "data0.tar.bz2.1",
                "data0.ab",
                "data00.tar.gz.0",
                "data0.tar.gz",
                "data0.tar.gz.next",
                "data0.ab.bak",
                "keystore.tar.gz.0",
                "keystore.tar.zst.2",
                "keystore",
                "keystore.tar.gz",
                "keystore.tar.gz.next");

        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(backupUuid));

        assertEquals(Arrays.asList("source.tar.gz.0", "source.tar.zst.12"), namesOf(backupItem.getSourceFiles()));
        assertEquals(Arrays.asList("data0.ab", "data0.tar.bz2.1", "data0.tar.gz.0"),
                namesOf(backupItem.getDataFiles(0)));
        assertEquals(Arrays.asList("keystore.tar.gz.0", "keystore.tar.zst.2"),
                namesOf(backupItem.getKeyStoreFiles()));
    }

    @Test
    public void checksumReaderRejectsMalformedRowsAsIOException() throws IOException {
        assertMalformedChecksum("missing-tab.txt", "not-tab-separated\n");
        assertMalformedChecksum("empty-checksum.txt", "\tdata0.tar.gz.0\n");
        assertMalformedChecksum("empty-filename.txt", "abcd\t\n");
        assertMalformedChecksum("duplicate-filename.txt", "abcd\tdata0.tar.gz.0\nefgh\tdata0.tar.gz.0\n");
    }

    @Test
    public void checksumWriterRejectsMalformedRows() throws IOException {
        Path checksumFile = testDir.createNewFile("checksums.txt", null);

        try (BackupItems.Checksum checksum = new BackupItems.Checksum(checksumFile, "w")) {
            checksum.add("data0.tar.gz.0", "abcd");
            assertThrows(IllegalArgumentException.class, () -> checksum.add("data0.tar.gz.0", "efgh"));
            assertThrows(IllegalArgumentException.class, () -> checksum.add("", "abcd"));
            assertThrows(IllegalArgumentException.class, () -> checksum.add("data1.tar.gz.0", ""));
        }
    }

    private static void createFiles(Path directory, String... names) throws IOException {
        for (String name : names) {
            directory.createNewFile(name, null);
        }
    }

    private void assertMalformedChecksum(String filename, String contents) throws IOException {
        Path checksumFile = testDir.createNewFile(filename, null);
        writeString(checksumFile, contents);

        IOException exception = assertThrows(IOException.class, () -> new BackupItems.Checksum(checksumFile, "r"));
        assertEquals("Illegal lines found in the checksum file.", exception.getMessage());
    }

    private static void writeString(Path file, String contents) throws IOException {
        try (OutputStream os = file.openOutputStream()) {
            os.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<String> namesOf(Path[] paths) {
        return Arrays.stream(paths)
                .map(Path::getName)
                .collect(Collectors.toList());
    }
}
