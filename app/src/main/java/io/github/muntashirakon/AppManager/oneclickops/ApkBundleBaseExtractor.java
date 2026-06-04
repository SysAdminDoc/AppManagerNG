// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts the base install identity APK from split/bundle archives so the
 * duplicate finder can feed it through {@code PackageManager.getPackageArchiveInfo}.
 *
 * <p>Bundle containers keep the install metadata in a real APK entry. APKS and
 * APKM normally call it {@code base.apk}; XAPK often stores a root
 * {@code <package>.apk}. Config and split entries are deliberately ignored
 * because they do not carry a complete package identity by themselves.
 */
public final class ApkBundleBaseExtractor {
    private ApkBundleBaseExtractor() {
    }

    @WorkerThread
    @Nullable
    public static File extractBaseApk(@NonNull File bundleFile, @NonNull File cacheDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(bundleFile)) {
            String entryName = selectBaseApkEntryName(collectEntryNames(zipFile));
            if (entryName == null) {
                return null;
            }
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory() || entry.getSize() == 0L) {
                return null;
            }
            File extractDir = new File(cacheDir, "oneclickops-base-apks");
            if (!extractDir.exists() && !extractDir.mkdirs()) {
                throw new IOException("Could not create " + extractDir);
            }
            File extracted = File.createTempFile("base-", ".apk", extractDir);
            try (InputStream in = zipFile.getInputStream(entry);
                 OutputStream out = new FileOutputStream(extracted)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException | RuntimeException | Error th) {
                //noinspection ResultOfMethodCallIgnored
                extracted.delete();
                throw th;
            }
            if (extracted.length() == 0L) {
                //noinspection ResultOfMethodCallIgnored
                extracted.delete();
                return null;
            }
            return extracted;
        }
    }

    @VisibleForTesting
    @Nullable
    static String selectBaseApkEntryName(@NonNull Set<String> entryNames) {
        String nestedBase = null;
        String fallback = null;
        for (String entryName : entryNames) {
            if (entryName == null || isUnsafeEntryName(entryName)) {
                continue;
            }
            String normalized = entryName.replace('\\', '/');
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".apk")) {
                continue;
            }
            int slash = lower.lastIndexOf('/');
            String leaf = slash >= 0 ? lower.substring(slash + 1) : lower;
            if ("base.apk".equals(leaf)) {
                if (slash < 0) {
                    return entryName;
                }
                if (nestedBase == null) {
                    nestedBase = entryName;
                }
                continue;
            }
            if (isSplitApkLeaf(leaf)) {
                continue;
            }
            if (fallback == null) {
                fallback = entryName;
            }
        }
        return nestedBase != null ? nestedBase : fallback;
    }

    @NonNull
    private static Set<String> collectEntryNames(@NonNull ZipFile zipFile) {
        Set<String> entryNames = new TreeSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name != null && !name.isEmpty()) {
                entryNames.add(name);
            }
        }
        return entryNames;
    }

    private static boolean isSplitApkLeaf(@NonNull String leaf) {
        return leaf.startsWith("split_") || leaf.startsWith("config.");
    }

    private static boolean isUnsafeEntryName(@NonNull String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        String[] parts = normalized.split("/");
        for (String part : parts) {
            if (".".equals(part) || "..".equals(part)) {
                return true;
            }
        }
        return false;
    }
}
