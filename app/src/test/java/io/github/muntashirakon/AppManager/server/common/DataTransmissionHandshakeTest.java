// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Each side proves it holds the token without sending it. Upstream App Manager's server, a stale
 * server with another token, or anything else listening on the port can take the connection, but
 * it can't answer the challenge and never sees the token.
 */
public class DataTransmissionHandshakeTest {
    private static final String TOKEN = "4f1c9a7e2b6d8035c1e7a9f2d4b6083e5a7c9e1f3b5d7092c4e6a8f0b2d4f617";
    private static final String OTHER_TOKEN = "a0b1c2d3e4f5061728394a5b6c7d8e9fa0b1c2d3e4f5061728394a5b6c7d8e9f";
    private static final String CLIENT_NONCE = "00112233445566778899aabbccddeeff";

    private ServerSocket mServerSocket;
    private ExecutorService mExecutor;

    @Before
    public void setUp() throws IOException {
        mServerSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws IOException {
        mExecutor.shutdownNow();
        mServerSocket.close();
    }

    @Test
    public void bothSidesHoldingTheTokenConnect() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client);
        }
        server.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void theTokenNeverReachesWhateverListensOnThePort() throws Exception {
        // A squatter that got to the port first records everything and answers as best it can
        List<String> received = new CopyOnWriteArrayList<>();
        serve(peer -> {
            String hello = readFrame(peer);
            received.add(hello);
            String nonce = hello.split(",")[1];
            writeFrame(peer, "challenge," + CLIENT_NONCE + "," + DataTransmission.proof(OTHER_TOKEN,
                    "amng-server-proof", nonce, CLIENT_NONCE));
            peer.setSoTimeout(2_000);
            try {
                received.add(readFrame(peer));
            } catch (IOException ignored) {
                // The client hung up without a word, as it should
            }
        });

        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
            assertTrue(e.getMessage(), e.getMessage().contains("didn't acknowledge"));
        }
        mExecutor.shutdown();
        assertTrue(mExecutor.awaitTermination(5, TimeUnit.SECONDS));
        // Up to 1.3.0 the first frame carried the token itself
        assertEquals(1, received.size());
        assertFalse(received.get(0), received.get(0).contains(TOKEN));
    }

    @Test
    public void serverWithAnotherTokenIsRejected() throws Exception {
        serve(peer -> transmission(peer).shakeHands(OTHER_TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
            assertTrue(e.getMessage(), e.getMessage().contains("didn't acknowledge"));
        }
    }

    @Test
    public void listenerThatNeverAnswersTimesOut() throws Exception {
        serve(peer -> {
            readFrame(peer);
            Thread.sleep(30_000);
        });

        long start = System.nanoTime();
        try (Socket client = connect(500)) {
            // Told apart from a rejection: the app reports it as a server that isn't answering
            assertThrows(SocketTimeoutException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
        }
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);
    }

    @Test
    public void listenerThatHangsUpIsRejected() throws Exception {
        serve(DataTransmissionHandshakeTest::readFrame);

        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
            assertTrue(e.getMessage(), e.getMessage().contains("without acknowledging"));
        }
    }

    @Test
    public void aProofForAnotherClientNonceIsRejected() throws Exception {
        // A challenge recorded from an earlier session is no good for this one
        serve(peer -> {
            readFrame(peer);
            String serverNonce = "ffeeddccbbaa99887766554433221100";
            writeFrame(peer, "challenge," + serverNonce + ","
                    + DataTransmission.proof(TOKEN, "amng-server-proof", CLIENT_NONCE, serverNonce));
            Thread.sleep(5_000);
        });

        try (Socket client = connect(5_000)) {
            assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
        }
    }

    @Test
    public void serverRefusesAClientWithoutTheToken() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            writeFrame(client, DataTransmission.PROTOCOL_VERSION + "," + CLIENT_NONCE);
            String[] challenge = readFrame(client).split(",");
            writeFrame(client, "auth," + DataTransmission.proof(OTHER_TOKEN, "amng-client-proof",
                    challenge[1], CLIENT_NONCE));
            assertUnauthorized(server);
        }
    }

    @Test
    public void theServersOwnProofDoesNotPassAsAClientProof() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            writeFrame(client, DataTransmission.PROTOCOL_VERSION + "," + CLIENT_NONCE);
            String[] challenge = readFrame(client).split(",");
            // Reflecting the server's proof back must not work
            writeFrame(client, "auth," + challenge[2]);
            assertUnauthorized(server);
        }
    }

    @Test
    public void serverRefusesMalformedAndOldHandshakes() throws Exception {
        for (String hello : new String[]{
                DataTransmission.PROTOCOL_VERSION,
                DataTransmission.PROTOCOL_VERSION + ",",
                DataTransmission.PROTOCOL_VERSION + ",not-hex",
                // 1.3.0 clients sent the token itself
                "1.3.0," + TOKEN + "," + CLIENT_NONCE}) {
            Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));
            try (Socket client = connect(5_000)) {
                writeFrame(client, hello);
                Exception e = assertThrows(hello, Exception.class, () -> server.get(5, TimeUnit.SECONDS));
                assertEquals(hello, "Malformed handshake", e.getCause().getMessage());
            }
        }
    }

    @Test
    public void serverRefusesAnotherProtocolVersion() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            writeFrame(client, "9.9.9," + CLIENT_NONCE);
            Exception e = assertThrows(Exception.class, () -> server.get(5, TimeUnit.SECONDS));
            assertTrue(String.valueOf(e.getCause()),
                    e.getCause() instanceof DataTransmission.ProtocolVersionException);
        }
    }

    @Test
    public void aProofDependsOnTokenLabelAndBothNonces() throws IOException {
        String proof = DataTransmission.proof(TOKEN, "amng-server-proof", "n1", "n2");

        assertTrue(proof, proof.matches("[0-9a-f]{64}"));
        assertEquals(proof, DataTransmission.proof(TOKEN, "amng-server-proof", "n1", "n2"));
        assertNotEquals(proof, DataTransmission.proof(OTHER_TOKEN, "amng-server-proof", "n1", "n2"));
        assertNotEquals(proof, DataTransmission.proof(TOKEN, "amng-client-proof", "n1", "n2"));
        assertNotEquals(proof, DataTransmission.proof(TOKEN, "amng-server-proof", "n2", "n1"));
    }

    @Test
    public void onlyShortAsciiHexIsANonce() {
        assertTrue(DataTransmission.isNonce(CLIENT_NONCE));
        assertTrue(DataTransmission.isNonce("ABCdef0123"));
        assertFalse(DataTransmission.isNonce(""));
        assertFalse(DataTransmission.isNonce(null));
        assertFalse(DataTransmission.isNonce("12,34"));
        // Arabic-Indic digits, which Character.digit() accepts
        assertFalse(DataTransmission.isNonce("٣٤"));
        assertFalse(DataTransmission.isNonce(new String(new char[65]).replace('\0', 'a')));
    }

    @Test
    public void anEmptyTokenNeverAuthenticates() throws Exception {
        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(IOException.class,
                    () -> transmission(client).shakeHands("", DataTransmission.Role.Client));
            assertEquals("Empty token", e.getMessage());
        }
    }

    private static void assertUnauthorized(Future<Void> server) {
        Exception e = assertThrows(Exception.class, () -> server.get(5, TimeUnit.SECONDS));
        assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof IOException);
        assertEquals("Unauthorized client", e.getCause().getMessage());
    }

    private interface Peer {
        void run(Socket peer) throws Exception;
    }

    private Future<Void> serve(Peer peer) {
        return mExecutor.submit(() -> {
            try (Socket socket = mServerSocket.accept()) {
                peer.run(socket);
            }
            return null;
        });
    }

    private Socket connect(int timeoutMs) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), mServerSocket.getLocalPort());
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    private static DataTransmission transmission(Socket socket) throws IOException {
        return new DataTransmission(socket.getOutputStream(), socket.getInputStream(), false);
    }

    private static String readFrame(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeFrame(Socket socket, String text) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }
}
