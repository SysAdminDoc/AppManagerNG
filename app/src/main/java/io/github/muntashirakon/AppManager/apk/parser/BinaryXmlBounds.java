// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks a binary XML document's structure before ARSCLib reads it, and drops the chunks Android
 * skips.
 *
 * <p>ARSCLib sizes its arrays from the counts and lengths written in the file and reads every
 * string up front, so a damaged or hostile file can make it allocate gigabytes, recurse until the
 * stack runs out, or spend minutes on deeply nested elements. This applies the bounds Android's
 * own ResXMLTree applies (every chunk inside its parent and on a 4-byte boundary, string pool
 * tables and strings inside the pool, attributes inside their element, a node header on every
 * chunk once the elements start) plus a nesting limit, so such a file fails fast with an
 * {@link IOException}. Unlike Android, which checks a string only when it's read, every string is
 * checked, because ARSCLib reads them all.
 *
 * <p>Android also reads past chunks it doesn't use ({@code ResXMLTree::setTo} and
 * {@code ResXMLParser::nextNode}), and ARSCLib reads some of them differently, so those chunks are
 * removed here:
 * <ul>
 * <li>Before the first node Android uses only the last string pool and the last resource map.
 * ARSCLib uses the first of each and refuses other chunks.</li>
 * <li>From the first node on Android uses only namespace, element and CDATA nodes. ARSCLib
 * refuses other chunks there.</li>
 * <li>An end element with no open element closes nothing. ARSCLib keeps it but stops counting
 * depth, which would let deeper nesting through.</li>
 * <li>Android stops reading at the root element's end, so every chunk after it is dropped, never
 * checked, except a namespace end that closes a namespace still open.</li>
 * <li>Android reads a document only up to the size its header gives, so bytes past that are
 * dropped too.</li>
 * </ul>
 *
 * <p>As in {@code ResXMLParser::nextNode}, a namespace, element or CDATA node too short for its
 * extension is refused, a stray end element included.</p>
 */
final class BinaryXmlBounds {
    static final int MAX_DEPTH = 256;

    private static final int RES_NULL_TYPE = 0x0000;
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_TYPE = 0x0003;
    private static final int RES_XML_FIRST_CHUNK_TYPE = 0x0100;
    private static final int RES_XML_START_NAMESPACE_TYPE = 0x0100;
    private static final int RES_XML_END_NAMESPACE_TYPE = 0x0101;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    private static final int RES_XML_CDATA_TYPE = 0x0104;
    private static final int RES_XML_LAST_CHUNK_TYPE = 0x017f;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final int NODE_HEADER_SIZE = 16;
    private static final int STRING_POOL_HEADER_SIZE = 28;
    private static final int NAMESPACE_EXTENSION_SIZE = 8;
    private static final int ELEMENT_EXTENSION_SIZE = 20;
    private static final int END_ELEMENT_EXTENSION_SIZE = 8;
    private static final int CDATA_EXTENSION_SIZE = 12;
    private static final int ATTRIBUTE_SIZE = 20;
    private static final int UTF8_FLAG = 1 << 8;

    private BinaryXmlBounds() {
    }

    /**
     * Checks {@code data} and returns the document ARSCLib should read: {@code data} itself, or a copy
     * without the chunks Android skips.
     */
    @NonNull
    static byte[] check(@NonNull byte[] data) throws IOException {
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
        // Counted first and copied second, so a file of tiny chunks needs no list as long as itself
        long dropped = walk(b, headerSize, (int) size, null);
        if (dropped == 0) {
            // Android reads only the size the header gives
            return size == data.length ? data : Arrays.copyOf(data, (int) size);
        }
        byte[] out = new byte[(int) (size - dropped)];
        walk(b, headerSize, (int) size, out);
        return out;
    }

