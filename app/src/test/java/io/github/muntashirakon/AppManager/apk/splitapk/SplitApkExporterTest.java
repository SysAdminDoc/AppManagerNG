// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class SplitApkExporterTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void getAllApkFilesFallsBackToPackageDirectorySplits() throws IOException {
        File packageDir = tmp.newFolder("split-package");
        File base = touch(packageDir, "base.apk");
        touch(packageDir, "split_config.en.apk");
        touch(packageDir, "split_config.hdpi.apk");
        touch(packageDir, "notes.txt");
        ApplicationInfo info = appInfo(base);

        List<Path> apkFiles = SplitApkExporter.getAllApkFiles(info);

        assertEquals(set("base.apk", "split_config.en.apk", "split_config.hdpi.apk"), names(apkFiles));
    }

    @Test
    public void getAllApkFilesDeduplicatesExplicitSplitPathsAndDirectoryScan() throws IOException {
        File packageDir = tmp.newFolder("dedupe-package");
        File base = touch(packageDir, "base.apk");
        File split = touch(packageDir, "split_config.en.apk");
        ApplicationInfo info = appInfo(base);
        info.splitPublicSourceDirs = new String[]{split.getAbsolutePath()};

        List<Path> apkFiles = SplitApkExporter.getAllApkFiles(info);

        assertEquals(set("base.apk", "split_config.en.apk"), names(apkFiles));
    }

    @Test
    public void getDeviceSpecificSplitApkNamesReturnsOnlyConfigSplits() throws IOException {
        File packageDir = tmp.newFolder("device-specific-package");
        File base = touch(packageDir, "base.apk");
        touch(packageDir, "split_config.arm64_v8a.apk");
        touch(packageDir, "feature.payments.apk");
        ApplicationInfo info = appInfo(base);

        List<String> splitNames = SplitApkExporter.getDeviceSpecificSplitApkNames(info);

        assertEquals(Arrays.asList("split_config.arm64_v8a.apk"), splitNames);
    }

    @Test
    public void metadataJsonLabelsDeviceSpecificSplits() throws Exception {
        File packageDir = tmp.newFolder("metadata-package");
        File base = touch(packageDir, "base.apk");
        File abiSplit = touch(packageDir, "split_config.arm64_v8a.apk");
        File featureSplit = touch(packageDir, "feature.payments.apk");
        ApksMetadata metadata = baseMetadata();
        metadata.sourceDeviceAbis.add("arm64-v8a");
        metadata.sourceDeviceDensityDpi = 440;

        metadata.setIncludedApkFiles(Arrays.asList(Paths.get(base), Paths.get(abiSplit), Paths.get(featureSplit)));

        JSONObject json = new JSONObject(metadata.getMetadataAsJson());
        JSONArray splitApks = json.getJSONArray("split_apks");
        assertEquals("split_config.arm64_v8a.apk", splitApks.getString(0));
        assertEquals("feature.payments.apk", splitApks.getString(1));
        JSONObject deviceSpecific = json.getJSONObject("device_specific_export");
        assertTrue(deviceSpecific.getBoolean("device_specific"));
        assertEquals("arm64-v8a", deviceSpecific.getJSONArray("source_device_abis").getString(0));
        assertEquals(440, deviceSpecific.getInt("source_device_density_dpi"));
        assertEquals("split_config.arm64_v8a.apk",
                deviceSpecific.getJSONArray("device_specific_splits").getString(0));
        assertTrue(deviceSpecific.getString("warning").contains("device-selected split APKs"));
    }

    @Test
    public void metadataJsonMarksFeatureOnlySplitsPortable() throws Exception {
        File packageDir = tmp.newFolder("portable-package");
        File base = touch(packageDir, "base.apk");
        File featureSplit = touch(packageDir, "feature.payments.apk");
        ApksMetadata metadata = baseMetadata();

        metadata.setIncludedApkFiles(Arrays.asList(Paths.get(base), Paths.get(featureSplit)));

        JSONObject deviceSpecific = new JSONObject(metadata.getMetadataAsJson())
                .getJSONObject("device_specific_export");
        assertFalse(deviceSpecific.getBoolean("device_specific"));
        assertEquals(0, deviceSpecific.getJSONArray("device_specific_splits").length());
        assertFalse(deviceSpecific.has("warning"));
    }

    @Test
    public void readMetadataParsesDeviceSpecificity() throws Exception {
        ApksMetadata metadata = new ApksMetadata();

        metadata.readMetadata("{"
                + "\"info_version\":1,"
                + "\"package_name\":\"com.example.app\","
                + "\"display_name\":\"Example\","
                + "\"version_name\":\"1.0\","
                + "\"version_code\":1,"
                + "\"min_sdk\":21,"
                + "\"target_sdk\":35,"
                + "\"split_apks\":[\"split_config.en.apk\",\"feature.payments.apk\"],"
                + "\"device_specific_export\":{"
                + "\"device_specific\":true,"
                + "\"source_device_abis\":[\"arm64-v8a\"],"
                + "\"source_device_density_dpi\":440,"
                + "\"device_specific_splits\":[\"split_config.en.apk\"]"
                + "}"
                + "}");

        assertTrue(metadata.deviceSpecificExport);
        assertEquals(Arrays.asList("split_config.en.apk", "feature.payments.apk"), metadata.splitApkNames);
        assertEquals(Arrays.asList("split_config.en.apk"), metadata.deviceSpecificSplitApkNames);
        assertEquals(Arrays.asList("arm64-v8a"), metadata.sourceDeviceAbis);
        assertEquals(440, metadata.sourceDeviceDensityDpi);
    }

    private static ApplicationInfo appInfo(File base) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.app";
        info.publicSourceDir = base.getAbsolutePath();
        info.sourceDir = base.getAbsolutePath();
        return info;
    }

    private static ApksMetadata baseMetadata() {
        ApksMetadata metadata = new ApksMetadata();
        metadata.packageName = "com.example.app";
        metadata.displayName = "Example";
        metadata.versionName = "1.0";
        metadata.versionCode = 1;
        metadata.minSdk = 21;
        metadata.targetSdk = 35;
        return metadata;
    }

    private static File touch(File parent, String name) throws IOException {
        File file = new File(parent, name);
        if (!file.createNewFile()) {
            throw new IOException("Could not create " + file);
        }
        return file;
    }

    private static Set<String> names(List<Path> paths) {
        Set<String> names = new LinkedHashSet<>();
        for (Path path : paths) {
            names.add(path.getName());
        }
        return names;
    }

    private static Set<String> set(String... values) {
        Set<String> names = new LinkedHashSet<>();
        for (String value : values) {
            names.add(value);
        }
        return names;
    }
}
