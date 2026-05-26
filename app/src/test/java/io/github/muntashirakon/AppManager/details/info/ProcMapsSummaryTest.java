// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProcMapsSummaryTest {

    @Test
    public void parsesRepresentativeAndroidMaps() {
        // Hand-crafted minimal /proc/maps subset; sizes chosen for easy math.
        // Each address range is exactly the indicated number of bytes.
        String maps = String.join("\n",
                // 0x1000 = 4096 bytes (4 KB) - dalvik main space
                "12c00000-12c01000 rw-p 00000000 00:00 0      [anon:dalvik-main space]",
                // 0x2000 = 8192 bytes (8 KB) - native heap (libc_malloc)
                "13000000-13002000 rw-p 00000000 00:00 0      [anon:libc_malloc]",
                // 0x10000 = 65536 bytes (64 KB) - stack
                "7fff80000-7fff90000 rw-p 00000000 00:00 0      [stack]",
                // 0x4000 = 16384 bytes (16 KB) - executable .so
                "720dab2000-720dab6000 r-xp 00002000 fc:00 12345 /system/lib64/libc.so",
                // 0x1000 = 4096 bytes (4 KB) - library data (.so but not exec)
                "720daca000-720dacb000 r--p 00000000 fc:00 12345 /system/lib64/libc.so",
                // 0x1000 = 4096 bytes (4 KB) - other file (APK)
                "73f000000-73f001000 r--p 00000000 fc:00 22222 /system/app/Chrome/base.apk",
                // 0x2000 = 8192 bytes (8 KB) - other anon (e.g. graphics)
                "780000000-780002000 rw-s 00000000 00:13 99999 anon_inode:dmabuf",
                ""
        );
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(7, s.regions);
        assertEquals(0, s.unparsedRegions);
        assertEquals(4_096L, s.dalvikHeapBytes);
        assertEquals(8_192L, s.nativeHeapBytes);
        assertEquals(65_536L, s.stackBytes);
        assertEquals(16_384L, s.codeBytes);
        assertEquals(4_096L, s.libraryBytes);
        // Both anon_inode and the APK count as "other"; APK is otherFile,
        // anon_inode resolves to a non-bracket pathname so it lands in otherFile too.
        assertEquals(4_096L + 8_192L, s.otherFileBytes);
        long expectedTotal = 4_096L + 8_192L + 65_536L + 16_384L + 4_096L + 4_096L + 8_192L;
        assertEquals(expectedTotal, s.totalBytes);
    }

    @Test
    public void groupsAllDalvikVariantsTogether() {
        // Several anon:dalvik-* variants are observed in the wild; all are
        // counted under dalvikHeapBytes.
        String maps = String.join("\n",
                "10000000-10001000 rw-p 00000000 00:00 0      [anon:dalvik-main space (region space)]",
                "10001000-10002000 rw-p 00000000 00:00 0      [anon:dalvik-large object space]",
                "10002000-10003000 rw-p 00000000 00:00 0      [anon:dalvik-non moving space]",
                "");
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(3 * 4_096L, s.dalvikHeapBytes);
        assertEquals(0L, s.nativeHeapBytes);
    }

    @Test
    public void stackVariantsAreCountedAsStack() {
        // Both [stack] (the main thread) and [stack:NNN] (worker threads).
        String maps = String.join("\n",
                "7fff00000-7fff01000 rw-p 00000000 00:00 0      [stack]",
                "7fef00000-7fef02000 rw-p 00000000 00:00 0      [stack:12345]",
                "");
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(4_096L + 8_192L, s.stackBytes);
    }

    @Test
    public void heapBracketCountsAsNativeHeap() {
        String maps = "70000000-70010000 rw-p 00000000 00:00 0      [heap]\n";
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(65_536L, s.nativeHeapBytes);
        assertEquals(0L, s.otherAnonBytes);
    }

    @Test
    public void anonymousRegionsWithoutCategoryLandInOtherAnon() {
        String maps = "70000000-70001000 rw-p 00000000 00:00 0      [anon:something-unknown]\n";
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(4_096L, s.otherAnonBytes);
        assertEquals(0L, s.dalvikHeapBytes);
        assertEquals(0L, s.nativeHeapBytes);
    }

    @Test
    public void scudoMatchesNativeHeap() {
        // Scudo (Android 11+) replaces jemalloc; allocator-tagged regions
        // start with [anon:scudo:
        String maps = "70000000-70004000 rw-p 00000000 00:00 0      [anon:scudo:primary]\n";
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(16_384L, s.nativeHeapBytes);
    }

    @Test
    public void malformedLineIsCountedAsUnparsed() {
        String maps = String.join("\n",
                "this-is-not-a-maps-line",
                "12c00000-12c01000 rw-p 00000000 00:00 0      [anon:dalvik-main space]",
                "");
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(1, s.regions);
        assertEquals(1, s.unparsedRegions);
        assertEquals(4_096L, s.totalBytes);
    }

    @Test
    public void inputWithNoParseableRegionsReturnsNull() {
        // Junk capture; UI must be able to detect this rather than render
        // an all-zero summary.
        assertNull(ProcMapsSummary.parse("<html><body>access denied</body></html>"));
        assertNull(ProcMapsSummary.parse(""));
    }

    @Test
    public void crlfLineEndingsParseIdenticallyToLf() {
        String maps =
                "12c00000-12c01000 rw-p 00000000 00:00 0      [anon:dalvik-main space]\r\n" +
                "70000000-70001000 rw-p 00000000 00:00 0      [heap]\r\n";
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(2, s.regions);
        assertEquals(4_096L, s.dalvikHeapBytes);
        assertEquals(4_096L, s.nativeHeapBytes);
    }

    @Test
    public void anonInodeBackingFileResolvesToOtherFile() {
        // Graphics buffers commonly map as /memfd: or anon_inode:dmabuf;
        // they should be classified as otherFile, not otherAnon, because
        // the line is file-backed (col 6 is non-bracketed).
        String maps = "70000000-70001000 rw-s 00000000 00:13 12345 /memfd:gralloc\n";
        ProcMapsSummary.Summary s = ProcMapsSummary.parse(maps);
        assertNotNull(s);
        assertEquals(4_096L, s.otherFileBytes);
        assertEquals(0L, s.codeBytes);
    }
}
