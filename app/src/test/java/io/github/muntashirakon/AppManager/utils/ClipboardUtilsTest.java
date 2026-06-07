// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ClipboardUtilsTest {
    @Test
    public void getUtf8BytesUsesStableEncoding() {
        assertArrayEquals("é😀".getBytes(StandardCharsets.UTF_8), ClipboardUtils.getUtf8Bytes("é😀"));
    }

    @Test
    public void truncateUtf8PreservesCompleteCodePoints() {
        assertEquals("abc", ClipboardUtils.truncateUtf8("abc😀def", 6));
        assertEquals("abc😀", ClipboardUtils.truncateUtf8("abc😀def", 7));
        assertEquals("éé", ClipboardUtils.truncateUtf8("ééé", 5));
    }

    @Test
    public void truncateUtf8KeepsTextUnderByteLimit() {
        String truncated = ClipboardUtils.truncateUtf8("😀😀😀", 9);

        assertEquals("😀😀", truncated);
        assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length <= 9);
        assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));
    }

    @Test
    public void truncateUtf8ReturnsEmptyForNonPositiveLimit() {
        assertEquals("", ClipboardUtils.truncateUtf8("text", 0));
        assertEquals("", ClipboardUtils.truncateUtf8("text", -1));
    }
}
