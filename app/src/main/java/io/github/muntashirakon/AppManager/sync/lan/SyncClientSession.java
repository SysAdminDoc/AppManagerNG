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
    /**
     * 0 until {@link #hostRevision()} asks.
     */
    private int mHostRevision;

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
     * The host's message revision, asked once and remembered. A host from before revisions
     * existed answers with a bare PONG, which reads as 1.
     */
    @WorkerThread
    public int hostRevision() throws IOException {
        if (mHostRevision == 0) {
            mChannel.send(SyncMessages.revisionQuery(SyncMessages.REVISION));
            SyncMessages.Reader r = SyncMessages.read(mChannel.receive(SyncMessages.MAX_CONTROL), SyncMessages.PONG);
            int revision = r.hasMore() ? r.readInt() : 1;
            r.expectEnd();
            if (revision < 1) {
                mChannel.close();
                throw new SyncProtocolException("The other device sent a bad revision.");
            }
            mHostRevision = revision;
        }
        return mHostRevision;
    }

    /**
     * Whether the host can continue a download part way into a file.
     */
    @WorkerThread
    public boolean canResume() throws IOException {
        return hostRevision() >= SyncMessages.REVISION_RESUME;
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
        return download(packageName, fileName, expectedSize, 0, null, out, progress);
    }

    /**
     * Like {@link #download(String, String, long, OutputStream, ProgressListener)}, but skips the
     * first {@code offset} bytes, which the caller already has. {@code prefixDigest} must already
     * hold those bytes: the host's digest covers the whole file, so it's the only way to check the
     * stitched copy. Progress counts from the offset. A host that can't resume is refused here,
     * before anything is sent, and the session stays usable.
     */
    @WorkerThread
    @NonNull
    public byte[] download(@NonNull String packageName, @NonNull String fileName, long expectedSize, long offset,
                           @Nullable MessageDigest prefixDigest, @NonNull OutputStream out,
                           @Nullable ProgressListener progress) throws IOException {
        if (!SyncMessages.isSafeFileName(fileName) || expectedSize < 0 || expectedSize > SyncMessages.MAX_FILE_SIZE) {
            throw new SyncProtocolException("Refusing to request " + fileName);
        }
        if (offset < 0 || offset > expectedSize) {
            throw new SyncProtocolException("Bad start offset for " + fileName);
        }
        if (offset > 0) {
            if (prefixDigest == null) {
                throw new IllegalArgumentException("A resumed download needs the digest of what is already here.");
            }
            if (!canResume()) {
                throw new SyncProtocolException("The other device can't resume a download.");
            }
        }
        mChannel.send(SyncMessages.getRequest(packageName, fileName, expectedSize, offset));
        byte[] reply = mChannel.receive(SyncMessages.MAX_CONTROL);
        throwIfError(reply);
        SyncMessages.Reader header = SyncMessages.read(reply, SyncMessages.FILE_HEADER);
        long size = header.readLong();
        long skipped = offset > 0 ? header.readLong() : 0;
        header.expectEnd();
        if (size != expectedSize) {
            mChannel.close();
            throw new SyncProtocolException("The other device announced a different file size.");
        }
        if (skipped != offset) {
            mChannel.close();
            throw new SyncProtocolException("The other device started from a different place.");
        }
        MessageDigest digest = offset > 0 ? prefixDigest : SyncCrypto.sha256();
        long received = offset;
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

    /**
     * Tells the host goodbye, then closes the connection. The goodbye is a network write, so this
     * must not run on the main thread.
     */
    @WorkerThread
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
