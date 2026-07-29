// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;

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
    public void anEmptyResultIsQualifiedAsNoKnownMatchesRatherThanNoTrackers() {
        Context context = ApplicationProvider.getApplicationContext();
        String absence = ScannerFragment.getResultLimitation(context, 0);

        assertEquals(context.getString(R.string.scanner_absence_limitation), absence);
        // The user must be told why "nothing found" is not "nothing there".
        assertTrue(absence.contains("not proof of absence"));
        assertEquals("No known tracker matches", context.getString(R.string.no_tracker_found));
    }

    @Test
    public void aNonEmptyResultIsQualifiedAsPresenceNotBehaviour() {
        Context context = ApplicationProvider.getApplicationContext();
        String match = ScannerFragment.getResultLimitation(context, 3);

        assertEquals(context.getString(R.string.scanner_match_limitation), match);
        assertTrue(match.contains("does not establish that the code runs"));
    }

    @Test
    public void buildMissingSignaturesEmailIntentRejectsEmptyBody() {
        assertThrows(IllegalArgumentException.class,
                () -> ScannerFragment.buildMissingSignaturesEmailIntent(""));
    }
}
