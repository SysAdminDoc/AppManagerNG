// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.PeerAuthority;

// Copyright 2017 Zheng Li
class Server implements Closeable {
    @NonNull
    private final LifecycleAgent mLifecycleAgent;
    @NonNull
    private final IServer mServer;
    @NonNull
    private final String mToken;
    /**
     * App id the channel was started for, or {@link PeerAuthority#UID_UNKNOWN} when it could not
     * be resolved.
     */
    private final int mExpectedAppId;
    @Nullable
    private final DataTransmission.OnReceiveCallback mOnReceiveCallback;

    private DataTransmission mDataTransmission;
    private boolean mRunning = true;
    boolean mRunInBackground = false;

    /**
     * Constructor for starting a local server
     *
     * @param name              Socket address
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(String name, @NonNull String token, int expectedAppId, @NonNull LifecycleAgent lifecycleAgent,
           @Nullable DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        mToken = token;
        mExpectedAppId = expectedAppId;
        mLifecycleAgent = lifecycleAgent;
        mServer = new LocalServerImpl(name);
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Constructor for starting a local server
     *
     * @param port              Port number
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(int port, @NonNull String token, int expectedAppId, @NonNull LifecycleAgent lifecycleAgent,
           @Nullable DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        mToken = token;
        mExpectedAppId = expectedAppId;
        mLifecycleAgent = lifecycleAgent;
        mServer = new NetSocketServerImpl(port);
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Run the server
     *
     * @throws IOException When server has failed to shake hands or the connection cannot be made
     */
    void run() throws IOException, RuntimeException {
        while (mRunning) {
            try {
                // Allow only one client
                mServer.accept();
                // Establish who connected before the token is even read. The token is a bearer
                // credential; the peer's uid is a property it cannot choose.
                if (!authorizePeer()) {
                    mServer.closeConnection();
                    continue;
                }
                // Prepare input and output streams for data interchange
                mDataTransmission = new DataTransmission(mServer.getOutputStream(), mServer.getInputStream(),
                        mOnReceiveCallback);
                // Handshake: check if tokens matched
                mDataTransmission.shakeHands(mToken, DataTransmission.Role.Server);
                // Send broadcast message to the system that the server has connected
                mLifecycleAgent.onConnected();
                // Handle the data received initially from the client
                mDataTransmission.handleReceive();
            } catch (DataTransmission.ProtocolVersionException e) {
                FLog.log(e);
                throw e;
            } catch (IOException e) {
                FLog.log(e);
                FLog.log("Run in background: " + mRunInBackground);
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected();
                // Throw exception only when run in background is not requested
                if (!mRunInBackground) {
                    mRunning = false;
                    throw e;
                }
            } catch (RuntimeException e) {
                FLog.log(e);
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected();
                // Re-throw the exception
                mRunInBackground = false;
                mRunning = false;
                throw e;
            }
        }
    }

    /**
     * @return {@code true} when the connected peer may proceed to the handshake
     */
    private boolean authorizePeer() {
        int peerUid = mServer.getPeerUid();
        int selfUid = Process.myUid();
        if (peerUid == PeerAuthority.UID_UNKNOWN || mExpectedAppId == PeerAuthority.UID_UNKNOWN) {
            // Say so explicitly: a check that cannot run must never read like a check that passed.
            FLog.log("Server: peer identity unavailable (peer uid " + peerUid + ", expected app id "
                    + mExpectedAppId + "); the handshake token is the only authenticator.");
            return true;
        }
        if (PeerAuthority.isAuthorizedPeer(peerUid, mExpectedAppId, selfUid)) {
            return true;
        }
        FLog.log("Server: refusing connection from uid " + peerUid + "; expected app id "
                + mExpectedAppId + ".");
        return false;
    }

    public void sendResult(byte[] bytes) throws IOException {
        if (mRunning && mDataTransmission != null) {
            LifecycleAgent.sServerInfo.txBytes += bytes.length;
            mDataTransmission.sendMessage(bytes);
        }
    }

