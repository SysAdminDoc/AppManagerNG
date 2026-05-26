// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.hex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class HexViewerUtilsTest {
    @Test
    public void buildLinesFormatsOffsetHexAndAscii() {
        byte[] page = new byte[]{0x41, 0x42, 0x00, 0x7F, (byte) 0xFF};

        List<HexViewerUtils.HexLine> lines = HexViewerUtils.buildLines(page, 0x10);

        assertEquals(1, lines.size());
        assertEquals(0x10, lines.get(0).offset);
        assertTrue(lines.get(0).hex.startsWith("41 42 00 7F FF"));
        assertEquals(47, lines.get(0).hex.length());
        assertEquals("AB...", lines.get(0).ascii);
    }

    @Test
    public void parseOffsetAcceptsDecimalAndHexAndClampsToFile() {
        assertEquals(32, HexViewerUtils.parseOffset("32", 100));
        assertEquals(32, HexViewerUtils.parseOffset("0x20", 100));
        assertEquals(99, HexViewerUtils.parseOffset("0x1000", 100));
        assertEquals(0, HexViewerUtils.parseOffset("0", 0));
    }

    @Test
    public void parseOffsetRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> HexViewerUtils.parseOffset("", 100));
        assertThrows(IllegalArgumentException.class, () -> HexViewerUtils.parseOffset("0x", 100));
        assertThrows(IllegalArgumentException.class, () -> HexViewerUtils.parseOffset("not-a-number", 100));
    }

    @Test
    public void parseHexPatternAcceptsCommonSeparators() {
        assertArrayEquals(new byte[]{0x7F, 0x45, 0x4C, 0x46},
                HexViewerUtils.parseHexPattern("0x7f 45:4c-46"));
    }

    @Test
    public void parseHexPatternRejectsIncompleteOrNonHexBytes() {
        assertThrows(IllegalArgumentException.class, () -> HexViewerUtils.parseHexPattern("A"));
        assertThrows(IllegalArgumentException.class, () -> HexViewerUtils.parseHexPattern("GG"));
    }

    @Test
    public void indexOfFindsNeedleAfterStart() {
        byte[] haystack = new byte[]{0, 1, 2, 3, 1, 2, 4};
        byte[] needle = new byte[]{1, 2};

        assertEquals(1, HexViewerUtils.indexOf(haystack, needle, 0));
        assertEquals(4, HexViewerUtils.indexOf(haystack, needle, 2));
        assertEquals(-1, HexViewerUtils.indexOf(haystack, new byte[]{2, 5}, 0));
    }

    @Test
    public void alignToPageReturnsContainingPageStart() {
        assertEquals(0, HexViewerUtils.alignToPage(0));
        assertEquals(0, HexViewerUtils.alignToPage(4095));
        assertEquals(4096, HexViewerUtils.alignToPage(4096));
        assertEquals(8192, HexViewerUtils.alignToPage(9000));
    }
}
