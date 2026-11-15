// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.test.shadows.ShadowPackageManagerCompat;

/**
 * A backup's info file isn't encrypted, so anyone who can write the backup folder can swap an
 * encrypted backup for plain files and say so in that file. A restore checks it against the mode
 * this app recorded for the folder before anything is cleared.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public class EncryptedBackupModeTest {
    private static final String PACKAGE = "dnsfilter.android";
    private static final String RELATIVE_DIR = PACKAGE + "/0";
    private static final int DATA = BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_EXT_DATA;

    private Path mResourceBackups;
    private Path mVolume;
    private String mVolumeBefore;

    @Before
    public void setUp() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertNotNull(classLoader);
        mResourceBackups = Paths.get(classLoader.getResource("backups/v4").getFile());
        mVolume = Paths.get(RoboUtils.getTestBaseDir());
        mVolumeBefore = Prefs.Storage.getVolumePath().toString();
        Prefs.Storage.setVolumePath(mVolume.getUri().toString());
        ShadowPackageManagerCompat.resetClearApplicationUserDataCalls();
        ShadowPackageManagerCompat.resetPackageInfo();
    }

    @After
    public void tearDown() {
        RestoreOp.setDirectoryCommandHook(null);
        ShadowPackageManagerCompat.resetClearApplicationUserDataCalls();
        ShadowPackageManagerCompat.resetPackageInfo();
        new AppDb().deleteAllBackups();
        Prefs.Storage.setVolumePath(mVolumeBefore);
        for (Path path : mVolume.listFiles()) {
            path.delete();
        }
    }

    @Test
    public void anEncryptedBackupWhoseInfoNowSaysUnencryptedIsNotRestored() throws Throwable {
        copyOfTheTestBackupForAnInstalledApp();
        // Made with AES; its files were then swapped for plain ones with matching checksums
        BackupCryptoRecord.record(RELATIVE_DIR, CryptoUtils.MODE_AES);

        BackupException exception = assertThrows(BackupException.class,
                () -> new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, RELATIVE_DIR, DATA), null));

        assertTrue(exception.getMessage(), exception.getMessage().contains("made with AES encryption"));
        assertTrue(exception.getMessage(), exception.getMessage().contains("now says no encryption"));
        assertEquals(0, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
        assertEquals(CryptoUtils.MODE_AES, BackupCryptoRecord.getRecordedMode(RELATIVE_DIR));
    }

    @Test
    public void anEncryptedBackupWhoseInfoNowNamesAnotherModeIsNotRestored() throws Throwable {
        Path backupPath = copyOfTheTestBackupForAnInstalledApp();
        Path info = backupPath.findFile("meta_v2.am.json");
        String json = info.getContentAsString();
        assertTrue(json.contains("\"crypto\": \"none\""));
        // A PGP info file needs key IDs to parse at all
        writeBytes(info, json.replace("\"crypto\": \"none\"", "\"crypto\": \"pgp\", \"key_ids\": \"6E9B1A2C\"")
                .getBytes(StandardCharsets.UTF_8));
        BackupCryptoRecord.record(RELATIVE_DIR, CryptoUtils.MODE_AES);

        BackupException exception = assertThrows(BackupException.class,
                () -> new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, RELATIVE_DIR, DATA), null));

        assertTrue(exception.getMessage(), exception.getMessage().contains("made with AES encryption"));
        assertTrue(exception.getMessage(), exception.getMessage().contains("now says PGP encryption"));
        assertEquals(0, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
    }

    @Test
    public void aBackupRecordedAsUnencryptedIsRestoredAsBefore() throws Throwable {
        copyOfTheTestBackupForAnInstalledApp();
        BackupCryptoRecord.record(RELATIVE_DIR, CryptoUtils.MODE_NO_ENCRYPTION);

        new BackupManager().restore(new RestoreOpOptions(PACKAGE, 0, RELATIVE_DIR, DATA), null);

        assertEquals(1, ShadowPackageManagerCompat.getClearApplicationUserDataCalls());
    }

    @Test
    public void aCommittedBackupRecordsItsModeAndDeletingItForgetsIt() throws IOException {
        BackupItems.BackupItem encrypted = BackupItems.createBackupItemGracefully(0, null, "com.example");
        encrypted.setCrypto(new FlipCrypto());
        BackupItems.BackupItem plain = BackupItems.createBackupItemGracefully(0, "plain", "com.example");
        try {
            Path file = encrypted.getUnencryptedBackupPath().createNewFile("data0.tar.gz.0", null);
            writeBytes(file, "the app's data".getBytes(StandardCharsets.UTF_8));
            encrypted.encrypt(new Path[]{file});
            encrypted.commit();
            writeBytes(plain.getUnencryptedBackupPath().createNewFile("data0.tar.gz.0", null),
                    "the app's data".getBytes(StandardCharsets.UTF_8));
            plain.commit();
        } finally {
            encrypted.cleanup();
            plain.cleanup();
        }

        assertEquals(CryptoUtils.MODE_AES, BackupCryptoRecord.getRecordedMode(encrypted.getRelativeDir()));
        assertEquals(CryptoUtils.MODE_NO_ENCRYPTION, BackupCryptoRecord.getRecordedMode(plain.getRelativeDir()));
        assertTrue(encrypted.delete());
        assertNull(BackupCryptoRecord.getRecordedMode(encrypted.getRelativeDir()));
    }

    @Test
    public void listingTheBackupsAgainKeepsTheEncryptedModesTheTableHeld() {
        Backup aes = row("aes", "backups/5d3c0c4e-aes", CryptoUtils.MODE_AES, 5);
        Backup none = row("none", "backups/5d3c0c4e-none", CryptoUtils.MODE_NO_ENCRYPTION, 5);
        // Rows from before v5 have no folder of their own and infer it, as Backup.getItem() does
        Backup v4 = row("v4", "", CryptoUtils.MODE_RSA, 2);
        // This app made the folder unencrypted after the table last listed it
        Backup remade = row("remade", "backups/5d3c0c4e-remade", CryptoUtils.MODE_ECC, 5);
        BackupCryptoRecord.record("backups/5d3c0c4e-remade", CryptoUtils.MODE_NO_ENCRYPTION);
        AppDb appDb = new AppDb();
        appDb.insertBackups(Arrays.asList(aes, none, v4, remade));

        // A full scan clears the table before filling it again from the info files
        appDb.deleteAllBackups();

        assertEquals(CryptoUtils.MODE_AES, BackupCryptoRecord.getRecordedMode("backups/5d3c0c4e-aes"));
        assertNull(BackupCryptoRecord.getRecordedMode("backups/5d3c0c4e-none"));
        assertEquals(CryptoUtils.MODE_RSA, BackupCryptoRecord.getRecordedMode("com.example.mode/0_v4"));
        assertEquals(CryptoUtils.MODE_NO_ENCRYPTION, BackupCryptoRecord.getRecordedMode("backups/5d3c0c4e-remade"));
    }

    @NonNull
    private static Backup row(@NonNull String backupName, @NonNull String relativeDir, @NonNull String crypto,
                              int version) {
        Backup backup = new Backup();
        backup.packageName = "com.example.mode";
        backup.backupName = backupName;
        backup.userId = 0;
        backup.relativeDir = relativeDir;
        backup.crypto = crypto;
        backup.version = version;
        backup.label = "Mode";
        backup.versionName = "1.0";
        backup.installer = "";
        return backup;
    }

    /**
     * Puts a copy of the unencrypted test backup on the volume and has the shadow report the app as
     * installed, so the restore leaves the APK alone.
     */
    @NonNull
    private Path copyOfTheTestBackupForAnInstalledApp() throws IOException {
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
        assertNotNull(mResourceBackups.findFile("AppManager").copyTo(mVolume, true));
        RestoreOp.setDirectoryCommandHook(command -> true);
        return mVolume.findFile("AppManager").findFile(PACKAGE).findFile("0");
    }

    private static void writeBytes(@NonNull Path file, @NonNull byte[] contents) throws IOException {
        try (OutputStream os = file.openOutputStream()) {
            os.write(contents);
        }
    }

    /** Flips every bit and calls itself AES, so the encrypted copy differs from the plain one. */
    private static final class FlipCrypto implements Crypto {
        @NonNull
        private static byte[] flip(@NonNull byte[] bytes) {
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
        public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
            for (int i = 0; i < inputFiles.length; ++i) {
                writeBytes(outputFiles[i], flip(inputFiles[i].getContentAsBinary()));
            }
        }

        @Override
        public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
