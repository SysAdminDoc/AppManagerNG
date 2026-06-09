// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Trims a package's backup set down to the user-configured retention policy.
 *
 * <p>Two independent dimensions, both applied:
 * <ul>
 *   <li><b>Count cap</b> — keep at most N most-recent backups per package.
 *       {@code 0} means unlimited.</li>
 *   <li><b>Age cap</b> — drop backups older than M days regardless of count.
 *       {@code 0} means unlimited.</li>
 * </ul>
 *
 * <p>The pure-function selector {@link #selectStaleBackups} is split out so the
 * policy can be unit-tested without Room / SAF — the on-disk pruner just maps
 * the selected entries to {@code BackupItems.findBackupItem(...).delete()}.
 *
 * <p>Reference: Neo Backup v8.3.x retention policy model ([S41]).
 */
public final class BackupRetentionPolicy {
    public static final String TAG = "BackupRetentionPolicy";

    public static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private BackupRetentionPolicy() {
    }

    /**
     * Prune the active retention policy across <em>every</em> known backup,
     * grouped by package and user. Safe to call from any worker thread.
     *
     * @return number of backups actually deleted from disk
     */
    @WorkerThread
    public static int pruneAll() {
        int maxCount = Prefs.BackupRestore.getMaxBackupsPerApp();
        int maxAgeDays = Prefs.BackupRestore.getMaxBackupAgeDays();
        if (maxCount <= 0 && maxAgeDays <= 0) return 0;
        try {
            List<Backup> all = AppsDb.getInstance().backupDao().getAll();
            return pruneFromList(all, maxCount, maxAgeDays, System.currentTimeMillis());
        } catch (Throwable t) {
            Log.w(TAG, "Backup retention pruneAll failed", t);
            return 0;
        }
    }

    /**
     * One-shot prune using an explicit policy rather than the persisted prefs.
     * Used by the 1-Click Ops "Delete old backups" entry so a user can clear out
     * stale backups without first changing their permanent retention setting.
     *
     * @return number of backups actually deleted from disk
     */
    @WorkerThread
    public static int pruneWithPolicy(int maxCount, int maxAgeDays) {
        if (maxCount <= 0 && maxAgeDays <= 0) return 0;
        try {
            List<Backup> all = AppsDb.getInstance().backupDao().getAll();
            return pruneFromList(all, maxCount, maxAgeDays, System.currentTimeMillis());
        } catch (Throwable t) {
            Log.w(TAG, "Backup retention pruneWithPolicy failed", t);
            return 0;
        }
    }

    /**
     * Prune backups for a single package — invoked after a successful backup so
     * the policy is applied incrementally rather than only on app launch.
     */
    @WorkerThread
    public static int pruneForPackage(@NonNull String packageName) {
        int maxCount = Prefs.BackupRestore.getMaxBackupsPerApp();
        int maxAgeDays = Prefs.BackupRestore.getMaxBackupAgeDays();
        if (maxCount <= 0 && maxAgeDays <= 0) return 0;
        try {
            List<Backup> rows = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName);
            return pruneFromList(rows, maxCount, maxAgeDays, System.currentTimeMillis());
        } catch (Throwable t) {
            Log.w(TAG, "Backup retention pruneForPackage(" + packageName + ") failed", t);
            return 0;
        }
    }

    @WorkerThread
    private static int pruneFromList(@NonNull List<Backup> backups, int maxCount,
                                     int maxAgeDays, long nowMillis) {
        List<Backup> stale = selectStaleBackups(backups, maxCount, maxAgeDays, nowMillis);
        int deleted = 0;
        for (Backup b : stale) {
            try {
                BackupItems.BackupItem item = b.getItem();
                if (item == null || item.isFrozen()) {
                    // Frozen backups are explicitly protected from deletion by the user;
                    // automated retention must skip them, mirroring manual deleteBackup().
                    continue;
                }
                if (item.delete()) {
                    ++deleted;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to delete pruned backup " + b.relativeDir, t);
            }
        }
        return deleted;
    }

    /**
     * Pure-function selector — given the full backup list, decide which rows
     * the retention policy would remove. Grouped by
     * {@code (packageName, userId, backupName)} so user-named "Keep" backups
     * are tracked independently from per-app daily auto-backups, mirroring the
     * way upstream's multi-backup mode already separates them.
     */
    @VisibleForTesting
    @NonNull
    public static List<Backup> selectStaleBackups(@NonNull List<Backup> all, int maxCount,
                                                  int maxAgeDays, long nowMillis) {
        if ((maxCount <= 0 && maxAgeDays <= 0) || all.isEmpty()) {
            return Collections.emptyList();
        }
        // Group by (package, user, name) so a user-created "preinstall" snapshot is
        // pruned independently from the daily auto-backup chain.
        java.util.Map<String, List<Backup>> groups = new java.util.LinkedHashMap<>();
        for (Backup b : all) {
            if (b == null || b.packageName == null) continue;
            String name = b.backupName == null ? "" : b.backupName;
            String key = b.packageName + "\0" + b.userId + "\0" + name;
            List<Backup> bucket = groups.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(key, bucket);
            }
            bucket.add(b);
        }
        long ageCutoff = maxAgeDays > 0 ? nowMillis - (long) maxAgeDays * DAY_MILLIS : Long.MIN_VALUE;
        List<Backup> stale = new ArrayList<>();
        for (List<Backup> bucket : groups.values()) {
            // Newest first so the count cap drops the oldest entries.
            Collections.sort(bucket, NEWEST_FIRST);
            for (int i = 0; i < bucket.size(); ++i) {
                Backup b = bucket.get(i);
                boolean tooOld = maxAgeDays > 0 && b.backupTime > 0 && b.backupTime < ageCutoff;
                boolean overCount = maxCount > 0 && i >= maxCount;
                if (tooOld || overCount) {
                    stale.add(b);
                }
            }
        }
        return stale;
    }

    private static final Comparator<Backup> NEWEST_FIRST = (a, b) -> Long.compare(b.backupTime, a.backupTime);

    /**
     * Pure-function selector for the backup-duplicate-cleaner row (T19-D).
     *
     * <p>Buckets each {@link Backup} by {@code (packageName, userId, versionCode)}
     * <em>ignoring backup name</em>, then within each bucket of size &gt; 1 marks
     * every entry except the keeper as a duplicate. The keep policy decides
     * which row survives:
     * <ul>
     *   <li>{@link DuplicateKeepStrategy#NEWEST} - newest {@code backupTime}
     *       wins. Ties broken by ascending {@code relativeDir} so the result is
     *       deterministic.</li>
     *   <li>{@link DuplicateKeepStrategy#OLDEST} - oldest {@code backupTime}
     *       wins. Same tie-breaker. Useful when a user has overwritten a
     *       known-good snapshot with a partially-failed retry and wants to
     *       reclaim the retry.</li>
     * </ul>
     *
     * <p>Rows where {@code versionCode &lt;= 0} or {@code packageName} is null
     * are skipped: they represent metadata that the duplicate scan cannot
     * compare safely. Rows with {@code backupTime &lt;= 0} are still bucketed
     * but treated as oldest under {@link DuplicateKeepStrategy#NEWEST}.
     *
     * <p>Existing retention policy is not invoked here. Callers that want both
     * passes should run {@link #selectStaleBackups} on the survivors, mirroring
     * the way {@link #pruneFromList} chains the on-disk delete step.
     */
    @VisibleForTesting
    @NonNull
    public static List<Backup> selectVersionDuplicates(@NonNull List<Backup> all,
                                                       @NonNull DuplicateKeepStrategy strategy) {
        return selectVersionDuplicates(all, strategy, null);
    }

    /**
     * Size-aware overload of {@link #selectVersionDuplicates(List, DuplicateKeepStrategy)}.
     *
     * <p>Required when {@code strategy} is {@link DuplicateKeepStrategy#LARGEST}
     * or {@link DuplicateKeepStrategy#LARGEST_THEN_NEWEST}; ignored otherwise.
     * Passing {@code null} when the strategy needs size is treated as
     * "every row has unknown size" and falls back to the deterministic
     * insertion-order tie-break, so the method never throws.
     *
     * <p>A {@link BackupSizeResolver} that returns a negative number for a
     * given backup is treated as "unknown". Unknown sizes lose against any
     * known size; if every row in a bucket is unknown the deterministic
     * {@code relativeDir} tie-break wins.
     */
    @VisibleForTesting
    @NonNull
    public static List<Backup> selectVersionDuplicates(@NonNull List<Backup> all,
                                                       @NonNull DuplicateKeepStrategy strategy,
                                                       @Nullable BackupSizeResolver sizeResolver) {
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.Map<String, List<Backup>> groups = new java.util.LinkedHashMap<>();
        for (Backup b : all) {
            if (b == null || b.packageName == null || b.versionCode <= 0) continue;
            String key = b.packageName + "\0" + b.userId + "\0" + b.versionCode;
            List<Backup> bucket = groups.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(key, bucket);
            }
            bucket.add(b);
        }
        Comparator<Backup> keeperFirst = comparatorFor(strategy, sizeResolver);
        List<Backup> duplicates = new ArrayList<>();
        for (List<Backup> bucket : groups.values()) {
            if (bucket.size() < 2) continue;
            Collections.sort(bucket, keeperFirst);
            // The first entry survives; everything after is a duplicate.
            for (int i = 1; i < bucket.size(); ++i) {
                duplicates.add(bucket.get(i));
            }
        }
        return duplicates;
    }

    /**
     * Sum of {@link BackupSizeResolver#sizeOnDisk(Backup)} across the rows the
     * selector would remove. Unknown sizes (negative resolver output) are
     * counted as zero. Callers use this to surface a "Reclaim X bytes" hint
     * in the duplicate-cleaner UI before committing.
     */
    public static long reclaimableBytes(@NonNull List<Backup> duplicates,
                                        @NonNull BackupSizeResolver sizeResolver) {
        long total = 0L;
        for (Backup b : duplicates) {
            long size = sizeResolver.sizeOnDisk(b);
            if (size > 0L) total += size;
        }
        return total;
    }

    @NonNull
    public static BackupSizeResolver backupItemSizeResolver() {
        return BackupRetentionPolicy::resolveBackupSize;
    }

    @VisibleForTesting
    static long resolveBackupSize(@NonNull Backup backup) {
        try {
            BackupItems.BackupItem item = backup.getItem();
            return item != null ? item.getTotalSize() : -1L;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to resolve backup size " + backup.relativeDir, t);
            return -1L;
        }
    }

    @NonNull
    private static Comparator<Backup> comparatorFor(@NonNull DuplicateKeepStrategy strategy,
                                                    @Nullable BackupSizeResolver sizeResolver) {
        switch (strategy) {
            case OLDEST:
                return OLDEST_FIRST_DETERMINISTIC;
            case LARGEST:
                return largestFirstThen(sizeResolver, /* breakTieWithNewest */ false);
            case LARGEST_THEN_NEWEST:
                return largestFirstThen(sizeResolver, /* breakTieWithNewest */ true);
            case NEWEST:
            default:
                return NEWEST_FIRST_DETERMINISTIC;
        }
    }

    @NonNull
    private static Comparator<Backup> largestFirstThen(@Nullable BackupSizeResolver sizeResolver,
                                                       boolean breakTieWithNewest) {
        return (a, b) -> {
            long sa = sizeResolver != null ? sizeResolver.sizeOnDisk(a) : -1L;
            long sb = sizeResolver != null ? sizeResolver.sizeOnDisk(b) : -1L;
            // Unknown sizes (negative) sort last so a known size always wins.
            boolean aKnown = sa >= 0L;
            boolean bKnown = sb >= 0L;
            if (aKnown != bKnown) return aKnown ? -1 : 1;
            if (aKnown) {
                int bySize = Long.compare(sb, sa);
                if (bySize != 0) return bySize;
            }
            if (breakTieWithNewest) {
                int byTime = Long.compare(b.backupTime, a.backupTime);
                if (byTime != 0) return byTime;
            }
            return safeCompare(a.relativeDir, b.relativeDir);
        };
    }

    /**
     * Prune cross-name version duplicates from disk. Mirrors the
     * {@link #pruneFromList} shape so the on-disk delete path stays uniform.
     *
     * @return number of duplicate backups actually deleted from disk
     */
    @WorkerThread
    public static int pruneVersionDuplicates(@NonNull DuplicateKeepStrategy strategy) {
        BackupSizeResolver sizeResolver = strategy == DuplicateKeepStrategy.LARGEST
                || strategy == DuplicateKeepStrategy.LARGEST_THEN_NEWEST
                ? backupItemSizeResolver()
                : null;
        return pruneVersionDuplicates(strategy, sizeResolver);
    }

    /**
     * Size-aware overload of {@link #pruneVersionDuplicates(DuplicateKeepStrategy)}.
     * The resolver is only consulted for {@link DuplicateKeepStrategy#LARGEST}
     * and {@link DuplicateKeepStrategy#LARGEST_THEN_NEWEST}; pass {@code null}
     * for the other strategies (or to opt into the deterministic fallback when
     * size is genuinely unavailable).
     */
    @WorkerThread
    public static int pruneVersionDuplicates(@NonNull DuplicateKeepStrategy strategy,
                                             @Nullable BackupSizeResolver sizeResolver) {
        try {
            List<Backup> all = AppsDb.getInstance().backupDao().getAll();
            List<Backup> duplicates = selectVersionDuplicates(all, strategy, sizeResolver);
            int deleted = 0;
            for (Backup b : duplicates) {
                try {
                    BackupItems.BackupItem item = b.getItem();
                    if (item == null || item.isFrozen()) {
                        // Frozen backups are user-protected; never auto-delete them.
                        continue;
                    }
                    if (item.delete()) {
                        ++deleted;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to delete duplicate backup " + b.relativeDir, t);
                }
            }
            return deleted;
        } catch (Throwable t) {
            Log.w(TAG, "Backup duplicate prune failed", t);
            return 0;
        }
    }

    public enum DuplicateKeepStrategy {
        NEWEST,
        OLDEST,
        /** Largest on-disk size wins. Ties broken by ascending {@code relativeDir}. */
        LARGEST,
        /**
         * Largest on-disk size wins; size ties broken by newest {@code backupTime},
         * then by ascending {@code relativeDir}. Recommended default for the
         * UI duplicate-cleaner when both size and freshness matter.
         */
        LARGEST_THEN_NEWEST
    }

    /**
     * Resolves the on-disk byte count of a backup. Implementations typically
     * call {@code BackupItems.findBackupItem(relativeDir).getTotalSize()} or
     * walk the backup file tree; the data-layer keeps the contract narrow so
     * the selector remains JVM-unit-testable. Return a negative value for
     * "unknown" so the comparator can demote unknown rows below known ones.
     */
    public interface BackupSizeResolver {
        long sizeOnDisk(@NonNull Backup backup);
    }

    private static final Comparator<Backup> NEWEST_FIRST_DETERMINISTIC = (a, b) -> {
        int byTime = Long.compare(b.backupTime, a.backupTime);
        return byTime != 0 ? byTime : safeCompare(a.relativeDir, b.relativeDir);
    };

    private static final Comparator<Backup> OLDEST_FIRST_DETERMINISTIC = (a, b) -> {
        int byTime = Long.compare(a.backupTime, b.backupTime);
        return byTime != 0 ? byTime : safeCompare(a.relativeDir, b.relativeDir);
    };

    private static int safeCompare(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
}
