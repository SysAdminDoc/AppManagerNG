// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local sync only talks to devices on the local network. These checks keep both the sharing side
 * and a typed-in address from reaching anything else.
 */
public final class LanAddresses {
    public static final class HostPort {
        @NonNull
        public final String host;
        public final int port;

        HostPort(@NonNull String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /**
     * One address of one network interface on this device.
     */
    static final class Candidate {
        @NonNull
        final String interfaceName;
        @NonNull
        final InetAddress address;
        final boolean up;

        Candidate(@NonNull String interfaceName, @NonNull InetAddress address, boolean up) {
            this.interfaceName = interfaceName;
            this.address = address;
            this.up = up;
        }
    }

    private LanAddresses() {
    }

    // Mobile data, VPN and tethering-internal interfaces: another phone on the Wi-Fi can't reach these
    private static final String[] UNREACHABLE_PREFIXES = {
            "lo", "rmnet", "ccmni", "pdp", "v4-", "clat", "tun", "ppp", "ipsec", "dummy"
    };

    /**
     * This device's IPv4 addresses that another device on the same network could type in, Wi-Fi
     * first. Empty when there is no such network, like on mobile data alone.
     */
    @WorkerThread
    @NonNull
    public static List<String> ownIpv4Addresses() {
        List<Candidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return Collections.emptyList();
            }
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                boolean up = networkInterface.isUp();
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    candidates.add(new Candidate(networkInterface.getName(), address, up));
                }
            }
        } catch (SocketException | RuntimeException e) {
            return Collections.emptyList();
        }
        return usableIpv4(candidates);
    }

    /**
     * Private IPv4 addresses (10/8, 172.16/12, 192.168/16) of interfaces that are up, without
     * duplicates, Wi-Fi first, then a hotspot, then a cable, then anything else.
     */
    @NonNull
    static List<String> usableIpv4(@NonNull List<Candidate> candidates) {
        List<Candidate> usable = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!candidate.up || !(candidate.address instanceof Inet4Address)
                    || !candidate.address.isSiteLocalAddress() || !isReachableInterface(candidate.interfaceName)) {
                continue;
            }
            if (seen.add(candidate.address.getHostAddress())) {
                usable.add(candidate);
            }
        }
        // A stable sort keeps the system's order within one kind
        Collections.sort(usable, (a, b) -> Integer.compare(rank(a.interfaceName), rank(b.interfaceName)));
        List<String> addresses = new ArrayList<>(usable.size());
        for (Candidate candidate : usable) {
            addresses.add(candidate.address.getHostAddress());
        }
        return addresses;
    }

    private static boolean isReachableInterface(@NonNull String name) {
        for (String prefix : UNREACHABLE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static int rank(@NonNull String name) {
        if (name.startsWith("wlan")) {
            return 0;
        }
        if (name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap")) {
            return 1;
        }
        if (name.startsWith("eth")) {
            return 2;
        }
        return 3;
    }

    /**
     * The addresses as one line, each with the port when there is one ({@code port > 0}).
     */
    @NonNull
    public static String describeOwn(@NonNull List<String> addresses, int port) {
        StringBuilder sb = new StringBuilder();
        for (String address : addresses) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(withPort(address, port));
        }
        return sb.toString();
    }

    /**
     * {@code address:port}, the form the address field takes, or the bare address without a port.
     */
    @NonNull
    public static String withPort(@NonNull String address, int port) {
        return port > 0 ? address + ":" + port : address;
    }

    /**
     * Private IPv4 ranges, link-local, loopback, and IPv6 unique local addresses.
     */
    public static boolean isLocal(@Nullable InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    /**
     * Parses {@code host:port}, {@code [ipv6]:port} or a bare IPv6 address followed by
     * {@code :port}. Returns {@code null} when the text is not one of those.
     */
    @Nullable
    public static HostPort parse(@NonNull String text) {
        String s = text.trim();
        String host;
        String port;
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close < 0 || close + 1 >= s.length() || s.charAt(close + 1) != ':') {
                return null;
            }
            host = s.substring(1, close);
            port = s.substring(close + 2);
        } else {
            int colon = s.lastIndexOf(':');
            if (colon <= 0) {
                return null;
            }
            host = s.substring(0, colon);
            port = s.substring(colon + 1);
        }
        if (host.isEmpty() || port.isEmpty() || port.length() > 5) {
            return null;
        }
        for (int i = 0; i < port.length(); ++i) {
            char c = port.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        int value = Integer.parseInt(port);
        if (value < 1 || value > 65535 || !isIpLiteral(host)) {
            return null;
        }
        return new HostPort(host, value);
    }

    /**
     * Turns a numeric address from {@link #parse(String)} into an {@link InetAddress} without any
     * name lookup. Android sends anything it cannot read as a number to the DNS resolver, so the
     * bytes are built here instead. Returns {@code null} for anything else, or for an IPv6 zone
     * that names no network interface.
     */
    @Nullable
    public static InetAddress toAddress(@NonNull String host) {
        int percent = host.indexOf('%');
        String literal = percent >= 0 ? host.substring(0, percent) : host;
        try {
            byte[] v4 = parseIpv4(literal);
            if (v4 != null) {
                return percent >= 0 ? null : InetAddress.getByAddress(v4);
            }
            byte[] v6 = parseIpv6(literal);
            if (v6 == null) {
                return null;
            }
            if (percent < 0) {
                return InetAddress.getByAddress(v6);
            }
            String zone = host.substring(percent + 1);
            NetworkInterface networkInterface = isValidZone(zone) ? NetworkInterface.getByName(zone) : null;
            return networkInterface != null ? Inet6Address.getByAddress(null, v6, networkInterface) : null;
        } catch (UnknownHostException | SocketException e) {
            return null;
        }
    }

    /**
     * True for a numeric IPv4 or IPv6 address (IPv6 optionally with a zone), which resolves
     * without asking a DNS server.
     */
    static boolean isIpLiteral(@NonNull String host) {
        int percent = host.indexOf('%');
        if (percent >= 0) {
            return isValidZone(host.substring(percent + 1)) && parseIpv6(host.substring(0, percent)) != null;
        }
        return parseIpv4(host) != null || parseIpv6(host) != null;
    }

    /**
     * Strict dotted quad: four decimal parts of one to three ASCII digits, each at most 255.
     */
    @Nullable
    static byte[] parseIpv4(@NonNull String s) {
        byte[] out = new byte[4];
        int parts = 0;
        int digits = 0;
        int value = 0;
        for (int i = 0; i <= s.length(); ++i) {
            char c = i < s.length() ? s.charAt(i) : '.';
            if (c == '.') {
                if (digits == 0 || value > 255 || parts == 4) {
                    return null;
                }
                out[parts++] = (byte) value;
                digits = 0;
                value = 0;
            } else if (c >= '0' && c <= '9' && digits < 3) {
                value = value * 10 + (c - '0');
                ++digits;
            } else {
                return null;
            }
        }
        return parts == 4 ? out : null;
    }

    /**
     * Eight groups of one to four ASCII hex digits, with at most one {@code ::}. The embedded
     * IPv4 form is not accepted.
     */
    @Nullable
    static byte[] parseIpv6(@NonNull String s) {
        if (s.isEmpty() || s.length() > 39) {
            return null;
        }
        int gap = s.indexOf("::");
        if (gap >= 0 && s.indexOf("::", gap + 1) >= 0) {
            return null;
        }
        int[] head = groups(gap >= 0 ? s.substring(0, gap) : s);
        int[] tail = groups(gap >= 0 ? s.substring(gap + 2) : "");
        if (head == null || tail == null) {
            return null;
        }
        int total = head.length + tail.length;
        if (gap >= 0 ? total > 7 : total != 8) {
            return null;
        }
        byte[] out = new byte[16];
        for (int i = 0; i < head.length; ++i) {
            out[2 * i] = (byte) (head[i] >> 8);
            out[2 * i + 1] = (byte) head[i];
        }
        for (int i = 0; i < tail.length; ++i) {
            int group = 8 - tail.length + i;
            out[2 * group] = (byte) (tail[i] >> 8);
            out[2 * group + 1] = (byte) tail[i];
        }
        return out;
    }

    @Nullable
    private static int[] groups(@NonNull String part) {
        if (part.isEmpty()) {
            return new int[0];
        }
        String[] pieces = part.split(":", -1);
        int[] values = new int[pieces.length];
        for (int i = 0; i < pieces.length; ++i) {
            String piece = pieces[i];
            if (piece.isEmpty() || piece.length() > 4) {
                return null;
            }
            int value = 0;
            for (int j = 0; j < piece.length(); ++j) {
                char c = piece.charAt(j);
                int digit;
                if (c >= '0' && c <= '9') {
                    digit = c - '0';
                } else if (c >= 'a' && c <= 'f') {
                    digit = c - 'a' + 10;
                } else if (c >= 'A' && c <= 'F') {
                    digit = c - 'A' + 10;
                } else {
                    return null;
                }
                value = value * 16 + digit;
            }
            values[i] = value;
        }
        return values;
    }

    private static boolean isValidZone(@NonNull String zone) {
        if (zone.isEmpty() || zone.length() > 32) {
            return false;
        }
        for (int i = 0; i < zone.length(); ++i) {
            char c = zone.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
