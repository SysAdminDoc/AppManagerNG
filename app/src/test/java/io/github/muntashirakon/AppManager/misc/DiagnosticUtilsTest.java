// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticUtilsTest {
    @Test
    public void formatDeviceInfoForReportScrubsSharedDeviceText() {
        String formatted = DiagnosticUtils.formatDeviceInfoForReport(
                "Device model: com.example.secret\n"
                        + "Android build ID: person@example.com\n"
                        + "Android build version: /storage/emulated/0/private.txt\n"
                        + "Mode: uid=10345");

        assertTrue(formatted.contains("<package>"));
        assertTrue(formatted.contains("<email>"));
        assertTrue(formatted.contains("<path>"));
        assertTrue(formatted.contains("uid=<redacted>"));
        assertFalse(formatted.contains("com.example.secret"));
        assertFalse(formatted.contains("person@example.com"));
        assertFalse(formatted.contains("/storage/emulated/0/private.txt"));
        assertFalse(formatted.contains("uid=10345"));
    }

    @Test
    public void formatSharedDiagnosticTextSanitizesStandaloneLines() {
        assertEquals("'=cmd payload\nplain line",
                DiagnosticUtils.formatSharedDiagnosticText("=cmd\tpayload\nplain\rline"));
    }
}
