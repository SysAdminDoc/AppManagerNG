// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Reads each backup file a restore uses once, into a directory no other app can write to, then
 * checks and decrypts that copy. A backup on shared storage or behind a document provider can
 * change between two reads, so hashing one read and extracting another would let the restored bytes
 * differ from the ones that were verified.
 */
@WorkerThread
final class RestoreStaging implements Closeable {
    static final String TAG = RestoreStaging.class.getSimpleName();

    /** Left free after each copy, so staging never fills the disk the app's data goes back to. */
    @VisibleForTesting
    static final long FREE_SPACE_RESERVE = 64L * 1024 * 1024;

    private static final int BUFFER_SIZE = 64 * 1024;

    /** Room made at a time for a source that turns out longer than it said it was. */
    private static final long GROWTH_STEP = 16L * 1024 * 1024;

    /** Stands in for {@link FileCache#makeRoom}, which tests can't hold to a small disk. */
    @VisibleForTesting
    interface Room {
        boolean make(@NonNull File directory, long bytes);
    }

    @Nullable
    private static Room sRoom;

    @VisibleForTesting
    static void setRoom(@Nullable Room room) {
        sRoom = room;
    }

    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @Nullable
    private final BackupItems.Checksum mChecksum;
    @NonNull
    private final String mChecksumAlgo;
    @NonNull
    private final File mDirectory;
    private int mBatches;

    /**
     * @param checksum Checksums the copies must match, or {@code null} when the restore skips the
     *                 checks. The copies are made either way.
     */
    RestoreStaging(@NonNull BackupItems.BackupItem backupItem, @Nullable BackupItems.Checksum checksum,
                   @NonNull String checksumAlgo) throws BackupException {
        mBackupItem = backupItem;
        mChecksum = checksum;
        mChecksumAlgo = checksumAlgo;
        mDirectory = createUniqueDirectory(FileCache.getPrivateStagingDir(), "restore-");
    }

    /** The directory the copies go in. */
    @NonNull
    File getDirectory() {
        return mDirectory;
    }

    /**
     * Copies {@code sources} in order, checks each copy against its checksum and decrypts the
     * copies. The returned files are what the restore reads from then on.
     *
     * @param failurePrefix First line of the error when a copy doesn't match its checksum
     */
    @NonNull
    Path[] stage(@NonNull Path[] sources, @NonNull String failurePrefix) throws BackupException {
        // Each call gets its own folder, so two files with one name never meet
        File batch = createUniqueDirectory(mDirectory, String.valueOf(mBatches++));
        Path[] copies = new Path[sources.length];
        boolean done = false;
        try {
            for (int i = 0; i < sources.length; ++i) {
                copies[i] = copyAndVerify(sources[i], batch, failurePrefix);
            }
            Path[] decrypted;
            try {
                decrypted = mBackupItem.decrypt(copies);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(sources), e);
            }
            if (decrypted != copies) {
                // Only the plain copies are read from here on
                deleteQuietly(batch);
            }
            done = true;
            return decrypted;
        } finally {
            if (!done) {
                deleteQuietly(batch);
            }
        }
    }

    /** A new empty folder next to the copies, deleted with them. */
    @NonNull
    Path newDirectory(@NonNull String prefix) throws BackupException {
        return Paths.getUnprivileged(createUniqueDirectory(mDirectory, prefix));
    }

    @Override
    public void close() {
        deleteQuietly(mDirectory);
    }

    @NonNull
    private Path copyAndVerify(@NonNull Path source, @NonNull File batch, @NonNull String failurePrefix)
            throws BackupException {
        String name = source.getName();
        if (!isPlainFileName(name)) {
            throw new BackupException(failurePrefix + "\nFile: " + source + "\nUnexpected file name.");
        }
        File copy = new File(batch, name);
        long declared = Math.max(0, source.length());
        if (!makeRoom(batch, declared + FREE_SPACE_RESERVE)) {
            throw new BackupException("Not enough free space to stage " + name + " for the restore."
                    + "\nNeeded: " + declared + " bytes");
        }
        try (InputStream in = source.openInputStream();
             OutputStream out = new FileOutputStream(copy)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long copied = 0;
            long room = declared;
            int read;
            while ((read = in.read(buffer)) != -1) {
                copied += read;
                if (copied > room) {
                    // Longer than it said it was, so make room a step at a time and keep the reserve
                    if (!makeRoom(batch, GROWTH_STEP + FREE_SPACE_RESERVE)) {
                        throw new BackupException("Not enough free space to stage " + name + " for the restore.");
                    }
                    room = copied - read + GROWTH_STEP;
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(copy);
            throw new BackupException("Could not copy " + source + " for the restore.", e);
        } catch (BackupException e) {
            deleteQuietly(copy);
            throw e;
        }
        if (mChecksum != null) {
            String found;
            try (InputStream in = new FileInputStream(copy)) {
                found = DigestUtils.getHexDigest(mChecksumAlgo, in);
            } catch (IOException e) {
                deleteQuietly(copy);
                throw new BackupException("Could not read the copy of " + source + ".", e);
            }
            String required = mChecksum.get(name);
            if (!found.equals(required)) {
                deleteQuietly(copy);
                throw new BackupException(failurePrefix
                        + "\nFile: " + source
                        + "\nFound: " + found
                        + "\nRequired: " + required);
            }
        }
        return Paths.getUnprivileged(copy);
    }

    private static boolean makeRoom(@NonNull File directory, long bytes) {
        Room room = sRoom;
        return room != null ? room.make(directory, bytes) : FileCache.makeRoom(directory, bytes);
    }

    private static boolean isPlainFileName(@Nullable String name) {
        return name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    @NonNull
    private static File createUniqueDirectory(@NonNull File parent, @NonNull String prefix) throws BackupException {
        if (!parent.mkdirs() && !parent.isDirectory()) {
            throw new BackupException("Could not create " + parent);
        }
        String base = prefix + Long.toHexString(System.nanoTime());
        for (int attempt = 0; attempt < 8; ++attempt) {
            File directory = new File(parent, attempt == 0 ? base : base + "-" + attempt);
            if (directory.mkdir()) {
                return directory;
            }
        }
        throw new BackupException("Could not create a restore staging directory in " + parent);
    }

    private static void deleteQuietly(@NonNull File file) {
        List<File> failed = new ArrayList<>();
        deleteRecursively(file, failed);
        if (!failed.isEmpty()) {
            Log.w(TAG, "Could not delete %s", failed);
        }
    }

    private static void deleteRecursively(@NonNull File file, @NonNull List<File> failed) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child, failed);
            }
        }
        if (file.exists() && !file.delete()) {
            failed.add(file);
        }
    }
}
