// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.UserHandleHidden;
import android.os.storage.StorageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.UUID;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.Path;

/**
 * Pre-backup storage check: refuses to start a backup that would almost certainly
 * fail mid-way for lack of disk space. Distinct from the post-write checksum step
 * — this is a cheap up-front gate.
 *
 * <p>Decision logic split into a pure function {@link #classify(long, long, long)}
 * so the policy ladder (OK / WARN / INSUFFICIENT) is testable without Android.
 *
 * <p>Reference: Neo Backup v8.3.15 "verify available storage is sufficient before
 * starting a backup" ([S41]).
 */
public final class BackupStorageCheck {
    public static final String TAG = "BackupStorageCheck";

    /**
     * Multiplier applied to a package's raw on-disk size to estimate the bytes
     * required for a backup. 1.2× covers tar headers, AppManagerNG's per-file
     * metadata records, and the per-backup checksum / icon / rules / misc files.
     */
    @VisibleForTesting
    static final double BACKUP_OVERHEAD_FACTOR = 1.2;

    /**
     * If the disk has under this many bytes free after the estimated backup, the
     * runner emits a WARN-but-proceed status rather than blocking. The point is
     * to avoid leaving the user's storage at 1% full, which itself causes other
     * failure modes on Android (logging, GMS, etc.).
     */
    @VisibleForTesting
    static final long SAFETY_MARGIN_BYTES = 64L * 1024 * 1024;  // 64 MB

    /**
     * FAT32 max file size (4 GiB − 1 byte). Archives on FAT32/vfat destinations
     * whose split size exceeds this will be silently truncated.
     */
    @VisibleForTesting
    static final long FAT32_MAX_FILE_BYTES = (4L * 1024 * 1024 * 1024) - 1;

    public enum Status {
        OK,
        WARN_LOW_HEADROOM,
        WARN_MAX_FILE_SIZE,
        INSUFFICIENT
    }

    public static final class Result {
        @NonNull
        public final Status status;
        public final long estimatedBytes;
        public final long freeBytes;
        @Nullable
        public final String detail;

        Result(@NonNull Status status, long estimatedBytes, long freeBytes, @Nullable String detail) {
            this.status = status;
            this.estimatedBytes = estimatedBytes;
            this.freeBytes = freeBytes;
            this.detail = detail;
        }
    }

    private BackupStorageCheck() {
    }

    @WorkerThread
    @NonNull
    public static Result evaluate(@NonNull String packageName) {
        Context appContext = ContextUtils.getContext();
        long estimated = estimateRequiredBytes(appContext, packageName);
        long free = getFreeBytesOnBackupVolume(appContext);
        Status status = classify(estimated, free, SAFETY_MARGIN_BYTES);
        if (status == Status.OK || status == Status.WARN_LOW_HEADROOM) {
            long maxFileSize = getMaxFileSizeOnBackupVolume(appContext);
            if (maxFileSize > 0 && estimated > maxFileSize) {
                return new Result(Status.WARN_MAX_FILE_SIZE, estimated, free,
                        "Estimated backup (" + (estimated / (1024 * 1024)) + " MB) exceeds the "
                                + "destination filesystem's max file size ("
                                + (maxFileSize / (1024 * 1024)) + " MB). "
                                + "The archive may be silently truncated.");
            }
        }
        return new Result(status, estimated, free, null);
    }

    /**
     * Pure-function classification of an (estimated, free, safetyMargin) tuple.
     * Split out so the size policy ladder is testable.
     *
     * <p>Rules:
     * <ul>
     *   <li>{@code free < estimated} → {@link Status#INSUFFICIENT} (refuse to start).</li>
     *   <li>{@code free - estimated < safetyMargin} → {@link Status#WARN_LOW_HEADROOM}
     *       (allow but tell the caller to surface a warning).</li>
     *   <li>Anything below 0 or non-finite is treated as unknown → {@link Status#OK}
     *       (the check can't decide; don't gate on unreliable input).</li>
     *   <li>Otherwise → {@link Status#OK}.</li>
     * </ul>
     */
    @VisibleForTesting
    @NonNull
    public static Status classify(long estimatedBytes, long freeBytes, long safetyMarginBytes) {
        if (estimatedBytes <= 0 || freeBytes <= 0) {
            // Unknown either side; don't gate.
            return Status.OK;
        }
        if (freeBytes < estimatedBytes) {
            return Status.INSUFFICIENT;
        }
        if (freeBytes - estimatedBytes < safetyMarginBytes) {
            return Status.WARN_LOW_HEADROOM;
        }
        return Status.OK;
    }

