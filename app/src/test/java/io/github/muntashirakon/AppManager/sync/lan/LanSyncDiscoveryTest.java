// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNsdManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class LanSyncDiscoveryTest {
    private static final String OWN_FINGERPRINT = SyncFixtures.repeat('1');
    private static final String OWN_ID = LanSyncHost.announcedId(OWN_FINGERPRINT);
    private static final String OTHER_ID = "2222222222222222";

    private NsdManager mNsd;
    private ShadowNsdManager mShadowNsd;
    private final List<List<LanSyncDiscovery.Peer>> mUpdates = new ArrayList<>();
    private final LanSyncDiscovery.Listener mListener = new LanSyncDiscovery.Listener() {
        @Override
        public void onPeersChanged(@NonNull List<LanSyncDiscovery.Peer> peers) {
            mUpdates.add(peers);
        }

        @Override
        public void onDiscoveryFailed(int errorCode) {
        }
    };

    @Before
    public void setUp() {
        mNsd = (NsdManager) RuntimeEnvironment.getApplication().getSystemService(Context.NSD_SERVICE);
        mShadowNsd = Shadow.extract(mNsd);
    }

    @After
    public void tearDown() {
        ShadowNsdManager.reset();
    }

    @Test
    public void resolvedServiceBecomesAPeer() throws Exception {
        LanSyncDiscovery.Peer peer = LanSyncDiscovery.toPeer(service("Galaxy", "192.168.1.20", 4000, "1", OTHER_ID), OWN_ID);
        assertNotNull(peer);
        assertEquals("Galaxy", peer.name);
        assertEquals(InetAddress.getByName("192.168.1.20"), peer.address);
        assertEquals(4000, peer.port);
        assertNotNull(LanSyncDiscovery.toPeer(service("Pixel", "fe80::1", 4000, "1", OTHER_ID), OWN_ID));
        assertNotNull(LanSyncDiscovery.toPeer(service("Pixel", "fd12:3456::1", 4000, "1", OTHER_ID), OWN_ID));
    }

    @Test
    public void ownOtherVersionAndRemoteServicesAreSkipped() throws Exception {
        assertNull(LanSyncDiscovery.toPeer(service("Me", "192.168.1.20", 4000, "1", OWN_ID), OWN_ID));
        assertNull(LanSyncDiscovery.toPeer(service("Next", "192.168.1.20", 4000, "2", OTHER_ID), OWN_ID));
        assertNull(LanSyncDiscovery.toPeer(service("Bare", "192.168.1.20", 4000, null, null), OWN_ID));
        assertNull(LanSyncDiscovery.toPeer(service("Far", "8.8.8.8", 4000, "1", OTHER_ID), OWN_ID));
        assertNull(LanSyncDiscovery.toPeer(service("Far6", "2001:db8::1", 4000, "1", OTHER_ID), OWN_ID));
        assertNull(LanSyncDiscovery.toPeer(service("NoPort", "192.168.1.20", 0, "1", OTHER_ID), OWN_ID));
    }

    @Test
    public void android14AndLaterFollowFoundServices() {
        assertNotNull(new LanSyncDiscovery(mNsd, OWN_FINGERPRINT, mListener).getFollower());
    }

    @Test
    @Config(sdk = 33)
    public void olderAndroidResolvesFoundServicesOnce() {
        assertNull(new LanSyncDiscovery(mNsd, OWN_FINGERPRINT, mListener).getFollower());
    }

    @Test
    public void aFollowedServiceThatComesBackOnAnotherPortShowsTheNewPort() throws Exception {
        FakeFollower follower = new FakeFollower();
        LanSyncDiscovery discovery = new LanSyncDiscovery(mNsd, OWN_FINGERPRINT, mListener, follower);
        discovery.start();
        NsdManager.DiscoveryListener found = mShadowNsd.getDiscoveryListeners(LanSyncHost.SERVICE_TYPE).get(0);

        NsdServiceInfo galaxy = unresolved("Galaxy");
        found.onServiceFound(galaxy);
        found.onServiceFound(unresolved("Galaxy"));
        assertEquals("Followed once", 1, follower.sinks.size());
        assertTrue("Not resolved from the cache", isEmpty(mShadowNsd.getResolveListeners(galaxy)));

        LanSyncDiscovery.FollowSink sink = follower.sinks.get("Galaxy");
        sink.onUpdated(service("Galaxy", "192.168.1.20", 4000, "1", OTHER_ID));
        assertEquals(4000, latest().get(0).port);
        // Sharing was turned off and on again, same name, new port
        sink.onUpdated(service("Galaxy", "192.168.1.20", 38299, "1", OTHER_ID));
        assertEquals(1, latest().size());
        assertEquals(38299, latest().get(0).port);

        sink.onLost();
        assertTrue(latest().isEmpty());
        sink.onUpdated(service("Galaxy", "192.168.1.20", 38299, "1", OTHER_ID));
        assertEquals(1, latest().size());

        found.onServiceLost(galaxy);
        assertTrue(latest().isEmpty());
        assertTrue("Stops following a lost service", follower.stopped.contains("Galaxy"));

        NsdServiceInfo me = unresolved("Me");
        found.onServiceFound(me);
        int updates = mUpdates.size();
        follower.sinks.get("Me").onUpdated(service("Me", "192.168.1.21", 4001, "1", OWN_ID));
        assertEquals("This device's own service is not listed", updates, mUpdates.size());

        // When following can't start, a one-off resolve still finds it
        NsdServiceInfo pixel = unresolved("Pixel");
        found.onServiceFound(pixel);
        follower.sinks.get("Pixel").onFailed(NsdManager.FAILURE_INTERNAL_ERROR);
        assertEquals(1, mShadowNsd.getResolveListeners(pixel).size());
        mShadowNsd.getResolveListeners(pixel).get(0)
                .onServiceResolved(service("Pixel", "192.168.1.22", 4002, "1", OTHER_ID));
        assertEquals("Pixel", latest().get(0).name);

        discovery.stop();
        assertTrue(follower.stopped.contains("Me"));
        updates = mUpdates.size();
        follower.sinks.get("Me").onUpdated(service("Galaxy", "192.168.1.20", 4000, "1", OTHER_ID));
        assertEquals("Late updates after stop change nothing", updates, mUpdates.size());
    }

    @Test
    public void discoveryResolvesFoundServicesAndForgetsLostOnes() throws Exception {
        LanSyncDiscovery discovery = new LanSyncDiscovery(mNsd, OWN_FINGERPRINT, mListener, null);
        discovery.start();
        List<NsdManager.DiscoveryListener> listeners = mShadowNsd.getDiscoveryListeners(LanSyncHost.SERVICE_TYPE);
        assertEquals(1, listeners.size());
        NsdManager.DiscoveryListener found = listeners.get(0);

        NsdServiceInfo galaxy = unresolved("Galaxy");
        NsdServiceInfo me = unresolved("Me");
        found.onServiceFound(galaxy);
        found.onServiceFound(me);
        // One resolve at a time
        assertEquals(1, mShadowNsd.getResolveListeners(galaxy).size());
        assertTrue(isEmpty(mShadowNsd.getResolveListeners(me)));

        mShadowNsd.getResolveListeners(galaxy).get(0)
                .onServiceResolved(service("Galaxy", "192.168.1.20", 4000, "1", OTHER_ID));
        assertEquals(1, mUpdates.size());
        assertEquals("Galaxy", mUpdates.get(0).get(0).name);

        assertEquals(1, mShadowNsd.getResolveListeners(me).size());
        mShadowNsd.getResolveListeners(me).get(0).onServiceResolved(service("Me", "192.168.1.21", 4001, "1", OWN_ID));
        assertEquals("This device's own service is not listed", 1, mUpdates.size());

        found.onServiceLost(galaxy);
        assertEquals(2, mUpdates.size());
        assertTrue(mUpdates.get(1).isEmpty());

        discovery.stop();
        assertTrue(isEmpty(mShadowNsd.getDiscoveryListeners(LanSyncHost.SERVICE_TYPE)));
        // Late callbacks after stop change nothing
        found.onServiceFound(galaxy);
        assertEquals(2, mUpdates.size());
        discovery.stop();
    }

    @NonNull
    private List<LanSyncDiscovery.Peer> latest() {
        assertTrue(!mUpdates.isEmpty());
        return mUpdates.get(mUpdates.size() - 1);
    }

    private static final class FakeFollower implements LanSyncDiscovery.ServiceFollower {
        final Map<String, LanSyncDiscovery.FollowSink> sinks = new HashMap<>();
        final List<String> stopped = new ArrayList<>();

        @NonNull
        @Override
        public Runnable follow(@NonNull NsdServiceInfo service, @NonNull LanSyncDiscovery.FollowSink sink) {
            String name = service.getServiceName();
            sinks.put(name, sink);
            return () -> stopped.add(name);
        }
    }

    @NonNull
    private static NsdServiceInfo unresolved(@NonNull String name) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(name);
        info.setServiceType(LanSyncHost.SERVICE_TYPE);
        return info;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private static NsdServiceInfo service(@NonNull String name, @NonNull String host, int port,
                                          @Nullable String version, @Nullable String id) throws UnknownHostException {
        NsdServiceInfo info = unresolved(name);
        info.setHost(InetAddress.getByName(host));
        info.setPort(port);
        if (version != null) {
            info.setAttribute(LanSyncHost.TXT_PROTOCOL, version);
        }
        if (id != null) {
            info.setAttribute(LanSyncHost.TXT_ID, id);
        }
        return info;
    }

    private static boolean isEmpty(@Nullable List<?> list) {
        return list == null || list.isEmpty();
    }
}
