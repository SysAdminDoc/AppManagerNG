// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

/**
 * The installer screen resolves one package file per queue item. Each one it moves past is closed,
 * and a queued install keeps the file it was reviewed from open until the service is done with it.
 */
@RunWith(RobolectricTestRunner.class)
public class ReviewedApkFileLifetimeTest {
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String OTHER_DIGEST = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void movingToTheNextItemClosesTheFileReviewedBefore() throws Throwable {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        ApkFile first = ApkSource.getApkSource(Uri.fromFile(baseApk("first.apk")), APK_MIME).resolve();
        File firstStaged = first.getBaseEntry().getFile(false);
        ApkFile second = ApkSource.getApkSource(Uri.fromFile(baseApk("second.apk")), APK_MIME).resolve();
        try {
            model.replaceApkFile(first);
            model.replaceApkFile(second);

            assertTrue(first.isClosed());
            assertFalse(firstStaged.exists());
            assertFalse(second.isClosed());
        } finally {
            second.close();
        }
    }

    @Test
    public void resolvingTheSameItemAgainKeepsOneReference() throws Throwable {
        PackageInstallerViewModel model = new PackageInstallerViewModel(ApplicationProvider.getApplicationContext());
        ApkSource source = ApkSource.getApkSource(Uri.fromFile(baseApk("again.apk")), APK_MIME);
        ApkFile apkFile = source.resolve();
        model.replaceApkFile(apkFile);
        // The screen is recreated and loads the same item: the source hands back the open instance
        ApkFile again = source.resolve();
        assertSame(apkFile, again);
        model.replaceApkFile(again);
        assertFalse(apkFile.isClosed());

        // One close is all the screen owes now
        apkFile.close();
        assertTrue(apkFile.isClosed());
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void aQueuedInstallKeepsItsFileUntilTheServiceIsDone() throws Throwable {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        PackageInstallerViewModel model = new PackageInstallerViewModel(app);
        ApkSource source = ApkSource.getApkSource(Uri.fromFile(baseApk("queued.apk")), APK_MIME);
        ApkFile reviewed = source.resolve();
        File staged = reviewed.getBaseEntry().getFile(false);
        model.replaceApkFile(reviewed);

        // What launching the install does, with a digest that makes the service stop before installing
        ApkQueueItem item = ApkQueueItem.fromApkSource(source);
        item.setAppLabel("Example");
        item.setSelectedSplits(new ArrayList<>());
        item.setReviewedPayload(OTHER_DIGEST, reviewed.getPayloadSize());
        item.setHoldsReviewedFile(reviewed.acquire());
        assertTrue(item.holdsReviewedFile());
        ApkQueueItem delivered = parcelled(item);
        assertTrue(delivered.holdsReviewedFile());

        // The screen moves on before the service gets to the item
        ApkFile next = ApkSource.getApkSource(Uri.fromFile(baseApk("next.apk")), APK_MIME).resolve();
        try {
            model.replaceApkFile(next);
            assertFalse(reviewed.isClosed());
            assertTrue(staged.isFile());

            runService(app, delivered);

            assertTrue(reviewed.isClosed());
            assertFalse(staged.exists());
        } finally {
            next.close();
        }
    }

    @Test
    public void aClosedFileCannotBeHeld() throws Throwable {
        ApkFile apkFile = ApkSource.getApkSource(Uri.fromFile(baseApk("closed.apk")), APK_MIME).resolve();
        apkFile.close();

        assertFalse(apkFile.acquire());
    }

    @Test
    public void anItemFromHistoryHoldsNothing() throws Exception {
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(
                Uri.fromFile(temporaryFolder.newFile("history.apk")), APK_MIME));
        item.setHoldsReviewedFile(true);

        ApkQueueItem restored = ApkQueueItem.DESERIALIZER.deserialize(item.serializeToJson());

        assertFalse(restored.holdsReviewedFile());
    }

    private static void runService(@NonNull Application app, @NonNull ApkQueueItem item) throws InterruptedException {
        Intent intent = new Intent(app, PackageInstallerService.class);
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, item);
        ServiceController<PackageInstallerService> controller = Robolectric.buildService(PackageInstallerService.class).create();
        PackageInstallerService service = controller.get();
        try {
            service.onStartCommand(new Intent(app, PackageInstallerService.class), 0, 1);
            // The service handles each queued item on its worker thread, which may write history
            Thread worker = new Thread(() -> service.onHandleIntent(intent));
            worker.start();
            worker.join(60_000);
            assertFalse(worker.isAlive());
            shadowOf(Looper.getMainLooper()).idle();
        } finally {
            controller.destroy();
        }
    }

    @NonNull
    private static ApkQueueItem parcelled(@NonNull ApkQueueItem item) {
        Parcel parcel = Parcel.obtain();
        try {
            item.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ApkQueueItem copy = ApkQueueItem.CREATOR.createFromParcel(parcel);
            assertEquals(item.getReviewedSha256(), copy.getReviewedSha256());
            return copy;
        } finally {
            parcel.recycle();
        }
    }

    @NonNull
    private File baseApk(@NonNull String name) throws IOException {
        File out = temporaryFolder.newFile(name);
        ClassLoader classLoader = ReviewedApkFileLifetimeTest.class.getClassLoader();
        assertNotNull(classLoader);
        try (ZipFile bundle = new ZipFile(new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile()));
             InputStream in = bundle.getInputStream(bundle.getEntry("base.apk"));
             OutputStream os = new FileOutputStream(out)) {
            IoUtils.copy(in, os);
        }
        return out;
    }
}
