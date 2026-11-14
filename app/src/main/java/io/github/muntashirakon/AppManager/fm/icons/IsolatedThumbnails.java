// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.documentfile.provider.VirtualDocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

/**
 * The app's side of {@link ThumbnailDecoderService}. Opens the file read-only, hands the
 * descriptor to the isolated decoder with its budgets, and checks what comes back before
 * building a bitmap. Anything that goes wrong (a slow or crashed decoder, a bad reply) gives
 * {@code null}, so the file list shows the generic icon.
 */
public final class IsolatedThumbnails {
    private static final String TAG = IsolatedThumbnails.class.getSimpleName();

    // Budgets for each kind of file. A file larger than its budget isn't decoded at all.
    static final long MAX_IMAGE_BYTES = 128L * 1024 * 1024;
    static final long MAX_SVG_BYTES = 16L * 1024 * 1024;
    static final long MAX_PDF_BYTES = 512L * 1024 * 1024;
    static final long MAX_FONT_BYTES = 64L * 1024 * 1024;
    /**
     * Archives are read entry by entry, never whole, so only the zip format's own limit applies.
     */
    static final long MAX_ARCHIVE_BYTES = 0xFFFFFFFFL;
    /**
     * A file the decoder can't be handed directly is copied to the cache first, and only up to
     * this size, so a large PDF on a root-only path doesn't turn into hundreds of megabytes of writes.
     */
    static final long MAX_COPY_BYTES = 64L * 1024 * 1024;
    /**
     * Each reply is up to {@code MAX_SIDE * MAX_SIDE} ints (256 KB) in the app's binder buffer,
     * which is about 1 MB and shared with every other incoming call.
     */
    static final int MAX_CONCURRENT_CALLS = 2;

    static final long CONNECT_TIMEOUT_MILLIS = 5_000;
    static final long DECODE_TIMEOUT_MILLIS = 5_000;
    /**
     * How long past the decoder's own budget the app waits before giving up on the call.
     */
    static final long REPLY_GRACE_MILLIS = 2_000;
    /**
     * How long the decoder process stays up after its last thumbnail.
     */
    static final long IDLE_UNBIND_MILLIS = 30_000;

    interface Connector {
        /**
         * @return the decoder, or {@code null} if it isn't up within the timeout
         */
        @Nullable
        IThumbnailDecoder connect(long timeoutMillis) throws InterruptedException;

        /**
         * Lets the decoder process go. The next {@link #connect(long)} starts a new one.
         */
        void disconnect();
    }

    @Nullable
    private static IsolatedThumbnails sInstance;

    @NonNull
    public static synchronized IsolatedThumbnails getInstance() {
        if (sInstance == null) {
            sInstance = new IsolatedThumbnails(new ServiceConnector(ContextUtils.getContext()),
                    CONNECT_TIMEOUT_MILLIS, DECODE_TIMEOUT_MILLIS, REPLY_GRACE_MILLIS, IDLE_UNBIND_MILLIS);
        }
        return sInstance;
    }

    public static long getMaxBytes(int kind) {
        switch (kind) {
            case ThumbnailDecoder.KIND_IMAGE:
                return MAX_IMAGE_BYTES;
            case ThumbnailDecoder.KIND_SVG:
                return MAX_SVG_BYTES;
            case ThumbnailDecoder.KIND_PDF:
                return MAX_PDF_BYTES;
            case ThumbnailDecoder.KIND_FONT:
                return MAX_FONT_BYTES;
            default:
                return MAX_ARCHIVE_BYTES;
        }
    }

