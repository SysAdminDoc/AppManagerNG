// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LanAddressesTest {
    @Test
    public void localAddressesAreAccepted() throws UnknownHostException {
        for (String address : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.9", "172.31.255.1", "192.168.0.1",
                "169.254.10.10", "::1", "fe80::1", "fd00::1", "fc00::5"}) {
            assertTrue(address, LanAddresses.isLocal(InetAddress.getByName(address)));
        }
    }

    @Test
    public void otherAddressesAreRefused() throws UnknownHostException {
        for (String address : new String[]{"8.8.8.8", "172.32.0.1", "100.64.0.1", "192.169.0.1", "2001:db8::1",
                "2606:4700::1111", "fe00::1", "0.0.0.0", "224.0.0.251"}) {
            assertFalse(address, LanAddresses.isLocal(InetAddress.getByName(address)));
        }
        assertFalse(LanAddresses.isLocal(null));
    }

    @Test
    public void parsesHostAndPort() {
        LanAddresses.HostPort v4 = LanAddresses.parse(" 192.168.1.20:4000 ");
        assertNotNull(v4);
        assertEquals("192.168.1.20", v4.host);
        assertEquals(4000, v4.port);

        LanAddresses.HostPort bracketed = LanAddresses.parse("[fe80::1%wlan0]:65535");
        assertNotNull(bracketed);
        assertEquals("fe80::1%wlan0", bracketed.host);
        assertEquals(65535, bracketed.port);

        LanAddresses.HostPort bare = LanAddresses.parse("fd00::1:8080");
        assertNotNull(bare);
        assertEquals("fd00::1", bare.host);
        assertEquals(8080, bare.port);
    }

    @Test
    public void refusesMalformedInputAndHostNames() {
        for (String text : new String[]{"", "192.168.1.20", "192.168.1.20:", ":4000", "192.168.1.20:0",
                "192.168.1.20:65536", "192.168.1.20:123456", "192.168.1.20:-1", "192.168.1.20:4o00",
                "[fe80::1]4000", "[fe80::1", "printer.local:4000", "example.com:443", "256.1.1.1:80",
                "1.2.3:80", "1.2.3.4.5:80", "1..2.3:80", "1234.1.1.1:80", "1.2.3.4%eth0:80"}) {
            assertNull(text, LanAddresses.parse(text));
        }
    }

    @Test
    public void numericAddressesConvertWithoutALookup() throws UnknownHostException {
        assertEquals(InetAddress.getByName("192.168.1.20"), LanAddresses.toAddress("192.168.1.20"));
        assertEquals(InetAddress.getByName("fd00::1"), LanAddresses.toAddress("fd00::1"));
        assertEquals(InetAddress.getByName("fe80::1:2:3:4"), LanAddresses.toAddress("FE80:0::1:2:3:4"));
        assertEquals(InetAddress.getByName("1:2:3:4:5:6:7:8"), LanAddresses.toAddress("1:2:3:4:5:6:7:8"));
        assertEquals(InetAddress.getByName("::"), LanAddresses.toAddress("::"));
        assertEquals(InetAddress.getByName("::1"), LanAddresses.toAddress("::1"));
        assertEquals(InetAddress.getByName("1::"), LanAddresses.toAddress("1::"));
        for (String text : new String[]{"fd00:", "1::2::3", ":::", ":1::", "12345::1", "1:2:3:4:5:6:7",
                "1:2:3:4:5:6:7:8:9", "1:2:3:4::5:6:7:8", "::ffff:1.2.3.4", "g::1", "printer.local",
                "192.168.1.20%wlan0", "fe80::1%", "fe80::1%no/such"}) {
            assertNull(text, LanAddresses.toAddress(text));
        }
    }

    @Test
    public void ipLiteralCheckAcceptsOnlyAsciiDigits() {
        assertTrue(LanAddresses.isIpLiteral("10.0.0.1"));
        assertTrue(LanAddresses.isIpLiteral("fe80::abcd%wlan0"));
        // Arabic-Indic digit one is a digit to Character.isDigit but would be sent to a resolver
        assertFalse(LanAddresses.isIpLiteral("10.0.0." + (char) 0x0661));
        assertFalse(LanAddresses.isIpLiteral("fe80::" + (char) 0x0661));
        assertFalse(LanAddresses.isIpLiteral("%wlan0"));
        assertFalse(LanAddresses.isIpLiteral("localhost"));
    }

    @Test
    public void ownAddressesAreTheOnesAnotherPhoneOnTheNetworkCanReach() throws UnknownHostException {
        List<LanAddresses.Candidate> candidates = Arrays.asList(
                candidate("lo", "127.0.0.1", true),
                candidate("rmnet_data0", "10.45.0.3", true),
                candidate("eth0", "192.168.50.2", true),
                candidate("wlan0", "fe80::1", true),
                candidate("wlan0", "169.254.3.4", true),
                candidate("wlan0", "192.168.1.23", true),
                candidate("tun0", "10.8.0.2", true),
                candidate("swlan0", "192.168.43.1", true),
                candidate("wlan1", "100.64.12.1", true),
                candidate("wlan2", "172.20.0.5", false),
                candidate("wlan0", "192.168.1.23", true));

        // Wi-Fi first, then a hotspot, then a cable; no loopback, link-local, mobile data, VPN,
        // public or carrier addresses, IPv6, or interfaces that are down
        assertEquals(Arrays.asList("192.168.1.23", "192.168.43.1", "192.168.50.2"),
                LanAddresses.usableIpv4(candidates));
    }

    @Test
    public void noWifiMeansNoOwnAddress() throws UnknownHostException {
        assertTrue(LanAddresses.usableIpv4(Collections.<LanAddresses.Candidate>emptyList()).isEmpty());
        assertTrue(LanAddresses.usableIpv4(Arrays.asList(candidate("lo", "127.0.0.1", true),
                candidate("rmnet0", "10.0.0.9", true), candidate("wlan0", "fe80::2", true))).isEmpty());
    }

    @Test
    public void ownAddressesShowThePortWhenThereIsOne() {
        List<String> two = Arrays.asList("192.168.1.23", "10.0.0.7");

        assertEquals("192.168.1.23:5137, 10.0.0.7:5137", LanAddresses.describeOwn(two, 5137));
        assertEquals("192.168.1.23, 10.0.0.7", LanAddresses.describeOwn(two, 0));
        assertEquals("192.168.1.23:5137", LanAddresses.describeOwn(two.subList(0, 1), 5137));
        assertEquals("", LanAddresses.describeOwn(Collections.<String>emptyList(), 5137));
        // What a copy puts on the clipboard, which the other phone's field takes as typed
        assertEquals("192.168.1.23:5137", LanAddresses.withPort("192.168.1.23", 5137));
        assertEquals("192.168.1.23", LanAddresses.withPort("192.168.1.23", 0));
        LanAddresses.HostPort typed = LanAddresses.parse(LanAddresses.withPort("192.168.1.23", 5137));
        assertNotNull(typed);
        assertEquals(5137, typed.port);
    }

    private static LanAddresses.Candidate candidate(String name, String address, boolean up)
            throws UnknownHostException {
        return new LanAddresses.Candidate(name, InetAddress.getByName(address), up);
    }
}