    @Override
    public void close() throws IOException {
        mRunning = false;
        if (mDataTransmission != null) {
            mDataTransmission.close();
        }
        mServer.close();
    }

    private interface IServer extends Closeable {
        InputStream getInputStream() throws IOException;

        OutputStream getOutputStream() throws IOException;

        void accept() throws IOException;

        /**
         * @return Uid owning the currently accepted peer, or {@link PeerAuthority#UID_UNKNOWN}
         */
        int getPeerUid();

        /** Drop the accepted connection, keeping the listener open. */
        void closeConnection();

        @Override
        void close() throws IOException;
    }

    private static class LocalServerImpl implements IServer {
        private final LocalServerSocket mServerSocket;
        private LocalSocket mLocalSocket;

        public LocalServerImpl(String name) throws IOException {
            mServerSocket = new LocalServerSocket(name);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return mLocalSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return mLocalSocket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            mLocalSocket = mServerSocket.accept();
        }

        @Override
        public int getPeerUid() {
            // A unix domain socket carries the peer's credentials with it.
            try {
                Credentials credentials = mLocalSocket.getPeerCredentials();
                return credentials != null ? credentials.getUid() : PeerAuthority.UID_UNKNOWN;
            } catch (IOException | RuntimeException e) {
                FLog.log(e);
                return PeerAuthority.UID_UNKNOWN;
            }
        }

        @Override
        public void closeConnection() {
            try {
                if (mLocalSocket != null) {
                    mLocalSocket.close();
                    mLocalSocket = null;
                }
            } catch (IOException | RuntimeException e) {
                FLog.log(e);
            }
        }

        @Override
        public void close() throws IOException {
            closeConnection();
            mServerSocket.close();
        }
    }

    private static class NetSocketServerImpl implements IServer {
        /** IPv4 and IPv6 connection tables; a loopback peer appears in one of them. */
        private static final String[] PROC_NET_TCP_TABLES = {"/proc/net/tcp", "/proc/net/tcp6"};

        private final ServerSocket mServerSocket;
        private Socket mSocket;

        public NetSocketServerImpl(int port) throws IOException {
            // Bind to the IPv4 loopback only. This is a privileged command
            // channel (TYPE_SHELL runs arbitrary commands as the root/ADB-shell
            // uid) whose sole authenticator is a handshake token; the client
            // always connects via 127.0.0.1 (ServerConfig.getLocalServerHost).
            // The bare `new ServerSocket(port)` bound the wildcard address
            // (0.0.0.0), exposing the channel to every interface — reachable
            // over the LAN whenever the device runs in ADB-over-TCP/root port
            // mode. Match the client's loopback target and the liveness probe
            // in LocalServer.alive(); keep the default backlog (50).
            mServerSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return mSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return mSocket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            mSocket = mServerSocket.accept();
        }

        @Override
        public int getPeerUid() {
            // TCP carries no peer credentials, so the kernel's connection table is the only
            // place the owner of a loopback connection is recorded.
            if (mSocket == null) {
                return PeerAuthority.UID_UNKNOWN;
            }
            int localPort = mSocket.getLocalPort();
            int remotePort = mSocket.getPort();
            for (String table : PROC_NET_TCP_TABLES) {
                int uid = PeerAuthority.findPeerUid(readLines(table), localPort, remotePort);
                if (uid != PeerAuthority.UID_UNKNOWN) {
                    return uid;
                }
            }
            return PeerAuthority.UID_UNKNOWN;
        }

        @Nullable
        private static List<String> readLines(String path) {
            File file = new File(path);
            if (!file.canRead()) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException | RuntimeException e) {
                FLog.log(e);
                return null;
            }
            return lines;
        }

        @Override
        public void closeConnection() {
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (IOException | RuntimeException e) {
                FLog.log(e);
            }
        }

        @Override
        public void close() throws IOException {
            closeConnection();
            mServerSocket.close();
        }
    }
}
