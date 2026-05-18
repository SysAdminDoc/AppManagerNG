// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.db.entity.Backup;

public class BackupRetentionPolicyTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long DAY = BackupRetentionPolicy.DAY_MILLIS;

    private static Backup backup(String pkg, int userId, String backupName, long ts) {
        Backup b = new Backup();
        b.packageName = pkg;
        b.userId = userId;
        b.backupName = backupName;
        b.backupTime = ts;
        b.relativeDir = pkg + "/" + ts;
        return b;
    }

    @Test
    public void zeroPolicySelectsNothing() {
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "default", NOW - DAY),
                backup("com.foo", 0, "default", NOW - 30 * DAY));
        assertTrue(BackupRetentionPolicy.selectStaleBackups(backups, 0, 0, NOW).isEmpty());
    }

    @Test
    public void emptyListIsAlwaysEmpty() {
        assertTrue(BackupRetentionPolicy.selectStaleBackups(
                new ArrayList<>(), 5, 30, NOW).isEmpty());
    }

    @Test
    public void countCapPrunesOldestPerBucket() {
        // Five daily backups for the same package; keep last 3 → oldest 2 prune.
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "auto", NOW - 4 * DAY),
                backup("com.foo", 0, "auto", NOW - 3 * DAY),
                backup("com.foo", 0, "auto", NOW - 2 * DAY),
                backup("com.foo", 0, "auto", NOW - DAY),
                backup("com.foo", 0, "auto", NOW));
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 3, 0, NOW);
        assertEquals(2, stale.size());
        Set<Long> staleTimes = new HashSet<>();
        for (Backup s : stale) staleTimes.add(s.backupTime);
        assertTrue(staleTimes.contains(NOW - 4 * DAY));
        assertTrue(staleTimes.contains(NOW - 3 * DAY));
    }

    @Test
    public void ageCapPrunesAnythingPastCutoffEvenIfCountAllows() {
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "auto", NOW - 31 * DAY),  // > 30d → stale
                backup("com.foo", 0, "auto", NOW - 10 * DAY),
                backup("com.foo", 0, "auto", NOW));
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 0, 30, NOW);
        assertEquals(1, stale.size());
        assertEquals(NOW - 31 * DAY, stale.get(0).backupTime);
    }

    @Test
    public void countAndAgeApplyTogetherWithUnion() {
        // Five daily backups, count=2 + age=3d. Both caps apply.
        // Count alone would drop the three oldest. Age alone would drop the
        // two oldest (>3d). The union is the three oldest.
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "auto", NOW - 4 * DAY),  // age stale
                backup("com.foo", 0, "auto", NOW - 3 * DAY - 1),  // age stale
                backup("com.foo", 0, "auto", NOW - 2 * DAY),  // count stale only
                backup("com.foo", 0, "auto", NOW - DAY),
                backup("com.foo", 0, "auto", NOW));
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 2, 3, NOW);
        assertEquals(3, stale.size());
    }

    @Test
    public void groupsByPackageUserAndBackupName() {
        // Each (package, user, name) bucket has its own count cap. With cap=1
        // and three buckets, three rows survive (one each).
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "auto", NOW - 2 * DAY),
                backup("com.foo", 0, "auto", NOW),
                backup("com.foo", 0, "named", NOW - 2 * DAY),  // different bucket via name
                backup("com.foo", 0, "named", NOW),
                backup("com.foo", 10, "auto", NOW - 2 * DAY),  // different user
                backup("com.foo", 10, "auto", NOW));
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 1, 0, NOW);
        // Three buckets, oldest pruned from each.
        assertEquals(3, stale.size());
    }

    @Test
    public void ageCapIgnoresBackupsWithUnknownTimestamp() {
        // backupTime == 0 (legacy / unknown) must NOT be considered "infinitely old".
        List<Backup> backups = Arrays.asList(
                backup("com.foo", 0, "auto", 0L),
                backup("com.foo", 0, "auto", NOW));
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 0, 7, NOW);
        // Only the unknown-timestamp row could conceivably be marked stale;
        // policy explicitly skips when backupTime <= 0.
        assertEquals(0, stale.size());
    }

    @Test
    public void nullEntriesAndNullPackageNamesAreSkipped() {
        List<Backup> backups = new ArrayList<>();
        backups.add(null);
        Backup unnamed = new Backup();
        unnamed.packageName = null;
        backups.add(unnamed);
        backups.add(backup("com.foo", 0, "auto", NOW - DAY));
        backups.add(backup("com.foo", 0, "auto", NOW));
        // Cap=1 leaves the bucket's oldest stale; null entries can't be grouped.
        List<Backup> stale = BackupRetentionPolicy.selectStaleBackups(backups, 1, 0, NOW);
        assertEquals(1, stale.size());
        assertEquals(NOW - DAY, stale.get(0).backupTime);
    }
}