    /**
     * Checks the chunks after the header and returns how many bytes the dropped ones take. With
     * {@code out}, sized for what's kept, it also copies the document there without them and with
     * the root size reduced to match.
     */
    private static long walk(@NonNull ByteBuffer b, int headerSize, int size, @Nullable byte[] out)
            throws IOException {
        long lastPool = -1;
        long lastMap = -1;
        for (long pos = headerSize; pos + CHUNK_HEADER_SIZE <= size; ) {
            int chunkType = u16(b, (int) pos);
            long chunkSize = u32(b, (int) pos + 4);
            if (isNode(chunkType) || chunkSize < CHUNK_HEADER_SIZE || pos + chunkSize > size) {
                // The first node, or a bad chunk the loop below refuses
                break;
            }
            if (chunkType == RES_STRING_POOL_TYPE) {
                lastPool = pos;
            } else if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                lastMap = pos;
            }
            pos += chunkSize;
        }
        long dropped = 0;
        int copiedTo = 0;
        int outPos = 0;
        long pos = headerSize;
        int depth = 0;
        int openNamespaces = 0;
        boolean inNodes = false;
        boolean rootClosed = false;
        // Android ignores a tail too short to hold a chunk header
        while (pos + CHUNK_HEADER_SIZE <= size) {
            int at = (int) pos;
            int chunkType = u16(b, at);
            int chunkHeader = u16(b, at + 2);
            long chunkSize = u32(b, at + 4);
            if (isNode(chunkType)) {
                inNodes = true;
            }
            boolean bad = chunkHeader < CHUNK_HEADER_SIZE || chunkSize < chunkHeader || pos + chunkSize > size;
            boolean misaligned = ((chunkHeader | chunkSize) & 3) != 0;
            boolean keep;
            if (rootClosed && bad) {
                // Its size can't be trusted to find the next chunk, so the rest goes too
                chunkSize = size - pos;
                keep = false;
            } else if (rootClosed) {
                // Android has stopped reading. A namespace end that closes one still open stays so
                // ARSCLib reads a whole document, and nothing else does.
                keep = chunkType == RES_XML_END_NAMESPACE_TYPE && openNamespaces > 0 && !misaligned
                        && chunkHeader >= NODE_HEADER_SIZE && chunkSize - chunkHeader >= extensionSize(chunkType);
            } else if (bad) {
                throw new IOException("Bad chunk at offset " + pos);
            } else if (misaligned) {
                throw new IOException("Chunk off a 4-byte boundary at offset " + pos);
            } else if (!inNodes) {
                keep = pos == lastPool || pos == lastMap;
            } else if (chunkHeader < NODE_HEADER_SIZE) {
                // nextNode validates every chunk as a node, the ones it skips too
                throw new IOException("Bad node header at offset " + pos);
            } else if (chunkSize - chunkHeader < extensionSize(chunkType)) {
                // As nextNode does, a stray end element included
                throw new IOException("Node too short for its type at offset " + pos);
            } else if (chunkType == RES_XML_END_ELEMENT_TYPE) {
                keep = depth > 0;
            } else {
                keep = chunkType >= RES_XML_START_NAMESPACE_TYPE && chunkType <= RES_XML_CDATA_TYPE;
            }
            if (!keep) {
                dropped += chunkSize;
                if (out != null) {
                    System.arraycopy(b.array(), copiedTo, out, outPos, at - copiedTo);
                    outPos += at - copiedTo;
                    copiedTo = (int) (pos + chunkSize);
                }
            } else if (chunkType == RES_STRING_POOL_TYPE) {
                checkStringPool(b, at, chunkHeader, chunkSize);
            } else if (chunkType == RES_XML_START_NAMESPACE_TYPE) {
                ++openNamespaces;
            } else if (chunkType == RES_XML_END_NAMESPACE_TYPE) {
                openNamespaces = Math.max(0, openNamespaces - 1);
            } else if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                checkElement(b, at, chunkHeader, chunkSize);
                if (++depth > MAX_DEPTH) {
                    throw new IOException("Elements nested deeper than " + MAX_DEPTH);
                }
            } else if (chunkType == RES_XML_END_ELEMENT_TYPE) {
                if (--depth == 0) {
                    rootClosed = true;
                }
            }
            pos += chunkSize;
        }
        if (out != null) {
            System.arraycopy(b.array(), copiedTo, out, outPos, size - copiedTo);
            ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putInt(4, (int) (size - dropped));
        }
        return dropped;
    }

    private static boolean isNode(int chunkType) {
        return chunkType >= RES_XML_FIRST_CHUNK_TYPE && chunkType <= RES_XML_LAST_CHUNK_TYPE;
    }

    /** The extension nextNode requires after a node's header, 0 for a type it skips. */
    private static int extensionSize(int chunkType) {
        switch (chunkType) {
            case RES_XML_START_NAMESPACE_TYPE:
            case RES_XML_END_NAMESPACE_TYPE:
                return NAMESPACE_EXTENSION_SIZE;
            case RES_XML_START_ELEMENT_TYPE:
                return ELEMENT_EXTENSION_SIZE;
            case RES_XML_END_ELEMENT_TYPE:
                return END_ELEMENT_EXTENSION_SIZE;
            case RES_XML_CDATA_TYPE:
                return CDATA_EXTENSION_SIZE;
            default:
                return 0;
        }
    }

    private static void checkElement(@NonNull ByteBuffer b, int at, int header, long size) throws IOException {
        if (header + (long) ELEMENT_EXTENSION_SIZE > size) {
            throw new IOException("Truncated element at offset " + at);
        }
        int attributeStart = u16(b, at + header + 8);
        int attributeSize = u16(b, at + header + 10);
        int attributeCount = u16(b, at + header + 12);
        // Android checks the bounds even when there are no attributes. The minimum stride is this
        // decoder's own rule: ARSCLib reads each attribute as a whole 20-byte record.
        if (header + attributeStart + (long) attributeCount * attributeSize > size
                || attributeCount > 0 && attributeSize < ATTRIBUTE_SIZE) {
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
