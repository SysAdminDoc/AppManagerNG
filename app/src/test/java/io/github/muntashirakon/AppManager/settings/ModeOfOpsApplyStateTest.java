// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModeOfOpsApplyStateTest {
    @Test
    public void anOpenApplyShowsAcrossInstancesUntilEveryWayOfEndingIt() {
        ModeOfOpsApplyState first = new ModeOfOpsApplyState();
        ModeOfOpsApplyState second = new ModeOfOpsApplyState();
        assertFalse(ModeOfOpsApplyState.isAnyApplying());

        assertTrue(first.begin(Ops.MODE_ROOT, Ops.MODE_NO_ROOT));
        // A refused begin isn't a second open apply
        assertFalse(first.begin(Ops.MODE_ROOT, Ops.MODE_SHIZUKU));
        assertTrue(second.begin(Ops.MODE_ROOT, Ops.MODE_AUTO));
        assertTrue(ModeOfOpsApplyState.isAnyApplying());

        first.finishSuccess();
        // Ending one twice doesn't take the other's count with it
        first.finishSuccess();
        first.dismiss();
        assertTrue(ModeOfOpsApplyState.isAnyApplying());

        second.finishFailure();
        assertFalse(ModeOfOpsApplyState.isAnyApplying());

        assertTrue(first.begin(Ops.MODE_ROOT, Ops.MODE_NO_ROOT));
        first.dismiss();
        assertFalse(ModeOfOpsApplyState.isAnyApplying());
    }

    @Test
    public void beginRejectsDoubleApplyUntilTerminalStatus() {
        ModeOfOpsApplyState state = new ModeOfOpsApplyState();

        assertTrue(state.begin(Ops.MODE_ROOT, Ops.MODE_ADB_OVER_TCP));
        assertFalse(state.begin(Ops.MODE_ROOT, Ops.MODE_SHIZUKU));

        assertEquals(Ops.MODE_ADB_OVER_TCP, state.getPendingMode());
        assertEquals(Ops.MODE_ADB_OVER_TCP, state.finishSuccess());
        assertFalse(state.isApplying());
    }

    @Test
    public void failureRollsBackToPreviousMode() {
        ModeOfOpsApplyState state = new ModeOfOpsApplyState();

        assertTrue(state.begin(Ops.MODE_SHIZUKU, Ops.MODE_ADB_WIFI));

        assertEquals(Ops.MODE_SHIZUKU, state.finishFailure());
        assertFalse(state.isApplying());
        assertNull(state.finishFailure());
    }

    @Test
    public void dismissClearsPendingApplyAndIgnoresLateSuccess() {
        ModeOfOpsApplyState state = new ModeOfOpsApplyState();

        assertTrue(state.begin(Ops.MODE_NO_ROOT, Ops.MODE_ROOT));

        assertEquals(Ops.MODE_NO_ROOT, state.dismiss());
        assertFalse(state.isApplying());
        assertNull(state.finishSuccess());
    }
}
