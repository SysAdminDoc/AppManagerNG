// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.ApplicationInfoApkSource;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;

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

    @Test
    public void aRowFoundInstalledAfterAllIsReadAgainInsteadOfChangedInPlace() throws IOException {
        String packageName = "com.example.installedafterall";
        Path base = Files.createTempFile("appmanagerng-installed", ".apk");
        base.toFile().deleteOnExit();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        // Installed as the list's own check sees it: flagged, with a process and an APK on disk
        packageInfo.applicationInfo = appInfo(base);
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.applicationInfo.processName = packageName;
        packageInfo.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        Application application = RuntimeEnvironment.getApplication();
        shadowOf(application.getPackageManager()).installPackage(packageInfo);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
        activity.setTheme(R.style.AppTheme_V2);
        MainRecyclerAdapter adapter = new MainRecyclerAdapter(activity);
        // What the list holds for an app it last saw installed for no user
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.isInstalled = false;
        int[] userIds = item.userIds;

        adapter.handleClick(item);

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(AppDetailsActivity.class.getName(), started.getComponent().getClassName());
        List<Intent> refreshes = new ArrayList<>();
        for (Intent intent : shadowOf(application).getBroadcastIntents()) {
            if (PackageChangeReceiver.ACTION_PACKAGE_ALTERED.equals(intent.getAction())) {
                refreshes.add(intent);
            }
        }
        // The list reads the package again and builds a new row for it
        assertEquals(1, refreshes.size());
        assertArrayEquals(new String[]{packageName},
                refreshes.get(0).getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST));
        // Filter and sort threads may be reading this one, so it's left as it was
        assertFalse(item.isInstalled);
        assertSame(userIds, item.userIds);
    }

    private static ApplicationInfo appInfo(Path base) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "com.example.app";
        info.publicSourceDir = base.toString();
        info.sourceDir = base.toString();
        return info;
    }
}
