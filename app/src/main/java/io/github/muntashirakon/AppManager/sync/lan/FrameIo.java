// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Length-prefixed frames over a byte stream: a four byte big-endian length, then that many bytes.
 * The reader refuses any length above the cap it was given before allocating anything, so a peer
 * cannot make this side reserve memory it did not agree to.
 */
public final class FrameIo implements Closeable {
    private final DataInputStream mIn;
    private final DataOutputStream mOut;
    private final InputStream mRawIn;
    private final OutputStream mRawOut;

    public FrameIo(@NonNull InputStream in, @NonNull OutputStream out) {
        mRawIn = in;
        mRawOut = out;
        mIn = new DataInputStream(new BufferedInputStream(in, 16 * 1024));
        mOut = new DataOutputStream(new BufferedOutputStream(out, 16 * 1024));
    }

    /**
     * Writes one frame. {@code header} is the length prefix the caller already built (so an AEAD
     * layer can authenticate it), and must hold exactly {@code body.length}.
     */
    void writeFrame(@NonNull byte[] header, @NonNull byte[] body) throws IOException {
        mOut.write(header);
        mOut.write(body);
        mOut.flush();
    }

    public void writeFrame(@NonNull byte[] body) throws IOException {
        writeFrame(lengthHeader(body.length), body);
    }

    /**
     * Reads the next frame's length and refuses it when it is negative or above {@code maxLength}.
     */
    int readLength(int maxLength) throws IOException {
        int length;
        try {
            length = mIn.readInt();
        } catch (EOFException e) {
            throw new EOFException("The other device closed the connection.");
        }
        if (length < 0 || length > maxLength) {
            throw new SyncProtocolException("Frame of " + length + " bytes is over the " + maxLength + " byte limit.");
        }
        return length;
    }

    @NonNull
    byte[] readBody(int length) throws IOException {
        byte[] body = new byte[length];
        mIn.readFully(body);
        return body;
    }

    @NonNull
    public byte[] readFrame(int maxLength) throws IOException {
        return readBody(readLength(maxLength));
    }

    @NonNull
    static byte[] lengthHeader(int length) {
        return new byte[]{
                (byte) (length >>> 24),
                (byte) (length >>> 16),
                (byte) (length >>> 8),
                (byte) length
        };
    }

    @Override
    public void close() {
        try {
            mRawIn.close();
        } catch (IOException ignore) {
        }
        try {
            mRawOut.close();
        } catch (IOException ignore) {
        }
    }
}
