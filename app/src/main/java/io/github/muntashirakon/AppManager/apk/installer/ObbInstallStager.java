// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

/**
 * Stages the OBB (expansion) files of an install request in a private directory and only replaces
 * the live per-user OBB generation once the APK session itself has succeeded.
 *
 * <p>The previous generation is retained under {@link #BACKUP_SUFFIX} for the whole duration of an
 * activation, so a failure part-way through the copy restores exactly the files that were there
 * before. An activation that is interrupted (process death, reboot) is rolled back deterministically
 * on the next attempt: a retained backup can only exist while an activation is in flight, so its
 * presence always means the new generation was never committed.
 */
public class ObbInstallStager implements Closeable {
    public static final String TAG = ObbInstallStager.class.getSimpleName();

    @VisibleForTesting
    static final String BACKUP_SUFFIX = ".ng-obb-prev";

    /**
     * Extracts every OBB of the install request into the given (empty, private) staging directory.
     */
    public interface ObbExtractor {
        @WorkerThread
        void extractObb(@NonNull Path stagingDir) throws IOException;
    }

    @NonNull
    private final Path mStagingDir;
    private final List<String> mStagedNames = new ArrayList<>();
    private boolean mStaged;
    private boolean mClosed;

    public ObbInstallStager(@NonNull Path stagingDir) {
        mStagingDir = stagingDir;
    }

    public boolean isStaged() {
        return mStaged;
    }

    @NonNull
    public List<String> getStagedNames() {
        return Collections.unmodifiableList(mStagedNames);
    }

    /**
     * Extracts and validates every OBB file before anything on the device is mutated. Throws if the
     * request does not produce at least one usable OBB file, so the caller can abort the install
     * while the existing generation is still untouched.
     */
    @WorkerThread
    public void stage(@NonNull ObbExtractor extractor) throws IOException {
        if (mClosed) {
            throw new IOException("The OBB stager has already been discarded.");
        }
        if (mStaged) {
            throw new IOException("OBB files have already been staged.");
        }
        if (!mStagingDir.exists() && !mStagingDir.mkdirs()) {
            throw new IOException("Could not create the OBB staging directory.");
        }
        if (!mStagingDir.isDirectory()) {
            throw new IOException("The OBB staging location is not a directory.");
        }
        // The staging directory is ours; make sure a previous aborted run cannot contribute files.
        for (Path leftover : mStagingDir.listFiles()) {
            leftover.delete();
        }
        extractor.extractObb(mStagingDir);
        Path[] staged = mStagingDir.listFiles();
        if (staged.length == 0) {
            throw new IOException("No OBB file could be extracted from the package.");
        }
        for (Path file : staged) {
            String name = file.getName();
            if (!file.isFile()) {
                throw new IOException("Staged OBB entry is not a regular file: " + name);
            }
            if (isRetainedName(name)) {
                throw new IOException("Staged OBB entry uses a reserved name: " + name);
            }
            if (file.length() <= 0) {
                throw new IOException("Staged OBB file is empty: " + name);
            }
            mStagedNames.add(name);
        }
        Collections.sort(mStagedNames);
        mStaged = true;
    }

    /**
     * Replaces the contents of {@code obbDir} with the staged generation. On any failure the
     * directory is restored to the state it had before the call and an {@link IOException} is
     * thrown; the staged files stay available so the caller can retry.
     */
    @WorkerThread
    public void activate(@NonNull Path obbDir) throws IOException {
        if (!mStaged) {
            throw new IOException("OBB files were never staged.");
        }
        if (mClosed) {
            throw new IOException("The OBB stager has already been discarded.");
        }
        rollbackInterruptedActivation(obbDir);
        List<String> retained = new ArrayList<>();
        List<String> written = new ArrayList<>();
        try {
            for (Path existing : obbDir.listFiles()) {
                String name = existing.getName();
                if (isRetainedName(name) || !existing.isFile()) {
                    continue;
                }
                if (!existing.renameTo(name + BACKUP_SUFFIX)) {
                    throw new IOException("Could not retain the previous OBB file " + name);
                }
                retained.add(name);
            }
            for (String name : mStagedNames) {
                Path source = mStagingDir.findFile(name);
                Path destination = obbDir.findOrCreateFile(name, null);
                written.add(name);
                try (InputStream is = source.openInputStream();
                     OutputStream os = destination.openOutputStream()) {
                    IoUtils.copy(is, os);
                }
            }
        } catch (IOException | RuntimeException e) {
            restore(obbDir, retained, written);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Could not activate the staged OBB files.", e);
        }
        // Everything landed: the retained generation is no longer needed.
        for (String name : retained) {
            deleteQuietly(obbDir.findFileOrNull(name + BACKUP_SUFFIX));
        }
    }

    /**
     * Deletes the staged files. Safe to call more than once and after {@link #activate(Path)}.
     */
    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        mStaged = false;
        try {
            for (Path file : mStagingDir.listFiles()) {
                file.delete();
            }
            mStagingDir.delete();
        } catch (Throwable e) {
            Log.w(TAG, "Could not clean up the OBB staging directory.", e);
        }
        mStagedNames.clear();
    }

    /**
     * An activation only removes the retained generation once every staged file has landed, so a
     * retained file means the previous attempt never committed. Undo it before starting over.
     */
    private void rollbackInterruptedActivation(@NonNull Path obbDir) {
        for (Path file : obbDir.listFiles()) {
            String name = file.getName();
            if (!isRetainedName(name)) {
                continue;
            }
            String originalName = name.substring(0, name.length() - BACKUP_SUFFIX.length());
            deleteQuietly(obbDir.findFileOrNull(originalName));
            if (!file.renameTo(originalName)) {
                Log.w(TAG, "Could not restore the interrupted OBB generation for %s", originalName);
            }
        }
    }

    private void restore(@NonNull Path obbDir, @NonNull List<String> retained, @NonNull List<String> written) {
        for (String name : written) {
            deleteQuietly(obbDir.findFileOrNull(name));
        }
        for (String name : retained) {
            Path backup = obbDir.findFileOrNull(name + BACKUP_SUFFIX);
            if (backup == null) {
                Log.w(TAG, "Retained OBB file %s disappeared during rollback.", name);
                continue;
            }
            if (!backup.renameTo(name)) {
                Log.w(TAG, "Could not restore the previous OBB file %s.", name);
            }
        }
    }

    private static boolean isRetainedName(@NonNull String name) {
        return name.endsWith(BACKUP_SUFFIX);
    }

    private static void deleteQuietly(@Nullable Path path) {
        if (path != null) {
            path.delete();
        }
    }
}
