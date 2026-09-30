// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

@RunWith(RobolectricTestRunner.class)
public class RootServiceManagerTest {
    @Test
    public void onlyRootModeRunsTheServiceFromTheAppsOwnCache() throws java.io.IOException {
        // A rooted phone in ADB mode still launches through the shell, which SELinux keeps out of
        // the app's data. hasRoot() only says su works, and asking can prompt.
        String source = new String(java.nio.file.Files.readAllBytes(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceManager.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int branch = source.indexOf("classPath = prepareMainJar(context).getAbsolutePath();");
        String condition = source.substring(source.lastIndexOf("if (", branch), branch);
        assertTrue(condition, condition.contains("Ops.isDirectRoot()"));
        assertFalse(condition, condition.contains("hasRoot()"));
    }

    private static java.nio.file.Path findRepoRoot() {
        java.nio.file.Path cursor = java.nio.file.Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (java.nio.file.Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    @Test
    public void mainJarStagingPathUsesInternalDeviceProtectedCache() {
        Context context = ApplicationProvider.getApplicationContext();
        File mainJar = RootServiceManager.getMainJarFile(context);

        assertEquals("main.jar", mainJar.getName());
        assertEquals(ContextUtils.getDeContext(context).getCacheDir(), mainJar.getParentFile());
        String mainJarPath = mainJar.getAbsolutePath();
        File[] externalCacheDirs = context.getExternalCacheDirs();
        assertNotNull(externalCacheDirs);
        for (File externalCacheDir : externalCacheDirs) {
            if (externalCacheDir == null) {
                continue;
            }
            assertFalse(mainJarPath.startsWith(externalCacheDir.getAbsolutePath()));
        }
    }
}
