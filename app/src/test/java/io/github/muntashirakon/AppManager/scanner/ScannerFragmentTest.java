// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void buildMissingSignaturesEmailIntentUsesPinnedRecipientSubjectAndBody() {
        Intent intent = ScannerFragment.buildMissingSignaturesEmailIntent("report body");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("message/rfc822", intent.getType());
        assertArrayEquals(new String[]{"am4android@riseup.net"}, intent.getStringArrayExtra(Intent.EXTRA_EMAIL));
        assertEquals("App Manager: Missing signatures", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals("report body", intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void buildMissingSignaturesEmailIntentRejectsEmptyBody() {
        assertThrows(IllegalArgumentException.class,
                () -> ScannerFragment.buildMissingSignaturesEmailIntent(""));
    }
}
