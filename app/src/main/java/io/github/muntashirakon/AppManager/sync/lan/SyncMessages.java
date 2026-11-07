// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Messages carried inside a {@link SecureChannel} once pairing is done. Each starts with a type
 * byte. Every count and length a peer sends is checked against a cap before anything is built
 * from it.
 */
public final class SyncMessages {
    public static final byte ACCEPT = 1;
    public static final byte REJECT = 2;
    public static final byte LIST_REQUEST = 10;
    public static final byte LIST_RESPONSE = 11;
    public static final byte GET_REQUEST = 20;
    public static final byte FILE_HEADER = 21;
    public static final byte FILE_CHUNK = 22;
    public static final byte FILE_END = 23;
    public static final byte ERROR = 30;
    public static final byte BYE = 40;
    public static final byte PING = 41;
    public static final byte PONG = 42;

    public static final int ERR_NOT_FOUND = 1;
    public static final int ERR_BAD_REQUEST = 2;
    public static final int ERR_CHANGED = 3;
    public static final int ERR_IO = 4;
    public static final int ERR_TOO_LARGE = 5;

    /**
     * Requests and small replies (accept, reject, errors, file headers and ends).
     */
    public static final int MAX_CONTROL = 4 * 1024;
    public static final int MAX_LIST = 4 * 1024 * 1024;
    public static final int MAX_CHUNK = 64 * 1024;
    /**
     * A chunk frame is the type byte plus a length plus the data.
     */
    public static final int MAX_CHUNK_FRAME = MAX_CHUNK + 8;
    public static final int MAX_NAME_CHARS = 255;
    public static final int MAX_LABEL_CHARS = 255;
    public static final int MAX_APPS = 10_000;
    public static final int MAX_SIGNERS = 16;
    public static final int MAX_FILES = 64;
    public static final long MAX_FILE_SIZE = 8L * 1024 * 1024 * 1024;

    private SyncMessages() {
    }

    // Builders

    @NonNull
    public static byte[] simple(byte type) {
        return new byte[]{type};
    }

    @NonNull
    public static byte[] accept(boolean remembered) {
        return new byte[]{ACCEPT, (byte) (remembered ? 1 : 0)};
    }

    @NonNull
    public static byte[] listRequest(boolean includeSystem) {
        return new byte[]{LIST_REQUEST, (byte) (includeSystem ? 1 : 0)};
    }

    @NonNull
    public static byte[] listResponse(@NonNull List<SyncApp> apps) throws IOException {
        if (apps.size() > MAX_APPS) {
            throw new SyncProtocolException("Too many apps to list.");
        }
        Writer w = new Writer(LIST_RESPONSE);
        w.out.writeInt(apps.size());
        for (SyncApp app : apps) {
            w.writeString(app.packageName, MAX_NAME_CHARS);
            w.writeString(clip(app.label, MAX_LABEL_CHARS), MAX_LABEL_CHARS);
            w.out.writeLong(app.versionCode);
            w.writeString(clip(app.versionName, MAX_LABEL_CHARS), MAX_LABEL_CHARS);
            w.out.writeBoolean(app.system);
            w.writeDigests(app.signers);
            w.writeDigests(app.pastSigners);
            if (app.files.isEmpty() || app.files.size() > MAX_FILES) {
                throw new SyncProtocolException("Bad file count for " + app.packageName);
            }
            w.out.writeShort(app.files.size());
            for (SyncApp.ApkFileInfo file : app.files) {
                w.writeString(file.name, MAX_NAME_CHARS);
                w.out.writeLong(file.size);
            }
        }
        byte[] bytes = w.toByteArray();
        if (bytes.length > MAX_LIST) {
            throw new SyncProtocolException("The app list is too large to send.");
        }
        return bytes;
    }

    @NonNull
    public static byte[] getRequest(@NonNull String packageName, @NonNull String fileName, long expectedSize)
            throws IOException {
        Writer w = new Writer(GET_REQUEST);
        w.writeString(packageName, MAX_NAME_CHARS);
        w.writeString(fileName, MAX_NAME_CHARS);
        w.out.writeLong(expectedSize);
        return w.toByteArray();
    }

    @NonNull
    public static byte[] fileHeader(long size) throws IOException {
        Writer w = new Writer(FILE_HEADER);
        w.out.writeLong(size);
        return w.toByteArray();
    }

    @NonNull
    public static byte[] fileChunk(@NonNull byte[] buffer, int length) throws IOException {
        if (length <= 0 || length > MAX_CHUNK) {
            throw new IllegalArgumentException("Bad chunk length " + length);
        }
        Writer w = new Writer(FILE_CHUNK);
        w.out.writeInt(length);
        w.out.write(buffer, 0, length);
        return w.toByteArray();
    }

    @NonNull
    public static byte[] fileEnd(@NonNull byte[] sha256) throws IOException {
        Writer w = new Writer(FILE_END);
        w.out.write(sha256);
        return w.toByteArray();
    }

    @NonNull
    public static byte[] error(int code, @NonNull String message) throws IOException {
        Writer w = new Writer(ERROR);
        w.out.writeInt(code);
        w.writeString(clip(message, MAX_LABEL_CHARS), MAX_LABEL_CHARS);
        return w.toByteArray();
    }

    // Parsers

    public static byte typeOf(@NonNull byte[] message) throws SyncProtocolException {
        if (message.length == 0) {
            throw new SyncProtocolException("Empty message.");
        }
        return message[0];
    }

    @NonNull
    public static Reader read(@NonNull byte[] message, byte expectedType) throws SyncProtocolException {
        if (typeOf(message) != expectedType) {
            throw new SyncProtocolException("Expected message " + expectedType + " but got " + message[0] + ".");
        }
        return new Reader(message);
    }

