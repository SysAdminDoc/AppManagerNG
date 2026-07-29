// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
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

import io.github.muntashirakon.AppManager.utils.ArchiveExtractionGuard;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class FmArchiveUtils {
    static final String ZIP_EXTENSION = "zip";
    /** Upper bound on numbered-collision probing in {@link #findNextBestDisplayName}. */
    private static final int MAX_NAME_ATTEMPTS = 10_000;
    @VisibleForTesting
    static final int MAX_ZIP_ENTRIES = ArchiveExtractionGuard.DEFAULT_MAX_ENTRIES;

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
        ArchiveExtractionGuard bombGuard = new ArchiveExtractionGuard(archive.length());
        // Name validation alone is not containment: the destination itself can be a symlink, or be
        // replaced by one after the archive was opened. Resolve the root once and re-check every
        // file we are about to write against it, so a swapped-in link cannot redirect the write.
        String realDestPath = destination.getRealFilePath();
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(archive.openInputStream()))) {
            ZipEntry zipEntry;
            int done = 0;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                throwIfInterrupted();
                bombGuard.onNewEntry();
                String entryName = normalizeZipEntryName(zipEntry.getName());
                assertNoSymlinkComponents(destination, entryName);
                if (zipEntry.isDirectory()) {
                    Path directory = destination.createDirectoriesIfRequired(entryName);
                    assertContained(directory, realDestPath, entryName);
                } else {
                    String outputName = resolveOutputName(destination, entryName, conflictResolver);
                    if (outputName != null) {
                        Path outputFile = destination.createNewArbitraryFile(outputName, null);
                        try {
                            assertContained(outputFile, realDestPath, entryName);
                        } catch (IOException e) {
                            outputFile.delete();
                            throw e;
                        }
                        try (OutputStream outputStream = new BufferedOutputStream(outputFile.openOutputStream())) {
                            bombGuard.copy(zipInputStream, outputStream);
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
    /**
     * Rejects an entry whose path traverses a symbolic link that already exists inside the
     * destination. A perfectly innocent-looking entry name can otherwise be redirected anywhere by
     * a link planted beforehand — the classic validate-then-write escape that name normalisation
     * cannot see.
     */
    private static void assertNoSymlinkComponents(@NonNull Path destination, @NonNull String entryName)
            throws IOException {
        Path current = destination;
        int from = 0;
        int slash;
        while ((slash = entryName.indexOf(Paths.PATH_SEPARATOR_CHAR, from)) >= 0) {
            String component = entryName.substring(from, slash);
            from = slash + 1;
            if (component.isEmpty()) {
                continue;
            }
            Path child = current.findFileOrNull(component);
            if (child == null) {
                // Nothing exists along the rest of the path, so there is nothing to follow.
                return;
            }
            if (child.isSymbolicLink()) {
                throw new IOException("Unsafe archive entry path: " + entryName
                        + " traverses the symbolic link " + component);
            }
            current = child;
        }
    }

    /**
     * Rejects a target whose resolved location is not under {@code realDestPath}. This is the
     * check that survives a destination that is (or becomes) a symlink, which name normalisation
     * alone cannot see.
     */
    private static void assertContained(@NonNull Path target, @Nullable String realDestPath,
                                        @NonNull String entryName) throws IOException {
        if (realDestPath == null) {
            // The destination has no real filesystem path (SAF/virtual); entry-name normalisation
            // is the containment guarantee there, and it already ran.
            return;
        }
        String realTargetPath = target.getRealFilePath();
        if (realTargetPath == null) {
            return;
        }
        // getRealFilePath() canonicalises through the platform, so accept either separator.
        if (!realTargetPath.equals(realDestPath)
                && !realTargetPath.startsWith(realDestPath + Paths.PATH_SEPARATOR_CHAR)
                && !realTargetPath.startsWith(realDestPath + File.separatorChar)) {
            throw new IOException("Unsafe archive entry path: " + entryName
                    + " resolved outside " + realDestPath);
        }
    }

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
                assertReasonableZipEntryCount(count);
                zipInputStream.closeEntry();
            }
        }
        return count;
    }

    @VisibleForTesting
    static void assertReasonableZipEntryCount(int count) throws IOException {
        if (count > MAX_ZIP_ENTRIES) {
            throw new IOException("Archive bomb detected: more than " + MAX_ZIP_ENTRIES
                    + " entries. Aborting extraction.");
        }
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
