// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
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

    private static void createFiles(Path directory, String... names) throws IOException {
        for (String name : names) {
            directory.createNewFile(name, null);
        }
    }

    private static List<String> namesOf(Path[] paths) {
        return Arrays.stream(paths)
                .map(Path::getName)
                .collect(Collectors.toList());
    }
}
