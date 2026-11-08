// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.Destroyable;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;

/**
 * The manager is one shared instance. A connection kept from an earlier port answered every
 * later connect with "already connected", so typing a new Wireless debugging port changed nothing.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbConnectionSwitchTest {
    private static final String HOST = "127.0.0.1";

    @Test
    public void connectingToAnotherPortDropsTheOldConnection() throws Exception {
        try (FakeAdbd first = new FakeAdbd(); FakeAdbd second = new FakeAdbd();
             AdbConnectionManager manager = new AdbConnectionManager(KeyStoreUtils.generateRSAKeyPair(
                     "CN=Test", 2048, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)))) {
            manager.setTimeout(10, TimeUnit.SECONDS);

            assertTrue(manager.connect(HOST, first.port()));
            assertTrue(manager.isConnectedTo(HOST, first.port()));
            assertFalse(manager.isConnectedTo(HOST, second.port()));
            assertFalse(manager.isConnectedTo("10.0.2.2", first.port()));

            assertTrue("A new port gets its own connection", manager.connect(HOST, second.port()));
            assertTrue(manager.isConnectedTo(HOST, second.port()));
            assertFalse(manager.isConnectedTo(HOST, first.port()));
            assertTrue("The old connection is closed", first.awaitSessionEnded(5, TimeUnit.SECONDS));
            assertEquals(1, second.sessions());

            assertFalse("The same port keeps the connection it has", manager.connect(HOST, second.port()));
            assertEquals(1, second.sessions());
        }
    }

    @Test
    public void switchingPortsLeavesTheKeyUsable() throws Exception {
        // libadb destroys the key of a connection it closes. The key loaded from the keystore is a
        // Bouncy Castle one that really goes, so Wireless debugging's TLS handshake failed with
        // "key destroyed" right after ADB over TCP's connection was dropped for it.
        KeyPair stored = storedKeyPair();
        assertTrue(stored.getPrivateKey() instanceof Destroyable);
        try (FakeAdbd first = new FakeAdbd(); FakeAdbd second = new FakeAdbd();
             AdbConnectionManager manager = new AdbConnectionManager(stored)) {
            manager.setTimeout(10, TimeUnit.SECONDS);
            assertTrue(manager.connect(HOST, first.port()));

            assertTrue(manager.connect(HOST, second.port()));

            assertTrue(first.awaitSessionEnded(5, TimeUnit.SECONDS));
            assertFalse(stored.getPrivateKey().isDestroyed());
            assertNotNull(manager.getPrivateKey().getEncoded());
        }
        assertFalse("Closing the manager leaves the stored key alone", stored.getPrivateKey().isDestroyed());
    }

    /**
     * A key pair like the one the keystore hands back: its private key is Bouncy Castle's.
     */
    @NonNull
    private static KeyPair storedKeyPair() throws Exception {
        KeyPair generated = KeyStoreUtils.generateRSAKeyPair("CN=Test", 2048,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        PrivateKey bouncyCastleKey = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
                .generatePrivate(new PKCS8EncodedKeySpec(generated.getPrivateKey().getEncoded()));
        return new KeyPair(bouncyCastleKey, generated.getCertificate());
    }

    /**
     * Answers ADB's CNXN like a daemon with authentication off.
     */
    private static final class FakeAdbd implements Closeable {
        private static final int A_CNXN = 0x4e584e43;
        private static final int A_VERSION = 0x01000001;
        private static final int MAX_PAYLOAD = 4096;

        private final ServerSocket mServer = new ServerSocket(0, 8, InetAddress.getByName(HOST));
        private final List<Socket> mSockets = new CopyOnWriteArrayList<>();
        private final AtomicInteger mSessions = new AtomicInteger();
        private final CountDownLatch mSessionEnded = new CountDownLatch(1);

        FakeAdbd() throws IOException {
            Thread thread = new Thread(this::acceptLoop, "FakeAdbd");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return mServer.getLocalPort();
        }

        int sessions() {
            return mSessions.get();
        }

        boolean awaitSessionEnded(long timeout, TimeUnit unit) throws InterruptedException {
            return mSessionEnded.await(timeout, unit);
        }

        private void acceptLoop() {
            while (true) {
                Socket socket;
                try {
                    socket = mServer.accept();
                } catch (IOException e) {
                    return;
                }
                mSockets.add(socket);
                Thread session = new Thread(() -> serve(socket), "FakeAdbd-session");
                session.setDaemon(true);
                session.start();
            }
        }

        private void serve(@NonNull Socket socket) {
            boolean connected = false;
            try (Socket s = socket) {
                DataInputStream in = new DataInputStream(s.getInputStream());
                OutputStream out = s.getOutputStream();
                while (true) {
                    int command = readMessage(in);
                    if (command == A_CNXN && !connected) {
                        connected = true;
                        mSessions.incrementAndGet();
                        out.write(message(A_CNXN, A_VERSION, MAX_PAYLOAD, "device::\0"));
                        out.flush();
                    }
                }
            } catch (IOException e) {
                // The reachability probe opens and closes without a word, which isn't a session
                if (connected) {
                    mSessionEnded.countDown();
                }
            }
        }

        private static int readMessage(@NonNull DataInputStream in) throws IOException {
            byte[] header = new byte[24];
            in.readFully(header);
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int command = buffer.getInt(0);
            int length = buffer.getInt(12);
            if (length < 0 || length > 1024 * 1024) {
                throw new EOFException("Bad length " + length);
            }
            in.readFully(new byte[length]);
            return command;
        }

        @NonNull
        private static byte[] message(int command, int arg0, int arg1, @NonNull String data) {
            byte[] payload = data.getBytes(StandardCharsets.UTF_8);
            int checksum = 0;
            for (byte b : payload) {
                checksum += b & 0xFF;
            }
            ByteBuffer buffer = ByteBuffer.allocate(24 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(command).putInt(arg0).putInt(arg1).putInt(payload.length).putInt(checksum)
                    .putInt(~command).put(payload);
            return buffer.array();
        }

        @Override
        public void close() throws IOException {
            mServer.close();
            for (Socket socket : mSockets) {
                socket.close();
            }
        }
    }
}
