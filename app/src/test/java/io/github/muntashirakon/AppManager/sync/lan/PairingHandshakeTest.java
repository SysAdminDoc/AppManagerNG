// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class PairingHandshakeTest {
    // CLIENT_HELLO: magic(4) version(1) pub(65) nonce(32) name. HOST_REVEAL: pub(65) nonce(32).
    private static final int CLIENT_PUB_OFFSET = 5;
    private static final int CLIENT_NONCE_OFFSET = CLIENT_PUB_OFFSET + SyncCrypto.POINT_LENGTH;

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;

    @Before
    public void setUp() throws GeneralSecurityException {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(11));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(12));
    }

    @After
    public void tearDown() {
        mExecutor.shutdownNow();
    }

    @Test
    public void honestPairDerivesTheSameKeysAndDigits() throws Exception {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        PairingHandshake.Agreement[] pair = agree(duplex.endpointA(), duplex.endpointB());
        PairingHandshake.Agreement host = pair[0];
        PairingHandshake.Agreement client = pair[1];

        assertEquals(host.getSas(), client.getSas());
        assertTrue(host.getSas(), host.getSas().matches("[0-9]{6}"));
        assertArrayEquals(host.transcript, client.transcript);
        assertEquals("Pixel client", host.getPeerName());
        assertEquals("Galaxy host", client.getPeerName());

        PairedSession[] sessions = authenticate(host, client);
        PairedSession hostSession = sessions[0];
        PairedSession clientSession = sessions[1];
        assertEquals(mClientIdentity.getFingerprint(), hostSession.getPeerFingerprint());
        assertEquals(mHostIdentity.getFingerprint(), clientSession.getPeerFingerprint());
        assertTrue(hostSession.isHost());
        assertFalse(clientSession.isHost());

        // Keys line up in both directions
        clientSession.getChannel().send(utf8("LIST"));
        assertArrayEquals(utf8("LIST"), hostSession.getChannel().receive(64));
        hostSession.getChannel().send(utf8("apps"));
        assertArrayEquals(utf8("apps"), clientSession.getChannel().receive(64));
    }

    @Test
    public void substitutedClientKeyChangesTheDigitsAndKillsTheSession() throws Exception {
        final byte[] attackerPub = SyncCrypto.encodePoint(SyncCrypto.generateKeyPair(new SeededRandom(99)).getPublic());
        FrameRelay.Link link = new FrameRelay.Link((index, frame) -> {
            if (index == 0) {
                byte[] copy = frame.clone();
                System.arraycopy(attackerPub, 0, copy, CLIENT_PUB_OFFSET, attackerPub.length);
                return copy;
            }
            return frame;
        }, FrameRelay.PASS);

        PairingHandshake.Agreement[] pair = agree(link.host, link.client);

        assertFalse(Arrays.equals(pair[0].transcript, pair[1].transcript));
        assertNotEquals("the digits on the two screens must differ", pair[0].getSas(), pair[1].getSas());
        assertBothAuthenticationsFail(pair);
    }

    @Test
    public void alteredClientNonceChangesTheDigits() throws Exception {
        FrameRelay.Link link = new FrameRelay.Link((index, frame) -> {
            if (index == 0) {
                byte[] copy = frame.clone();
                copy[CLIENT_NONCE_OFFSET] ^= 0x01;
                return copy;
            }
            return frame;
        }, FrameRelay.PASS);

        PairingHandshake.Agreement[] pair = agree(link.host, link.client);

        assertNotEquals(pair[0].getSas(), pair[1].getSas());
        assertBothAuthenticationsFail(pair);
    }

    @Test
    public void hostKeyThatDoesNotMatchItsCommitmentAborts() throws Exception {
        final byte[] attackerPub = SyncCrypto.encodePoint(SyncCrypto.generateKeyPair(new SeededRandom(98)).getPublic());
        FrameRelay.Link link = new FrameRelay.Link(FrameRelay.PASS, (index, frame) -> {
            if (index == 1) {
                byte[] copy = frame.clone();
                System.arraycopy(attackerPub, 0, copy, 0, attackerPub.length);
                return copy;
            }
            return frame;
        });
        Future<PairingHandshake.Agreement> host = mExecutor.submit(hostTask(link.host));
        Future<PairingHandshake.Agreement> client = mExecutor.submit(clientTask(link.client));

        Throwable cause = failureOf(client);
        assertTrue(String.valueOf(cause), cause instanceof SyncProtocolException);
        assertTrue(cause.getMessage(), cause.getMessage().contains("commit"));
        try {
            PairingHandshake.Agreement hostAgreement = host.get(20, TimeUnit.SECONDS);
            // The host cannot tell yet, but the client hung up, so its first read fails
            assertThrows(IOException.class, () -> PairingHandshake.authenticate(hostAgreement, mHostIdentity));
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IOException);
        }
    }

    @Test
    public void hostNonceThatDoesNotMatchItsCommitmentAborts() throws Exception {
        FrameRelay.Link link = new FrameRelay.Link(FrameRelay.PASS, (index, frame) -> {
            if (index == 1) {
                byte[] copy = frame.clone();
                copy[copy.length - 1] ^= 0x40;
                return copy;
            }
            return frame;
        });
        mExecutor.submit(hostTask(link.host));
        Throwable cause = failureOf(mExecutor.submit(clientTask(link.client)));
        assertTrue(String.valueOf(cause), cause instanceof SyncProtocolException);
        assertTrue(cause.getMessage(), cause.getMessage().contains("commit"));
    }

    @Test
    public void pointOffTheCurveIsRefused() throws Exception {
        FrameRelay.Link link = new FrameRelay.Link((index, frame) -> {
            if (index == 0) {
                byte[] copy = frame.clone();
                copy[CLIENT_NONCE_OFFSET - 1] ^= 0x01; // last byte of Y
                return copy;
            }
            return frame;
        }, FrameRelay.PASS);
        mExecutor.submit(clientTask(link.client));
        Throwable cause = failureOf(mExecutor.submit(hostTask(link.host)));
        assertTrue(String.valueOf(cause), cause instanceof SyncProtocolException);
    }

    @Test
    public void wrongVersionIsRefused() throws Exception {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{'A', 'M', 'N', 'G'});
        out.writeByte(PairingHandshake.PROTOCOL_VERSION + 1);
        out.write(new byte[SyncCrypto.HASH_LENGTH]);
        out.writeUTF("future host");
        duplex.endpointA().writeFrame(bytes.toByteArray());

        SyncProtocolException e = assertThrows(SyncProtocolException.class,
                () -> PairingHandshake.client(duplex.endpointB(), "client", new SeededRandom(3)));
        assertTrue(e.getMessage(), e.getMessage().contains("version"));
    }

    @Test
    public void somethingThatIsNotAPairingHelloIsRefused() throws Exception {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        duplex.endpointA().writeFrame("GET / HTTP/1.1".getBytes(StandardCharsets.US_ASCII));

        assertThrows(SyncProtocolException.class,
                () -> PairingHandshake.client(duplex.endpointB(), "client", new SeededRandom(3)));
    }

    @Test
    public void identityProofIsBoundToTheTranscriptAndRole() throws Exception {
        byte[] transcript = SyncCrypto.hash("t", utf8("session one"));
        byte[] otherTranscript = SyncCrypto.hash("t", utf8("session two"));
        byte client = PairingHandshake.roleByte(false);
        byte host = PairingHandshake.roleByte(true);
        byte[] frame = PairingHandshake.buildIdentityFrame(mClientIdentity, client, transcript);

        assertArrayEquals(mClientIdentity.getPublicPoint(),
                PairingHandshake.checkIdentityFrame(frame, client, transcript));
        assertThrows(SyncProtocolException.class,
                () -> PairingHandshake.checkIdentityFrame(frame, client, otherTranscript));
        assertThrows(SyncProtocolException.class,
                () -> PairingHandshake.checkIdentityFrame(frame, host, transcript));

        byte[] swapped = frame.clone();
        byte[] otherPub = mHostIdentity.getPublicPoint();
        System.arraycopy(otherPub, 0, swapped, 1, otherPub.length);
        assertThrows(SyncProtocolException.class,
                () -> PairingHandshake.checkIdentityFrame(swapped, client, transcript));
    }

    @Test
    public void storedIdentityRestoresToTheSameFingerprint() throws Exception {
        LocalIdentity restored = LocalIdentity.restore(mHostIdentity.getEncodedPrivateKey(),
                mHostIdentity.getPublicPoint());
        assertEquals(mHostIdentity.getFingerprint(), restored.getFingerprint());
        byte[] transcript = SyncCrypto.hash("t", utf8("x"));
        byte[] frame = PairingHandshake.buildIdentityFrame(restored, PairingHandshake.roleByte(true), transcript);
        assertArrayEquals(mHostIdentity.getPublicPoint(),
                PairingHandshake.checkIdentityFrame(frame, PairingHandshake.roleByte(true), transcript));
    }

    @Test
    public void namesAreCleanedAndCapped() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 200; ++i) {
            longName.append('x');
        }
        assertEquals(PairingHandshake.MAX_NAME_CHARS, PairingHandshake.cleanName(longName.toString()).length());
        assertEquals("Phone", PairingHandshake.cleanName("  Pho\nne" + (char) 0 + " "));
        assertEquals("?", PairingHandshake.cleanName("\t"));
    }

    @Test
    public void hkdfMatchesRfc5869TestCaseOne() throws Exception {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] okm = SyncCrypto.hkdf(SyncCrypto.fromHex("000102030405060708090a0b0c"), ikm,
                SyncCrypto.fromHex("f0f1f2f3f4f5f6f7f8f9"), 42);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                SyncCrypto.toHex(okm));
    }

    // Helpers

    @NonNull
    private PairingHandshake.Agreement[] agree(@NonNull FrameIo hostIo, @NonNull FrameIo clientIo) throws Exception {
        Future<PairingHandshake.Agreement> host = mExecutor.submit(hostTask(hostIo));
        Future<PairingHandshake.Agreement> client = mExecutor.submit(clientTask(clientIo));
        return new PairingHandshake.Agreement[]{host.get(20, TimeUnit.SECONDS), client.get(20, TimeUnit.SECONDS)};
    }

    @NonNull
    private PairedSession[] authenticate(@NonNull PairingHandshake.Agreement host,
                                         @NonNull PairingHandshake.Agreement client) throws Exception {
        Future<PairedSession> h = mExecutor.submit(() -> PairingHandshake.authenticate(host, mHostIdentity));
        Future<PairedSession> c = mExecutor.submit(() -> PairingHandshake.authenticate(client, mClientIdentity));
        return new PairedSession[]{h.get(20, TimeUnit.SECONDS), c.get(20, TimeUnit.SECONDS)};
    }

    private void assertBothAuthenticationsFail(@NonNull PairingHandshake.Agreement[] pair) throws Exception {
        Future<PairedSession> h = mExecutor.submit(() -> PairingHandshake.authenticate(pair[0], mHostIdentity));
        Future<PairedSession> c = mExecutor.submit(() -> PairingHandshake.authenticate(pair[1], mClientIdentity));
        assertTrue(failureOf(h) instanceof IOException);
        assertTrue(failureOf(c) instanceof IOException);
        assertTrue(pair[0].getChannel().isDead());
        assertTrue(pair[1].getChannel().isDead());
    }

    @NonNull
    private static Callable<PairingHandshake.Agreement> hostTask(@NonNull FrameIo io) {
        return () -> PairingHandshake.host(io, "Galaxy host", new SeededRandom(1));
    }

    @NonNull
    private static Callable<PairingHandshake.Agreement> clientTask(@NonNull FrameIo io) {
        return () -> PairingHandshake.client(io, "Pixel client", new SeededRandom(2));
    }

    @NonNull
    private static Throwable failureOf(@NonNull Future<?> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
        fail("expected the step to fail");
        throw new AssertionError();
    }

    @NonNull
    private static byte[] utf8(@NonNull String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
