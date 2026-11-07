// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SecureChannelTest {
    private static final byte[] CLIENT_TO_HOST = key(1);
    private static final byte[] HOST_TO_CLIENT = key(2);

    @Test
    public void framesRoundTripInOrder() throws IOException {
        List<byte[]> frames = seal(utf8("LIST"), new byte[0], utf8("GET base.apk"));
        SecureChannel receiver = receiver(new ClosingInput(concat(frames)));

        assertArrayEquals(utf8("LIST"), receiver.receive(1024));
        assertArrayEquals(new byte[0], receiver.receive(1024));
        assertArrayEquals(utf8("GET base.apk"), receiver.receive(1024));
        assertFalse(receiver.isDead());
    }

    @Test
    public void flippedCiphertextByteKillsTheSession() throws IOException {
        List<byte[]> frames = seal(utf8("first message"), utf8("second message"));
        frames.get(0)[6] ^= 0x01; // inside the ciphertext, past the 4 byte length
        ClosingInput wire = new ClosingInput(concat(frames));
        SecureChannel receiver = receiver(wire);

        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
        assertTrue(receiver.isDead());
        assertTrue("streams under a failed channel are closed", wire.closed);
        // The second frame is intact, but a dead session never reads again
        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
    }

    @Test
    public void flippedTagByteKillsTheSession() throws IOException {
        List<byte[]> frames = seal(utf8("payload"));
        byte[] frame = frames.get(0);
        frame[frame.length - 1] ^= (byte) 0x80;
        SecureChannel receiver = receiver(new ClosingInput(concat(frames)));

        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
        assertTrue(receiver.isDead());
    }

    @Test
    public void replayedFrameIsRejected() throws IOException {
        List<byte[]> frames = seal(utf8("ACCEPT"));
        SecureChannel receiver = receiver(new ClosingInput(concat(Arrays.asList(frames.get(0), frames.get(0)))));

        assertArrayEquals(utf8("ACCEPT"), receiver.receive(1024));
        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
        assertTrue(receiver.isDead());
    }

    @Test
    public void reorderedFramesAreRejected() throws IOException {
        List<byte[]> frames = seal(utf8("one"), utf8("two"));
        SecureChannel receiver = receiver(new ClosingInput(concat(Arrays.asList(frames.get(1), frames.get(0)))));

        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
        assertTrue(receiver.isDead());
    }

    @Test
    public void droppedFrameIsDetected() throws IOException {
        List<byte[]> frames = seal(utf8("one"), utf8("two"));
        SecureChannel receiver = receiver(new ClosingInput(frames.get(1)));

        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
    }

    @Test
    public void ownFramesReflectedBackAreRejected() throws IOException {
        // The receiver's own outgoing frames use the other direction's key
        ByteArrayOutputStream ownWire = new ByteArrayOutputStream();
        SecureChannel self = new SecureChannel(new FrameIo(new ByteArrayInputStream(new byte[0]), ownWire),
                HOST_TO_CLIENT, CLIENT_TO_HOST);
        self.send(utf8("reflected"));
        SecureChannel receiver = receiver(new ClosingInput(ownWire.toByteArray()));

        assertThrows(SyncProtocolException.class, () -> receiver.receive(1024));
    }

    @Test
    public void frameOverTheCapIsRefusedBeforeItIsRead() throws IOException {
        byte[] big = new byte[2048];
        List<byte[]> frames = seal(big);
        SecureChannel receiver = receiver(new ClosingInput(concat(frames)));

        SyncProtocolException e = assertThrows(SyncProtocolException.class, () -> receiver.receive(100));
        assertTrue(e.getMessage(), e.getMessage().contains("limit"));
        assertTrue(receiver.isDead());
    }

    @Test
    public void sendAfterCloseFails() {
        SecureChannel channel = new SecureChannel(new FrameIo(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream()), CLIENT_TO_HOST, HOST_TO_CLIENT);
        channel.close();
        assertThrows(SyncProtocolException.class, () -> channel.send(utf8("late")));
    }

    @Test
    public void ciphertextDoesNotCarryThePlaintext() throws IOException {
        byte[] plain = utf8("com.example.secret.package");
        byte[] wire = seal(plain).get(0);
        assertEquals(4 + plain.length + SecureChannel.TAG_BYTES, wire.length);
        assertFalse(new String(wire, StandardCharsets.ISO_8859_1).contains("com.example"));
    }

    @NonNull
    private static List<byte[]> seal(@NonNull byte[]... messages) throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        SecureChannel sender = new SecureChannel(new FrameIo(new ByteArrayInputStream(new byte[0]), wire),
                CLIENT_TO_HOST, HOST_TO_CLIENT);
        List<byte[]> frames = new ArrayList<>();
        for (byte[] message : messages) {
            int before = wire.size();
            sender.send(message);
            byte[] all = wire.toByteArray();
            frames.add(Arrays.copyOfRange(all, before, all.length));
        }
        return frames;
    }

    @NonNull
    private static SecureChannel receiver(@NonNull ClosingInput wire) {
        return new SecureChannel(new FrameIo(wire, new ByteArrayOutputStream()), HOST_TO_CLIENT, CLIENT_TO_HOST);
    }

    @NonNull
    private static byte[] concat(@NonNull List<byte[]> frames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            out.write(frame, 0, frame.length);
        }
        return out.toByteArray();
    }

    @NonNull
    private static byte[] key(int seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; ++i) {
            key[i] = (byte) (seed * 31 + i);
        }
        return key;
    }

    @NonNull
    private static byte[] utf8(@NonNull String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ClosingInput extends ByteArrayInputStream {
        boolean closed;

        ClosingInput(@NonNull byte[] data) {
            super(data);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
