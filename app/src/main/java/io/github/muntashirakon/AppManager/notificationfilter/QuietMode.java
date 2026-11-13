// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Whether quiet mode is on at a given moment. It is on while the user turned it on, or while the daily schedule is
 * running, unless the user turned it off partway through today's run.
 */
public final class QuietMode {
    public static final int MINUTES_PER_DAY = 24 * 60;

    /** Turned on by hand, from the screen or the tile. Stays on until turned off. */
    public final boolean manual;
    public final boolean scheduled;
    /** Minutes after midnight. A start equal to the end means all day. */
    public final int startMinute;
    public final int endMinute;
    /** Before this time the schedule is ignored, because the user turned quiet mode off during a run. */
    public final long scheduleSkippedUntilMillis;

    public QuietMode(boolean manual, boolean scheduled, int startMinute, int endMinute,
                     long scheduleSkippedUntilMillis) {
        this.manual = manual;
        this.scheduled = scheduled;
        this.startMinute = clampMinute(startMinute);
        this.endMinute = clampMinute(endMinute);
        this.scheduleSkippedUntilMillis = scheduleSkippedUntilMillis;
    }

    public boolean isActive(long nowMillis, @NonNull TimeZone zone) {
        return manual || isScheduleRunning(nowMillis, zone);
    }

    public boolean isScheduleRunning(long nowMillis, @NonNull TimeZone zone) {
        return scheduled && nowMillis >= scheduleSkippedUntilMillis
                && inWindow(minuteOfDay(nowMillis, zone), startMinute, endMinute);
    }

    /** What the switch or the tile leads to. Turning it off during a scheduled run skips the rest of that run. */
    @NonNull
    public QuietMode toggled(long nowMillis, @NonNull TimeZone zone) {
        if (!isActive(nowMillis, zone)) {
            return new QuietMode(true, scheduled, startMinute, endMinute, 0);
        }
        long skipUntil = isScheduleRunning(nowMillis, zone) ? runEnd(nowMillis, zone) : scheduleSkippedUntilMillis;
        return new QuietMode(false, scheduled, startMinute, endMinute, skipUntil);
    }

    @NonNull
    public QuietMode withSchedule(boolean scheduled, int startMinute, int endMinute) {
        // A new schedule starts fresh.
        return new QuietMode(manual, scheduled, startMinute, endMinute, 0);
    }

    static boolean inWindow(int minuteOfDay, int startMinute, int endMinute) {
        if (startMinute == endMinute) {
            return true;
        }
        if (startMinute < endMinute) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
        // Runs past midnight.
        return minuteOfDay >= startMinute || minuteOfDay < endMinute;
    }

    /** The next time the clock reads the end minute, which is when the run that's going now stops. */
    static long runEnd(long nowMillis, @NonNull TimeZone zone, int endMinute) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.HOUR_OF_DAY, endMinute / 60);
        calendar.set(Calendar.MINUTE, endMinute % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= nowMillis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar.getTimeInMillis();
    }

    private long runEnd(long nowMillis, @NonNull TimeZone zone) {
        return runEnd(nowMillis, zone, endMinute);
    }

    static int minuteOfDay(long millis, @NonNull TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static int clampMinute(int minute) {
        return Math.max(0, Math.min(MINUTES_PER_DAY - 1, minute));
    }
}
