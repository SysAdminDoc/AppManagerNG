// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.storage.StorageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Pre-install storage gate: refuses to start an install session that cannot fit, before any
 * expansion file is staged and before the session is created.
 *
 * <p>The arithmetic is deliberately split into the pure functions {@link #estimate(long[], long, int)}
 * and {@link #classify(long, long)} so the unknown/overflow/multi-user policy is testable without
 * Android. Only {@link #getAllocatableBytesForInstall(Context)} touches the platform.
 *
 * <p>Sizing model — the bytes that must be free at once are:
 * <ul>
 *   <li>every selected split, because the whole set is written into the session before commit;</li>
 *   <li>one private staging copy of the expansion files ({@link ObbInstallStager} validates them
 *       before it touches the live directory);</li>
 *   <li>one activated copy of the expansion files <em>per targeted user</em>.</li>
 * </ul>
 *
 * <p>No safety margin is added. Over-reporting the requirement would block installs that would
 * actually succeed, and the platform's own allocatable-bytes figure already accounts for the cache
 * it is willing to evict on our behalf.
 */
public final class InstallStorageCheck {
    public static final String TAG = "InstallStorageCheck";

    /** A byte count that could not be determined. */
    public static final long UNKNOWN = -1L;
    /** A byte count that does not fit in a signed 64-bit value. */
    public static final long OVERFLOW = -2L;

    public enum Status {
        /** The requirement is known and fits. */
        OK,
        /** The requirement is known and does not fit — the install must not start. */
        INSUFFICIENT,
        /** Either side of the comparison is unknown; the check cannot decide, so it does not gate. */
        UNKNOWN
    }

    public static final class Estimate {
        /** Total bytes of the selected splits, or {@link #UNKNOWN}. */
        public final long apkBytes;
        /** Bytes one activated copy of the expansion files occupies, or {@link #UNKNOWN}. */
        public final long obbBytesPerUser;
        /** Number of users the expansion files are activated for; at least {@code 1}. */
        public final int userCount;
        /** Peak bytes required, {@link #UNKNOWN}, or {@link #OVERFLOW}. */
        public final long totalBytes;

        Estimate(long apkBytes, long obbBytesPerUser, int userCount, long totalBytes) {
            this.apkBytes = apkBytes;
            this.obbBytesPerUser = obbBytesPerUser;
            this.userCount = userCount;
            this.totalBytes = totalBytes;
        }

        public boolean isUnknown() {
            return totalBytes == UNKNOWN;
        }

        public boolean isOverflow() {
            return totalBytes == OVERFLOW;
        }
    }

    public static final class Result {
        @NonNull
        public final Status status;
        @NonNull
        public final Estimate estimate;
        /** Bytes the installer may claim, or {@link #UNKNOWN}. */
        public final long freeBytes;

        Result(@NonNull Status status, @NonNull Estimate estimate, long freeBytes) {
            this.status = status;
            this.estimate = estimate;
            this.freeBytes = freeBytes;
        }

        /** {@code true} when the install must not proceed. */
        public boolean isBlocking() {
            return status == Status.INSUFFICIENT;
        }

        /** Bytes required, or {@link #UNKNOWN}/{@link #OVERFLOW}. */
        public long getRequiredBytes() {
            return estimate.totalBytes;
        }
    }

    private InstallStorageCheck() {
    }

    /**
     * @param selectedApkSizes Byte size of each selected split. A negative entry marks that split's
     *                         size as unknown, which makes the whole estimate unknown.
     * @param obbBytesPerUser  Bytes one copy of the expansion files occupies, {@code 0} when there
     *                         are none, or a negative value when the size is unknown.
     * @param userCount        Number of users the install targets. Values below {@code 1} are
     *                         clamped to {@code 1}.
     */
    @NonNull
    public static Estimate estimate(@Nullable long[] selectedApkSizes, long obbBytesPerUser, int userCount) {
        int users = Math.max(1, userCount);
        long apkBytes = 0;
        if (selectedApkSizes != null) {
            for (long size : selectedApkSizes) {
                if (size < 0) {
                    apkBytes = UNKNOWN;
                    break;
                }
                apkBytes = addChecked(apkBytes, size);
                if (apkBytes == OVERFLOW) {
                    break;
                }
            }
        }
        long obbPerUser = obbBytesPerUser < 0 ? UNKNOWN : obbBytesPerUser;
        if (apkBytes == UNKNOWN || obbPerUser == UNKNOWN) {
            return new Estimate(apkBytes, obbPerUser, users, UNKNOWN);
        }
        if (apkBytes == OVERFLOW) {
            return new Estimate(apkBytes, obbPerUser, users, OVERFLOW);
        }
        // One private staging copy plus one activated copy per targeted user.
        long obbTotal = multiplyChecked(obbPerUser, users + 1L);
        long total = obbTotal == OVERFLOW ? OVERFLOW : addChecked(apkBytes, obbTotal);
        return new Estimate(apkBytes, obbPerUser, users, total);
    }

    /**
     * Resolves the sizes of the selected splits and the bundled expansion files.
     *
     * @param signed Whether the entries will be re-signed before installation. Passing {@code false}
     *               keeps the estimate cheap: it reads the declared entry sizes instead of forcing
     *               every split through the signer, and a signature block is negligible next to the
     *               payload it is attached to.
     */
    @WorkerThread
    @NonNull
    public static Estimate estimateFor(@NonNull ApkFile apkFile, @NonNull Collection<String> selectedSplitIds,
                                       boolean signed, int userCount) {
        int selectedCount = 0;
        for (ApkFile.Entry entry : apkFile.getEntries()) {
            if (selectedSplitIds.contains(entry.id)) {
                ++selectedCount;
            }
        }
        long[] sizes = new long[selectedCount];
        int i = 0;
        for (ApkFile.Entry entry : apkFile.getEntries()) {
            if (i < sizes.length && selectedSplitIds.contains(entry.id)) {
                sizes[i++] = entry.getFileSize(signed);
            }
        }
        long obbBytes = apkFile.hasObb() ? apkFile.getObbSize() : 0;
        return estimate(sizes, obbBytes, userCount);
    }

    /**
     * @param requiredBytes Bytes needed, or {@link #UNKNOWN}/{@link #OVERFLOW}.
     * @param freeBytes     Bytes available, or a negative value when unavailable.
     */
    @NonNull
    public static Status classify(long requiredBytes, long freeBytes) {
        if (requiredBytes == OVERFLOW) {
            // A requirement that cannot even be represented certainly cannot be satisfied.
            return Status.INSUFFICIENT;
        }
        if (requiredBytes == UNKNOWN || requiredBytes < 0) {
            return Status.UNKNOWN;
        }
        if (requiredBytes == 0) {
            return Status.OK;
        }
        if (freeBytes < 0) {
            return Status.UNKNOWN;
        }
        return freeBytes < requiredBytes ? Status.INSUFFICIENT : Status.OK;
    }

    @NonNull
    public static Result check(@NonNull Estimate estimate, long freeBytes) {
        return new Result(classify(estimate.totalBytes, freeBytes), estimate, freeBytes < 0 ? UNKNOWN : freeBytes);
    }

    /**
     * Bytes the installer can expect to claim on the volume that holds installed packages, or
     * {@link #UNKNOWN}.
     *
     * <p>API 26+ asks {@link StorageManager#getAllocatableBytes(UUID)}, which includes the cache the
     * platform would evict to satisfy an allocation and is therefore the figure an install actually
     * gets. Older releases have no such query, so the documented fallback is
     * {@link File#getUsableSpace()} on the app's own data directory: it sits on the same volume as
     * {@code /data/app}, and it under-reports (it excludes evictable cache), so the fallback errs
     * toward warning rather than toward a surprise mid-install failure.
     */
    @WorkerThread
    public static long getAllocatableBytesForInstall(@NonNull Context context) {
        File target = context.getFilesDir();
        if (target == null) {
            return UNKNOWN;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    UUID uuid = sm.getUuidForPath(target);
                    long allocatable = sm.getAllocatableBytes(uuid);
                    if (allocatable >= 0) {
                        return allocatable;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not query allocatable bytes; falling back to usable space.", e);
            }
        }
        try {
            long usable = target.getUsableSpace();
            return usable > 0 ? usable : UNKNOWN;
        } catch (Exception e) {
            Log.w(TAG, "Could not query usable space.", e);
            return UNKNOWN;
        }
    }

    /** Whether the platform can show the storage manager on request. */
    public static boolean canOfferManageStorage() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
    }

    /**
     * Builds the intent that sends the user to the storage manager, hinting at how many bytes the
     * install still needs, or {@code null} when the platform has no such screen.
     */
    @Nullable
    public static Intent buildManageStorageIntent(@NonNull Context context, long requiredBytes) {
        if (!canOfferManageStorage()) {
            return null;
        }
        Intent intent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requiredBytes > 0) {
            try {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                File filesDir = context.getFilesDir();
                if (sm != null && filesDir != null) {
                    intent.putExtra(StorageManager.EXTRA_UUID, sm.getUuidForPath(filesDir));
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not attach a storage UUID to the manage-storage request.", e);
            }
            intent.putExtra(StorageManager.EXTRA_REQUESTED_BYTES, requiredBytes);
        }
        return intent;
    }

    @VisibleForTesting
    static long addChecked(long a, long b) {
        long sum = a + b;
        // Overflow iff the operands share a sign that the result does not.
        return ((a ^ sum) & (b ^ sum)) < 0 ? OVERFLOW : sum;
    }

    @VisibleForTesting
    static long multiplyChecked(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        long product = a * b;
        return product / b != a ? OVERFLOW : product;
    }
}
