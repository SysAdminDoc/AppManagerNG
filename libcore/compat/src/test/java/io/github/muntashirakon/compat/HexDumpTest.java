// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HexDumpTest {
    @Test
    public void roundTrip() {
        byte[] bytes = new byte[]{0x00, 0x7f, (byte) 0x80, (byte) 0xff, 0x12};
        assertEquals("007F80FF12", HexDump.toHexString(bytes));
        assertArrayEquals(bytes, HexDump.hexStringToByteArray("007F80FF12"));
        assertArrayEquals(bytes, HexDump.hexStringToByteArray("007f80ff12"));
    }

    @Test
    public void oddLengthIsRejected() {
        // Used to read one character past the end and throw ArrayIndexOutOfBoundsException /
        // StringIndexOutOfBoundsException from deep inside the loop.
        assertRejected("00F");
        assertRejected("A");
    }

    @Test
    public void nonHexIsRejected() {
        assertRejected("zz");
        assertRejected("00 11");
        assertRejected("00-1");
    }

    @Test
    public void nullIsRejected() {
        assertRejected(null);
    }

    @Test
    public void emptyDecodesToEmpty() {
        assertArrayEquals(new byte[0], HexDump.hexStringToByteArray(""));
    }

    private static void assertRejected(String input) {
        try {
            HexDump.hexStringToByteArray(input);
            fail("Expected IllegalArgumentException for " + input);
        } catch (IllegalArgumentException expected) {
            // Documented contract; callers translate this into their own checked failure.
        }
    }
}
