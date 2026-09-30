// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdbSyncTest {
    private static final String PATH = "/data/local/tmp/app.id/am.jar";

    @Test
    public void pushSplitsDataIntoChunksAdbdAccepts() throws IOException {
        byte[] data = bytes(AdbSync.MAX_DATA + 5);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AdbSync sync = new AdbSync(replies(frame("OKAY", 0)), sent, () -> {
        });

        sync.push(data, PATH, 0644, 1234);

        ByteBuffer b = wrap(sent);
        assertEquals("SEND", id(b));
        // 33188 is S_IFREG | 0644
        assertEquals(PATH + ",33188", string(b, b.getInt()));
        assertEquals("DATA", id(b));
        assertEquals(AdbSync.MAX_DATA, b.getInt());
        assertArrayEquals(Arrays.copyOfRange(data, 0, AdbSync.MAX_DATA), take(b, AdbSync.MAX_DATA));
        assertEquals("DATA", id(b));
        assertEquals(5, b.getInt());
        assertArrayEquals(Arrays.copyOfRange(data, AdbSync.MAX_DATA, data.length), take(b, 5));
        assertEquals("DONE", id(b));
        assertEquals(1234, b.getInt());
        assertFalse(b.hasRemaining());
    }

    @Test
    public void pushOfEmptyFileSendsNoData() throws IOException {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AdbSync sync = new AdbSync(replies(frame("OKAY", 0)), sent, () -> {
        });

        sync.push(new byte[0], PATH, 0755, 7);

        ByteBuffer b = wrap(sent);
        assertEquals("SEND", id(b));
        assertEquals(PATH + ",33261", string(b, b.getInt()));
        assertEquals("DONE", id(b));
        assertEquals(7, b.getInt());
        assertFalse(b.hasRemaining());
    }

    @Test
    public void pushReportsAdbdFailureAndEndsTheSession() throws IOException {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AtomicBoolean closed = new AtomicBoolean();
        AdbSync sync = new AdbSync(replies(fail("Permission denied")), sent, () -> closed.set(true));

        IOException e = assertThrows(IOException.class, () -> sync.push(bytes(3), PATH, 0644, 0));
        assertTrue(e.getMessage().contains("Permission denied"));
        assertThrows(IOException.class, () -> sync.pull(PATH, 3));

        int sentBeforeClose = sent.size();
        sync.close();
        // adbd has already hung up, so no QUIT
        assertEquals(sentBeforeClose, sent.size());
        assertTrue(closed.get());
    }

    @Test
    public void pullJoinsChunksUntilDone() throws IOException {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AdbSync sync = new AdbSync(replies(data(new byte[]{1, 2}), data(new byte[]{3}), frame("DONE", 0)), sent, () -> {
        });

        assertArrayEquals(new byte[]{1, 2, 3}, sync.pull(PATH, 3));

        ByteBuffer b = wrap(sent);
        assertEquals("RECV", id(b));
        assertEquals(PATH, string(b, b.getInt()));
        assertFalse(b.hasRemaining());
    }

    @Test
    public void pullRefusesMoreThanExpected() {
        AdbSync sync = new AdbSync(replies(data(bytes(4)), frame("DONE", 0)), new ByteArrayOutputStream(), () -> {
        });

        assertThrows(IOException.class, () -> sync.pull(PATH, 3));
    }

    @Test
    public void pullReportsMissingFile() {
        AdbSync sync = new AdbSync(replies(fail("No such file or directory")), new ByteArrayOutputStream(), () -> {
        });

        IOException e = assertThrows(IOException.class, () -> sync.pull(PATH, 3));
        assertTrue(e.getMessage().contains("No such file or directory"));
    }

    @Test
    public void pullReportsTruncatedStream() {
        AdbSync sync = new AdbSync(new ByteArrayInputStream(new byte[]{'D', 'A'}), new ByteArrayOutputStream(), () -> {
        });

        assertThrows(IOException.class, () -> sync.pull(PATH, 3));
    }

    @Test
    public void pushVerifiedAcceptsIdenticalReadBack() throws IOException {
        byte[] data = bytes(10);
        AdbSync sync = new AdbSync(replies(frame("OKAY", 0), data(data), frame("DONE", 0)),
                new ByteArrayOutputStream(), () -> {
        });

        sync.pushVerified(data, PATH, 0644, 0);
    }

    @Test
    public void pushVerifiedRejectsChangedReadBack() {
        byte[] data = bytes(10);
        byte[] changed = data.clone();
        changed[9] ^= 1;
        AdbSync sync = new AdbSync(replies(frame("OKAY", 0), data(changed), frame("DONE", 0)),
                new ByteArrayOutputStream(), () -> {
        });

        IOException e = assertThrows(IOException.class, () -> sync.pushVerified(data, PATH, 0644, 0));
        assertTrue(e.getMessage().contains("doesn't match"));
    }

    @Test
    public void pushVerifiedRejectsShortReadBack() {
        byte[] data = bytes(10);
        AdbSync sync = new AdbSync(replies(frame("OKAY", 0), data(Arrays.copyOf(data, 9)), frame("DONE", 0)),
                new ByteArrayOutputStream(), () -> {
        });

        assertThrows(IOException.class, () -> sync.pushVerified(data, PATH, 0644, 0));
    }

    @Test
    public void closeSendsQuitAndClosesTheStream() throws IOException {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AtomicBoolean closed = new AtomicBoolean();
        AdbSync sync = new AdbSync(replies(), sent, () -> closed.set(true));

        sync.close();

        ByteBuffer b = wrap(sent);
        assertEquals("QUIT", id(b));
        assertEquals(0, b.getInt());
        assertFalse(b.hasRemaining());
        assertTrue(closed.get());
    }

    @Test
    public void relativeAndOverlongPathsAreRefused() {
        AdbSync sync = new AdbSync(replies(), new ByteArrayOutputStream(), () -> {
        });
        char[] longName = new char[1024];
        Arrays.fill(longName, 'a');

        assertThrows(IllegalArgumentException.class, () -> sync.push(bytes(1), "am.jar", 0644, 0));
        assertThrows(IllegalArgumentException.class, () -> sync.pull("/" + new String(longName), 1));
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; ++i) {
            data[i] = (byte) (i * 31 + 7);
        }
        return data;
    }

    private static byte[] frame(String id, int value, byte... payload) {
        ByteBuffer b = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(value).put(payload);
        return b.array();
    }

    private static byte[] data(byte[] payload) {
        return frame("DATA", payload.length, payload);
    }

    private static byte[] fail(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        return frame("FAIL", payload.length, payload);
    }

    private static ByteArrayInputStream replies(byte[]... frames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] f : frames) {
            out.write(f, 0, f.length);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static ByteBuffer wrap(ByteArrayOutputStream sent) {
        return ByteBuffer.wrap(sent.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String id(ByteBuffer b) {
        return new String(take(b, 4), StandardCharsets.US_ASCII);
    }

    private static String string(ByteBuffer b, int length) {
        return new String(take(b, length), StandardCharsets.UTF_8);
    }

    private static byte[] take(ByteBuffer b, int length) {
        byte[] out = new byte[length];
        b.get(out);
        return out;
    }
}
