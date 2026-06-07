// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;

public class SaveLogHelperTest {
    @Test
    public void writeTextUsesUtf8ForSingleString() throws Exception {
        String text = "Device " + "\u03b4" + " " + "\u2603";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SaveLogHelper.writeText(output, text, null);

        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), output.toByteArray());
    }

    @Test
    public void writeTextUsesUtf8ForLineCollections() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SaveLogHelper.writeText(output, null, Arrays.asList("one", "two-" + "\u03b4"));

        assertArrayEquals(("one\ntwo-" + "\u03b4" + "\n").getBytes(StandardCharsets.UTF_8),
                output.toByteArray());
    }

    @Test
    public void readLogUsesUtf8AndKeepsLastMaxLines() throws Exception {
        String input = "old\nnew-" + "\u03b4" + "\nlast\n";

        SavedLog savedLog = SaveLogHelper.readLog(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), 2);

        assertEquals(Arrays.asList("new-" + "\u03b4", "last"), savedLog.getLogLines());
        assertTrue(savedLog.isTruncated());
    }

    @Test
    public void isInvalidFilenameRejectsWhitespaceControlsAndPathSeparators() {
        assertFalse(SaveLogHelper.isInvalidFilename("2026-06-07-120000.am.log"));

        assertTrue(SaveLogHelper.isInvalidFilename(null));
        assertTrue(SaveLogHelper.isInvalidFilename("events.txt"));
        assertTrue(SaveLogHelper.isInvalidFilename("event log.am.log"));
        assertTrue(SaveLogHelper.isInvalidFilename("event\tlog.am.log"));
        assertTrue(SaveLogHelper.isInvalidFilename("event\nlog.am.log"));
        assertTrue(SaveLogHelper.isInvalidFilename("event/log.am.log"));
        assertTrue(SaveLogHelper.isInvalidFilename("event\\log.am.log"));
        assertTrue(SaveLogHelper.isInvalidFilename("event:log.am.log"));
    }
}
