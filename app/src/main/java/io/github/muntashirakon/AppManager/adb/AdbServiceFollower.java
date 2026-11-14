// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Finds an ADB service over NSD and follows each one it finds, so a daemon that comes back on a new port under the
 * same name is reported on the new port.
 * <p>
 * Android 17's adbd advertises Wireless debugging under one name, kept in {@code persist.adb.wifi.guid}, and Android
 * 14 and later answer a resolve for a name they've seen from their cache without reporting that name again on another
 * port. libadb's {@link AdbMdns} resolves each name once, so after Wireless debugging restarted on a new port the scan
 * found nothing it could use and the connect fell back to the old saved port. A service info callback reports each
 * change instead.
 * <p>
 * Like {@link AdbMdns}, only this device's own daemon counts, and only at a port something is listening on: the cache
 * can still hand over the old port first.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
final class AdbServiceFollower implements AdbUtils.Scanner {
    private static final String TAG = AdbServiceFollower.class.getSimpleName();

    /**
     * Keeps a found service's addresses and port current until the returned stopper runs.
     */
    interface ServiceFollower {
        @NonNull
        Runnable follow(@NonNull NsdServiceInfo service, @NonNull FollowSink sink);

        /**
         * Resolves a service once, for when following it couldn't start. The returned stopper cancels a resolve
         * that's still running.
         */
        @NonNull
        Runnable resolveOnce(@NonNull NsdServiceInfo service, @NonNull NsdManager.ResolveListener listener);
    }

    interface FollowSink {
        void onUpdated(@NonNull NsdServiceInfo service);

        void onLost();

        /**
         * Following never started.
         */
        void onFailed(int errorCode);
    }

    /**
     * Whether a daemon at this address and port is this device's own and listening.
     */
    interface DaemonCheck {
        boolean isOwnDaemon(@NonNull InetAddress host, int port);
    }

    /**
     * Whether an address can be this device's. Unsure counts as yes, since a service dropped for being elsewhere
     * isn't followed again until discovery loses it.
     */
    interface HostCheck {
        boolean mayBeThisDevice(@NonNull InetAddress host);
    }

    /**
     * A service found on one network. The same daemon can be found on more than one, such as Wi-Fi and a hotspot.
     */
    private static final class Followed {
        // All guarded by the follower's lock
        /**
         * Null until {@link ServiceFollower#follow} returns.
         */
        @Nullable
        Runnable mStopper;
        @Nullable
        Runnable mResolveStopper;
        /**
         * What the service said last, to report again if another network's copy that was reported goes away.
         */
        @Nullable
        NsdServiceInfo mLatest;
        /**
         * Another device's daemon. No longer followed, but kept so discovery finding it again doesn't follow it again.
         */
        boolean mElsewhere;

        /**
         * Hands over the stoppers that are set, so each one runs once.
         */
        void takeStoppers(@NonNull List<Runnable> stoppers) {
            if (mStopper != null) {
                stoppers.add(mStopper);
                mStopper = null;
            }
            if (mResolveStopper != null) {
                stoppers.add(mResolveStopper);
                mResolveStopper = null;
            }
        }
    }

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

