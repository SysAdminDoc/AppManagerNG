// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.filecache;

import android.os.Build;
import android.os.storage.StorageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FileCache implements Closeable {
    private static final String TAG = FileCache.class.getSimpleName();

    private static FileCache sInstance;

    @NonNull
    public static FileCache getGlobalFileCache() {
        if (sInstance == null) {
            sInstance = new FileCache(false);
        }
        return sInstance;
    }

    private final File mCacheDir;
    private final Map<Path, File> mFileCacheMap = new HashMap<>();
    private final Set<File> mFileCache = new HashSet<>();
    private final Set<File> mDirectoryCache = new HashSet<>();
    private final boolean mSessionOnly;

    private boolean mClosed = false;

    public FileCache() {
        this(true);
    }

    /**
     * A session cache in {@link #getPrivateStagingDir()}. The default cache prefers shared storage,
     * which other apps holding the storage permission can write to up to Android 10; no other app can
     * write here on any version, so a file staged here stays the bytes that were read.
     */
    @NonNull
    public static FileCache newPrivateSessionCache() {
        return new FileCache(true, getPrivateStagingDir());
    }

    /**
     * Where packages and archives wait for review, install or restore: inside the app's data
     * directory, which no other app can write to, and outside its cache directory, which the system
     * may trim while an install is still reading a file. InternalCacheCleanerService sweeps what a
     * dead process left behind.
     */
    @NonNull
    public static File getPrivateStagingDir() {
        return new File(ContextUtils.getContext().getNoBackupFilesDir(), "staged");
    }

    /**
     * Reads {@code source} once into a new file in {@link #getPrivateStagingDir()}. The caller owns
     * the copy and deletes it.
     */
    @NonNull
    public static File copyToPrivateStaging(@NonNull Path source) throws IOException {
        File stagingDir = getPrivateStagingDir();
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory()) {
            throw new IOException("Could not create " + stagingDir);
        }
        if (!makeRoom(stagingDir, source.length())) {
            throw new IOException("Not enough free space to stage " + source.getName());
        }
        String extension = source.getExtension();
        File copy = File.createTempFile("source_", "." + (extension != null ? extension : "tmp"), stagingDir);
        try (InputStream in = source.openInputStream();
             OutputStream out = new FileOutputStream(copy)) {
            IoUtils.copy(in, out);
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteSilently(copy);
            throw e;
        }
        return copy;
    }

    /**
     * Whether {@code directory}'s volume has room for {@code bytes} more. When the free space falls
     * short, Android 8 and later are asked to clear cached files, other apps' included, to make the
     * room; earlier releases can only report what's free. That can block for a while, so call it
     * where the copy it makes room for runs, never on the main thread. Not marked
     * {@code @WorkerThread}: ApkFile stages from {@code @AnyThread} entry points that already copy
     * the whole package there.
     */
    public static boolean makeRoom(@NonNull File directory, long bytes) {
        if (bytes <= 0 || directory.getUsableSpace() >= bytes) {
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        try {
            StorageManager storageManager = ContextUtils.getContext().getSystemService(StorageManager.class);
            if (storageManager == null) {
                return false;
            }
            UUID volume = storageManager.getUuidForPath(directory);
            if (storageManager.getAllocatableBytes(volume) < bytes) {
                return false;
            }
            storageManager.allocateBytes(volume, bytes);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not make room for %d bytes in %s", e, bytes, directory);
            return false;
        }
    }

    private FileCache(boolean sessionOnly) {
        this(sessionOnly, new File(FileUtils.getCachePath(), "files"));
    }

    private FileCache(boolean sessionOnly, @NonNull File cacheDir) {
        mSessionOnly = sessionOnly;
        mCacheDir = cacheDir;
        // Packages open on several threads at once, so another one may create the folder first
        if (!mCacheDir.mkdirs() && !mCacheDir.isDirectory()) {
            throw new IllegalStateException("Could not create cache. Is this OS broken?");
        }
    }

    @Override
    public void close() {
        mClosed = true;
        if (mSessionOnly) {
            deleteAll();
        }
    }

    @Override
    protected void finalize() {
        if (!mClosed) {
            close();
        }
    }

    @NonNull
    public File getCachedFile(@NonNull Path source) throws IOException {
        if (!source.exists()) {
            // No need for cache if the path is non-existent
            throw new FileNotFoundException("Path " + source + " does not exist.");
        }
        File tempFile = mFileCacheMap.get(source);
        if (tempFile == null || !tempFile.exists()) {
            String extension = source.getExtension();
            tempFile = File.createTempFile(source.getName() + "_", "." + (extension != null ? extension : "tmp"), mCacheDir);
            mFileCacheMap.put(source, tempFile);
        } else if (source.lastModified() > 0 && source.lastModified() < tempFile.lastModified()) {
            return tempFile;
        }
        IoUtils.copy(source, Paths.get(tempFile));
        return tempFile;
    }

    @NonNull
    public File getCachedFile(@NonNull InputStream is, @Nullable String extension) throws IOException {
        File tempFile = File.createTempFile("file_", "." + (extension != null ? extension : "tmp"), mCacheDir);
        mFileCache.add(tempFile);
        try (OutputStream os = new FileOutputStream(tempFile)) {
            IoUtils.copy(is, os);
        }
        return tempFile;
    }

    @NonNull
    public File getCachedFile(@NonNull byte[] bytes, @Nullable String extension) throws IOException {
        File tempFile = File.createTempFile("file_", "." + (extension != null ? extension : "tmp"), mCacheDir);
        mFileCache.add(tempFile);
        try (OutputStream os = new FileOutputStream(tempFile)) {
            os.write(bytes);
        }
        return tempFile;
    }

    @NonNull
    public File createCachedFile(@Nullable String extension) throws IOException {
        File tempFile = File.createTempFile("file_", "." + (extension != null ? extension : "tmp"), mCacheDir);
        mFileCache.add(tempFile);
        return tempFile;
    }

    @NonNull
    public File createCachedDir(@Nullable String prefix) {
        if (prefix != null) {
            prefix = Paths.sanitize(prefix, true);
        }
        if (prefix == null) {
            prefix = "folder";
        }
        String dirName = prefix;
        int i = 1;
        File newDir = new File(mCacheDir, dirName);
        while (newDir.exists()) {
            dirName = prefix + "_" + i;
            newDir = new File(mCacheDir, dirName);
            ++i;
        }
        newDir.mkdirs();
        // Get first path component from dirName
        int idx = dirName.indexOf(File.separator);
        String firstComponent;
        if (idx == -1) {
            firstComponent = dirName;
        } else {
            firstComponent = dirName.substring(0, idx);
        }
        mDirectoryCache.add(new File(mCacheDir, firstComponent));
        return newDir;
    }

    public boolean delete(@Nullable Path path) {
        File tempFile = mFileCacheMap.remove(path);
        return tempFile != null && tempFile.delete();
    }

    public boolean delete(@Nullable File tempFile) {
        if (mFileCache.remove(tempFile)) {
            return tempFile != null && tempFile.delete();
        }
        return false;
    }

    public void deleteAll() {
        for (File file : mFileCacheMap.values()) {
            FileUtils.deleteSilently(file);
        }
        for (File file : mFileCache) {
            FileUtils.deleteSilently(file);
        }
        for (File file : mDirectoryCache) {
            Paths.get(file).delete();
        }
    }
}
