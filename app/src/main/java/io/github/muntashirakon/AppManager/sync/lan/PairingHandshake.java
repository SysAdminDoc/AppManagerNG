// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;

/**
 * The pairing handshake, in two steps.
 * <p>
 * Agreement (plaintext): the host sends HOST_HELLO carrying SHA-256(hostPub, Nh) as a commitment,
 * the client answers with CLIENT_HELLO (its ephemeral key and nonce), and only then does the host
 * reveal its key and nonce. Because the host is bound to its values before it sees the client's,
 * and the client sends its values before it sees the host's, nobody in the middle can search for
 * keys that make the six digits match on both screens. Both sides hash the three frames into a
 * transcript, derive the digits from it, and run HKDF over the ECDH secret with the transcript as
 * salt to get one AES-256 key per direction.
 * <p>
 * Identity (encrypted): each side signs the transcript with its long-term key. A remembered
 * device is recognised by that key's fingerprint, and a signature made in one session cannot be
 * replayed in another because the transcript differs.
 */
public final class PairingHandshake {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_NAME_CHARS = 64;
    static final int MAX_HANDSHAKE_FRAME = 1024;
    static final int MAX_IDENTITY_FRAME = 512;
    static final byte MSG_IDENTITY = 5;
    private static final byte[] MAGIC = {'A', 'M', 'N', 'G'};
    private static final byte ROLE_HOST = 'H';
    private static final byte ROLE_CLIENT = 'C';
    private static final String LABEL_COMMIT = "AMNG-SYNC/1 commit";
    private static final String LABEL_TRANSCRIPT = "AMNG-SYNC/1 transcript";
    private static final String LABEL_KEYS = "AMNG-SYNC/1 keys";
    private static final String LABEL_PROOF = "AMNG-SYNC/1 identity proof";

    private PairingHandshake() {
    }

    /**
     * Result of the first step: a working channel and the digits to compare, but no identity yet.
     */
    public static final class Agreement {
        @NonNull
        final SecureChannel channel;
        @NonNull
        final byte[] transcript;
        @NonNull
        final String sas;
        @NonNull
        final String peerName;
        final boolean host;

        Agreement(@NonNull SecureChannel channel, @NonNull byte[] transcript, @NonNull String sas,
                  @NonNull String peerName, boolean host) {
            this.channel = channel;
            this.transcript = transcript;
            this.sas = sas;
            this.peerName = peerName;
            this.host = host;
        }

        @NonNull
        public String getSas() {
            return sas;
        }

        @NonNull
        public String getPeerName() {
            return peerName;
        }

        @NonNull
        public SecureChannel getChannel() {
            return channel;
        }
    }

    @NonNull
    public static Agreement host(@NonNull FrameIo io, @NonNull String hostName, @NonNull SecureRandom random)
            throws IOException {
        try {
            KeyPair ephemeral = SyncCrypto.generateKeyPair(random);
            byte[] hostPub = SyncCrypto.encodePoint(ephemeral.getPublic());
            byte[] hostNonce = new byte[SyncCrypto.NONCE_LENGTH];
            random.nextBytes(hostNonce);
            byte[] commitment = SyncCrypto.hash(LABEL_COMMIT, hostPub, hostNonce);

            byte[] hello = buildHello(commitment, hostName);
            io.writeFrame(hello);

            byte[] clientHello = io.readFrame(MAX_HANDSHAKE_FRAME);
            DataInputStream in = open(clientHello);
            readMagicAndVersion(in);
            byte[] clientPubBytes = readFixed(in, SyncCrypto.POINT_LENGTH);
            byte[] clientNonce = readFixed(in, SyncCrypto.NONCE_LENGTH);
            String clientName = cleanName(in.readUTF());
            expectEnd(in);
            ECPublicKey clientPub = decodePeerPoint(clientPubBytes, ephemeral);

            byte[] reveal = buildReveal(hostPub, hostNonce);
            io.writeFrame(reveal);

            byte[] transcript = SyncCrypto.hash(LABEL_TRANSCRIPT, hello, clientHello, reveal);
            byte[] secret = SyncCrypto.ecdh(ephemeral.getPrivate(), clientPub);
            byte[] keys = SyncCrypto.hkdf(transcript, secret, LABEL_KEYS.getBytes(StandardCharsets.UTF_8), 64);
            SecureChannel channel = new SecureChannel(io,
                    SyncCrypto.copyOfRange(keys, 32, 64), SyncCrypto.copyOfRange(keys, 0, 32));
            return new Agreement(channel, transcript, SyncCrypto.shortAuthString(transcript), clientName, true);
        } catch (GeneralSecurityException e) {
            io.close();
            throw new SyncProtocolException("Pairing failed: " + e.getMessage(), e);
        } catch (IOException e) {
            io.close();
            throw e;
        }
    }

