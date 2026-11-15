// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.io.IoUtils;

/**
 * Android reads past chunks it doesn't know (upstream App Manager #2060): before the first node it
 * keeps only the string pool and the resource map, and from the first node on only namespace,
 * element and CDATA nodes. Every reader here has to do the same.
 */
@RunWith(RobolectricTestRunner.class)
public class BinaryXmlSkippedChunkTest {
    private static final String[] FIXTURES = {
            "xml/framework_ref_api35.bin.xml",
            "xml/test_layout.bin.xml",
            "xml/HMS_Core_Android_Manifest.bin.xml",
    };
    /** Types Android skips anywhere: null, a table type, and one past the XML node range. */
    private static final int[] UNKNOWN = {0x0000, 0x0200, 0x01ff};
    /** Types Android skips only once the nodes have started: an unused node type, a string pool, a resource map. */
    private static final int[] UNKNOWN_AMONG_NODES = {0x0105, 0x017f, 0x0001, 0x0180};
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    private static final int ATTR_VERSION_NAME = 0x0101021c;
    /** A private attribute the API 35 table names, so moving android:versionName onto it shows in the output. */
    private static final int PRIVATE_ATTR = 0x01120017;

    @Test
    public void theViewerSkipsUnknownChunksBeforeTheRootAndBetweenElements() throws IOException {
        for (String fixture : FIXTURES) {
            byte[] data = resource(fixture);
            String expected = AndroidBinXmlDecoder.decode(data);
            for (int type : UNKNOWN) {
                assertSame(fixture, type, "before the root", expected, withChunk(data, firstNode(data), type, 16, 16));
                assertSame(fixture, type, "between elements", expected,
                        withChunk(data, secondElement(data), type, 16, 16));
                assertSame(fixture, type, "at the end", expected, withChunk(data, rootEnd(data), type, 16, 16));
            }
            for (int type : UNKNOWN_AMONG_NODES) {
                assertSame(fixture, type, "between elements", expected,
                        withChunk(data, secondElement(data), type, 16, 16));
            }
        }
    }

