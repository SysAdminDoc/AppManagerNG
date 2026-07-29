// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated, passphrase-based encryption for snapshot bundles.
 *
 * <p>Envelope layout (all lengths fixed):
 * <pre>
 *   MAGIC(8) | VERSION(1) | SALT(16) | NONCE(12) | AES-256-GCM ciphertext + 128-bit tag
 *   \___________________ header, authenticated as AAD ______________/
 * </pre>
 *
 * <p>The key is derived from the passphrase with Argon2id (OWASP minimums: m=19456 KiB,
 * t=2, p=1) and a random 16-byte salt; the payload is AES-256-GCM with a random 12-byte
 * nonce and the header bound in as additional authenticated data. A wrong passphrase or any
 * tampering (including header edits) fails authentication in {@link #decrypt} <em>before</em>
 * any plaintext is returned, so a bad import never writes partial state. Passphrase and
 * derived-key material are zeroed after use.
 */
public final class SnapshotCrypto {
    @VisibleForTesting
    static final byte[] MAGIC = {'A', 'M', 'N', 'G', 'S', 'N', 'P', '1'};
    @VisibleForTesting
    static final int VERSION = 1;

    private static final int SALT_LEN = 16;
    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LEN = 32; // AES-256
    private static final int HEADER_LEN = MAGIC.length + 1 + SALT_LEN + NONCE_LEN;
    private static final String AES_GCM = "AES/GCM/NoPadding";
    /** Bounds the transient heap a streaming encrypt/decrypt needs, independent of bundle size. */
    @VisibleForTesting
    static final int STREAM_CHUNK = 64 * 1024;

    // Argon2id parameters (OWASP Password Storage Cheat Sheet minimums).
    private static final int ARGON2_MEMORY_KB = 19456;
    private static final int ARGON2_ITERATIONS = 2;
    private static final int ARGON2_PARALLELISM = 1;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SnapshotCrypto() {
    }

    /** True if {@code data} begins with the encrypted-snapshot magic. */
    public static boolean looksEncrypted(@Nullable byte[] data) {
        if (data == null || data.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; ++i) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static byte[] encrypt(@NonNull byte[] plaintext, @NonNull char[] passphrase)
            throws GeneralSecurityException {
        byte[] salt = randomBytes(SALT_LEN);
        byte[] nonce = randomBytes(NONCE_LEN);
        byte[] header = buildHeader(salt, nonce);
        byte[] key = deriveKey(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] out = new byte[header.length + ciphertext.length];
            System.arraycopy(header, 0, out, 0, header.length);
            System.arraycopy(ciphertext, 0, out, header.length, ciphertext.length);
            return out;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Streaming counterpart of {@link #encrypt(byte[], char[])}: reads the plaintext bundle and
     * writes the envelope without ever holding either in full.
     */
    public static void encryptTo(@NonNull InputStream plaintextIn, @NonNull OutputStream out,
                                 @NonNull char[] passphrase)
            throws GeneralSecurityException, IOException {
        byte[] salt = randomBytes(SALT_LEN);
        byte[] nonce = randomBytes(NONCE_LEN);
        byte[] header = buildHeader(salt, nonce);
        byte[] key = deriveKey(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            out.write(header);
            pump(cipher, plaintextIn, out);
            out.flush();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Streaming counterpart of {@link #decrypt(byte[], char[])}.
     *
     * <p>GCM only authenticates at the end, so the plaintext this writes must be treated as
     * unverified until the call returns normally. Callers write it to private staging and only
     * consume it after a clean return — a {@link SnapshotImportException} means the staged bytes
     * must be discarded.
     */
    public static void decryptTo(@NonNull InputStream envelopeIn, @NonNull OutputStream out,
                                 @NonNull char[] passphrase)
            throws GeneralSecurityException, SnapshotImportException, IOException {
        byte[] header = new byte[HEADER_LEN];
        readFully(envelopeIn, header);
        if (!looksEncrypted(header)) {
            throw new SnapshotImportException("Not an encrypted AppManagerNG snapshot.");
        }
        int version = header[MAGIC.length] & 0xff;
        if (version != VERSION) {
            throw new SnapshotImportException("Unsupported encrypted-snapshot version: " + version);
        }
        byte[] salt = Arrays.copyOfRange(header, MAGIC.length + 1, MAGIC.length + 1 + SALT_LEN);
        byte[] nonce = Arrays.copyOfRange(header, MAGIC.length + 1 + SALT_LEN, HEADER_LEN);
        byte[] key = deriveKey(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            pump(cipher, envelopeIn, out);
            out.flush();
        } catch (AEADBadTagException e) {
            throw new SnapshotImportException(
                    "Wrong passphrase, or the encrypted snapshot has been altered.");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void pump(@NonNull Cipher cipher, @NonNull InputStream in,
                             @NonNull OutputStream out)
            throws GeneralSecurityException, IOException {
        byte[] buffer = new byte[STREAM_CHUNK];
        int read;
        while ((read = in.read(buffer)) != -1) {
            byte[] chunk = cipher.update(buffer, 0, read);
            if (chunk != null && chunk.length > 0) {
                out.write(chunk);
            }
        }
        byte[] last = cipher.doFinal();
        if (last != null && last.length > 0) {
            out.write(last);
        }
    }

    private static void readFully(@NonNull InputStream in, @NonNull byte[] buffer)
            throws IOException, SnapshotImportException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n < 0) {
                throw new SnapshotImportException("Encrypted snapshot is truncated.");
            }
            read += n;
        }
    }

    @NonNull
    public static byte[] decrypt(@NonNull byte[] envelope, @NonNull char[] passphrase)
            throws GeneralSecurityException, SnapshotImportException {
        if (!looksEncrypted(envelope)) {
            throw new SnapshotImportException("Not an encrypted AppManagerNG snapshot.");
        }
        if (envelope.length < HEADER_LEN + (TAG_BITS / 8)) {
            throw new SnapshotImportException("Encrypted snapshot is truncated.");
        }
        int version = envelope[MAGIC.length] & 0xff;
        if (version != VERSION) {
            throw new SnapshotImportException("Unsupported encrypted-snapshot version: " + version);
        }
        byte[] header = Arrays.copyOfRange(envelope, 0, HEADER_LEN);
        byte[] salt = Arrays.copyOfRange(envelope, MAGIC.length + 1, MAGIC.length + 1 + SALT_LEN);
        byte[] nonce = Arrays.copyOfRange(envelope, MAGIC.length + 1 + SALT_LEN, HEADER_LEN);
        byte[] ciphertext = Arrays.copyOfRange(envelope, HEADER_LEN, envelope.length);
        byte[] key = deriveKey(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new SnapshotImportException(
                    "Wrong passphrase, or the encrypted snapshot has been altered.");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @NonNull
    private static byte[] buildHeader(@NonNull byte[] salt, @NonNull byte[] nonce) {
        byte[] header = new byte[HEADER_LEN];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[MAGIC.length] = (byte) VERSION;
        System.arraycopy(salt, 0, header, MAGIC.length + 1, SALT_LEN);
        System.arraycopy(nonce, 0, header, MAGIC.length + 1 + SALT_LEN, NONCE_LEN);
        return header;
    }

    @NonNull
    private static byte[] deriveKey(@NonNull char[] passphrase, @NonNull byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withIterations(ARGON2_ITERATIONS)
                .withParallelism(ARGON2_PARALLELISM)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] passwordBytes = toUtf8Bytes(passphrase);
        byte[] key = new byte[KEY_LEN];
        try {
            generator.generateBytes(passwordBytes, key);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
        return key;
    }

    @NonNull
    private static byte[] toUtf8Bytes(@NonNull char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), (byte) 0);
        }
        return bytes;
    }

    @NonNull
    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
