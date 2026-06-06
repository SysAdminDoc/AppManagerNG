// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.ApplicationInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.ApplicationInfoApkSource;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

@RunWith(RobolectricTestRunner.class)
public class MainRecyclerAdapterReinstallIntentTest {
    @Test
    public void reinstallIntentUsesApplicationInfoSourceForSplitPackages() throws IOException {
        Path base = Files.createTempFile("appmanagerng-base", ".apk");
        Path split = Files.createTempFile("appmanagerng-split", ".apk");
        try {
            ApplicationInfo info = appInfo(base);
            info.splitPublicSourceDirs = new String[]{split.toString()};

            Intent intent = MainRecyclerAdapter.getReinstallApkSourceIntent(RuntimeEnvironment.getApplication(), info);

            assertNotNull(intent);
            assertNull(intent.getData());
            ApkSource apkSource = IntentCompat.getUnwrappedParcelableExtra(intent, "link", ApkSource.class);
            assertTrue(apkSource instanceof ApplicationInfoApkSource);
        } finally {
            Files.deleteIfExists(split);
            Files.deleteIfExists(base);
        }
    }

    @Test
    public void reinstallIntentRequiresReadableBaseApk() {
        ApplicationInfo info = appInfo(java.nio.file.Paths.get("missing-base.apk"));

        assertNull(MainRecyclerAdapter.getReinstallApkSourceIntent(RuntimeEnvironment.getApplication(), info));
    }

    private static ApplicationInfo appInfo(Path base) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.app";
        info.publicSourceDir = base.toString();
        info.sourceDir = base.toString();
        return info;
    }
}
