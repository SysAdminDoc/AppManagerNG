// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;

/**
 * Client for adbd's file sync service ({@code sync:}), limited to what {@code adb push} and
 * {@code adb pull} do for a single regular file. Files written this way belong to the shell user,
 * so it is how ADB mode places files the shell user has to run.
 *
 * @see <a href="https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/adb/SYNC.TXT">SYNC.TXT</a>
 */
public final class AdbSync implements Closeable {
    /**
     * Largest DATA chunk adbd accepts (SYNC_DATA_MAX).
     */
    static final int MAX_DATA = 64 * 1024;
    private static final int MAX_PATH = 1024;
    private static final int S_IFREG = 0100000;

    @WorkerThread
    @NonNull
    public static AdbSync open(@NonNull AbsAdbConnectionManager manager) throws IOException {
        AdbStream stream;
        try {
            stream = manager.openStream(LocalServices.SYNC);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while opening the ADB sync service.", e);
        }
        return new AdbSync(stream.openInputStream(), stream.openOutputStream(), stream);
    }

    @NonNull
    private final InputStream mIn;
    @NonNull
    private final OutputStream mOut;
    @NonNull
    private final Closeable mStream;
    // adbd ends the sync session after it reports FAIL, so nothing more may be sent
    private boolean mBroken;

    AdbSync(@NonNull InputStream in, @NonNull OutputStream out, @NonNull Closeable stream) {
        mIn = in;
        mOut = out;
        mStream = stream;
    }

    /**
     * Write {@code data} to {@code remotePath} as a regular file, replacing any file already there.
     * adbd creates missing parent directories.
     */
    @WorkerThread
    public void push(@NonNull byte[] data, @NonNull String remotePath, int permissions, long mtimeSeconds)
            throws IOException {
        byte[] header = (checkPath(remotePath) + "," + (S_IFREG | (permissions & 0777)))
                .getBytes(StandardCharsets.UTF_8);
        ensureUsable();
        writeRequest("SEND", header.length);
        mOut.write(header);
        for (int offset = 0; offset < data.length; offset += MAX_DATA) {
            int len = Math.min(MAX_DATA, data.length - offset);
            writeRequest("DATA", len);
            mOut.write(data, offset, len);
        }
        writeRequest("DONE", (int) mtimeSeconds);
        mOut.flush();
        String id = readId();
        int len = readLength();
        if ("OKAY".equals(id)) {
            return;
        }
        mBroken = true;
        if ("FAIL".equals(id)) {
            throw new IOException("adbd could not write " + remotePath + ": " + readMessage(len));
        }
        throw new IOException("Unexpected ADB sync reply " + id + " while writing " + remotePath);
    }

    /**
     * {@link #push} followed by reading the file back and comparing its SHA-256 with {@code data}.
     */
    @WorkerThread
    public void pushVerified(@NonNull byte[] data, @NonNull String remotePath, int permissions, long mtimeSeconds)
            throws IOException {
        push(data, remotePath, permissions, mtimeSeconds);
        byte[] staged = pull(remotePath, data.length);
        if (!MessageDigest.isEqual(sha256(data), sha256(staged))) {
            throw new IOException(remotePath + " doesn't match what was sent.");
        }
    }

    /**
     * Read {@code remotePath} back, refusing a file larger than {@code maxBytes}.
     */
    @WorkerThread
    @NonNull
    public byte[] pull(@NonNull String remotePath, int maxBytes) throws IOException {
        byte[] path = checkPath(remotePath).getBytes(StandardCharsets.UTF_8);
        ensureUsable();
        writeRequest("RECV", path.length);
        mOut.write(path);
        mOut.flush();
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        while (true) {
            String id = readId();
            int len = readLength();
            switch (id) {
                case "DATA":
                    if (len < 0 || len > MAX_DATA || contents.size() + (long) len > maxBytes) {
                        mBroken = true;
                        throw new IOException(remotePath + " is larger than the expected " + maxBytes + " bytes.");
                    }
                    contents.write(readFully(len));
                    break;
                case "DONE":
                    return contents.toByteArray();
                case "FAIL":
                    mBroken = true;
                    throw new IOException("adbd could not read " + remotePath + ": " + readMessage(len));
                default:
                    mBroken = true;
                    throw new IOException("Unexpected ADB sync reply " + id + " while reading " + remotePath);
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (!mBroken) {
                writeRequest("QUIT", 0);
                mOut.flush();
            }
        } finally {
            mBroken = true;
            mStream.close();
        }
    }

    private void ensureUsable() throws IOException {
        if (mBroken) {
            throw new IOException("The ADB sync session has ended.");
        }
    }

    @NonNull
    private static String checkPath(@NonNull String remotePath) {
        int length = remotePath.getBytes(StandardCharsets.UTF_8).length;
        if (length == 0 || length > MAX_PATH || !remotePath.startsWith("/")) {
            throw new IllegalArgumentException("Invalid remote path " + remotePath);
        }
        return remotePath;
    }

    private void writeRequest(@NonNull String id, int value) throws IOException {
        byte[] frame = new byte[8];
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, frame, 0, 4);
        frame[4] = (byte) value;
        frame[5] = (byte) (value >>> 8);
        frame[6] = (byte) (value >>> 16);
        frame[7] = (byte) (value >>> 24);
        mOut.write(frame);
    }

    @NonNull
    private String readId() throws IOException {
        return new String(readFully(4), StandardCharsets.US_ASCII);
    }

    private int readLength() throws IOException {
        byte[] b = readFully(4);
        return (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
    }

    @NonNull
    private String readMessage(int len) throws IOException {
        if (len < 0 || len > MAX_DATA) {
            return "(no message)";
        }
        return new String(readFully(len), StandardCharsets.UTF_8);
    }

    @NonNull
    private static byte[] sha256(@NonNull byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private byte[] readFully(int len) throws IOException {
        byte[] buffer = new byte[len];
        int offset = 0;
        while (offset < len) {
            int read = mIn.read(buffer, offset, len - offset);
            if (read < 0) {
                mBroken = true;
                throw new EOFException("The ADB sync stream closed early.");
            }
            offset += read;
        }
        return buffer;
    }
}
