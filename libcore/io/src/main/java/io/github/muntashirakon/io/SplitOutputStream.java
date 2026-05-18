// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SplitOutputStream extends OutputStream {
    private static final long MAX_BYTES_WRITTEN = 1024 * 1024 * 1024;  // 1GB
    private static final int MAX_PROVIDER_WRITE_CHUNK = 256 * 1024;  // 256KB

    private final List<OutputTarget> mOutputStreams = new ArrayList<>(1);
    private final List<Path> mFiles = new ArrayList<>(1);
    private int mCurrentIndex = -1;
    private long mBytesWritten;
    private final long mMaxBytesPerFile;
    private final String mBaseName;
    private final Path mBasePath;
    private final boolean mSyncAfterEachChunk;

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName) {
        this(basePath, baseName, MAX_BYTES_WRITTEN);
    }

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName, long maxBytesPerFile) {
        this(basePath, baseName, maxBytesPerFile, false);
    }

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName, long maxBytesPerFile,
                             boolean syncAfterEachChunk) {
        mBasePath = basePath;
        mBaseName = baseName;
        mMaxBytesPerFile = Math.max(1, maxBytesPerFile);
        mBytesWritten = mMaxBytesPerFile;
        mSyncAfterEachChunk = syncAfterEachChunk;
    }

    public List<Path> getFiles() {
        return mFiles;
    }

    @WorkerThread
    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @WorkerThread
    @Override
    public void write(@NonNull byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @WorkerThread
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException("b == null");
        }
        if ((off | len | (b.length - (off + len)) | (off + len)) < 0) {
            throw new IndexOutOfBoundsException("Invalid offset/length");
        }
        int offset = off;
        int remaining = len;
        while (remaining > 0) {
            checkCurrentStream();
            long bytesRemainingInCurrentFile = mMaxBytesPerFile - mBytesWritten;
            int bytesToWrite = (int) Math.min(remaining,
                    Math.min(MAX_PROVIDER_WRITE_CHUNK, bytesRemainingInCurrentFile));
            OutputTarget target = mOutputStreams.get(mCurrentIndex);
            target.write(b, offset, bytesToWrite);
            mBytesWritten += bytesToWrite;
            offset += bytesToWrite;
            remaining -= bytesToWrite;
            if (mSyncAfterEachChunk) {
                target.sync();
            }
        }
    }

    @WorkerThread
    @Override
    public void flush() throws IOException {
        for (OutputTarget target : mOutputStreams) {
            target.flush();
        }
    }

    @WorkerThread
    @Override
    public void close() throws IOException {
        IOException pendingException = null;
        for (OutputTarget target : mOutputStreams) {
            try {
                target.close();
            } catch (IOException e) {
                if (pendingException == null) {
                    pendingException = e;
                } else {
                    pendingException.addSuppressed(e);
                }
            }
        }
        if (pendingException != null) {
            throw pendingException;
        }
    }

    @WorkerThread
    private void checkCurrentStream() throws IOException {
        if (mBytesWritten >= mMaxBytesPerFile) {
            // Need to create a new stream
            Path newFile = getNextFile();
            mFiles.add(newFile);
            mOutputStreams.add(OutputTarget.open(newFile, mSyncAfterEachChunk));
            ++mCurrentIndex;
            mBytesWritten = 0;
        }
    }

    @NonNull
    private Path getNextFile() throws IOException {
        return mBasePath.createNewFile(mBaseName + "." + (mCurrentIndex + 1), null);
    }

    private static class OutputTarget implements Closeable {
        @NonNull
        private final Path mFile;
        @NonNull
        private final OutputStream mStream;
        private final ParcelFileDescriptor mParcelFileDescriptor;
        private final boolean mVerifySizeOnClose;
        private long mBytesWritten;
        private boolean mClosed;

        private OutputTarget(@NonNull Path file, @NonNull OutputStream stream,
                             ParcelFileDescriptor parcelFileDescriptor, boolean verifySizeOnClose) {
            mFile = file;
            mStream = stream;
            mParcelFileDescriptor = parcelFileDescriptor;
            mVerifySizeOnClose = verifySizeOnClose;
        }

        @NonNull
        private static OutputTarget open(@NonNull Path file, boolean syncAfterEachChunk) throws IOException {
            if (syncAfterEachChunk) {
                try {
                    ParcelFileDescriptor pfd = file.openFileDescriptor("w", getCallbackHandler());
                    return new OutputTarget(file, new ParcelFileDescriptor.AutoCloseOutputStream(pfd), pfd, true);
                } catch (Exception ignore) {
                    // Fall back to the provider output stream when it does not support file descriptors.
                }
            }
            return new OutputTarget(file, file.openOutputStream(), null, syncAfterEachChunk);
        }

        private static Handler getCallbackHandler() throws IOException {
            Looper looper = Looper.getMainLooper();
            if (looper == null) {
                looper = Looper.myLooper();
            }
            if (looper == null) {
                throw new IOException("Cannot open a proxy file descriptor without a Looper");
            }
            return new Handler(looper);
        }

        void write(@NonNull byte[] b, int off, int len) throws IOException {
            mStream.write(b, off, len);
            mBytesWritten += len;
        }

        void flush() throws IOException {
            mStream.flush();
        }

        void sync() throws IOException {
            mStream.flush();
            if (mParcelFileDescriptor != null) {
                mParcelFileDescriptor.getFileDescriptor().sync();
            }
        }

        @Override
        public void close() throws IOException {
            if (mClosed) {
                return;
            }
            IOException pendingException = null;
            try {
                sync();
            } catch (IOException e) {
                pendingException = e;
            }
            try {
                mStream.close();
            } catch (IOException e) {
                if (pendingException == null) {
                    pendingException = e;
                } else {
                    pendingException.addSuppressed(e);
                }
            }
            mClosed = true;
            if (pendingException == null && mVerifySizeOnClose) {
                long actualSize = mFile.length();
                if (actualSize != mBytesWritten) {
                    pendingException = new IOException("Short write detected for " + mFile + ": expected "
                            + mBytesWritten + " bytes, provider reports " + actualSize + " bytes.");
                }
            }
            if (pendingException != null) {
                throw pendingException;
            }
        }
    }
}
