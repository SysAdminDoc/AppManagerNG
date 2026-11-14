// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNsdManager;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.android.AdbMdns;

@RunWith(RobolectricTestRunner.class)
public class AdbServiceFollowerTest {
    private static final String TYPE = AdbServiceFollower.nsdType(AdbMdns.SERVICE_TYPE_TLS_CONNECT);
    // Android 17 keeps this name, from persist.adb.wifi.guid, across Wireless debugging restarts
    private static final String NAME = "adb-0d5e4c1b-3a1c2f";
    private static final String HOST = "192.168.1.20";
    // This device's address on the hotspot it runs
    private static final String HOTSPOT_HOST = "192.168.43.1";
    private static final Set<String> OWN_HOSTS = new HashSet<>(Arrays.asList(HOST, HOTSPOT_HOST));

    private NsdManager mNsd;
    private ShadowNsdManager mShadowNsd;
    private final FakeFollower mFollower = new FakeFollower();
    // Ports this device's adbd listens on
    private final Set<Integer> mListening = new HashSet<>();
    private final List<Pair<String, Integer>> mReports = new ArrayList<>();
    // Runs as the scanner checks an address and port
    private Runnable mOnCheck;
    private AdbServiceFollower mScanner;

    @Before
    public void setUp() {
        mNsd = (NsdManager) RuntimeEnvironment.getApplication().getSystemService(Context.NSD_SERVICE);
        mShadowNsd = Shadow.extract(mNsd);
        mFollower.nsd = mNsd;
        mScanner = scanner((host, port) -> mReports.add(new Pair<>(host != null ? host.getHostAddress() : null, port)));
    }

    @After
    public void tearDown() {
        mScanner.stop();
        ShadowNsdManager.reset();
    }

    @Test
    public void android14AndLaterFollowWirelessDebugging() {
        assertTrue(AdbUtils.followsServices(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AdbMdns.SERVICE_TYPE_TLS_CONNECT));
        assertTrue(AdbUtils.followsServices(37, AdbMdns.SERVICE_TYPE_TLS_CONNECT));
        // Older versions resolve a name fresh each scan, and adbd picked a new name each time it started
        assertFalse(AdbUtils.followsServices(Build.VERSION_CODES.TIRAMISU, AdbMdns.SERVICE_TYPE_TLS_CONNECT));
        assertFalse(AdbUtils.followsServices(36, AdbMdns.SERVICE_TYPE_ADB));
        assertFalse(AdbUtils.followsServices(36, AdbMdns.SERVICE_TYPE_TLS_PAIRING));
        assertEquals("_adb-tls-connect._tcp", TYPE);
    }

    @Test
    public void wirelessDebuggingBackOnANewPortUnderTheSameNameIsChosenThere() throws Exception {
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(false, 1500);
        mScanner.stop();
        mScanner = scanner((host, port) -> chooser.onFound(false, host != null ? host.getHostAddress() : null, port));
        mListening.add(37555);
        mScanner.start();

        discovery().onServiceFound(unresolved(NAME));
        AdbServiceFollower.FollowSink sink = mFollower.sinks.get(NAME);
        // NSD's cache answers first with the port Wireless debugging had before it restarted
        sink.onUpdated(service(NAME, HOST, 41234));
        assertFalse("nothing listens on the old port", chooser.await(50, TimeUnit.MILLISECONDS));
        sink.onUpdated(service(NAME, HOST, 37555));

        assertTrue(chooser.await(1, TimeUnit.SECONDS));
        assertEquals(new Pair<>(HOST, 37555), chooser.result());
    }

    @Test
    public void aFollowedDaemonThatMovesIsReportedAtEachPort() throws Exception {
        mListening.add(37555);
        mListening.add(40111);
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        discovery().onServiceFound(found);
        discovery().onServiceFound(unresolved(NAME));
        assertEquals("followed once", 1, mFollower.sinks.size());
        assertTrue("never resolved from the cache", isEmpty(mShadowNsd.getResolveListeners(found)));

        AdbServiceFollower.FollowSink sink = mFollower.sinks.get(NAME);
        sink.onUpdated(service(NAME, HOST, 37555));
        sink.onUpdated(service(NAME, HOST, 40111));

        assertEquals(Arrays.asList(new Pair<>(HOST, 37555), new Pair<>(HOST, 40111)), mReports);
    }

    @Test
    public void onlyThisDevicesOwnDaemonCounts() throws Exception {
        mListening.add(37555);
        mScanner.start();
        discovery().onServiceFound(unresolved("adb-other-phone"));

        mFollower.sinks.get("adb-other-phone").onUpdated(service("adb-other-phone", "192.168.1.77", 37555));

        assertTrue(mReports.isEmpty());
    }

