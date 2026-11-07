// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class PermissionWatchSchedulerTest {
    private Application mApp;
    private RecordingWorkSink mSink;
    private PermissionWatchDb mDb;
    private int mIntervalBefore;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mIntervalBefore = Prefs.Privacy.getPermissionWatcherIntervalMinutes();
        mSink = RecordingWorkSink.install();
    }

    @After
    public void tearDown() {
        RecordingWorkSink.uninstall();
        WatchTestDb.uninstall(mDb);
        Prefs.Privacy.setPermissionWatcherIntervalMinutes(mIntervalBefore);
    }

    @Test
    public void theSettingOffersOffAndThreeIntervals() {
        assertArrayEquals(new int[]{0, 15, 60, 24 * 60}, PermissionWatchScheduler.INTERVAL_CHOICES_MINUTES);
        assertEquals("the scheduled check starts off", 0, Prefs.Privacy.getPermissionWatcherIntervalMinutes());
    }

    @Test
    public void onlyAnIntervalSchedulesAnything() {
        assertFalse(PermissionWatchScheduler.shouldSchedule(0));
        assertFalse(PermissionWatchScheduler.shouldSchedule(-1));
        assertTrue(PermissionWatchScheduler.shouldSchedule(15));
        assertTrue(PermissionWatchScheduler.shouldSchedule(24 * 60));
    }

    @Test
    public void thePeriodicCheckRunsAtTheChosenInterval() {
        PeriodicWorkRequest request = PermissionWatchScheduler.buildPeriodicRequest(60);

        assertEquals(TimeUnit.MINUTES.toMillis(60), request.getWorkSpec().intervalDuration);
        assertEquals(PermissionWatchWorker.class.getName(), request.getWorkSpec().workerClassName);
        assertTrue(request.getTags().contains(PermissionWatchScheduler.WORK_TAG));
    }

    @Test
    public void anIntervalBelowWorkManagersFloorIsRaisedToIt() {
        assertEquals(TimeUnit.MINUTES.toMillis(15),
                PermissionWatchScheduler.buildPeriodicRequest(5).getWorkSpec().intervalDuration);
    }

    @Test
    public void aOneTimeCheckCarriesTheAppAndUser() {
        Data input = PermissionWatchScheduler.buildOneTimeRequest("com.example.one", 10).getWorkSpec().input;

        assertEquals("com.example.one", input.getString(PermissionWatchWorker.INPUT_PACKAGE));
        assertEquals(10, input.getInt(PermissionWatchWorker.INPUT_USER, -1));

        Data all = PermissionWatchScheduler.buildOneTimeRequest(null, 0).getWorkSpec().input;
        assertNull(all.getString(PermissionWatchWorker.INPUT_PACKAGE));
    }

    @Test
    public void choosingAnIntervalSchedulesAndOffCancels() {
        PermissionWatchScheduler.scheduleOrCancel(mApp, 24 * 60);

        assertEquals(1, mSink.scheduled.size());
        assertEquals(TimeUnit.DAYS.toMillis(1), mSink.scheduled.get(0).getWorkSpec().intervalDuration);
        assertEquals(0, mSink.cancels);

        PermissionWatchScheduler.scheduleOrCancel(mApp, 0);

        assertEquals(1, mSink.scheduled.size());
        assertEquals(1, mSink.cancels);
    }

    @Test
    public void eachAppAndUserGetsItsOwnPendingCheck() {
        PermissionWatchScheduler.checkSoon(mApp, "com.example.one", 0);
        PermissionWatchScheduler.checkSoon(mApp, "com.example.one", 10);
        PermissionWatchScheduler.checkSoon(mApp, null, 0);

        assertEquals(3, mSink.enqueuedNames.size());
        assertNotEquals(mSink.enqueuedNames.get(0), mSink.enqueuedNames.get(1));
        assertEquals(PermissionWatchScheduler.ALL_RULES_WORK_NAME, mSink.enqueuedNames.get(2));
        OneTimeWorkRequest work = mSink.enqueued.get(1);
        assertEquals(10, work.getWorkSpec().input.getInt(PermissionWatchWorker.INPUT_USER, -1));
    }

    @Test
    public void aRestartWithTheScheduleOnChecksEveryRuleRightAway() {
        mDb = WatchTestDb.install(mApp);
        Prefs.Privacy.setPermissionWatcherIntervalMinutes(60);

        PermissionWatchScheduler.onBoot(mApp);

        assertEquals(1, mSink.scheduled.size());
        assertEquals(Collections.singletonList(PermissionWatchScheduler.ALL_RULES_WORK_NAME), mSink.enqueuedNames);
    }

    @Test
    public void aRestartWithTheScheduleOffChecksNothing() {
        mDb = WatchTestDb.install(mApp);
        Prefs.Privacy.setPermissionWatcherIntervalMinutes(0);

        PermissionWatchScheduler.onBoot(mApp);

        assertEquals(1, mSink.cancels);
        assertTrue(mSink.scheduled.isEmpty());
        assertTrue(mSink.enqueuedNames.isEmpty());
    }

    @Test
    public void aRestartWithNoRulesSkipsTheCheck() {
        Prefs.Privacy.setPermissionWatcherIntervalMinutes(60);

        PermissionWatchScheduler.onBoot(mApp);

        assertEquals(1, mSink.scheduled.size());
        assertTrue(mSink.enqueuedNames.isEmpty());
    }
}
