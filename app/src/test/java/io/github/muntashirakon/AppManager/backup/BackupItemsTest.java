// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
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

    @Test
    public void readInfoWrapsMalformedCryptoMetadataAsIOException() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                CryptoUtils.MODE_AES, ",\"iv\":\"zz\"");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getMessage().contains("for path"));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readInfoRejectsUnknownCryptoMode() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("cccccccc-cccc-cccc-cccc-cccccccccccc",
                "rot13", "");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readInfoRejectsNegativeUserHandle() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("dddddddd-dddd-dddd-dddd-dddddddddddd",
                -1, CryptoUtils.MODE_NO_ENCRYPTION, "");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readInfoRejectsNegativeBackupTime() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                -1L, 0, CryptoUtils.MODE_NO_ENCRYPTION, "");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readInfoRejectsUnknownTarType() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("ffffffff-ffff-ffff-ffff-ffffffffffff",
                1L, 0, "x", DigestUtils.SHA_256, CryptoUtils.MODE_NO_ENCRYPTION, "");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readInfoRejectsUnsupportedChecksumAlgorithm() throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup("00000000-0000-0000-0000-000000000000",
                1L, 0, TarUtils.TAR_GZIP, "rot13", CryptoUtils.MODE_NO_ENCRYPTION, "");

        IOException exception = assertThrows(IOException.class, backupItem::getInfo);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsInvalidPackageName() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("11111111-1111-1111-1111-111111111111",
                "com..example");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsInvalidApkName() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("22222222-2222-2222-2222-222222222222",
                "com.example", "../base.apk", "[]");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsInvalidSplitConfigName() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("33333333-3333-3333-3333-333333333333",
                "com.example", "base.apk", "[\"split/evil.apk\"]");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsNegativeVersionCode() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("44444444-4444-4444-4444-444444444444",
                "com.example", -1L, "base.apk", "[]");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsEmptyDataDirEntry() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("55555555-5555-5555-5555-555555555555",
                "com.example", 1L, "[\"\"]", "base.apk", "[]");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void readMetadataRejectsInvalidInstallerName() throws IOException {
        BackupItems.BackupItem backupItem = createMetadataBackup("66666666-6666-6666-6666-666666666666",
                "com.example", 1L, "[]", "base.apk", "[]", "\"com..installer\"");

        IOException exception = assertThrows(IOException.class, backupItem::getMetadata);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    private static void createFiles(Path directory, String... names) throws IOException {
        for (String name : names) {
            directory.createNewFile(name, null);
        }
    }

    private BackupItems.BackupItem createInfoBackup(String backupUuid, String crypto, String extraFields)
            throws IOException {
        return createInfoBackup(backupUuid, 1L, 0, TarUtils.TAR_GZIP, DigestUtils.SHA_256, crypto, extraFields);
    }

    private BackupItems.BackupItem createInfoBackup(String backupUuid, int userHandle, String crypto,
                                                    String extraFields) throws IOException {
        return createInfoBackup(backupUuid, 1L, userHandle, TarUtils.TAR_GZIP, DigestUtils.SHA_256, crypto,
                extraFields);
    }

    private BackupItems.BackupItem createInfoBackup(String backupUuid, long backupTime, int userHandle, String crypto,
                                                    String extraFields) throws IOException {
        return createInfoBackup(backupUuid, backupTime, userHandle, TarUtils.TAR_GZIP, DigestUtils.SHA_256, crypto,
                extraFields);
    }

    private BackupItems.BackupItem createInfoBackup(String backupUuid, long backupTime, int userHandle, String tarType,
                                                    String checksumAlgo, String crypto, String extraFields)
            throws IOException {
        Path backupPath = Prefs.Storage.getAppManagerDirectory()
                .findOrCreateDirectory(BackupItems.BACKUP_DIRECTORY)
                .findOrCreateDirectory(backupUuid);
        Path infoFile = backupPath.createNewFile(MetadataManager.INFO_V5_FILE, null);
        writeString(infoFile, "{"
                + "\"version\":7,"
                + "\"backup_time\":" + backupTime + ","
                + "\"flags\":0,"
                + "\"user_handle\":" + userHandle + ","
                + "\"tar_type\":\"" + tarType + "\","
                + "\"checksum_algo\":\"" + checksumAlgo + "\","
                + "\"crypto\":\"" + crypto + "\""
                + extraFields
                + "}");
        return BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(backupUuid));
    }

    private BackupItems.BackupItem createMetadataBackup(String backupUuid, String packageName) throws IOException {
        return createMetadataBackup(backupUuid, packageName, "base.apk", "[]");
    }

    private BackupItems.BackupItem createMetadataBackup(String backupUuid, String packageName, String apkName,
                                                        String splitConfigs) throws IOException {
        return createMetadataBackup(backupUuid, packageName, 1L, apkName, splitConfigs);
    }

    private BackupItems.BackupItem createMetadataBackup(String backupUuid, String packageName, long versionCode,
                                                        String apkName, String splitConfigs) throws IOException {
        return createMetadataBackup(backupUuid, packageName, versionCode, "[]", apkName, splitConfigs);
    }

    private BackupItems.BackupItem createMetadataBackup(String backupUuid, String packageName, long versionCode,
                                                        String dataDirs, String apkName, String splitConfigs)
            throws IOException {
        return createMetadataBackup(backupUuid, packageName, versionCode, dataDirs, apkName, splitConfigs, "null");
    }

    private BackupItems.BackupItem createMetadataBackup(String backupUuid, String packageName, long versionCode,
                                                        String dataDirs, String apkName, String splitConfigs,
                                                        String installerJson)
            throws IOException {
        BackupItems.BackupItem backupItem = createInfoBackup(backupUuid, CryptoUtils.MODE_NO_ENCRYPTION, "");
        Path backupPath = Prefs.Storage.getAppManagerDirectory()
                .findOrCreateDirectory(BackupItems.BACKUP_DIRECTORY)
                .findOrCreateDirectory(backupUuid);
        Path metadataFile = backupPath.createNewFile(MetadataManager.META_V5_FILE, null);
        writeString(metadataFile, "{"
                + "\"version\":7,"
                + "\"backup_name\":null,"
                + "\"label\":\"Example\","
                + "\"package_name\":\"" + packageName + "\","
                + "\"version_name\":\"1\","
                + "\"version_code\":" + versionCode + ","
                + "\"data_dirs\":" + dataDirs + ","
                + "\"is_system\":false,"
                + "\"is_split_apk\":false,"
                + "\"split_configs\":" + splitConfigs + ","
                + "\"has_rules\":false,"
                + "\"apk_name\":\"" + apkName + "\","
                + "\"instruction_set\":\"arm64\","
                + "\"key_store\":false,"
                + "\"installer\":" + installerJson + ","
                + "\"default_roles\":[]"
                + "}");
        return backupItem;
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
