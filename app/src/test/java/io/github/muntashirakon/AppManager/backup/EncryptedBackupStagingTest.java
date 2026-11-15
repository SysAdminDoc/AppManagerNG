// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * An encrypted backup is written as plain files first, and those hold what the user chose to
 * encrypt. They go where no other app can read or change them, and they're gone once the backup
 * ends, whether it worked or not.
 */
@RunWith(RobolectricTestRunner.class)
public class EncryptedBackupStagingTest {
    private static final String PLAIN_NAME = "data0.tar.gz.0";
    private static final byte[] PLAIN = "the app's data".getBytes(StandardCharsets.UTF_8);

    private Path mTestDir;
    private String mVolumeBefore;

    @Before
    public void setUp() throws IOException {
        mVolumeBefore = Prefs.Storage.getVolumePath().toString();
        mTestDir = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("encrypted-backup-staging");
        Prefs.Storage.setVolumePath(mTestDir.getUri().toString());
    }

    @After
    public void tearDown() {
        Prefs.Storage.setVolumePath(mVolumeBefore);
        mTestDir.delete();
    }

    @Test
    public void aBackupWritesItsPlainFilesOnlyInPrivateStorageAndRemovesThem() throws IOException {
        File sharedCache = new File(Prefs.Storage.getTempPath().getFilePath());
        List<String> stagedBefore = namesIn(FileCache.getPrivateStagingDir());
        BackupItems.BackupItem backupItem = BackupItems.createBackupItemGracefully(0, null, "com.example");
        backupItem.setCrypto(new FlipCrypto());
        try {
            Path plainFile = backupItem.getUnencryptedBackupPath().createNewFile(PLAIN_NAME, null);
            write(plainFile, PLAIN);

            assertTrue(isInside(plainFile, FileCache.getPrivateStagingDir()));
            assertFalse(filesUnder(sharedCache).contains(PLAIN_NAME));

            Path[] encrypted = backupItem.encrypt(new Path[]{plainFile});
            assertFalse(plainFile.exists());
            assertTrue(isInside(encrypted[0], new File(backupItem.getBackupPath().getFilePath())));
            assertArrayEquals(FlipCrypto.flip(PLAIN), encrypted[0].getContentAsBinary());
            backupItem.commit();
        } finally {
            backupItem.cleanup();
        }
        assertEquals(stagedBefore, namesIn(FileCache.getPrivateStagingDir()));
        assertFalse(filesUnder(sharedCache).contains(PLAIN_NAME));
    }

    @Test
    public void aBackupThatFailsLeavesNoPlainCopyBehind() throws IOException {
        File sharedCache = new File(Prefs.Storage.getTempPath().getFilePath());
        List<String> stagedBefore = namesIn(FileCache.getPrivateStagingDir());
        BackupItems.BackupItem backupItem = BackupItems.createBackupItemGracefully(0, null, "com.example");
        backupItem.setCrypto(new FlipCrypto());
        try {
            Path plainFile = backupItem.getUnencryptedBackupPath().createNewFile(PLAIN_NAME, null);
            write(plainFile, PLAIN);
            assertTrue(isInside(plainFile, FileCache.getPrivateStagingDir()));
            // The backup stops here, before anything is encrypted or committed
        } finally {
            backupItem.cleanup();
        }
        assertEquals(stagedBefore, namesIn(FileCache.getPrivateStagingDir()));
        assertFalse(filesUnder(sharedCache).contains(PLAIN_NAME));
    }

    @NonNull
    private static List<String> namesIn(@NonNull File directory) {
        String[] names = directory.list();
        List<String> sorted = names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
        sorted.sort(null);
        return sorted;
    }

    @NonNull
    private static List<String> filesUnder(@NonNull File directory) {
        List<String> files = new ArrayList<>();
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    files.addAll(filesUnder(child));
                } else {
                    files.add(child.getName());
                }
            }
        }
        return files;
    }

    private static boolean isInside(@NonNull Path path, @NonNull File directory) throws IOException {
        String child = new File(path.getFilePath()).getCanonicalPath();
        return child.startsWith(directory.getCanonicalPath() + File.separator);
    }

    private static void write(@NonNull Path file, @NonNull byte[] contents) throws IOException {
        try (OutputStream os = file.openOutputStream()) {
            os.write(contents);
        }
    }

    /** Flips every bit, so the encrypted copy differs from the plain one. */
    private static final class FlipCrypto implements Crypto {
        @NonNull
        static byte[] flip(@NonNull byte[] bytes) {
            byte[] flipped = new byte[bytes.length];
            for (int i = 0; i < bytes.length; ++i) {
                flipped[i] = (byte) ~bytes[i];
            }
            return flipped;
        }

        @NonNull
        @Override
        public String getModeName() {
            return CryptoUtils.MODE_AES;
        }

        @Override
        public void encrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
            for (int i = 0; i < inputFiles.length; ++i) {
                write(outputFiles[i], flip(inputFiles[i].getContentAsBinary()));
            }
        }

        @Override
        public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) throws IOException {
            encryptedStream.write(flip(IoUtils.readFully(unencryptedStream, -1, true)));
        }

        @Override
        public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
            for (int i = 0; i < inputFiles.length; ++i) {
                write(outputFiles[i], flip(inputFiles[i].getContentAsBinary()));
            }
        }

        @Override
        public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) throws IOException {
            unencryptedStream.write(flip(IoUtils.readFully(encryptedStream, -1, true)));
        }

        @Override
        public void close() {
        }
    }
}
