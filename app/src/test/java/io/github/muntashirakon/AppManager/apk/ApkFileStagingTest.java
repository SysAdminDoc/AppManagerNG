// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class ApkFileStagingTest {
    private static final String APK_MIME = "application/vnd.android.package-archive";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aSharedFileIsReadOnceAndLaterChangesDontReachTheInstall() throws Throwable {
        File shared = extractBaseApk(temporaryFolder.newFile("shared.apk"));
        String reviewedSha256 = sha256(shared);
        long reviewedSize = shared.length();

        ApkFile apkFile = open(shared);
        try {
            assertEquals(reviewedSha256, apkFile.getPayloadSha256());
            assertEquals(reviewedSize, apkFile.getPayloadSize());

            // Another app rewrites the shared file after the review
            try (OutputStream out = new FileOutputStream(shared)) {
                out.write("not the reviewed package".getBytes());
            }

            File installed = apkFile.getBaseEntry().getFile(false);
            assertNotEquals(shared.getCanonicalFile(), installed.getCanonicalFile());
            assertEquals(reviewedSha256, sha256(installed));
            assertEquals(reviewedSize, installed.length());
        } finally {
            apkFile.close();
        }
    }

    @Test
    public void aFileInTheAppsOwnStorageIsReadWhereItIs() throws Throwable {
        // The context ApkFile itself reads, which a shared test sandbox may have set up for an earlier test
        Context context = ContextUtils.getContext();
        File own = new File(context.getFilesDir(), "own.apk");
        extractBaseApk(own);

        ApkFile apkFile = open(own);
        try {
            assertEquals(own.getCanonicalFile(), apkFile.getBaseEntry().getFile(false).getCanonicalFile());
            assertEquals(sha256(own), apkFile.getPayloadSha256());
            assertEquals(own.length(), apkFile.getPayloadSize());
        } finally {
            apkFile.close();
            //noinspection ResultOfMethodCallIgnored
            own.delete();
        }
    }

    @Test
    public void onlyFilesInsideTheDataDirectoryCountAsPrivate() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File own = new File(context.getFilesDir(), "inside.apk");
        //noinspection ResultOfMethodCallIgnored
        own.getParentFile().mkdirs();
        assertTrue(own.createNewFile() || own.isFile());
        File dataDir = new File(context.getApplicationInfo().dataDir);
        File outside = temporaryFolder.newFile("outside.apk");

        assertEquals(own.getCanonicalFile(), ApkFile.getPrivateLocalFile(context, Uri.fromFile(own)));
        assertNull(ApkFile.getPrivateLocalFile(context, Uri.fromFile(outside)));
        // A path that climbs out of the data directory is judged by where it ends up
        File neighbour = new File(dataDir.getParentFile(), dataDir.getName() + "-elsewhere/x.apk");
        //noinspection ResultOfMethodCallIgnored
        neighbour.getParentFile().mkdirs();
        assertTrue(neighbour.createNewFile() || neighbour.isFile());
        try {
            Uri escaping = Uri.fromFile(new File(dataDir, "files/../../" + dataDir.getName() + "-elsewhere/x.apk"));
            assertNull(ApkFile.getPrivateLocalFile(context, escaping));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            neighbour.delete();
        }
        assertNull(ApkFile.getPrivateLocalFile(context, Uri.parse("content://com.example.provider/own.apk")));
        assertNull(ApkFile.getPrivateLocalFile(context, Uri.fromFile(context.getFilesDir())));
    }

    @Test
    public void aPreviewReadsTheFileWhereItLiesWithNoCopy() throws Throwable {
        File shared = extractBaseApk(temporaryFolder.newFile("preview.apk"));
        Set<String> stagedBefore = stagedNames();

        ApkFile apkFile = ApkFile.openForPreview(Uri.fromFile(shared), APK_MIME);
        try {
            assertEquals(shared.getCanonicalFile(), apkFile.getBaseEntry().getFile(false).getCanonicalFile());
            // Never something to install from
            assertNull(apkFile.getPayloadSha256());
            assertEquals(stagedBefore, stagedNames());
        } finally {
            apkFile.close();
        }
        assertTrue(apkFile.isClosed());
        assertTrue(shared.isFile());
    }

    @Test
    public void aPackageThatFailsToParseLeavesNothingStaged() throws IOException {
        File broken = temporaryFolder.newFile("broken.apk");
        try (OutputStream out = new FileOutputStream(broken)) {
            out.write("not a package".getBytes());
        }
        Set<String> stagedBefore = stagedNames();

        try {
            ApkFile.createInstance(Uri.fromFile(broken), APK_MIME);
            fail("A file with no manifest opened");
        } catch (ApkFile.ApkFileException expected) {
            // The private copy it made before parsing is gone
        }

        assertEquals(stagedBefore, stagedNames());
    }

    @Test
    public void aCopyInPrivateStagingIsReadInPlaceAndOutlivesACacheTrim() throws Throwable {
        Context context = ContextUtils.getContext();
        File shared = extractBaseApk(temporaryFolder.newFile("provider.apk"));
        File copy = FileCache.copyToPrivateStaging(Paths.get(shared));
        try {
            // Inside the data directory, outside the cache the system may trim mid-install
            File stagingDir = FileCache.getPrivateStagingDir().getCanonicalFile();
            assertEquals(stagingDir, copy.getParentFile().getCanonicalFile());
            assertFalse(stagingDir.getPath().startsWith(context.getCacheDir().getCanonicalPath()));
            assertNotNull(ApkFile.getPrivateLocalFile(context, Uri.fromFile(copy)));

            ApkFile apkFile = open(copy);
            try {
                assertEquals(copy.getCanonicalFile(), apkFile.getBaseEntry().getFile(false).getCanonicalFile());
                assertEquals(sha256(shared), apkFile.getPayloadSha256());
            } finally {
                apkFile.close();
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            copy.delete();
        }
    }

    @Test
    public void roomIsOnlyPromisedForWhatTheDiskCanTake() {
        File stagingDir = FileCache.getPrivateStagingDir();
        assertTrue(stagingDir.mkdirs() || stagingDir.isDirectory());

        assertTrue(FileCache.makeRoom(stagingDir, 0));
        assertTrue(FileCache.makeRoom(stagingDir, 1));
        // No disk holds this much, however much cache the system clears
        assertFalse(FileCache.makeRoom(stagingDir, Long.MAX_VALUE));
    }

    /**
     * Screens close their package on the main thread, and an APKM opens through a service that
     * binds on the main thread. A close that waited for an open to finish could wait forever.
     */
    @Test
    public void closingOnePackageNeverWaitsForAnotherToOpen() throws Throwable {
        ApkFile open = open(extractBaseApk(temporaryFolder.newFile("open.apk")));
        File opening = extractBaseApk(temporaryFolder.newFile("opening.apk"));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ApkFile.sOpeningHook = () -> {
            inside.countDown();
            try {
                finish.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // ApkFileException is a Throwable, which a Callable can't throw
            Future<ApkFile> second = executor.submit(() -> {
                try {
                    return open(opening);
                } catch (ApkFile.ApkFileException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertTrue(inside.await(30, TimeUnit.SECONDS));

            // The other open is still going: holding and closing this one must not wait for it
            Future<Boolean> closed = executor.submit(() -> {
                assertTrue(open.acquire());
                open.close();
                open.close();
                return open.isClosed();
            });
            assertTrue(closed.get(10, TimeUnit.SECONDS));

            finish.countDown();
            ApkFile secondFile = second.get(60, TimeUnit.SECONDS);
            assertFalse(secondFile.isClosed());
            secondFile.close();
        } finally {
            ApkFile.sOpeningHook = null;
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @NonNull
    private static Set<String> stagedNames() {
        String[] names = FileCache.getPrivateStagingDir().list();
        return names != null ? new HashSet<>(Arrays.asList(names)) : new HashSet<>();
    }

    @NonNull
    private static ApkFile open(@NonNull File file) throws ApkFile.ApkFileException {
        int key = ApkFile.createInstance(Uri.fromFile(file), APK_MIME);
        ApkFile apkFile = ApkFile.getInstance(key);
        assertNotNull(apkFile);
        return apkFile;
    }

    @NonNull
    private static File extractBaseApk(@NonNull File out) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        out.getParentFile().mkdirs();
        ClassLoader classLoader = ApkFileStagingTest.class.getClassLoader();
        assertNotNull(classLoader);
        File bundle = new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile());
        try (ZipFile zipFile = new ZipFile(bundle)) {
            ZipEntry base = zipFile.getEntry("base.apk");
            try (InputStream in = zipFile.getInputStream(base);
                 OutputStream os = new FileOutputStream(out)) {
                IoUtils.copy(in, os);
            }
        }
        return out;
    }

    @NonNull
    private static String sha256(@NonNull File file) {
        return DigestUtils.getHexDigest(DigestUtils.SHA_256, file).toLowerCase(Locale.ROOT);
    }
}
