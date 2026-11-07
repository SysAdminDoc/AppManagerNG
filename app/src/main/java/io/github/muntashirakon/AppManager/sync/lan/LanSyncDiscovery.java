// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds sharing devices over NSD. Services are resolved one at a time, since older Android
 * versions refuse a second resolve while one is running, and this device's own announcement is
 * left out.
 */
public final class LanSyncDiscovery {
    public static final class Peer {
        @NonNull
        public final String name;
        @NonNull
        public final InetAddress address;
        public final int port;

        Peer(@NonNull String name, @NonNull InetAddress address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
        }
    }

    public interface Listener {
        /**
         * Called on an NSD thread with the current list.
         */
        void onPeersChanged(@NonNull List<Peer> peers);

        void onDiscoveryFailed(int errorCode);
    }

    @NonNull
    private final NsdManager mNsd;
    @NonNull
    private final String mOwnId;
    @NonNull
    private final Listener mListener;
    private final Object mLock = new Object();
    private final Map<String, Peer> mPeers = new LinkedHashMap<>();
    private final Deque<NsdServiceInfo> mPending = new ArrayDeque<>();
    private boolean mResolving;
    private boolean mRunning;
    private final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            synchronized (mLock) {
                mRunning = false;
            }
            mListener.onDiscoveryFailed(errorCode);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            synchronized (mLock) {
                if (!mRunning) {
                    return;
                }
                mPending.add(serviceInfo);
            }
            resolveNext();
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            List<Peer> snapshot;
            synchronized (mLock) {
                if (mPeers.remove(serviceInfo.getServiceName()) == null) {
                    return;
                }
                snapshot = new ArrayList<>(mPeers.values());
            }
            mListener.onPeersChanged(snapshot);
        }
    };

    /**
     * @param ownFingerprint this device's identity fingerprint, used to skip its own service
     */
    public LanSyncDiscovery(@NonNull NsdManager nsd, @NonNull String ownFingerprint, @NonNull Listener listener) {
        mNsd = nsd;
        mOwnId = LanSyncHost.announcedId(ownFingerprint);
        mListener = listener;
    }

    public void start() {
        synchronized (mLock) {
            if (mRunning) {
                return;
            }
            mRunning = true;
        }
        mNsd.discoverServices(LanSyncHost.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stop() {
        synchronized (mLock) {
            if (!mRunning) {
                return;
            }
            mRunning = false;
            mPending.clear();
            mPeers.clear();
        }
        try {
            mNsd.stopServiceDiscovery(mDiscoveryListener);
        } catch (IllegalArgumentException ignore) {
            // Discovery failed to start, so NSD already dropped the listener
        }
    }

    @SuppressWarnings("deprecation")
    private void resolveNext() {
        NsdServiceInfo next;
        synchronized (mLock) {
            if (mResolving || !mRunning) {
                return;
            }
            next = mPending.poll();
            if (next == null) {
                return;
            }
            mResolving = true;
        }
        mNsd.resolveService(next, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                synchronized (mLock) {
                    mResolving = false;
                }
                resolveNext();
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                List<Peer> snapshot = null;
                synchronized (mLock) {
                    mResolving = false;
                    Peer peer = toPeer(serviceInfo, mOwnId);
                    if (mRunning && peer != null) {
                        mPeers.put(serviceInfo.getServiceName(), peer);
                        snapshot = new ArrayList<>(mPeers.values());
                    }
                }
                if (snapshot != null) {
                    mListener.onPeersChanged(snapshot);
                }
                resolveNext();
            }
        });
    }

    /**
     * Returns {@code null} for this device's own service, another protocol version, or an
     * address outside the local network.
     */
    @VisibleForTesting
    @Nullable
    @SuppressWarnings("deprecation")
    static Peer toPeer(@NonNull NsdServiceInfo info, @NonNull String ownId) {
        Map<String, byte[]> attributes = info.getAttributes();
        String id = attribute(attributes, LanSyncHost.TXT_ID);
        String version = attribute(attributes, LanSyncHost.TXT_PROTOCOL);
        InetAddress host = info.getHost();
        if (ownId.equals(id) || !String.valueOf(PairingHandshake.PROTOCOL_VERSION).equals(version)
                || !LanAddresses.isLocal(host) || info.getPort() <= 0 || info.getServiceName() == null) {
            return null;
        }
        return new Peer(info.getServiceName(), host, info.getPort());
    }

    @Nullable
    private static String attribute(@Nullable Map<String, byte[]> attributes, @NonNull String key) {
        if (attributes == null) {
            return null;
        }
        byte[] value = attributes.get(key);
        return value != null ? new String(value, StandardCharsets.UTF_8) : null;
    }
}
