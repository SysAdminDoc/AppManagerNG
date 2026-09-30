// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.servermanager.ServerConnectionFailure;
import io.github.muntashirakon.AppManager.servermanager.ServerConnectionFailure.Reason;

@RunWith(RobolectricTestRunner.class)
public class OpsServerFailureStatusTest {
    @Test
    public void eachReasonHasItsOwnStatus() {
        assertEquals(Ops.STATUS_FAILURE_SERVER_START,
                Ops.getServerFailureStatus(failure(Reason.SERVER_START), Ops.STATUS_FAILURE));
        assertEquals(Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE,
                Ops.getServerFailureStatus(failure(Reason.UNRESPONSIVE), Ops.STATUS_FAILURE));
        assertEquals(Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED,
                Ops.getServerFailureStatus(failure(Reason.NOT_ACKNOWLEDGED), Ops.STATUS_FAILURE));
    }

    @Test
    public void aWrappedFailureIsStillFound() {
        // LocalServer and the session code wrap it before it reaches Ops
        Throwable wrapped = new IllegalStateException(new IOException("Could not create session",
                failure(Reason.UNRESPONSIVE)));

        assertEquals(Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE,
                Ops.getServerFailureStatus(wrapped, Ops.STATUS_FAILURE));
    }

    @Test
    public void otherFailuresKeepTheCallersStatus() {
        // A refused connection still offers the pair/connect chooser after auto-connect
        assertEquals(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED, Ops.getServerFailureStatus(
                new IOException("Connection refused"), Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED));
        assertEquals(Ops.STATUS_FAILURE,
                Ops.getServerFailureStatus(new RuntimeException(), Ops.STATUS_FAILURE));
    }

    @Test
    public void aLoopingCauseChainEnds() {
        Exception first = new Exception("first");
        Exception second = new Exception("second", first);
        first.initCause(second);

        assertNull(ServerConnectionFailure.find(first));
    }

    @Test
    public void onlyServerFailuresHaveAMessageAndEachOneDiffers() {
        Set<Integer> messages = new HashSet<>();
        for (int status : new int[]{Ops.STATUS_FAILURE_SERVER_START, Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE,
                Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED}) {
            int message = Ops.getServerFailureMessage(status);
            assertNotEquals(0, message);
            messages.add(message);
        }
        assertEquals(3, messages.size());
        assertEquals(0, Ops.getServerFailureMessage(Ops.STATUS_FAILURE));
        assertEquals(0, Ops.getServerFailureMessage(Ops.STATUS_SUCCESS));
    }

    private static ServerConnectionFailure failure(Reason reason) {
        return new ServerConnectionFailure(reason, reason.name(), null);
    }
}
