// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.DeadObjectException;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.fm.FmItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

/**
 * The app's side of the isolated thumbnail decoder: whatever the decoder process does (hang, die,
 * reply with nonsense, never start), the file list gets a bitmap within bounds or {@code null},
 * and never waits on it from the main thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36})
public class IsolatedThumbnailsTest {
    private static final int MAX = ThumbnailDecoder.MAX_SIDE;
    private static final String DOCUMENT_AUTHORITY = "io.github.muntashirakon.AppManager.test.documents";
    private static final String SECTION_AUTHORITY = "io.github.muntashirakon.AppManager.test.sections";

    private static volatile File sServedDocument;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final FakeConnector mConnector = new FakeConnector();

    @After
    public void tearDown() {
        mWorker.shutdownNow();
    }

    @Test
    public void mainThreadNeverWaitsOnTheDecoder() throws IOException {
        mConnector.decoder = reply(new int[]{1, 1, Color.RED});
        IsolatedThumbnails thumbnails = thumbnails(5_000, 5_000);
        assertNull(thumbnails.decode(write(16), ThumbnailDecoder.KIND_IMAGE, null, 1024));
        try (ParcelFileDescriptor fd = open(16)) {
            assertNull(thumbnails.decode(fd, ThumbnailDecoder.KIND_IMAGE, null, 1024));
        }
        assertEquals(0, mConnector.connects.get());
    }

    @Test
    public void validReplyBecomesABitmap() throws Exception {
        mConnector.decoder = reply(new int[]{2, 1, Color.RED, Color.BLUE});
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        Bitmap bitmap = onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024));
        assertNotNull(bitmap);
        assertEquals(2, bitmap.getWidth());
        assertEquals(1, bitmap.getHeight());
        assertEquals(Color.RED, bitmap.getPixel(0, 0));
        assertEquals(Color.BLUE, bitmap.getPixel(1, 0));
        assertEquals(0, mConnector.disconnects.get());
    }

    @Test
    public void fileOverItsBudgetIsNeverSent() throws Exception {
        mConnector.decoder = reply(new int[]{1, 1, Color.RED});
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(2048);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(0, mConnector.connects.get());
    }

    @Test
    public void decoderThatNeverStartsGivesNoThumbnail() throws Exception {
        mConnector.decoder = null;
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(1, mConnector.connects.get());
    }

    @Test
    public void hungDecoderTimesOutAndIsLetGo() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new int[]{1, 1, Color.RED};
            }
        };
        // 100 ms for the decoder plus 100 ms of grace
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(mConnector, 5_000, 100, 100, 60_000);
        File file = write(16);
        try {
            long start = System.nanoTime();
            assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
            assertTrue("gave up in time", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
            assertEquals(1, mConnector.disconnects.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void deadDecoderIsLetGoAndTheNextCallStartsAnother() throws Exception {
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) throws RemoteException {
                throw new DeadObjectException();
            }

            @Override
            public boolean pingBinder() {
                // What a decoder process that's gone answers
                return false;
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(1, mConnector.disconnects.get());

        mConnector.decoder = reply(new int[]{1, 1, Color.GREEN});
        Bitmap bitmap = onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024));
        assertNotNull(bitmap);
        assertEquals(Color.GREEN, bitmap.getPixel(0, 0));
        assertEquals(2, mConnector.connects.get());
    }

    @Test
    public void decoderThatFailsButStaysUpIsKept() throws Exception {
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) throws RemoteException {
                // The reply didn't fit in the binder buffer; the decoder itself is fine
                throw new TransactionTooLargeException();
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals("a live decoder is kept", 0, mConnector.disconnects.get());
    }

    @Test
    public void decoderThatFillsTheBinderBufferIsKept() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) throws RemoteException {
                if (calls.incrementAndGet() == 1) {
                    // How Android reports a failed transaction with a small parcel, out of buffer
                    // space or not: the decoder still answers a ping
                    throw new DeadObjectException("Transaction failed on small parcel");
                }
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals("a decoder that answers is kept", 0, mConnector.disconnects.get());
        assertNotNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
    }

    @Test
    public void pdfsReachTheDecoderOneAtATimeWithoutUsingUpTheirTime() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                most.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    // Each well within its own time, the three together well past it
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                inFlight.decrementAndGet();
                return new int[]{1, 1, Color.RED};
            }
        };
        // 1 second for the decoder plus 500 ms of grace
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(mConnector, 5_000, 1_000, 500, 60_000);
        File file = write(16);
        ExecutorService callers = Executors.newFixedThreadPool(3);
        try {
            List<Future<Bitmap>> results = new ArrayList<>();
            for (int i = 0; i < 3; ++i) {
                results.add(callers.submit(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_PDF, null, 1024)));
            }
            for (Future<Bitmap> result : results) {
                assertNotNull("no PDF ran out of time waiting for another", result.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, most.get());
            assertEquals(0, mConnector.disconnects.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void plainFileIsHandedOverDirectly() throws Exception {
        AtomicLong seen = new AtomicLong(-1);
        List<String> copies = new ArrayList<>();
        File cacheDir = new File(FileUtils.getCachePath(), "files");
        List<String> before = listOf(cacheDir);
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                seen.set(fd.getStatSize());
                copies.addAll(newSince(cacheDir, before));
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        Path path = Paths.get(write(24));

        assertNotNull(onWorker(() -> thumbnails.decode(path, ThumbnailDecoder.KIND_IMAGE, null)));
        assertEquals(24, seen.get());
        assertEquals(Collections.emptyList(), copies);
    }

    @Test
    public void archiveEntryIsCopiedWithinTheCopyBudgetAndNotLeftExtracted() throws Exception {
        File zip = mTemp.newFile("small.zip");
        writeZip(zip, "page.pdf", 16);
        Path mountPoint = Paths.get("/tmp/am_thumbnail_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(zip), "application/zip");
        try {
            Path entry = mountPoint.findFile("page.pdf");
            assertNull(entry.getFile());

            assertEquals(16, decodeLeavingNothingBehind(entry, ThumbnailDecoder.KIND_PDF));
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    @Test
    public void archiveEntryThatUnderstatesItsSizeIsCutOffAtTheCopyBudget() throws Exception {
        File zip = mTemp.newFile("lying.zip");
        writeZip(zip, "page.pdf", IsolatedThumbnails.MAX_COPY_BYTES + 1);
        // The central directory says 16 bytes; the entry inflates to more than a copy may hold
        declareUncompressedSize(zip, 16);
        Path mountPoint = Paths.get("/tmp/am_thumbnail_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(zip), "application/zip");
        try {
            mConnector.decoder = reply(new int[]{1, 1, Color.RED});
            IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
            Path entry = mountPoint.findFile("page.pdf");
            assertEquals(16, entry.length());
            File cacheDir = new File(FileUtils.getCachePath(), "files");
            List<String> before = listOf(cacheDir);

            assertNull(onWorker(() -> thumbnails.decode(entry, ThumbnailDecoder.KIND_PDF, null)));
            assertEquals("not sent", 0, mConnector.connects.get());
            assertEquals("nothing left in the cache while the archive is open", Collections.emptyList(),
                    newSince(cacheDir, before));
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    @Test
    public void apkEntryIsStreamedWithoutExtractingIt() throws Exception {
        File apkFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("oandbackups/ademar.textlauncher/base.apk")).getFile());
        long manifestSize;
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            manifestSize = zipFile.getEntry("AndroidManifest.xml").getSize();
        }
        Path mountPoint = Paths.get("/tmp/am_thumbnail_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(apkFile),
                "application/vnd.android.package-archive");
        try {
            Path entry = mountPoint.findFile("AndroidManifest.xml");
            assertEquals("the entry's own size, not its directory record's", manifestSize, entry.length());

            assertEquals(manifestSize, decodeLeavingNothingBehind(entry, ThumbnailDecoder.KIND_IMAGE));
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    @Test
    public void archiveEntryBehindThisAppsOwnProviderIsReadAsTheEntry() throws Exception {
        ProviderInfo info = new ProviderInfo();
        info.authority = FmProvider.AUTHORITY;
        info.grantUriPermissions = true;
        Robolectric.buildContentProvider(FmProvider.class).create(info);
        File zip = mTemp.newFile("shared.zip");
        writeZip(zip, "page.pdf", 16);
        Path mountPoint = Paths.get("/tmp/am_thumbnail_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(zip), "application/zip");
        try {
            Uri shared = FmProvider.getContentUri(mountPoint.findFile("page.pdf"));
            assertEquals(ContentResolver.SCHEME_CONTENT, shared.getScheme());

            // Through the provider, the entry would be extracted whole and stay in the cache
            assertEquals(16, decodeLeavingNothingBehind(Paths.get(shared), ThumbnailDecoder.KIND_PDF));
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    @Test
    public void documentWithoutAWholeFileDescriptorIsCopiedAndTheCopyDeleted() throws Exception {
        Robolectric.setupContentProvider(SectionProvider.class, SECTION_AUTHORITY);
        sServedDocument = write(16);
        Path path = Paths.get(Uri.parse("content://" + SECTION_AUTHORITY + "/doc.pdf"));

        assertEquals(16, decodeLeavingNothingBehind(path, ThumbnailDecoder.KIND_PDF));
    }

    @Test
    public void documentThatHidesItsSizeIsCutOffAtTheCopyBudget() throws Exception {
        Robolectric.setupContentProvider(SectionProvider.class, SECTION_AUTHORITY);
        File large = mTemp.newFile();
        try (RandomAccessFile file = new RandomAccessFile(large, "rw")) {
            file.setLength(IsolatedThumbnails.MAX_COPY_BYTES + 1);
        }
        sServedDocument = large;
        mConnector.decoder = reply(new int[]{1, 1, Color.RED});
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        Path path = Paths.get(Uri.parse("content://" + SECTION_AUTHORITY + "/doc.pdf"));
        assertEquals("no size to go by", 0, path.length());
        File cacheDir = new File(FileUtils.getCachePath(), "files");
        List<String> before = listOf(cacheDir);

        assertNull(onWorker(() -> thumbnails.decode(path, ThumbnailDecoder.KIND_PDF, null)));
        assertEquals("not sent", 0, mConnector.connects.get());
        assertEquals("the partial copy is deleted", Collections.emptyList(), newSince(cacheDir, before));
    }

    @Test
    public void archiveEntryOverTheCopyBudgetIsNeverExtracted() throws Exception {
        File zip = mTemp.newFile("large.zip");
        // Within a PDF's own budget, past what a copy may hold. Zeros compress to almost nothing.
        writeZip(zip, "large.pdf", IsolatedThumbnails.MAX_COPY_BYTES + 1);
        Path mountPoint = Paths.get("/tmp/am_thumbnail_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(zip), "application/zip");
        try {
            mConnector.decoder = reply(new int[]{1, 1, Color.RED});
            IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
            Path entry = mountPoint.findFile("large.pdf");

            assertNull(onWorker(() -> thumbnails.decode(entry, ThumbnailDecoder.KIND_PDF, null)));
            assertEquals("not extracted, not sent", 0, mConnector.connects.get());
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    @Test
    public void copyStopsAtItsBudget() throws IOException {
        File out = mTemp.newFile();
        assertTrue(IsolatedThumbnails.copyAtMost(new ByteArrayInputStream(new byte[100]), out, 100));
        assertEquals(100, out.length());
        // A source that reported no length, or the wrong one, is still held to the budget
        assertFalse(IsolatedThumbnails.copyAtMost(new ByteArrayInputStream(new byte[101]), out, 100));
        assertTrue("stopped before the budget was passed", out.length() <= 100);
    }

    @Test
    public void decoderWhoseBinderDiedIsLetGo() throws Exception {
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public boolean isBinderAlive() {
                return false;
            }

            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) throws RemoteException {
                throw new RemoteException("gone");
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(1, mConnector.disconnects.get());
    }

    @Test
    public void noMoreThanTwoCallsReachTheDecoderAtOnce() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                most.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                inFlight.decrementAndGet();
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(30_000, 60_000);
        File file = write(16);
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Future<Bitmap>> results = new ArrayList<>();
            for (int i = 0; i < 4; ++i) {
                results.add(callers.submit(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (inFlight.get() < IsolatedThumbnails.MAX_CONCURRENT_CALLS && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            // Time for a third call to get in, if it could
            Thread.sleep(300);
            assertEquals(IsolatedThumbnails.MAX_CONCURRENT_CALLS, inFlight.get());
            release.countDown();
            for (Future<Bitmap> result : results) {
                assertNotNull(result.get(20, TimeUnit.SECONDS));
            }
            assertEquals(IsolatedThumbnails.MAX_CONCURRENT_CALLS, most.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void documentIsHandedOverWithoutACopy() throws Exception {
        Robolectric.setupContentProvider(DocumentProvider.class, DOCUMENT_AUTHORITY);
        sServedDocument = write(16);
        DocumentProvider.sOpens.set(0);
        AtomicLong seen = new AtomicLong(-1);
        List<String> copies = new ArrayList<>();
        File cacheDir = new File(FileUtils.getCachePath(), "files");
        List<String> before = listOf(cacheDir);
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                seen.set(fd.getStatSize());
                // A copy would sit in the file cache for as long as the decoder reads it
                copies.addAll(newSince(cacheDir, before));
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        Path path = Paths.get(Uri.parse("content://" + DOCUMENT_AUTHORITY + "/doc.pdf"));

        Bitmap bitmap = onWorker(() -> thumbnails.decode(path, ThumbnailDecoder.KIND_PDF, null));

        assertNotNull(bitmap);
        assertEquals(16, seen.get());
        assertEquals("the document's own descriptor, not a copy", Collections.emptyList(), copies);
        assertEquals(1, DocumentProvider.sOpens.get());
    }

    @Test
    public void fileListThumbnailsRunOnTheirOwnTwoThreads() throws IOException {
        Executor executor = new FmIconFetcher(new FmItem(Paths.get(write(16)))).getExecutor();
        assertNotSame(ThreadUtils.getBackgroundThreadExecutor(), executor);
        assertTrue(executor instanceof ThreadPoolExecutor);
        assertEquals(IsolatedThumbnails.MAX_CONCURRENT_CALLS, ((ThreadPoolExecutor) executor).getMaximumPoolSize());
    }

    @Test
    public void replyLargerThanAThumbnailIsDropped() throws Exception {
        int side = MAX + 1;
        int[] pixels = new int[2 + side * side];
        pixels[0] = side;
        pixels[1] = side;
        mConnector.decoder = reply(pixels);
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);
        File file = write(16);
        assertNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
    }

    @Test
    public void malformedRepliesAreNoBitmap() {
        assertNull(IsolatedThumbnails.toBitmap(null, MAX));
        assertNull(IsolatedThumbnails.toBitmap(new int[0], MAX));
        assertNull(IsolatedThumbnails.toBitmap(new int[]{1}, MAX));
        assertNull(IsolatedThumbnails.toBitmap(new int[]{0, 1}, MAX));
        assertNull(IsolatedThumbnails.toBitmap(new int[]{1, -1, 0}, MAX));
        // Fewer or more pixels than the size says
        assertNull(IsolatedThumbnails.toBitmap(new int[]{2, 2, 1, 2, 3}, MAX));
        assertNull(IsolatedThumbnails.toBitmap(new int[]{2, 1, 1, 2, 3}, MAX));
        // 65536 times 65536 overflows an int to 0, which would match a reply of just the size
        assertNull(IsolatedThumbnails.toBitmap(new int[]{65536, 65536}, Integer.MAX_VALUE));
        assertNull(IsolatedThumbnails.toBitmap(new int[]{MAX + 1, 1, 0}, MAX));

        Bitmap bitmap = IsolatedThumbnails.toBitmap(new int[]{1, 1, Color.RED}, MAX);
        assertNotNull(bitmap);
        assertEquals(Color.RED, bitmap.getPixel(0, 0));
    }

    @Test
    public void decoderIsLetGoOnceIdleButNotDuringACall() throws Exception {
        CountDownLatch slowCallRunning = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                if (calls.incrementAndGet() == 2) {
                    slowCallRunning.countDown();
                    try {
                        // Longer than the idle timeout the first call left behind
                        Thread.sleep(900);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(mConnector, 5_000, 5_000, 1_000, 300);
        File file = write(16);
        assertNotNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertNotNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals("no unbind while a call ran", 0, mConnector.disconnects.get());
        assertTrue("unbound once idle", mConnector.disconnected.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void idleTimerKeepsNoCancelledTimersAndNoThreadOnceIdle() throws Exception {
        mConnector.decoder = reply(new int[]{1, 1, Color.RED});
        IsolatedThumbnails thumbnails = thumbnails(5_000, 1_000);
        File file = write(16);
        for (int i = 0; i < 5; ++i) {
            assertNotNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        }
        // Each call cancelled the timer the one before it set
        assertEquals("only the last call's timer is queued", 1, thumbnails.getPendingIdleTimers());
        assertTrue("unbound once idle", mConnector.disconnected.await(10, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thumbnails.getIdleTimerThreads() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("no thread waits for a timer that isn't there", 0, thumbnails.getIdleTimerThreads());
        // And the next call still gets a timer, on a new thread
        assertNotNull(onWorker(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(1, thumbnails.getPendingIdleTimers());
        assertTrue("a timer thread is back", thumbnails.getIdleTimerThreads() > 0);
    }

    @Test
    public void bindsTheRealServiceConnection() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AtomicInteger kills = new AtomicInteger();
        ThumbnailDecoderBinder service = new ThumbnailDecoderBinder(
                (fd, kind, label, maxSide) -> Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888),
                kills::incrementAndGet);
        ComponentName component = new ComponentName(app, ThumbnailDecoderService.class);
        shadowOf(app).setComponentNameAndServiceForBindService(component, service);
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(new IsolatedThumbnails.ServiceConnector(app),
                5_000, 5_000, 1_000, 60_000);
        try {
            File file = write(16);
            Bitmap bitmap = onWorkerWhileLooping(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024));
            assertNotNull(bitmap);
            assertEquals(4, bitmap.getWidth());
            assertEquals(3, bitmap.getHeight());
            assertEquals(1, shadowOf(app).getBoundServiceConnections().size());
            assertTrue(shadowOf(app).getUnboundServiceConnections().isEmpty());
            assertEquals(0, kills.get());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void realConnectionUnbindsADeadDecoderAndBindsAgain() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AtomicInteger calls = new AtomicInteger();
        IThumbnailDecoder.Stub service = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) throws RemoteException {
                if (calls.incrementAndGet() == 1) {
                    throw new DeadObjectException();
                }
                return new int[]{1, 1, Color.RED};
            }

            @Override
            public boolean pingBinder() {
                // Gone after the first call, as a decoder process that died would be
                return calls.get() != 1;
            }
        };
        shadowOf(app).setComponentNameAndServiceForBindService(
                new ComponentName(app, ThumbnailDecoderService.class), service);
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(new IsolatedThumbnails.ServiceConnector(app),
                5_000, 5_000, 1_000, 60_000);
        File file = write(16);
        assertNull(onWorkerWhileLooping(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(1, shadowOf(app).getUnboundServiceConnections().size());
        // Bound again for the next file
        assertNotNull(onWorkerWhileLooping(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024)));
        assertEquals(2, calls.get());
    }

    @Test
    public void realConnectionNoticesADecoderThatDiedBetweenCalls() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AtomicBoolean alive = new AtomicBoolean(true);
        IThumbnailDecoder.Stub service = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                return new int[]{1, 1, Color.RED};
            }

            @Override
            public boolean isBinderAlive() {
                return alive.get();
            }
        };
        ComponentName component = new ComponentName(app, ThumbnailDecoderService.class);
        shadowOf(app).setComponentNameAndServiceForBindService(component, service);
        IsolatedThumbnails thumbnails = new IsolatedThumbnails(new IsolatedThumbnails.ServiceConnector(app),
                5_000, 5_000, 1_000, 60_000);
        File file = write(16);
        Bitmap first = onWorkerWhileLooping(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024));
        assertNotNull(first);
        assertEquals(Color.RED, first.getPixel(0, 0));
        // The decoder process is gone, and the system hasn't said so yet. Binding again starts a new one.
        alive.set(false);
        shadowOf(app).setComponentNameAndServiceForBindService(component,
                reply(new int[]{1, 1, Color.BLUE}).asBinder());
        Bitmap second = onWorkerWhileLooping(() -> thumbnails.decode(file, ThumbnailDecoder.KIND_IMAGE, null, 1024));
        assertNotNull(second);
        assertEquals("answered by the new decoder", Color.BLUE, second.getPixel(0, 0));
    }

    @Test
    public void connectionThatArrivesAfterUnbindingIsIgnored() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        ComponentName component = new ComponentName(app, ThumbnailDecoderService.class);
        IThumbnailDecoder service = reply(new int[]{1, 1, Color.RED});
        shadowOf(app).setComponentNameAndServiceForBindService(component, service.asBinder());
        IsolatedThumbnails.ServiceConnector connector = new IsolatedThumbnails.ServiceConnector(app);
        // Queued for a binding that was already let go
        connector.onServiceConnected(component, reply(new int[]{1, 1, Color.BLUE}).asBinder());

        IThumbnailDecoder decoder = onWorkerWhileLooping(() -> connector.connect(5_000));

        assertSame(service, decoder);
        assertEquals(1, shadowOf(app).getBoundServiceConnections().size());
        connector.disconnect();
    }

    @Test
    public void decoderServiceRunsIsolatedAndUnexported() throws PackageManager.NameNotFoundException {
        Application app = RuntimeEnvironment.getApplication();
        ServiceInfo info = app.getPackageManager()
                .getServiceInfo(new ComponentName(app, ThumbnailDecoderService.class), 0);
        assertTrue("isolatedProcess", (info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0);
        assertFalse("exported", info.exported);
        assertNotNull(info.processName);
        assertTrue(info.processName, info.processName.endsWith(":thumbnails"));
    }

    // Helpers

    private IsolatedThumbnails thumbnails(long decodeTimeoutMillis, long idleUnbindMillis) {
        return new IsolatedThumbnails(mConnector, 5_000, decodeTimeoutMillis, 1_000, idleUnbindMillis);
    }

    private <T> T onWorker(Callable<T> callable) throws Exception {
        return mWorker.submit(callable).get(20, TimeUnit.SECONDS);
    }

    /**
     * Runs the call off the main thread while this thread, the main looper's, delivers the
     * service connection callbacks it waits for.
     */
    private <T> T onWorkerWhileLooping(Callable<T> callable) throws Exception {
        Future<T> future = mWorker.submit(callable);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!future.isDone() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private File write(int size) throws IOException {
        File file = mTemp.newFile();
        Files.write(file.toPath(), new byte[size]);
        return file;
    }

    private ParcelFileDescriptor open(int size) throws IOException {
        return ParcelFileDescriptor.open(write(size), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Decodes a path that has to be copied first, and checks the copy was there while the decoder
     * read it and is gone afterwards, with nothing else left in the cache.
     *
     * @return the size of what the decoder was handed
     */
    private long decodeLeavingNothingBehind(@NonNull Path path, int kind) throws Exception {
        AtomicLong seen = new AtomicLong(-1);
        List<String> copies = new ArrayList<>();
        File cacheDir = new File(FileUtils.getCachePath(), "files");
        List<String> before = listOf(cacheDir);
        mConnector.decoder = new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                seen.set(fd.getStatSize());
                copies.addAll(newSince(cacheDir, before));
                return new int[]{1, 1, Color.RED};
            }
        };
        IsolatedThumbnails thumbnails = thumbnails(5_000, 60_000);

        assertNotNull(onWorker(() -> thumbnails.decode(path, kind, null)));
        assertEquals("one copy while the decoder reads it", 1, copies.size());
        assertEquals("nothing left afterwards", Collections.emptyList(), newSince(cacheDir, before));
        return seen.get();
    }

    /**
     * Rewrites the uncompressed size the central directory gives every entry, leaving the data and
     * the local headers as they are.
     */
    private static void declareUncompressedSize(@NonNull File zip, int size) throws IOException {
        byte[] bytes = Files.readAllBytes(zip.toPath());
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int patched = 0;
        for (int i = 0; i + 46 <= bytes.length; ++i) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + 24, size);
                ++patched;
            }
        }
        assertTrue("a central directory header was found", patched > 0);
        Files.write(zip.toPath(), bytes);
    }

    private static void writeZip(@NonNull File zip, @NonNull String entryName, long size) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(entryName));
            byte[] zeros = new byte[64 * 1024];
            for (long left = size; left > 0; left -= zeros.length) {
                zos.write(zeros, 0, (int) Math.min(left, zeros.length));
            }
            zos.closeEntry();
        }
    }

    @NonNull
    private static List<String> listOf(@NonNull File dir) {
        String[] names = dir.list();
        return names != null ? Arrays.asList(names) : Collections.emptyList();
    }

    @NonNull
    private static List<String> newSince(@NonNull File dir, @NonNull List<String> before) {
        List<String> added = new ArrayList<>(listOf(dir));
        added.removeAll(before);
        return added;
    }

    private static IThumbnailDecoder reply(int[] pixels) {
        return new IThumbnailDecoder.Stub() {
            @Override
            public int[] decode(ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
                                long timeoutMillis) {
                return pixels.clone();
            }
        };
    }

    /** Serves {@link #sServedDocument} for any URI. */
    public static class DocumentProvider extends ContentProvider {
        static final AtomicInteger sOpens = new AtomicInteger();

        @Override
        public boolean onCreate() {
            return true;
        }

        @Nullable
        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
            sOpens.incrementAndGet();
            return ParcelFileDescriptor.open(sServedDocument, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            return null;
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return "application/pdf";
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                          @Nullable String[] selectionArgs) {
            return 0;
        }
    }

    /**
     * Serves {@link #sServedDocument} as a section of a file, the way a provider serves an asset,
     * and doesn't report a size. A section can't be opened as a whole-file descriptor, only read.
     */
    public static class SectionProvider extends DocumentProvider {
        @Nullable
        @Override
        public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
            return new AssetFileDescriptor(openFile(uri, mode), 0, sServedDocument.length());
        }
    }

    private static final class FakeConnector implements IsolatedThumbnails.Connector {
        volatile IThumbnailDecoder decoder;
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger disconnects = new AtomicInteger();
        final CountDownLatch disconnected = new CountDownLatch(1);

        @Override
        public IThumbnailDecoder connect(long timeoutMillis) {
            connects.incrementAndGet();
            return decoder;
        }

        @Override
        public void disconnect() {
            disconnects.incrementAndGet();
            disconnected.countDown();
        }
    }
}
