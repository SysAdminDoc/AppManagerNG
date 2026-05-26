// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatStructuredExporter;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;

public class LogcatStructuredExporterTest {
    @Test
    public void toJsonIncludesParsedFields() throws Exception {
        LogLine logLine = createLogLine("message");

        String exported = LogcatStructuredExporter.toJson(Collections.singletonList(logLine));
        JSONObject root = new JSONObject(exported);
        JSONObject entry = root.getJSONArray("entries").getJSONObject(0);

        assertEquals(1, root.getInt("entry_count"));
        assertEquals("05-26 13:45:12.123", entry.getString("timestamp"));
        assertEquals("u0_a123", entry.getString("uid_owner"));
        assertEquals(10123, entry.getInt("uid"));
        assertEquals(1234, entry.getInt("pid"));
        assertEquals(5678, entry.getInt("tid"));
        assertEquals("I", entry.getString("level"));
        assertEquals("ActivityManager", entry.getString("tag"));
        assertEquals("com.example", entry.getString("package"));
        assertEquals("message", entry.getString("message"));
        assertFalse(exported.contains("mOriginalLine"));
    }

    @Test
    public void toCsvEscapesQuotesAndDefusesFormulaValues() {
        LogLine logLine = createLogLine("=HYPERLINK(\"http://evil/\",\"click\")");

        String exported = LogcatStructuredExporter.toCsv(Collections.singletonList(logLine));

        assertTrue(exported.startsWith("\"index\",\"timestamp\",\"uid_owner\""));
        assertTrue(exported.contains("\"ActivityManager\""));
        assertTrue(exported.contains("\"'=HYPERLINK(\"\"http://evil/\"\",\"\"click\"\")\""));
        assertFalse(exported.contains("\"=HYPERLINK"));
    }

    @Test
    public void createExportFilenameUsesStructuredExtension() {
        assertTrue(LogcatStructuredExporter.createExportFilename(LogcatStructuredExporter.Format.JSON)
                .endsWith(".logcat.json"));
        assertTrue(LogcatStructuredExporter.createExportFilename(LogcatStructuredExporter.Format.CSV)
                .endsWith(".logcat.csv"));
    }

    private static LogLine createLogLine(String message) {
        LogLine logLine = new LogLine("05-26 13:45:12.123 u0_a123 1234 5678 I ActivityManager: " + message);
        logLine.setTimestamp("05-26 13:45:12.123");
        logLine.setUidOwner("u0_a123");
        logLine.setUid(10123);
        logLine.setPid(1234);
        logLine.setTid(5678);
        logLine.setLogLevel(Log.INFO);
        logLine.setTag("ActivityManager");
        logLine.setPackageName("com.example");
        logLine.setLogOutput(message);
        return logLine;
    }
}
