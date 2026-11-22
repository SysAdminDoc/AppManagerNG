// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.ArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;

@RunWith(RobolectricTestRunner.class)
public class SBConverterTest {
    private static final String PACKAGE_NAME_FULL = "dnsfilter.android";
    private static final String PACKAGE_NAME_APK_INT = "org.billthefarmer.editor";
    private static final String PACKAGE_NAME_APK = "ademar.textlauncher";
    private static final String PACKAGE_NAME_APK_SPLITS = "com.google.android.samples.dynamicfeatures.ondemand";
    private static final String OBB_NAME = "main.7.ademar.textlauncher.obb";

    private final ClassLoader classLoader = getClass().getClassLoader();
    private File backupLocation;
    private Path tmpBackupPath;

    @Before
    public void setUp() throws IOException {
        assert classLoader != null;
        backupLocation = new File(classLoader.getResource("SwiftBackup").getFile());
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("backup-dir");
        Prefs.Storage.setVolumePath(tmpBackupPath.toString());
    }

    @After
    public void tearDown() {
        tmpBackupPath.delete();
    }

    @Test
    public void convertFullTest() throws BackupException, IOException {
        final List<String> internalStorage = Collections.singletonList("code_cache/");
        final List<String> externalStorage = Arrays.asList("files/",
                "files/PersonalDNSFilter/",
                "files/PersonalDNSFilter/dnsfilter.conf",
                "files/PersonalDNSFilter/additionalHosts.txt",
                "files/PersonalDNSFilter/VERSION.TXT");
        Collections.sort(internalStorage);
        Collections.sort(externalStorage);

        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_FULL + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(1)));
        Collections.sort(files);
        assertEquals(externalStorage, files);
    }

    @Test
    public void convertApkInternalStorageTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("code_cache/",
                "shared_prefs/",
                "shared_prefs/org.billthefarmer.editor_preferences.xml");
        Collections.sort(internalStorage);
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_INT + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkSplitsTest() throws BackupException, IOException {
        final List<String> expectedApkFiles = Arrays.asList("base.apk",
                "split_config.en.apk",
                "split_config.hdpi.apk");
        Collections.sort(expectedApkFiles);
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_SPLITS + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        List<String> actualApkFiles = TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles()));
        Collections.sort(actualApkFiles);
        assertEquals(expectedApkFiles, actualApkFiles);
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }


    @Test
    public void convertApkObbTest() throws BackupException, IOException {
        // An APK Robolectric can read, with an OBB archive in the layout Swift Backup writes.
        // Robolectric's resource table parser trips on some older APKs, which kept this test off.
        File source = copyFixtures(PACKAGE_NAME_APK, PACKAGE_NAME_APK);
        byte[] obbPayload = writeObbArchive(source, PACKAGE_NAME_APK);
        Map<String, List<String>> cachedBefore = cachedApkDirs(PACKAGE_NAME_APK);
        SBConverter sbConvert = new SBConverter(Paths.get(new File(source, PACKAGE_NAME_APK + ".xml")));
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertTrue(metadataV5.info.flags.backupMediaObb());
        assertFalse(metadataV5.info.flags.backupInternalData());
        assertFalse(metadataV5.info.flags.backupExternalData());
        assertArrayEquals(new String[]{"/storage/emulated/0/Android/obb/" + PACKAGE_NAME_APK},
                metadataV5.metadata.dataDirs);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        // The OBB is the only data, so it's the first data archive, byte for byte
        assertEquals(Collections.singletonMap(OBB_NAME, sha256(new ByteArrayInputStream(obbPayload))),
                tarDigests(backupItem.getDataFiles(0)));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
        assertNoNewCachedApk(cachedBefore, PACKAGE_NAME_APK);

        // Only after a good conversion are the Swift files removed, the OBB archive with them
        sbConvert.cleanup();
        assertEquals(Collections.emptyList(), Arrays.asList(Objects.requireNonNull(source.list())));
    }

    @Test
    public void aManifestThatIsNotJsonStopsTheConversion() throws IOException {
        File source = copyFixtures(PACKAGE_NAME_APK, PACKAGE_NAME_APK);
        writeObbArchive(source, PACKAGE_NAME_APK);
        setZipComment(new File(source, PACKAGE_NAME_APK + ".exp"), "{\"packageName\":");

        assertConversionFails(source, PACKAGE_NAME_APK, "Invalid Swift Backup manifest");
    }

    @Test
    public void aManifestForAnotherAppStopsTheConversion() throws IOException {
        File source = copyFixtures(PACKAGE_NAME_APK, PACKAGE_NAME_APK);
        setZipComment(new File(source, PACKAGE_NAME_APK + ".app"), "{\"packageName\":\"com.example.other\"}");

        assertConversionFails(source, PACKAGE_NAME_APK, "Invalid Swift Backup manifest");
    }

    @Test
    public void anApkOfAnotherAppStopsTheConversion() throws IOException {
        // The files are named for one app while the APK inside them is another
        File source = copyFixtures(PACKAGE_NAME_APK, "com.example.other");

        assertConversionFails(source, "com.example.other", "Package name mismatch");
    }

    @Test
    public void withoutTheApkNothingIsConverted() throws IOException {
        File source = copyFixtures(PACKAGE_NAME_APK_INT, PACKAGE_NAME_APK_INT);
        assertTrue(new File(source, PACKAGE_NAME_APK_INT + ".app").delete());

        assertConversionFails(source, PACKAGE_NAME_APK_INT, "Could not cache APK file");
    }

    @Test
    public void aDataArchiveThatBreaksPartWayLeavesNoHalfBackup() throws IOException {
        // Its end record is whole, so it passes the manifest check, but an entry inside is damaged.
        // The APK is already in the new backup by the time the data is read.
        File source = copyFixtures(PACKAGE_NAME_APK_INT, PACKAGE_NAME_APK_INT);
        byte[] payload = "theme=dark\nwrap=true\n".getBytes(StandardCharsets.UTF_8);
        File data = new File(source, PACKAGE_NAME_APK_INT + ".dat");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(data))) {
            ZipEntry entry = new ZipEntry(PACKAGE_NAME_APK_INT + "/shared_prefs/settings.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            CRC32 crc = new CRC32();
            crc.update(payload);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(payload);
            zos.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(data.toPath());
        int at = indexOf(bytes, payload);
        assertTrue(at > 0);
        bytes[at] ^= 0x20;
        Files.write(data.toPath(), bytes);

        assertConversionFails(source, PACKAGE_NAME_APK_INT, "Backup failed for");
    }

    private void assertConversionFails(@NonNull File source, @NonNull String packageName,
                                       @NonNull String reason) {
        List<String> before = Arrays.asList(Objects.requireNonNull(source.list()));
        Map<String, List<String>> cachedBefore = cachedApkDirs(packageName);
        SBConverter converter = new SBConverter(Paths.get(new File(source, packageName + ".xml")));

        BackupException e = assertThrows(BackupException.class, converter::convert);

        assertTrue(e.getMessage(), e.getMessage().startsWith(reason));
        assertEquals("a half-made backup was left", Collections.emptyList(), backupFiles());
        assertNoNewCachedApk(cachedBefore, packageName);
        // The Swift files stay, so the user can try again or keep them
        List<String> after = Arrays.asList(Objects.requireNonNull(source.list()));
        Collections.sort(before);
        Collections.sort(after);
        assertEquals(before, after);
    }

    /** Copies one app's Swift files to a folder of their own, renamed for {@code asPackage}. */
    @NonNull
    private File copyFixtures(@NonNull String packageName, @NonNull String asPackage) throws IOException {
        File dir = new File(RoboUtils.getTestBaseDir(), "swift-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        File[] fixtures = Objects.requireNonNull(backupLocation.listFiles((d, name) -> name.startsWith(packageName + ".")));
        for (File fixture : fixtures) {
            String name = asPackage + fixture.getName().substring(packageName.length());
            Files.copy(fixture.toPath(), new File(dir, name).toPath());
        }
        return dir;
    }

    /** Gives a ZIP with no comment the one given, the way Swift Backup stores its manifest. */
    private static void setZipComment(@NonNull File zip, @NonNull String comment) throws IOException {
        byte[] bytes = Files.readAllBytes(zip.toPath());
        int end = bytes.length - 22;
        assertEquals(0x06054b50, ByteBuffer.wrap(bytes, end, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        byte[] text = comment.getBytes(StandardCharsets.UTF_8);
        ByteBuffer.wrap(bytes, end + 20, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) text.length);
        try (FileOutputStream out = new FileOutputStream(zip)) {
            out.write(bytes);
            out.write(text);
        }
    }

    @NonNull
    private static List<String> backupFiles() {
        List<String> files = new ArrayList<>();
        Path backups;
        try {
            backups = Prefs.Storage.getAppManagerDirectory().findFile(BackupItems.BACKUP_DIRECTORY);
        } catch (IOException e) {
            return files;
        }
        collectFiles(backups, "", files);
        return files;
    }

    private static void collectFiles(@NonNull Path dir, @NonNull String prefix, @NonNull List<String> files) {
        for (Path file : dir.listFiles()) {
            if (file.isDirectory()) {
                collectFiles(file, prefix + file.getName() + "/", files);
            } else {
                files.add(prefix + file.getName());
            }
        }
    }

    /** Fails if the converter left a copy of the APK in the cache since {@code before} was taken. */
    private static void assertNoNewCachedApk(@NonNull Map<String, List<String>> before,
                                             @NonNull String packageName) {
        File cache = cacheDir();
        for (Map.Entry<String, List<String>> dir : cachedApkDirs(packageName).entrySet()) {
            if (dir.getValue().equals(before.get(dir.getKey()))) continue;
            // Robolectric keeps every APK it parsed open for the rest of the run, and Windows won't
            // delete an open file (Android and Linux will). So a base.apk passes only if it can't be
            // deleted now either; one that can should have gone with the rest.
            File apk = new File(new File(cache, dir.getKey()), "base.apk");
            if (dir.getValue().equals(Collections.singletonList("base.apk")) && apk.exists() && !apk.delete()) {
                continue;
            }
            fail("the APK copy was left in the cache: " + dir);
        }
    }

    @NonNull
    private static File cacheDir() {
        File probe = FileCache.getGlobalFileCache().createCachedDir("probe");
        File cache = Objects.requireNonNull(probe.getParentFile());
        assertTrue(probe.delete());
        return cache;
    }

    /** The folders the converter copies an app's APK into, with what each holds. */
    @NonNull
    private static Map<String, List<String>> cachedApkDirs(@NonNull String packageName) {
        File cache = cacheDir();
        Map<String, List<String>> dirs = new TreeMap<>();
        File[] found = cache.listFiles((d, name) -> name.equals(packageName) || name.startsWith(packageName + "_"));
        for (File dir : Objects.requireNonNull(found)) {
            String[] names = dir.list();
            dirs.put(dir.getName(), names == null ? Collections.emptyList() : Arrays.asList(names));
        }
        return dirs;
    }

    /** Writes the OBB archive Swift Backup keeps next to the APK, and returns the OBB's bytes. */
    @NonNull
    private static byte[] writeObbArchive(@NonNull File dir, @NonNull String packageName) throws IOException {
        byte[] payload = new byte[40_000];
        new Random(42).nextBytes(payload);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(dir, packageName + ".exp")))) {
            zos.putNextEntry(new ZipEntry(packageName + "/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(packageName + "/" + OBB_NAME));
            zos.write(payload);
            zos.closeEntry();
        }
        return payload;
    }

    @NonNull
    private static Map<String, String> tarDigests(@NonNull Path[] tarFiles) throws IOException {
        Map<String, String> digests = new HashMap<>();
        try (SplitInputStream sis = new SplitInputStream(Arrays.asList(tarFiles));
             GzipCompressorInputStream gis = new GzipCompressorInputStream(new BufferedInputStream(sis), true);
             TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                digests.put(entry.getName(), sha256(tis));
            }
        }
        return digests;
    }

    @NonNull
    private static String sha256(@NonNull InputStream is) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = is.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; ++i) {
            for (int j = 0; j < needle.length; ++j) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
