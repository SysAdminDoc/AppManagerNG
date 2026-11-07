// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * One-way in-memory byte pipe with socket-like close behaviour: a closed writer drains to EOF and
 * a closed reader makes further writes fail. Unlike PipedInputStream it does not care which
 * threads come and go, and a read gives up after a few seconds so a broken test cannot hang.
 */
final class TestPipe {
    private static final long READ_TIMEOUT_MS = 10_000;

    private final Object mLock = new Object();
    private byte[] mData = new byte[16 * 1024];
    private int mHead;
    private int mTail;
    private boolean mWriterClosed;
    private boolean mReaderClosed;

    final InputStream in = new InputStream() {
        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            synchronized (mLock) {
                long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS;
                while (mHead == mTail && !mWriterClosed && !mReaderClosed) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) {
                        throw new InterruptedIOException("Test pipe read timed out");
                    }
                    try {
                        mLock.wait(left);
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException("Interrupted");
                    }
                }
                if (mReaderClosed) {
                    throw new IOException("Pipe closed");
                }
                if (mHead == mTail) {
                    return -1;
                }
                int n = Math.min(len, mTail - mHead);
                System.arraycopy(mData, mHead, b, off, n);
                mHead += n;
                return n;
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                mReaderClosed = true;
                mLock.notifyAll();
            }
        }
    };

    final OutputStream out = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            synchronized (mLock) {
                if (mReaderClosed || mWriterClosed) {
                    throw new IOException("Pipe closed");
                }
                if (mTail + len > mData.length) {
                    int used = mTail - mHead;
                    byte[] bigger = new byte[Math.max(mData.length, (used + len) * 2)];
                    System.arraycopy(mData, mHead, bigger, 0, used);
                    mData = bigger;
                    mHead = 0;
                    mTail = used;
                }
                System.arraycopy(b, off, mData, mTail, len);
                mTail += len;
                mLock.notifyAll();
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                mWriterClosed = true;
                mLock.notifyAll();
            }
        }
    };

    /**
     * Two pipes wired as a connected pair of endpoints.
     */
    static final class Duplex {
        final TestPipe aToB = new TestPipe();
        final TestPipe bToA = new TestPipe();

        @NonNull
        FrameIo endpointA() {
            return new FrameIo(bToA.in, aToB.out);
        }

        @NonNull
        FrameIo endpointB() {
            return new FrameIo(aToB.in, bToA.out);
        }
    }
}
