// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
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
                if (item != null && item.delete()) {
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
}
