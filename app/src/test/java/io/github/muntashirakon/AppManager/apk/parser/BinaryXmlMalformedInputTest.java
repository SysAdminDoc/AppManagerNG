// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

import io.github.muntashirakon.io.IoUtils;

/**
 * Damaged binary XML must end in a quick {@link IOException} or a decoded document, never another
 * exception, an error such as running out of memory or stack, or a hang.
 */
@RunWith(RobolectricTestRunner.class)
public class BinaryXmlMalformedInputTest {
    private static final String[] FIXTURES = {
            "xml/framework_ref_api21.bin.xml",
            "xml/framework_ref_api35.bin.xml",
            "xml/test_layout.bin.xml",
    };
    private static final int[] STOMPS = {0, -1, 0x7fffffff, 0x80000000, 0x0000ffff, 0x00010000};
    /** A single decode of a few kilobytes that takes longer than this has stopped being bounded. */
    private static final long SLOW_NANOS = 5_000_000_000L;

    /**
     * The sweeps accept a refusal, so they prove nothing if the decoder refuses everything: the
     * undamaged fixtures have to decode.
     */
    @Test
    public void theUndamagedFixturesDecode() throws IOException {
        for (String fixture : FIXTURES) {
            String xml = AndroidBinXmlDecoder.decode(resource(fixture));
            assertTrue(fixture, xml.contains("xmlns:android=\"http://schemas.android.com/apk/res/android\""));
        }
        assertTrue(AndroidBinXmlDecoder.decode(nested(3)).contains("<e>"));
    }

    @Test(timeout = 600_000)
    public void everyTruncationIsRefusedOrDecoded() throws IOException {
        for (String fixture : FIXTURES) {
            byte[] data = resource(fixture);
            for (int length = 0; length < data.length; ++length) {
                expectBounded(fixture + " cut to " + length, Arrays.copyOf(data, length));
            }
        }
    }

