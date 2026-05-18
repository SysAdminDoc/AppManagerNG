// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;

import android.app.job.JobScheduler;

import androidx.work.WorkInfo;

import org.junit.Test;

public class AutoBackupDiagnosticsTest {
    @Test
    public void describeWorkStopReasonLabelsSchedulerStopReasons() {
        assertEquals("not stopped",
                AutoBackupDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_NOT_STOPPED));
        assertEquals("charging constraint",
                AutoBackupDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_CONSTRAINT_CHARGING));
        assertEquals("quota",
                AutoBackupDiagnostics.describeWorkStopReason(WorkInfo.STOP_REASON_QUOTA));
    }

    @Test
    public void describePendingReasonLabelsSchedulerPendingReasons() {
        assertEquals("charging constraint",
                AutoBackupDiagnostics.describePendingReason(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING));
        assertEquals("quota",
                AutoBackupDiagnostics.describePendingReason(JobScheduler.PENDING_JOB_REASON_QUOTA));
        assertEquals("scheduler optimization",
                AutoBackupDiagnostics.describePendingReason(
                        JobScheduler.PENDING_JOB_REASON_JOB_SCHEDULER_OPTIMIZATION));
    }
}
