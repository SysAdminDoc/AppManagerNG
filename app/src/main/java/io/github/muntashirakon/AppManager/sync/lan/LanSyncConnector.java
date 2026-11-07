// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;

/**
 * One attempt to connect to a sharing device. {@link #cancel()} can be called from any thread
 * and ends the attempt even while it waits for the other side to allow it.
 */
public final class LanSyncConnector {
    public static final int CONNECT_TIMEOUT_MS = 10_000;

    @Nullable
    private volatile Socket mSocket;
    private volatile boolean mCancelled;

    @WorkerThread
    @NonNull
    public SyncClientSession connect(@NonNull InetAddress address, int port, @NonNull String clientName,
                                     @NonNull LocalIdentity identity, @NonNull SecureRandom random,
                                     @Nullable SyncClientSession.PairingListener listener) throws IOException {
        if (!LanAddresses.isLocal(address)) {
            throw new IOException("Only devices on the local network can be reached.");
        }
        Socket socket = new Socket();
        mSocket = socket;
        try {
            if (mCancelled) {
                throw new IOException("Cancelled");
            }
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            FrameIo io = new FrameIo(socket.getInputStream(), socket.getOutputStream());
            return SyncClientSession.connect(io, socket::setSoTimeout, clientName, identity, random, listener);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    public void cancel() {
        mCancelled = true;
        closeQuietly(mSocket);
    }

    private static void closeQuietly(@Nullable Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
