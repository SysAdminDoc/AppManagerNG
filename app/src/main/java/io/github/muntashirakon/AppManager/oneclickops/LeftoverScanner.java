// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Detects orphan per-package storage directories left behind by uninstalled
 * apps (T19-B).
 *
 * <p>On Android, an app's external storage payload lives under one of three
 * canonical paths:
 * <ul>
 *   <li>{@code <ext>/Android/data/&lt;package&gt;}</li>
 *   <li>{@code <ext>/Android/obb/&lt;package&gt;}</li>
 *   <li>{@code <ext>/Android/media/&lt;package&gt;}</li>
 * </ul>
 *
 * <p>The Android framework only cleans these up on uninstall when the
 * {@code KEEP_DATA} flag is NOT set (the default) and when the install path
 * cooperates. Sideloaded APKs from older installers, OEM cleanup edge cases,
 * and the persistent-by-design {@code KEEP_DATA} flag all routinely leave
 * orphan directories behind. This scanner walks the three roots and reports
 * every child directory whose name (treated as a package name) is not in the
 * installed-package set.
 *
 * <p>The scanner is split as a pure-function selector ({@link #selectOrphans})
 * plus the I/O wrapper ({@link #scan}) so the bucketing rules stay JVM-unit-
 * testable. The {@code KIND_*} taxonomy is propagated through both layers so
 * UI / op-history downstream can render per-bucket counts without re-walking.
 *
 * <p>The {@code /data/data/<pkg>} stub fallback is surfaced through the
 * dedicated entry point {@link #scanInternalDataStubs} so callers can route
 * the listing through the privileged runner (root / Shizuku) before
 * deciding to delete. UI wiring, recursive size reporting, and the
 * deletion path with op-history capture remain on the T19-B roadmap row.
 */
public final class LeftoverScanner {

    public static final int KIND_DATA = 0;
    public static final int KIND_OBB = 1;
    public static final int KIND_MEDIA = 2;
    /**
     * Root-accessible {@code /data/data/<pkg>} stub left behind after an
     * uninstall. Surfaced as a separate bucket because (a) reading
     * {@code /data/data} requires root, so a UI must gate the
     * privileged-runner call, and (b) deleting an internal stub is a
     * higher-stakes action than removing an external-storage orphan.
     */
    public static final int KIND_INTERNAL_STUB = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({KIND_DATA, KIND_OBB, KIND_MEDIA, KIND_INTERNAL_STUB})
    public @interface LeftoverKind {
    }

    /** A single orphan directory under one of the three canonical roots. */
    public static final class Leftover {
        @NonNull
        public final File path;
        @NonNull
        public final String packageName;
        @LeftoverKind
        public final int kind;

        public Leftover(@NonNull File path, @NonNull String packageName, @LeftoverKind int kind) {
            this.path = path;
            this.packageName = packageName;
            this.kind = kind;
        }

        @NonNull
        public String kindLabel() {
            switch (kind) {
                case KIND_DATA: return "data";
                case KIND_OBB: return "obb";
                case KIND_MEDIA: return "media";
                case KIND_INTERNAL_STUB: return "internal-stub";
                default: return "unknown";
            }
        }
    }

    private LeftoverScanner() {
    }

    /**
     * Walk the three canonical roots under {@code <ext>/Android/} and return
     * every package-named child directory whose name is not present in
     * {@code installedPackages}.
     *
     * @param externalStorageRoot typically {@code Environment.getExternalStorageDirectory()};
     *                            must be the directory that <em>contains</em>
     *                            the {@code Android} subdirectory, not the
     *                            {@code Android} dir itself.
     * @param installedPackages set of currently-installed package names,
     *                          across all users the caller can enumerate.
     *                          A null entry is silently dropped.
     */
    @WorkerThread
    @NonNull
    public static List<Leftover> scan(@NonNull File externalStorageRoot,
                                      @NonNull Set<String> installedPackages) {
        if (!externalStorageRoot.isDirectory()) {
            return Collections.emptyList();
        }
        File androidRoot = new File(externalStorageRoot, "Android");
        if (!androidRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<Leftover> all = new ArrayList<>();
        all.addAll(scanRoot(new File(androidRoot, "data"), installedPackages, KIND_DATA));
        if (ThreadUtils.isInterrupted()) return all;
        all.addAll(scanRoot(new File(androidRoot, "obb"), installedPackages, KIND_OBB));
        if (ThreadUtils.isInterrupted()) return all;
        all.addAll(scanRoot(new File(androidRoot, "media"), installedPackages, KIND_MEDIA));
        return all;
    }

    /**
     * Walk a root-accessible {@code /data/data} directory and return every
     * child whose name (treated as a package name) is not in
     * {@code installedPackages}. Always returns {@code KIND_INTERNAL_STUB}.
     *
     * <p>Caller is responsible for routing the listing through the
     * privileged runner (root/Shizuku) before passing the directory listing
     * into the underlying {@link #selectOrphans} call. The {@link File}
     * objects bound to a privileged listing intentionally need to be
     * resolvable by the privileged runner that later deletes them; this
     * I/O wrapper exists only for the common case where the JVM already
     * has direct read access (e.g. when invoked from a privileged
     * AppManagerNG worker process).
     *
     * @param dataDataRoot the literal {@code /data/data} directory; usually
     *                     {@code new File("/data/data")} on real devices.
     *                     If the directory cannot be enumerated (typical
     *                     unprivileged caller) the method returns
     *                     {@link Collections#emptyList()}.
     * @param installedPackages set of currently-installed package names,
     *                          across all users the caller can enumerate.
     */
    @WorkerThread
    @NonNull
    public static List<Leftover> scanInternalDataStubs(@NonNull File dataDataRoot,
                                                       @NonNull Set<String> installedPackages) {
        return scanRoot(dataDataRoot, installedPackages, KIND_INTERNAL_STUB);
    }

    @WorkerThread
    @NonNull
    private static List<Leftover> scanRoot(@NonNull File root,
                                           @NonNull Set<String> installedPackages,
                                           @LeftoverKind int kind) {
        if (!root.isDirectory()) return Collections.emptyList();
        File[] children = root.listFiles();
        if (children == null) return Collections.emptyList();
        List<File> dirs = new ArrayList<>(children.length);
        for (File c : children) {
            if (c != null && c.isDirectory()) dirs.add(c);
        }
        return selectOrphans(dirs, installedPackages, kind);
    }

    /**
     * Pure-function selector — given a list of candidate directories and the
     * installed-package set, return the orphan entries (directories whose name
     * is a valid-looking package name AND is not in the installed set).
     *
     * <p>"Valid-looking package name" means the directory name passes a
     * conservative filter: at least one dot, no path separators, no leading
     * dot. This avoids labelling stray dotfiles or oddball OEM folders (e.g.
     * {@code .nomedia}, {@code .thumbnails}, {@code lost+found}) as orphans.
     * The same filter is the documented audit boundary - a directory that
     * does not look like a package name is treated as out-of-scope rather
     * than ambiguous-orphan, because deleting it might be destructive.
     */
    @VisibleForTesting
    @NonNull
    public static List<Leftover> selectOrphans(@NonNull List<File> directories,
                                               @NonNull Set<String> installedPackages,
                                               @LeftoverKind int kind) {
        Set<String> installedCopy = new LinkedHashSet<>();
        for (String pkg : installedPackages) {
            if (pkg != null && !pkg.isEmpty()) installedCopy.add(pkg);
        }
        List<Leftover> out = new ArrayList<>();
        for (File dir : directories) {
            if (dir == null) continue;
            String name = dir.getName();
            if (!looksLikePackageName(name)) continue;
            if (installedCopy.contains(name)) continue;
            out.add(new Leftover(dir, name, kind));
        }
        return out;
    }

    /**
     * Conservative "is this a package name" predicate. Mirrors the boundary
     * used by {@code AppManagerNG.packageName} validation: at least one
     * dot-separated segment, ASCII letters / digits / underscore, no leading
     * digit per Java identifier rules, no path separators.
     */
    @VisibleForTesting
    static boolean looksLikePackageName(@NonNull String name) {
        if (name.isEmpty() || name.length() > 200) return false;
        if (name.charAt(0) == '.') return false;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return false;
        if (name.indexOf('.') < 0) return false;
        boolean lastWasDot = true;  // forces first segment to start with a letter/underscore
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '.') {
                if (lastWasDot) return false;  // empty segment
                lastWasDot = true;
                continue;
            }
            boolean valid;
            if (lastWasDot) {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            } else {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '_';
            }
            if (!valid) return false;
            lastWasDot = false;
        }
        // Trailing dot is invalid (segment cannot be empty).
        return !lastWasDot;
    }

    /**
     * Recursive sizing helper. Walks {@link Leftover#path} and returns the
     * cumulative byte count of all files, ignoring symlink cycles (relies on
     * {@link File#length()} which already follows symlinks but does not
     * recurse through them).
     *
     * <p>Errors during the walk (permission denied, vanished entries) are
     * swallowed and treated as zero-byte contributions so a single problem
     * directory cannot abort the whole maintenance scan.
     */
    @WorkerThread
    public static long sizeOnDisk(@NonNull File path) {
        if (!path.exists()) return 0L;
        if (path.isFile()) return path.length();
        File[] children = path.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            if (ThreadUtils.isInterrupted()) return total;
            try {
                total += sizeOnDisk(child);
            } catch (Throwable ignored) {
                // Permission denied on a sub-tree shouldn't fail the whole scan.
            }
        }
        return total;
    }
}
