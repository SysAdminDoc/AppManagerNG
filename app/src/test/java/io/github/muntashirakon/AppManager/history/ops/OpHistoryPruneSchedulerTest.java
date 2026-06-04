// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OpHistoryPruneSchedulerTest {
    @Test
    public void keepForeverRetentionDoesNotScheduleWorker() {
        assertFalse(OpHistoryPruneScheduler.shouldSchedule(0));
        assertFalse(OpHistoryPruneScheduler.shouldSchedule(-1));
    }

    @Test
    public void finiteRetentionSchedulesWorker() {
        assertTrue(OpHistoryPruneScheduler.shouldSchedule(7));
        assertTrue(OpHistoryPruneScheduler.shouldSchedule(365));
    }

    @Test
    public void periodicRequestCarriesStableWorkTag() {
        assertTrue(OpHistoryPruneScheduler.buildPeriodicRequest()
                .getTags()
                .contains(OpHistoryPruneScheduler.WORK_TAG));
    }
}
