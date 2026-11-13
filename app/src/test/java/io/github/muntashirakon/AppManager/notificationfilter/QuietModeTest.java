// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class QuietModeTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final int TEN_PM = 22 * 60;
    private static final int SEVEN_AM = 7 * 60;

    /** Millis for the given day of November 2026 at the given time, in UTC. */
    private static long at(int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    private static QuietMode nightly() {
        return new QuietMode(false, true, TEN_PM, SEVEN_AM, 0);
    }

    @Test
    public void scheduleRunsAcrossMidnight() {
        QuietMode quiet = nightly();

        assertTrue(quiet.isActive(at(10, 22, 0), UTC));
        assertTrue(quiet.isActive(at(10, 23, 59), UTC));
        assertTrue(quiet.isActive(at(11, 0, 0), UTC));
        assertTrue(quiet.isActive(at(11, 6, 59), UTC));
        assertFalse(quiet.isActive(at(11, 7, 0), UTC));
        assertFalse(quiet.isActive(at(11, 12, 0), UTC));
        assertFalse(quiet.isActive(at(11, 21, 59), UTC));
    }

    @Test
    public void scheduleWithinOneDay() {
        QuietMode quiet = new QuietMode(false, true, 9 * 60, 17 * 60, 0);

        assertFalse(quiet.isActive(at(10, 8, 59), UTC));
        assertTrue(quiet.isActive(at(10, 9, 0), UTC));
        assertTrue(quiet.isActive(at(10, 16, 59), UTC));
        assertFalse(quiet.isActive(at(10, 17, 0), UTC));
    }

    @Test
    public void sameStartAndEndMeansAllDay() {
        QuietMode quiet = new QuietMode(false, true, 8 * 60, 8 * 60, 0);

        assertTrue(quiet.isActive(at(10, 3, 0), UTC));
        assertTrue(quiet.isActive(at(10, 15, 0), UTC));
    }

    @Test
    public void scheduleIsReadInTheGivenTimeZone() {
        TimeZone newYork = TimeZone.getTimeZone("America/New_York");
        // 03:30 UTC on November 11 is 22:30 on November 10 in New York.
        long now = at(11, 3, 30);
        QuietMode lateEvening = new QuietMode(false, true, TEN_PM, 23 * 60, 0);

        assertTrue(lateEvening.isActive(now, newYork));
        assertFalse(lateEvening.isActive(now, UTC));
    }

    @Test
    public void scheduleOffMeansOnlyTheSwitchCounts() {
        QuietMode quiet = new QuietMode(false, false, TEN_PM, SEVEN_AM, 0);

        assertFalse(quiet.isActive(at(10, 23, 0), UTC));
        assertTrue(quiet.toggled(at(10, 12, 0), UTC).isActive(at(10, 12, 0), UTC));
    }

    @Test
    public void manualOnHoldsAllDay() {
        QuietMode quiet = new QuietMode(true, true, TEN_PM, SEVEN_AM, 0);

        assertTrue(quiet.isActive(at(10, 12, 0), UTC));
        assertTrue(quiet.isActive(at(10, 23, 0), UTC));
    }

    @Test
    public void turningOffDuringARunSkipsTheRestOfThatRunOnly() {
        long lateNight = at(10, 23, 0);
        QuietMode off = nightly().toggled(lateNight, UTC);

        assertFalse(off.manual);
        assertFalse(off.isActive(lateNight, UTC));
        assertFalse(off.isActive(at(11, 6, 59), UTC));
        assertEquals(at(11, 7, 0), off.scheduleSkippedUntilMillis);
        // The next night runs as usual.
        assertTrue(off.isActive(at(11, 22, 0), UTC));
    }

    @Test
    public void turningOffAfterMidnightSkipsUntilThatMorning() {
        long early = at(11, 2, 0);
        QuietMode off = nightly().toggled(early, UTC);

        assertEquals(at(11, 7, 0), off.scheduleSkippedUntilMillis);
        assertFalse(off.isActive(at(11, 6, 0), UTC));
        assertTrue(off.isActive(at(11, 22, 30), UTC));
    }

    @Test
    public void turningOnAgainDuringASkippedRunWorks() {
        long lateNight = at(10, 23, 0);
        QuietMode off = nightly().toggled(lateNight, UTC);
        QuietMode on = off.toggled(at(10, 23, 30), UTC);

        assertTrue(on.manual);
        assertTrue(on.isActive(at(10, 23, 30), UTC));
        assertEquals(0, on.scheduleSkippedUntilMillis);
    }

    @Test
    public void manualOffOutsideARunLeavesTheScheduleAlone() {
        long noon = at(10, 12, 0);
        QuietMode on = nightly().toggled(noon, UTC);
        QuietMode off = on.toggled(at(10, 13, 0), UTC);

        assertTrue(on.manual);
        assertFalse(off.manual);
        assertEquals(0, off.scheduleSkippedUntilMillis);
        assertTrue(off.isActive(at(10, 22, 0), UTC));
    }

    @Test
    public void newScheduleClearsTheSkip() {
        QuietMode off = nightly().toggled(at(10, 23, 0), UTC);
        QuietMode rescheduled = off.withSchedule(true, 23 * 60, SEVEN_AM);

        assertEquals(0, rescheduled.scheduleSkippedUntilMillis);
        assertTrue(rescheduled.isActive(at(10, 23, 30), UTC));
    }

    @Test
    public void minutesOutsideTheDayAreClamped() {
        QuietMode quiet = new QuietMode(false, true, -5, 5000, 0);

        assertEquals(0, quiet.startMinute);
        assertEquals(QuietMode.MINUTES_PER_DAY - 1, quiet.endMinute);
    }
}
