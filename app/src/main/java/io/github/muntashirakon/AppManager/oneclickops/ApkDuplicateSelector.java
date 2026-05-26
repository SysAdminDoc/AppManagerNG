// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function selector for the APK duplicate finder (T19-C).
 *
 * <p>Given a flat list of {@link Candidate} records that describe parsed
 * {@code .apk} / {@code .apks} / {@code .apkm} / {@code .xapk} payloads,
 * this bucket-and-pick layer returns every redundant copy as a "drop" list,
 * partitioned by package identity:
 * <ul>
 *   <li>Buckets are keyed by
 *       {@code (packageName, versionCode, signingCertSha256)}. Two payloads
 *       with the same triple are byte-equivalent installs from Android's
 *       point of view; the signing-cert dimension stops a sideloaded fork
 *       from masquerading as the same install identity.</li>
 *   <li>The keeper per bucket is chosen by {@link KeepStrategy}.
 *       {@code LARGEST} picks the largest on-disk file (split-APK bundles
 *       beat the bare APK); {@code SMALLEST} picks the smallest (useful when
 *       Downloads has both a stub install and a backed-up full archive).
 *       Ties on size are broken by ascending absolute path so the selector
 *       stays deterministic.</li>
 * </ul>
 *
 * <p>This is the data layer. APK enumeration on disk, metadata extraction
 * (which has to go through {@code PackageManager.getPackageArchiveInfo} for
 * {@code .apk} and a custom parser for {@code .apkm} / {@code .xapk}), op
 * history capture, and the One-Click Ops UI are tracked as the follow-up on
 * the T19-C row.
 */
public final class ApkDuplicateSelector {

    /** A parsed candidate APK payload. */
    public static final class Candidate {
        @NonNull
        public final File path;
        @NonNull
        public final String packageName;
        public final long versionCode;
        /** Hex SHA-256 of the signing certificate, or {@code null} if unknown. */
        @Nullable
        public final String signingCertSha256;
        /** On-disk size in bytes, or {@code -1} if unknown. */
        public final long sizeBytes;

        public Candidate(@NonNull File path, @NonNull String packageName, long versionCode,
                         @Nullable String signingCertSha256, long sizeBytes) {
            this.path = path;
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.signingCertSha256 = signingCertSha256;
            this.sizeBytes = sizeBytes;
        }
    }

    /** A group of duplicates and the keeper picked for that group. */
    public static final class DuplicateGroup {
        @NonNull
        public final String packageName;
        public final long versionCode;
        @Nullable
        public final String signingCertSha256;
        @NonNull
        public final Candidate keeper;
        @NonNull
        public final List<Candidate> drop;

        DuplicateGroup(@NonNull String packageName, long versionCode,
                       @Nullable String signingCertSha256,
                       @NonNull Candidate keeper, @NonNull List<Candidate> drop) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.signingCertSha256 = signingCertSha256;
            this.keeper = keeper;
            this.drop = drop;
        }
    }

    public enum KeepStrategy {
        LARGEST,
        SMALLEST
    }

    private ApkDuplicateSelector() {
    }

    /**
     * Bucket {@code candidates} by install identity and return every bucket
     * with more than one entry as a {@link DuplicateGroup}.
     *
     * <p>Candidates with an empty {@code packageName} or non-positive
     * {@code versionCode} are skipped because the duplicate decision cannot
     * be made safely.
     */
    @NonNull
    public static List<DuplicateGroup> selectDuplicates(@NonNull List<Candidate> candidates,
                                                        @NonNull KeepStrategy strategy) {
        if (candidates.isEmpty()) return Collections.emptyList();
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            if (c == null || c.packageName.isEmpty() || c.versionCode <= 0) continue;
            String key = c.packageName + "\0" + c.versionCode + "\0"
                    + (c.signingCertSha256 == null ? "" : c.signingCertSha256);
            List<Candidate> bucket = groups.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(key, bucket);
            }
            bucket.add(c);
        }
        List<DuplicateGroup> out = new ArrayList<>();
        for (List<Candidate> bucket : groups.values()) {
            if (bucket.size() < 2) continue;
            Candidate keeper = pickKeeper(bucket, strategy);
            List<Candidate> drop = new ArrayList<>(bucket.size() - 1);
            for (Candidate c : bucket) {
                if (c != keeper) drop.add(c);
            }
            out.add(new DuplicateGroup(
                    keeper.packageName, keeper.versionCode, keeper.signingCertSha256,
                    keeper, drop));
        }
        return out;
    }

    @NonNull
    private static Candidate pickKeeper(@NonNull List<Candidate> bucket,
                                        @NonNull KeepStrategy strategy) {
        Candidate keeper = bucket.get(0);
        for (int i = 1; i < bucket.size(); ++i) {
            Candidate c = bucket.get(i);
            if (beats(c, keeper, strategy)) keeper = c;
        }
        return keeper;
    }

    private static boolean beats(@NonNull Candidate c, @NonNull Candidate keeper,
                                 @NonNull KeepStrategy strategy) {
        int sizeCompare = Long.compare(c.sizeBytes, keeper.sizeBytes);
        if (sizeCompare == 0) {
            // Stable tie-breaker on absolute path.
            return c.path.getAbsolutePath().compareTo(keeper.path.getAbsolutePath()) < 0;
        }
        switch (strategy) {
            case SMALLEST:
                return sizeCompare < 0;
            case LARGEST:
            default:
                return sizeCompare > 0;
        }
    }

    /**
     * Total bytes that would be reclaimed if every {@link DuplicateGroup#drop}
     * file were deleted. Unknown sizes (negative bytes) are treated as zero.
     */
    public static long reclaimableBytes(@NonNull List<DuplicateGroup> groups) {
        long total = 0L;
        for (DuplicateGroup g : groups) {
            for (Candidate c : g.drop) {
                if (c.sizeBytes > 0) total += c.sizeBytes;
            }
        }
        return total;
    }
}
