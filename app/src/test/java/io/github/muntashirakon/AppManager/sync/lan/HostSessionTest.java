// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.app;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.muntashirakon.AppManager.sync.lan.SyncComparator.Status;

@RunWith(RobolectricTestRunner.class)
public class HostSessionTest {
    private static final String PKG = "com.example.app";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.MemoryTrust mTrust;
    private SyncFixtures.Catalog mCatalog;
    private final AtomicInteger mApprovals = new AtomicInteger();
    private ApprovalGate.Decision mDecision = ApprovalGate.Decision.ALLOW;
    private final HostSession.Approver mApprover = session -> {
        mApprovals.incrementAndGet();
        return mDecision;
    };

    @Before
    public void setUp() throws GeneralSecurityException {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(21));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(22));
        mTrust = new SyncFixtures.MemoryTrust();
        mCatalog = new SyncFixtures.Catalog();
    }

    @After
    public void tearDown() {
        mExecutor.shutdownNow();
    }

    @Test
    public void listCarriesEveryFieldAndSortsIntoBuckets() throws Exception {
        mCatalog.apps.add(app("com.example.newer", 5, SIGNER_A));
        mCatalog.apps.add(app("com.example.same", 3, SIGNER_A));
        mCatalog.apps.add(app("com.example.older", 1, SIGNER_A));
        mCatalog.apps.add(app("com.example.absent", 1, SIGNER_A));
        mCatalog.apps.add(app("com.example.resigned", 9, SIGNER_B));
        mCatalog.apps.add(new SyncApp("com.example.split", "Split app", (7L << 32) | 3, "7.3", true,
                Collections.singletonList(SIGNER_B), Arrays.asList(SIGNER_A, SIGNER_B),
                Arrays.asList(new SyncApp.ApkFileInfo("base.apk", 1000), new SyncApp.ApkFileInfo("split_config.en.apk", 20))));
        Map<String, SyncApp> local = new HashMap<>();
        local.put("com.example.newer", app("com.example.newer", 4, SIGNER_A));
        local.put("com.example.same", app("com.example.same", 3, SIGNER_A));
        local.put("com.example.older", app("com.example.older", 2, SIGNER_A));
        local.put("com.example.resigned", app("com.example.resigned", 1, SIGNER_A));

        Future<Void> host = startHost();
        List<SyncApp> apps;
        try (SyncClientSession client = connect()) {
            apps = client.list(false);
        }
        host.get(10, TimeUnit.SECONDS);

        assertEquals(6, apps.size());
        Map<String, Status> buckets = new HashMap<>();
        for (SyncApp remote : apps) {
            buckets.put(remote.packageName, SyncComparator.compare(remote, local.get(remote.packageName)));
        }
        assertEquals(Status.NEWER, buckets.get("com.example.newer"));
        assertEquals(Status.SAME, buckets.get("com.example.same"));
        assertEquals(Status.OLDER, buckets.get("com.example.older"));
        assertEquals(Status.NOT_INSTALLED, buckets.get("com.example.absent"));
        assertEquals(Status.SIGNATURE_DIFFERS, buckets.get("com.example.resigned"));

        SyncApp split = apps.get(5);
        assertEquals("com.example.split", split.packageName);
        assertEquals("Split app", split.label);
        assertEquals((7L << 32) | 3, split.versionCode);
        assertEquals("7.3", split.versionName);
        assertTrue(split.system);
        assertEquals(Collections.singletonList(SIGNER_B), split.signers);
        assertEquals(Arrays.asList(SIGNER_A, SIGNER_B), split.pastSigners);
        assertEquals(2, split.files.size());
        assertEquals("split_config.en.apk", split.files.get(1).name);
        assertEquals(20, split.files.get(1).size);
        assertEquals(1020, split.totalSize());
    }

    @Test
    public void downloadDeliversTheExactBytesAndDigest() throws Exception {
        byte[] content = new byte[3 * SyncMessages.MAX_CHUNK + 123];
        new Random(5).nextBytes(content);
        File base = write("base.apk", content);
        mCatalog.add(app(PKG, 2, Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", content.length))), base);

        Future<Void> host = startHost();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicLong progress = new AtomicLong();
        byte[] digest;
        try (SyncClientSession client = connect()) {
            digest = client.download(PKG, "base.apk", content.length, out, progress::set);
        }
        host.get(10, TimeUnit.SECONDS);

        assertArrayEquals(content, out.toByteArray());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(content), digest);
        assertEquals(content.length, progress.get());
    }

    @Test
    public void getWithAPathIsRefusedAndEndsTheSession() throws Exception {
        mCatalog.add(appWithFile(), write("base.apk", new byte[10]));
        Future<Void> host = startHost();
        SyncClientSession client = connect();
        try {
            // The client refuses to build such a request itself
            assertThrows(SyncProtocolException.class,
                    () -> client.download(PKG, "../base.apk", 10, new ByteArrayOutputStream(), null));
            // A hostile client sends it anyway
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.getRequest(PKG, "../base.apk", 10));
            assertError(SyncMessages.ERR_BAD_REQUEST, channel.receive(SyncMessages.MAX_CONTROL));
            host.get(10, TimeUnit.SECONDS);
            assertThrows(IOException.class, () -> channel.receive(SyncMessages.MAX_CONTROL));
            assertTrue("A refused name must never reach the catalog", mCatalog.fileLookups.isEmpty());
        } finally {
            client.close();
        }
    }

    @Test
    public void getOfAFileOutsideTheInstallIsNotFoundAndTheSessionGoesOn() throws Exception {
        File base = write("base.apk", new byte[10]);
        File other = write("other.apk", new byte[10]);
        assertTrue(other.isFile());
        mCatalog.add(appWithFile(), base);
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            SyncClientSession.RemoteError error = assertThrows(SyncClientSession.RemoteError.class,
                    () -> client.download(PKG, "other.apk", 10, new ByteArrayOutputStream(), null));
            assertEquals(SyncMessages.ERR_NOT_FOUND, error.code);
            error = assertThrows(SyncClientSession.RemoteError.class,
                    () -> client.download("com.example.unknown", "base.apk", 10, new ByteArrayOutputStream(), null));
            assertEquals(SyncMessages.ERR_NOT_FOUND, error.code);
            assertFalse(client.isClosed());
            assertEquals(1, client.list(false).size());
        }
        host.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void getWithAStaleSizeReportsAChange() throws Exception {
        mCatalog.add(appWithFile(), write("base.apk", new byte[10]));
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            SyncClientSession.RemoteError error = assertThrows(SyncClientSession.RemoteError.class,
                    () -> client.download(PKG, "base.apk", 11, new ByteArrayOutputStream(), null));
            assertEquals(SyncMessages.ERR_CHANGED, error.code);
            client.ping();
        }
        host.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void listOverTheAppCapIsRefusedByTheHost() throws Exception {
        for (int i = 0; i <= SyncMessages.MAX_APPS; ++i) {
            mCatalog.apps.add(app("com.example.a" + i, 1, SIGNER_A));
        }
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            SyncClientSession.RemoteError error = assertThrows(SyncClientSession.RemoteError.class,
                    () -> client.list(false));
            assertEquals(SyncMessages.ERR_TOO_LARGE, error.code);
        }
        host.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void unknownRequestEndsTheSession() throws Exception {
        Future<Void> host = startHost();
        SyncClientSession client = connect();
        try {
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(new byte[]{99});
            assertError(SyncMessages.ERR_BAD_REQUEST, channel.receive(SyncMessages.MAX_CONTROL));
            host.get(10, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }

    @Test
    public void deniedPairingIsRejected() throws Exception {
        mDecision = ApprovalGate.Decision.DENY;
        Future<Void> host = startHost();
        assertThrows(SyncClientSession.RejectedException.class, this::connect);
        host.get(10, TimeUnit.SECONDS);
        assertEquals(1, mApprovals.get());
        assertTrue(mTrust.list().isEmpty());
    }

    @Test
    public void allowAndRememberStoresTheClientAndSkipsTheQuestionNextTime() throws Exception {
        mDecision = ApprovalGate.Decision.ALLOW_AND_REMEMBER;
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            assertTrue(client.isRemembered());
        }
        host.get(10, TimeUnit.SECONDS);
        assertEquals(Collections.singletonMap(mClientIdentity.getFingerprint(), "Pixel client"), mTrust.list());

        mDecision = ApprovalGate.Decision.DENY;
        host = startHost();
        try (SyncClientSession client = connect()) {
            assertTrue(client.isRemembered());
        }
        host.get(10, TimeUnit.SECONDS);
        assertEquals("A remembered device must not be asked about again", 1, mApprovals.get());
    }

    @Test
    public void plainAllowDoesNotRemember() throws Exception {
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            assertFalse(client.isRemembered());
        }
        host.get(10, TimeUnit.SECONDS);
        assertTrue(mTrust.list().isEmpty());
    }

    @Test
    public void listNamingAPathIsRejectedByTheClient() throws Exception {
        SyncApp evil = app(PKG, 1, Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("../evil.apk", 1)));
        byte[] message = SyncMessages.listResponse(Collections.singletonList(evil));
        assertThrows(SyncProtocolException.class, () -> SyncMessages.parseList(message));

        SyncApp huge = app(PKG, 1, Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", SyncMessages.MAX_FILE_SIZE + 1)));
        byte[] hugeMessage = SyncMessages.listResponse(Collections.singletonList(huge));
        assertThrows(SyncProtocolException.class, () -> SyncMessages.parseList(hugeMessage));
    }

    @Test
    public void clientRefusesAHostThatSendsMoreThanItAnnounced() throws Exception {
        Future<?> host = fakeHost(channel -> {
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.fileHeader(10));
            channel.send(SyncMessages.fileChunk(new byte[20], 20));
        });
        SyncClientSession client = connect();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk", 10, out, null));
        assertTrue(client.isClosed());
        assertEquals(0, out.size());
        host.cancel(true);
    }

    @Test
    public void clientRefusesAFileWhoseDigestDoesNotMatch() throws Exception {
        Future<?> host = fakeHost(channel -> {
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.fileHeader(4));
            channel.send(SyncMessages.fileChunk(new byte[]{1, 2, 3, 4}, 4));
            channel.send(SyncMessages.fileEnd(new byte[SyncCrypto.HASH_LENGTH]));
        });
        SyncClientSession client = connect();
        assertThrows(SyncProtocolException.class,
                () -> client.download(PKG, "base.apk", 4, new ByteArrayOutputStream(), null));
        assertTrue(client.isClosed());
        host.cancel(true);
    }

    @Test
    public void clientRefusesAHeaderWithAnotherSize() throws Exception {
        Future<?> host = fakeHost(channel -> {
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.fileHeader(SyncMessages.MAX_FILE_SIZE * 2));
        });
        SyncClientSession client = connect();
        assertThrows(SyncProtocolException.class,
                () -> client.download(PKG, "base.apk", 4, new ByteArrayOutputStream(), null));
        assertTrue(client.isClosed());
        host.cancel(true);
    }

    @Test
    public void clientRefusesAnOversizedFrame() throws Exception {
        Future<?> host = fakeHost(channel -> {
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.fileHeader(SyncMessages.MAX_CHUNK + 1));
            byte[] frame = new byte[SyncMessages.MAX_CHUNK_FRAME + 1];
            frame[0] = SyncMessages.FILE_CHUNK;
            channel.send(frame);
        });
        SyncClientSession client = connect();
        assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk", SyncMessages.MAX_CHUNK + 1,
                new ByteArrayOutputStream(), null));
        assertTrue(client.isClosed());
        host.cancel(true);
    }

    @Test
    public void clientRefusesOversizedRequestsBeforeSending() throws Exception {
        mCatalog.add(appWithFile(), write("base.apk", new byte[10]));
        Future<Void> host = startHost();
        try (SyncClientSession client = connect()) {
            assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk",
                    SyncMessages.MAX_FILE_SIZE + 1, new ByteArrayOutputStream(), null));
            assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.txt", 10,
                    new ByteArrayOutputStream(), null));
            client.ping();
        }
        host.get(10, TimeUnit.SECONDS);
        assertTrue(mCatalog.fileLookups.isEmpty());
    }

    // Helpers

    private interface HostScript {
        void run(@NonNull SecureChannel channel) throws Exception;
    }

    private TestPipe.Duplex mDuplex;

    @NonNull
    private Future<Void> startHost() {
        mDuplex = new TestPipe.Duplex();
        FrameIo io = mDuplex.endpointA();
        return mExecutor.submit(() -> {
            new HostSession(io, HostSession.NO_TIMEOUTS, "Galaxy host", mHostIdentity, new SeededRandom(31), mTrust,
                    mApprover, mCatalog, null).run();
            return null;
        });
    }

    @NonNull
    private Future<?> fakeHost(@NonNull HostScript script) {
        mDuplex = new TestPipe.Duplex();
        FrameIo io = mDuplex.endpointA();
        return mExecutor.submit(() -> {
            PairingHandshake.Agreement agreement = PairingHandshake.host(io, "Hostile host", new SeededRandom(41));
            PairedSession session = PairingHandshake.authenticate(agreement, mHostIdentity);
            SecureChannel channel = session.getChannel();
            channel.send(SyncMessages.accept(false));
            script.run(channel);
            return null;
        });
    }

    @NonNull
    private SyncClientSession connect() throws IOException {
        return SyncClientSession.connect(mDuplex.endpointB(), HostSession.NO_TIMEOUTS, "Pixel client",
                mClientIdentity, new SeededRandom(32), null);
    }

    @NonNull
    private static SyncApp appWithFile() {
        return app(PKG, 2, Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 10)));
    }

    @NonNull
    private File write(@NonNull String name, @NonNull byte[] content) throws IOException {
        File file = new File(mTemp.getRoot(), name);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
        return file;
    }

    private static void assertError(int code, @Nullable byte[] message) throws IOException {
        assertTrue(message != null && message.length > 0);
        SyncMessages.Reader r = SyncMessages.read(message, SyncMessages.ERROR);
        assertEquals(code, r.readInt());
    }
}
