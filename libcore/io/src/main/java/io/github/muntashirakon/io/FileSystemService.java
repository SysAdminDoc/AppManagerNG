// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SELinux;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import aosp.android.content.pm.StringParceledListSlice;
import io.github.muntashirakon.compat.system.OsCompat;
import io.github.muntashirakon.compat.system.StructTimespec;

/**
 * Privileged filesystem service. Every method here runs with the privileges of the process
 * hosting it — root, or the shell uid — and operates on a path chosen by the caller.
 *
 * <h3>Caller-gating contract</h3>
 * <b>This service deliberately has no path root and performs no traversal checks.</b> Anchoring
 * it would defeat its purpose: the file manager browses from {@code /}, backup and restore reach
 * into {@code /data}, and debloating writes under {@code /system}. A caller-supplied {@code ../},
 * an absolute path anywhere on the device, and a symlink pointing outside any particular
 * directory are all <em>expected</em> inputs, not attacks to be filtered here.
 * <p>
 * The security boundary is therefore <em>who holds the binder</em>, not which path they name:
 * <ul>
 * <li>The root-mode binder is handed out by {@code RootServiceServer.bind}, which returns
 * {@code null} unless the calling uid has already registered itself through {@code connect()},
 * and which refuses to instantiate a component that is not a {@code RootService} subclass.
 * <li>The Shizuku-mode binder is handed out by Shizuku's own per-package permission gate.
 * </ul>
 * Anything holding this binder already has the privileges these calls confer. A change that
 * widens who can bind is a change to this contract and must be reviewed as one.
 *
 * <h3>What is checked here</h3>
 * <ul>
 * <li>Structurally unusable paths — null, empty, or carrying an embedded NUL — are refused. A
 * NUL truncates the string in the native layer, so {@code "/data/local/tmp\0/../../system"}
 * would reach the syscall as {@code "/data/local/tmp"}: the path acted on would not be the path
 * that was reviewed.
 * <li>Metadata writes do not follow symlinks where the platform allows it, matching the reads
 * (which use {@code lstat}). {@code setUidGid} uses {@code lchown} and {@code setLastAccess}
 * passes {@code AT_SYMLINK_NOFOLLOW}. Linux has no {@code lchmod} and no symlink-safe SELinux
 * label write, so {@code setMode}, {@code setSelinuxContext} and {@code restoreSelinuxContext}
 * still resolve the final component.
 * </ul>
 */
// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
class FileSystemService extends IFileSystemService.Stub {

    static final int PIPE_CAPACITY = 16 * 4096;

