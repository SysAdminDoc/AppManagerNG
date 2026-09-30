// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The client only takes a server as its own once the server proves it holds the client's token.
 * Upstream App Manager's server, a stale server with another token, or any other listener on the
 * port can accept the connection, but none of them can answer the nonce.
 */
public class DataTransmissionHandshakeTest {
    private static final String TOKEN = "4f1c9a7e2b6d8035c1e7a9f2d4b6083e5a7c9e1f3b5d7092c4e6a8f0b2d4f617";
    private static final String OTHER_TOKEN = "a0b1c2d3e4f5061728394a5b6c7d8e9fa0b1c2d3e4f5061728394a5b6c7d8e9f";

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
    public void serverHoldingTheTokenIsAccepted() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client);
        }
        server.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void serverWithAnotherTokenIsRejected() throws Exception {
        serve(peer -> transmission(peer).shakeHands(OTHER_TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
            assertTrue(e.getMessage(), e.getMessage().contains("without acknowledging"));
        }
    }

    @Test
    public void listenerThatNeverAcknowledgesIsRejected() throws Exception {
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
    public void acknowledgementForAnotherNonceIsRejected() throws Exception {
        // A reply recorded from an earlier session is no good for this one
        serve(peer -> {
            readFrame(peer);
            writeFrame(peer, "ack," + DataTransmission.acknowledgement(TOKEN, "00112233445566778899aabbccddeeff"));
            Thread.sleep(5_000);
        });

        try (Socket client = connect(5_000)) {
            IOException e = assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
            assertTrue(e.getMessage(), e.getMessage().contains("didn't acknowledge"));
        }
    }

    @Test
    public void acknowledgementWithAnotherTokenIsRejected() throws Exception {
        serve(peer -> {
            String nonce = readFrame(peer).split(",")[2];
            writeFrame(peer, "ack," + DataTransmission.acknowledgement(OTHER_TOKEN, nonce));
            Thread.sleep(5_000);
        });

        try (Socket client = connect(5_000)) {
            assertThrows(DataTransmission.HandshakeRejectedException.class,
                    () -> transmission(client).shakeHands(TOKEN, DataTransmission.Role.Client));
        }
    }

    @Test
    public void serverRefusesAHandshakeWithoutANonce() throws Exception {
        Future<Void> server = serve(peer -> transmission(peer).shakeHands(TOKEN, DataTransmission.Role.Server));

        try (Socket client = connect(5_000)) {
            writeFrame(client, DataTransmission.PROTOCOL_VERSION + "," + TOKEN);
            Exception e = assertThrows(Exception.class, () -> server.get(5, TimeUnit.SECONDS));
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof IOException);
            assertEquals("Malformed handshake", e.getCause().getMessage());
        }
    }

    @Test
    public void acknowledgementDependsOnTokenAndNonce() throws IOException {
        String ack = DataTransmission.acknowledgement(TOKEN, "n1");

        assertTrue(ack, ack.matches("[0-9a-f]{64}"));
        assertEquals(ack, DataTransmission.acknowledgement(TOKEN, "n1"));
        assertNotEquals(ack, DataTransmission.acknowledgement(TOKEN, "n2"));
        assertNotEquals(ack, DataTransmission.acknowledgement(OTHER_TOKEN, "n1"));
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
