// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

/**
 * The sharing side of Local sync. Listens on the port it had last time (or any free one),
 * announces itself over NSD, and serves one paired client at a time. It only runs while the
 * Local sync screen is open with sharing on:
 * {@link #stop()} closes every socket, withdraws the announcement and turns down any pending
 * pairing request.
 */
public final class LanSyncHost {
    public static final String SERVICE_TYPE = "_amng-sync._tcp";
    public static final String TXT_PROTOCOL = "v";
    public static final String TXT_ID = "id";
    /**
     * Fingerprint characters announced so a device can skip its own service.
     */
    public static final int ANNOUNCED_ID_CHARS = 16;
    private static final int MAX_SERVICE_NAME_CHARS = 40;
    private static final int BACKLOG = 4;

    public interface Listener extends ApprovalGate.Listener, HostSession.Listener {
        /**
         * The connection with a client ended. {@code error} is null for a normal goodbye or
         * a turned down request.
         */
        void onSessionEnded(@Nullable String error);

        /**
         * Listening stopped on its own, not through {@link #stop()}.
         */
        void onHostFailed(@NonNull String error);
    }

    @Nullable
    private final NsdManager mNsd;
    @NonNull
    private final String mName;
    @NonNull
    private final LocalIdentity mIdentity;
    @NonNull
    private final TrustedDevices mTrust;
    @NonNull
    private final HostCatalog mCatalog;
    @NonNull
    private final Listener mListener;
    @NonNull
    private final SecureRandom mRandom;
    @NonNull
    private final ApprovalGate mGate;
    private final Object mLock = new Object();
    @Nullable
    private ServerSocket mServer;
    @Nullable
    private Socket mClient;
    /**
     * Set once the connected client is accepted.
     */
    @Nullable
    private String mClientFingerprint;
    /**
     * A client dropped through {@link #endSessionWith}, whose end isn't an error.
     */
    @Nullable
    private Socket mEndedOnPurpose;
    @Nullable
    private NsdManager.RegistrationListener mRegistration;
    private boolean mStarted;
    private boolean mStopped;

    public LanSyncHost(@Nullable NsdManager nsd, @NonNull String name, @NonNull LocalIdentity identity,
                       @NonNull TrustedDevices trust, @NonNull HostCatalog catalog, @NonNull SecureRandom random,
                       @NonNull Listener listener) {
        mNsd = nsd;
        mName = name;
        mIdentity = identity;
        mTrust = trust;
        mCatalog = catalog;
        mRandom = random;
        mListener = listener;
        mGate = new ApprovalGate(listener, HostSession.APPROVAL_TIMEOUT_MS);
    }

    /**
     * Opens the listening socket on any free port and announces it. Returns the port.
     */
    public int start() throws IOException {
        return start(0);
    }