    private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
        @Override
        protected File create(String key) {
            return new File(key);
        }
    };

    /**
     * A path the service will not act on at all. See the class contract: this is not a traversal
     * filter, it only rejects strings whose meaning would change between here and the syscall.
     */
    static boolean isUsablePath(@Nullable String path) {
        return path != null && !path.isEmpty() && path.indexOf('\0') < 0;
    }

    /**
     * @throws IOException when the path is structurally unusable, so the caller sees an
     *                     {@link IOResult} error rather than a silently truncated path
     */
    @NonNull
    static String checkPath(@Nullable String path) throws IOException {
        if (!isUsablePath(path)) {
            throw new IOException("Unusable path");
        }
        return path;
    }

    @NonNull
    private File cached(@Nullable String path) throws IOException {
        return mCache.get(checkPath(path));
    }

    @Override
    public IOResult getCanonicalPath(String path) {
        try {
            return new IOResult(cached(path).getCanonicalPath());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return isUsablePath(path) && mCache.get(path).isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        return isUsablePath(path) && mCache.get(path).isFile();
    }

    @Override
    public boolean isHidden(String path) {
        return isUsablePath(path) && mCache.get(path).isHidden();
    }

    @Override
    public long lastModified(String path) {
        return isUsablePath(path) ? mCache.get(path).lastModified() : 0L;
    }

    @Override
    public IOResult lastAccess(String path) {
        try {
            return new IOResult(Os.lstat(checkPath(path)).st_atime * 1000);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult creationTime(String path) {
        try {
            return new IOResult(Os.lstat(checkPath(path)).st_ctime * 1000);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public long length(String path) {
        return isUsablePath(path) ? mCache.get(path).length() : 0L;
    }

    @Override
    public IOResult createNewFile(String path) {
        try {
            return new IOResult(cached(path).createNewFile());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean delete(String path) {
        return isUsablePath(path) && mCache.get(path).delete();
    }

    @Override
    public StringParceledListSlice list(String path) {
        if (!isUsablePath(path)) {
            return null;
        }
        String[] list = mCache.get(path).list();
        return list != null ? new StringParceledListSlice(Arrays.asList(list)) : null;
    }

    @Override
    public boolean mkdir(String path) {
        return isUsablePath(path) && mCache.get(path).mkdir();
    }

    @Override
    public boolean mkdirs(String path) {
        return isUsablePath(path) && mCache.get(path).mkdirs();
    }

    @Override
    public boolean renameTo(String path, String dest) {
        return isUsablePath(path) && isUsablePath(dest)
                && mCache.get(path).renameTo(mCache.get(dest));
    }

    @Override
    public boolean setLastModified(String path, long time) {
        return isUsablePath(path) && mCache.get(path).setLastModified(time);
    }

    @Override
    public IOResult setLastAccess(String path, long time) {
        long seconds_part = time / 1_000;
        long nanoseconds_part = (time % 1_000) * 1_000_000;
        StructTimespec atime = new StructTimespec(seconds_part, nanoseconds_part);
        StructTimespec mtime = new StructTimespec(0, OsCompat.UTIME_OMIT);
        try {
            OsCompat.utimensat(OsCompat.AT_FDCWD, checkPath(path), atime, mtime,
                    OsCompat.AT_SYMLINK_NOFOLLOW);
            return new IOResult(true);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean setReadOnly(String path) {
        return isUsablePath(path) && mCache.get(path).setReadOnly();
    }

    @Override
    public boolean setWritable(String path, boolean writable, boolean ownerOnly) {
        return isUsablePath(path) && mCache.get(path).setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setReadable(String path, boolean readable, boolean ownerOnly) {
        return isUsablePath(path) && mCache.get(path).setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setExecutable(String path, boolean executable, boolean ownerOnly) {
        return isUsablePath(path) && mCache.get(path).setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean checkAccess(String path, int access) {
        if (!isUsablePath(path)) {
            return false;
        }
        try {
            return Os.access(path, access);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public long getTotalSpace(String path) {
        return isUsablePath(path) ? mCache.get(path).getTotalSpace() : 0L;
    }

    @Override
    public long getFreeSpace(String path) {
        return isUsablePath(path) ? mCache.get(path).getFreeSpace() : 0L;
    }

    @SuppressLint("UsableSpace")
    @Override
    public long getUsableSpace(String path) {
        return isUsablePath(path) ? mCache.get(path).getUsableSpace() : 0L;
    }

    @Override
    public IOResult getMode(String path) {
        try {
            return new IOResult(Os.lstat(checkPath(path)).st_mode);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult setMode(String path, int mode) {
        try {
            // Linux has no lchmod; the mode of a symlink is not meaningful anyway, so this
            // necessarily resolves the final component.
            Os.chmod(checkPath(path), mode);
            return new IOResult(true);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult getUidGid(String path) {
        try {
            StructStat s = Os.lstat(checkPath(path));
            return new IOResult(new UidGidPair(s.st_uid, s.st_gid));
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult setUidGid(String path, int uid, int gid) {
        try {
            // lchown, not chown: the ownership read above comes from lstat, and a chown that
            // follows a symlink would hand ownership of an unrelated file to the caller.
            Os.lchown(checkPath(path), uid, gid);
            return new IOResult(true);
        } catch (ErrnoException | IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public String getSelinuxContext(String path) {
        return isUsablePath(path) ? SELinux.getFileContext(path) : null;
    }

    @Override
    public boolean restoreSelinuxContext(String path) {
        return isUsablePath(path) && SELinux.restorecon(path);
    }

    @Override
    public boolean setSelinuxContext(String path, String context) {
        return isUsablePath(path) && SELinux.setFileContext(path, context);
    }

    @Override
    public IOResult createLink(String link, String target, boolean soft) {
        try {
            checkPath(link);
            checkPath(target);
            if (soft) {
                Os.symlink(target, link);
            } else {
                Os.link(target, link);
            }
            return new IOResult(true);
        } catch (IOException e) {
            return new IOResult(e);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                return new IOResult(false);
            } else {
                return new IOResult(e);
            }
        }
    }

    // I/O APIs

    private final FileContainer openFiles = new FileContainer();
    private final ExecutorService streamPool = Executors.newCachedThreadPool();

    @Override
    public void register(IBinder client) {
        int pid = Binder.getCallingPid();
        try {
            client.linkToDeath(() -> openFiles.pidDied(pid), 0);
        } catch (RemoteException ignored) {
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openChannel(String path, int mode, String fifo) {
        OpenFile f = new OpenFile();
        try {
            checkPath(path);
            checkPath(fifo);
            f.fd = Os.open(path, mode | O_NONBLOCK, 0666);
            f.read = Os.open(fifo, O_RDONLY | O_NONBLOCK, 0);
            f.write = Os.open(fifo, O_WRONLY | O_NONBLOCK, 0);
            return new IOResult(openFiles.put(f));
        } catch (ErrnoException | IOException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public IOResult openReadStream(String path, ParcelFileDescriptor fd) {
        OpenFile f = new OpenFile();
        try {
            f.fd = Os.open(checkPath(path), O_RDONLY, 0);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.write = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pread(PIPE_CAPACITY, -1) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException | IOException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openWriteStream(String path, ParcelFileDescriptor fd, boolean append) {
        OpenFile f = new OpenFile();
        try {
            int mode = O_CREAT | O_WRONLY | (append ? O_APPEND : O_TRUNC);
            f.fd = Os.open(checkPath(path), mode, 0666);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.read = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pwrite(PIPE_CAPACITY, -1, false) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException | IOException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public void close(int handle) {
        openFiles.remove(handle);
    }

    @Override
    public IOResult pread(int handle, int len, long offset) {
        try {
            // A read may legitimately ask for more than one pipe-full; the implementation
            // clamps it. Only nonsensical values are refused.
            checkLenOffset(len, offset, Integer.MAX_VALUE);
            return new IOResult(openFiles.get(handle).pread(len, offset));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult pwrite(int handle, int len, long offset) {
        try {
            // A write is fed from the peer's pipe, so it can never legitimately exceed the
            // pipe capacity; the pre-API-28 path would otherwise apply len as a limit on a
            // fixed-capacity direct buffer and throw an unchecked exception.
            checkLenOffset(len, offset, PIPE_CAPACITY);
            openFiles.get(handle).pwrite(len, offset, true);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    /**
     * Reject caller-supplied lengths and offsets that the I/O paths cannot honour, so they
     * surface as an {@link IOResult} error instead of an unchecked exception inside a
     * privileged process. {@code offset == -1} is the "use the current file position"
     * sentinel; any other negative offset is malformed.
     */
    static void checkLenOffset(int len, long offset, int maxLen) throws IOException {
        if (len < 0) {
            throw new IOException("Negative length " + len);
        }
        if (len > maxLen) {
            throw new IOException("Length " + len + " exceeds the maximum of " + maxLen);
        }
        if (offset < -1) {
            throw new IOException("Negative offset " + offset);
        }
        if (offset > 0 && offset > Long.MAX_VALUE - len) {
            throw new IOException("Offset " + offset + " plus length " + len + " overflows");
        }
    }

    @Override
    public IOResult lseek(int handle, long offset, int whence) {
        try {
            return new IOResult(openFiles.get(handle).lseek(offset, whence));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult size(int handle) {
        try {
            return new IOResult(openFiles.get(handle).size());
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult ftruncate(int handle, long length) {
        try {
            openFiles.get(handle).ftruncate(length);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult sync(int handle, boolean metadata) {
        try {
            openFiles.get(handle).sync(metadata);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }
}
