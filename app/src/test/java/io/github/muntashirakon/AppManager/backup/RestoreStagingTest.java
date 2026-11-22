// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.test.shadows.ShadowPackageInstallerCompat;
import io.github.muntashirakon.test.shadows.ShadowPackageManagerCompat;

/**
 * A restore reads each backup file once, into private storage, and checks, decrypts and restores
 * only that copy. What happens to the backup afterwards can't reach the app.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public class RestoreStagingTest {
    private static final String PACKAGE = "dnsfilter.android";
    private static final String CONF = "files/PersonalDNSFilter/dnsfilter.conf";
    private static final int DATA = BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_EXT_DATA;

    private Path rscBackupPath;
    private Path tmpBackupPath;

    @Before
    public void setUp() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertNotNull(classLoader);
        rscBackupPath = Paths.get(classLoader.getResource("backups/v4").getFile());
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir());
        ShadowPackageManagerCompat.resetClearApplicationUserDataCalls();
        ShadowPackageManagerCompat.resetPackageInfo();
        ShadowPackageInstallerCompat.resetHandedApks();
    }

    @After
    public void tearDown() {
        RestoreOp.setStagedInputsHook(null);
        RestoreOp.setDirectoryCommandHook(null);
        RestoreStaging.setRoom(null);
        ShadowPackageManagerCompat.resetClearApplicationUserDataCalls();
        ShadowPackageManagerCompat.resetPackageInfo();
        ShadowPackageInstallerCompat.resetHandedApks();
        for (Path path : tmpBackupPath.listFiles()) {
            path.delete();
        }
    }

    @Test
    public void aBackupChangedAfterItsCheckCannotChangeWhatIsRestored() throws Throwable {
        List<String> restoredDirs = Collections.synchronizedList(new ArrayList<>());
        Path backupPath = copyOfTheTestBackupForAnInstalledApp(restoredDirs);
        Path dataArchive = backupPath.findFile("data0.tar.gz.0");
        byte[] swapped = archiveHolding(CONF, "swapped after the check");
        RestoreOp.setStagedInputsHook(() -> {
            // Another app, or the provider behind the backup, rewrites it once it has been checked
            try {
                writeBytes(dataArchive, swapped);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Set<String> stagedBefore = stagedNames();

        new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, PACKAGE + "/0", DATA), null);

        assertArrayEquals(swapped, dataArchive.getContentAsBinary());
        assertEquals(restoredDirs.toString(), 2, restoredDirs.size());
        String internal = Paths.get(restoredDirs.get(0)).findFile("files").findFile("PersonalDNSFilter")
                .findFile("dnsfilter.conf").getContentAsString();
        // Index 1 holds the same archive as index 0 did, and nothing changed it
        String external = Paths.get(restoredDirs.get(1)).findFile("files").findFile("PersonalDNSFilter")
                .findFile("dnsfilter.conf").getContentAsString();
        assertNotEquals("swapped after the check", internal);
        assertEquals(external, internal);
        assertEquals(1, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void restoredApksAreStagedPrivatelyAndGoneOnceInstalled() throws Throwable {
        // The app isn't installed, so the backed-up APK is installed as it is
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager").copyTo(tmpBackupPath, true));
        Set<String> stagedBefore = stagedNames();

        new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, PACKAGE + "/0", BackupFlags.BACKUP_APK_FILES),
                null);

        List<String> handed = ShadowPackageInstallerCompat.getHandedApks();
        assertEquals(1, handed.size());
        File apk = new File(handed.get(0));
        assertEquals("base.apk", apk.getName());
        // Only this app can reach it, unlike /data/local/tmp, which the shell user owns
        assertTrue(handed.get(0), isInside(Paths.get(apk), FileCache.getPrivateStagingDir()));
        assertFalse(apk.exists());
        assertNotNull(RuntimeEnvironment.getApplication().getPackageManager().getPackageInfo(PACKAGE, 0));
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aFileThatFailsItsCheckStopsTheRestoreBeforeTheDataIsCleared() throws Throwable {
        Path backupPath = copyOfTheTestBackupForAnInstalledApp(new ArrayList<>());
        // The extras are applied after the data is back, but checked with everything else first
        writeBytes(backupPath.findFile("misc.am.tsv"), "not what was backed up".getBytes(StandardCharsets.UTF_8));
        Set<String> stagedBefore = stagedNames();
        RestoreOpOptions options = new RestoreOpOptions(PACKAGE, 0, PACKAGE + "/0",
                DATA | BackupFlags.BACKUP_EXTRAS);

        BackupException exception = assertThrows(BackupException.class,
                () -> new BackupManager().restore(options, null));

        assertTrue(exception.getMessage(), exception.getMessage().contains("Couldn't verify misc file."));
        assertEquals(0, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aDataArchiveThatFailsItsCheckIsNotKept() throws Throwable {
        Path backupPath = copyOfTheTestBackupForAnInstalledApp(new ArrayList<>());
        writeBytes(backupPath.findFile("data1.tar.gz.0"), archiveHolding(CONF, "changed"));
        Set<String> stagedBefore = stagedNames();

        BackupException exception = assertThrows(BackupException.class,
                () -> new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, PACKAGE + "/0", DATA), null));

        assertTrue(exception.getMessage(), exception.getMessage().startsWith("Data file verification failed for index 1."));
        assertEquals(0, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void splitPartsAreCopiedInOrderAndCheckedOneByOne() throws Throwable {
        Path dir = tmpBackupPath.createNewDirectory("split");
        Path first = dir.createNewFile("data0.tar.gz.0", null);
        Path second = dir.createNewFile("data0.tar.gz.1", null);
        writeBytes(first, "first part".getBytes(StandardCharsets.UTF_8));
        writeBytes(second, "second part".getBytes(StandardCharsets.UTF_8));
        BackupItems.BackupItem backupItem = restoreItem();
        try (BackupItems.Checksum checksum = checksums(first, second);
             RestoreStaging staging = new RestoreStaging(backupItem, checksum, DigestUtils.SHA_256)) {
            Path[] staged = staging.stage(new Path[]{first, second}, "Check failed.");

            assertEquals(2, staged.length);
            assertEquals("data0.tar.gz.0", staged[0].getName());
            assertEquals("first part", staged[0].getContentAsString());
            assertEquals("second part", staged[1].getContentAsString());
            assertTrue(isInside(staged[0], FileCache.getPrivateStagingDir()));

            // A changed second part fails, and the first part's copy goes with it
            writeBytes(second, "changed".getBytes(StandardCharsets.UTF_8));
            BackupException exception = assertThrows(BackupException.class,
                    () -> staging.stage(new Path[]{first, second}, "Check failed."));
            assertTrue(exception.getMessage(), exception.getMessage().startsWith("Check failed.\nFile: "));
            assertEquals(2, filesUnder(staging.getDirectory()).size());
        } finally {
            backupItem.cleanup();
        }
    }

    @Test
    public void anEncryptedFileIsDecryptedFromItsCheckedCopyIntoPrivateStorage() throws Throwable {
        byte[] plain = "data the app gets back".getBytes(StandardCharsets.UTF_8);
        Path cipherFile = tmpBackupPath.createNewDirectory("encrypted")
                .createNewFile("data0.tar.gz.0" + CryptoUtils.getExtension(CryptoUtils.MODE_AES), null);
        writeBytes(cipherFile, XorCrypto.flip(plain));
        BackupItems.BackupItem backupItem = restoreItem();
        backupItem.setCrypto(new XorCrypto());
        Set<String> stagedBefore = stagedNames();
        try (BackupItems.Checksum checksum = checksums(cipherFile);
             RestoreStaging staging = new RestoreStaging(backupItem, checksum, DigestUtils.SHA_256)) {
            Path[] decrypted = staging.stage(new Path[]{cipherFile}, "Check failed.");

            assertEquals("data0.tar.gz.0", decrypted[0].getName());
            assertArrayEquals(plain, decrypted[0].getContentAsBinary());
            assertTrue(isInside(decrypted[0], FileCache.getPrivateStagingDir()));
            assertFalse(isInside(decrypted[0], new File(Prefs.Storage.getTempPath().getFilePath())));
            // The encrypted copy isn't needed once it's decrypted
            assertEquals(Collections.emptyList(), filesUnder(staging.getDirectory()));
        } finally {
            backupItem.cleanup();
        }
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aFileThatFailsToDecryptLeavesNothingBehind() throws Throwable {
        Path cipherFile = tmpBackupPath.createNewDirectory("tampered")
                .createNewFile("data0.tar.gz.0" + CryptoUtils.getExtension(CryptoUtils.MODE_AES), null);
        writeBytes(cipherFile, XorCrypto.flip(XorCrypto.REFUSED));
        BackupItems.BackupItem backupItem = restoreItem();
        backupItem.setCrypto(new XorCrypto());
        Set<String> stagedBefore = stagedNames();
        try (BackupItems.Checksum checksum = checksums(cipherFile);
             RestoreStaging staging = new RestoreStaging(backupItem, checksum, DigestUtils.SHA_256)) {
            BackupException exception = assertThrows(BackupException.class,
                    () -> staging.stage(new Path[]{cipherFile}, "Check failed."));

            assertTrue(exception.getMessage(), exception.getMessage().startsWith("Failed to decrypt"));
            assertEquals(Collections.emptyList(), filesUnder(staging.getDirectory()));
        } finally {
            backupItem.cleanup();
        }
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aFileLargerThanTheSpaceLeftIsNotCopied() throws Throwable {
        Path source = tmpBackupPath.createNewDirectory("large").createNewFile("data0.tar.gz.0", null);
        writeBytes(source, new byte[100]);
        // The disk has room for 99 bytes past the reserve
        RestoreStaging.setRoom((directory, bytes) -> bytes <= RestoreStaging.FREE_SPACE_RESERVE + 99);
        BackupItems.BackupItem backupItem = restoreItem();
        try (RestoreStaging staging = new RestoreStaging(backupItem, null, DigestUtils.SHA_256)) {
            BackupException exception = assertThrows(BackupException.class,
                    () -> staging.stage(new Path[]{source}, "Check failed."));

            assertTrue(exception.getMessage(), exception.getMessage().startsWith("Not enough free space to stage data0.tar.gz.0"));
            assertEquals(Collections.emptyList(), filesUnder(staging.getDirectory()));
        } finally {
            backupItem.cleanup();
        }
    }

    @Test
    public void anEncryptedFileWithNoRoomForItsPlainCopyIsNotDecrypted() throws Throwable {
        byte[] plain = "data the app gets back".getBytes(StandardCharsets.UTF_8);
        Path cipherFile = tmpBackupPath.createNewDirectory("no-room")
                .createNewFile("data0.tar.gz.0" + CryptoUtils.getExtension(CryptoUtils.MODE_AES), null);
        writeBytes(cipherFile, XorCrypto.flip(plain));
        BackupItems.BackupItem backupItem = restoreItem();
        backupItem.setCrypto(new XorCrypto());
        Set<String> stagedBefore = stagedNames();
        List<Long> asked = new ArrayList<>();
        // Room for the copy in its batch folder, none for a second copy that size next to it
        RestoreStaging.setRoom((directory, bytes) -> {
            asked.add(bytes);
            return !directory.getName().startsWith("restore-");
        });
        try (BackupItems.Checksum checksum = checksums(cipherFile);
             RestoreStaging staging = new RestoreStaging(backupItem, checksum, DigestUtils.SHA_256)) {
            BackupException exception = assertThrows(BackupException.class,
                    () -> staging.stage(new Path[]{cipherFile}, "Check failed."));

            assertTrue(exception.getMessage(), exception.getMessage().startsWith("Not enough free space to decrypt"));
            assertEquals(Arrays.asList(plain.length + RestoreStaging.FREE_SPACE_RESERVE,
                    plain.length + RestoreStaging.FREE_SPACE_RESERVE), asked);
            assertEquals(Collections.emptyList(), filesUnder(staging.getDirectory()));
        } finally {
            backupItem.cleanup();
        }
        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aLengthNearTheLimitAsksForAllTheSpaceInsteadOfNone() {
        // A provider that claims a length near Long.MAX_VALUE would otherwise wrap the request negative,
        // which makeRoom() reads as asking for nothing
        assertEquals(Long.MAX_VALUE, RestoreStaging.saturatedAdd(Long.MAX_VALUE - 1, RestoreStaging.FREE_SPACE_RESERVE));
        assertEquals(Long.MAX_VALUE, RestoreStaging.saturatedAdd(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(100 + RestoreStaging.FREE_SPACE_RESERVE, RestoreStaging.saturatedAdd(100, RestoreStaging.FREE_SPACE_RESERVE));
    }

    /**
     * Puts a copy of the test backup on the volume and has the shadow report the app as installed,
     * so the restore leaves the APK alone. Each directory the restore hands back to the app is
     * added to the list, in index order.
     */
    @NonNull
    private Path copyOfTheTestBackupForAnInstalledApp(@NonNull List<String> restoredDirs) throws IOException {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PACKAGE;
        applicationInfo.uid = 10421;
        applicationInfo.publicSourceDir = RoboUtils.getTestBaseDir().getAbsolutePath()
                + "/data/app/" + PACKAGE + "/base.apk";
        applicationInfo.sourceDir = applicationInfo.publicSourceDir;
        applicationInfo.dataDir = "/data/data/" + PACKAGE;
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE;
        packageInfo.applicationInfo = applicationInfo;
        ShadowPackageManagerCompat.setPackageInfoForUser(PACKAGE, 0, packageInfo);
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager").copyTo(tmpBackupPath, true));
        RestoreOp.setDirectoryCommandHook(command -> {
            if (command[0].equals("chown")) {
                restoredDirs.add(command[command.length - 1].replace('\\', '/'));
            }
            return true;
        });
        return tmpBackupPath.findFile("AppManager").findFile(PACKAGE).findFile("0");
    }

    @NonNull
    private BackupItems.BackupItem restoreItem() throws IOException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager").copyTo(tmpBackupPath, true));
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(PACKAGE + "/0");
        assertFalse(backupItem.isBackupMode());
        return backupItem;
    }

    @NonNull
    private BackupItems.Checksum checksums(@NonNull Path... files) throws IOException {
        StringBuilder lines = new StringBuilder();
        for (Path file : files) {
            lines.append(DigestUtils.getHexDigest(DigestUtils.SHA_256, file)).append('\t')
                    .append(file.getName()).append('\n');
        }
        Path checksumFile = tmpBackupPath.findOrCreateDirectory("checksums")
                .createNewFile("checksums.txt", null);
        writeBytes(checksumFile, lines.toString().getBytes(StandardCharsets.UTF_8));
        return new BackupItems.Checksum(checksumFile, "r");
    }

    @NonNull
    private byte[] archiveHolding(@NonNull String relativePath, @NonNull String content) throws IOException {
        Path sourceDir = tmpBackupPath.createNewDirectory("archive-source-" + System.nanoTime());
        Path parent = sourceDir;
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length - 1; ++i) {
            parent = parent.findOrCreateDirectory(parts[i]);
        }
        writeBytes(parent.createNewFile(parts[parts.length - 1], null), content.getBytes(StandardCharsets.UTF_8));
        Path out = tmpBackupPath.createNewDirectory("archive-out-" + System.nanoTime());
        List<Path> archives = TarUtils.create(TarUtils.TAR_GZIP, sourceDir, out, "data0.tar.gz", null, null,
                null, false);
        assertEquals(1, archives.size());
        return archives.get(0).getContentAsBinary();
    }

    @NonNull
    private static Set<String> stagedNames() {
        String[] names = FileCache.getPrivateStagingDir().list();
        return names != null ? new HashSet<>(Arrays.asList(names)) : new HashSet<>();
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

    private static void writeBytes(@NonNull Path file, @NonNull byte[] contents) throws IOException {
        try (OutputStream os = file.openOutputStream()) {
            os.write(contents);
        }
    }

    /** Flips every bit, and refuses one plaintext the way a failed authentication tag would. */
    private static final class XorCrypto implements Crypto {
        static final byte[] REFUSED = "forged".getBytes(StandardCharsets.UTF_8);

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
                writeBytes(outputFiles[i], flip(inputFiles[i].getContentAsBinary()));
            }
        }

        @Override
        public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) throws IOException {
            encryptedStream.write(flip(IoUtils.readFully(unencryptedStream, -1, true)));
        }

        @Override
        public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
            for (int i = 0; i < inputFiles.length; ++i) {
                byte[] plain = flip(inputFiles[i].getContentAsBinary());
                if (Arrays.equals(REFUSED, plain)) {
                    throw new IOException("Authentication failed");
                }
                writeBytes(outputFiles[i], plain);
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