    @WorkerThread
    @VisibleForTesting
    static long estimateRequiredBytes(@NonNull Context context, @NonNull String packageName) {
        long apkBytes = 0;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (info.publicSourceDir != null) {
                File apk = new File(info.publicSourceDir);
                if (apk.isFile()) apkBytes = apk.length();
            }
            if (info.splitPublicSourceDirs != null) {
                for (String s : info.splitPublicSourceDirs) {
                    if (s == null) continue;
                    File f = new File(s);
                    if (f.isFile()) apkBytes += f.length();
                }
            }
        } catch (PackageManager.NameNotFoundException ignore) {
            // Package is gone (backup of an uninstalled app) — return 0 so the check
            // doesn't block.
            return 0;
        }
        long dataBytes = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dataBytes = queryDataBytesViaStorageStats(context, packageName);
        }
        long total = apkBytes + dataBytes;
        return (long) Math.ceil(total * BACKUP_OVERHEAD_FACTOR);
    }

    @WorkerThread
    private static long queryDataBytesViaStorageStats(@NonNull Context context, @NonNull String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0;
        try {
            android.app.usage.StorageStatsManager ssm =
                    (android.app.usage.StorageStatsManager) context
                            .getSystemService(Context.STORAGE_STATS_SERVICE);
            if (ssm == null) return 0;
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm == null) return 0;
            UUID volumeUuid;
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
                volumeUuid = StorageManager.UUID_DEFAULT;
                if (info.storageUuid != null) {
                    volumeUuid = info.storageUuid;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return 0;
            }
            android.app.usage.StorageStats stats = ssm.queryStatsForPackage(volumeUuid, packageName,
                    Process.myUserHandle());
            return stats.getDataBytes() + stats.getCacheBytes();
        } catch (Exception t) {
            // PACKAGE_USAGE_STATS may be denied; fall back to APK-only estimate.
            Log.d(TAG, "queryStatsForPackage(" + packageName + ") not available: " + t.getMessage());
            return 0;
        }
    }

    @WorkerThread
    @VisibleForTesting
    static long getFreeBytesOnBackupVolume(@NonNull Context appContext) {
        try {
            Path baseDir = Prefs.Storage.getAppManagerDirectory();
            File f = baseDir.getFile();
            if (f == null) {
                // SAF-backed volume — fall back to internal storage as a coarse proxy
                // because Path doesn't expose a free-space query directly on this
                // version of the io abstraction.
                f = appContext.getFilesDir();
            }
            if (f == null) return -1;
            long usable = f.getUsableSpace();
            return usable > 0 ? usable : -1;
        } catch (Exception t) {
            Log.d(TAG, "Could not query free space on backup volume: " + t.getMessage());
            return -1;
        }
    }

    @WorkerThread
    @VisibleForTesting
    static long getMaxFileSizeOnBackupVolume(@NonNull Context appContext) {
        try {
            Path baseDir = Prefs.Storage.getAppManagerDirectory();
            File f = baseDir.getFile();
            if (f == null) {
                return -1;
            }
            String mountPoint = f.getAbsolutePath();
            String fsType = detectFilesystemType(mountPoint);
            if (fsType != null && (fsType.equals("vfat") || fsType.equals("fat32"))) {
                return FAT32_MAX_FILE_BYTES;
            }
        } catch (Exception t) {
            Log.d(TAG, "Could not detect filesystem type: " + t.getMessage());
        }
        return -1;
    }

    @Nullable
    @VisibleForTesting
    static String detectFilesystemType(@NonNull String path) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/mounts"));
            String line;
            String bestMatch = null;
            int bestLen = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;
                String mountPoint = parts[1];
                if (path.startsWith(mountPoint) && mountPoint.length() > bestLen) {
                    bestLen = mountPoint.length();
                    bestMatch = parts[2];
                }
            }
            reader.close();
            return bestMatch;
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static int currentUserId() {
        try {
            return UserHandleHidden.myUserId();
        } catch (Exception t) {
            return 0;
        }
    }
}
