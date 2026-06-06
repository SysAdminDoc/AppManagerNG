// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import static org.junit.Assert.assertEquals;

import android.content.pm.ApplicationInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.io.Path;

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

    private static ApplicationInfo appInfo(File base) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.app";
        info.publicSourceDir = base.getAbsolutePath();
        info.sourceDir = base.getAbsolutePath();
        return info;
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
