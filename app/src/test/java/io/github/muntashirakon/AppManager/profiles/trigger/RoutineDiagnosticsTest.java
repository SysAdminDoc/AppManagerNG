// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RoutineDiagnosticsTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        RoutineScheduler.clearStoredState(mContext, "trigger-a");
    }

    @Test
    public void describeWorkStopReasonLabelsQuotaStops() {
        assertEquals("not stopped",
                RoutineDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_NOT_STOPPED));
        assertEquals("charging constraint",
                RoutineDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_CONSTRAINT_CHARGING));
        assertEquals("quota",
                RoutineDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_QUOTA));
    }

    @Test
    public void describePendingReasonLabelsQuotaReasons() {
        assertEquals("charging constraint",
                RoutineDiagnostics.describePendingReason(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING));
        assertEquals("quota",
                RoutineDiagnostics.describePendingReason(JobScheduler.PENDING_JOB_REASON_QUOTA));
        assertEquals("scheduler optimization",
                RoutineDiagnostics.describePendingReason(
                        JobScheduler.PENDING_JOB_REASON_JOB_SCHEDULER_OPTIMIZATION));
    }

    @Test
    public void formatTriggerSummaryIncludesPersistedDiagnostics() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_ON_CHARGING)
                .id("trigger-a")
                .build();

        RoutineScheduler.recordDiagnostics(mContext, trigger.id, "WorkManager: enqueued, attempt 0, stop: quota");

        String summary = RoutineScheduler.formatTriggerSummary(mContext, trigger);
        assertTrue(summary, summary.contains("Never run"));
        assertTrue(summary, summary.contains("Diagnostics: WorkManager: enqueued, attempt 0, stop: quota"));
    }
}
