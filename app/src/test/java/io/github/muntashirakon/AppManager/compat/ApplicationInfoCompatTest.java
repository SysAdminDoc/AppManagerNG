// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class ApplicationInfoCompatTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void isOnlyDataInstalledWhenInstalledFlagAndSourceAreMissing() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.leftover";

        assertTrue(ApplicationInfoCompat.isOnlyDataInstalled(info));
        assertFalse(ApplicationInfoCompat.isInstalled(info));
    }

    @Test
    public void retainedSystemSourceIsNotDataOnly() throws IOException {
        File sourceApk = temporaryFolder.newFile("base.apk");
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.system";
        info.processName = "com.example.system";
        info.publicSourceDir = sourceApk.getAbsolutePath();

        assertFalse(ApplicationInfoCompat.isOnlyDataInstalled(info));
        assertFalse(ApplicationInfoCompat.isInstalled(info));
    }

    @Test
    public void installedPackageRequiresInstalledFlagAndReadableSource() throws IOException {
        File sourceApk = temporaryFolder.newFile("base.apk");
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.installed";
        info.processName = "com.example.installed";
        info.publicSourceDir = sourceApk.getAbsolutePath();
        info.flags = ApplicationInfo.FLAG_INSTALLED;

        assertTrue(ApplicationInfoCompat.isInstalled(info));
        assertFalse(ApplicationInfoCompat.isOnlyDataInstalled(info));
    }
}
