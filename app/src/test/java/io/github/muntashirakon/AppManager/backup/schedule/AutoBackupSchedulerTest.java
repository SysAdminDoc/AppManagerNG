// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;

import androidx.work.NetworkType;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class AutoBackupSchedulerTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

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
}
