// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.work.Data;

import org.junit.Test;

import java.util.UUID;

public class AutoBackupWorkerTest {
    @Test
    public void scheduledRunsRecordTheSchedule() {
        assertEquals(AutoBackupWorker.ORIGIN_SCHEDULE, AutoBackupWorker.originOf(Data.EMPTY));
    }

    @Test
    public void manualRunsRecordTheOriginTheirRequestNamed() {
        assertEquals(AutoBackupWorker.ORIGIN_SETTINGS,
                AutoBackupWorker.originOf(AutoBackupWorker.manualInputData(AutoBackupWorker.ORIGIN_SETTINGS)));
        assertEquals(AutoBackupWorker.ORIGIN_HOME_SCREEN_SHORTCUT, AutoBackupWorker.originOf(
                AutoBackupWorker.manualInputData(AutoBackupWorker.ORIGIN_HOME_SCREEN_SHORTCUT)));
    }

    @Test
    public void manualRunsWithoutAKnownOriginRecordNone() {
        Data queuedBeforeOrigins = new Data.Builder().putBoolean(AutoBackupWorker.KEY_MANUAL, true).build();

        assertEquals("", AutoBackupWorker.originOf(queuedBeforeOrigins));
        assertEquals("", AutoBackupWorker.originOf(AutoBackupWorker.manualInputData("schedule_now")));
        // A manual run never claims the schedule started it.
        assertEquals("", AutoBackupWorker.originOf(AutoBackupWorker.manualInputData(AutoBackupWorker.ORIGIN_SCHEDULE)));
    }

    @Test
    public void everyRecordedOriginHasALabelAndAnUnknownOneHasNone() {
        assertNotEquals(0, AutoBackupScheduler.getOriginLabel(AutoBackupWorker.ORIGIN_SCHEDULE));
        assertNotEquals(0, AutoBackupScheduler.getOriginLabel(AutoBackupWorker.ORIGIN_SETTINGS));
        assertNotEquals(0, AutoBackupScheduler.getOriginLabel(AutoBackupWorker.ORIGIN_HOME_SCREEN_SHORTCUT));
        assertEquals(0, AutoBackupScheduler.getOriginLabel(""));
    }

    @Test
    public void foregroundNotificationIdIsStableForWorkerId() {
        UUID workerId = UUID.fromString("00000000-0000-0001-0000-000000000002");

        assertEquals(AutoBackupWorker.foregroundNotificationIdFor(workerId),
                AutoBackupWorker.foregroundNotificationIdFor(workerId));
    }

    @Test
    public void foregroundNotificationIdUsesWorkerNamespace() {
        UUID first = UUID.fromString("00000000-0000-0001-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0001-0000-000000000003");

        int firstId = AutoBackupWorker.foregroundNotificationIdFor(first);
        int secondId = AutoBackupWorker.foregroundNotificationIdFor(second);

        assertNotEquals(firstId, secondId);
        assertTrue((firstId & 0xffff0000) == 0x4a110000);
        assertTrue((secondId & 0xffff0000) == 0x4a110000);
        assertNotEquals(0x4a12, firstId);
        assertNotEquals(0x4a12, secondId);
    }
}
