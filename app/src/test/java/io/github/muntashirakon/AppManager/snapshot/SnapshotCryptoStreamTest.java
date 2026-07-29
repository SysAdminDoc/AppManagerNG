// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Random;

/**
 * The streaming envelope must round-trip exactly like the in-memory one, stay compatible with it
 * in both directions, and never surface plaintext for a bundle that fails authentication.
 */
public class SnapshotCryptoStreamTest {
    private static char[] passphrase() {
        return "correct horse battery staple".toCharArray();
    }

    @Test
    public void streamingRoundTripsThroughItself() throws Exception {
        byte[] plaintext = payload(300_000);
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(plaintext), envelope, passphrase());
        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        SnapshotCrypto.decryptTo(new ByteArrayInputStream(envelope.toByteArray()), decrypted, passphrase());
        assertArrayEquals(plaintext, decrypted.toByteArray());
    }

    @Test
    public void aStreamedEnvelopeIsReadableByTheInMemoryDecrypt() throws Exception {
        byte[] plaintext = payload(4096);
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(plaintext), envelope, passphrase());
        assertTrue(SnapshotCrypto.looksEncrypted(envelope.toByteArray()));
        assertArrayEquals(plaintext, SnapshotCrypto.decrypt(envelope.toByteArray(), passphrase()));
    }

    @Test
    public void anInMemoryEnvelopeIsReadableByTheStreamingDecrypt() throws Exception {
        byte[] plaintext = payload(4096);
        byte[] envelope = SnapshotCrypto.encrypt(plaintext, passphrase());
        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        SnapshotCrypto.decryptTo(new ByteArrayInputStream(envelope), decrypted, passphrase());
        assertArrayEquals(plaintext, decrypted.toByteArray());
    }

    @Test
    public void anEmptyBundleRoundTrips() throws Exception {
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(new byte[0]), envelope, passphrase());
        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        SnapshotCrypto.decryptTo(new ByteArrayInputStream(envelope.toByteArray()), decrypted, passphrase());
        assertEquals(0, decrypted.size());
    }

    @Test
    public void aWrongPassphraseFails() throws Exception {
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(payload(2048)), envelope, passphrase());
        assertThrows(SnapshotImportException.class, () -> SnapshotCrypto.decryptTo(
                new ByteArrayInputStream(envelope.toByteArray()), new ByteArrayOutputStream(),
                "wrong".toCharArray()));
    }

    @Test
    public void aTamperedPayloadFails() throws Exception {
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(payload(2048)), envelope, passphrase());
        byte[] bytes = envelope.toByteArray();
        bytes[bytes.length - 1] ^= 0x40;
        assertThrows(SnapshotImportException.class, () -> SnapshotCrypto.decryptTo(
                new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), passphrase()));
    }

    @Test
    public void aTamperedHeaderFails() throws Exception {
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        SnapshotCrypto.encryptTo(new ByteArrayInputStream(payload(2048)), envelope, passphrase());
        byte[] bytes = envelope.toByteArray();
        // Flip a salt byte: the header is bound in as AAD, so this must not authenticate.
        bytes[SnapshotCrypto.MAGIC.length + 1] ^= 0x01;
        assertThrows(SnapshotImportException.class, () -> SnapshotCrypto.decryptTo(
                new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), passphrase()));
    }

    @Test
    public void aTruncatedHeaderFails() {
        assertThrows(SnapshotImportException.class, () -> SnapshotCrypto.decryptTo(
                new ByteArrayInputStream(SnapshotCrypto.MAGIC), new ByteArrayOutputStream(),
                passphrase()));
    }

    @Test
    public void aPlaintextInputIsRejectedRatherThanTreatedAsAnEnvelope() {
        assertThrows(SnapshotImportException.class, () -> SnapshotCrypto.decryptTo(
                new ByteArrayInputStream(payload(64)), new ByteArrayOutputStream(), passphrase()));
    }

    @Test
    public void aLargeBundleIsProcessedWithoutBufferingItWhole() throws Exception {
        // 32 MiB through source/sink streams that hold nothing. The bundle is never materialised
        // by this code: it is consumed in fixed-size reads regardless of how large it is.
        long size = 32L * 1024 * 1024;
        ZeroInputStream source = new ZeroInputStream(size);
        CountingOutputStream envelopeSink = new CountingOutputStream();
        SnapshotCrypto.encryptTo(source, envelopeSink, passphrase());
        assertTrue(envelopeSink.count >= size);
        assertEquals(SnapshotCrypto.STREAM_CHUNK, source.maxSingleRead);
    }

    private static byte[] payload(int size) {
        byte[] out = new byte[size];
        new Random(size).nextBytes(out);
        return out;
    }

    /** Produces {@code size} bytes without allocating them. */
    private static final class ZeroInputStream extends InputStream {
        private long remaining;
        int maxSingleRead;

        ZeroInputStream(long size) {
            remaining = size;
        }

        @Override
        public int read() {
            if (remaining <= 0) return -1;
            --remaining;
            maxSingleRead = Math.max(maxSingleRead, 1);
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            maxSingleRead = Math.max(maxSingleRead, len);
            if (remaining <= 0) return -1;
            int n = (int) Math.min(len, remaining);
            remaining -= n;
            return n;
        }
    }

    /** Discards everything but remembers how much arrived and in what size chunks. */
    private static final class CountingOutputStream extends OutputStream {
        long count;
        int maxSingleWrite;

        @Override
        public void write(int b) {
            ++count;
            maxSingleWrite = Math.max(maxSingleWrite, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            count += len;
            maxSingleWrite = Math.max(maxSingleWrite, len);
        }
    }
}
