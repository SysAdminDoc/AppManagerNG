// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Decides whether a peer that reached the privileged command channel is allowed to use it.
 * <p>
 * The channel's only historical authenticator was the handshake token: anything that could reach
 * the listening socket and present the token got {@code shell.exec} in the root or shell-uid
 * process. The token is device-local, but it is a bearer credential, and on the loopback path any
 * on-device process can attempt a connection. The peer's owning uid is an independent property
 * that the peer cannot choose, so it is checked as well.
 * <p>
 * The two transports learn the peer uid differently — {@code SO_PEERCRED} for a
 * {@code LocalSocket}, and the {@code /proc/net/tcp} table for the loopback socket — but they
 * share the decision made here. See {@code docs/security/privileged-channel-peer-authority.md}.
 */
public final class PeerAuthority {
    /** Returned when the peer's uid could not be established. */
    public static final int UID_UNKNOWN = -1;

    /** Android packs a user id into the uid; the app identity is what remains. */
    private static final int PER_USER_RANGE = 100_000;

    /** Column of the owning uid in a {@code /proc/net/tcp} row. */
    private static final int PROC_NET_UID_COLUMN = 7;

    private PeerAuthority() {
    }

    /**
     * The app identity carried by a uid, with the user id stripped off, so a client running in a
     * secondary user or a work profile is still recognised as the same app.
     */
    public static int appIdOf(int uid) {
        return uid < 0 ? UID_UNKNOWN : uid % PER_USER_RANGE;
    }

    /**
     * @param peerUid       Uid owning the connected peer, or {@link #UID_UNKNOWN}
     * @param expectedAppId App id the channel was started for, or {@link #UID_UNKNOWN} when it
     *                      could not be resolved
     * @param selfUid       Uid the privileged server itself runs as
     * @return {@code true} when the peer may proceed to the handshake
     */
    public static boolean isAuthorizedPeer(int peerUid, int expectedAppId, int selfUid) {
        if (peerUid == UID_UNKNOWN || expectedAppId == UID_UNKNOWN) {
            // Nothing to compare against. The token remains the only authenticator; the caller
            // logs this so it can never read as a check that quietly passed.
            return true;
        }
        if (peerUid == selfUid) {
            // The server talking to itself, e.g. a liveness probe from the same process.
            return true;
        }
        return appIdOf(peerUid) == expectedAppId;
    }

    /**
     * Find the uid owning a TCP connection by matching it in a {@code /proc/net/tcp} or
     * {@code /proc/net/tcp6} table. TCP carries no peer credentials of its own, so this table is
     * the only way the loopback path can learn who connected.
     * <p>
     * A row's addresses are {@code <hex-address>:<hex-port>} and each row is written from the
     * point of view of the socket that owns it. A loopback connection therefore has <em>two</em>
     * rows: ours, whose local port is the listening port and whose uid is our own, and the
     * peer's, whose local port is the peer's ephemeral port and whose uid is the one we are
     * after. Matching our own row would only ever report the uid we already run as — a check
     * that always passes. The peer's row is the one to find, so the ports are given from the
     * peer's point of view.
     *
     * @param procNetLines  Lines of the table, header included
     * @param peerPort      Peer's ephemeral port — the {@code local_address} port of its row
     * @param listeningPort Port we accepted on — the {@code rem_address} port of its row
     * @return The owning uid, or {@link #UID_UNKNOWN} when no row matches
     */
    public static int findPeerUid(@Nullable Iterable<String> procNetLines, int peerPort, int listeningPort) {
        if (procNetLines == null || peerPort <= 0 || listeningPort <= 0) {
            return UID_UNKNOWN;
        }
        for (String line : procNetLines) {
            if (line == null) {
                continue;
            }
            String[] columns = line.trim().split("\\s+");
            if (columns.length <= PROC_NET_UID_COLUMN) {
                // Header row, or a truncated read.
                continue;
            }
            if (portOf(columns[1]) != peerPort || portOf(columns[2]) != listeningPort) {
                continue;
            }
            try {
                return Integer.parseInt(columns[PROC_NET_UID_COLUMN]);
            } catch (NumberFormatException e) {
                return UID_UNKNOWN;
            }
        }
        return UID_UNKNOWN;
    }

    /**
     * Port half of a {@code <hex-address>:<hex-port>} field, or {@code -1} when unparsable.
     */
    private static int portOf(@NonNull String address) {
        int sep = address.lastIndexOf(':');
        if (sep < 0 || sep == address.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(address.substring(sep + 1), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
