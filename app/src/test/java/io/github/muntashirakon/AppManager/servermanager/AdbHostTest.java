// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBuild;

import java.net.InetAddress;
import java.net.ServerSocket;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Wireless debugging on an emulator never connected: the connect went to the computer (10.0.2.2)
 * while the port Wireless debugging opened only exists on the emulator itself.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbHostTest {
    private static final String LOOPBACK = "127.0.0.1";

    @Test
    public void onAnEmulatorAPortOpenOnTheDeviceIsReachedThere() throws Exception {
        ShadowBuild.setHardware("ranchu");
        Context context = ContextUtils.getContext();
        try (ServerSocket wirelessDebugging = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            assertEquals(LOOPBACK, ServerConfig.getAdbHost(context, wirelessDebugging.getLocalPort()));
        }
    }

    @Test
    public void onAnEmulatorAPortNothingAnswersOnGoesThroughTheComputer() throws Exception {
        // ADB over TCP on an emulator without adb tcpip: the computer forwards its port to adbd
        ShadowBuild.setHardware("ranchu");
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            closedPort = socket.getLocalPort();
        }
        assertEquals(ServerConfig.EMULATOR_HOST_LOOPBACK,
                ServerConfig.getAdbHost(ContextUtils.getContext(), closedPort));
    }

    @Test
    public void aPhoneAlwaysUsesItsOwnLoopback() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            closedPort = socket.getLocalPort();
        }
        assertEquals(LOOPBACK, ServerConfig.chooseAdbHost(false, closedPort));
        assertEquals(LOOPBACK, ServerConfig.getAdbPairingHost());
    }
}
