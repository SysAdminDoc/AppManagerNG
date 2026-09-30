// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

/**
 * Writes an editor save so that a failure at any step leaves the original bytes in place.
 * <p>
 * The new content is encoded in full before the file is touched. The current bytes are then kept, the
 * file is rewritten in place, flushed, synced where the stream allows it, and read back. If anything
 * after the first write goes wrong, the original bytes are written back. Rewriting in place, rather
 * than renaming a temporary file over the original, keeps the file's owner, mode and SELinux label,
 * which matters for files the editor reaches as root inside another app's data.
 */
final class EditorSaver {
    private static final String TAG = EditorSaver.class.getSimpleName();

    /** Produces the bytes to save. Runs before the target is touched. */
    interface Encoder {
        @NonNull
        byte[] encode() throws IOException;
    }

    /** Where the save goes. */
    interface Target {
        /** The current bytes, or {@code null} when there is no file yet. */
        @Nullable
        byte[] read(int limit) throws IOException;

        /** Opens the target for writing from the start, dropping what was there. */
        @NonNull
        OutputStream openTruncating() throws IOException;
    }

    static final class SaveException extends IOException {
        /** Whether the target still holds exactly what it held before the save. */
        final boolean originalIntact;

        SaveException(@NonNull String message, @NonNull Throwable cause, boolean originalIntact) {
            super(message, cause);
            this.originalIntact = originalIntact;
        }
    }

    private EditorSaver() {
    }

    /**
     * Saves the encoder's output to {@code target} and reads it back.
     *
     * @param limit the largest original the save may replace; a larger one is left alone
     * @throws SaveException when the save did not land; {@link SaveException#originalIntact} says
     *                       whether the original survived
     */
    @WorkerThread
    static void save(@NonNull Encoder encoder, @NonNull Target target, int limit) throws SaveException {
        byte[] updated;
        try {
            updated = encoder.encode();
        } catch (IOException | RuntimeException e) {
            throw new SaveException("the text could not be encoded", e, true);
        }
        byte[] original;
        try {
            original = target.read(limit + 1);
        } catch (IOException | RuntimeException e) {
            throw new SaveException("the file could not be read before replacing it", e, true);
        }
        if (original != null && original.length > limit) {
            throw new SaveException("the file on disk is larger than the editor can keep a copy of",
                    new IOException("larger than " + limit + " bytes"), true);
        }
        try {
            write(target, updated);
            verify(target, updated, limit);
        } catch (IOException | RuntimeException e) {
            boolean intact = original == null || restore(target, original, limit);
            throw new SaveException(describe(e), e, intact);
        }
    }

    private static void write(@NonNull Target target, @NonNull byte[] bytes) throws IOException {
        try (OutputStream out = target.openTruncating()) {
            out.write(bytes);
            out.flush();
            if (out instanceof FileOutputStream) {
                // Not swallowed: a save that never reached the disk is not a save.
                ((FileOutputStream) out).getFD().sync();
            }
        }
    }

    private static void verify(@NonNull Target target, @NonNull byte[] expected, int limit) throws IOException {
        byte[] actual = target.read(Math.max(limit, expected.length) + 1);
        if (actual == null || !Arrays.equals(actual, expected)) {
            throw new IOException("the saved file did not read back as written");
        }
    }

    private static boolean restore(@NonNull Target target, @NonNull byte[] original, int limit) {
        try {
            write(target, original);
            verify(target, original, limit);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Could not put the original content back.", e);
            return false;
        }
    }

    @NonNull
    private static String describe(@NonNull Throwable e) {
        String message = e.getMessage();
        return message != null && !message.isEmpty() ? message : e.getClass().getSimpleName();
    }

    /** A {@link Target} backed by an AppManagerNG path: raw, root, SAF or VFS. */
    static final class PathTarget implements Target {
        @NonNull
        private final Path mPath;

        PathTarget(@NonNull Path path) {
            mPath = path;
        }

        @Nullable
        @Override
        public byte[] read(int limit) throws IOException {
            if (!mPath.exists()) {
                return null;
            }
            try (InputStream in = mPath.openInputStream()) {
                return IoUtils.readFully(in, limit, false);
            }
        }

        @NonNull
        @Override
        public OutputStream openTruncating() throws IOException {
            return mPath.openOutputStream(false);
        }
    }
}
