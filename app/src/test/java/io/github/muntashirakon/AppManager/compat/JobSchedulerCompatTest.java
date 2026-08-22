// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.app.job.JobScheduler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JobSchedulerCompatTest {
    @Test
    public void parseDumpsysFiltersPackageAndUserAndExplainsConstraints() {
        List<String> dump = Arrays.asList(
                "JobStatus{abc #u0a123/17 com.example.app/com.example.JobService u=0 NET CHARGING "
                        + "satisfied:0x0 unsatisfied:0x80000021}: 100",
                "JobStatus{ns #u0a123/18 @androidx.work.systemjobscheduler@com.example.app/"
                        + "androidx.work.impl.background.systemjob.SystemJobService u=0 NET "
                        + "satisfied:0x0 unsatisfied:0x20}: 101",
                "JobStatus{def #u10a123/18 com.example.app/com.example.OtherJob u=10 NET "
                        + "satisfied:0x0 unsatisfied:0x20}: 101",
                "JobStatus{ghi #u0a456/19 com.other.app/com.other.Job u=0 NET "
                        + "satisfied:0x0 unsatisfied:0x20}: 102");

        List<JobSchedulerCompat.PendingJob> jobs = JobSchedulerCompat.parseDumpsys(
                dump, "com.example.app", 0);

        assertEquals(2, jobs.size());
        assertEquals(17, jobs.get(0).getJobId());
        assertEquals("com.example.app/com.example.JobService", jobs.get(0).getService());
        assertTrue(jobs.get(0).getReasons().contains("charging constraint"));
        assertTrue(jobs.get(0).getReasons().contains("network constraint"));
        assertTrue(jobs.get(0).getReasons().contains("system state"));
        assertEquals(18, jobs.get(1).getJobId());
    }

    @Test
    public void describesApiReasons() {
        assertEquals("quota", JobSchedulerCompat.describePendingReason(
                JobScheduler.PENDING_JOB_REASON_QUOTA));
        assertEquals("ready or unknown", JobSchedulerCompat.describePendingReason(
                JobScheduler.PENDING_JOB_REASON_UNDEFINED));
    }
}
