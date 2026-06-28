// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShellBoundedOutputTest {
    @Test
    public void boundedOutput_preservesSmallOutput() {
        Shell.BoundedOutput output = new Shell.BoundedOutput(64);

        output.appendLine("first");
        output.appendLine("second");

        assertEquals("first\nsecond\n", output.toString());
        assertFalse(output.isTruncated());
    }

    @Test
    public void boundedOutput_truncatesLargeOutputOnce() {
        Shell.BoundedOutput output = new Shell.BoundedOutput(12);

        output.appendLine("0123456789");
        output.appendLine("abcdefghij");
        output.appendLine("ignored");

        String text = output.toString();
        assertTrue(text.startsWith("0123456789\na"));
        assertTrue(text.contains(Shell.OUTPUT_TRUNCATED_MARKER));
        assertTrue(output.isTruncated());
        assertFalse(text.contains("ignored"));
        assertTrue(text.length() <= 12 + Shell.OUTPUT_TRUNCATED_MARKER.length());
    }

    @Test
    public void defaultOutputLimitStaysUnderBinderFriendlySize() {
        Shell.BoundedOutput output = new Shell.BoundedOutput(Shell.MAX_OUTPUT_CHARS);
        StringBuilder largeLine = new StringBuilder(Shell.MAX_OUTPUT_CHARS + 4096);
        for (int i = 0; i < Shell.MAX_OUTPUT_CHARS + 4096; ++i) {
            largeLine.append('x');
        }

        output.appendLine(largeLine.toString());

        assertTrue(output.isTruncated());
        assertTrue(output.toString().contains(Shell.OUTPUT_TRUNCATED_MARKER));
        assertTrue(output.toString().length()
                <= Shell.MAX_OUTPUT_CHARS + Shell.OUTPUT_TRUNCATED_MARKER.length());
    }
}