    @NonNull
    public static Agreement client(@NonNull FrameIo io, @NonNull String clientName, @NonNull SecureRandom random)
            throws IOException {
        try {
            byte[] hello = io.readFrame(MAX_HANDSHAKE_FRAME);
            DataInputStream in = open(hello);
            readMagicAndVersion(in);
            byte[] commitment = readFixed(in, SyncCrypto.HASH_LENGTH);
            String hostName = cleanName(in.readUTF());
            expectEnd(in);

            KeyPair ephemeral = SyncCrypto.generateKeyPair(random);
            byte[] clientPub = SyncCrypto.encodePoint(ephemeral.getPublic());
            byte[] clientNonce = new byte[SyncCrypto.NONCE_LENGTH];
            random.nextBytes(clientNonce);
            byte[] clientHello = buildClientHello(clientPub, clientNonce, clientName);
            io.writeFrame(clientHello);

            byte[] reveal = io.readFrame(MAX_HANDSHAKE_FRAME);
            in = open(reveal);
            byte[] hostPubBytes = readFixed(in, SyncCrypto.POINT_LENGTH);
            byte[] hostNonce = readFixed(in, SyncCrypto.NONCE_LENGTH);
            expectEnd(in);
            if (!MessageDigest.isEqual(commitment, SyncCrypto.hash(LABEL_COMMIT, hostPubBytes, hostNonce))) {
                throw new SyncProtocolException("The other device changed its key after committing to it.");
            }
            ECPublicKey hostPub = decodePeerPoint(hostPubBytes, ephemeral);

            byte[] transcript = SyncCrypto.hash(LABEL_TRANSCRIPT, hello, clientHello, reveal);
            byte[] secret = SyncCrypto.ecdh(ephemeral.getPrivate(), hostPub);
            byte[] keys = SyncCrypto.hkdf(transcript, secret, LABEL_KEYS.getBytes(StandardCharsets.UTF_8), 64);
            SecureChannel channel = new SecureChannel(io,
                    SyncCrypto.copyOfRange(keys, 0, 32), SyncCrypto.copyOfRange(keys, 32, 64));
            return new Agreement(channel, transcript, SyncCrypto.shortAuthString(transcript), hostName, false);
        } catch (GeneralSecurityException e) {
            io.close();
            throw new SyncProtocolException("Pairing failed: " + e.getMessage(), e);
        } catch (IOException e) {
            io.close();
            throw e;
        }
    }

