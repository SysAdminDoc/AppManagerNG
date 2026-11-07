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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pulls one app's APK files from a host into the cache, checks their signatures, and packs them
 * the way the installer reads them: a plain APK, or an APKS bundle when there are splits.
 * Nothing is left behind when any step fails.
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

        Result(@NonNull File file, @NonNull String mimeType) {
            this.file = file;
            this.mimeType = mimeType;
        }
    }

    private LanSyncDownloader() {
    }

    @WorkerThread
    @NonNull
    public static Result pull(@NonNull SyncClientSession session, @NonNull SyncApp app, @Nullable SyncApp installed,
                              @NonNull File dir, @NonNull ApkSignatureGate.DigestReader reader,
                              @Nullable Progress progress) throws IOException {
        if (!SyncComparator.canPull(SyncComparator.compare(app, installed))) {
            throw new IOException("This app is not newer on the other device or is signed differently.");
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
        boolean bundle = app.files.size() > 1;
        long total = app.totalSize();
        long needed = (bundle ? 2 * total : total) + SPACE_MARGIN;
        if (dir.getUsableSpace() < needed) {
            throw new IOException("Not enough free space to download " + app.label + ".");
        }
        List<File> parts = new ArrayList<>(app.files.size());
        List<Long> crcs = new ArrayList<>(app.files.size());
        File output = null;
        boolean success = false;
        try {
            long done = 0;
            for (SyncApp.ApkFileInfo info : app.files) {
                File part = File.createTempFile("part-", ".apk", dir);
                parts.add(part);
                final long offset = done;
                CRC32 crc = new CRC32();
                try (OutputStream out = new CheckedOutputStream(
                        new BufferedOutputStream(new FileOutputStream(part)), crc)) {
                    session.download(app.packageName, info.name, info.size, out, progress == null ? null
                            : bytes -> progress.onProgress(offset + bytes, total));
                }
                crcs.add(crc.getValue());
                done += info.size;
            }
            ApkSignatureGate.check(parts, app, installed, reader);
            Result result;
            if (bundle) {
                output = File.createTempFile("sync-", ".apks", dir);
                writeBundle(output, app.files, parts, crcs);
                result = new Result(output, MIME_APKS);
            } else {
                result = new Result(parts.get(0), MIME_APK);
            }
            success = true;
            return result;
        } finally {
            if (!success) {
                deleteQuietly(output);
            }
            for (File part : parts) {
                if (!success || bundle) {
                    deleteQuietly(part);
                }
            }
        }
    }

    /**
     * Deletes leftovers older than {@code maxAgeMillis}, such as files from an install that was
     * never finished because the app was closed.
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
                                    @NonNull List<File> parts, @NonNull List<Long> crcs) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            for (int i = 0; i < parts.size(); ++i) {
                File part = parts.get(i);
                ZipEntry entry = new ZipEntry(infos.get(i).name);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(part.length());
                entry.setCompressedSize(part.length());
                entry.setCrc(crcs.get(i));
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

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