    @Test
    public void severalSkippedChunksInARowAreAllSkipped() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        byte[] damaged = data;
        for (int type : UNKNOWN_AMONG_NODES) {
            damaged = withChunk(damaged, secondElement(damaged), type, 16, 24);
        }
        for (int type : UNKNOWN) {
            damaged = withChunk(damaged, firstNode(damaged), type, 8, 8);
        }
        assertEquals(AndroidBinXmlDecoder.decode(data), AndroidBinXmlDecoder.decode(damaged));
    }

    @Test
    public void theManifestParserSkipsUnknownChunks() throws IOException {
        byte[] data = resource("xml/HMS_Core_Android_Manifest.bin.xml");
        byte[] damaged = withEveryUnknownChunk(data);

        List<ManifestComponent> components = new ManifestParser(damaged).parseComponents();
        assertEquals(1598, components.size());
        int intentFilters = 0;
        for (ManifestComponent component : components) {
            intentFilters += component.intentFilters.size();
        }
        assertEquals(156, intentFilters);
        assertEquals(503, new ManifestParser(damaged).parseMetadata().size());
    }

    @Test
    public void manifestAttributesSkipUnknownChunks() throws Throwable {
        for (String fixture : new String[]{"xml/framework_ref_api35.bin.xml", "xml/HMS_Core_Android_Manifest.bin.xml"}) {
            byte[] data = resource(fixture);
            assertEquals(fixture, ApkUtils.getManifestAttributes(ByteBuffer.wrap(data)),
                    ApkUtils.getManifestAttributes(ByteBuffer.wrap(withEveryUnknownChunk(data))));
        }
    }

    @Test
    public void aChunkAmongTheNodesNeedsANodeHeader() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        // Before the first node a plain chunk header is enough, as in ResXMLTree::setTo
        assertEquals(AndroidBinXmlDecoder.decode(data),
                AndroidBinXmlDecoder.decode(withChunk(data, firstNode(data), 0x0200, 8, 16)));
        // From the first node on, nextNode validates every chunk as a node
        IOException e = assertThrows(IOException.class,
                () -> AndroidBinXmlDecoder.decode(withChunk(data, secondElement(data), 0x0200, 8, 16)));
        assertTrue(e.getMessage(), e.getMessage().contains("node header"));
    }

    @Test
    public void aSkippedChunkThatRunsPastItsDocumentIsRefused() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        for (int at : new int[]{firstNode(data), secondElement(data)}) {
            byte[] damaged = withChunk(data, at, 0x0200, 16, 16);
            ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putInt(at + 4, damaged.length);
            assertThrows(IOException.class, () -> AndroidBinXmlDecoder.decode(damaged));
            assertThrows(IOException.class, () -> new ManifestParser(damaged).parseComponents());
        }
    }

    /** ResXMLTree::setTo reads every pool before the first node, and only the last one's error counts. */
    @Test
    public void onlyTheLastStringPoolCounts() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        String expected = AndroidBinXmlDecoder.decode(data);
        // One string whose offset table runs past the pool
        byte[] broken = new byte[28];
        ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putShort((short) RES_STRING_POOL_TYPE)
                .putShort((short) 28).putInt(28).putInt(1);
        assertEquals(expected, AndroidBinXmlDecoder.decode(inserted(data, headerSize(data), broken)));
        assertThrows(IOException.class, () -> AndroidBinXmlDecoder.decode(inserted(data, firstNode(data), broken)));
        // A copy after the resource map replaces the first pool
        assertEquals(expected, AndroidBinXmlDecoder.decode(
                inserted(data, firstNode(data), chunk(data, chunkOfType(data, RES_STRING_POOL_TYPE)))));
    }

    /** ResXMLTree::setTo points its resource IDs at each map it meets, so the last one is used. */
    @Test
    public void onlyTheLastResourceMapCounts() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        String expected = AndroidBinXmlDecoder.decode(data);
        byte[] changed = data.clone();
        int map = chunkOfType(changed, RES_XML_RESOURCE_MAP_TYPE);
        replaceId(changed, map, ATTR_VERSION_NAME, PRIVATE_ATTR);
        String expectedChanged = AndroidBinXmlDecoder.decode(changed);
        assertNotEquals(expected, expectedChanged);

        byte[] changedMap = chunk(changed, map);
        assertEquals(expectedChanged, AndroidBinXmlDecoder.decode(inserted(data, firstNode(data), changedMap)));
        assertEquals(expected, AndroidBinXmlDecoder.decode(inserted(data, headerSize(data), changedMap)));
    }

    /** validate_chunk refuses a header or size off a 4-byte boundary, before and among the nodes. */
    @Test
    public void aChunkOffA4ByteBoundaryIsRefusedUntilTheRootCloses() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        String expected = AndroidBinXmlDecoder.decode(data);
        for (int at : new int[]{headerSize(data), firstNode(data), secondElement(data)}) {
            for (int[] headerAndSize : new int[][]{{16, 18}, {10, 16}}) {
                IOException e = assertThrows(IOException.class, () -> AndroidBinXmlDecoder.decode(
                        withChunk(data, at, 0x0200, headerAndSize[0], headerAndSize[1])));
                assertTrue(e.getMessage(), e.getMessage().contains("4-byte"));
            }
        }
        // Android stops reading a manifest at its root element's end
        for (int at : new int[]{afterRootElement(data), rootEnd(data)}) {
            for (int[] headerAndSize : new int[][]{{16, 18}, {10, 16}, {8, 8}, {8, 16}}) {
                assertEquals(Arrays.toString(headerAndSize) + " at " + at, expected, AndroidBinXmlDecoder.decode(
                        withChunk(data, at, 0x0200, headerAndSize[0], headerAndSize[1])));
            }
        }
    }

    @Test
    public void aBrokenChunkAfterTheRootIsDroppedWithEverythingAfterIt() throws IOException {
        byte[] data = resource("xml/framework_ref_api35.bin.xml");
        String expected = AndroidBinXmlDecoder.decode(data);
        for (int at : new int[]{afterRootElement(data), rootEnd(data)}) {
            byte[] pastTheEnd = withChunk(data, at, 0x0200, 16, 16);
            ByteBuffer.wrap(pastTheEnd).order(ByteOrder.LITTLE_ENDIAN).putInt(at + 4, pastTheEnd.length);
            assertEquals("past the end at " + at, expected, AndroidBinXmlDecoder.decode(pastTheEnd));
            assertEquals("short header at " + at, expected,
                    AndroidBinXmlDecoder.decode(withChunk(data, at, 0x0200, 4, 16)));
        }
    }

    @Test
    public void anEndElementWithNothingOpenIsDropped() throws IOException {
        for (String fixture : FIXTURES) {
            byte[] data = resource(fixture);
            String expected = AndroidBinXmlDecoder.decode(data);
            byte[] damaged = withChunk(data, firstNode(data), RES_XML_END_ELEMENT_TYPE, 16, 24);
            damaged = withChunk(damaged, rootEnd(damaged), RES_XML_END_ELEMENT_TYPE, 16, 24);
            assertEquals(fixture, expected, AndroidBinXmlDecoder.decode(damaged));
        }
    }

    private static void assertSame(@NonNull String fixture, int type, @NonNull String where, @NonNull String expected,
                                   @NonNull byte[] damaged) throws IOException {
        assertEquals(String.format("%s with a 0x%04x chunk %s", fixture, type, where), expected,
                AndroidBinXmlDecoder.decode(damaged));
    }

    @NonNull
    private static byte[] withEveryUnknownChunk(@NonNull byte[] data) {
        byte[] damaged = data;
        for (int type : UNKNOWN) {
            damaged = withChunk(damaged, firstNode(damaged), type, 16, 16);
            damaged = withChunk(damaged, secondElement(damaged), type, 16, 16);
            damaged = withChunk(damaged, rootEnd(damaged), type, 16, 16);
        }
        for (int type : UNKNOWN_AMONG_NODES) {
            damaged = withChunk(damaged, secondElement(damaged), type, 16, 16);
        }
        return damaged;
    }

    /**
     * {@code data} with a chunk of {@code size} bytes inserted at {@code at}, filled like a node
     * header (line 1, no comment) and zeros, and the document's size raised to match.
     */
    @NonNull
    private static byte[] withChunk(@NonNull byte[] data, int at, int type, int header, int size) {
        byte[] out = new byte[data.length + size];
        System.arraycopy(data, 0, out, 0, at);
        System.arraycopy(data, at, out, at + size, data.length - at);
        ByteBuffer b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort(at, (short) type).putShort(at + 2, (short) header).putInt(at + 4, size);
        if (size >= 16) {
            b.putInt(at + 8, 1).putInt(at + 12, -1);
        }
        for (int i = at + 16; i < at + size; ++i) {
            out[i] = 0;
        }
        b.putInt(4, b.getInt(4) + size);
        return out;
    }

    /** {@code data} with {@code chunk} inserted at {@code at} and the document's size raised to match. */
    @NonNull
    private static byte[] inserted(@NonNull byte[] data, int at, @NonNull byte[] chunk) {
        byte[] out = new byte[data.length + chunk.length];
        System.arraycopy(data, 0, out, 0, at);
        System.arraycopy(chunk, 0, out, at, chunk.length);
        System.arraycopy(data, at, out, at + chunk.length, data.length - at);
        ByteBuffer b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(4, b.getInt(4) + chunk.length);
        return out;
    }

    @NonNull
    private static byte[] chunk(@NonNull byte[] data, int at) {
        int size = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(at + 4);
        return Arrays.copyOfRange(data, at, at + size);
    }

    private static int headerSize(@NonNull byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(2) & 0xffff;
    }

    private static int chunkOfType(@NonNull byte[] data, int wanted) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int end = b.getInt(4);
        for (int pos = headerSize(data); pos + 8 <= end; pos += b.getInt(pos + 4)) {
            if ((b.getShort(pos) & 0xffff) == wanted) {
                return pos;
            }
        }
        throw new AssertionError("No chunk of type " + wanted);
    }

    private static void replaceId(@NonNull byte[] data, int map, int from, int to) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int end = map + b.getInt(map + 4);
        for (int pos = map + (b.getShort(map + 2) & 0xffff); pos + 4 <= end; pos += 4) {
            if (b.getInt(pos) == from) {
                b.putInt(pos, to);
                return;
            }
        }
        throw new AssertionError("No resource ID " + Integer.toHexString(from));
    }

    /** Just past the end element that closes the root, before the namespace ends. */
    private static int afterRootElement(@NonNull byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int end = b.getInt(4);
        int depth = 0;
        for (int pos = headerSize(data); pos + 8 <= end; ) {
            int type = b.getShort(pos) & 0xffff;
            pos += b.getInt(pos + 4);
            if (type == RES_XML_START_ELEMENT_TYPE) {
                ++depth;
            } else if (type == RES_XML_END_ELEMENT_TYPE && --depth == 0) {
                assertTrue("Nothing follows the root", pos < end);
                return pos;
            }
        }
        throw new AssertionError("The root never closes");
    }

    private static int firstNode(@NonNull byte[] data) {
        return findChunk(data, true);
    }

    private static int secondElement(@NonNull byte[] data) {
        return findChunk(data, false);
    }

    private static int rootEnd(@NonNull byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
    }

    /** The first node, or the second start element. */
    private static int findChunk(@NonNull byte[] data, boolean firstNode) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int end = b.getInt(4);
        int pos = b.getShort(2) & 0xffff;
        int elements = 0;
        while (pos + 8 <= end) {
            int type = b.getShort(pos) & 0xffff;
            if (firstNode && type >= 0x0100 && type <= 0x017f) {
                return pos;
            }
            if (type == RES_XML_START_ELEMENT_TYPE && ++elements == 2) {
                return pos;
            }
            pos += b.getInt(pos + 4);
        }
        throw new AssertionError("No such chunk");
    }

    @NonNull
    private static byte[] resource(@NonNull String name) throws IOException {
        ClassLoader classLoader = Objects.requireNonNull(BinaryXmlSkippedChunkTest.class.getClassLoader());
        try (InputStream in = classLoader.getResourceAsStream(name)) {
            assertNotNull(name, in);
            return IoUtils.readFully(in, -1, true);
        }
    }
}
