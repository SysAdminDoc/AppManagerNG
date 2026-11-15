// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Looper;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void aResultPostedJustBeforeTheNextItemStartedLoadingIsNotDelivered() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        List<PackageInfo> delivered = observe(model);
        int first = model.startLoad();
        assertTrue(model.publish(first, loaded("first", "com.example.first", null), null));

        // Next is tapped before the posted result reaches the screen
        model.startLoad();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(delivered.isEmpty());
    }

    @Test
    public void aFailurePostedJustBeforeTheNextItemStartedLoadingIsNotDelivered() {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        List<PackageInfo> delivered = observe(model);
        int first = model.startLoad();
        assertTrue(model.publishFailure(first));

        model.startLoad();
        shadowOf(Looper.getMainLooper()).idle();

        // Batch mode would have skipped the next item as unreadable
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void theNewestOfTwoQuickResultsIsDelivered() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        List<PackageInfo> delivered = observe(model);
        int first = model.startLoad();
        assertTrue(model.publish(first, loaded("first", "com.example.first", null), null));
        int second = model.startLoad();
        PackageInstallerViewModel.LoadedPackage newer = loaded("second", "com.example.second", null);
        assertTrue(model.publish(second, newer, null));

        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, delivered.size());
        assertSame(newer.newPackageInfo, delivered.get(0));
    }

    @Test
    public void aLoadCancelledForTheNextItemDoesNotReportItsFailureForThatItem() throws Exception {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        List<PackageInfo> delivered = observe(model);
        BlockingApkSource first = new BlockingApkSource();
        BlockingApkSource second = new BlockingApkSource();
        model.getPackageInfo(ApkQueueItem.fromApkSource(first));
        assertTrue(first.entered.await(30, TimeUnit.SECONDS));

        // Next: the interrupt makes the first load fail at once
        model.getPackageInfo(ApkQueueItem.fromApkSource(second));
        first.awaitFinished();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(delivered.isEmpty());

        // The item on screen still reports its own failure
        second.fail.countDown();
        awaitDelivery(delivered);
        second.awaitFinished();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, delivered.size());
        assertNull(delivered.get(0));
    }

    @Test
    public void installingAnExistingPackageKeepsTheNameItIsInstalledUnder() throws Exception {
        // A renamed package (original-package) stays installed under its old name, which its APK doesn't
        // declare, and install-existing has to ask for that name
        Context context = ApplicationProvider.getApplicationContext();
        File apk = installedApk();
        // Parsed before the install: once a package is installed from this path, Robolectric answers
        // getPackageArchiveInfo with the installed PackageInfo instead of parsing the APK as Android does
        PackageInfo parsed = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        assertNotNull(parsed);
        PackageInfo installed = new PackageInfo();
        installed.packageName = "com.example.renamed";
        installed.applicationInfo = new ApplicationInfo();
        installed.applicationInfo.packageName = installed.packageName;
        installed.applicationInfo.sourceDir = apk.getAbsolutePath();
        installed.applicationInfo.publicSourceDir = apk.getAbsolutePath();
        shadowOf(context.getPackageManager()).installPackage(installed);
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(apk.getAbsolutePath(), parsed);
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        List<PackageInfo> delivered = observe(model);
        ApkQueueItem item = ApkQueueItem.fromIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("package:com.example.renamed")), null).get(0);

        try {
            model.getPackageInfo(item);
            awaitDelivery(delivered);

            assertNotNull("The installed package couldn't be read", delivered.get(0));
            assertEquals("io.github.muntashirakon.AppManager", delivered.get(0).packageName);
            assertEquals("com.example.renamed", model.getPackageName());
            assertEquals("com.example.renamed", item.getPackageName());
        } finally {
            model.onCleared();
        }
    }

    @NonNull
    private static List<PackageInfo> observe(@NonNull PackageInstallerViewModel model) {
        List<PackageInfo> delivered = new ArrayList<>();
        model.packageInfoLiveData().observeForever(delivered::add);
        return delivered;
    }

    private static void awaitDelivery(@NonNull List<PackageInfo> delivered) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (delivered.isEmpty()) {
            assertTrue("Nothing reached the screen", System.nanoTime() < deadline);
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    /**
     * Resolves nothing: blocks until told to fail or interrupted, then fails.
     */
    private static final class BlockingApkSource extends ApkSource {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch fail = new CountDownLatch(1);
        private final CountDownLatch mThrown = new CountDownLatch(1);
        volatile Thread worker;

        @NonNull
        @Override
        public ApkFile resolve() throws ApkFile.ApkFileException {
            worker = Thread.currentThread();
            entered.countDown();
            try {
                fail.await();
            } catch (InterruptedException e) {
                throw new ApkFile.ApkFileException("Interrupted", e);
            } finally {
                mThrown.countDown();
            }
            throw new ApkFile.ApkFileException("Unreadable");
        }

        /**
         * Waits until the load that called {@link #resolve()} has finished, failure handling included, and its
         * pool thread is idle again.
         */
        void awaitFinished() throws InterruptedException {
            assertTrue("The load didn't fail", mThrown.await(30, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            Thread thread = worker;
            assertNotNull(thread);
            while (true) {
                Thread.State state = thread.getState();
                if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
                        || state == Thread.State.TERMINATED) {
                    return;
                }
                assertTrue("The load didn't finish", System.nanoTime() < deadline);
                Thread.sleep(10);
            }
        }

        @Nullable
        @Override
        public ApkFile resolveIfOpen() {
            return null;
        }

        @NonNull
        @Override
        public ApkSource toCachedSource() {
            return this;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A base APK in a folder of its own: an installed package's other APKs beside it are read as its splits.
     */
    @NonNull
    private File installedApk() throws IOException {
        File apk = new File(temporaryFolder.newFolder("installed"), "base.apk");
        assertTrue(baseApk("installed-source.apk").renameTo(apk));
        return apk;
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
