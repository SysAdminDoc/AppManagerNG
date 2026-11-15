// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes through a file only the root service can reach go over a FIFO, one service call per chunk, and the
 * service takes at most {@link FileSystemService#PIPE_CAPACITY} bytes per call. A FIFO holds 16 pages, so on a
 * kernel with 16KB pages it takes far more than that, and a big buffer used to go in whole and be refused.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, shadows = RemoteFileChannelTest.ShadowSixteenKbPageFifo.class)
public class RemoteFileChannelTest {
    private static final int HANDLE = 7;

    private final List<Integer> mWriteLengths = new ArrayList<>();
    private final List<Long> mWriteOffsets = new ArrayList<>();
    private byte[] mFile = new byte[0];
    private int mFilePosition;

    @Before
    public void setUp() {
        ShadowSixteenKbPageFifo.reset();
    }

    @After
    public void tearDown() {
        ShadowSixteenKbPageFifo.reset();
    }

    @Test
    public void aBufferBiggerThanOneServiceCallGoesOverInPieces() throws IOException {
        byte[] data = pattern(200_000);
        ByteBuffer src = ByteBuffer.wrap(data);
        try (RemoteFileChannel channel = openForWriting()) {
            assertEquals(data.length, channel.write(src));
        }
        assertArrayEquals(data, mFile);
        assertEquals(4, mWriteLengths.size());
        for (int len : mWriteLengths) {
            assertTrue("A call carried " + len + " bytes", len <= FileSystemService.PIPE_CAPACITY);
        }
        // The caller's buffer ends up fully read with its own limit
        assertEquals(data.length, src.limit());
        assertEquals(data.length, src.position());
    }

    @Test
    public void aPositionedWriteMovesItsOffsetOnWithEachPiece() throws IOException {
        byte[] data = pattern(150_000);
        try (RemoteFileChannel channel = openForWriting()) {
            assertEquals(data.length, channel.write(ByteBuffer.wrap(data), 1000));
        }
        List<Long> expected = new ArrayList<>();
        expected.add(1000L);
        expected.add(1000L + FileSystemService.PIPE_CAPACITY);
        expected.add(1000L + 2L * FileSystemService.PIPE_CAPACITY);
        assertEquals(expected, mWriteOffsets);
        byte[] written = new byte[data.length];
        System.arraycopy(mFile, 1000, written, 0, data.length);
        assertArrayEquals(data, written);
    }

    private RemoteFileChannel openForWriting() throws IOException {
        IFileSystemService fs = (IFileSystemService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IFileSystemService.class, InThisPackage.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "openChannel":
                            return new IOResult(HANDLE);
                        case "pwrite":
                            return pwrite((int) args[1], (long) args[2]);
                        case "close":
                        case "asBinder":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        return new RemoteFileChannel(fs, new File("/data/app-only/target"), ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    /** What the service does with one call: refuse what it can't take, or move that many bytes into the file. */
    private IOResult pwrite(int len, long offset) {
        try {
            FileSystemService.checkLenOffset(len, offset, FileSystemService.PIPE_CAPACITY);
        } catch (IOException e) {
            return new IOResult(e);
        }
        mWriteLengths.add(len);
        mWriteOffsets.add(offset);
        byte[] chunk = ShadowSixteenKbPageFifo.drain(len);
        int at = offset >= 0 ? (int) offset : mFilePosition;
        if (at + len > mFile.length) {
            byte[] grown = new byte[at + len];
            System.arraycopy(mFile, 0, grown, 0, mFile.length);
            mFile = grown;
        }
        System.arraycopy(chunk, 0, mFile, at, len);
        if (offset < 0) {
            mFilePosition = at + len;
        }
        return new IOResult();
    }

    /**
     * A proxy with a non-public interface is defined in that interface's package, so it can return
     * {@link IOResult}, which is package-private. Otherwise it lands in a JDK proxy module that can't.
     */
    interface InThisPackage {
    }

    private static byte[] pattern(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; ++i) {
            data[i] = (byte) (i * 31 + i / 251);
        }
        return data;
    }

    /** A FIFO on a kernel with 16KB pages: 16 pages, so 256KB fit before a non-blocking write comes up short. */
    @Implements(Os.class)
    public static class ShadowSixteenKbPageFifo {
        private static final int CAPACITY = 16 * 16384;
        private static final ByteArrayOutputStream sPipe = new ByteArrayOutputStream();

        static void reset() {
            sPipe.reset();
        }

        static byte[] drain(int len) {
            byte[] held = sPipe.toByteArray();
            if (held.length < len) {
                throw new AssertionError("The service asked for " + len + " bytes and the FIFO holds " + held.length);
            }
            byte[] chunk = new byte[len];
            System.arraycopy(held, 0, chunk, 0, len);
            sPipe.reset();
            sPipe.write(held, len, held.length - len);
            return chunk;
        }

        @Implementation
        protected static void mkfifo(String path, int mode) {
        }

        @Implementation
        protected static FileDescriptor open(String path, int flags, int mode) {
            return new FileDescriptor();
        }

        @Implementation
        protected static int write(FileDescriptor fd, ByteBuffer buffer) throws ErrnoException {
            int len = Math.min(buffer.remaining(), CAPACITY - sPipe.size());
            if (len == 0) {
                throw new ErrnoException("write", OsConstants.EAGAIN);
            }
            byte[] bytes = new byte[len];
            buffer.get(bytes);
            sPipe.write(bytes, 0, len);
            return len;
        }

        @Implementation
        protected static void close(FileDescriptor fd) {
        }
    }
}
