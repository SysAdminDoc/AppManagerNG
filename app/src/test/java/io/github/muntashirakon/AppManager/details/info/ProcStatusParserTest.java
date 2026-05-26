// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcStatusParserTest {

    private static final String MODERN_DUMP =
            "Name:\tcom.android.chrome\n" +
            "Umask:\t0077\n" +
            "State:\tS (sleeping)\n" +
            "Tgid:\t12345\n" +
            "Pid:\t12345\n" +
            "PPid:\t567\n" +
            "VmPeak:\t  987654 kB\n" +
            "VmSize:\t  900000 kB\n" +
            "VmHWM:\t  300000 kB\n" +
            "VmRSS:\t  250000 kB\n" +
            "RssAnon:\t 200000 kB\n" +
            "RssFile:\t  45000 kB\n" +
            "RssShmem:\t  5000 kB\n" +
            "VmData:\t  600000 kB\n" +
            "VmStk:\t     132 kB\n" +
            "VmExe:\t       4 kB\n" +
            "VmLib:\t  150000 kB\n" +
            "VmPTE:\t     500 kB\n" +
            "VmSwap:\t    1024 kB\n" +
            "Threads:\t   42\n";

    @Test
    public void parsesModernStatusDump() {
        ProcStatusParser.Snapshot snap = ProcStatusParser.parse(MODERN_DUMP);
        assertNotNull(snap);
        assertEquals("com.android.chrome", snap.name);
        assertEquals(12345, snap.pid);
        assertEquals(12345, snap.tgid);
        assertEquals(567, snap.ppid);
        assertEquals(42, snap.threads);
        assertEquals(987_654L, snap.vmPeakKb);
        assertEquals(900_000L, snap.vmSizeKb);
        assertEquals(300_000L, snap.vmHwmKb);
        assertEquals(250_000L, snap.vmRssKb);
        assertEquals(200_000L, snap.rssAnonKb);
        assertEquals(45_000L, snap.rssFileKb);
        assertEquals(5_000L, snap.rssShmemKb);
        assertEquals(600_000L, snap.vmDataKb);
        assertEquals(132L, snap.vmStkKb);
        assertEquals(4L, snap.vmExeKb);
        assertEquals(150_000L, snap.vmLibKb);
        assertEquals(500L, snap.vmPteKb);
        assertEquals(1_024L, snap.vmSwapKb);
        assertTrue(snap.hasAnyMemoryField());
    }

    @Test
    public void unknownRowsAreIgnoredButRestSurvives() {
        // Future kernel versions add new rows; the parser must keep going.
        String mixed = "Name:\tsystem_server\n" +
                "FutureRow:\tsomething new\n" +
                "VmRSS:\t12345 kB\n" +
                "MoreFuture:\twith spaces 42\n";
        ProcStatusParser.Snapshot snap = ProcStatusParser.parse(mixed);
        assertNotNull(snap);
        assertEquals("system_server", snap.name);
        assertEquals(12_345L, snap.vmRssKb);
        assertEquals(ProcStatusParser.UNKNOWN, snap.vmSizeKb);
        assertEquals(ProcStatusParser.UNKNOWN, snap.rssAnonKb);
    }

    @Test
    public void garbledKbValueLeavesFieldAtUnknown() {
        String garbled = "Name:\tx\nVmRSS:\tNaN kB\nVmSize:\t987 kB\n";
        ProcStatusParser.Snapshot snap = ProcStatusParser.parse(garbled);
        assertNotNull(snap);
        assertEquals(ProcStatusParser.UNKNOWN, snap.vmRssKb);
        assertEquals(987L, snap.vmSizeKb);
    }

    @Test
    public void valueWithoutKbSuffixIsStillParsed() {
        // Older kernel builds omit the unit suffix on a few rows.
        String dump = "VmRSS:\t1234\nThreads:\t8\n";
        ProcStatusParser.Snapshot snap = ProcStatusParser.parse(dump);
        assertNotNull(snap);
        assertEquals(1_234L, snap.vmRssKb);
        assertEquals(8, snap.threads);
    }

    @Test
    public void inputWithNoRecognisableRowsReturnsNull() {
        // A junk capture (e.g. an HTML error page) must not pass for a
        // status dump - return null so the UI can show "could not read".
        assertNull(ProcStatusParser.parse("<html><body>error</body></html>"));
    }

    @Test
    public void blankInputReturnsNull() {
        assertNull(ProcStatusParser.parse(""));
        assertNull(ProcStatusParser.parse("\n\n\n"));
    }

    @Test
    public void hasAnyMemoryFieldReportsFalseWhenOnlyHeaderRowsPresent() {
        // A status block with Name/Pid/Threads but no Vm* rows is a thin
        // surface; UI should treat it as "no memory data".
        String dump = "Name:\tx\nPid:\t10\nThreads:\t1\n";
        ProcStatusParser.Snapshot snap = ProcStatusParser.parse(dump);
        assertNotNull(snap);
        assertFalse(snap.hasAnyMemoryField());
        assertEquals(ProcStatusParser.UNKNOWN, snap.vmRssKb);
        assertEquals("x", snap.name);
        assertEquals(10, snap.pid);
        assertEquals(1, snap.threads);
    }

    @Test
    public void crlfLineEndingsParseIdenticallyToLf() {
        ProcStatusParser.Snapshot crlf = ProcStatusParser.parse(MODERN_DUMP.replace("\n", "\r\n"));
        assertNotNull(crlf);
        assertEquals(250_000L, crlf.vmRssKb);
        assertEquals(42, crlf.threads);
    }
}
