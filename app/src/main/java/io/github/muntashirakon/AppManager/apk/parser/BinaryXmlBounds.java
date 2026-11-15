// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks a binary XML document's structure before ARSCLib reads it.
 *
 * <p>ARSCLib sizes its arrays from the counts and lengths written in the file and reads every
 * string up front, so a damaged or hostile file can make it allocate gigabytes, recurse until the
 * stack runs out, or spend minutes on deeply nested elements. This applies the bounds Android's
 * own ResXMLTree applies (every chunk inside its parent, string pool tables and strings inside the
 * pool, attributes inside their element) plus a nesting limit, so such a file fails fast with an
 * {@link IOException}. Unlike Android, which checks a string only when it's read, every string is
 * checked, because ARSCLib reads them all.
 */
final class BinaryXmlBounds {
    static final int MAX_DEPTH = 256;

    private static final int RES_NULL_TYPE = 0x0000;
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_TYPE = 0x0003;
    private static final int RES_XML_FIRST_CHUNK_TYPE = 0x0100;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    private static final int RES_XML_LAST_CHUNK_TYPE = 0x017f;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final int NODE_HEADER_SIZE = 16;
    private static final int STRING_POOL_HEADER_SIZE = 28;
    private static final int ELEMENT_EXTENSION_SIZE = 20;
    private static final int ATTRIBUTE_SIZE = 20;
    private static final int UTF8_FLAG = 1 << 8;

    private BinaryXmlBounds() {
    }

    static void check(@NonNull byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < CHUNK_HEADER_SIZE) {
            throw new IOException("Too short for binary XML");
        }
        int type = u16(b, 0);
        int headerSize = u16(b, 2);
        long size = u32(b, 4);
        // NP Manager writes a null type in place of RES_XML_TYPE, see AndroidBinXmlDecoder#isBinaryXml
        if (type != RES_XML_TYPE && type != RES_NULL_TYPE) {
            throw new IOException("Not binary XML");
        }
        if (headerSize < CHUNK_HEADER_SIZE || headerSize > size || size > data.length) {
            throw new IOException("Bad binary XML header");
        }
        long pos = headerSize;
        int depth = 0;
        // Android ignores a tail too short to hold a chunk header
        while (pos + CHUNK_HEADER_SIZE <= size) {
            int at = (int) pos;
            int chunkType = u16(b, at);
            int chunkHeader = u16(b, at + 2);
            long chunkSize = u32(b, at + 4);
            if (chunkHeader < CHUNK_HEADER_SIZE || chunkSize < chunkHeader || pos + chunkSize > size) {
                throw new IOException("Bad chunk at offset " + pos);
            }
            if (chunkType == RES_STRING_POOL_TYPE) {
                checkStringPool(b, at, chunkHeader, chunkSize);
            } else if (chunkType >= RES_XML_FIRST_CHUNK_TYPE && chunkType <= RES_XML_LAST_CHUNK_TYPE) {
                if (chunkHeader < NODE_HEADER_SIZE) {
                    throw new IOException("Bad node header at offset " + pos);
                }
                if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                    checkElement(b, at, chunkHeader, chunkSize);
                    if (++depth > MAX_DEPTH) {
                        throw new IOException("Elements nested deeper than " + MAX_DEPTH);
                    }
                } else if (chunkType == RES_XML_END_ELEMENT_TYPE) {
                    --depth;
                }
            }
            pos += chunkSize;
        }
    }

    private static void checkElement(@NonNull ByteBuffer b, int at, int header, long size) throws IOException {
        if (header + (long) ELEMENT_EXTENSION_SIZE > size) {
            throw new IOException("Truncated element at offset " + at);
        }
        int attributeStart = u16(b, at + header + 8);
        int attributeSize = u16(b, at + header + 10);
        int attributeCount = u16(b, at + header + 12);
        if (attributeCount > 0 && (attributeSize < ATTRIBUTE_SIZE
                || header + attributeStart + (long) attributeCount * attributeSize > size)) {
            throw new IOException("Attributes outside their element at offset " + at);
        }
    }

    private static void checkStringPool(@NonNull ByteBuffer b, int at, int header, long size) throws IOException {
        if (header < STRING_POOL_HEADER_SIZE) {
            throw new IOException("Bad string pool header at offset " + at);
        }
        long stringCount = u32(b, at + 8);
        long styleCount = u32(b, at + 12);
        long flags = u32(b, at + 16);
        long stringsStart = u32(b, at + 20);
        long stylesStart = u32(b, at + 24);
        if (header + 4 * (stringCount + styleCount) > size) {
            throw new IOException("String pool counts too large at offset " + at);
        }
        if ((stringCount > 0 && stringsStart > size) || (styleCount > 0 && stylesStart > size)) {
            throw new IOException("String pool data outside the pool at offset " + at);
        }
        long stringsEnd = styleCount > 0 && stylesStart > stringsStart ? stylesStart : size;
        boolean utf8 = (flags & UTF8_FLAG) != 0;
        for (long i = 0; i < stringCount; ++i) {
            long offset = stringsStart + u32(b, (int) (at + header + 4 * i));
            long end = utf8 ? utf8End(b, at, offset, stringsEnd) : utf16End(b, at, offset, stringsEnd);
            if (end > stringsEnd) {
                throw new IOException("String " + i + " runs past its pool at offset " + at);
            }
        }
    }

    /**
     * Where a UTF-8 string ends, its terminator included: the UTF-16 length, then the UTF-8 length,
     * each one or two bytes, then the bytes.
     */
    private static long utf8End(@NonNull ByteBuffer b, int at, long offset, long end) throws IOException {
        long p = offset + ((u8(b, at, offset, end) & 0x80) != 0 ? 2 : 1);
        int first = u8(b, at, p, end);
        long length;
        if ((first & 0x80) != 0) {
            length = ((first & 0x7f) << 8) | u8(b, at, p + 1, end);
            p += 2;
        } else {
            length = first;
            p += 1;
        }
        return p + length + 1;
    }

    /** Where a UTF-16 string ends, its terminator included: the length in one or two units, then the units. */
    private static long utf16End(@NonNull ByteBuffer b, int at, long offset, long end) throws IOException {
        if (offset + 2 > end) {
            throw new IOException("String outside its pool at offset " + at);
        }
        long length = u16(b, (int) (at + offset));
        long p = offset + 2;
        if ((length & 0x8000) != 0) {
            if (p + 2 > end) {
                throw new IOException("String outside its pool at offset " + at);
            }
            length = ((length & 0x7fff) << 16) | u16(b, (int) (at + p));
            p += 2;
        }
        return p + 2 * length + 2;
    }

    private static int u8(@NonNull ByteBuffer b, int at, long p, long end) throws IOException {
        if (p >= end) {
            throw new IOException("String outside its pool at offset " + at);
        }
        return b.get((int) (at + p)) & 0xff;
    }

    private static int u16(@NonNull ByteBuffer b, int index) {
        return b.getShort(index) & 0xffff;
    }

    private static long u32(@NonNull ByteBuffer b, int index) {
        return b.getInt(index) & 0xffffffffL;
    }
}
