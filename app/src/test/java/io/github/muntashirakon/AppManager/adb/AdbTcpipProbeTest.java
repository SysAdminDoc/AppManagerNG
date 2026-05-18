// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class AdbTcpipProbeTest {
    @Test
    public void isPortReachableDetectsListeningLoopbackSocket() throws Exception {
        InetAddress loopback = InetAddress.getByName(AdbTcpipProbe.LOOPBACK_HOST);
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            Thread acceptThread = new Thread(() -> {
                try (Socket ignored = server.accept()) {
                    // Accept once so the probe's connect can complete cleanly.
                } catch (IOException ignored) {
                }
            }, "adb-tcpip-probe-test");
            acceptThread.start();

            assertTrue(AdbTcpipProbe.isPortReachable(AdbTcpipProbe.LOOPBACK_HOST,
                    server.getLocalPort(), 1000));
            acceptThread.join(1000);
        }
    }

    @Test
    public void isPortReachableReturnsFalseForClosedLoopbackSocket() throws Exception {
        InetAddress loopback = InetAddress.getByName(AdbTcpipProbe.LOOPBACK_HOST);
        int closedPort;
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            closedPort = server.getLocalPort();
        }

        assertFalse(AdbTcpipProbe.isPortReachable(AdbTcpipProbe.LOOPBACK_HOST, closedPort, 100));
    }

    @Test
    public void defaultTcpipPortMatchesPersistentAdbConvention() {
        assertTrue(AdbTcpipProbe.DEFAULT_TCPIP_PORT == 5555);
    }
}
