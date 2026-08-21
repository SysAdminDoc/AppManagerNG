// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertArrayEquals;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class OABConverterTest {
    private static final String NEO_PATH_SUFFIX = "neo_backups";
    private static final String NEO_INSTANCE = "2021-05-29-18-27-13-717-user_0";
    private static final String PACKAGE_NAME_FULL = "dnsfilter.android";
    private static final String PACKAGE_NAME_APK_INT = "org.billthefarmer.editor";
    private static final String PACKAGE_NAME_INT = "ca.cmetcalfe.locationshare";
    private static final String PACKAGE_NAME_APK = "ademar.textlauncher";
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path tmpBackupPath;
    private TimeZone originalTimeZone;

    @Before
    public void setUp() throws IOException {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("backup-dir");
        Prefs.Storage.setVolumePath(tmpBackupPath.toString());
    }

    @After
    public void tearDown() {
        tmpBackupPath.delete();
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void detectsLegacyLayoutTest() throws BackupException, IOException {
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_INT);
        OABConverter.SourceLayout sourceLayout = OABConverter.detectSourceLayout(backupLocation);
        assertEquals(OABConverter.Layout.LEGACY, sourceLayout.layout);
        assertEquals(PACKAGE_NAME_INT, sourceLayout.packageName);
        assertEquals(backupLocation, sourceLayout.location);
    }

    @Test
    public void detectsNeoLayoutTest() throws BackupException, IOException {
        assert classLoader != null;
        Path packageDirectory = Paths.get(classLoader.getResource(NEO_PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_INT);
        Path instanceDirectory = packageDirectory.findFile(NEO_INSTANCE);
        OABConverter.SourceLayout directLayout = OABConverter.detectSourceLayout(instanceDirectory);
        assertEquals(OABConverter.Layout.NEO, directLayout.layout);
        assertEquals(PACKAGE_NAME_INT, directLayout.packageName);
        assertEquals(instanceDirectory, directLayout.location);
        assertEquals(instanceDirectory.findFile("backup.properties"), directLayout.propertiesFile);

        OABConverter.SourceLayout nestedLayout = OABConverter.detectSourceLayout(packageDirectory);
        assertEquals(OABConverter.Layout.NEO, nestedLayout.layout);
        assertEquals(instanceDirectory, nestedLayout.location);

        Path[] importLocations = ConvertUtils.getRelevantImportFiles(
                packageDirectory.requireParent(), ImportType.OAndBackup);
        assertArrayEquals(new Path[]{instanceDirectory}, importLocations);
    }

    @Test
    public void detectsDefaultAndFlatNeoLayoutsTest() throws BackupException, IOException {
        Path defaultInstance = createNeoFixture("neo-default", false);
        OABConverter.SourceLayout defaultLayout = OABConverter.detectSourceLayout(defaultInstance);
        assertEquals(OABConverter.Layout.NEO, defaultLayout.layout);
        assertEquals(PACKAGE_NAME_INT, defaultLayout.packageName);
        assertEquals(NEO_INSTANCE + ".properties", defaultLayout.propertiesFile.getName());
        assertArrayEquals(new Path[]{defaultInstance}, ConvertUtils.getRelevantImportFiles(
                defaultInstance.requireParent().requireParent(), ImportType.OAndBackup));

        Path flatInstance = createNeoFixture("neo-flat", true);
        OABConverter.SourceLayout flatLayout = OABConverter.detectSourceLayout(flatInstance);
        assertEquals(OABConverter.Layout.NEO, flatLayout.layout);
        assertEquals(PACKAGE_NAME_INT, flatLayout.packageName);
        assertEquals(flatInstance.getName() + ".properties", flatLayout.propertiesFile.getName());
        assertArrayEquals(new Path[]{flatInstance}, ConvertUtils.getRelevantImportFiles(
                flatInstance.requireParent(), ImportType.OAndBackup));
    }

    @Test
    public void rejectsUnknownLayoutTest() throws IOException {
        Path unknownLayout = tmpBackupPath.createNewDirectory("not-a-backup");
        BackupException exception = assertThrows(BackupException.class,
                () -> OABConverter.detectSourceLayout(unknownLayout));
        assertTrue(exception.getMessage().contains("<packageName>.log"));
        assertTrue(exception.getMessage().contains("<revision>.properties"));
        assertTrue(exception.getMessage().contains("backup.properties"));
        assertTrue(exception.getMessage().contains("YYYY-MM-DD-HH-MM-SS[-mmm]-user_N"));
    }

    @Test
    public void normalizesNeoArchiveEntriesTest() throws IOException {
        assertEquals("shared_prefs/settings.xml", OABConverter.getNeoRelativeBackupEntryName(
                "./shared_prefs/settings.xml", PACKAGE_NAME_INT));
        assertEquals("shared_prefs/settings.xml", OABConverter.getNeoRelativeBackupEntryName(
                PACKAGE_NAME_INT + "/shared_prefs/settings.xml", PACKAGE_NAME_INT));
        assertEquals("", OABConverter.getNeoRelativeBackupEntryName(PACKAGE_NAME_INT, PACKAGE_NAME_INT));
        assertThrows(IOException.class, () -> OABConverter.getNeoRelativeBackupEntryName(
                "../escape", PACKAGE_NAME_INT));
        assertThrows(IOException.class, () -> OABConverter.getNeoRelativeBackupEntryName(
                PACKAGE_NAME_INT + "/../escape", PACKAGE_NAME_INT));
    }

    @Test
    public void convertFullTest() throws BackupException, IOException {
        final List<String> internalStorage = Collections.emptyList();
        final List<String> externalStorage = Arrays.asList("files/PersonalDNSFilter/dnsfilter.conf",
                "files/PersonalDNSFilter/additionalHosts.txt",
                "files/PersonalDNSFilter/VERSION.TXT",
                "files/PersonalDNSFilter/log/trafficlog/trafficlog_0.log",
                "files/PersonalDNSFilter/dnsperf.info",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/IDX_VERSION",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx0",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx1",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx2",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx3",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx4",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx5",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx6",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx7",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx8",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx9",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx10",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx11",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx12",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx13",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx14",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.DLD_CNT");
        Collections.sort(internalStorage);
        Collections.sort(externalStorage);
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_FULL);
        OABConverter oabConvert = new OABConverter(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("OAndBackup", metadataV5.metadata.backupName);
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
        final List<String> internalStorage = Collections.singletonList("shared_prefs/org.billthefarmer.editor_preferences.xml");
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_APK_INT);
        OABConverter oabConvert = new OABConverter(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("OAndBackup", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertInternalStorageOnlyTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("shared_prefs/ca.cmetcalfe.locationshare_preferences.xml",
                "shared_prefs/_has_set_default_values.xml");
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_INT);
        OABConverter oabConvert = new OABConverter(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertLocationShareMetadata(metadataV5);
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("source.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertCurrentNeoBackupTest() throws BackupException, IOException {
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(NEO_PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_INT)
                .findFile(NEO_INSTANCE);
        assertCurrentNeoConversion(backupLocation);
    }

    @Test
    public void convertDefaultNeoBackupTest() throws BackupException, IOException {
        assertCurrentNeoConversion(createNeoFixture("neo-default-convert", false));
    }

    @Test
    public void convertFlatNeoBackupTest() throws BackupException, IOException {
        assertCurrentNeoConversion(createNeoFixture("neo-flat-convert", true));
    }

    private void assertCurrentNeoConversion(@NonNull Path backupLocation) throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList(
                "shared_prefs/",
                "shared_prefs/ca.cmetcalfe.locationshare_preferences.xml",
                "shared_prefs/_has_set_default_values.xml");
        OABConverter converter = new OABConverter(backupLocation);
        assertEquals(PACKAGE_NAME_INT, converter.getPackageName());
        converter.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(
                BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertLocationShareMetadata(metadataV5);
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        Collections.sort(files);
        Collections.sort(internalStorage);
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("source.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @NonNull
    private Path createNeoFixture(@NonNull String rootName, boolean flat) throws IOException {
        assert classLoader != null;
        Path sourceInstance = Paths.get(classLoader.getResource(NEO_PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_INT)
                .findFile(NEO_INSTANCE);
        Path root = tmpBackupPath.createNewDirectory(rootName);
        Path parent = flat ? root : root.createNewDirectory(PACKAGE_NAME_INT);
        String instanceName = flat ? PACKAGE_NAME_INT + "@" + NEO_INSTANCE : NEO_INSTANCE;
        Path instance = parent.createNewDirectory(instanceName);
        assertTrue(sourceInstance.findFile("data.tar").copyTo(instance) != null);
        Path propertiesFile = sourceInstance.findFile("backup.properties").copyTo(parent);
        assertTrue(propertiesFile != null);
        assertTrue(propertiesFile.renameTo(instanceName + ".properties"));
        return instance;
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        assert classLoader != null;
        Path backupLocation = Paths.get(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile())
                .findFile(PACKAGE_NAME_APK);
        OABConverter oabConvert = new OABConverter(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("OAndBackup", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    private static void assertLocationShareMetadata(BackupMetadataV5 metadataV5) {
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals(1622312833717L, metadataV5.info.backupTime);
        assertEquals(BackupFlags.BACKUP_MULTIPLE | BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_CACHE,
                metadataV5.info.flags.getFlags());
        assertEquals("OAndBackup", metadataV5.metadata.backupName);
        assertEquals("Location Share", metadataV5.metadata.label);
        assertEquals(PACKAGE_NAME_INT, metadataV5.metadata.packageName);
        assertEquals("1.4.1", metadataV5.metadata.versionName);
        assertEquals(8, metadataV5.metadata.versionCode);
        assertArrayEquals(new String[]{"/data/user/0/" + PACKAGE_NAME_INT}, metadataV5.metadata.dataDirs);
        assertFalse(metadataV5.metadata.isSystem);
        assertFalse(metadataV5.metadata.isSplitApk);
        assertArrayEquals(new String[0], metadataV5.metadata.splitConfigs);
        assertEquals("base.apk", metadataV5.metadata.apkName);
    }
}
