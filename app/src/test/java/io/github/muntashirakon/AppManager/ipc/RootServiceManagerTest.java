// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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
