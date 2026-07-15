// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public void newLogLineParsesModernLineWithoutUid() {
        LogLine logLine = LogLine.newLogLine(
                "05-26 13:45:12.123 1234 5678 I ActivityManager: process started",
                false,
                null);

        assertNotNull(logLine);
        assertEquals("05-26 13:45:12.123", logLine.getTimestamp());
        assertNull(logLine.getUidOwner());
        assertEquals(-1, logLine.getUid());
        assertEquals(1234, logLine.getPid());
        assertEquals(5678, logLine.getTid());
        assertEquals("ActivityManager", logLine.getTagName());
        assertEquals("process started", logLine.getLogOutput());
    }

    @Test
    public void logPatternMatchesModernLineWithUid() throws Exception {
        Field patternField = LogLine.class.getDeclaredField("LOG_PATTERN");
        patternField.setAccessible(true);
        Pattern pattern = (Pattern) patternField.get(null);
        Matcher matcher = pattern.matcher(
                "05-26 13:45:12.123 u0_a123 1234 5678 W PackageManager: package changed");

        assertTrue(matcher.matches());
        assertEquals("u0_a123", matcher.group(2));
        assertEquals("1234", matcher.group(3));
        assertEquals("5678", matcher.group(4));
        assertEquals("W", matcher.group(5));
        assertEquals("PackageManager", matcher.group(6));
        assertEquals("package changed", matcher.group(7));
    }

    @Test(timeout = 2000)
    public void newLogLineRejectsLongMalformedNumericPrefixWithoutBacktrackingSpike() {
        String line = "01-01 00:00:00.000 " + "1".repeat(50_000) + " X native crash dump";

        LogLine logLine = LogLine.newLogLine(line, false, null);

        assertNotNull(logLine);
        assertEquals(-1, logLine.getLogLevel());
        assertEquals(line, logLine.getLogOutput());
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
