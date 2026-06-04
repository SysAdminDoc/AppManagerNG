// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdbPairingRequestTest {
    @Test
    public void createAcceptsValidPortAndTrimmedSixDigitCode() {
        AdbPairingRequest request = AdbPairingRequest.create(37123, " 123456 ");

        assertNotNull(request);
        assertEquals(37123, request.getPort());
        assertEquals("123456", request.getPairingCode());
    }

    @Test
    public void createRejectsInvalidPort() {
        assertNull(AdbPairingRequest.create(0, "123456"));
        assertNull(AdbPairingRequest.create(65536, "123456"));
    }

    @Test
    public void createRejectsNonSixDigitCode() {
        assertNull(AdbPairingRequest.create(37123, "12345"));
        assertNull(AdbPairingRequest.create(37123, "1234567"));
        assertNull(AdbPairingRequest.create(37123, "12345a"));
    }

    @Test
    public void stateAllowsSubmitOnlyForPortBackedEditableStates() {
        assertTrue(AdbPairingState.portFound(37123).canSubmitCode("123456"));
        assertTrue(AdbPairingState.failed(37123).canSubmitCode("123456"));
        assertFalse(AdbPairingState.searching().canSubmitCode("123456"));
        assertFalse(AdbPairingState.pairing(37123).canSubmitCode("123456"));
        assertFalse(AdbPairingState.cancelled().canSubmitCode("123456"));
    }

    @Test
    public void retryWaitStopsForCancelledSessionsEvenWhenServiceStillRuns() {
        assertTrue(AdbPairingSession.shouldWaitForRetry(AdbPairingState.failed(37123), true));
        assertFalse(AdbPairingSession.shouldWaitForRetry(AdbPairingState.failed(37123), false));
        assertFalse(AdbPairingSession.shouldWaitForRetry(AdbPairingState.cancelled(), true));
    }
}
