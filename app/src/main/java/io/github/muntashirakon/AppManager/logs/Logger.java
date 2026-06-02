// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class Logger implements Closeable {
    /**
     * Within-session ceiling for a file-backed log. A long-lived process (the
     * resident app, the privileged server, monitors) writes to the same file
     * for its whole lifetime; truncate-on-launch only bounds growth across
     * restarts, not within a session. When the file crosses this size it is
     * truncated and a marker line is written so it cannot grow without bound.
     */
    private static final long MAX_LOG_BYTES = 4L * 1024 * 1024;

    @NonNull
    public static File getLoggingDirectory() {
        return FileUtils.getCachePath();
    }

    /** Stable lock so {@link #mWriter} can be safely reopened during rotation. */
    private final Object mLock = new Object();
    @Nullable
    private PrintWriter mWriter;
    /** Set only for file-backed loggers; enables in-session size-capped rotation. */
    @Nullable
    private final File mLogFile;
    private long mBytesWritten;

    private boolean mIsClosed;

    protected Logger(@Nullable File logFile, boolean append) throws IOException {
        mLogFile = logFile;
        if (logFile != null) {
            mWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, append)));
            mBytesWritten = append && logFile.isFile() ? logFile.length() : 0L;
        } else mWriter = null;
    }

    protected Logger(@Nullable PrintWriter printWriter) throws IOException {
        mWriter = printWriter;
        mLogFile = null;
    }

    @CallSuper
    public void println(@Nullable Object message) {
        println(message, null);
    }

    @CallSuper
    public void println(@Nullable Object message, @Nullable Throwable tr) {
        synchronized (mLock) {
            if (mWriter == null) {
                // Do nothing
                return;
            }
            String text = String.valueOf(message);
            mWriter.println(text);
            // +1 for the newline; an approximation is fine for a rotation gate.
            mBytesWritten += text.length() + 1L;
            if (tr != null) {
                tr.printStackTrace(mWriter);
                mBytesWritten += 512L; // rough stack-trace estimate
            }
            if (BuildConfig.DEBUG) {
                mWriter.flush();
            }
            if (mLogFile != null && mBytesWritten > MAX_LOG_BYTES) {
                rotateLocked();
            }
        }
    }

    /**
     * Truncate the file-backed log when it crosses {@link #MAX_LOG_BYTES}.
     * Must be called while holding {@link #mLock}. On any failure the previous
     * writer is left in place so logging never breaks because of rotation.
     */
    private void rotateLocked() {
        File logFile = mLogFile;
        if (logFile == null) {
            return;
        }
        PrintWriter rotated;
        try {
            mWriter.flush();
            mWriter.close();
            rotated = new PrintWriter(new BufferedWriter(new FileWriter(logFile, false)));
        } catch (IOException | RuntimeException e) {
            // Could not reopen — keep writing to the existing writer if it is
            // still usable; otherwise drop to no-op rather than crash.
            return;
        }
        mWriter = rotated;
        mBytesWritten = 0L;
        rotated.println("--- log truncated (exceeded " + MAX_LOG_BYTES + " bytes) ---");
    }

    @CallSuper
    @Override
    public void close() {
        synchronized (mLock) {
            if (mWriter != null) {
                mWriter.flush();
                mWriter.close();
            }
            mIsClosed = true;
        }
    }

    @Override
    protected void finalize() {
        // Closing is mandatory in order to make sure the logs are written correctly
        synchronized (mLock) {
            if (!mIsClosed && mWriter != null) {
                mWriter.flush();
                mWriter.close();
            }
        }
    }
}
