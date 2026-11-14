// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * Reads single entries out of a zip file through positional reads on an open channel, using only
 * the central directory. Every offset and size comes from the file, so each one is checked against
 * the file and the caller's limit before anything is allocated. ZIP64, split and encrypted
 * archives, and compression other than stored and deflate are refused.
 */
final class BoundedZipReader {
    static final int MAX_CENTRAL_DIRECTORY_BYTES = 4 * 1024 * 1024;

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int LOCAL_SIGNATURE = 0x04034b50;
    private static final int EOCD_SIZE = 22;
    private static final int CENTRAL_HEADER_SIZE = 46;
    private static final int LOCAL_HEADER_SIZE = 30;
    private static final int MAX_COMMENT_LENGTH = 0xFFFF;
    private static final int METHOD_STORED = 0;
    private static final int METHOD_DEFLATED = 8;
    private static final int FLAG_ENCRYPTED = 1;
    private static final int INFLATE_CHUNK = 64 * 1024;

    private static final class Entry {
        final int flags;
        final int method;
        final long crc;
        final long compressedSize;
        final long size;
        final long localHeaderOffset;

        Entry(int flags, int method, long crc, long compressedSize, long size, long localHeaderOffset) {
            this.flags = flags;
            this.method = method;
            this.crc = crc;
            this.compressedSize = compressedSize;
            this.size = size;
            this.localHeaderOffset = localHeaderOffset;
        }
    }

    @NonNull
    private final FileChannel mChannel;
    private final long mCentralDirectoryOffset;
    private final Map<String, Entry> mEntries = new HashMap<>();

    BoundedZipReader(@NonNull FileChannel channel) throws IOException {
        mChannel = channel;
        long fileSize = channel.size();
        if (fileSize < EOCD_SIZE) {
            throw new ZipException("Not a zip file");
        }
        int tailLength = (int) Math.min(fileSize, EOCD_SIZE + MAX_COMMENT_LENGTH);
        long tailStart = fileSize - tailLength;
        ByteBuffer tail = readFully(tailStart, tailLength);
        int eocd = -1;
        for (int i = tailLength - EOCD_SIZE; i >= 0; --i) {
            // The record's comment has to end where the file does
            if (tail.getInt(i) == EOCD_SIGNATURE && i + EOCD_SIZE + u16(tail, i + 20) == tailLength) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) {
            throw new ZipException("No end of central directory");
        }
        int disk = u16(tail, eocd + 4);
        int centralDirectoryDisk = u16(tail, eocd + 6);
        int entriesOnDisk = u16(tail, eocd + 8);
        int entryCount = u16(tail, eocd + 10);
        long centralDirectorySize = u32(tail, eocd + 12);
        long centralDirectoryOffset = u32(tail, eocd + 16);
        if (entryCount == 0xFFFF || centralDirectorySize == 0xFFFFFFFFL || centralDirectoryOffset == 0xFFFFFFFFL) {
            throw new ZipException("ZIP64 is not supported");
        }
        if (disk != 0 || centralDirectoryDisk != 0 || entriesOnDisk != entryCount) {
            throw new ZipException("Split archives are not supported");
        }
        if (centralDirectorySize > MAX_CENTRAL_DIRECTORY_BYTES
                || centralDirectoryOffset + centralDirectorySize > tailStart + eocd) {
            throw new ZipException("Bad central directory");
        }
        mCentralDirectoryOffset = centralDirectoryOffset;
        int centralSize = (int) centralDirectorySize;
        ByteBuffer central = readFully(centralDirectoryOffset, centralSize);
        int position = 0;
        for (int i = 0; i < entryCount; ++i) {
            if (position + CENTRAL_HEADER_SIZE > centralSize || central.getInt(position) != CENTRAL_SIGNATURE) {
                throw new ZipException("Bad central directory entry");
            }
            int nameLength = u16(central, position + 28);
            int next = position + CENTRAL_HEADER_SIZE + nameLength + u16(central, position + 30)
                    + u16(central, position + 32);
            if (next > centralSize) {
                throw new ZipException("Bad central directory entry");
            }
            long compressedSize = u32(central, position + 20);
            long size = u32(central, position + 24);
            long localHeaderOffset = u32(central, position + 42);
            if (compressedSize == 0xFFFFFFFFL || size == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL) {
                throw new ZipException("ZIP64 is not supported");
            }
            byte[] name = new byte[nameLength];
            ((Buffer) central).position(position + CENTRAL_HEADER_SIZE);
            central.get(name);
            String entryName = new String(name, StandardCharsets.UTF_8);
            if (!mEntries.containsKey(entryName)) {
                mEntries.put(entryName, new Entry(u16(central, position + 8), u16(central, position + 10),
                        u32(central, position + 16), compressedSize, size, localHeaderOffset));
            }
            position = next;
        }
    }

