// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The privileged command channel runs any command with root or shell rights, so it must listen on loopback only.
 * The trust model is in docs/policy/privileged-channel-peer-authority.md.
 */
public class ServerLoopbackBindTest {
    @Test
    public void theCommandChannelListensOnLoopbackOnly() throws IOException {
        try (Server.NetSocketServerImpl server = new Server.NetSocketServerImpl(0)) {
            InetAddress bound = server.getBoundAddress();
            assertFalse("bound to every address", bound.isAnyLocalAddress());
            assertTrue("bound to " + bound, bound.isLoopbackAddress());
            // The client and the liveness check both dial 127.0.0.1, not ::1
            assertEquals("127.0.0.1", bound.getHostAddress());
        }
    }

    @Test
    public void noOtherAddressOfThisMachineReachesIt() throws IOException {
        List<InetAddress> others = otherIpv4Addresses();
        Assume.assumeFalse("this machine has no address besides loopback", others.isEmpty());
        try (Server.NetSocketServerImpl server = new Server.NetSocketServerImpl(0)) {
            for (InetAddress address : others) {
                if (connects(new InetSocketAddress(address, server.getPort()))) {
                    fail("reached through " + address.getHostAddress());
                }
            }
            assertTrue("loopback still reaches it",
                    connects(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), server.getPort())));
        }
    }

    private static boolean connects(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(address, 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<InetAddress> otherIpv4Addresses() throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    addresses.add(address);
                }
            }
        }
        return addresses;
    }
}