    @Test(timeout = 600_000)
    public void everyWordStompIsRefusedOrDecoded() throws IOException {
        for (String fixture : FIXTURES) {
            byte[] data = resource(fixture);
            for (int at = 0; at + 4 <= data.length; at += 2) {
                for (int value : STOMPS) {
                    byte[] copy = data.clone();
                    ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value);
                    expectBounded(String.format("%s with 0x%08x at %d", fixture, value, at), copy);
                }
            }
        }
    }

    @Test(timeout = 600_000)
    public void everyByteFlipIsRefusedOrDecoded() throws IOException {
        for (String fixture : FIXTURES) {
            byte[] data = resource(fixture);
            for (int at = 0; at < data.length; ++at) {
                for (int mask : new int[]{0x01, 0x80, 0xff}) {
                    byte[] copy = data.clone();
                    copy[at] ^= (byte) mask;
                    expectBounded(String.format("%s with byte %d ^ 0x%02x", fixture, at, mask), copy);
                }
            }
        }
    }

    @Test
    public void nestingIsCappedAtTheLimit() throws IOException {
        assertNotNull(AndroidBinXmlDecoder.decode(nested(BinaryXmlBounds.MAX_DEPTH)));
        IOException e = assertThrows(IOException.class,
                () -> AndroidBinXmlDecoder.decode(nested(BinaryXmlBounds.MAX_DEPTH + 1)));
        assertTrue(e.getMessage(), e.getMessage().contains("nested"));
        // Far past the limit: refused before anything recurses
        assertThrows(IOException.class, () -> AndroidBinXmlDecoder.readDocument(nested(100_000)));
    }

    /**
     * An end element with nothing open closes nothing. Counting it anyway let as many extra levels
     * of nesting through as there were stray ends, and ARSCLib then ran out of stack.
     */
    @Test
    public void strayEndElementsDoNotRaiseTheNestingLimit() throws IOException {
        int over = BinaryXmlBounds.MAX_DEPTH + 1;
        for (int[] runs : new int[][]{
                {-5_000, over, -over},
                {1, -1, -5_000, over, -over},
                {-5_000, 5_000 + over, -(5_000 + over)},
        }) {
            IOException e = assertThrows(Arrays.toString(runs), IOException.class,
                    () -> AndroidBinXmlDecoder.decode(elements(runs)));
            assertTrue(e.getMessage(), e.getMessage().contains("nested"));
        }
        int limit = BinaryXmlBounds.MAX_DEPTH;
        assertTrue(AndroidBinXmlDecoder.decode(elements(-5_000, limit, -limit)).contains("<e>"));
    }

    @Test(timeout = 120_000)
    public void manySiblingsAndNamespacesStillDecode() throws IOException {
        StringBuilder wide = new StringBuilder("<root>");
        for (int i = 0; i < 20_000; ++i) {
            wide.append("<item n=\"").append(i).append("\"/>");
        }
        wide.append("</root>");
        expectBounded("20000 siblings", AndroidBinXmlEncoder.encodeString(wide.toString()));

        StringBuilder namespaces = new StringBuilder("<root");
        for (int i = 0; i < 2_000; ++i) {
            namespaces.append(" xmlns:n").append(i).append("=\"urn:n").append(i).append('"');
        }
        namespaces.append("/>");
        expectBounded("2000 namespaces", AndroidBinXmlEncoder.encodeString(namespaces.toString()));
    }

    /**
     * {@code depth} nested {@code <e>} elements, written chunk by chunk: an encoder would recurse as deep.
     */
    @NonNull
    private static byte[] nested(int depth) {
        int poolSize = 28 + 4 + 4;
        int startSize = 36;
        int endSize = 24;
        int total = 8 + poolSize + depth * (startSize + endSize);
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        // RES_XML_TYPE
        b.putShort((short) 0x0003).putShort((short) 8).putInt(total);
        // UTF-8 string pool with one string, "e", starting after the one offset
        b.putShort((short) 0x0001).putShort((short) 28).putInt(poolSize)
                .putInt(1).putInt(0).putInt(0x100).putInt(28 + 4).putInt(0);
        b.putInt(0);
        b.put(new byte[]{1, 1, 'e', 0});
        for (int i = 0; i < depth; ++i) {
            // RES_XML_START_ELEMENT_TYPE: line, no comment, no namespace, name 0, no attributes
            b.putShort((short) 0x0102).putShort((short) 16).putInt(startSize).putInt(i + 1).putInt(-1)
                    .putInt(-1).putInt(0).putShort((short) 20).putShort((short) 20)
                    .putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        }
        for (int i = 0; i < depth; ++i) {
            // RES_XML_END_ELEMENT_TYPE
            b.putShort((short) 0x0103).putShort((short) 16).putInt(endSize).putInt(depth + i + 1).putInt(-1)
                    .putInt(-1).putInt(0);
        }
        return b.array();
    }

    /**
     * {@code <e>} start and end elements in runs: a positive run is that many starts, a negative
     * run that many ends, whether or not anything is open.
     */
    @NonNull
    private static byte[] elements(int... runs) {
        int poolSize = 28 + 4 + 4;
        int startSize = 36;
        int endSize = 24;
        int total = 8 + poolSize;
        for (int run : runs) {
            total += run > 0 ? run * startSize : -run * endSize;
        }
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0x0003).putShort((short) 8).putInt(total);
        b.putShort((short) 0x0001).putShort((short) 28).putInt(poolSize)
                .putInt(1).putInt(0).putInt(0x100).putInt(28 + 4).putInt(0);
        b.putInt(0);
        b.put(new byte[]{1, 1, 'e', 0});
        int line = 0;
        for (int run : runs) {
            for (int i = 0; i < Math.abs(run); ++i) {
                if (run > 0) {
                    b.putShort((short) 0x0102).putShort((short) 16).putInt(startSize).putInt(++line).putInt(-1)
                            .putInt(-1).putInt(0).putShort((short) 20).putShort((short) 20)
                            .putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 0);
                } else {
                    b.putShort((short) 0x0103).putShort((short) 16).putInt(endSize).putInt(++line).putInt(-1)
                            .putInt(-1).putInt(0);
                }
            }
        }
        return b.array();
    }

    private static void expectBounded(@NonNull String what, @NonNull byte[] data) {
        long start = System.nanoTime();
        try {
            AndroidBinXmlDecoder.decode(data);
        } catch (IOException expected) {
            // Refused, which is fine
        } catch (Throwable th) {
            throw new AssertionError(what + " ended in " + th, th);
        }
        long took = System.nanoTime() - start;
        if (took > SLOW_NANOS) {
            fail(what + " took " + took / 1_000_000 + " ms");
        }
    }

    @NonNull
    private static byte[] resource(@NonNull String name) throws IOException {
        ClassLoader classLoader = Objects.requireNonNull(BinaryXmlMalformedInputTest.class.getClassLoader());
        try (InputStream in = classLoader.getResourceAsStream(name)) {
            assertNotNull(name, in);
            return IoUtils.readFully(in, -1, true);
        }
    }
}
