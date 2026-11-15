// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Below Android 12 a backup copies an app's raw KeyStore entries before archiving them. The copies
 * go where no other app can read them, and they're gone afterwards even when the archive fails.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.R)
public class KeyStoreStagingTest {
    private static final int UID = 10021;
    private static final List<String> ENTRIES = Arrays.asList(UID + "_USRPKEY_signing", UID + "_USRCERT_signing");
    private static final List<String> STAGED = Arrays.asList("-1000_USRCERT_signing", "-1000_USRPKEY_signing");

    private Path mKeyStore;

    @Before
    public void setUp() throws IOException {
        mKeyStore = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("keystore-staging");
        for (String entry : ENTRIES) {
            try (OutputStream os = mKeyStore.createNewFile(entry, null).openOutputStream()) {
                os.write(("raw " + entry).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @After
    public void tearDown() {
        mKeyStore.delete();
    }

    @Test
    public void theEntriesAreStagedPrivatelyAndRemovedAfterTheArchive() throws Throwable {
        List<String> stagedBefore = namesIn(FileCache.getPrivateStagingDir());
        List<String> seen = new ArrayList<>();
        Path archive = mKeyStore.createNewFile("keystore.tar.gz.0", null);

        Path[] archived = BackupOp.archiveKeyStoreFiles(mKeyStore, ENTRIES, UID, (staging, filters) -> {
            assertTrue(isInside(staging, FileCache.getPrivateStagingDir()));
            assertFalse(isInside(staging, FileUtils.getCachePath()));
            for (Path file : staging.listFiles()) {
                seen.add(file.getName());
            }
            assertEquals(2, filters.length);
            return new Path[]{archive};
        });

        seen.sort(null);
        assertEquals(STAGED, seen);
        assertEquals(Arrays.asList(archive), Arrays.asList(archived));
        assertEquals(stagedBefore, namesIn(FileCache.getPrivateStagingDir()));
        assertNoStagedEntryIn(FileUtils.getCachePath());
    }

    @Test
    public void aFailedArchiveLeavesNoCopyBehind() {
        List<String> stagedBefore = namesIn(FileCache.getPrivateStagingDir());
        IOException tarFailure = new IOException("tar failed");
        try {
            BackupOp.archiveKeyStoreFiles(mKeyStore, ENTRIES, UID, (staging, filters) -> {
                throw tarFailure;
            });
            fail("A failed archive must fail the KeyStore backup");
        } catch (BackupException e) {
            assertEquals(tarFailure, e.getCause());
        }
        assertEquals(stagedBefore, namesIn(FileCache.getPrivateStagingDir()));
        assertNoStagedEntryIn(FileUtils.getCachePath());
    }

    @NonNull
    private static List<String> namesIn(@NonNull File directory) {
        String[] names = directory.list();
        List<String> sorted = names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
        sorted.sort(null);
        return sorted;
    }

    private static void assertNoStagedEntryIn(@NonNull File directory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                assertNoStagedEntryIn(child);
            } else {
                assertFalse(child.getPath(), STAGED.contains(child.getName()));
            }
        }
    }

    private static boolean isInside(@NonNull Path path, @NonNull File directory) throws IOException {
        String child = new File(path.getFilePath()).getCanonicalPath();
        return child.startsWith(directory.getCanonicalPath() + File.separator);
    }
}
