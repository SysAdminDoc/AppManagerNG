// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * File-system enumeration glue for the T19-C APK duplicate finder. Pairs
 * with {@link ApkDuplicateSelector}: this layer walks a directory tree and
 * returns every well-formed APK candidate; the selector groups + dedups.
 *
 * <p>Recognised extensions (case-insensitive): {@code .apk}, {@code .apks},
 * {@code .apkm}, {@code .xapk}. The walk is depth-first with an explicit
 * stack so we never recurse, and a {@link Set} of already-visited
 * <em>canonical</em> paths protects against symlink loops on rooted devices
 * that mount {@code /sdcard} via {@code /mnt/runtime/...}.
 *
 * <p>Rejection rules:
 * <ul>
 *   <li>Zero-byte files - either a download placeholder or a corrupted
 *       drop; we skip them.</li>
 *   <li>Partial download markers - any file whose name ends in
 *       {@code .crdownload}, {@code .part}, {@code .download},
 *       {@code .opdownload}, or starts with the in-progress
 *       {@code .com.android.chrome} hidden prefix - they would point at
 *       half-written content. The set is intentionally allow-listed; we'd
 *       rather miss a renamed APK than try to fingerprint a partial download.</li>
 *   <li>Symlink cycles - the canonical-path visited set short-circuits any
 *       second visit.</li>
 * </ul>
 *
 * <p>The scanner is JVM-clean: it works against {@link File} only, with a
 * {@link CancellationSignal} hook so a UI can abort. The signal is checked
 * once per directory and once per file emit so cancellation is bounded.
 *
 * <p>Metadata extraction (package name / version code / signing cert hash)
 * remains on the T19-C row as a follow-up; this layer just hands the
 * selector a list of file references that are <em>safe</em> to fingerprint.
 */
public final class ApkFileScanner {

    /** Default APK extension allow-list. */
    public static final Set<String> APK_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(".apk", ".apks", ".apkm", ".xapk")));

    private static final Set<String> PARTIAL_DOWNLOAD_SUFFIXES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    ".crdownload",   // Chrome
                    ".part",         // Firefox / wget / curl resumable
                    ".download",     // Safari / Edge
                    ".opdownload",   // Opera
                    ".tmp")));       // Generic in-progress

    /** Bounded recursion safety net - matches Android scoped-storage depth ceilings. */
    private static final int MAX_RECURSION_DEPTH = 32;

    /**
     * Cancellable handle the caller can keep around. Methods are thread-safe;
     * the scanner only ever reads {@link #isCancelled()}.
     */
    public static final class CancellationSignal {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private ApkFileScanner() {
    }

    /**
     * Walk {@code root} and return every file whose name ends in one of
     * {@link #APK_EXTENSIONS}, excluding zero-byte files and partial-download
     * markers. Returns {@link Collections#emptyList()} when {@code root} is
     * not a directory.
     *
     * <p>{@code signal} may be {@code null} to opt out of cancellation;
     * either way the scan also checks {@link ThreadUtils#isInterrupted()}
     * so it can be aborted by interrupting the worker thread.
     */
    @WorkerThread
    @NonNull
    public static List<File> scan(@NonNull File root, ApkFileScanner.CancellationSignal signal) {
        return scan(root, APK_EXTENSIONS, signal);
    }

    /**
     * Variant that accepts a custom extension allow-list. Useful for tests
     * that want to exercise the walker without depending on the canonical
     * APK set.
     */
    @WorkerThread
    @NonNull
    public static List<File> scan(@NonNull File root,
                                  @NonNull Set<String> extensionsLowercase,
                                  ApkFileScanner.CancellationSignal signal) {
        if (!root.isDirectory()) return Collections.emptyList();
        List<File> hits = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(root, 0));
        while (!stack.isEmpty()) {
            if (isCancelled(signal)) return hits;
            Frame top = stack.pop();
            File dir = top.dir;
            if (top.depth > MAX_RECURSION_DEPTH) continue;
            String canonical = canonicalPath(dir);
            if (canonical == null || !visited.add(canonical)) continue;
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child == null) continue;
                if (isCancelled(signal)) return hits;
                if (child.isDirectory()) {
                    stack.push(new Frame(child, top.depth + 1));
                } else if (isAcceptableApk(child, extensionsLowercase)) {
                    hits.add(child);
                }
            }
        }
        return hits;
    }

    /**
     * Pure-function file predicate. Public for testability and for callers
     * that already have a file list from elsewhere (e.g. SAF tree walk) and
     * just want the rejection rules applied uniformly.
     */
    @VisibleForTesting
    public static boolean isAcceptableApk(@NonNull File file, @NonNull Set<String> extensionsLowercase) {
        if (!file.isFile()) return false;
        String name = file.getName();
        if (name.isEmpty() || name.charAt(0) == '.') return false; // hidden / partial
        String lower = name.toLowerCase(Locale.ROOT);
        if (!hasAcceptedExtension(lower, extensionsLowercase)) return false;
        if (matchesPartialDownloadSuffix(lower)) return false;
        // Zero-byte = empty placeholder or interrupted save; partial-download by another name.
        return file.length() > 0L;
    }

    @VisibleForTesting
    static boolean hasAcceptedExtension(@NonNull String lowerName, @NonNull Set<String> extensionsLowercase) {
        for (String ext : extensionsLowercase) {
            if (ext != null && !ext.isEmpty() && lowerName.endsWith(ext)) return true;
        }
        return false;
    }

    @VisibleForTesting
    static boolean matchesPartialDownloadSuffix(@NonNull String lowerName) {
        for (String suffix : PARTIAL_DOWNLOAD_SUFFIXES) {
            if (lowerName.endsWith(suffix)) return true;
        }
        return false;
    }

    private static boolean isCancelled(ApkFileScanner.CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) return true;
        return ThreadUtils.isInterrupted();
    }

    private static String canonicalPath(@NonNull File file) {
        try {
            return file.getCanonicalPath();
        } catch (Throwable ignored) {
            return file.getAbsolutePath();
        }
    }

    private static final class Frame {
        final File dir;
        final int depth;

        Frame(File dir, int depth) {
            this.dir = dir;
            this.depth = depth;
        }
    }
}
