// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pulls one app's APK files from a host into the {@link ReceivedApkStore}, checks their
 * signatures, and packs them the way the installer reads them: a plain APK, or an APKS bundle
 * when there are splits.
 * <p>
 * Files that already arrived for the same listing are reused, and a file that broke off part way
 * continues from where it stopped when the host can do that. A file whose bytes can't be trusted
 * is thrown away, and an entry that fails the signature check is deleted whole.
 */
public final class LanSyncDownloader {
    public static final String DIR_NAME = "lan_sync";
    public static final String MIME_APK = "application/vnd.android.package-archive";
    public static final String MIME_APKS = "application/x-apks";
    static final long SPACE_MARGIN = 32L * 1024 * 1024;

    public interface Progress {
        void onProgress(long bytesDone, long bytesTotal);
    }

    public static final class Result {
        @NonNull
        public final File file;
        @NonNull
        public final String mimeType;
        /**
         * Whether {@link #file} is a temporary bundle the installer should delete when it's done.
         * A single APK is the received copy itself, which stays until it's cleaned up.
         */
        public final boolean owned;
        @NonNull
        public final String packageName;
        @NonNull
        public final String label;
        /**
         * The {@link ReceivedApkStore} entry the files came from.
         */
        @NonNull
        public final String entryKey;
        /**
         * The received APK files {@link #file} was made from, base first.
         */
        @NonNull
        public final List<File> apks;

        Result(@NonNull File file, @NonNull String mimeType, boolean owned, @NonNull ReceivedApkStore.Entry entry) {
            this.file = file;
            this.mimeType = mimeType;
            this.owned = owned;
            this.packageName = entry.app.packageName;
            this.label = entry.app.label;
            this.entryKey = entry.key;
            this.apks = entry.files();
        }
    }

    private LanSyncDownloader() {
    }

    @WorkerThread
    @NonNull
    public static Result pull(@NonNull SyncClientSession session, @NonNull SyncApp app, @Nullable SyncApp installed,
                              @NonNull File dir, @NonNull ApkSignatureGate.DigestReader reader,
                              @Nullable Progress progress) throws IOException {
        return pull(session, app, installed, false, dir, reader, progress);
    }

    /**
     * @param replacing the installed copy is signed by a different key and will be uninstalled
     *                  before this one goes in. The files still have to be signed by the keys the
     *                  other device listed, splits matching the base, but not by the installed key.
     *                  Ignored when the keys match or nothing is installed.
     */
    @WorkerThread
    @NonNull
    public static Result pull(@NonNull SyncClientSession session, @NonNull SyncApp app, @Nullable SyncApp installed,
                              boolean replacing, @NonNull File dir, @NonNull ApkSignatureGate.DigestReader reader,
                              @Nullable Progress progress) throws IOException {
        SyncComparator.Status status = SyncComparator.compare(app, installed);
        replacing = replacing && status == SyncComparator.Status.SIGNATURE_DIFFERS;
        if (!replacing && !SyncComparator.canPull(status)) {
            throw new IOException("This app is not newer on the other device or is signed differently.");
        }
        if (replacing) {
            // Checked against the listed keys only: the installed copy goes before the install
            installed = null;
        }
        Set<String> names = new HashSet<>();
        for (SyncApp.ApkFileInfo info : app.files) {
            if (!SyncMessages.isSafeFileName(info.name) || !names.add(info.name)) {
                throw new SyncProtocolException("Bad file name " + info.name);
            }
        }
        if (app.files.isEmpty()) {
            throw new SyncProtocolException("The other device listed no files for " + app.packageName);
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not create " + dir);
        }
        ReceivedApkStore store = new ReceivedApkStore(dir);
        ReceivedApkStore.Entry entry = store.open(app, System.currentTimeMillis());
        long total = app.totalSize();
        long needed = (total - entry.bytesHere()) + (entry.isBundle() ? total : 0) + SPACE_MARGIN;
        if (dir.getUsableSpace() < needed) {
            throw new IOException("Not enough free space to download " + app.label + ".");
        }
        try {
            long done = 0;
            for (SyncApp.ApkFileInfo info : app.files) {
                File file = entry.file(info.name);
                if (file.isFile() && file.length() == info.size) {
                    // Arrived earlier and passed the host's digest then
                    done += info.size;
                    if (progress != null) {
                        progress.onProgress(done, total);
                    }
                    continue;
                }
                deleteQuietly(file);
                fetch(session, entry, info, done, total, progress);
                done += info.size;
            }
            return prepare(store, store.markComplete(entry, System.currentTimeMillis()), dir, installed, reader);
        } catch (SyncClientSession.RemoteError e) {
            // The host no longer has this exact copy, so the entry can never be finished
            store.delete(entry);
            throw e;
        }
    }

