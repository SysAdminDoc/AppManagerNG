// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.ConnectException;
import java.net.ServerSocket;

/**
 * libadb's connect has no timeout, so a host that drops the connection held the first launch on
 * "Initializing..." until TCP gave up, about two and a half minutes on an emulator.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbConnectReachabilityTest {
    /** TEST-NET-1 is reserved and never answers, the same silence 10.0.2.2 gave. */
    private static final String SILENT_HOST = "192.0.2.1";

    @Test
    public void aHostThatNeverAnswersGivesUpWithinSeconds() {
        long startedAt = System.nanoTime();

        assertThrows(ConnectException.class, () -> AdbConnectionManager.ensureReachable(SILENT_HOST, 5555));

        long seconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
        assertTrue("took " + seconds + " seconds", seconds < 10);
    }

    @Test
    public void aRefusedPortStillFailsAsAConnectionProblem() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThrows(ConnectException.class, () -> AdbConnectionManager.ensureReachable("127.0.0.1", closedPort));
    }

    @Test
    public void aListeningPortPasses() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            AdbConnectionManager.ensureReachable("127.0.0.1", socket.getLocalPort());
        }
    }
}