    /**
     * Opens the listening socket on {@code preferredPort} when it's free, otherwise on any free
     * port, and announces it. Returns the port.
     * <p>
     * Pass the port from last time. Android 14 and later answer a lookup for a name they've seen
     * from their cache, and a search never hears that the same name came back on another port, so
     * a nearby device would keep offering the old one.
     */
    public int start(int preferredPort) throws IOException {
        ServerSocket server;
        synchronized (mLock) {
            if (mStarted || mStopped) {
                // A stop that came first means sharing was turned off before this got going
                throw new IllegalStateException(mStopped ? "Already stopped" : "Already started");
            }
            mStarted = true;
            try {
                server = bind(preferredPort);
            } catch (IOException e) {
                mStopped = true;
                throw e;
            }
            mServer = server;
        }
        Thread acceptThread = new Thread(() -> acceptLoop(server), "LanSyncHost-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        register(server.getLocalPort());
        return server.getLocalPort();
    }

    @NonNull
    private static ServerSocket bind(int preferredPort) throws IOException {
        if (preferredPort > 0 && preferredPort <= 0xFFFF) {
            try {
                return bindTo(preferredPort);
            } catch (IOException ignore) {
                // Something else has it now, so any free port will do
            }
        }
        return bindTo(0);
    }

    @NonNull
    private static ServerSocket bindTo(int port) throws IOException {
        ServerSocket server = new ServerSocket();
        try {
            // Lets the port be reused while the last session's connection is still in TIME_WAIT
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), BACKLOG);
            return server;
        } catch (IOException e) {
            closeQuietly(server);
            throw e;
        }
    }

    public void stop() {
        ServerSocket server;
        Socket client;
        NsdManager.RegistrationListener registration;
        synchronized (mLock) {
            if (mStopped) {
                return;
            }
            mStopped = true;
            server = mServer;
            client = mClient;
            registration = mRegistration;
            mServer = null;
            mClient = null;
            mClientFingerprint = null;
            mRegistration = null;
        }
        mGate.cancel();
        closeQuietly(server);
        closeQuietly(client);
        if (mNsd != null && registration != null) {
            try {
                mNsd.unregisterService(registration);
            } catch (IllegalArgumentException ignore) {
                // Registration already failed, so NSD dropped the listener itself
            }
        }
    }

    /**
     * Drops the connection to the device with this fingerprint, if it's the one connected.
     * Sharing stays on for everyone else.
     */
    public boolean endSessionWith(@NonNull String fingerprint) {
        Socket client;
        synchronized (mLock) {
            client = mClient;
            if (client == null || !fingerprint.equals(mClientFingerprint)) {
                return false;
            }
            mEndedOnPurpose = client;
        }
        closeQuietly(client);
        return true;
    }

    public boolean isRunning() {
        synchronized (mLock) {
            return mStarted && !mStopped;
        }
    }

    @NonNull
    public ApprovalGate getApprovalGate() {
        return mGate;
    }

    @NonNull
    public static String announcedId(@NonNull String fingerprint) {
        return fingerprint.substring(0, Math.min(ANNOUNCED_ID_CHARS, fingerprint.length()));
    }

    @VisibleForTesting
    @Nullable
    NsdManager.RegistrationListener getRegistrationListener() {
        synchronized (mLock) {
            return mRegistration;
        }
    }

    private void register(int port) {
        if (mNsd == null) {
            return;
        }
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(mName.length() > MAX_SERVICE_NAME_CHARS ? mName.substring(0, MAX_SERVICE_NAME_CHARS) : mName);
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);
        info.setAttribute(TXT_PROTOCOL, String.valueOf(PairingHandshake.PROTOCOL_VERSION));
        info.setAttribute(TXT_ID, announcedId(mIdentity.getFingerprint()));
        NsdManager.RegistrationListener registration = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                synchronized (mLock) {
                    if (mRegistration == this) {
                        mRegistration = null;
                    }
                }
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }
        };
        synchronized (mLock) {
            // Held across the call so stop() cannot slip in between and miss the registration
            if (mStopped) {
                return;
            }
            mRegistration = registration;
            mNsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
        }
    }

    private void acceptLoop(@NonNull ServerSocket server) {
        while (true) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                boolean stopped;
                synchronized (mLock) {
                    stopped = mStopped;
                }
                if (!stopped) {
                    stop();
                    mListener.onHostFailed(String.valueOf(e.getMessage()));
                }
                return;
            }
            if (!LanAddresses.isLocal(socket.getInetAddress()) || !claim(socket)) {
                closeQuietly(socket);
                continue;
            }
            Thread sessionThread = new Thread(() -> runSession(socket), "LanSyncHost-session");
            sessionThread.setDaemon(true);
            sessionThread.start();
        }
    }

    private boolean claim(@NonNull Socket socket) {
        synchronized (mLock) {
            if (mStopped || mClient != null) {
                return false;
            }
            mClient = socket;
            return true;
        }
    }

    private void runSession(@NonNull Socket socket) {
        String error = null;
        HostSession.Listener sessionListener = new HostSession.Listener() {
            @Override
            public void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint) {
                synchronized (mLock) {
                    if (mClient == socket) {
                        mClientFingerprint = peerFingerprint;
                    }
                }
                mListener.onClientAccepted(peerName, peerFingerprint);
            }

            @Override
            public void onFileSent(@NonNull String packageName, @NonNull String fileName) {
                mListener.onFileSent(packageName, fileName);
            }
        };
        try {
            socket.setTcpNoDelay(true);
            FrameIo io = new FrameIo(socket.getInputStream(), socket.getOutputStream());
            new HostSession(io, socket::setSoTimeout, mName, mIdentity, mRandom, mTrust, mGate, mCatalog,
                    sessionListener).run();
        } catch (IOException e) {
            error = String.valueOf(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(socket);
            boolean endedOnPurpose;
            synchronized (mLock) {
                if (mClient == socket) {
                    mClient = null;
                    mClientFingerprint = null;
                }
                endedOnPurpose = mEndedOnPurpose == socket;
                if (endedOnPurpose) {
                    mEndedOnPurpose = null;
                }
            }
            mListener.onSessionEnded(endedOnPurpose ? null : error);
        }
    }

    private static void closeQuietly(@Nullable java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignore) {
            }
        }
    }
}