    /**
     * Second step: both sides send a signed proof of their long-term key, then read the peer's.
     * Any failure closes the channel.
     */
    @NonNull
    public static PairedSession authenticate(@NonNull Agreement agreement, @NonNull LocalIdentity identity)
            throws IOException {
        SecureChannel channel = agreement.channel;
        try {
            byte ourRole = agreement.host ? ROLE_HOST : ROLE_CLIENT;
            byte peerRole = agreement.host ? ROLE_CLIENT : ROLE_HOST;
            channel.send(buildIdentityFrame(identity, ourRole, agreement.transcript));
            byte[] peerPub = checkIdentityFrame(channel.receive(MAX_IDENTITY_FRAME), peerRole, agreement.transcript);
            return new PairedSession(channel, agreement.sas, agreement.peerName, peerPub, agreement.host);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    @VisibleForTesting
    @NonNull
    static byte[] buildIdentityFrame(@NonNull LocalIdentity identity, byte role, @NonNull byte[] transcript)
            throws IOException {
        byte[] signature;
        try {
            signature = identity.sign(proofData(role, transcript));
        } catch (GeneralSecurityException e) {
            throw new SyncProtocolException("Could not sign the identity proof.", e);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(MSG_IDENTITY);
        out.write(identity.getPublicPoint());
        out.writeShort(signature.length);
        out.write(signature);
        out.flush();
        return bytes.toByteArray();
    }

    /**
     * Returns the peer's identity point when its signature over this session's transcript checks
     * out, and throws otherwise.
     */
    @VisibleForTesting
    @NonNull
    static byte[] checkIdentityFrame(@NonNull byte[] frame, byte role, @NonNull byte[] transcript)
            throws IOException {
        DataInputStream in = open(frame);
        if (in.readByte() != MSG_IDENTITY) {
            throw new SyncProtocolException("Expected the other device's identity.");
        }
        byte[] pub = readFixed(in, SyncCrypto.POINT_LENGTH);
        int sigLength = in.readUnsignedShort();
        if (sigLength == 0 || sigLength > 128) {
            throw new SyncProtocolException("Identity signature has a bad length.");
        }
        byte[] signature = readFixed(in, sigLength);
        expectEnd(in);
        try {
            ECPublicKey key = SyncCrypto.decodePoint(pub, SyncCrypto.p256());
            if (!SyncCrypto.verify(key, proofData(role, transcript), signature)) {
                throw new SyncProtocolException("The other device's identity proof did not verify.");
            }
        } catch (GeneralSecurityException e) {
            throw new SyncProtocolException("The other device's identity key is not valid.", e);
        }
        return pub;
    }

    @VisibleForTesting
    @NonNull
    static byte[] proofData(byte role, @NonNull byte[] transcript) {
        return SyncCrypto.hash(LABEL_PROOF, new byte[]{role}, transcript);
    }

    @VisibleForTesting
    static byte roleByte(boolean host) {
        return host ? ROLE_HOST : ROLE_CLIENT;
    }

    @NonNull
    static String cleanName(@NonNull String name) {
        StringBuilder sb = new StringBuilder(Math.min(name.length(), MAX_NAME_CHARS));
        for (int i = 0; i < name.length() && sb.length() < MAX_NAME_CHARS; ++i) {
            char c = name.charAt(i);
            if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? "?" : cleaned;
    }

    @NonNull
    private static byte[] buildHello(@NonNull byte[] commitment, @NonNull String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeByte(PROTOCOL_VERSION);
        out.write(commitment);
        out.writeUTF(cleanName(name));
        out.flush();
        return bytes.toByteArray();
    }

    @NonNull
    private static byte[] buildClientHello(@NonNull byte[] pub, @NonNull byte[] nonce, @NonNull String name)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeByte(PROTOCOL_VERSION);
        out.write(pub);
        out.write(nonce);
        out.writeUTF(cleanName(name));
        out.flush();
        return bytes.toByteArray();
    }

    @NonNull
    private static byte[] buildReveal(@NonNull byte[] pub, @NonNull byte[] nonce) {
        byte[] out = new byte[pub.length + nonce.length];
        System.arraycopy(pub, 0, out, 0, pub.length);
        System.arraycopy(nonce, 0, out, pub.length, nonce.length);
        return out;
    }

    @NonNull
    private static ECPublicKey decodePeerPoint(@NonNull byte[] encoded, @NonNull KeyPair ours)
            throws GeneralSecurityException {
        return SyncCrypto.decodePoint(encoded, SyncCrypto.paramsOf(ours));
    }

    private static void readMagicAndVersion(@NonNull DataInputStream in) throws IOException {
        byte[] magic = readFixed(in, MAGIC.length);
        if (!MessageDigest.isEqual(magic, MAGIC)) {
            throw new SyncProtocolException("This is not a Local sync device.");
        }
        int version = in.readUnsignedByte();
        if (version != PROTOCOL_VERSION) {
            throw new SyncProtocolException("The other device uses Local sync version " + version
                    + ". Update the app on both devices.");
        }
    }

    @NonNull
    private static DataInputStream open(@NonNull byte[] frame) {
        return new DataInputStream(new ByteArrayInputStream(frame));
    }

    @NonNull
    private static byte[] readFixed(@NonNull DataInputStream in, int length) throws IOException {
        byte[] out = new byte[length];
        try {
            in.readFully(out);
        } catch (EOFException e) {
            throw new SyncProtocolException("Pairing message is too short.");
        }
        return out;
    }

    private static void expectEnd(@NonNull DataInputStream in) throws IOException {
        if (in.read() != -1) {
            throw new SyncProtocolException("Pairing message has trailing bytes.");
        }
    }
}
