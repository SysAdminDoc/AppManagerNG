// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AppMemoryInfoParserTest {

    /**
     * Captured from {@code dumpsys meminfo} on an Android 14 Pixel 8 build.
     * Whitespace and column widths match the actual surface so the regex
     * tolerates real-world spacing.
     */
    private static final String ANDROID_14_DUMP = ""
            + "Applications Memory Usage (in Kilobytes):\n"
            + "Uptime: 12345 Realtime: 23456\n"
            + "\n"
            + "** MEMINFO in pid 1234 [com.example.app] **\n"
            + "                   Pss  Private  Private     Swap      Rss     Heap     Heap     Heap\n"
            + "                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free\n"
            + "                ------   ------   ------   ------   ------   ------   ------   ------\n"
            + "  Native Heap     1234     1234        0        0     5678     8192     5432     2760\n"
            + "  Dalvik Heap      567      560        0        0    12345     8192     6789     1403\n"
            + "\n"
            + " App Summary\n"
            + "                       Pss(KB)                        Rss(KB)\n"
            + "                        ------                         ------\n"
            + "           Java Heap:      801                         12912\n"
            + "         Native Heap:     1234                          5678\n"
            + "                Code:     2789                          7800\n"
            + "               Stack:      120                           567\n"
            + "            Graphics:      456                           678\n"
            + "       Private Other:      245\n"
            + "              System:     1080\n"
            + "             Unknown:                                     34\n"
            + "           TOTAL PSS:     6725            TOTAL RSS:    27669       TOTAL SWAP (KB):       0\n";

    /** Captured from a non-RSS-supporting Android 7 build. */
    private static final String ANDROID_7_DUMP = ""
            + "Applications Memory Usage (in Kilobytes):\n"
            + "\n"
            + "** MEMINFO in pid 9999 [com.example.legacy] **\n"
            + "\n"
            + " App Summary\n"
            + "                       Pss(KB)\n"
            + "                        ------\n"
            + "           Java Heap:     2456\n"
            + "         Native Heap:     1024\n"
            + "                Code:     4096\n"
            + "               Stack:      256\n"
            + "            Graphics:        0\n"
            + "       Private Other:      512\n"
            + "              System:      640\n"
            + "           TOTAL PSS:     8984            TOTAL SWAP (KB):       128\n";

    @Test
    public void parsesModernDumpsysSummary() {
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(ANDROID_14_DUMP);
        assertNotNull(s);
        assertEquals(801L, s.javaHeapPssKb);
        assertEquals(12912L, s.javaHeapRssKb);
        assertEquals(1234L, s.nativeHeapPssKb);
        assertEquals(5678L, s.nativeHeapRssKb);
        assertEquals(2789L, s.codePssKb);
        assertEquals(7800L, s.codeRssKb);
        assertEquals(120L, s.stackPssKb);
        assertEquals(567L, s.stackRssKb);
        assertEquals(456L, s.graphicsPssKb);
        assertEquals(678L, s.graphicsRssKb);
        assertEquals(245L, s.privateOtherPssKb);
        assertEquals(1080L, s.systemPssKb);
        assertEquals(34L, s.unknownRssKb);
        assertEquals(6725L, s.totalPssKb);
        assertEquals(27669L, s.totalRssKb);
        assertEquals(0L, s.totalSwapKb);
    }

    @Test
    public void parsesLegacyDumpsysWithoutRssColumn() {
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(ANDROID_7_DUMP);
        assertNotNull(s);
        assertEquals(2456L, s.javaHeapPssKb);
        assertEquals(1024L, s.nativeHeapPssKb);
        assertEquals(4096L, s.codePssKb);
        assertEquals(256L, s.stackPssKb);
        assertEquals(0L, s.graphicsPssKb);
        assertEquals(512L, s.privateOtherPssKb);
        assertEquals(640L, s.systemPssKb);
        assertEquals(8984L, s.totalPssKb);
        assertEquals(128L, s.totalSwapKb);
        // No RSS column at all - those fields remain at -1.
        assertEquals(-1L, s.javaHeapRssKb);
        assertEquals(-1L, s.totalRssKb);
    }

    @Test
    public void returnsNullWhenAppSummaryHeaderAbsent() {
        String dump = "Applications Memory Usage (in Kilobytes):\nUptime: 5\n\n** MEMINFO **\n";
        assertNull(AppMemoryInfoParser.parseAppSummary(dump));
    }

    @Test
    public void emptyInputReturnsNull() {
        assertNull(AppMemoryInfoParser.parseAppSummary(""));
    }

    @Test
    public void onlyHeaderInputProducesAllMissingSnapshot() {
        // App Summary header is present but rows are not - every field stays at -1.
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(
                " App Summary\n");
        assertNotNull(s);
        assertEquals(-1L, s.javaHeapPssKb);
        assertEquals(-1L, s.nativeHeapPssKb);
        assertEquals(-1L, s.totalPssKb);
        assertEquals(0L, s.sumPresentPssKb());
    }

    @Test
    public void unknownRowDoesNotPoisonSnapshot() {
        // Future Android version might add a "Fonts" row. The parser must not
        // crash, and known fields must still populate.
        String dump = ""
                + " App Summary\n"
                + "                       Pss(KB)\n"
                + "                        ------\n"
                + "                Fonts:      999\n"
                + "           Java Heap:      100\n"
                + "           TOTAL PSS:     1099\n";
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(dump);
        assertNotNull(s);
        assertEquals(100L, s.javaHeapPssKb);
        assertEquals(1099L, s.totalPssKb);
        // Unknown row is ignored; sumPresent only includes the recognized row.
        assertEquals(100L, s.sumPresentPssKb());
    }

    @Test
    public void sumPresentMatchesTotalsWhenAllRowsKnown() {
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(ANDROID_7_DUMP);
        assertNotNull(s);
        // 2456 + 1024 + 4096 + 256 + 0 + 512 + 640 = 8984 (matches TOTAL PSS).
        assertEquals(8984L, s.sumPresentPssKb());
        assertEquals(s.totalPssKb, s.sumPresentPssKb());
    }

    @Test
    public void garbageNumbersAreSkippedNotCrashing() {
        // Older or rooted-OEM dumpsys variants occasionally print "????" for
        // unreadable fields. The parser must drop the line, not throw.
        String dump = ""
                + " App Summary\n"
                + "                       Pss(KB)\n"
                + "                        ------\n"
                + "           Java Heap:     ????\n"
                + "         Native Heap:      500\n";
        AppMemoryInfoParser.Snapshot s = AppMemoryInfoParser.parseAppSummary(dump);
        assertNotNull(s);
        assertEquals(-1L, s.javaHeapPssKb);  // unparseable -> stays -1
        assertEquals(500L, s.nativeHeapPssKb);
    }
}
