// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;

/**
 * Root selection for duplicate-APK scans.
 */
public final class ApkDuplicateScanRoots {
    private static final String TAG = ApkDuplicateScanRoots.class.getSimpleName();

    private ApkDuplicateScanRoots() {
    }

    @WorkerThread
    @NonNull
    public static List<File> scanDefaultRoots(@Nullable ApkFileScanner.CancellationSignal signal) {
        return scanRoots(getDefaultRoots(), signal);
    }

    @WorkerThread
    @NonNull
    static List<File> getDefaultRoots() {
        List<File> roots = new ArrayList<>(2);
        roots.add(Environment.getExternalStorageDirectory());
        File configuredAppManagerDir = getConfiguredAppManagerDirectory();
        if (configuredAppManagerDir != null) {
            roots.add(configuredAppManagerDir);
        }
        return deduplicateRoots(roots);
    }

    @WorkerThread
    @NonNull
    @VisibleForTesting
    static List<File> scanRoots(@NonNull List<File> roots,
                                @Nullable ApkFileScanner.CancellationSignal signal) {
        List<File> uniqueFiles = new ArrayList<>();
        Set<String> seenFiles = new LinkedHashSet<>();
        for (File root : deduplicateRoots(roots)) {
            if (isCancelled(signal)) {
                return uniqueFiles;
            }
            for (File apkFile : ApkFileScanner.scan(root, signal)) {
                if (isCancelled(signal)) {
                    return uniqueFiles;
                }
                String canonical = canonicalPath(apkFile);
                if (seenFiles.add(canonical)) {
                    uniqueFiles.add(apkFile);
                }
            }
        }
        return uniqueFiles;
    }

    @NonNull
    @VisibleForTesting
    static List<File> deduplicateRoots(@NonNull List<File> roots) {
        List<Root> uniqueRoots = new ArrayList<>();
        for (File root : roots) {
            if (root == null || !root.isDirectory()) {
                continue;
            }
            Root candidate = new Root(root, canonicalPath(root));
            boolean skipCandidate = false;
            for (Iterator<Root> it = uniqueRoots.iterator(); it.hasNext(); ) {
                Root existing = it.next();
                if (isSameOrChild(candidate.canonicalPath, existing.canonicalPath)) {
                    skipCandidate = true;
                    break;
                }
                if (isSameOrChild(existing.canonicalPath, candidate.canonicalPath)) {
                    it.remove();
                }
            }
            if (!skipCandidate) {
                uniqueRoots.add(candidate);
            }
        }
        List<File> out = new ArrayList<>(uniqueRoots.size());
        for (Root root : uniqueRoots) {
            out.add(root.file);
        }
        return out;
    }

    @Nullable
    private static File getConfiguredAppManagerDirectory() {
        try {
            Path path = Prefs.Storage.getAppManagerDirectory();
            return path.getFile();
        } catch (Throwable th) {
            Log.w(TAG, "Failed to resolve configured AppManager directory for APK duplicate scan.", th);
            return null;
        }
    }

    private static boolean isCancelled(@Nullable ApkFileScanner.CancellationSignal signal) {
        return (signal != null && signal.isCancelled()) || ThreadUtils.isInterrupted();
    }

    @NonNull
    private static String canonicalPath(@NonNull File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static boolean isSameOrChild(@NonNull String path, @NonNull String parent) {
        if (path.equals(parent)) {
            return true;
        }
        return path.startsWith(parent + File.separator);
    }

    private static final class Root {
        @NonNull
        final File file;
        @NonNull
        final String canonicalPath;

        Root(@NonNull File file, @NonNull String canonicalPath) {
            this.file = file;
            this.canonicalPath = canonicalPath;
        }
    }
}