        @NonNull
        @Override
        @SuppressWarnings("deprecation")
        public Runnable resolveOnce(@NonNull NsdServiceInfo service, @NonNull NsdManager.ResolveListener listener) {
            mNsd.resolveService(service, listener);
            return () -> {
                try {
                    mNsd.stopServiceResolution(listener);
                } catch (IllegalArgumentException ignore) {
                    // Already resolved or failed, and NSD let the listener go
                }
            };
        }
    }

    @NonNull
    private final NsdManager mNsd;
    @NonNull
    private final String mNsdType;
    @NonNull
    private final AdbMdns.OnAdbDaemonDiscoveredListener mListener;
    @NonNull
    private final ServiceFollower mFollower;
    @NonNull
    private final DaemonCheck mCheck;
    @NonNull
    private final HostCheck mHostCheck;
    private final Object mLock = new Object();
    // Keyed by name and network
    private final Map<Pair<String, Network>, Followed> mFollowing = new HashMap<>();
    private boolean mRunning;
    // The service whose daemon was reported last, which alone can take it back
    @Nullable
    private Pair<String, Network> mReportedKey;

    @NonNull
    private static Pair<String, Network> keyOf(@NonNull String name, @NonNull NsdServiceInfo service) {
        return new Pair<>(name, service.getNetwork());
    }

    private final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.w(TAG, "Could not look for %s (error %d)", serviceType, errorCode);
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
            String name = serviceInfo.getServiceName();
            if (name == null) {
                return;
            }
            Pair<String, Network> key = keyOf(name, serviceInfo);
            Followed followed;
            synchronized (mLock) {
                if (!mRunning || mFollowing.containsKey(key)) {
                    return;
                }
                followed = new Followed();
                mFollowing.put(key, followed);
            }
            follow(serviceInfo, key, followed);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            String name = serviceInfo.getServiceName();
            if (name == null) {
                return;
            }
            Pair<String, Network> key = keyOf(name, serviceInfo);
            List<Runnable> stoppers = new ArrayList<>(2);
            synchronized (mLock) {
                Followed followed = mFollowing.remove(key);
                if (followed != null) {
                    // The follow's stopper is still null while follow() runs, which then stops it itself
                    followed.takeStoppers(stoppers);
                }
            }
            for (Runnable stopper : stoppers) {
                stopper.run();
            }
            lost(key);
        }
    };

    /**
     * @param serviceType one of the {@link AdbMdns} service types
     */
    AdbServiceFollower(@NonNull NsdManager nsd, @NonNull String serviceType,
                       @NonNull AdbMdns.OnAdbDaemonDiscoveredListener listener) {
        this(nsd, serviceType, listener, new NsdFollower(nsd), AdbServiceFollower::isOwnListeningDaemon,
                AdbServiceFollower::mayBeThisDevice);
    }

    @VisibleForTesting
    AdbServiceFollower(@NonNull NsdManager nsd, @NonNull String serviceType,
                       @NonNull AdbMdns.OnAdbDaemonDiscoveredListener listener, @NonNull ServiceFollower follower,
                       @NonNull DaemonCheck check, @NonNull HostCheck hostCheck) {
        mNsd = nsd;
        mNsdType = nsdType(serviceType);
        mListener = listener;
        mFollower = follower;
        mCheck = check;
        mHostCheck = hostCheck;
    }

    /**
     * The NSD type for an {@link AdbMdns} service type, written the way {@link AdbMdns} writes it.
     */
    @NonNull
    static String nsdType(@NonNull String serviceType) {
        return "_" + serviceType + "._tcp";
    }

    @Override
    public void start() {
        synchronized (mLock) {
            if (mRunning) {
                return;
            }
            mRunning = true;
        }
        mNsd.discoverServices(mNsdType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    public void stop() {
        List<Runnable> stoppers = new ArrayList<>();
        synchronized (mLock) {
            if (!mRunning) {
                return;
            }
            mRunning = false;
            for (Followed followed : mFollowing.values()) {
                followed.takeStoppers(stoppers);
            }
            mFollowing.clear();
            mReportedKey = null;
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

    private boolean isFollowing(@NonNull Pair<String, Network> key, @NonNull Followed followed) {
        synchronized (mLock) {
            return isLive(key, followed);
        }
    }

    /**
     * Whether this service is still one to follow. Hold the lock.
     */
    private boolean isLive(@NonNull Pair<String, Network> key, @NonNull Followed followed) {
        return mRunning && mFollowing.get(key) == followed && !followed.mElsewhere;
    }

    private void follow(@NonNull NsdServiceInfo service, @NonNull Pair<String, Network> key,
                        @NonNull Followed followed) {
        Runnable stopper = mFollower.follow(service, new FollowSink() {
            @Override
            public void onUpdated(@NonNull NsdServiceInfo info) {
                updated(key, followed, info);
            }

            @Override
            public void onLost() {
                synchronized (mLock) {
                    if (!isLive(key, followed)) {
                        return;
                    }
                    followed.mLatest = null;
                }
                lost(key);
            }

            @Override
            public void onFailed(int errorCode) {
                if (isFollowing(key, followed)) {
                    Log.w(TAG, "Could not follow %s (error %d), resolving it once", key.first, errorCode);
                    resolveOnce(service, key, followed);
                }
            }
        });
        boolean keep;
        synchronized (mLock) {
            keep = isLive(key, followed);
            if (keep) {
                followed.mStopper = stopper;
            }
        }
        if (!keep) {
            // Lost, stopped or found to be elsewhere while it was being set up
            stopper.run();
        }
    }

    private void resolveOnce(@NonNull NsdServiceInfo service, @NonNull Pair<String, Network> key,
                             @NonNull Followed followed) {
        Runnable stopper = mFollower.resolveOnce(service, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(TAG, "Could not resolve %s (error %d)", key.first, errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                updated(key, followed, serviceInfo);
            }
        });
        boolean keep;
        synchronized (mLock) {
            keep = isLive(key, followed);
            if (keep) {
                followed.mResolveStopper = stopper;
            }
        }
        if (!keep) {
            // Lost or stopped while the resolve was being started. Left running, NSD would keep it for good.
            stopper.run();
        }
    }

    private void updated(@NonNull Pair<String, Network> key, @NonNull Followed followed,
                         @NonNull NsdServiceInfo info) {
        if (isElsewhere(info)) {
            dropElsewhere(key, followed);
            return;
        }
        synchronized (mLock) {
            if (!isLive(key, followed)) {
                return;
            }
            followed.mLatest = info;
        }
        report(key, info);
    }

    /**
     * Whether every address the service gave belongs to another device, as with another phone's Wireless debugging
     * on the same network.
     */
    private boolean isElsewhere(@NonNull NsdServiceInfo info) {
        List<InetAddress> hosts = hostAddresses(info);
        if (hosts.isEmpty()) {
            return false;
        }
        for (InetAddress host : hosts) {
            if (mHostCheck.mayBeThisDevice(host)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stops following another device's daemon. NSD allows an app 10 requests at a time, discovery and follows
     * included, so other phones' daemons left followed could make the resolve of this one fail.
     */
    private void dropElsewhere(@NonNull Pair<String, Network> key, @NonNull Followed followed) {
        List<Runnable> stoppers = new ArrayList<>(2);
        synchronized (mLock) {
            if (!isLive(key, followed)) {
                return;
            }
            followed.mElsewhere = true;
            followed.mLatest = null;
            // A follow still being set up has no stopper yet, and follow() stops it once it sees mElsewhere
            followed.takeStoppers(stoppers);
        }
        Log.d(TAG, "%s is another device's daemon, no longer following it", key.first);
        for (Runnable stopper : stoppers) {
            stopper.run();
        }
        lost(key);
    }

    /**
     * @return whether the daemon was reported
     */
    private boolean report(@NonNull Pair<String, Network> key, @NonNull NsdServiceInfo info) {
        int port = info.getPort();
        if (port <= 0) {
            return false;
        }
        for (InetAddress host : hostAddresses(info)) {
            if (mCheck.isOwnDaemon(host, port)) {
                synchronized (mLock) {
                    if (!mRunning) {
                        return false;
                    }
                    mReportedKey = key;
                }
                mListener.onPortChanged(host, port);
                return true;
            }
        }
        Log.d(TAG, "%s at port %d isn't this device's daemon or isn't listening", key.first, port);
        return false;
    }

    private void lost(@NonNull Pair<String, Network> key) {
        List<Pair<Pair<String, Network>, NsdServiceInfo>> others = new ArrayList<>();
        synchronized (mLock) {
            if (!mRunning || !key.equals(mReportedKey)) {
                return;
            }
            mReportedKey = null;
            for (Map.Entry<Pair<String, Network>, Followed> entry : mFollowing.entrySet()) {
                NsdServiceInfo latest = entry.getValue().mLatest;
                if (latest != null && !key.equals(entry.getKey())) {
                    others.add(new Pair<>(entry.getKey(), latest));
                }
            }
        }
        // The same daemon can still be there on another network
        for (Pair<Pair<String, Network>, NsdServiceInfo> other : others) {
            if (report(other.first, other.second)) {
                return;
            }
        }
        synchronized (mLock) {
            if (!mRunning) {
                // Stopped meanwhile, which says nothing about whether the daemon is still there
                return;
            }
        }
        mListener.onPortChanged(null, -1);
    }

    /**
     * IPv4 first. A link-local IPv6 address can still be settling just after Wi-Fi comes back, and its check then
     * says nothing about the port.
     */
    @NonNull
    @SuppressWarnings("deprecation")
    private static List<InetAddress> hostAddresses(@NonNull NsdServiceInfo info) {
        List<InetAddress> addresses = new ArrayList<>(info.getHostAddresses());
        if (addresses.isEmpty() && info.getHost() != null) {
            addresses.add(info.getHost());
        }
        List<InetAddress> ordered = new ArrayList<>(addresses.size());
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                ordered.add(address);
            }
        }
        for (InetAddress address : addresses) {
            if (!(address instanceof Inet4Address)) {
                ordered.add(address);
            }
        }
        return ordered;
    }

    @VisibleForTesting
    static boolean isOwnListeningDaemon(@NonNull InetAddress host, int port) {
        try {
            if (NetworkInterface.getByInetAddress(host) == null) {
                return false;
            }
        } catch (SocketException e) {
            return false;
        }
        return isPortTaken(host, port);
    }

    private static boolean mayBeThisDevice(@NonNull InetAddress host) {
        try {
            return NetworkInterface.getByInetAddress(host) != null;
        } catch (SocketException e) {
            return true;
        }
    }

    /**
     * Whether something holds the port, as libadb checks it: binding there fails while adbd listens.
     */
    @VisibleForTesting
    static boolean isPortTaken(@NonNull InetAddress host, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(host, port), 1);
            return false;
        } catch (IOException e) {
            return isAddressInUse(e);
        }
    }

    /**
     * Whether a failed bind says the port is in use. An address that isn't ready yet, or a bind that isn't allowed,
     * says nothing about the port, and taking either as "in use" reported a port adbd had already left.
     */
    @VisibleForTesting
    static boolean isAddressInUse(@NonNull IOException e) {
        if (!(e instanceof BindException)) {
            return false;
        }
        Throwable cause = e.getCause();
        if (cause instanceof ErrnoException) {
            return ((ErrnoException) cause).errno == OsConstants.EADDRINUSE;
        }
        // Off Android there's no errno to read, and the refused bind is all there is to go on
        return true;
    }
}
