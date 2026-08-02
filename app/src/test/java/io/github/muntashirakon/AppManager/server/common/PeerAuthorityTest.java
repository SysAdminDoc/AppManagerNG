// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The privileged channel grants {@code shell.exec} in a root or shell-uid process, and its only
 * historical authenticator was a bearer token. These cases cover the independent property — the
 * peer's owning uid — that a peer cannot choose for itself.
 *
 * @see <a href="../../../../../../../../../../docs/policy/privileged-channel-peer-authority.md">
 * Who the privileged command channel trusts</a>
 */
public class PeerAuthorityTest {
    private static final int ROOT = 0;
    private static final int SHELL = 2000;
    private static final int APP_ID = 10123;
    private static final int APP_UID_USER_0 = 10123;
    private static final int APP_UID_USER_10 = 1010123;
    private static final int OTHER_APP_UID = 10456;

    /** A real /proc/net/tcp table: a listener, our accepted connection, and an unrelated one. */
    private static final List<String> PROC_NET_TCP = Arrays.asList(
            "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode",
            "   0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 41001 1 0000000000000000 100 0 0 10 0",
            "   1: 0100007F:1F90 0100007F:C001 01 00000000:00000000 00:00000000 00000000 10123        0 41002 1 0000000000000000 20 4 30 10 -1",
            "   2: 0100007F:2328 0100007F:C002 01 00000000:00000000 00:00000000 00000000 10456        0 41003 1 0000000000000000 20 4 30 10 -1");

    private static final int LISTEN_PORT = 0x1F90; // 8080
    private static final int PEER_PORT = 0xC001;   // 49153

    @Test
    public void appIdStripsTheUserId() {
        assertEquals(APP_ID, PeerAuthority.appIdOf(APP_UID_USER_0));
        assertEquals(APP_ID, PeerAuthority.appIdOf(APP_UID_USER_10));
        assertEquals(ROOT, PeerAuthority.appIdOf(ROOT));
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.appIdOf(-5));
    }

    @Test
    public void theAppItselfIsAuthorized() {
        assertTrue(PeerAuthority.isAuthorizedPeer(APP_UID_USER_0, APP_ID, ROOT));
        // Same app running in a secondary user or work profile.
        assertTrue(PeerAuthority.isAuthorizedPeer(APP_UID_USER_10, APP_ID, ROOT));
        // The server talking to itself.
        assertTrue(PeerAuthority.isAuthorizedPeer(ROOT, APP_ID, ROOT));
        assertTrue(PeerAuthority.isAuthorizedPeer(SHELL, APP_ID, SHELL));
    }

    @Test
    public void anotherAppHoldingAValidTokenIsStillRefused() {
        // This is the case the token alone could not decide.
        assertFalse(PeerAuthority.isAuthorizedPeer(OTHER_APP_UID, APP_ID, ROOT));
        assertFalse(PeerAuthority.isAuthorizedPeer(1010456, APP_ID, ROOT));
        // A peer running as root is not the app, and is refused on this channel even though it
        // could reach the token by other means.
        assertFalse(PeerAuthority.isAuthorizedPeer(ROOT, APP_ID, SHELL));
    }

    @Test
    public void anUndecidablePeerFallsBackToTheToken() {
        // Documented, and logged loudly by the caller: a check that cannot run must not read as
        // a check that passed.
        assertTrue(PeerAuthority.isAuthorizedPeer(PeerAuthority.UID_UNKNOWN, APP_ID, ROOT));
        assertTrue(PeerAuthority.isAuthorizedPeer(OTHER_APP_UID, PeerAuthority.UID_UNKNOWN, ROOT));
    }

    @Test
    public void theConnectionIsFoundByItsPortPair() {
        assertEquals(APP_UID_USER_0, PeerAuthority.findPeerUid(PROC_NET_TCP, LISTEN_PORT, PEER_PORT));
    }

    @Test
    public void aDifferentConnectionIsNotMistakenForOurs() {
        // Same listener, different peer port.
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(PROC_NET_TCP, LISTEN_PORT, 0xC099));
        // Different listener, same peer port.
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(PROC_NET_TCP, 0x9999, PEER_PORT));
        // The other app's connection is only found by its own port pair.
        assertEquals(OTHER_APP_UID, PeerAuthority.findPeerUid(PROC_NET_TCP, 0x2328, 0xC002));
    }

    @Test
    public void theListeningRowIsNeverMatched() {
        // Row 0 has a rem_address port of 0, which no accepted socket reports.
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(PROC_NET_TCP, LISTEN_PORT, 0));
    }

    @Test
    public void anIpv6TableIsParsedTheSameWay() {
        List<String> tcp6 = Arrays.asList(
                "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode",
                "   0: 00000000000000000000000001000000:1F90 00000000000000000000000001000000:C001 01 00000000:00000000 00:00000000 00000000 10123        0 51002 1 0000000000000000 20 4 30 10 -1");
        assertEquals(APP_UID_USER_0, PeerAuthority.findPeerUid(tcp6, LISTEN_PORT, PEER_PORT));
    }

    @Test
    public void unusableTablesYieldUnknownRatherThanThrowing() {
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(null, LISTEN_PORT, PEER_PORT));
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(Collections.emptyList(), LISTEN_PORT, PEER_PORT));
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(PROC_NET_TCP, 0, PEER_PORT));
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(PROC_NET_TCP, LISTEN_PORT, -1));
        // Truncated, header-only, blank and malformed rows are skipped, not indexed into.
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(
                Arrays.asList("", "   1: 0100007F:1F90 0100007F:C001", null, "garbage"),
                LISTEN_PORT, PEER_PORT));
        // A row whose uid column is not a number.
        assertEquals(PeerAuthority.UID_UNKNOWN, PeerAuthority.findPeerUid(Collections.singletonList(
                        "   1: 0100007F:1F90 0100007F:C001 01 00000000:00000000 00:00000000 00000000 nobody 0 41002 1"),
                LISTEN_PORT, PEER_PORT));
    }

    @Test
    public void tokenComparisonIsLengthIndependent() {
        assertTrue(DataTransmission.constantTimeEquals("s3cr3t-token", "s3cr3t-token"));
        assertFalse(DataTransmission.constantTimeEquals("s3cr3t-token", "s3cr3t-toke"));
        assertFalse(DataTransmission.constantTimeEquals("s3cr3t-token", "s3cr3t-tokenn"));
        assertFalse(DataTransmission.constantTimeEquals("s3cr3t-token", "s"));
        assertFalse(DataTransmission.constantTimeEquals("s3cr3t-token", ""));
        assertFalse(DataTransmission.constantTimeEquals("s3cr3t-token", null));
        assertFalse(DataTransmission.constantTimeEquals("", ""));
    }
}
