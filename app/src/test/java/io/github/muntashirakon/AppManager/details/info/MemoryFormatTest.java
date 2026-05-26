// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MemoryFormatTest {

    @Test
    public void formatKbRendersUnitLadder() {
        assertEquals("0 KB", MemoryFormat.formatKb(0L));
        assertEquals("512 KB", MemoryFormat.formatKb(512L));
        assertEquals("1.0 MB", MemoryFormat.formatKb(1024L));
        assertEquals("4.0 MB", MemoryFormat.formatKb(4096L));
        assertEquals("1.0 GB", MemoryFormat.formatKb(1024L * 1024L));
        assertEquals("2.0 GB", MemoryFormat.formatKb(2L * 1024L * 1024L));
        assertEquals("1.0 TB", MemoryFormat.formatKb(1024L * 1024L * 1024L));
    }

    @Test
    public void formatKbReportsNotAvailableForNegative() {
        assertEquals("n/a", MemoryFormat.formatKb(-1L));
        assertEquals("n/a", MemoryFormat.formatKb(Long.MIN_VALUE));
    }

    @Test
    public void formatBytesRendersUnitLadder() {
        assertEquals("0 B", MemoryFormat.formatBytes(0L));
        assertEquals("512 B", MemoryFormat.formatBytes(512L));
        assertEquals("1.0 KB", MemoryFormat.formatBytes(1024L));
        assertEquals("1.0 MB", MemoryFormat.formatBytes(1024L * 1024L));
        assertEquals("1.5 MB",
                MemoryFormat.formatBytes(1024L * 1024L + 512L * 1024L));
        assertEquals("1.0 GB", MemoryFormat.formatBytes(1024L * 1024L * 1024L));
    }

    @Test
    public void formatBytesReportsNotAvailableForNegative() {
        assertEquals("n/a", MemoryFormat.formatBytes(-1L));
    }

    @Test
    public void formatPercentRendersOneDecimal() {
        assertEquals("0.0%", MemoryFormat.formatPercent(0.0));
        assertEquals("1.5%", MemoryFormat.formatPercent(1.5));
        assertEquals("99.9%", MemoryFormat.formatPercent(99.9));
        assertEquals("100.0%", MemoryFormat.formatPercent(100.0));
    }

    @Test
    public void formatPercentReportsNotAvailableForNegative() {
        assertEquals("n/a", MemoryFormat.formatPercent(-0.1));
        assertEquals("n/a", MemoryFormat.formatPercent(-100.0));
    }

    @Test
    public void formatLatencyMsRendersMilliseconds() {
        assertEquals("0 ms", MemoryFormat.formatLatencyMs(0L));
        assertEquals("12 ms", MemoryFormat.formatLatencyMs(12L));
        assertEquals("99 ms", MemoryFormat.formatLatencyMs(99L));
    }

    @Test
    public void formatLatencyMsReportsNotAvailableForNegative() {
        assertEquals("n/a", MemoryFormat.formatLatencyMs(-1L));
    }

    @Test
    public void formatThreadCountRendersInteger() {
        assertEquals("0", MemoryFormat.formatThreadCount(0));
        assertEquals("42", MemoryFormat.formatThreadCount(42));
        assertEquals("n/a", MemoryFormat.formatThreadCount(-1));
    }

    @Test
    public void formatSwapKbCollapseZeroToNone() {
        assertEquals("none", MemoryFormat.formatSwapKb(0L));
        assertEquals("1.0 MB", MemoryFormat.formatSwapKb(1024L));
        assertEquals("n/a", MemoryFormat.formatSwapKb(-1L));
    }

    @Test
    public void notAvailableConstantIsStable() {
        // Pin the literal so UI string lookup can match against it without
        // re-deriving the value.
        assertEquals("n/a", MemoryFormat.NOT_AVAILABLE);
    }
}
