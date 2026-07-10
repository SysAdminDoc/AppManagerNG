// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.settings.Prefs;

public class AutoFreezeOnLockWorkerTest {
    @Test
    public void freezeDecisionRequiresEnabledAndNonInteractiveDevice() {
        assertTrue(AutoFreezeOnLockWorker.shouldFreeze(true, false));
        assertFalse(AutoFreezeOnLockWorker.shouldFreeze(false, false));
        assertFalse(AutoFreezeOnLockWorker.shouldFreeze(true, true));
    }

    @Test
    public void delayIsBoundedToSettingsRange() {
        assertEquals(0, AutoFreezeOnLockWorker.sanitizeDelaySeconds(-1));
        assertEquals(30, AutoFreezeOnLockWorker.sanitizeDelaySeconds(30));
        assertEquals(Prefs.Blocking.MAX_AUTO_FREEZE_DELAY_SECONDS,
                AutoFreezeOnLockWorker.sanitizeDelaySeconds(Integer.MAX_VALUE));
    }
}
