// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The isolated process's side of the thumbnail call: budgets are checked before any decoding, a
 * decoder that overruns its time budget gets the process killed, and any failure is a null reply.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36})
public class ThumbnailDecoderBinderTest {
    private static final long BUDGET = 1024;
    private static final long TIMEOUT = 5_000;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private final AtomicInteger mDecodes = new AtomicInteger();
    private final AtomicInteger mKills = new AtomicInteger();
    private ThumbnailDecoderBinder mBinder;

    @After
    public void tearDown() {
        if (mBinder != null) {
            mBinder.shutdown();
        }
    }

    @Test
    public void nullDescriptorGetsNoReply() {
        mBinder = binder((fd, kind, label, maxSide) -> bitmap(10, 10));
        assertNull(mBinder.decode(null, ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        assertEquals(0, mDecodes.get());
    }

    @Test
    public void budgetsOutOfRangeGetNoReplyAndCloseTheDescriptor() throws IOException {
        mBinder = binder((fd, kind, label, maxSide) -> bitmap(10, 10));
        long[][] budgets = {
                // maxSide, maxBytes, timeout
                {0, BUDGET, TIMEOUT},
                {ThumbnailDecoder.MAX_SIDE + 1, BUDGET, TIMEOUT},
                {64, 0, TIMEOUT},
                {64, BUDGET, 0},
                {64, BUDGET, ThumbnailDecoderBinder.MAX_TIMEOUT_MILLIS + 1},
        };
        for (long[] budget : budgets) {
            ParcelFileDescriptor fd = open(16);
            FileDescriptor descriptor = fd.getFileDescriptor();
            assertNull(mBinder.decode(fd, ThumbnailDecoder.KIND_IMAGE, null, (int) budget[0], budget[1], budget[2]));
            assertFalse("descriptor closed", descriptor.valid());
        }
        assertEquals(0, mDecodes.get());
        assertEquals(0, mKills.get());
    }

    @Test
    public void fileOverTheByteBudgetIsNeverDecoded() throws IOException {
        mBinder = binder((fd, kind, label, maxSide) -> bitmap(10, 10));
        assertNull(mBinder.decode(open(BUDGET + 1), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        assertEquals(0, mDecodes.get());
    }

    @Test
    public void decodedBitmapComesBackAsPixelsWithinMaxSide() throws IOException {
        mBinder = binder((fd, kind, label, maxSide) -> bitmap(512, 256));
        ParcelFileDescriptor fd = open(BUDGET);
        FileDescriptor descriptor = fd.getFileDescriptor();
        int[] pixels = mBinder.decode(fd, ThumbnailDecoder.KIND_IMAGE, null, 128, BUDGET, TIMEOUT);
        assertNotNull(pixels);
        assertEquals(128, pixels[0]);
        assertEquals(64, pixels[1]);
        assertEquals(2 + 128 * 64, pixels.length);
        assertFalse("descriptor closed", descriptor.valid());
    }

    @Test
    public void nothingToShowIsNull() throws IOException {
        mBinder = binder((fd, kind, label, maxSide) -> null);
        assertNull(mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        assertEquals(1, mDecodes.get());
    }

    @Test
    public void decoderFailuresAreNullReplies() throws IOException {
        mBinder = binder((fd, kind, label, maxSide) -> {
            throw new OutOfMemoryError("bitmap too large");
        });
        assertNull(mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        mBinder.shutdown();

        mBinder = binder((fd, kind, label, maxSide) -> {
            throw new IOException("damaged");
        });
        assertNull(mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        mBinder.shutdown();

        mBinder = binder((fd, kind, label, maxSide) -> {
            throw new StackOverflowError();
        });
        assertNull(mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, TIMEOUT));
        assertEquals(0, mKills.get());
    }

    @Test
    public void realDecoderTurnsMalformedFilesIntoNullReplies() throws IOException {
        // The decoder this process really runs, on files that aren't what their name says. Images
        // and PDFs need real graphics, so ThumbnailDecoderTest covers those.
        mBinder = new ThumbnailDecoderBinder(mKills::incrementAndGet);
        int[] kinds = {ThumbnailDecoder.KIND_SVG, ThumbnailDecoder.KIND_EPUB, ThumbnailDecoder.KIND_J2ME,
                ThumbnailDecoder.KIND_OPEN_DOCUMENT, ThumbnailDecoder.KIND_APK_BUNDLE, 0, 99};
        for (int kind : kinds) {
            assertNull("kind " + kind, mBinder.decode(open(512), kind, null, 64, BUDGET, TIMEOUT));
        }
        assertEquals(0, mKills.get());
    }

    @Test
    public void hungDecoderGetsTheProcessKilled() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch killed = new CountDownLatch(1);
        mBinder = new ThumbnailDecoderBinder((fd, kind, label, maxSide) -> {
            mDecodes.incrementAndGet();
            // Stands in for native code that never returns
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }, killed::countDown);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<int[]> reply = caller.submit(() ->
                    mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, 100));
            assertTrue("watchdog fired", killed.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertNull(reply.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    public void decoderWithinItsBudgetIsNeverKilled() throws Exception {
        mBinder = binder((fd, kind, label, maxSide) -> bitmap(8, 8));
        assertNotNull(mBinder.decode(open(16), ThumbnailDecoder.KIND_IMAGE, null, 64, BUDGET, 100));
        // Past the budget the call had: the watchdog was cancelled when it returned
        Thread.sleep(400);
        assertEquals(0, mKills.get());
    }

    @Test
    @Config(sdk = 23)
    public void fontsBeforeOreoHaveNoThumbnail() throws IOException {
        // A font loads only from a path there, and the isolated process can't open paths
        try (ParcelFileDescriptor fd = open(256)) {
            assertNull(ThumbnailDecoder.decode(fd, ThumbnailDecoder.KIND_FONT, "TTF", 64));
        }
    }

    private ThumbnailDecoderBinder binder(ThumbnailDecoderBinder.Decoder decoder) {
        return new ThumbnailDecoderBinder((fd, kind, label, maxSide) -> {
            mDecodes.incrementAndGet();
            return decoder.decode(fd, kind, label, maxSide);
        }, mKills::incrementAndGet);
    }

    private ParcelFileDescriptor open(long size) throws IOException {
        File file = mTemp.newFile();
        byte[] bytes = new byte[(int) size];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) (i * 7);
        }
        Files.write(file.toPath(), bytes);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static Bitmap bitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
}
