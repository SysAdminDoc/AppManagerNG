// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.work.NetworkType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public class AutoBackupSchedulerTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void initialDelayUsesLaterTodayWhenTargetIsFuture() {
        long now = millis(2026, Calendar.MAY, 18, 1, 30);
        long delay = AutoBackupScheduler.computeInitialDelayMillis(2, 0, now, UTC);

        assertEquals(TimeUnit.MINUTES.toMillis(30), delay);
    }

    @Test
    public void initialDelayRollsToTomorrowWhenTargetAlreadyPassed() {
        long now = millis(2026, Calendar.MAY, 18, 3, 0);
        long delay = AutoBackupScheduler.computeInitialDelayMillis(2, 0, now, UTC);

        assertEquals(TimeUnit.HOURS.toMillis(23), delay);
    }

    @Test
    public void initialDelayRollsToTomorrowWhenTargetIsNow() {
        long now = millis(2026, Calendar.MAY, 18, 2, 0);
        long delay = AutoBackupScheduler.computeInitialDelayMillis(2, 0, now, UTC);

        assertEquals(TimeUnit.DAYS.toMillis(1), delay);
    }

    @Test
    public void invalidTimeInputsAreClamped() {
        assertEquals(0, AutoBackupScheduler.sanitizeHour(-4));
        assertEquals(23, AutoBackupScheduler.sanitizeHour(90));
        assertEquals(0, AutoBackupScheduler.sanitizeMinute(-1));
        assertEquals(59, AutoBackupScheduler.sanitizeMinute(90));
        assertEquals(0, AutoBackupScheduler.sanitizeMinimumAgeDays(-1));
        assertEquals(AutoBackupScheduler.MAX_MINIMUM_AGE_DAYS,
                AutoBackupScheduler.sanitizeMinimumAgeDays(AutoBackupScheduler.MAX_MINIMUM_AGE_DAYS + 1));
    }

    @Test
    public void networkPreferenceMapsToWorkManagerNetworkType() {
        assertEquals(NetworkType.NOT_REQUIRED,
                AutoBackupScheduler.toWorkNetworkType(AutoBackupScheduler.NETWORK_NOT_REQUIRED));
        assertEquals(NetworkType.CONNECTED,
                AutoBackupScheduler.toWorkNetworkType(AutoBackupScheduler.NETWORK_CONNECTED));
        assertEquals(NetworkType.UNMETERED,
                AutoBackupScheduler.toWorkNetworkType(AutoBackupScheduler.NETWORK_UNMETERED));
        assertEquals(NetworkType.NOT_REQUIRED, AutoBackupScheduler.toWorkNetworkType(99));
    }

    @Test
    public void newestBackupWithinMinimumAgeIsSkipped() {
        long now = 10 * DAY;
        AutoBackupScheduler.BackupSelection selection = AutoBackupScheduler.selectPackagesDueForBackup(
                Arrays.asList(pair("com.fresh", 0), pair("com.stale", 0), pair("com.unknown", 0)),
                Arrays.asList(
                        backup("com.fresh", 0, now - 2 * DAY),
                        backup("com.fresh", 0, now - TimeUnit.HOURS.toMillis(6)),
                        backup("com.stale", 0, now - 3 * DAY),
                        backup("com.unknown", 0, 0)),
                1,
                now);

        assertEquals(1, selection.getSkippedPackages());
        assertEquals(2, selection.getDuePackages().size());
        assertEquals("com.stale", selection.getDuePackages().get(0).getPackageName());
        assertEquals("com.unknown", selection.getDuePackages().get(1).getPackageName());
    }

    @Test
    public void skippedDetailsCaptureReasonAndNewestBackupTime() {
        long now = 10 * DAY;
        AutoBackupScheduler.BackupSelection selection = AutoBackupScheduler.selectPackagesDueForBackup(
                Arrays.asList(pair("com.fresh", 0), pair("com.stale", 0), pair("com.unknown", 0)),
                Arrays.asList(
                        backup("com.fresh", 0, now - 2 * DAY),
                        backup("com.fresh", 0, now - TimeUnit.HOURS.toMillis(6)),
                        backup("com.stale", 0, now - 3 * DAY),
                        backup("com.unknown", 0, 0)),
                1,
                now);

        java.util.List<AutoBackupScheduler.SkippedPackage> skipped = selection.getSkippedDetails();
        assertEquals(1, skipped.size());
        AutoBackupScheduler.SkippedPackage sp = skipped.get(0);
        assertEquals("com.fresh", sp.packageName);
        assertEquals(0, sp.userId);
        assertEquals(AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, sp.reason);
        // The newest of com.fresh's two backups (6h ago) is the one recorded.
        assertEquals(now - TimeUnit.HOURS.toMillis(6), sp.lastBackupMillis);
    }

    @Test
    public void ageGateIsPerUser() {
        long now = 10 * DAY;
        AutoBackupScheduler.BackupSelection selection = AutoBackupScheduler.selectPackagesDueForBackup(
                Arrays.asList(pair("com.multi", 0), pair("com.multi", 10)),
                Collections.singletonList(backup("com.multi", 0, now - TimeUnit.HOURS.toMillis(1))),
                1,
                now);

        assertEquals(1, selection.getSkippedPackages());
        assertEquals(1, selection.getDuePackages().size());
        assertEquals(10, selection.getDuePackages().get(0).getUserId());
    }

    @Test
    public void zeroMinimumAgeKeepsEveryPackageDue() {
        long now = 10 * DAY;
        AutoBackupScheduler.BackupSelection selection = AutoBackupScheduler.selectPackagesDueForBackup(
                Collections.singletonList(pair("com.fresh", 0)),
                Collections.singletonList(backup("com.fresh", 0, now - TimeUnit.HOURS.toMillis(1))),
                0,
                now);

        assertEquals(0, selection.getSkippedPackages());
        assertEquals(1, selection.getDuePackages().size());
    }

    @Test
    public void skippedDetailsSerializeRoundTrip() {
        String serialized = AutoBackupScheduler.serializeSkippedDetails(Arrays.asList(
                skipped("com.fresh", 0, AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, 1234L),
                skipped("com.other", 10, AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, 5678L)),
                10);

        java.util.List<AutoBackupScheduler.SkippedPackage> restored =
                AutoBackupScheduler.deserializeSkippedDetails(serialized);

        assertEquals(2, restored.size());
        assertEquals("com.fresh", restored.get(0).packageName);
        assertEquals(0, restored.get(0).userId);
        assertEquals(AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, restored.get(0).reason);
        assertEquals(1234L, restored.get(0).lastBackupMillis);
        assertEquals("com.other", restored.get(1).packageName);
        assertEquals(10, restored.get(1).userId);
        assertEquals(5678L, restored.get(1).lastBackupMillis);
    }

    @Test
    public void skippedDetailsSerializationIsBounded() {
        String serialized = AutoBackupScheduler.serializeSkippedDetails(Arrays.asList(
                skipped("com.first", 0, AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, 1L),
                skipped("com.second", 0, AutoBackupScheduler.SkipReason.BACKED_UP_RECENTLY, 2L)),
                1);

        java.util.List<AutoBackupScheduler.SkippedPackage> restored =
                AutoBackupScheduler.deserializeSkippedDetails(serialized);

        assertEquals(1, restored.size());
        assertEquals("com.first", restored.get(0).packageName);
    }

    @Test
    public void skippedDetailsParserIgnoresMalformedInput() {
        assertTrue(AutoBackupScheduler.deserializeSkippedDetails("").isEmpty());
        assertTrue(AutoBackupScheduler.deserializeSkippedDetails("not json").isEmpty());
        assertTrue(AutoBackupScheduler.deserializeSkippedDetails(
                "[{\"package\":\"com.example\",\"reason\":\"UNKNOWN\"}]").isEmpty());
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static UserPackagePair pair(String packageName, int userId) {
        return new UserPackagePair(packageName, userId);
    }

    private static Backup backup(String packageName, int userId, long backupTime) {
        Backup backup = new Backup();
        backup.packageName = packageName;
        backup.userId = userId;
        backup.backupName = "";
        backup.backupTime = backupTime;
        return backup;
    }

    private static AutoBackupScheduler.SkippedPackage skipped(String packageName, int userId,
                                                              AutoBackupScheduler.SkipReason reason,
                                                              long lastBackupMillis) {
        return new AutoBackupScheduler.SkippedPackage(packageName, userId, reason, lastBackupMillis);
    }
}
