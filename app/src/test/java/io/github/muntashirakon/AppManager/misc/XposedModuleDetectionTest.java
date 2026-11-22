// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Issue 42: App Info names an Xposed module from either API: the legacy meta-data (xposedmodule, xposedminversion,
 * xposeddescription) or a modern module's META-INF/xposed files (java_init.list, module.prop).
 */
@RunWith(RobolectricTestRunner.class)
public class XposedModuleDetectionTest {
    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void theLegacyFlagAloneMarksALegacyModule() throws IOException {
        Bundle metaData = new Bundle();
        metaData.putBoolean("xposedmodule", true);
        metaData.putString("xposeddescription", "Hides the clock");
        ApplicationInfo app = app(metaData);

        try (ZipFile apk = apk()) {
            assertNull("null means legacy", XposedModuleInfo.isXposedModule(app, apk));
            XposedModuleInfo info = new XposedModuleInfo(app, null);
            assertTrue(info.legacy);
            assertEquals("Hides the clock",
                    info.getDescription(RuntimeEnvironment.getApplication().getPackageManager()).toString());
        }
    }

    @Test
    public void theMinimumVersionAloneStillMarksALegacyModule() throws IOException {
        Bundle metaData = new Bundle();
        metaData.putInt("xposedminversion", 82);

        try (ZipFile apk = apk()) {
            assertNull(XposedModuleInfo.isXposedModule(app(metaData), apk));
            assertEquals(82, new XposedModuleInfo(app(metaData), null).minVersion);
        }
    }

    @Test
    public void aJavaInitListMarksAModernModule() throws IOException {
        try (ZipFile apk = apk("META-INF/xposed/java_init.list", "com.example.Hook")) {
            assertEquals(Boolean.TRUE, XposedModuleInfo.isXposedModule(app(null), apk));
            assertFalse(new XposedModuleInfo(app(null), apk).legacy);
        }
    }

    @Test
    public void aModulePropMarksAModernModule() throws IOException {
        try (ZipFile apk = apk("META-INF/xposed/module.prop", "minApiVersion=100\ntargetApiVersion=101\n")) {
            assertEquals(Boolean.TRUE, XposedModuleInfo.isXposedModule(app(null), apk));
            assertEquals(101, new XposedModuleInfo(app(null), apk).targetVersion);
        }
    }

    @Test
    public void anOrdinaryAppIsNoModule() throws IOException {
        Bundle metaData = new Bundle();
        metaData.putBoolean("xposedmodule", false);
        try (ZipFile apk = apk("classes.dex", "dex")) {
            assertEquals(Boolean.FALSE, XposedModuleInfo.isXposedModule(app(null), apk));
            assertEquals(Boolean.FALSE, XposedModuleInfo.isXposedModule(app(metaData), apk));
        }
    }

    @NonNull
    private static ApplicationInfo app(@Nullable Bundle metaData) {
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = "com.example.module";
        app.metaData = metaData;
        return app;
    }

    @NonNull
    private ZipFile apk(@NonNull String... nameAndContent) throws IOException {
        File file = mTemp.newFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zos.write(new byte[]{0});
            zos.closeEntry();
            for (int i = 0; i + 1 < nameAndContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameAndContent[i]));
                zos.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new ZipFile(file);
    }
}
