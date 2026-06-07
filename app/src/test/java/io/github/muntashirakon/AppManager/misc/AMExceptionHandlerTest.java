// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AMExceptionHandlerTest {
    @Test
    public void formatCrashReportForShareScrubsPrivateTextAndSanitizesLines() {
        String report = "=cmd\tpayload\n"
                + "content://com.example.secret/private.apk /storage/emulated/0/private.apk "
                + "userId=10345 person@example.com";

        String shared = AMExceptionHandler.formatCrashReportForShare(report);

        assertTrue(shared.startsWith("'=cmd payload\n"));
        assertTrue(shared.contains("userId=<redacted>"));
        assertTrue(shared.contains("<email>"));
        assertFalse(shared.contains("\t"));
        assertFalse(shared.contains("com.example.secret"));
        assertFalse(shared.contains("private.apk"));
        assertFalse(shared.contains("10345"));
        assertFalse(shared.contains("person@example.com"));
    }
}
