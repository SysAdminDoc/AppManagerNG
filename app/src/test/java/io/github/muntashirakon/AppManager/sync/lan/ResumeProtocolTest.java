// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.app;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

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
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Revision 2 of the sync messages: a client asks the host's revision, then continues a broken
 * download from where it stopped. Hosts from the first release must keep working, which these
 * tests emulate with {@link HostSession#setRevision(int)}.
 */
@RunWith(RobolectricTestRunner.class)
public class ResumeProtocolTest {
    private static final String PKG = "com.example.app";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private TestPipe.Duplex mDuplex;
    private byte[] mContent;

    @Before
    public void setUp() throws GeneralSecurityException, IOException {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(21));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(22));
        mCatalog = new SyncFixtures.Catalog();
        mContent = new byte[3 * SyncMessages.MAX_CHUNK + 123];
        new Random(9).nextBytes(mContent);
        File base = new File(mTemp.getRoot(), "base.apk");
        try (OutputStream out = new FileOutputStream(base)) {
            out.write(mContent);
        }
        mCatalog.add(app(PKG, 2, Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", mContent.length))), base);
    }

    @After
    public void tearDown() {
        mExecutor.shutdownNow();
    }

    @Test
    public void aResumedDownloadSendsOnlyTheRestAndChecksTheWholeFile() throws Exception {
        int offset = SyncMessages.MAX_CHUNK + 17;
        Future<Void> host = startHost(SyncMessages.REVISION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicLong firstProgress = new AtomicLong(-1);
        AtomicLong lastProgress = new AtomicLong(-1);
        byte[] digest;
        try (SyncClientSession client = connect()) {
            assertEquals(SyncMessages.REVISION, client.hostRevision());
            assertTrue(client.canResume());
            digest = client.download(PKG, "base.apk", mContent.length, offset, prefixDigest(offset), out, done -> {
                firstProgress.compareAndSet(-1, done);
                lastProgress.set(done);
            });
            // A keepalive after the revision query is still the bare frame both sides know
            client.ping();
        }
        host.get(10, TimeUnit.SECONDS);

        assertArrayEquals(Arrays.copyOfRange(mContent, offset, mContent.length), out.toByteArray());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(mContent), digest);
        assertTrue("Progress counts from the offset", firstProgress.get() > offset);
        assertEquals(mContent.length, lastProgress.get());
    }

    @Test
    public void aPartThatIsAlreadyWholeOnlyNeedsTheDigest() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] digest;
        try (SyncClientSession client = connect()) {
            digest = client.download(PKG, "base.apk", mContent.length, mContent.length,
                    prefixDigest(mContent.length), out, null);
        }
        host.get(10, TimeUnit.SECONDS);

        assertEquals(0, out.size());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(mContent), digest);
    }

    @Test
    public void aPartThatDoesNotMatchTheHostsFileFailsTheDigest() throws Exception {
        int offset = 1000;
        Future<Void> host = startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            MessageDigest wrongPrefix = SyncCrypto.sha256();
            wrongPrefix.update(new byte[offset]);
            assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk", mContent.length,
                    offset, wrongPrefix, new ByteArrayOutputStream(), null));
            assertTrue(client.isClosed());
        } finally {
            client.close();
        }
        host.cancel(true);
    }

    @Test
    public void aFirstReleaseHostReadsAsRevisionOneAndStillServesWholeFiles() throws Exception {
        Future<Void> host = startHost(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] digest;
        try (SyncClientSession client = connect()) {
            assertEquals(1, client.hostRevision());
            assertFalse(client.canResume());
            // A ranged request would end an old host's session, so the client never sends one
            assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk", mContent.length,
                    100, prefixDigest(100), new ByteArrayOutputStream(), null));
            assertFalse(client.isClosed());
            assertTrue(mCatalog.fileLookups.isEmpty());
            digest = client.download(PKG, "base.apk", mContent.length, out, null);
            client.ping();
        }
        host.get(10, TimeUnit.SECONDS);

        assertArrayEquals(mContent, out.toByteArray());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(mContent), digest);
    }

    @Test
    public void aFirstReleaseClientGetsTheSameFramesAsBefore() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.simple(SyncMessages.PING));
            assertArrayEquals(SyncMessages.simple(SyncMessages.PONG), channel.receive(SyncMessages.MAX_CONTROL));
            channel.send(SyncMessages.getRequest(PKG, "base.apk", mContent.length));
            assertArrayEquals(SyncMessages.fileHeader(mContent.length), channel.receive(SyncMessages.MAX_CONTROL));
        } finally {
            client.getPairedSession().getChannel().close();
        }
        host.cancel(true);
    }

    @Test
    public void aRangedRequestPastTheEndIsRefusedAndEndsTheSession() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            assertThrows(SyncProtocolException.class, () -> client.download(PKG, "base.apk", mContent.length,
                    mContent.length + 1, prefixDigest(10), new ByteArrayOutputStream(), null));
            // A hostile client sends it anyway
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.getRequest(PKG, "base.apk", mContent.length, mContent.length + 1));
            byte[] reply = channel.receive(SyncMessages.MAX_CONTROL);
            assertEquals(SyncMessages.ERR_BAD_REQUEST, SyncMessages.read(reply, SyncMessages.ERROR).readInt());
            host.get(10, TimeUnit.SECONDS);
            assertThrows(IOException.class, () -> channel.receive(SyncMessages.MAX_CONTROL));
        } finally {
            client.close();
        }
    }

    // Helpers

    @NonNull
    private MessageDigest prefixDigest(int length) throws GeneralSecurityException {
        MessageDigest digest = SyncCrypto.sha256();
        digest.update(mContent, 0, Math.min(length, mContent.length));
        return digest;
    }

    @NonNull
    private Future<Void> startHost(int revision) {
        mDuplex = new TestPipe.Duplex();
        FrameIo io = mDuplex.endpointA();
        return mExecutor.submit(() -> {
            HostSession session = new HostSession(io, HostSession.NO_TIMEOUTS, "Galaxy host", mHostIdentity,
                    new SeededRandom(31), new SyncFixtures.MemoryTrust(), s -> ApprovalGate.Decision.ALLOW,
                    mCatalog, null);
            session.setRevision(revision);
            session.run();
            return null;
        });
    }

    @NonNull
    private SyncClientSession connect() throws IOException {
        return SyncClientSession.connect(mDuplex.endpointB(), HostSession.NO_TIMEOUTS, "Pixel client",
                mClientIdentity, new SeededRandom(32), null);
    }
}
