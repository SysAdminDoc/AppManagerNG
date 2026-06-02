// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class FmArchiveUtils {
    static final String ZIP_EXTENSION = "zip";
    /** Upper bound on numbered-collision probing in {@link #findNextBestDisplayName}. */
    private static final int MAX_NAME_ATTEMPTS = 10_000;

    private FmArchiveUtils() {
    }

    static boolean isSupportedZip(@NonNull Path path) {
        if (path.isDirectory()) {
            return false;
        }
        try {
            return FileUtils.isZip(path);
        } catch (IOException e) {
            return false;
        }
    }

    @WorkerThread
    static void createZipArchive(@NonNull List<Path> sources, @NonNull Path destination,
                                 @Nullable ProgressCallback progressCallback) throws IOException {
        List<ArchiveItem> archiveItems = getArchiveItems(sources);
        int total = archiveItems.size();
        if (progressCallback != null) {
            progressCallback.onProgress(destination.getName(), 0, total);
        }
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(destination.openOutputStream()))) {
            int done = 0;
            for (ArchiveItem archiveItem : archiveItems) {
                throwIfInterrupted();
                ZipEntry zipEntry = new ZipEntry(archiveItem.entryName);
                long lastModified = archiveItem.path.lastModified();
                if (lastModified > 0) {
                    zipEntry.setTime(lastModified);
                }
                zipOutputStream.putNextEntry(zipEntry);
                if (!archiveItem.path.isDirectory()) {
                    try (InputStream inputStream = new BufferedInputStream(archiveItem.path.openInputStream())) {
                        IoUtils.copy(inputStream, zipOutputStream);
                    }
                }
                zipOutputStream.closeEntry();
                ++done;
                if (progressCallback != null) {
                    progressCallback.onProgress(archiveItem.path.getName(), done, total);
                }
            }
        }
    }

    @WorkerThread
    static void extractZipArchive(@NonNull Path archive, @NonNull Path destination,
                                  @NonNull ConflictResolver conflictResolver,
                                  @Nullable ProgressCallback progressCallback) throws IOException {
        int total = countZipEntries(archive);
        if (progressCallback != null) {
            progressCallback.onProgress(archive.getName(), 0, total);
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(archive.openInputStream()))) {
            ZipEntry zipEntry;
            int done = 0;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                throwIfInterrupted();
                String entryName = normalizeZipEntryName(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    destination.createDirectoriesIfRequired(entryName);
                } else {
                    String outputName = resolveOutputName(destination, entryName, conflictResolver);
                    if (outputName != null) {
                        Path outputFile = destination.createNewArbitraryFile(outputName, null);
                        try (OutputStream outputStream = new BufferedOutputStream(outputFile.openOutputStream())) {
                            IoUtils.copy(zipInputStream, outputStream);
                        }
                        long entryTime = zipEntry.getTime();
                        if (entryTime > 0) {
                            outputFile.setLastModified(entryTime);
                        }
                    }
                }
                zipInputStream.closeEntry();
                ++done;
                if (progressCallback != null) {
                    progressCallback.onProgress(entryName, done, total);
                }
            }
        }
    }

    @NonNull
    private static List<ArchiveItem> getArchiveItems(@NonNull List<Path> sources) throws IOException {
        List<ArchiveItem> archiveItems = new ArrayList<>();
        Set<String> seenEntryNames = new HashSet<>();
        for (Path source : sources) {
            throwIfInterrupted();
            if (!source.exists()) {
                continue;
            }
            for (Path path : Paths.getAll(source)) {
                throwIfInterrupted();
                String entryName = getEntryName(source, path);
                if (path.isDirectory()) {
                    entryName += Paths.PATH_SEPARATOR;
                }
                if (seenEntryNames.add(entryName)) {
                    archiveItems.add(new ArchiveItem(path, entryName));
                }
            }
        }
        return archiveItems;
    }

    @NonNull
    private static String getEntryName(@NonNull Path source, @NonNull Path path) throws IOException {
        if (source.equals(path)) {
            return normalizeZipEntryName(source.getName());
        }
        String relativePath = Paths.relativePath(path, source);
        if (path.isDirectory() && relativePath.endsWith(Paths.PATH_SEPARATOR)) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        return normalizeZipEntryName(source.getName() + Paths.PATH_SEPARATOR + relativePath);
    }

    @Nullable
    private static String resolveOutputName(@NonNull Path destination, @NonNull String entryName,
                                            @NonNull ConflictResolver conflictResolver) throws IOException {
        if (!destination.hasFile(entryName)) {
            return entryName;
        }
        switch (conflictResolver.onConflict(entryName)) {
            case REPLACE:
                return entryName;
            case KEEP_BOTH:
                return findNextBestEntryName(destination, entryName);
            case SKIP:
                return null;
            case ABORT:
            default:
                throw new InterruptedIOException("Archive extraction aborted.");
        }
    }

    @NonNull
    private static String findNextBestEntryName(@NonNull Path destination, @NonNull String entryName) throws IOException {
        String parentName = Paths.removeLastPathSegment(entryName);
        String filename = Paths.getLastPathSegment(entryName);
        Path parent = parentName.isEmpty() ? destination : destination.createDirectoriesIfRequired(parentName);
        String prefix = Paths.trimPathExtension(filename);
        String extension = Paths.getPathExtension(filename);
        String nextName = findNextBestDisplayName(parent, prefix, extension);
        return parentName.isEmpty() ? nextName : parentName + Paths.PATH_SEPARATOR + nextName;
    }

    @NonNull
    static String normalizeZipEntryName(@NonNull String entryName) throws IOException {
        String normalizedName = entryName.replace('\\', Paths.PATH_SEPARATOR_CHAR);
        if (normalizedName.startsWith(Paths.PATH_SEPARATOR) || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe archive entry path: " + entryName);
        }
        normalizedName = Paths.normalize(normalizedName);
        if (normalizedName == null
                || normalizedName.equals("..")
                || normalizedName.startsWith("../")
                || normalizedName.endsWith("/..")
                || normalizedName.contains("/../")) {
            throw new IOException("Unsafe archive entry path: " + entryName);
        }
        return normalizedName;
    }

    @NonNull
    private static String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix,
                                                  @Nullable String extension) {
        if (extension == null || extension.isEmpty()) {
            extension = "";
        } else {
            extension = "." + extension;
        }
        String displayName = prefix + extension;
        int i = 1;
        while (basePath.hasFile(displayName)) {
            if (i > MAX_NAME_ATTEMPTS) {
                // Bound the stat loop: a destination already holding a long run
                // of "name (1)..name (N)" entries (an attacker-crafted archive
                // extracting many KEEP_BOTH collisions can amplify this) would
                // otherwise issue an unbounded number of hasFile() IPC calls on
                // the worker thread. Fall back to a timestamp-suffixed name that
                // is effectively unique so extraction always makes progress.
                displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix,
                        System.currentTimeMillis(), extension);
                break;
            }
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, extension);
            ++i;
        }
        return displayName;
    }

    private static int countZipEntries(@NonNull Path archive) throws IOException {
        int count = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(archive.openInputStream()))) {
            while (zipInputStream.getNextEntry() != null) {
                throwIfInterrupted();
                ++count;
                zipInputStream.closeEntry();
            }
        }
        return count;
    }

    private static void throwIfInterrupted() throws InterruptedIOException {
        if (ThreadUtils.isInterrupted()) {
            throw new InterruptedIOException();
        }
    }

    interface ProgressCallback {
        void onProgress(@NonNull String label, int done, int total);
    }

    interface ConflictResolver {
        @NonNull
        ConflictAction onConflict(@NonNull String entryName) throws IOException;
    }

    enum ConflictAction {
        REPLACE,
        KEEP_BOTH,
        SKIP,
        ABORT
    }

    private static class ArchiveItem {
        @NonNull
        final Path path;
        @NonNull
        final String entryName;

        ArchiveItem(@NonNull Path path, @NonNull String entryName) {
            this.path = path;
            this.entryName = entryName;
        }
    }
}
