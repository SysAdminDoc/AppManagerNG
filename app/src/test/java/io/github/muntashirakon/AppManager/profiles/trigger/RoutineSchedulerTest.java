// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.work.Constraints;
import androidx.work.NetworkType;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class RoutineSchedulerTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void timeOfDayTriggerUsesDailyPeriodAndNextWallClockDelay() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_TIME_OF_DAY)
                .timeOfDay(2, 0)
                .build();
        long now = millis(2026, Calendar.MAY, 26, 1, 30);

        assertEquals(RoutineScheduler.DAILY_INTERVAL_MILLIS, RoutineScheduler.getRepeatIntervalMillis(trigger));
        assertEquals(TimeUnit.MINUTES.toMillis(30),
                RoutineScheduler.getInitialDelayMillis(trigger, now, UTC));
    }

    @Test
    public void timeOfDayDelayRollsToTomorrowWhenTargetPassed() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_TIME_OF_DAY)
                .timeOfDay(2, 0)
                .build();
        long now = millis(2026, Calendar.MAY, 26, 3, 0);

        assertEquals(TimeUnit.HOURS.toMillis(23),
                RoutineScheduler.getInitialDelayMillis(trigger, now, UTC));
    }

    @Test
    public void eventTriggersUseMinimumPeriodAndExpectedConstraints() {
        ProfileTrigger charging = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_ON_CHARGING).build();
        ProfileTrigger wifi = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_ON_NETWORK_WIFI).build();
        ProfileTrigger any = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_ON_NETWORK_ANY).build();

        assertEquals(RoutineScheduler.MIN_PERIODIC_INTERVAL_MILLIS,
                RoutineScheduler.getRepeatIntervalMillis(charging));
        assertEquals(0L, RoutineScheduler.getInitialDelayMillis(charging, System.currentTimeMillis()));
        Constraints chargingConstraints = RoutineScheduler.buildConstraints(charging);
        assertTrue(chargingConstraints.requiresCharging());
        assertEquals(NetworkType.NOT_REQUIRED, chargingConstraints.getRequiredNetworkType());
        assertFalse(RoutineScheduler.buildConstraints(wifi).requiresCharging());
        assertEquals(NetworkType.UNMETERED, RoutineScheduler.buildConstraints(wifi).getRequiredNetworkType());
        assertEquals(NetworkType.CONNECTED, RoutineScheduler.buildConstraints(any).getRequiredNetworkType());
    }

    @Test
    public void workNameAndInputDataAreStablePerTrigger() {
        assertEquals("routine_trigger_abc", RoutineScheduler.uniqueWorkName("abc"));
        assertEquals("abc", RoutineScheduler.inputData("abc").getString(RoutineWorker.KEY_TRIGGER_ID));
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.set(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
