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

    private static Backup versionedBackup(String pkg, int userId, String backupName,
                                          long versionCode, long ts) {
        Backup b = backup(pkg, userId, backupName, ts);
        b.versionCode = versionCode;
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
    public void duplicateSelectorReturnsNothingWhenBucketsAreUnique() {
        List<Backup> backups = Arrays.asList(
                versionedBackup("com.foo", 0, "auto", 1L, NOW),
                versionedBackup("com.foo", 0, "auto", 2L, NOW),  // newer version, different bucket
                versionedBackup("com.bar", 0, "auto", 1L, NOW));
        assertTrue(BackupRetentionPolicy.selectVersionDuplicates(
                backups, BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST).isEmpty());
    }

    @Test
    public void duplicateSelectorKeepsNewestAndMarksRest() {
        // Three copies of the same (pkg, user, versionCode) under different backupNames.
        List<Backup> backups = Arrays.asList(
                versionedBackup("com.foo", 0, "preinstall", 100L, NOW - 30 * DAY),
                versionedBackup("com.foo", 0, "manual", 100L, NOW - 10 * DAY),
                versionedBackup("com.foo", 0, "auto", 100L, NOW));
        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                backups, BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST);
        assertEquals(2, dupes.size());
        Set<String> dupeNames = new HashSet<>();
        for (Backup b : dupes) dupeNames.add(b.backupName);
        assertTrue(dupeNames.contains("preinstall"));
        assertTrue(dupeNames.contains("manual"));
    }

    @Test
    public void duplicateSelectorOldestStrategyKeepsTheOldest() {
        List<Backup> backups = Arrays.asList(
                versionedBackup("com.foo", 0, "preinstall", 100L, NOW - 30 * DAY),
                versionedBackup("com.foo", 0, "manual", 100L, NOW - 10 * DAY),
                versionedBackup("com.foo", 0, "auto", 100L, NOW));
        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                backups, BackupRetentionPolicy.DuplicateKeepStrategy.OLDEST);
        assertEquals(2, dupes.size());
        Set<String> dupeNames = new HashSet<>();
        for (Backup b : dupes) dupeNames.add(b.backupName);
        // "preinstall" is oldest, so it survives; "manual" and "auto" are dupes.
        assertTrue(dupeNames.contains("manual"));
        assertTrue(dupeNames.contains("auto"));
    }

    @Test
    public void duplicateSelectorSplitsByUserId() {
        // Same package + version code but different users are NOT duplicates;
        // they represent backup of the same APK under separate user data roots.
        List<Backup> backups = Arrays.asList(
                versionedBackup("com.foo", 0, "auto", 100L, NOW),
                versionedBackup("com.foo", 10, "auto", 100L, NOW),
                versionedBackup("com.foo", 0, "manual", 100L, NOW - DAY));
        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                backups, BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST);
        // Only user 0 has two entries with the same (pkg, version); user 10 stays.
        assertEquals(1, dupes.size());
        assertEquals(0, dupes.get(0).userId);
    }

    @Test
    public void duplicateSelectorSkipsRowsWithoutVersionCode() {
        // versionCode <= 0 means we cannot prove the rows are the same payload.
        List<Backup> backups = Arrays.asList(
                versionedBackup("com.foo", 0, "manual", 0L, NOW - DAY),
                versionedBackup("com.foo", 0, "auto", 0L, NOW));
        assertTrue(BackupRetentionPolicy.selectVersionDuplicates(
                backups, BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST).isEmpty());
    }

    @Test
    public void duplicateSelectorIsDeterministicOnTieBreaker() {
        // Two backups with identical (pkg, user, versionCode, backupTime).
        // Tie-break by relativeDir keeps the result deterministic across runs.
        Backup a = versionedBackup("com.foo", 0, "alpha", 100L, NOW);
        Backup b = versionedBackup("com.foo", 0, "beta", 100L, NOW);
        a.relativeDir = "com.foo/alpha";
        b.relativeDir = "com.foo/beta";
        // Run twice; both runs should produce the same duplicate set ordering.
        List<Backup> run1 = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(a, b), BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST);
        List<Backup> run2 = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(b, a), BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST);
        assertEquals(1, run1.size());
        assertEquals(1, run2.size());
        assertEquals(run1.get(0).relativeDir, run2.get(0).relativeDir);
    }

    @Test
    public void duplicateSelectorLargestKeepsTheBiggestPayload() {
        // Three copies of the same (pkg, user, versionCode). LARGEST wins on
        // size; the smaller two are dropped regardless of backup name.
        Backup small = versionedBackup("com.foo", 0, "small", 100L, NOW);
        Backup medium = versionedBackup("com.foo", 0, "medium", 100L, NOW - DAY);
        Backup huge = versionedBackup("com.foo", 0, "huge", 100L, NOW - 2 * DAY);
        java.util.Map<Backup, Long> sizes = new java.util.HashMap<>();
        sizes.put(small, 100L);
        sizes.put(medium, 1_000L);
        sizes.put(huge, 50_000L);
        BackupRetentionPolicy.BackupSizeResolver resolver = backup -> {
            Long s = sizes.get(backup);
            return s == null ? -1L : s;
        };

        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(small, medium, huge),
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST, resolver);
        assertEquals(2, dupes.size());
        Set<String> dupeNames = new HashSet<>();
        for (Backup b : dupes) dupeNames.add(b.backupName);
        // "huge" is the largest and survives; "small" + "medium" are dupes.
        assertTrue(dupeNames.contains("small"));
        assertTrue(dupeNames.contains("medium"));
    }

    @Test
    public void duplicateSelectorLargestThenNewestBreaksSizeTiesWithFreshness() {
        // Three copies with the same on-disk size; the LARGEST_THEN_NEWEST
        // policy must keep the newest backup.
        Backup oldA = versionedBackup("com.foo", 0, "old-a", 100L, NOW - 30 * DAY);
        Backup mid = versionedBackup("com.foo", 0, "mid", 100L, NOW - 5 * DAY);
        Backup fresh = versionedBackup("com.foo", 0, "fresh", 100L, NOW);
        java.util.Map<Backup, Long> sizes = new java.util.HashMap<>();
        sizes.put(oldA, 5_000L);
        sizes.put(mid, 5_000L);
        sizes.put(fresh, 5_000L);

        BackupRetentionPolicy.BackupSizeResolver resolverTied = backup -> {
            Long s = sizes.get(backup);
            return s == null ? -1L : s;
        };
        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(oldA, mid, fresh),
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST_THEN_NEWEST,
                resolverTied);
        assertEquals(2, dupes.size());
        Set<String> dupeNames = new HashSet<>();
        for (Backup b : dupes) dupeNames.add(b.backupName);
        assertTrue(dupeNames.contains("old-a"));
        assertTrue(dupeNames.contains("mid"));
    }

    @Test
    public void duplicateSelectorLargestTreatsUnknownSizesAsLastResort() {
        // Two rows in the same bucket; resolver returns -1 ("unknown") for one
        // and a real size for the other. Known size must win regardless of
        // which row appears first in the input.
        Backup known = versionedBackup("com.foo", 0, "known", 100L, NOW - DAY);
        Backup unknown = versionedBackup("com.foo", 0, "unknown", 100L, NOW);
        BackupRetentionPolicy.BackupSizeResolver resolver = b -> b == known ? 4_096L : -1L;

        // Same input ordering both ways: keeper must always be `known`.
        List<Backup> a = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(known, unknown),
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST, resolver);
        List<Backup> b = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(unknown, known),
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST, resolver);
        assertEquals(1, a.size());
        assertEquals(1, b.size());
        assertEquals("unknown", a.get(0).backupName);
        assertEquals("unknown", b.get(0).backupName);
    }

    @Test
    public void duplicateSelectorLargestNullResolverFallsBackToDeterministicOrder() {
        // Without a resolver every row is "unknown size", so the
        // relativeDir tie-break selects the keeper deterministically.
        Backup alpha = versionedBackup("com.foo", 0, "alpha", 100L, NOW);
        Backup beta = versionedBackup("com.foo", 0, "beta", 100L, NOW - DAY);
        alpha.relativeDir = "com.foo/alpha";
        beta.relativeDir = "com.foo/beta";
        List<Backup> dupes = BackupRetentionPolicy.selectVersionDuplicates(
                Arrays.asList(alpha, beta),
                BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST, null);
        assertEquals(1, dupes.size());
        // alpha < beta lexicographically, so alpha is the keeper.
        assertEquals("beta", dupes.get(0).backupName);
    }

    @Test
    public void reclaimableBytesSumsKnownSizesAndIgnoresUnknown() {
        Backup a = versionedBackup("com.foo", 0, "a", 100L, NOW);
        Backup b = versionedBackup("com.foo", 0, "b", 100L, NOW - DAY);
        Backup c = versionedBackup("com.foo", 0, "c", 100L, NOW - 2 * DAY);
        BackupRetentionPolicy.BackupSizeResolver resolver = row -> {
            if (row == a) return 1_024L;
            if (row == b) return -1L;       // unknown
            if (row == c) return 2_048L;
            return 0L;
        };
        long bytes = BackupRetentionPolicy.reclaimableBytes(Arrays.asList(a, b, c), resolver);
        assertEquals(3_072L, bytes);
    }

    @Test
    public void pruneSelectedBackupsCountsOnlySuccessfulMetadataCleanupCandidates() {
        Backup a = backup("com.foo", 0, "old", NOW - 2 * DAY);
        Backup b = backup("com.foo", 0, "newer", NOW - DAY);
        List<Backup> attempted = new ArrayList<>();

        int deleted = BackupRetentionPolicy.pruneSelectedBackups(Arrays.asList(a, b), row -> {
            attempted.add(row);
            return row == a;
        });

        assertEquals(1, deleted);
        assertEquals(Arrays.asList(a, b), attempted);
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
