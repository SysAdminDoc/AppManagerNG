// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Review finding: a Run now tap during a scheduled run recorded its origin before finding the run
 * slot taken, so the scheduled run's result showed up as started from Settings.
 */
@RunWith(RobolectricTestRunner.class)
public class AutoBackupRunOriginTest {
    @After
    public void tearDown() {
        AutoBackupWorker.releaseRun();
    }

    @Test
    public void onlyTheRunHoldingTheSlotRecordsItsOrigin() {
        assertTrue(AutoBackupWorker.claimRun(AutoBackupWorker.ORIGIN_SCHEDULE));
        assertEquals(AutoBackupWorker.ORIGIN_SCHEDULE, Prefs.BackupRestore.getScheduledBackupLastOrigin());

        assertFalse(AutoBackupWorker.claimRun(AutoBackupWorker.ORIGIN_SETTINGS));
        assertEquals(AutoBackupWorker.ORIGIN_SCHEDULE, Prefs.BackupRestore.getScheduledBackupLastOrigin());

        AutoBackupWorker.releaseRun();
        assertTrue(AutoBackupWorker.claimRun(AutoBackupWorker.ORIGIN_SETTINGS));
        assertEquals(AutoBackupWorker.ORIGIN_SETTINGS, Prefs.BackupRestore.getScheduledBackupLastOrigin());
    }
}
