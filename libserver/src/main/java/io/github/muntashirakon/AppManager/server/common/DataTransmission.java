// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * <code>DataTransmission</code> class handles the data sent and received by server or client.
 */
// Copyright 2017 Zheng Li
public final class DataTransmission implements Closeable {
    /**
     * Protocol version. Neither side ever sends the token: each proves it holds it with an HMAC
     * over both nonces (see {@link #shakeHands(String, Role)}). Up to 1.3.0 the client sent the
     * token first, so whatever listened on the port got it.
     */
    public static final String PROTOCOL_VERSION = "1.4.0";

    private static final String CHALLENGE = "challenge";
    private static final String AUTH = "auth";
    private static final String OK = "ok";
    private static final String SERVER_PROOF = "amng-server-proof";
    private static final String CLIENT_PROOF = "amng-client-proof";
    private static final int NONCE_BYTES = 16;
    private static final int MAX_NONCE_LENGTH = 64;

    /**
     * Hard cap on a single length-prefixed message. The length is read straight off the
     * socket before authentication, so an unbounded value lets any local peer trigger a
     * multi-gigabyte allocation (OutOfMemoryError kills the accept loop) or a negative
     * size (NegativeArraySizeException drops the listener). 64 MiB is far above any real
     * command payload while keeping a malformed packet cheap to reject.
     */
    private static final int MAX_MESSAGE_LENGTH = 64 * 1024 * 1024;

    public enum Role {
        Server,
        Client
    }

    @NonNull
    private final DataOutputStream mOutputStream;
    @NonNull
    private final DataInputStream mInputStream;
    private final boolean mAsync;

