// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Fail-closed replacement of a small local metadata file.
 *
 * <p>Unlike a plain temp-file-plus-rename, this never deletes the current file to make room for a
 * retry: the previous content is moved aside first and is put back if the replacement does not
 * land. A failed {@code fsync} is reported rather than swallowed, and every intermediate state a
 * crash can leave behind is resolved deterministically on the next read:
 *
 * <ul>
 *     <li>{@code <name>.new} present — the replacement never started; discard it.</li>
 *     <li>{@code <name>.bak} present and {@code <name>} missing — the replacement was interrupted
 *     between moving the old file aside and renaming the new one in; restore the old file.</li>
 *     <li>{@code <name>.bak} present and {@code <name>} present — the replacement landed and only
 *     the cleanup was interrupted; drop the backup.</li>
 * </ul>
 *
 * <p>A reader therefore always observes either the complete old content or the complete new one.
 * Callers are responsible for their own mutual exclusion.
 */
public final class DurableFile {
    private static final String TAG = "DurableFile";

    @VisibleForTesting
    static final String NEW_SUFFIX = ".new";
    @VisibleForTesting
    static final String BACKUP_SUFFIX = ".bak";

    @NonNull
    private final File mBase;
    @NonNull
    private final File mNew;
    @NonNull
    private final File mBackup;

    public DurableFile(@NonNull File base) {
        mBase = base;
        mNew = new File(base.getPath() + NEW_SUFFIX);
        mBackup = new File(base.getPath() + BACKUP_SUFFIX);
    }

    @NonNull
    public File getBaseFile() {
        return mBase;
    }

    /**
     * Resolves any state left behind by an interrupted write. Idempotent, and implied by
     * {@link #read(long)} and {@link #write(byte[])}.
     */
    public void recover() {
        if (mBackup.exists()) {
            if (mBase.exists()) {
                deleteOrLog(mBackup);
            } else if (!mBackup.renameTo(mBase)) {
                Log.w(TAG, "Could not restore %s from its backup.", mBase);
            }
        }
        if (mNew.exists()) {
            deleteOrLog(mNew);
        }
    }

    public boolean exists() {
        recover();
        return mBase.isFile();
    }

    /**
     * @param maxBytes hard ceiling on the accepted file size; a larger file is treated as corrupt.
     * @return the file content, or {@code null} when there is nothing valid to read.
     */
    @Nullable
    @WorkerThread
    public String read(long maxBytes) {
        recover();
        if (!mBase.isFile()) {
            return null;
        }
        long length = mBase.length();
        if (length <= 0L) {
            return null;
        }
        if (length > maxBytes) {
            Log.w(TAG, "%s is implausibly large (%d bytes); treating as corrupt.", mBase, length);
            return null;
        }
        try (FileInputStream inputStream = new FileInputStream(mBase)) {
            byte[] buffer = new byte[(int) length];
            int read = 0;
            while (read < buffer.length) {
                int count = inputStream.read(buffer, read, buffer.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read " + mBase, e);
            return null;
        }
    }

    /**
     * Replaces the file content.
     *
     * @throws IOException if the new content could not be written, synced, or moved into place. The
     *                     previous content is still readable in every one of those cases.
     */
    @WorkerThread
    public void write(@NonNull byte[] bytes) throws IOException {
        recover();
        File parent = mBase.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (FileOutputStream outputStream = new FileOutputStream(mNew)) {
            outputStream.write(bytes);
            outputStream.flush();
            // Not swallowed: an unsynced replacement is not a durable one.
            outputStream.getFD().sync();
        } catch (IOException e) {
            deleteOrLog(mNew);
            throw e;
        }
        boolean hadBase = mBase.exists();
        if (hadBase && !mBase.renameTo(mBackup)) {
            deleteOrLog(mNew);
            throw new IOException("Could not move " + mBase + " aside.");
        }
        if (!mNew.renameTo(mBase)) {
            if (hadBase && !mBackup.renameTo(mBase)) {
                Log.e(TAG, "Could not restore %s after a failed replacement.", mBase);
            }
            deleteOrLog(mNew);
            throw new IOException("Could not replace " + mBase + ".");
        }
        if (hadBase) {
            deleteOrLog(mBackup);
        }
    }

    /**
     * Removes the file and every artefact of an interrupted write.
     */
    public void delete() {
        deleteOrLog(mNew);
        deleteOrLog(mBackup);
        deleteOrLog(mBase);
    }

    private static void deleteOrLog(@NonNull File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete %s", file);
        }
    }
}