    /**
     * @return the entry's contents, or {@code null} if the archive has no entry by that name
     * @throws ZipException if the entry is larger than {@code maxBytes}, encrypted, or damaged
     */
    @Nullable
    byte[] read(@NonNull String name, int maxBytes) throws IOException {
        Entry entry = mEntries.get(name);
        if (entry == null) {
            return null;
        }
        if ((entry.flags & FLAG_ENCRYPTED) != 0) {
            throw new ZipException("Encrypted entry");
        }
        if (entry.size > maxBytes) {
            throw new ZipException("Entry is larger than " + maxBytes + " bytes");
        }
        if (entry.localHeaderOffset + LOCAL_HEADER_SIZE > mCentralDirectoryOffset) {
            throw new ZipException("Bad local header offset");
        }
        ByteBuffer local = readFully(entry.localHeaderOffset, LOCAL_HEADER_SIZE);
        if (local.getInt(0) != LOCAL_SIGNATURE) {
            throw new ZipException("Bad local header");
        }
        long dataStart = entry.localHeaderOffset + LOCAL_HEADER_SIZE + u16(local, 26) + u16(local, 28);
        if (dataStart + entry.compressedSize > mCentralDirectoryOffset) {
            throw new ZipException("Entry data runs past the archive");
        }
        int size = (int) entry.size;
        byte[] contents;
        if (entry.method == METHOD_STORED) {
            if (entry.compressedSize != entry.size) {
                throw new ZipException("Bad stored entry size");
            }
            contents = readFully(dataStart, size).array();
        } else if (entry.method == METHOD_DEFLATED) {
            contents = inflate(dataStart, entry.compressedSize, size);
        } else {
            throw new ZipException("Unsupported compression method " + entry.method);
        }
        CRC32 crc = new CRC32();
        crc.update(contents, 0, contents.length);
        if (crc.getValue() != entry.crc) {
            throw new ZipException("CRC mismatch");
        }
        return contents;
    }

    @NonNull
    private byte[] inflate(long start, long compressedSize, int size) throws IOException {
        byte[] out = new byte[size];
        byte[] probe = new byte[1];
        ByteBuffer chunk = ByteBuffer.allocate((int) Math.max(1, Math.min(INFLATE_CHUNK, compressedSize)));
        long position = start;
        long end = start + compressedSize;
        boolean paddingGiven = false;
        int written = 0;
        Inflater inflater = new Inflater(true);
        try {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    throw new ZipException("Entry needs a preset dictionary");
                }
                if (inflater.needsInput()) {
                    if (position < end) {
                        int length = (int) Math.min(chunk.capacity(), end - position);
                        ((Buffer) chunk).clear();
                        ((Buffer) chunk).limit(length);
                        readFully(chunk, position);
                        inflater.setInput(chunk.array(), 0, length);
                        position += length;
                    } else if (!paddingGiven) {
                        // Inflater without the zlib wrapper may need one byte past the data
                        paddingGiven = true;
                        inflater.setInput(probe, 0, 1);
                    } else {
                        throw new ZipException("Truncated entry");
                    }
                    continue;
                }
                int count = written < size ? inflater.inflate(out, written, size - written) : inflater.inflate(probe);
                if (written == size && count > 0) {
                    // The entry inflates past the size it declares
                    throw new ZipException("Entry is larger than it declares");
                }
                written += count;
                if (count == 0 && !inflater.finished() && !inflater.needsInput() && !inflater.needsDictionary()) {
                    throw new ZipException("Inflater made no progress");
                }
            }
        } catch (DataFormatException e) {
            ZipException zipException = new ZipException("Bad deflate data");
            zipException.initCause(e);
            throw zipException;
        } finally {
            inflater.end();
        }
        if (written != size) {
            throw new ZipException("Entry is smaller than it declares");
        }
        return out;
    }

    @NonNull
    private ByteBuffer readFully(long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        readFully(buffer, position);
        return buffer;
    }

    private void readFully(@NonNull ByteBuffer buffer, long position) throws IOException {
        int start = buffer.position();
        while (buffer.hasRemaining()) {
            int count = mChannel.read(buffer, position + buffer.position() - start);
            if (count < 0) {
                throw new EOFException();
            }
        }
    }

    private static int u16(@NonNull ByteBuffer buffer, int index) {
        return buffer.getShort(index) & 0xFFFF;
    }

    private static long u32(@NonNull ByteBuffer buffer, int index) {
        return buffer.getInt(index) & 0xFFFFFFFFL;
    }
}
