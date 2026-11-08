// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM frames after pairing. Each direction has its own key and its own counter, and the
 * counter is the nonce, so it never travels on the wire: a replayed, dropped or reordered frame
 * is opened with the wrong nonce and fails its tag. The length prefix is authenticated as well.
 * Any failure kills the channel for good and closes the streams under it.
 */
public final class SecureChannel implements Closeable {
    public static final int TAG_BYTES = 16;
    private static final int NONCE_BYTES = 12;

    @NonNull
    private final FrameIo mIo;
    @NonNull
    private final SecretKeySpec mSendKey;
    @NonNull
    private final SecretKeySpec mReceiveKey;
    private final Object mSendLock = new Object();
    private final Object mReceiveLock = new Object();
    private long mSendCounter;
    private long mReceiveCounter;
    private volatile boolean mDead;

    SecureChannel(@NonNull FrameIo io, @NonNull byte[] sendKey, @NonNull byte[] receiveKey) {
        if (sendKey.length != 32 || receiveKey.length != 32) {
            throw new IllegalArgumentException("AES-256 keys are 32 bytes.");
        }
        mIo = io;
        mSendKey = new SecretKeySpec(sendKey, "AES");
        mReceiveKey = new SecretKeySpec(receiveKey, "AES");
    }

    public void send(@NonNull byte[] plaintext) throws IOException {
        synchronized (mSendLock) {
            ensureAlive();
            try {
                if (mSendCounter == Long.MAX_VALUE) {
                    throw new SyncProtocolException("Too many messages in one session.");
                }
                byte[] header = FrameIo.lengthHeader(plaintext.length + TAG_BYTES);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, mSendKey, new GCMParameterSpec(TAG_BYTES * 8, nonce(mSendCounter)));
                cipher.updateAAD(header);
                byte[] ciphertext = cipher.doFinal(plaintext);
                mIo.writeFrame(header, ciphertext);
                ++mSendCounter;
            } catch (GeneralSecurityException e) {
                kill();
                throw new SyncProtocolException("Could not encrypt a message.", e);
            } catch (IOException e) {
                kill();
                throw e;
            }
        }
    }

    /**
     * Reads and opens the next frame. A frame whose plaintext would be larger than
     * {@code maxPlaintext} is refused before it is read.
     */
    @NonNull
    public byte[] receive(int maxPlaintext) throws IOException {
        synchronized (mReceiveLock) {
            ensureAlive();
            try {
                if (mReceiveCounter == Long.MAX_VALUE) {
                    throw new SyncProtocolException("Too many messages in one session.");
                }
                int length = mIo.readLength(maxPlaintext + TAG_BYTES);
                if (length < TAG_BYTES) {
                    throw new SyncProtocolException("Frame is shorter than its tag.");
                }
                byte[] ciphertext = mIo.readBody(length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, mReceiveKey, new GCMParameterSpec(TAG_BYTES * 8, nonce(mReceiveCounter)));
                cipher.updateAAD(FrameIo.lengthHeader(length));
                byte[] plaintext = cipher.doFinal(ciphertext);
                ++mReceiveCounter;
                return plaintext;
            } catch (AEADBadTagException e) {
                kill();
                throw new SyncProtocolException("A message failed authentication, so the session was closed.", e);
            } catch (GeneralSecurityException e) {
                kill();
                throw new SyncProtocolException("Could not decrypt a message.", e);
            } catch (IOException e) {
                kill();
                throw e;
            }
        }
    }

    public boolean isDead() {
        return mDead;
    }

    @Override
    public void close() {
        kill();
    }

    private void kill() {
        mDead = true;
        mIo.close();
    }

    private void ensureAlive() throws SyncProtocolException {
        if (mDead) {
            throw new SyncProtocolException("The session is closed.");
        }
    }

    @NonNull
    private static byte[] nonce(long counter) {
        byte[] nonce = new byte[NONCE_BYTES];
        for (int i = 0; i < 8; ++i) {
            nonce[NONCE_BYTES - 1 - i] = (byte) (counter >>> (8 * i));
        }
        return nonce;
    }
}
