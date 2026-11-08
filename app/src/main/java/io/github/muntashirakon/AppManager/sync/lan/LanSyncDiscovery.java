// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds sharing devices over NSD, leaving this device's own announcement out. On Android 14 and
 * later each found service is followed, so a device that shares again on a new port or address
 * shows the new one. Older versions resolve services one at a time, since they refuse a second
 * resolve while one is running.
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
    private final Map<String, Followed> mFollowing = new HashMap<>();
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
            Followed followed = null;
            synchronized (mLock) {
                if (!mRunning) {
                    return;
                }
                String name = serviceInfo.getServiceName();
                if (mFollower != null && name != null) {
                    if (mFollowing.containsKey(name)) {
                        return;
                    }
                    followed = new Followed();
                    mFollowing.put(name, followed);
                } else {
                    mPending.add(serviceInfo);
                }
            }
            if (followed != null) {
                follow(serviceInfo, followed);
            } else {
                resolveNext();
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Runnable stopper = null;
            List<Peer> snapshot = null;
            synchronized (mLock) {
                Followed followed = mFollowing.remove(serviceInfo.getServiceName());
                if (followed != null) {
                    // Still null while follow() runs, which then stops it itself
                    stopper = followed.mStopper;
                }
                if (mPeers.remove(serviceInfo.getServiceName()) != null) {
                    snapshot = new ArrayList<>(mPeers.values());
                }
            }
            if (stopper != null) {
                stopper.run();
            }
            if (snapshot != null) {
                mListener.onPeersChanged(snapshot);
            }
        }
    };

    /**
     * Keeps a found service's address and port current until the returned stopper runs.
     */
    interface ServiceFollower {
        @NonNull
        Runnable follow(@NonNull NsdServiceInfo service, @NonNull FollowSink sink);
    }

    interface FollowSink {
        void onUpdated(@NonNull NsdServiceInfo service);

        void onLost();

        /**
         * Following never started.
         */
        void onFailed(int errorCode);
    }

    private static final class Followed {
        /**
         * Guarded by the discovery's lock. Null until {@link ServiceFollower#follow} returns.
         */
        @Nullable
        Runnable mStopper;
    }

    /**
     * Android 14 and later answer a resolve for a name they've seen from their cache, and the
     * discovery listener never reports that name coming back on another port, so the list kept
     * offering a port that was gone. A service info callback reports each change instead.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final class NsdFollower implements ServiceFollower {
        @NonNull
        private final NsdManager mNsd;

        NsdFollower(@NonNull NsdManager nsd) {
            mNsd = nsd;
        }

        @NonNull
        @Override
        public Runnable follow(@NonNull NsdServiceInfo service, @NonNull FollowSink sink) {
            NsdManager.ServiceInfoCallback callback = new NsdManager.ServiceInfoCallback() {
                @Override
                public void onServiceInfoCallbackRegistrationFailed(int errorCode) {
                    sink.onFailed(errorCode);
                }

                @Override
                public void onServiceUpdated(@NonNull NsdServiceInfo serviceInfo) {
                    sink.onUpdated(serviceInfo);
                }

                @Override
                public void onServiceLost() {
                    sink.onLost();
                }

                @Override
                public void onServiceInfoCallbackUnregistered() {
                }
            };
            try {
                mNsd.registerServiceInfoCallback(service, Runnable::run, callback);
            } catch (IllegalArgumentException e) {
                sink.onFailed(NsdManager.FAILURE_INTERNAL_ERROR);
                return () -> {
                };
            }
            return () -> {
                try {
                    mNsd.unregisterServiceInfoCallback(callback);
                } catch (IllegalArgumentException ignore) {
                    // Registration failed, so NSD never kept the callback
                }
            };
        }
    }

    @Nullable
    private final ServiceFollower mFollower;

    /**
     * @param ownFingerprint this device's identity fingerprint, used to skip its own service
     */
    public LanSyncDiscovery(@NonNull NsdManager nsd, @NonNull String ownFingerprint, @NonNull Listener listener) {
        this(nsd, ownFingerprint, listener,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? new NsdFollower(nsd) : null);
    }

    @VisibleForTesting
    LanSyncDiscovery(@NonNull NsdManager nsd, @NonNull String ownFingerprint, @NonNull Listener listener,
                     @Nullable ServiceFollower follower) {
        mNsd = nsd;
        mOwnId = LanSyncHost.announcedId(ownFingerprint);
        mListener = listener;
        mFollower = follower;
    }

    @VisibleForTesting
    @Nullable
    ServiceFollower getFollower() {
        return mFollower;
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
        List<Runnable> stoppers = new ArrayList<>();
        synchronized (mLock) {
            if (!mRunning) {
                return;
            }
            mRunning = false;
            mPending.clear();
            mPeers.clear();
            for (Followed followed : mFollowing.values()) {
                if (followed.mStopper != null) {
                    stoppers.add(followed.mStopper);
                }
            }
            mFollowing.clear();
        }
        for (Runnable stopper : stoppers) {
            stopper.run();
        }
        try {
            mNsd.stopServiceDiscovery(mDiscoveryListener);
        } catch (IllegalArgumentException ignore) {
            // Discovery failed to start, so NSD already dropped the listener
        }
    }

    private void follow(@NonNull NsdServiceInfo service, @NonNull Followed followed) {
        String name = service.getServiceName();
        Runnable stopper = Objects.requireNonNull(mFollower).follow(service, new FollowSink() {
            @Override
            public void onUpdated(@NonNull NsdServiceInfo info) {
                List<Peer> snapshot;
                synchronized (mLock) {
                    if (!mRunning || mFollowing.get(name) != followed) {
                        return;
                    }
                    Peer peer = toPeer(info, mOwnId);
                    // An update with no usable address takes the device off the list until the next one
                    Peer old = peer != null ? mPeers.put(name, peer) : mPeers.remove(name);
                    if (peer == null && old == null) {
                        return;
                    }
                    snapshot = new ArrayList<>(mPeers.values());
                }
                mListener.onPeersChanged(snapshot);
            }

            @Override
            public void onLost() {
                List<Peer> snapshot;
                synchronized (mLock) {
                    if (!mRunning || mFollowing.get(name) != followed || mPeers.remove(name) == null) {
                        return;
                    }
                    snapshot = new ArrayList<>(mPeers.values());
                }
                mListener.onPeersChanged(snapshot);
            }

            @Override
            public void onFailed(int errorCode) {
                synchronized (mLock) {
                    if (!mRunning || mFollowing.get(name) != followed) {
                        return;
                    }
                    mFollowing.remove(name);
                    mPending.add(service);
                }
                resolveNext();
            }
        });
        boolean keep;
        synchronized (mLock) {
            keep = mRunning && mFollowing.get(name) == followed;
            if (keep) {
                followed.mStopper = stopper;
            }
        }
        if (!keep) {
            // Lost, stopped or failed while it was being set up
            stopper.run();
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
