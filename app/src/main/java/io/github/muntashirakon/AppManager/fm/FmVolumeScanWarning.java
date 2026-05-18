// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class FmVolumeScanWarning {
    @VisibleForTesting
    static final long DEFAULT_SCAN_BYTES_PER_SECOND = 64L * 1024L * 1024L;

    private FmVolumeScanWarning() {
    }

    static boolean shouldWarnBeforeRecursiveSearch(@Nullable Uri uri, @Nullable String query) {
        if (uri == null || TextUtils.isEmpty(query)) {
            return false;
        }
        try {
            Path path = Paths.getStrict(uri);
            File file = path.getFile();
            return file != null && path.isDirectory()
                    && isLikelyStorageVolumeRoot(file, Environment.getExternalStorageDirectory());
        } catch (Throwable ignore) {
            return false;
        }
    }

    @NonNull
    static CharSequence buildWarningMessage(@NonNull Context context, @NonNull Uri uri) {
        File file = null;
        try {
            Path path = Paths.getStrict(uri);
            file = path.getFile();
        } catch (Throwable ignore) {
        }
        String displayPath = FmUtils.getDisplayablePath(uri);
        long totalBytes = file != null ? getVolumeTotalBytes(context, file) : -1;
        if (totalBytes > 0) {
            long minutes = estimateScanMinutes(totalBytes, DEFAULT_SCAN_BYTES_PER_SECOND);
            return context.getString(R.string.fm_volume_scan_warning_message, displayPath,
                    Formatter.formatFileSize(context, totalBytes), minutes);
        }
        return context.getString(R.string.fm_volume_scan_warning_message_unknown, displayPath);
    }

    @VisibleForTesting
    static boolean isLikelyStorageVolumeRoot(@NonNull File file, @Nullable File primaryExternalRoot) {
        String path = normalizePath(file);
        if (primaryExternalRoot != null && path.equals(normalizePath(primaryExternalRoot))) {
            return true;
        }
        if (path.matches("^/storage/[^/]+/[^/]+$")) {
            return true;
        }
        if (path.matches("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")) {
            return true;
        }
        return path.matches("^/mnt/media_rw/[^/]+$");
    }

    @VisibleForTesting
    static long estimateScanMinutes(long totalBytes, long bytesPerSecond) {
        if (totalBytes <= 0 || bytesPerSecond <= 0) {
            return 1;
        }
        long seconds = (long) Math.ceil(totalBytes / (double) bytesPerSecond);
        return Math.max(1, (long) Math.ceil(seconds / 60d));
    }

    private static long getVolumeTotalBytes(@NonNull Context context, @NonNull File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                StorageStatsManager statsManager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                if (storageManager != null && statsManager != null) {
                    UUID uuid = storageManager.getUuidForPath(file);
                    return statsManager.getTotalBytes(uuid);
                }
            } catch (IOException | RuntimeException ignore) {
            }
        }
        long totalSpace = file.getTotalSpace();
        return totalSpace > 0 ? totalSpace : -1;
    }

    @NonNull
    private static String normalizePath(@NonNull File file) {
        String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            path = file.getAbsolutePath();
        }
        path = path.replace(File.separatorChar, '/');
        if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '/') {
            path = path.substring(2);
        }
        return path;
    }
}