    @NonNull
    public static List<SyncApp> parseList(@NonNull byte[] message) throws IOException {
        Reader r = read(message, LIST_RESPONSE);
        int count = r.in.readInt();
        if (count < 0 || count > MAX_APPS) {
            throw new SyncProtocolException("Bad app count " + count);
        }
        List<SyncApp> apps = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; ++i) {
            String packageName = r.readString(MAX_NAME_CHARS);
            if (!isValidPackageName(packageName)) {
                throw new SyncProtocolException("Bad package name in the app list.");
            }
            String label = r.readString(MAX_LABEL_CHARS);
            long versionCode = r.in.readLong();
            String versionName = r.readString(MAX_LABEL_CHARS);
            boolean system = r.in.readBoolean();
            List<String> signers = r.readDigests();
            List<String> pastSigners = r.readDigests();
            int fileCount = r.in.readUnsignedShort();
            if (fileCount == 0 || fileCount > MAX_FILES) {
                throw new SyncProtocolException("Bad file count " + fileCount);
            }
            List<SyncApp.ApkFileInfo> files = new ArrayList<>(fileCount);
            for (int j = 0; j < fileCount; ++j) {
                String name = r.readString(MAX_NAME_CHARS);
                long size = r.in.readLong();
                if (!isSafeFileName(name) || size < 0 || size > MAX_FILE_SIZE) {
                    throw new SyncProtocolException("Bad file entry in the app list.");
                }
                files.add(new SyncApp.ApkFileInfo(name, size));
            }
            apps.add(new SyncApp(packageName, label, versionCode, versionName, system, signers, pastSigners, files));
        }
        r.expectEnd();
        return apps;
    }

    // Validation

    /**
     * A bare APK file name: no directory parts, no parent references, and an .apk suffix.
     */
    public static boolean isSafeFileName(@NonNull String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_CHARS) return false;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf((char) 0) >= 0) return false;
        if (name.contains("..") || name.startsWith(".")) return false;
        return name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    public static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_CHARS) return false;
        boolean segmentStart = true;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '.') {
                if (segmentStart) return false;
                segmentStart = true;
                continue;
            }
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            boolean digitOrUnderscore = (c >= '0' && c <= '9') || c == '_';
            if (!letter && !digitOrUnderscore) return false;
            segmentStart = false;
        }
        return !segmentStart;
    }

    @NonNull
    private static String clip(@NonNull String s, int maxChars) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    static final class Writer {
        private final ByteArrayOutputStream mBytes = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(mBytes);

        Writer(byte type) throws IOException {
            out.writeByte(type);
        }

        void writeString(@NonNull String s, int maxChars) throws IOException {
            if (s.length() > maxChars) {
                throw new SyncProtocolException("Text is over " + maxChars + " characters.");
            }
            out.writeUTF(s);
        }

        void writeDigests(@NonNull List<String> digests) throws IOException {
            if (digests.size() > MAX_SIGNERS) {
                throw new SyncProtocolException("Too many signers.");
            }
            out.writeByte(digests.size());
            for (String digest : digests) {
                byte[] raw = SyncCrypto.fromHex(digest);
                if (raw.length != SyncCrypto.HASH_LENGTH) {
                    throw new SyncProtocolException("Signer digest is not SHA-256.");
                }
                out.write(raw);
            }
        }

        @NonNull
        byte[] toByteArray() throws IOException {
            out.flush();
            return mBytes.toByteArray();
        }
    }

    public static final class Reader {
        final DataInputStream in;

        Reader(@NonNull byte[] message) {
            in = new DataInputStream(new ByteArrayInputStream(message, 1, message.length - 1));
        }

        public boolean readBoolean() throws IOException {
            try {
                return in.readBoolean();
            } catch (EOFException e) {
                throw new SyncProtocolException("Message is too short.");
            }
        }

        public int readInt() throws IOException {
            try {
                return in.readInt();
            } catch (EOFException e) {
                throw new SyncProtocolException("Message is too short.");
            }
        }

        public long readLong() throws IOException {
            try {
                return in.readLong();
            } catch (EOFException e) {
                throw new SyncProtocolException("Message is too short.");
            }
        }

        @NonNull
        public String readString(int maxChars) throws IOException {
            String s;
            try {
                s = in.readUTF();
            } catch (EOFException e) {
                throw new SyncProtocolException("Message is too short.");
            }
            if (s.length() > maxChars) {
                throw new SyncProtocolException("Text is over " + maxChars + " characters.");
            }
            return s;
        }

        @NonNull
        public byte[] readFixed(int length) throws IOException {
            byte[] out = new byte[length];
            try {
                in.readFully(out);
            } catch (EOFException e) {
                throw new SyncProtocolException("Message is too short.");
            }
            return out;
        }

        /**
         * Reads a length-prefixed chunk of at most {@link #MAX_CHUNK} bytes.
         */
        @NonNull
        public byte[] readChunk() throws IOException {
            int length = readInt();
            if (length <= 0 || length > MAX_CHUNK) {
                throw new SyncProtocolException("Bad chunk length " + length);
            }
            return readFixed(length);
        }

        @NonNull
        List<String> readDigests() throws IOException {
            int count = in.readUnsignedByte();
            if (count > MAX_SIGNERS) {
                throw new SyncProtocolException("Too many signers.");
            }
            List<String> digests = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
                digests.add(SyncCrypto.toHex(readFixed(SyncCrypto.HASH_LENGTH)));
            }
            return digests;
        }

        public void expectEnd() throws IOException {
            if (in.read() != -1) {
                throw new SyncProtocolException("Message has trailing bytes.");
            }
        }
    }
}