    @Test
    public void anotherPhonesDaemonIsNoLongerFollowed() throws Exception {
        mScanner.start();
        NsdServiceInfo other = unresolved("adb-other-phone");
        discovery().onServiceFound(other);

        mFollower.sinks.get("adb-other-phone").onUpdated(service("adb-other-phone", "192.168.1.77", 37555));

        // NSD allows an app 10 requests at a time, so a network full of phones with Wireless debugging on used
        // them all up following daemons that would never be this one
        assertEquals(Collections.singletonList("adb-other-phone"), mFollower.stopped);
        // Still seen by discovery, so not followed again
        discovery().onServiceFound(other);
        assertEquals(1, mFollower.follows);
        mScanner.stop();
        assertEquals("stopped once", 1, mFollower.stopped.size());
        assertTrue(mReports.isEmpty());
    }

    @Test
    public void aDaemonLostWhileTheScanStopsIsNotReportedGone() throws Exception {
        mListening.add(37555);
        mScanner.start();
        Network wifi = ShadowNetwork.newInstance(100);
        Network hotspot = ShadowNetwork.newInstance(101);
        NsdServiceInfo onWifi = on(unresolved(NAME), wifi);
        NsdManager.DiscoveryListener discovery = discovery();
        discovery.onServiceFound(onWifi);
        discovery.onServiceFound(on(unresolved(NAME), hotspot));
        mFollower.sinkOn(hotspot).onUpdated(on(service(NAME, HOTSPOT_HOST, 37555), hotspot));
        mFollower.sinkOn(wifi).onUpdated(on(service(NAME, HOST, 37555), wifi));
        // The scan ends just as the hotspot's copy is checked
        mOnCheck = () -> mScanner.stop();

        discovery.onServiceLost(onWifi);

        // A stopped scan says nothing about the daemon, and a gone report then threw away the port it had found
        assertFalse(mReports.contains(new Pair<String, Integer>(null, -1)));
    }

    @Test
    public void anIpv4AddressIsCheckedBeforeALinkLocalOne() throws Exception {
        mListening.add(37555);
        mScanner.start();
        discovery().onServiceFound(unresolved(NAME));
        NsdServiceInfo info = unresolved(NAME);
        info.setHostAddresses(Arrays.asList(InetAddress.getByName("fe80::1"), InetAddress.getByName(HOST)));
        info.setPort(37555);

        mFollower.sinks.get(NAME).onUpdated(info);

        assertEquals(Collections.singletonList(new Pair<>(HOST, 37555)), mReports);
    }

    @Test
    public void aLostDaemonIsForgottenAndFollowedAgainWhenItComesBack() throws Exception {
        mListening.add(37555);
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        discovery().onServiceFound(found);
        mFollower.sinks.get(NAME).onUpdated(service(NAME, HOST, 37555));

        // Wireless debugging was turned off
        discovery().onServiceLost(found);
        assertTrue(mFollower.stopped.contains(NAME));
        assertEquals(new Pair<String, Integer>(null, -1), mReports.get(mReports.size() - 1));

        // And on again, on a new port under the same name
        mListening.add(40111);
        discovery().onServiceFound(unresolved(NAME));
        assertEquals("followed again", 2, mFollower.follows);
        mFollower.sinks.get(NAME).onUpdated(service(NAME, HOST, 40111));
        assertEquals(new Pair<>(HOST, 40111), mReports.get(mReports.size() - 1));

        mFollower.sinks.get(NAME).onLost();
        assertEquals(new Pair<String, Integer>(null, -1), mReports.get(mReports.size() - 1));
    }

    @Test
    public void anotherServiceGoingAwayDoesNotTakeBackTheReportedOne() throws Exception {
        mListening.add(37555);
        mScanner.start();
        NsdServiceInfo other = unresolved("adb-other-phone");
        discovery().onServiceFound(unresolved(NAME));
        discovery().onServiceFound(other);
        mFollower.sinks.get(NAME).onUpdated(service(NAME, HOST, 37555));

        discovery().onServiceLost(other);

        assertEquals(Collections.singletonList(new Pair<>(HOST, 37555)), mReports);
    }

    @Test
    public void whenFollowingFailsAOneOffResolveStillFindsIt() throws Exception {
        mListening.add(37555);
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        discovery().onServiceFound(found);

        mFollower.sinks.get(NAME).onFailed(NsdManager.FAILURE_INTERNAL_ERROR);
        assertEquals(1, mShadowNsd.getResolveListeners(found).size());
        mShadowNsd.getResolveListeners(found).get(0).onServiceResolved(service(NAME, HOST, 37555));

        assertEquals(Collections.singletonList(new Pair<>(HOST, 37555)), mReports);
    }

