// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** About the device reads swap from /proc/meminfo (#54), so one odd line must not lose the rest. */
public class ProcMemoryInfoTest {
    private static final String MEMINFO = "MemTotal:        7810668 kB\n"
            + "MemFree:          213456 kB\n"
            + "MemAvailable:    3125000 kB\n"
            + "Buffers:            4100 kB\n"
            + "Cached:          2900000 kB\n"
            + "SwapCached:        52000 kB\n"
            + "SwapTotal:       4194300 kB\n"
            + "SwapFree:        1048572 kB\n"
            + "HugePages_Total:       0\n"
            + "Hugepagesize:       2048 kB\n";

    @Test
    public void readsMemoryAndSwapInBytes() {
        ProcMemoryInfo info = ProcMemoryInfo.parse(MEMINFO);

        assertEquals(7810668L * 1024, info.getTotalMemory());
        assertEquals(3125000L * 1024, info.getAvailableMemory());
        assertEquals(4194300L * 1024, info.getTotalSwap());
        assertEquals(1048572L * 1024, info.getFreeSwap());
        assertEquals((4194300L - 1048572L) * 1024, info.getUsedSwap());
        // A count without a unit isn't a size.
        assertEquals(-1, info.get("HugePages_Total", -1));
    }

    @Test
    public void aLineThatIsNotANumberIsSkipped() {
        ProcMemoryInfo info = ProcMemoryInfo.parse("Weird:   n/a kB\n" + MEMINFO + "Broken line kB\nZswap:  12x kB");

        assertEquals(-1, info.get("Weird", -1));
        assertEquals(-1, info.get("Zswap", -1));
        assertEquals(4194300L * 1024, info.getTotalSwap());
    }

    @Test
    public void windowsLineEndingsAndNoSwap() {
        ProcMemoryInfo info = ProcMemoryInfo.parse("MemTotal: 2048 kB\r\nSwapTotal: 0 kB\r\nSwapFree: 0 kB\r\n");

        assertEquals(2048L * 1024, info.getTotalMemory());
        assertEquals(0, info.getTotalSwap());
        assertEquals(0, info.getUsedSwap());
    }

    @Test
    public void numbersAreDecimalEvenWithALeadingZero() {
        // Long.decode would read 010 as octal 8.
        ProcMemoryInfo info = ProcMemoryInfo.parse("SwapFree: 010 kB\n");

        assertEquals(10L * 1024, info.getFreeSwap());
    }
}