    @Nullable
    private OnReceiveCallback mOnReceiveCallback;
    private boolean mRunning = true;

    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream,
                            @Nullable OnReceiveCallback onReceiveCallback, boolean async) {
        mOutputStream = new DataOutputStream(outputStream);
        mInputStream = new DataInputStream(inputStream);
        mOnReceiveCallback = onReceiveCallback;
        mAsync = async;
    }

    /**
     * Create a new asynchronous data transfer object with receiver callback
     *
     * @param outputStream      Stream where new messages will be written
     * @param inputStream       Stream where new messages will be read from
     * @param onReceiveCallback The callback object whose method is called after receiving new messages
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream,
                            @Nullable OnReceiveCallback onReceiveCallback) {
        this(outputStream, inputStream, onReceiveCallback, true);
    }

    /**
     * Create a new asynchronous data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream) {
        this(outputStream, inputStream, true);
    }

    /**
     * Create a new data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     * @param async        Whether the transfer should be asynchronous or synchronous
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream, boolean async) {
        this(outputStream, inputStream, null, async);
    }

    /**
     * Set custom callback for receiving message.
     *
     * @param onReceiveCallback Callback that wants to receive message.
     */
    public void setOnReceiveCallback(@Nullable OnReceiveCallback onReceiveCallback) {
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Send text message
     *
     * @param text Text to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(byte[])
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(@Nullable String text) throws IOException {
        if (text != null) {
            sendMessage(text.getBytes());
        }
    }

    /**
     * Send message as bytes
     *
     * @param messageBytes Bytes to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(String)
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(@Nullable byte[] messageBytes) throws IOException {
        if (messageBytes != null) {
            mOutputStream.writeInt(messageBytes.length);
            mOutputStream.write(messageBytes);
            mOutputStream.flush();
        }
    }

    /**
     * Read response as bytes after sending a message
     *
     * @return The bytes to be read
     * @throws IOException When it fails to read the message
     */
    @NonNull
    private byte[] readMessage() throws IOException {
        int len = mInputStream.readInt();
        if (len < 0 || len > MAX_MESSAGE_LENGTH) {
            throw new IOException("Invalid message length: " + len);
        }
        byte[] bytes = new byte[len];
        mInputStream.readFully(bytes, 0, len);
        return bytes;
    }

    /**
     * Send and receive messages at the same time (half-duplex)
     *
     * @param messageBytes Bytes to be sent
     * @return Bytes to be read
     * @throws IOException When it fails to send or read the message
     * @see #sendMessage(String)
     * @see #sendMessage(byte[])
     */
    @NonNull
    public synchronized byte[] sendAndReceiveMessage(@NonNull byte[] messageBytes) throws IOException {
        sendMessage(messageBytes);
        return readMessage();
    }

    /**
     * Handshake: each side proves it holds the token without sending it.
     * <ol>
     * <li>Client: <code>version,client-nonce</code></li>
     * <li>Server: <code>challenge,server-nonce,proof(server, client-nonce, server-nonce)</code></li>
     * <li>Client: <code>auth,proof(client, server-nonce, client-nonce)</code></li>
     * <li>Server: <code>ok</code></li>
     * </ol>
     * A proof is HMAC-SHA256 keyed with the token, in hex. The two sides use different labels, so
     * one side's proof can never be replayed as the other's, and fresh nonces on both sides keep an
     * old exchange from being replayed. Whatever listens on the port before this app's server
     * learns nothing it can use.
     *
     * @param token The shared token
     * @param role  Which side of the handshake this is
     * @throws IOException                 When the peer can't prove it holds the token
     * @throws ProtocolVersionException    When the client speaks another {@link #PROTOCOL_VERSION}
     * @throws HandshakeRejectedException  (client) When the server didn't prove it holds the
     *                                     token, or refused this client's proof
     */
    public void shakeHands(@NonNull String token, Role role) throws IOException {
        Objects.requireNonNull(token);
        if (token.isEmpty()) {
            // An empty key would make every proof computable by anyone
            throw new IOException("Empty token");
        }
        if (role == Role.Server) {
            FLog.log("DataTransmission#shakeHands: Server protocol: " + PROTOCOL_VERSION);
            // The first packet is fully attacker-controlled, so check its shape before using it
            String[] hello = new String(readMessage(), StandardCharsets.UTF_8).split(",", -1);
            if (hello.length != 2) {
                FLog.log("DataTransmission#shakeHands: Malformed handshake.");
                throw new IOException("Malformed handshake");
            }
            if (!PROTOCOL_VERSION.equals(hello[0])) {
                throw new ProtocolVersionException("Client protocol version: " + hello[0] + ", " +
                        "Server protocol version: " + PROTOCOL_VERSION);
            }
            String clientNonce = hello[1];
            if (!isNonce(clientNonce)) {
                FLog.log("DataTransmission#shakeHands: Handshake has no usable nonce.");
                throw new IOException("Malformed handshake");
            }
            String serverNonce = newNonce();
            sendMessage(CHALLENGE + "," + serverNonce + "," + proof(token, SERVER_PROOF, clientNonce, serverNonce));
            String auth = new String(readMessage(), StandardCharsets.UTF_8);
            if (!constantTimeEquals(AUTH + "," + proof(token, CLIENT_PROOF, serverNonce, clientNonce), auth)) {
                // Never log what the client sent: nothing in it is worth keeping, and FLog lands in
                // a file the diagnostic dump reads.
                FLog.log("DataTransmission#shakeHands: Authentication failed.");
                throw new IOException("Unauthorized client");
            }
            FLog.log("DataTransmission#shakeHands: Authentication successful.");
            sendMessage(OK);
        } else if (role == Role.Client) {
            Log.d("DataTransmission", "shakeHands: Client protocol: " + PROTOCOL_VERSION);
            String clientNonce = newNonce();
            sendMessage(PROTOCOL_VERSION + "," + clientNonce);
            String[] challenge = readHandshakeReply().split(",", -1);
            if (challenge.length != 3 || !CHALLENGE.equals(challenge[0]) || !isNonce(challenge[1])
                    || !constantTimeEquals(proof(token, SERVER_PROOF, clientNonce, challenge[1]), challenge[2])) {
                // Checked before this side proves anything, so a server that can't prove it holds
                // the token gets no proof it could relay
                throw new HandshakeRejectedException("The server didn't acknowledge this app's token.", null);
            }
            String serverNonce = challenge[1];
            sendMessage(AUTH + "," + proof(token, CLIENT_PROOF, serverNonce, clientNonce));
            if (!OK.equals(readHandshakeReply())) {
                throw new HandshakeRejectedException("The server refused this app's token.", null);
            }
        }
    }

    @NonNull
    private String readHandshakeReply() throws IOException {
        try {
            return new String(readMessage(), StandardCharsets.UTF_8);
        } catch (EOFException e) {
            throw new HandshakeRejectedException("The server closed the connection without acknowledging this app's token.", e);
        }
    }

    /**
     * HMAC-SHA256 keyed with the token over <code>label,first,second</code>, in hex. Nonces are hex
     * only, so the commas can't be shifted to make two different inputs read the same.
     */
    @NonNull
    static String proof(@NonNull String token, @NonNull String label, @NonNull String first,
                        @NonNull String second) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal((label + "," + first + "," + second).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IOException("HMAC-SHA256 is unavailable.", e);
        }
    }

    static boolean isNonce(@Nullable String nonce) {
        if (nonce == null || nonce.isEmpty() || nonce.length() > MAX_NONCE_LENGTH) {
            return false;
        }
        for (int i = 0; i < nonce.length(); ++i) {
            char c = nonce.charAt(i);
            // ASCII only: Character.digit() also takes other scripts' digits
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static String newNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Compare two tokens without leaking where they first differ. The comparison time still
     * depends on the expected token's length, which is fixed, not on the peer's input.
     */
    static boolean constantTimeEquals(@NonNull String expected, @Nullable String actual) {
        if (actual == null || actual.isEmpty() || expected.isEmpty()) {
            // An empty token never authenticates anything.
            return false;
        }
        int diff = expected.length() ^ actual.length();
        for (int i = 0; i < expected.length(); ++i) {
            // Wrap rather than stop short, so a shorter input costs the same as a full-length one.
            diff |= expected.charAt(i) ^ actual.charAt(i % actual.length());
        }
        return diff == 0;
    }

    /**
     * Handle for messages received. For asynchronous operations or when the socket is not active,
     * nothing is done. But when server is running {@link #onReceiveMessage(byte[])} is called.
     *
     * @throws IOException When it fails to read the message received
     */
    public void handleReceive() throws IOException {
        if (!mAsync) return;
        while (mRunning) {
            onReceiveMessage(readMessage());
        }
    }

    /**
     * Calls the callback function {@link OnReceiveCallback#onMessage(byte[])}.
     *
     * @param bytes Bytes that was received earlier
     */
    private void onReceiveMessage(@NonNull byte[] bytes) {
        if (mOnReceiveCallback != null) {
            mOnReceiveCallback.onMessage(bytes);
        }
    }

    /**
     * Stop data transmission, called when socket connection is being closed
     */
    @Override
    public void close() {
        mRunning = false;
        try {
            mOutputStream.close();
        } catch (IOException e) {
            FLog.log(e);
        }
        try {
            mInputStream.close();
        } catch (IOException e) {
            FLog.log(e);
        }
    }

    /**
     * The callback that executes when a new message is received
     */
    public interface OnReceiveCallback {
        /**
         * Implement this method to handle the received message
         *
         * @param bytes The message that was received
         */
        void onMessage(@NonNull byte[] bytes);
    }

    /**
     * Indicates that a protocol version mismatch has been occurred
     */
    public static class ProtocolVersionException extends IOException {
        public ProtocolVersionException(String message) {
            super(message);
        }
    }

    /**
     * The server on the port answered, but not as a holder of this app's token: another app's
     * server, a leftover one from before the token changed, or one speaking another protocol.
     */
    public static class HandshakeRejectedException extends IOException {
        public HandshakeRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
