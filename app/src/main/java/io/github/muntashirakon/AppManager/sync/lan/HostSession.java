// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;

/**
 * One client connection on the sharing device: pairing, the user's approval, then LIST and GET
 * requests until the client says goodbye, goes quiet, or breaks the protocol.
 * <p>
 * A GET is only ever answered from {@link HostCatalog#apkFiles(String)}, matched on the exact file
 * name, so a client can read the APKs of installed packages and nothing else on the device.
 */
public final class HostSession {
    public static final int HANDSHAKE_TIMEOUT_MS = 15_000;
    public static final int IDLE_TIMEOUT_MS = 90_000;
    public static final long APPROVAL_TIMEOUT_MS = 120_000;

    public interface Approver {
        @NonNull
        ApprovalGate.Decision approve(@NonNull PairedSession session) throws InterruptedException;
    }

    public interface TimeoutSetter {
        void setReadTimeout(int millis) throws IOException;
    }

    public interface Listener {
        void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint);

        void onFileSent(@NonNull String packageName, @NonNull String fileName);
    }

    public static final TimeoutSetter NO_TIMEOUTS = millis -> {
    };

    @NonNull
    private final FrameIo mIo;
    @NonNull
    private final TimeoutSetter mTimeouts;
    @NonNull
    private final String mHostName;
    @NonNull
    private final LocalIdentity mIdentity;
    @NonNull
    private final SecureRandom mRandom;
    @NonNull
    private final TrustedDevices mTrust;
    @NonNull
    private final Approver mApprover;
    @NonNull
    private final HostCatalog mCatalog;
    @Nullable
    private final Listener mListener;
    private int mRevision = SyncMessages.REVISION;
    /**
     * The revision the client sent with a PING, 0 until it does.
     */
    private int mClientRevision;

    public HostSession(@NonNull FrameIo io, @NonNull TimeoutSetter timeouts, @NonNull String hostName,
                       @NonNull LocalIdentity identity, @NonNull SecureRandom random,
                       @NonNull TrustedDevices trust, @NonNull Approver approver,
                       @NonNull HostCatalog catalog, @Nullable Listener listener) {
        mIo = io;
        mTimeouts = timeouts;
        mHostName = hostName;
        mIdentity = identity;
        mRandom = random;
        mTrust = trust;
        mApprover = approver;
        mCatalog = catalog;
        mListener = listener;
    }

    /**
     * Makes this session answer like a host of an older revision, so tests can check that a newer
     * client still works with one. Revision 1 is the first release's exact behavior.
     */
    @VisibleForTesting
    void setRevision(int revision) {
        mRevision = revision;
    }

    /**
     * Runs the session to the end. Returns normally when the client leaves or is turned away,
     * throws when the connection or the protocol fails. The streams are closed either way.
     */
    @WorkerThread
    public void run() throws IOException, InterruptedException {
        try {
            mTimeouts.setReadTimeout(HANDSHAKE_TIMEOUT_MS);
            PairingHandshake.Agreement agreement = PairingHandshake.host(mIo, mHostName, mRandom);
            PairedSession session = PairingHandshake.authenticate(agreement, mIdentity);
            SecureChannel channel = session.getChannel();
            boolean known = mTrust.isTrusted(session.getPeerFingerprint());
            ApprovalGate.Decision decision = known ? ApprovalGate.Decision.ALLOW : mApprover.approve(session);
            if (decision == ApprovalGate.Decision.DENY) {
                channel.send(SyncMessages.simple(SyncMessages.REJECT));
                return;
            }
            boolean remembered = known || decision == ApprovalGate.Decision.ALLOW_AND_REMEMBER;
            if (!known && remembered) {
                mTrust.remember(session.getPeerFingerprint(), session.getPeerName());
            }
            channel.send(SyncMessages.accept(remembered));
            if (mListener != null) {
                mListener.onClientAccepted(session.getPeerName(), session.getPeerFingerprint());
            }
            mTimeouts.setReadTimeout(IDLE_TIMEOUT_MS);
            serve(channel);
        } finally {
            mIo.close();
        }
    }

    private void serve(@NonNull SecureChannel channel) throws IOException {
        while (true) {
            byte[] message = channel.receive(SyncMessages.MAX_CONTROL);
            byte type = SyncMessages.typeOf(message);
            switch (type) {
                case SyncMessages.LIST_REQUEST: {
                    SyncMessages.Reader r = SyncMessages.read(message, SyncMessages.LIST_REQUEST);
                    boolean includeSystem = r.readBoolean();
                    r.expectEnd();
                    byte[] reply;
                    try {
                        reply = SyncMessages.listResponse(mCatalog.listApps(includeSystem),
                                sendsSignerSubjects());
                    } catch (SyncProtocolException e) {
                        reply = SyncMessages.error(SyncMessages.ERR_TOO_LARGE, e.getMessage());
                    }
                    channel.send(reply);
                    break;
                }
                case SyncMessages.GET_REQUEST:
                    if (!serveFile(channel, message)) {
                        return;
                    }
                    break;
                case SyncMessages.PING:
                    channel.send(answerPing(message));
                    break;
                case SyncMessages.BYE:
                    return;
                default:
                    channel.send(SyncMessages.error(SyncMessages.ERR_BAD_REQUEST, "Unknown request."));
                    return;
            }
        }
    }

    /**
     * A bare PING is a keepalive and gets a bare PONG, as it always did. A PING carrying the
     * client's revision gets this host's revision back. Revision 1 never looked past the type
     * byte, which is what lets a client ask an older host without breaking the session.
     */
    @NonNull
    private byte[] answerPing(@NonNull byte[] message) throws IOException {
        if (mRevision >= SyncMessages.REVISION_RESUME) {
            SyncMessages.Reader r = SyncMessages.read(message, SyncMessages.PING);
            if (r.hasMore()) {
                int clientRevision = r.readInt();
                r.expectEnd();
                // A nonsense revision just means the plain list
                mClientRevision = Math.max(clientRevision, 0);
                return SyncMessages.revisionAnswer(mRevision);
            }
        }
        return SyncMessages.simple(SyncMessages.PONG);
    }

    /**
     * Whether the app list carries signer subjects: only when this host and the client both said
     * they're on {@link SyncMessages#REVISION_SIGNER_SUBJECTS}. A client that never asked gets the
     * list it always got.
     */
    private boolean sendsSignerSubjects() {
        return mRevision >= SyncMessages.REVISION_SIGNER_SUBJECTS
                && mClientRevision >= SyncMessages.REVISION_SIGNER_SUBJECTS;
    }

    /**
     * Answers one GET. Returns false when the request was malformed and the session should end.
     */
    private boolean serveFile(@NonNull SecureChannel channel, @NonNull byte[] message) throws IOException {
        SyncMessages.Reader r = SyncMessages.read(message, SyncMessages.GET_REQUEST);
        String packageName = r.readString(SyncMessages.MAX_NAME_CHARS);
        String fileName = r.readString(SyncMessages.MAX_NAME_CHARS);
        long expectedSize = r.readLong();
        boolean ranged = mRevision >= SyncMessages.REVISION_RESUME && r.hasMore();
        long offset = ranged ? r.readLong() : 0;
        r.expectEnd();
        if (!SyncMessages.isValidPackageName(packageName) || !SyncMessages.isSafeFileName(fileName)) {
            channel.send(SyncMessages.error(SyncMessages.ERR_BAD_REQUEST, "That file is not offered."));
            return false;
        }
        if (ranged && (offset <= 0 || offset > expectedSize)) {
            channel.send(SyncMessages.error(SyncMessages.ERR_BAD_REQUEST, "Bad start offset."));
            return false;
        }
        Map<String, File> files = mCatalog.apkFiles(packageName);
        File file = files != null ? files.get(fileName) : null;
        if (file == null || !file.isFile()) {
            channel.send(SyncMessages.error(SyncMessages.ERR_NOT_FOUND, "That file is not offered."));
            return true;
        }
        long size = file.length();
        if (size != expectedSize) {
            channel.send(SyncMessages.error(SyncMessages.ERR_CHANGED, "The app changed on this device."));
            return true;
        }
        InputStream in;
        try {
            in = new FileInputStream(file);
        } catch (IOException e) {
            channel.send(SyncMessages.error(SyncMessages.ERR_IO, "Could not read the file."));
            return true;
        }
        try {
            channel.send(ranged ? SyncMessages.fileHeader(size, offset) : SyncMessages.fileHeader(size));
            MessageDigest digest = SyncCrypto.sha256();
            byte[] buffer = new byte[SyncMessages.MAX_CHUNK];
            // The client already has the first part. It still goes into the digest, since FILE_END
            // vouches for the whole file and that is what the client checks its copy against.
            long skip = offset;
            while (skip > 0) {
                int n;
                try {
                    n = in.read(buffer, 0, (int) Math.min(buffer.length, skip));
                } catch (IOException e) {
                    n = -1;
                }
                if (n < 0) {
                    channel.send(SyncMessages.error(SyncMessages.ERR_IO, "The file changed while it was being sent."));
                    return true;
                }
                digest.update(buffer, 0, n);
                skip -= n;
            }
            long remaining = size - offset;
            while (remaining > 0) {
                int n;
                try {
                    n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                } catch (IOException e) {
                    n = -1;
                }
                if (n < 0) {
                    // The file shrank or vanished under us; the client discards what it got
                    channel.send(SyncMessages.error(SyncMessages.ERR_IO, "The file changed while it was being sent."));
                    return true;
                }
                if (n == 0) {
                    continue;
                }
                digest.update(buffer, 0, n);
                channel.send(SyncMessages.fileChunk(buffer, n));
                remaining -= n;
            }
            channel.send(SyncMessages.fileEnd(digest.digest()));
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }
        if (mListener != null) {
            mListener.onFileSent(packageName, fileName);
        }
        return true;
    }
}
