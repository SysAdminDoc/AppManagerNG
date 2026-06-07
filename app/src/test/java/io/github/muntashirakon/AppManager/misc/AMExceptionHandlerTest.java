// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void buildCrashShareIntentAttachesScrubbedReportAndCrashUriGrant() {
        Uri crashUri = Uri.parse("content://io.github.sysadmindoc.AppManagerNG.filecache/crashes/report.json");

        Intent intent = AMExceptionHandler.buildCrashShareIntent(
                "=cmd\tpayload\n/storage/emulated/0/private.apk", crashUri, 123L);

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("AppManager NG: Crash Report", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT).startsWith("'=cmd payload"));
        assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT).contains("private.apk"));
        assertEquals(crashUri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(crashUri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void buildCrashShareIntentWithoutCrashUriHasNoStreamGrant() {
        Intent intent = AMExceptionHandler.buildCrashShareIntent("plain", null, 123L);

        assertNull(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertFalse((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNull(intent.getClipData());
    }
}
