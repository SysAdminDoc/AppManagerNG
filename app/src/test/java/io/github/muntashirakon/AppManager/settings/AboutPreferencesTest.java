// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AboutPreferencesTest {
    @Test
    public void buildDiagnosticShareIntentPinsZipStreamGrantAndSubject() {
        Uri reportUri = Uri.parse("content://io.github.sysadmindoc.AppManagerNG.filecache/diagnostics/report.zip");

        Intent intent = AboutPreferences.buildDiagnosticShareIntent(reportUri, "Diagnostics");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("application/zip", intent.getType());
        assertEquals("Diagnostics", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals(reportUri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(reportUri, intent.getClipData().getItemAt(0).getUri());
    }
}
