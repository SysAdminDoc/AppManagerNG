// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

/**
 * The pulling side of a Local sync connection. Not thread safe: callers run every call on one
 * worker thread, keepalive pings included.
 */
public final class SyncClientSession implements Closeable {
    /**
     * Longer than the host's approval window, so the host always answers first.
     */
    public static final int APPROVAL_READ_TIMEOUT_MS = (int) HostSession.APPROVAL_TIMEOUT_MS + 15_000;
    public static final long PING_INTERVAL_MS = 30_000;

    public interface PairingListener {
        /**
         * Called once the keys are agreed, before the host decides, so the digits can be shown.
         */
        void onPaired(@NonNull PairedSession session);
    }

    public interface ProgressListener {
        void onProgress(long bytesDone);
    }

    /**
     * The host answered a request with an error. The session stays usable.
     */
    public static final class RemoteError extends IOException {
        public final int code;

        RemoteError(int code, @NonNull String message) {
            super(message);
            this.code = code;
        }
    }

    /**
     * The host user turned the pairing down.
     */
    public static final class RejectedException extends IOException {
        RejectedException() {
            super("The other device did not allow the connection.");
        }
    }

    @NonNull
    private final PairedSession mSession;
    @NonNull
    private final SecureChannel mChannel;
    private final boolean mRemembered;

    private SyncClientSession(@NonNull PairedSession session, boolean remembered) {
        mSession = session;
        mChannel = session.getChannel();
        mRemembered = remembered;
    }

    @WorkerThread
    @NonNull
    public static SyncClientSession connect(@NonNull FrameIo io, @NonNull HostSession.TimeoutSetter timeouts,
                                            @NonNull String clientName, @NonNull LocalIdentity identity,
                                            @NonNull SecureRandom random, @Nullable PairingListener listener)
            throws IOException {
        PairedSession session = null;
        try {
            timeouts.setReadTimeout(HostSession.HANDSHAKE_TIMEOUT_MS);
            PairingHandshake.Agreement agreement = PairingHandshake.client(io, clientName, random);
            session = PairingHandshake.authenticate(agreement, identity);
            if (listener != null) {
                listener.onPaired(session);
            }
            timeouts.setReadTimeout(APPROVAL_READ_TIMEOUT_MS);
            byte[] answer = session.getChannel().receive(SyncMessages.MAX_CONTROL);
            byte type = SyncMessages.typeOf(answer);
            if (type == SyncMessages.REJECT) {
                throw new RejectedException();
            }
            SyncMessages.Reader r = SyncMessages.read(answer, SyncMessages.ACCEPT);
            boolean remembered = r.readBoolean();
            r.expectEnd();
            timeouts.setReadTimeout(HostSession.IDLE_TIMEOUT_MS);
            return new SyncClientSession(session, remembered);
        } catch (IOException e) {
            if (session != null) {
                session.close();
            } else {
                io.close();
            }
            throw e;
        }
    }

    @NonNull
    public PairedSession getPairedSession() {
        return mSession;
    }

    /**
     * Whether the host remembered this device (or already had).
     */
    public boolean isRemembered() {
        return mRemembered;
    }

    public boolean isClosed() {
        return mChannel.isDead();
    }

    @WorkerThread
    @NonNull
    public List<SyncApp> list(boolean includeSystem) throws IOException {
        mChannel.send(SyncMessages.listRequest(includeSystem));
        byte[] reply = mChannel.receive(SyncMessages.MAX_LIST);
        throwIfError(reply);
        return SyncMessages.parseList(reply);
    }

    @WorkerThread
    public void ping() throws IOException {
        mChannel.send(SyncMessages.simple(SyncMessages.PING));
        SyncMessages.read(mChannel.receive(SyncMessages.MAX_CONTROL), SyncMessages.PONG).expectEnd();
    }

    /**
     * Streams one APK file into {@code out}. The size must match what the host listed, every
     * byte is counted against it, and the SHA-256 the host sends at the end must match what
     * arrived. Returns that digest.
     */
    @WorkerThread
    @NonNull
    public byte[] download(@NonNull String packageName, @NonNull String fileName, long expectedSize,
                           @NonNull OutputStream out, @Nullable ProgressListener progress) throws IOException {
        if (!SyncMessages.isSafeFileName(fileName) || expectedSize < 0 || expectedSize > SyncMessages.MAX_FILE_SIZE) {
            throw new SyncProtocolException("Refusing to request " + fileName);
        }
        mChannel.send(SyncMessages.getRequest(packageName, fileName, expectedSize));
        byte[] reply = mChannel.receive(SyncMessages.MAX_CONTROL);
        throwIfError(reply);
        SyncMessages.Reader header = SyncMessages.read(reply, SyncMessages.FILE_HEADER);
        long size = header.readLong();
        header.expectEnd();
        if (size != expectedSize) {
            mChannel.close();
            throw new SyncProtocolException("The other device announced a different file size.");
        }
        MessageDigest digest = SyncCrypto.sha256();
        long received = 0;
        while (true) {
            byte[] frame = mChannel.receive(SyncMessages.MAX_CHUNK_FRAME);
            byte type = SyncMessages.typeOf(frame);
            if (type == SyncMessages.FILE_CHUNK) {
                byte[] chunk = SyncMessages.read(frame, SyncMessages.FILE_CHUNK).readChunk();
                received += chunk.length;
                if (received > size) {
                    mChannel.close();
                    throw new SyncProtocolException("The other device sent more than it announced.");
                }
                digest.update(chunk);
                out.write(chunk);
                if (progress != null) {
                    progress.onProgress(received);
                }
            } else if (type == SyncMessages.FILE_END) {
                SyncMessages.Reader end = SyncMessages.read(frame, SyncMessages.FILE_END);
                byte[] claimed = end.readFixed(SyncCrypto.HASH_LENGTH);
                end.expectEnd();
                byte[] actual = digest.digest();
                if (received != size || !MessageDigest.isEqual(claimed, actual)) {
                    mChannel.close();
                    throw new SyncProtocolException("The file arrived incomplete or altered.");
                }
                return actual;
            } else {
                throwIfError(frame);
                mChannel.close();
                throw new SyncProtocolException("Unexpected message during a download.");
            }
        }
    }

    @Override
    public void close() {
        if (!mChannel.isDead()) {
            try {
                mChannel.send(SyncMessages.simple(SyncMessages.BYE));
            } catch (IOException ignore) {
            }
        }
        mChannel.close();
    }

    private static void throwIfError(@NonNull byte[] message) throws IOException {
        if (SyncMessages.typeOf(message) == SyncMessages.ERROR) {
            SyncMessages.Reader r = SyncMessages.read(message, SyncMessages.ERROR);
            int code = r.readInt();
            String text = r.readString(SyncMessages.MAX_LABEL_CHARS);
            throw new RemoteError(code, text);
        }
    }
}
