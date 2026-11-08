// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNsdManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class LanSyncHostTest {
    private ExecutorService mExecutor;
    private NsdManager mNsd;
    private ShadowNsdManager mShadowNsd;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private RecordingListener mListener;
    private LanSyncHost mHost;

    @Before
    public void setUp() throws GeneralSecurityException {
        mExecutor = Executors.newFixedThreadPool(2);
        mNsd = (NsdManager) RuntimeEnvironment.getApplication().getSystemService(Context.NSD_SERVICE);
        mShadowNsd = Shadow.extract(mNsd);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(71));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(72));
        mCatalog = new SyncFixtures.Catalog();
        mCatalog.apps.add(SyncFixtures.app("com.example.app", 3, SIGNER_A));
        mListener = new RecordingListener();
    }

    @After
    public void tearDown() {
        if (mHost != null) {
            mHost.stop();
        }
        mExecutor.shutdownNow();
        ShadowNsdManager.reset();
    }

    @Test
    public void startAnnouncesARandomPortAndServesAnAllowedClient() throws Exception {
        mListener.autoDecision = ApprovalGate.Decision.ALLOW;
        mHost = newHost(mNsd);
        int port = mHost.start();
        assertTrue(mHost.isRunning());
        assertTrue(port > 0);

        NsdManager.RegistrationListener registration = mHost.getRegistrationListener();
        assertNotNull(registration);
        NsdServiceInfo info = mShadowNsd.getRegisteredServiceInfo(registration);
        assertNotNull(info);
        assertEquals("Galaxy host", info.getServiceName());
        assertEquals(LanSyncHost.SERVICE_TYPE, info.getServiceType());
        assertEquals(port, info.getPort());
        assertEquals("1", new String(info.getAttributes().get(LanSyncHost.TXT_PROTOCOL), StandardCharsets.UTF_8));
        assertEquals(LanSyncHost.announcedId(mHostIdentity.getFingerprint()),
                new String(info.getAttributes().get(LanSyncHost.TXT_ID), StandardCharsets.UTF_8));

        try (SyncClientSession client = new LanSyncConnector().connect(InetAddress.getLoopbackAddress(), port,
                "Pixel client", mClientIdentity, new SeededRandom(73), null)) {
            List<SyncApp> apps = client.list(false);
            assertEquals(1, apps.size());
            assertEquals("com.example.app", apps.get(0).packageName);
        }
        assertTrue(mListener.ended.await(10, TimeUnit.SECONDS));
        assertEquals(Collections.singletonList("Pixel client"), mListener.accepted);
        assertTrue("The host keeps listening after a client leaves", mHost.isRunning());
    }

    @Test
    public void stopClosesTheSocketWithdrawsTheServiceAndDeniesAPendingRequest() throws Exception {
        mHost = newHost(mNsd);
        int port = mHost.start();
        NsdManager.RegistrationListener registration = mHost.getRegistrationListener();
        assertNotNull(mShadowNsd.getRegisteredServiceInfo(registration));

        Future<SyncClientSession> client = mExecutor.submit(() -> new LanSyncConnector().connect(
                InetAddress.getLoopbackAddress(), port, "Pixel client", mClientIdentity, new SeededRandom(74), null));
        ApprovalGate.Request request = mListener.requests.poll(10, TimeUnit.SECONDS);
        assertNotNull("The pairing request reached the screen", request);
        assertFalse(request.isSettled());

        mHost.stop();

        assertFalse(mHost.isRunning());
        assertTrue(request.isSettled());
        ExecutionException failure = assertThrows(ExecutionException.class, () -> client.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof IOException);
        assertNull(mHost.getRegistrationListener());
        assertNull("The service is withdrawn", mShadowNsd.getRegisteredServiceInfo(registration));
        assertTrue(mListener.ended.await(5, TimeUnit.SECONDS));
        assertRefused(port);
        // A second stop is harmless
        mHost.stop();
    }

    @Test
    public void stopWithoutAClientClosesTheListeningSocket() throws Exception {
        mHost = newHost(null);
        int port = mHost.start();
        mHost.stop();
        assertFalse(mHost.isRunning());
        assertRefused(port);
        assertThrows(IllegalStateException.class, () -> mHost.start());

        // Turned off before it got going: it must not open a socket afterwards
        LanSyncHost early = newHost(mNsd);
        early.stop();
        assertThrows(IllegalStateException.class, early::start);
        assertFalse(early.isRunning());
        assertNull(early.getRegistrationListener());
    }

    @Test
    public void startTakesThePreferredPortAndFallsBackWhenItIsTaken() throws Exception {
        mHost = newHost(mNsd);
        int port = mHost.start();
        mHost.stop();

        mHost = newHost(mNsd);
        assertEquals(port, mHost.start(port));
        NsdServiceInfo info = mShadowNsd.getRegisteredServiceInfo(mHost.getRegistrationListener());
        assertNotNull(info);
        assertEquals(port, info.getPort());

        // Still held by the running host: a second one opens on a free port instead of failing
        LanSyncHost other = newHost(null);
        try {
            int otherPort = other.start(port);
            assertTrue(otherPort > 0);
            assertNotEquals(port, otherPort);
            assertTrue(other.isRunning());
        } finally {
            other.stop();
        }
    }

    @Test
    public void secondClientIsTurnedAwayWhileOneIsConnected() throws Exception {
        mListener.autoDecision = ApprovalGate.Decision.ALLOW;
        mHost = newHost(mNsd);
        int port = mHost.start();
        try (SyncClientSession first = new LanSyncConnector().connect(InetAddress.getLoopbackAddress(), port,
                "First", mClientIdentity, new SeededRandom(75), null)) {
            assertThrows(IOException.class, () -> new LanSyncConnector().connect(InetAddress.getLoopbackAddress(),
                    port, "Second", mClientIdentity, new SeededRandom(76), null));
            first.ping();
        }
    }

    @Test
    public void connectorRefusesAddressesOutsideTheLocalNetwork() throws Exception {
        assertThrows(IOException.class, () -> new LanSyncConnector().connect(InetAddress.getByName("8.8.8.8"), 80,
                "Pixel client", mClientIdentity, new SeededRandom(77), null));
    }

    @NonNull
    private LanSyncHost newHost(@Nullable NsdManager nsd) {
        return new LanSyncHost(nsd, "Galaxy host", mHostIdentity, new SyncFixtures.MemoryTrust(), mCatalog,
                new SeededRandom(78), mListener);
    }

    private static void assertRefused(int port) {
        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
            }
        });
    }

    private static final class RecordingListener implements LanSyncHost.Listener {
        @Nullable
        volatile ApprovalGate.Decision autoDecision;
        final BlockingQueue<ApprovalGate.Request> requests = new LinkedBlockingQueue<>();
        final List<String> accepted = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch ended = new CountDownLatch(1);

        @Override
        public void onApprovalRequest(@Nullable ApprovalGate.Request request) {
            if (request == null) {
                return;
            }
            requests.add(request);
            ApprovalGate.Decision decision = autoDecision;
            if (decision != null) {
                request.decide(decision);
            }
        }

        @Override
        public void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint) {
            accepted.add(peerName);
        }

        @Override
        public void onFileSent(@NonNull String packageName, @NonNull String fileName) {
        }

        @Override
        public void onSessionEnded(@Nullable String error) {
            ended.countDown();
        }

        @Override
        public void onHostFailed(@NonNull String error) {
        }
    }
}
