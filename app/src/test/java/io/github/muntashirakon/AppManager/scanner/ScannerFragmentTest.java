// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;

public class ScannerFragmentTest {
    @Test
    public void formatMissingSignaturesReportNormalizesSharedText() {
        String report = ScannerFragment.formatMissingSignaturesReport(null,
                Arrays.asList("\t=BAD\nFake", "com.example.Safe"));

        assertEquals("Package: \nSignatures:\n- ' =BAD Fake\n- com.example.Safe", report);
        assertFalse(report.contains("\t"));
        assertFalse(report.contains("["));
        assertFalse(report.contains("null"));
    }
}
