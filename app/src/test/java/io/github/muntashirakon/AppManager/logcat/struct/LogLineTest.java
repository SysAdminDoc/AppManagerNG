// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogLineTest {
    @Test
    public void newLogLineFallsBackForOverflowModernPid() {
        String line = "01-01 00:00:00.000 999999999999 123 I ExampleTag: payload";

        LogLine logLine = LogLine.newLogLine(line, false, null);

        assertNotNull(logLine);
        assertEquals(-1, logLine.getLogLevel());
        assertEquals(line, logLine.getLogOutput());
        assertEquals(-1, logLine.getPid());
    }

    @Test
    public void newLogLineFallsBackForOverflowLegacyPid() {
        String line = "01-01 00:00:00.000 I/ExampleTag(999999999999): payload";

        LogLine logLine = LogLine.newLogLine(line, false, null);

        assertNotNull(logLine);
        assertEquals(-1, logLine.getLogLevel());
        assertEquals(line, logLine.getLogOutput());
        assertEquals(-1, logLine.getPid());
    }

    @Test
    public void selectPackageNameForUidReturnsNullForNoPackages() {
        assertNull(LogLine.selectPackageNameForUid(null));
        assertNull(LogLine.selectPackageNameForUid(new String[0]));
        assertNull(LogLine.selectPackageNameForUid(new String[]{"", null}));
    }

    @Test
    public void selectPackageNameForUidPrefersShortestPackageName() {
        assertEquals("com.primary", LogLine.selectPackageNameForUid(new String[]{
                "com.example.longer",
                "com.primary",
                "com.example.longest"
        }));
    }

    @Test
    public void selectPackageNameForUidBreaksLengthTiesByName() {
        assertEquals("com.alpha", LogLine.selectPackageNameForUid(new String[]{
                "com.gamma",
                "com.alpha",
                "com.delta"
        }));
    }
}
