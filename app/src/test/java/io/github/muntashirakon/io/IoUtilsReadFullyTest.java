// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * An unbounded read checked its ceiling only before growing, so a stream just under the 256 MiB
 * limit could make it allocate about 512 MiB before it failed. These tests use a small ceiling and
 * record the largest buffer the reader asked the stream to fill.
 */
public class IoUtilsReadFullyTest {
    private static final int LIMIT = 10_000;

    @Test
    public void aStreamAtTheCeilingIsReadWhole() throws IOException {
        TrackingStream in = new TrackingStream(LIMIT, Integer.MAX_VALUE);

        byte[] bytes = IoUtils.readFully(in, -1, true, LIMIT);

        assertEquals(LIMIT, bytes.length);
        assertTrue(in.largestBuffer <= LIMIT + 1);
    }

    @Test
    public void aStreamOneByteOverTheCeilingFailsWithoutGrowingPastIt() {
        TrackingStream in = new TrackingStream(LIMIT + 1, Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> IoUtils.readFully(in, -1, true, LIMIT));
        assertTrue("buffer grew to " + in.largestBuffer, in.largestBuffer <= LIMIT + 1);
    }

    @Test
    public void aMuchLongerStreamStillFailsAtTheCeiling() {
        TrackingStream in = new TrackingStream(LIMIT * 50, Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> IoUtils.readFully(in, -1, true, LIMIT));
        assertTrue("buffer grew to " + in.largestBuffer, in.largestBuffer <= LIMIT + 1);
    }

    @Test
    public void shortReadsReassembleTheWholeStream() throws IOException {
        TrackingStream in = new TrackingStream(3_000, 7);

        byte[] bytes = IoUtils.readFully(in, -1, true, LIMIT);

        assertEquals(3_000, bytes.length);
        for (int i = 0; i < bytes.length; ++i) {
            assertEquals((byte) i, bytes[i]);
        }
    }

    @Test
    public void aBoundedReadStopsAtItsLengthAndReportsAShortStream() throws IOException {
        byte[] source = new byte[500];
        for (int i = 0; i < source.length; ++i) {
            source[i] = (byte) i;
        }

        byte[] first100 = IoUtils.readFully(new ByteArrayInputStream(source), 100, true);
        assertEquals(100, first100.length);
        assertArrayEquals(java.util.Arrays.copyOf(source, 100), first100);
        assertThrows(EOFException.class, () -> IoUtils.readFully(new ByteArrayInputStream(source), 600, true));
        assertEquals(500, IoUtils.readFully(new ByteArrayInputStream(source), 600, false).length);
    }

    /** Serves {@code length} bytes, at most {@code maxChunk} per read, and records buffer sizes. */
    private static final class TrackingStream extends InputStream {
        private final int mLength;
        private final int mMaxChunk;
        private int mPosition;
        int largestBuffer;

        TrackingStream(int length, int maxChunk) {
            mLength = length;
            mMaxChunk = maxChunk;
        }

        @Override
        public int read() {
            return mPosition < mLength ? (byte) mPosition++ & 0xFF : -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            largestBuffer = Math.max(largestBuffer, buffer.length);
            if (mPosition >= mLength) {
                return -1;
            }
            int count = Math.min(Math.min(length, mMaxChunk), mLength - mPosition);
            for (int i = 0; i < count; ++i) {
                buffer[offset + i] = (byte) mPosition++;
            }
            return count;
        }
    }
}