    @Test
    public void stoppingEndsTheDiscoveryAndEveryFollow() throws Exception {
        mListening.add(37555);
        mScanner.start();
        NsdManager.DiscoveryListener discovery = discovery();
        discovery.onServiceFound(unresolved(NAME));
        AdbServiceFollower.FollowSink sink = mFollower.sinks.get(NAME);

        mScanner.stop();

        assertTrue(mFollower.stopped.contains(NAME));
        assertTrue(isEmpty(mShadowNsd.getDiscoveryListeners(TYPE)));
        // Late callbacks after the scan ended change nothing
        sink.onUpdated(service(NAME, HOST, 37555));
        discovery.onServiceFound(unresolved("adb-late"));
        assertTrue(mReports.isEmpty());
        assertEquals(1, mFollower.follows);
        mScanner.stop();
    }

    @Test
    public void aServiceLostWhileItsFollowStartsIsStoppedRightAway() {
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        mFollower.onFollow = () -> discovery().onServiceLost(found);

        discovery().onServiceFound(found);

        assertTrue(mFollower.stopped.contains(NAME));
    }

    @Test
    public void aTakenPortIsOneSomethingListensOn() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        int port;
        try (ServerSocket daemon = new ServerSocket(0, 1, loopback)) {
            port = daemon.getLocalPort();
            assertTrue(AdbServiceFollower.isPortTaken(loopback, port));
            assertTrue(AdbServiceFollower.isOwnListeningDaemon(loopback, port));
            // TEST-NET-1 is never this machine's
            assertFalse(AdbServiceFollower.isOwnListeningDaemon(InetAddress.getByName("192.0.2.1"), port));
        }
        assertFalse("free once the daemon's gone", AdbServiceFollower.isPortTaken(loopback, port));
    }

    @Test
    public void theSameDaemonOnTwoNetworksIsStillReportedWhenOneGoesAway() throws Exception {
        mListening.add(37555);
        mScanner.start();
        Network wifi = ShadowNetwork.newInstance(100);
        Network hotspot = ShadowNetwork.newInstance(101);
        NsdServiceInfo onHotspot = on(unresolved(NAME), hotspot);
        discovery().onServiceFound(on(unresolved(NAME), wifi));
        discovery().onServiceFound(onHotspot);
        assertEquals("followed on each network", 2, mFollower.follows);
        mFollower.sinkOn(wifi).onUpdated(on(service(NAME, HOST, 37555), wifi));
        mFollower.sinkOn(hotspot).onUpdated(on(service(NAME, HOTSPOT_HOST, 37555), hotspot));

        // The hotspot is turned off, and the daemon is still there on Wi-Fi
        discovery().onServiceLost(onHotspot);

        assertTrue(mFollower.stopped.contains(NAME));
        assertFalse("never reported gone", mReports.contains(new Pair<String, Integer>(null, -1)));
        assertEquals(new Pair<>(HOST, 37555), mReports.get(mReports.size() - 1));
        // And Wi-Fi is still followed
        mListening.add(40111);
        mFollower.sinkOn(wifi).onUpdated(on(service(NAME, HOST, 40111), wifi));
        assertEquals(new Pair<>(HOST, 40111), mReports.get(mReports.size() - 1));
    }

    @Test
    public void onlyAPortInUseCountsAsTaken() {
        // A link-local address still settling just after Wi-Fi comes back, or a bind that isn't
        // allowed, said the old port was taken and it was reported again
        assertFalse(AdbServiceFollower.isAddressInUse(bindFailure(OsConstants.EADDRNOTAVAIL)));
        assertFalse(AdbServiceFollower.isAddressInUse(bindFailure(OsConstants.EACCES)));
        assertFalse(AdbServiceFollower.isAddressInUse(bindFailure(OsConstants.EPERM)));
        assertFalse(AdbServiceFollower.isAddressInUse(new SocketException("not a bind")));
        assertTrue(AdbServiceFollower.isAddressInUse(bindFailure(OsConstants.EADDRINUSE)));
    }

    @Test
    public void aOneOffResolveStillRunningIsCancelledWhenTheScanStops() throws Exception {
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        discovery().onServiceFound(found);
        mFollower.sinks.get(NAME).onFailed(NsdManager.FAILURE_INTERNAL_ERROR);
        assertEquals(1, mShadowNsd.getResolveListeners(found).size());

        mScanner.stop();

        // NSD keeps a resolve nobody cancels, and each one counts against the app's limit
        assertEquals(Collections.singletonList(NAME), mFollower.resolvesStopped);
    }

    @Test
    public void aOneOffResolveIsCancelledWhenItsServiceIsLost() {
        mScanner.start();
        NsdServiceInfo found = unresolved(NAME);
        discovery().onServiceFound(found);
        mFollower.sinks.get(NAME).onFailed(NsdManager.FAILURE_INTERNAL_ERROR);

        discovery().onServiceLost(found);

        assertEquals(Collections.singletonList(NAME), mFollower.resolvesStopped);
    }

    @Test
    public void stoppingWhileAOneOffResolveStartsCancelsIt() {
        mScanner.start();
        discovery().onServiceFound(unresolved(NAME));
        mFollower.onResolve = () -> mScanner.stop();

        mFollower.sinks.get(NAME).onFailed(NsdManager.FAILURE_INTERNAL_ERROR);

        assertEquals(Collections.singletonList(NAME), mFollower.resolvesStopped);
    }

    @Test
    public void stoppingWhileAFollowStartsStopsItRightAway() {
        mScanner.start();
        mFollower.onFollow = () -> mScanner.stop();

        discovery().onServiceFound(unresolved(NAME));

        assertTrue(mFollower.stopped.contains(NAME));
    }

    @Test
    public void wirelessDebuggingScansWithTheFollowerOnThisAndroid() {
        Context context = RuntimeEnvironment.getApplication();
        AdbMdns.OnAdbDaemonDiscoveredListener none = (host, port) -> {
        };
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        assertTrue(AdbUtils.scanner(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, none) instanceof AdbServiceFollower);
        assertFalse(AdbUtils.scanner(context, AdbMdns.SERVICE_TYPE_TLS_PAIRING, none) instanceof AdbServiceFollower);
        assertFalse(AdbUtils.scanner(context, AdbMdns.SERVICE_TYPE_ADB, none) instanceof AdbServiceFollower);
    }

    @NonNull
    private AdbServiceFollower scanner(@NonNull AdbMdns.OnAdbDaemonDiscoveredListener listener) {
        return new AdbServiceFollower(mNsd, AdbMdns.SERVICE_TYPE_TLS_CONNECT, listener, mFollower,
                (host, port) -> {
                    if (mOnCheck != null) {
                        mOnCheck.run();
                    }
                    return OWN_HOSTS.contains(host.getHostAddress()) && mListening.contains(port);
                },
                host -> OWN_HOSTS.contains(host.getHostAddress()));
    }

    @NonNull
    private NsdManager.DiscoveryListener discovery() {
        List<NsdManager.DiscoveryListener> listeners = mShadowNsd.getDiscoveryListeners(TYPE);
        assertEquals(1, listeners.size());
        return listeners.get(0);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static final class FakeFollower implements AdbServiceFollower.ServiceFollower {
        final Map<String, AdbServiceFollower.FollowSink> sinks = new HashMap<>();
        final List<Pair<NsdServiceInfo, AdbServiceFollower.FollowSink>> all = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();
        final List<String> resolvesStopped = new ArrayList<>();
        NsdManager nsd;
        int follows;
        Runnable onFollow;
        Runnable onResolve;

        @NonNull
        @Override
        public Runnable follow(@NonNull NsdServiceInfo service, @NonNull AdbServiceFollower.FollowSink sink) {
            String name = service.getServiceName();
            sinks.put(name, sink);
            all.add(new Pair<>(service, sink));
            follows++;
            if (onFollow != null) {
                onFollow.run();
            }
            return () -> stopped.add(name);
        }

        @NonNull
        @Override
        @SuppressWarnings("deprecation")
        public Runnable resolveOnce(@NonNull NsdServiceInfo service, @NonNull NsdManager.ResolveListener listener) {
            nsd.resolveService(service, listener);
            if (onResolve != null) {
                onResolve.run();
            }
            return () -> resolvesStopped.add(service.getServiceName());
        }

        @NonNull
        AdbServiceFollower.FollowSink sinkOn(@NonNull Network network) {
            for (Pair<NsdServiceInfo, AdbServiceFollower.FollowSink> followed : all) {
                if (network.equals(followed.first.getNetwork())) {
                    return followed.second;
                }
            }
            throw new AssertionError("not followed on " + network);
        }
    }

    @NonNull
    private static NsdServiceInfo on(@NonNull NsdServiceInfo info, @NonNull Network network) {
        info.setNetwork(network);
        return info;
    }

    @NonNull
    private static BindException bindFailure(int errno) {
        BindException e = new BindException("bind failed");
        e.initCause(new ErrnoException("bind", errno));
        return e;
    }

    @NonNull
    private static NsdServiceInfo unresolved(@NonNull String name) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(name);
        info.setServiceType(TYPE);
        return info;
    }

    @NonNull
    private static NsdServiceInfo service(@NonNull String name, @NonNull String host, int port)
            throws UnknownHostException {
        NsdServiceInfo info = unresolved(name);
        info.setHostAddresses(Collections.singletonList(InetAddress.getByName(host)));
        info.setPort(port);
        return info;
    }
}
