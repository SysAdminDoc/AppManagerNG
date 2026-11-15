// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.io.IoUtils;

/**
 * Moving to the next queue item cancels the load still reading the previous one, but a cancelled
 * load can run on. Whatever it resolves or reads after the next load started must not reach the
 * screen: the install would then check the wrong file's digest and the service would refuse it.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallerLoadGenerationTest {
    private static final String APK_MIME = "application/vnd.android.package-archive";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aFileResolvedByAReplacedLoadIsClosedAndTheNewerFileStays() throws Throwable {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        int replaced = model.startLoad();
        int current = model.startLoad();
        ApkFile newer = ApkSource.getApkSource(Uri.fromFile(baseApk("newer.apk")), APK_MIME).resolve();
        try {
            assertTrue(model.replaceApkFile(newer, current));
            // The slow load of the previous item only now finishes resolving its package
            ApkFile late = ApkSource.getApkSource(Uri.fromFile(baseApk("late.apk")), APK_MIME).resolve();
            File lateStaged = late.getBaseEntry().getFile(false);

            assertFalse(model.replaceApkFile(late, replaced));

            assertTrue(late.isClosed());
            assertFalse(lateStaged.exists());
            assertFalse(newer.isClosed());
            assertSame(newer, model.getApkFile());
        } finally {
            newer.close();
        }
    }

    @Test
    public void aReplacedLoadPublishesNothing() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        int replaced = model.startLoad();
        int current = model.startLoad();
        PackageInstallerViewModel.LoadedPackage newer = loaded("newer", "com.example.newer", null);
        PackageInstallerViewModel.LoadedPackage stale = loaded("stale", "com.example.stale", installed("com.example.stale"));
        ApkQueueItem staleItem = ApkQueueItem.fromApkSource(stale.apkSource);

        assertTrue(model.publish(current, newer, null));
        assertFalse(model.publish(replaced, stale, staleItem));
        shadowOf(Looper.getMainLooper()).idle();

        assertSame(newer.newPackageInfo, model.packageInfoLiveData().getValue());
        assertSame(newer.newPackageInfo, model.getNewPackageInfo());
        assertSame(newer.packageName, model.getPackageName());
        assertSame(newer.apkSource, model.getApkSource());
        assertNull(model.getInstalledPackageInfo());
        assertNull(staleItem.getPackageName());
        assertNull(staleItem.getAppLabel());
    }

    @Test
    public void aPackageThatIsNotInstalledDoesNotKeepThePreviousItemsInstalledVersion() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        int first = model.startLoad();
        assertTrue(model.publish(first, loaded("first", "com.example.first", installed("com.example.first")), null));
        assertNotNull(model.getInstalledPackageInfo());

        int second = model.startLoad();
        assertTrue(model.publish(second, loaded("second", "com.example.second", null), null));

        assertNull(model.getInstalledPackageInfo());
        assertFalse(model.isSignatureDifferent());
    }

    @Test
    public void aLoadThatFinishesAfterTheScreenIsGonePublishesNothing() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        int generation = model.startLoad();
        model.onCleared();

        assertFalse(model.publish(generation, loaded("gone", "com.example.gone", null), null));
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(model.getNewPackageInfo());
        assertNull(model.packageInfoLiveData().getValue());
    }

    @NonNull
    private PackageInstallerViewModel.LoadedPackage loaded(@NonNull String fileName, @NonNull String packageName,
                                                           @Nullable PackageInfo installed) throws IOException {
        ApkSource source = ApkSource.getApkSource(Uri.fromFile(temporaryFolder.newFile(fileName + ".apk")), APK_MIME);
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        return new PackageInstallerViewModel.LoadedPackage(source, packageInfo, installed, packageName, fileName,
                null, 0, null, installed != null);
    }

    @NonNull
    private static PackageInfo installed(@NonNull String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        return packageInfo;
    }

    @NonNull
    private File baseApk(@NonNull String name) throws IOException {
        File out = temporaryFolder.newFile(name);
        ClassLoader classLoader = InstallerLoadGenerationTest.class.getClassLoader();
        assertNotNull(classLoader);
        try (ZipFile bundle = new ZipFile(new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile()));
             InputStream in = bundle.getInputStream(bundle.getEntry("base.apk"));
             OutputStream os = new FileOutputStream(out)) {
            IoUtils.copy(in, os);
        }
        return out;
    }
}
