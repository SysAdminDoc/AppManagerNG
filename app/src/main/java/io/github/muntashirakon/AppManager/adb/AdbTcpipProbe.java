// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class AdbTcpipProbe {
    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int DEFAULT_TCPIP_PORT = 5555;
    public static final int DEFAULT_TIMEOUT_MILLIS = 400;

    private AdbTcpipProbe() {
    }

    @WorkerThread
    public static boolean isDefaultTcpipSessionReachable() {
        return isPortReachable(LOOPBACK_HOST, DEFAULT_TCPIP_PORT, DEFAULT_TIMEOUT_MILLIS);
    }

    @WorkerThread
    public static boolean isPortReachable(@NonNull String host,
                                          @IntRange(from = 0, to = 65535) int port,
                                          @IntRange(from = 1) int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
