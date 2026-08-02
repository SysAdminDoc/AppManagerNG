// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

/**
 * The privileged filesystem service runs as root and receives {@code len} / {@code offset}
 * straight from the peer. Values it cannot honour must be refused at the boundary rather than
 * reaching a raw fd splice or being applied as a limit to a fixed-capacity direct buffer.
 */
public class FileSystemServiceBoundsTest {
    private static final int PIPE_CAPACITY = FileSystemService.PIPE_CAPACITY;

    @Test
    public void negativeLengthIsRejected() {
        assertRejected(-1, 0, PIPE_CAPACITY);
        assertRejected(Integer.MIN_VALUE, 0, PIPE_CAPACITY);
        assertRejected(-1, -1, Integer.MAX_VALUE);
    }

    @Test
    public void oversizedWriteLengthIsRejected() {
        // The pre-API-28 write path applies len as a limit on a PIPE_CAPACITY direct buffer.
        assertRejected(PIPE_CAPACITY + 1, 0, PIPE_CAPACITY);
        assertRejected(Integer.MAX_VALUE, 0, PIPE_CAPACITY);
    }

    @Test
    public void negativeOffsetBelowTheSentinelIsRejected() {
        // -1 means "use the current file position"; anything below it is malformed.
        assertRejected(16, -2, PIPE_CAPACITY);
        assertRejected(16, Long.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void overflowingOffsetPlusLengthIsRejected() {
        assertRejected(4096, Long.MAX_VALUE, Integer.MAX_VALUE);
        assertRejected(2, Long.MAX_VALUE - 1, PIPE_CAPACITY);
    }

    @Test
    public void legitimateValuesAreAccepted() throws IOException {
        // The sentinel offset used by the streaming paths.
        FileSystemService.checkLenOffset(PIPE_CAPACITY, -1, PIPE_CAPACITY);
        // A positioned read of one pipe-full.
        FileSystemService.checkLenOffset(PIPE_CAPACITY, 0, PIPE_CAPACITY);
        FileSystemService.checkLenOffset(0, 0, PIPE_CAPACITY);
        // A read may legitimately ask for more than one pipe-full; the read path clamps it.
        FileSystemService.checkLenOffset(1 << 24, 1L << 40, Integer.MAX_VALUE);
    }

    private static void assertRejected(int len, long offset, int maxLen) {
        try {
            FileSystemService.checkLenOffset(len, offset, maxLen);
            fail("Expected IOException for len=" + len + " offset=" + offset + " max=" + maxLen);
        } catch (IOException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().isEmpty());
        }
    }
}