    /**
     * Checks a finished entry's files again and hands back what the installer opens. A failed
     * check deletes the entry. A bundle is packed into a new temporary file in {@code dir}.
     */
    @WorkerThread
    @NonNull
    public static Result prepare(@NonNull ReceivedApkStore store, @NonNull ReceivedApkStore.Entry entry,
                                 @NonNull File dir, @Nullable SyncApp installed,
                                 @NonNull ApkSignatureGate.DigestReader reader) throws IOException {
        List<File> files = verify(store, entry, installed, reader);
        if (!entry.isBundle()) {
            return new Result(files.get(0), MIME_APK, false, entry);
        }
        File output = File.createTempFile("sync-", ".apks", dir);
        boolean written = false;
        try {
            writeBundle(output, entry.app.files, files);
            written = true;
        } finally {
            if (!written) {
                deleteQuietly(output);
            }
        }
        return new Result(output, MIME_APKS, true, entry);
    }

    /**
     * Checks a finished entry before anything installs it: every file is here, signed by the keys
     * the other device listed, and the splits match the base. An entry that fails is deleted.
     *
     * @return the entry's files, base first
     */
    @WorkerThread
    @NonNull
    public static List<File> verify(@NonNull ReceivedApkStore store, @NonNull ReceivedApkStore.Entry entry,
                                    @Nullable SyncApp installed,
                                    @NonNull ApkSignatureGate.DigestReader reader) throws IOException {
        if (!entry.hasAllFiles()) {
            throw new IOException("Some files of " + entry.app.label + " are missing.");
        }
        List<File> files = entry.files();
        try {
            ApkSignatureGate.check(files, entry.app, installed, reader);
        } catch (ApkSignatureGate.MismatchException e) {
            store.delete(entry);
            throw e;
        }
        return files;
    }

    /**
     * Gets one file into the entry, continuing a part left from an earlier try when the host can.
     * A part survives a broken connection. It goes when its bytes can't be trusted: a protocol
     * error, a digest that doesn't match, or a host that reports a problem with the file.
     */
    private static void fetch(@NonNull SyncClientSession session, @NonNull ReceivedApkStore.Entry entry,
                              @NonNull SyncApp.ApkFileInfo info, long base, long total,
                              @Nullable Progress progress) throws IOException {
        File part = entry.part(info.name);
        long offset = part.isFile() ? part.length() : 0;
        if (offset > info.size || (offset > 0 && !session.canResume())) {
            deleteQuietly(part);
            offset = 0;
        }
        MessageDigest prefix = offset > 0 ? digestOf(part, offset) : null;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(part, offset > 0))) {
            session.download(entry.app.packageName, info.name, info.size, offset, prefix, out, progress == null
                    ? null : bytes -> progress.onProgress(base + bytes, total));
        } catch (SyncProtocolException | SyncClientSession.RemoteError e) {
            deleteQuietly(part);
            throw e;
        }
        File file = entry.file(info.name);
        if (!part.renameTo(file)) {
            deleteQuietly(part);
            throw new IOException("Could not save " + info.name);
        }
    }

    @NonNull
    private static MessageDigest digestOf(@NonNull File file, long length) throws IOException {
        MessageDigest digest = SyncCrypto.sha256();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            long left = length;
            while (left > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (n < 0) {
                    throw new IOException("Could not read " + file);
                }
                digest.update(buffer, 0, n);
                left -= n;
            }
        }
        return digest;
    }

    /**
     * Deletes loose files older than {@code maxAgeMillis}, such as a bundle from an install that
     * was never finished because the app was closed. Entries of the {@link ReceivedApkStore} live
     * in a folder and have their own cleanup.
     */
    public static void purgeStale(@NonNull File dir, long maxAgeMillis, long nowMillis) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && nowMillis - file.lastModified() > maxAgeMillis) {
                deleteQuietly(file);
            }
        }
    }

    private static void writeBundle(@NonNull File output, @NonNull List<SyncApp.ApkFileInfo> infos,
                                    @NonNull List<File> parts) throws IOException {
        writeBundle(new FileOutputStream(output), infos, parts);
    }

    /**
     * Writes the files as a stored (uncompressed) .apks bundle and closes {@code out}.
     */
    static void writeBundle(@NonNull OutputStream out, @NonNull List<SyncApp.ApkFileInfo> infos,
                            @NonNull List<File> parts) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out))) {
            for (int i = 0; i < parts.size(); ++i) {
                File part = parts.get(i);
                ZipEntry entry = new ZipEntry(infos.get(i).name);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(part.length());
                entry.setCompressedSize(part.length());
                entry.setCrc(crcOf(part, buffer));
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(part)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        zip.write(buffer, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static long crcOf(@NonNull File file, @NonNull byte[] buffer) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                crc.update(buffer, 0, n);
            }
        }
        return crc.getValue();
    }

    static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