    @NonNull
    private final Connector mConnector;
    private final long mConnectTimeoutMillis;
    private final long mDecodeTimeoutMillis;
    private final long mReplyGraceMillis;
    private final long mIdleUnbindMillis;
    @NonNull
    private final ExecutorService mCalls = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ThumbnailCall");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull
    private final ScheduledThreadPoolExecutor mIdleTimer = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "ThumbnailIdle");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull
    private final Semaphore mCallPermits = new Semaphore(MAX_CONCURRENT_CALLS, true);
    @NonNull
    private final Semaphore mPdfPermit = new Semaphore(1, true);
    @GuardedBy("this")
    private int mActiveCalls;
    @GuardedBy("this")
    @Nullable
    private ScheduledFuture<?> mIdleUnbind;

    @VisibleForTesting
    IsolatedThumbnails(@NonNull Connector connector, long connectTimeoutMillis, long decodeTimeoutMillis,
                       long replyGraceMillis, long idleUnbindMillis) {
        mConnector = connector;
        mConnectTimeoutMillis = connectTimeoutMillis;
        mDecodeTimeoutMillis = decodeTimeoutMillis;
        mReplyGraceMillis = replyGraceMillis;
        mIdleUnbindMillis = idleUnbindMillis;
        // Every call cancels the last call's timer, so a cancelled one leaves the queue at once,
        // and the thread ends once no timer is pending instead of waiting forever for the next.
        mIdleTimer.setRemoveOnCancelPolicy(true);
        mIdleTimer.setKeepAliveTime(idleUnbindMillis, TimeUnit.MILLISECONDS);
        mIdleTimer.allowCoreThreadTimeOut(true);
    }

    @VisibleForTesting
    int getPendingIdleTimers() {
        return mIdleTimer.getQueue().size();
    }

    @VisibleForTesting
    int getIdleTimerThreads() {
        return mIdleTimer.getPoolSize();
    }

    /**
     * @param kind one of the {@code ThumbnailDecoder.KIND_*} values
     * @return the thumbnail, at most {@link ThumbnailDecoder#MAX_SIDE} pixels a side, or
     * {@code null} on the main thread or if the file can't be decoded within its budgets
     */
    @WorkerThread
    @Nullable
    public Bitmap decode(@NonNull Path path, int kind, @Nullable String label) {
        if (isMainThread()) {
            return null;
        }
        Uri uri = path.getUri();
        Uri unwrapped = FmProvider.unwrap(uri);
        if (!unwrapped.equals(uri)) {
            // One of this app's own provider URIs. Read through the provider, it would be classed
            // as a document while it's really a file or an archive entry.
            try {
                path = Paths.getStrict(unwrapped);
            } catch (FileNotFoundException e) {
                return null;
            }
            uri = unwrapped;
        }
        long maxBytes = getMaxBytes(kind);
        long length = path.length();
        if (length > maxBytes) {
            return null;
        }
        boolean archiveEntry = VirtualFileSystem.SCHEME.equals(uri.getScheme());
        File file = path.getFile();
        if (file != null) {
            // The path's own file may read through root; the decoder gets only what this process can open
            File plain = new File(file.getPath());
            if (plain.canRead()) {
                return decode(plain, kind, label, maxBytes);
            }
            // Root-only. Its descriptor would be a proxy that the isolated process may not be allowed
            // to read, so it's copied below.
        } else if (!archiveEntry) {
            // A document. Its own descriptor saves a copy when it can seek.
            ParcelFileDescriptor fd = openSeekable(path);
            if (fd != null) {
                try {
                    return decode(fd, kind, label, maxBytes);
                } finally {
                    closeQuietly(fd);
                }
            }
        }
        // An archive entry is copied too: its own descriptor would extract all of it into the
        // archive's cache, where it stays until the archive is closed.
        if (length > MAX_COPY_BYTES) {
            return null;
        }
        // The length is only a hint. A provider that doesn't know it reports 0, and an archive
        // declares whatever it likes, so the copy keeps its own count.
        // A cache of its own: the global one isn't safe to share between threads.
        try (FileCache cache = new FileCache()) {
            File copy = archiveEntry ? copyEntryAtMost(uri, path.getExtension(), cache, MAX_COPY_BYTES)
                    : copyAtMost(path, cache, MAX_COPY_BYTES);
            return copy != null ? decode(copy, kind, label, maxBytes) : null;
        }
    }

    /**
     * @return a copy of the path in {@code cache}, or {@code null} if it can't be read or holds
     * more than {@code maxBytes}
     */
    @Nullable
    private static File copyAtMost(@NonNull Path path, @NonNull FileCache cache, long maxBytes) {
        try {
            File copy = cache.createCachedFile(path.getExtension());
            try (InputStream in = path.openInputStream()) {
                if (copyAtMost(in, copy, maxBytes)) {
                    return copy;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not copy %s for a thumbnail", e, path);
        }
        return null;
    }

    /**
     * Streams an entry of an archive open in the file manager, never extracting it into the
     * archive's own cache.
     *
     * @return a copy in {@code cache}, or {@code null} if it can't be read or holds more than
     * {@code maxBytes}, whatever size the archive declares for it
     */
    @Nullable
    private static File copyEntryAtMost(@NonNull Uri uri, @Nullable String extension, @NonNull FileCache cache,
                                        long maxBytes) {
        Pair<Integer, String> location = uri.getAuthority() != null ? VirtualDocumentFile.parseUri(uri) : null;
        VirtualFileSystem fs = location != null ? VirtualFileSystem.getFileSystem(location.first) : null;
        if (fs == null || location.second == null) {
            return null;
        }
        try {
            File copy = cache.createCachedFile(extension);
            try (OutputStream out = new FileOutputStream(copy)) {
                if (fs.copyAtMost(location.second, out, maxBytes)) {
                    return copy;
                }
            }
        } catch (IOException | VirtualFileSystem.NotMountedException e) {
            // Closed while it was read, say
            Log.w(TAG, "Could not copy %s for a thumbnail", e, uri);
        }
        return null;
    }

    /**
     * @return {@code true} if all of {@code in} fit in {@code maxBytes} and went to {@code out};
     * {@code false} once it's past, with {@code out} left partial for the caller to delete
     */
    @VisibleForTesting
    static boolean copyAtMost(@NonNull InputStream in, @NonNull File out, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream os = new FileOutputStream(out)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return false;
                }
                os.write(buffer, 0, read);
            }
        }
        return true;
    }

    /**
     * @return the path's own read-only descriptor if the decoder can seek in it, {@code null} for
     * a pipe or anything that can't be opened
     */
    @Nullable
    private static ParcelFileDescriptor openSeekable(@NonNull Path path) {
        ParcelFileDescriptor fd;
        try {
            // The handler only serves proxy descriptors, which come from root-only files, not documents
            fd = path.openFileDescriptor("r", ThreadUtils.getUiThreadHandler());
        } catch (FileNotFoundException | SecurityException e) {
            Log.w(TAG, "Could not open %s for a thumbnail", e, path);
            return null;
        }
        if (fd.getStatSize() >= 0) {
            return fd;
        }
        closeQuietly(fd);
        return null;
    }

    private static void closeQuietly(@NonNull ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (IOException ignore) {
            // Read-only, nothing to lose
        }
    }

    @WorkerThread
    @Nullable
    Bitmap decode(@NonNull File file, int kind, @Nullable String label, long maxBytes) {
        if (isMainThread() || file.length() > maxBytes) {
            return null;
        }
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return decode(fd, kind, label, maxBytes);
        } catch (IOException e) {
            Log.w(TAG, "Could not open %s for a thumbnail", e, file);
            return null;
        }
    }

    @WorkerThread
    @Nullable
    @VisibleForTesting
    Bitmap decode(@NonNull ParcelFileDescriptor fd, int kind, @Nullable String label, long maxBytes) {
        if (isMainThread()) {
            return null;
        }
        // Counted as active while it waits for a permit, so the decoder isn't let go in between
        callStarted();
        // The decoder renders one PDF at a time. A second PDF waits here, before its timer starts,
        // instead of in the decoder, where the wait would count against its budget.
        Semaphore pdfPermit = kind == ThumbnailDecoder.KIND_PDF ? mPdfPermit : null;
        try {
            if (pdfPermit != null) {
                pdfPermit.acquire();
            }
            try {
                mCallPermits.acquire();
                try {
                    return callDecoder(fd, kind, label, maxBytes);
                } finally {
                    mCallPermits.release();
                }
            } finally {
                if (pdfPermit != null) {
                    pdfPermit.release();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            callFinished();
        }
    }

    @Nullable
    private Bitmap callDecoder(@NonNull ParcelFileDescriptor fd, int kind, @Nullable String label, long maxBytes)
            throws InterruptedException {
        IThumbnailDecoder decoder = mConnector.connect(mConnectTimeoutMillis);
        if (decoder == null) {
            Log.w(TAG, "The thumbnail decoder didn't start");
            return null;
        }
        int maxSide = ThumbnailDecoder.MAX_SIDE;
        Future<int[]> call = mCalls.submit(() -> decoder.decode(fd, kind, label, maxSide, maxBytes,
                mDecodeTimeoutMillis));
        int[] pixels;
        try {
            pixels = call.get(mDecodeTimeoutMillis + mReplyGraceMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // The decoder kills itself past its budget; this is for when even that doesn't happen
            call.cancel(true);
            Log.w(TAG, "The thumbnail decoder didn't answer in time");
            mConnector.disconnect();
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // A full binder buffer also comes back as DeadObjectException, so the exception alone
            // doesn't say the decoder is gone. Asking it does.
            IBinder binder = decoder.asBinder();
            if (!binder.isBinderAlive() || !binder.pingBinder()) {
                // The next call starts a new decoder
                Log.w(TAG, "The thumbnail decoder died", cause);
                mConnector.disconnect();
            } else {
                // A reply too large for the binder buffer, say. The decoder is fine for the next file.
                Log.w(TAG, "The thumbnail decoder failed", cause);
            }
            return null;
        }
        return toBitmap(pixels, maxSide);
    }

    /**
     * Builds the bitmap the decoder described, or returns {@code null} if the reply doesn't
     * describe one within {@code maxSide}.
     */
    @VisibleForTesting
    @Nullable
    static Bitmap toBitmap(@Nullable int[] pixels, int maxSide) {
        if (pixels == null || pixels.length < 2) {
            return null;
        }
        int width = pixels[0];
        int height = pixels[1];
        if (width < 1 || height < 1 || width > maxSide || height > maxSide) {
            return null;
        }
        if (pixels.length != 2 + (long) width * height) {
            return null;
        }
        return Bitmap.createBitmap(pixels, 2, width, width, height, Bitmap.Config.ARGB_8888);
    }

    private synchronized void callStarted() {
        ++mActiveCalls;
        if (mIdleUnbind != null) {
            mIdleUnbind.cancel(false);
            mIdleUnbind = null;
        }
    }

    private synchronized void callFinished() {
        if (--mActiveCalls == 0) {
            mIdleUnbind = mIdleTimer.schedule(this::unbindIfIdle, mIdleUnbindMillis, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void unbindIfIdle() {
        if (mActiveCalls == 0) {
            mIdleUnbind = null;
            mConnector.disconnect();
        }
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * Binds {@link ThumbnailDecoderService}. Called from worker threads; the system answers on
     * the main thread.
     */
    @VisibleForTesting
    static final class ServiceConnector implements Connector, ServiceConnection {
        @NonNull
        private final Context mContext;
        @GuardedBy("this")
        private boolean mBound;
        @GuardedBy("this")
        @Nullable
        private IThumbnailDecoder mDecoder;

        ServiceConnector(@NonNull Context context) {
            mContext = context.getApplicationContext();
        }

        @Nullable
        @Override
        public synchronized IThumbnailDecoder connect(long timeoutMillis) throws InterruptedException {
            if (mDecoder != null && !mDecoder.asBinder().isBinderAlive()) {
                // Died before the system told us
                unbindLocked();
            }
            if (mDecoder == null && !mBound) {
                Intent intent = new Intent(mContext, ThumbnailDecoderService.class);
                mBound = mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
                if (!mBound) {
                    // Unbind even when binding fails, or the connection leaks
                    unbindQuietly();
                    return null;
                }
            }
            // Real time: callbacks can come in any order, and each one wakes every waiting call
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (mDecoder == null && mBound) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) {
                    return null;
                }
                wait(remaining);
            }
            return mDecoder;
        }

        @Override
        public synchronized void disconnect() {
            unbindLocked();
        }

        @Override
        public synchronized void onServiceConnected(ComponentName name, IBinder service) {
            if (!mBound) {
                // Arrived after we let this binding go
                return;
            }
            mDecoder = IThumbnailDecoder.Stub.asInterface(service);
            notifyAll();
        }

        @Override
        public synchronized void onServiceDisconnected(ComponentName name) {
            // Still bound, so the system starts the decoder again and connects once more
            mDecoder = null;
        }

        @Override
        public synchronized void onBindingDied(ComponentName name) {
            unbindLocked();
        }

        @Override
        public synchronized void onNullBinding(ComponentName name) {
            unbindLocked();
        }

        @GuardedBy("this")
        private void unbindLocked() {
            mDecoder = null;
            if (mBound) {
                mBound = false;
                unbindQuietly();
            }
            // Anyone still waiting gives up rather than waiting out the timeout
            notifyAll();
        }

        private void unbindQuietly() {
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException ignore) {
                // Not bound
            }
        }
    }
}
