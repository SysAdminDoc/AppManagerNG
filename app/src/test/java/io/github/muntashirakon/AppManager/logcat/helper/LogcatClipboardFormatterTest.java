// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class LogcatClipboardFormatterTest {
    @Test
    public void formatLineSanitizesSingleCopiedLogLine() {
        assertEquals("'@tag payload", LogcatClipboardFormatter.formatLine("@tag\tpayload"));
    }

    @Test
    public void formatLinesSanitizesSelectedLogLines() {
        assertEquals("'=raw payload\nok line",
                LogcatClipboardFormatter.formatLines(Arrays.asList("=raw\tpayload", "ok\rline")));
    }
}
