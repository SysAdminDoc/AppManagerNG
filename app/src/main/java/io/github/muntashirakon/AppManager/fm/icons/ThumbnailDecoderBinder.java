// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The isolated decoder's side of {@link IThumbnailDecoder}. Every call checks its budgets before
 * decoding, and a decoder that runs past its time budget takes the process down with it, since
 * nothing else can stop a decoder stuck in native code. The app sees the process die and shows
 * the generic icon.
 */
final class ThumbnailDecoderBinder extends IThumbnailDecoder.Stub {
    // Uses android.util.Log: the app's own logger writes files this process can't open
    private static final String TAG = "ThumbnailDecoder";

    static final long MAX_TIMEOUT_MILLIS = 30_000;

    interface Decoder {
        @Nullable
        Bitmap decode(@NonNull ParcelFileDescriptor fd, int kind, @Nullable String label, int maxSide)
                throws IOException;
    }

    interface ProcessKiller {
        void kill();
    }

    @NonNull
    private final Decoder mDecoder;
    @NonNull
    private final ProcessKiller mKiller;
    @NonNull
    private final ScheduledExecutorService mWatchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ThumbnailWatchdog");
        thread.setDaemon(true);
        return thread;
    });

    ThumbnailDecoderBinder(@NonNull ProcessKiller killer) {
        this(ThumbnailDecoder::decode, killer);
    }

    @VisibleForTesting
    ThumbnailDecoderBinder(@NonNull Decoder decoder, @NonNull ProcessKiller killer) {
        mDecoder = decoder;
        mKiller = killer;
    }

    @Nullable
    @Override
    public int[] decode(@Nullable ParcelFileDescriptor fd, int kind, @Nullable String label, int maxSide,
                        long maxBytes, long timeoutMillis) {
        if (fd == null) {
            return null;
        }
        ScheduledFuture<?> watchdog = null;
        try {
            if (maxSide < 1 || maxSide > ThumbnailDecoder.MAX_SIDE || maxBytes < 1 || timeoutMillis < 1
                    || timeoutMillis > MAX_TIMEOUT_MILLIS) {
                return null;
            }
            watchdog = mWatchdog.schedule(mKiller::kill, timeoutMillis, TimeUnit.MILLISECONDS);
            long size = fd.getStatSize();
            if (size < 0 || size > maxBytes) {
                // Only regular files within the byte budget
                return null;
            }
            Bitmap bitmap = mDecoder.decode(fd, kind, label, maxSide);
            return bitmap != null ? ThumbnailDecoder.toPixels(bitmap, maxSide) : null;
        } catch (Throwable th) {
            // OutOfMemoryError and StackOverflowError included: a damaged file only costs its thumbnail
            Log.w(TAG, "Could not decode a thumbnail of kind " + kind, th);
            return null;
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            try {
                fd.close();
            } catch (IOException ignore) {
            }
        }
    }

    void shutdown() {
        mWatchdog.shutdownNow();
    }
}
